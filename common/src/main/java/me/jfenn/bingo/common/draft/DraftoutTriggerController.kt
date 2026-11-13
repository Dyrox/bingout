package me.jfenn.bingo.common.draft

import me.jfenn.bingo.api.state.GameState
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.model.PlayerDeathEvent
import me.jfenn.bingo.platform.scoreboard.IObjectiveHandle
import me.jfenn.bingo.platform.scoreboard.IScoreboardManager
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraft.tags.EntityTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Leashable
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.MapItem
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.AABB
import org.slf4j.Logger
import java.util.*

/**
 * Detects the "draftout" goals that cannot be expressed with vanilla advancement criteria or statistics.
 *
 * - Boolean goals are completed by awarding the hidden `yet-another-minecraft-bingo:draftout/...` advancement
 *   (which only has a `minecraft:impossible` criterion), referenced by the objective json.
 * - Counting goals write their value (relative to the start of the game) into a dummy scoreboard objective,
 *   which is referenced by a "scoreboard" objective.
 *
 * Generated data lives in `scripts/draftout/` (see `generate.py`).
 */
internal class DraftoutTriggerController(
    events: ScopedEvents,
    eventBus: IEventBus,
    private val log: Logger,
    private val server: MinecraftServer,
    private val state: BingoState,
    private val teamService: TeamService,
    private val playerManager: IPlayerManager,
    private val scoreboardManager: IScoreboardManager,
) : BingoComponent() {

    companion object {
        const val SCORE_BRED = "draftout_bred"
        const val SCORE_SPY = "draftout_spy"
        const val SCORE_COMPOST = "draftout_compost"
        const val SCORE_FOOD = "draftout_food"
        const val SCORE_CRAFTED = "draftout_crafted"
        const val SCORE_UNDEAD = "draftout_undead"
        const val SCORE_ARTHROPOD = "draftout_arthropod"
        const val SCORE_ADVANCEMENTS = "draftout_advancements"
        const val SCORE_EFFECTS = "draftout_effects"
        const val SCORE_PUMPKIN = "draftout_pumpkin"
        const val SCORE_LEASH = "draftout_leash"

        val SCORES = listOf(
            SCORE_BRED, SCORE_SPY, SCORE_COMPOST, SCORE_FOOD, SCORE_CRAFTED, SCORE_UNDEAD, SCORE_ARTHROPOD,
            SCORE_ADVANCEMENTS, SCORE_EFFECTS, SCORE_PUMPKIN, SCORE_LEASH,
        )

        private const val DRAFTOUT_PREFIX = "$MOD_ID_BINGO:draftout/"
        private val ARMOR_SLOTS = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
        private val ARMOR_SUFFIXES = listOf("_helmet", "_chestplate", "_leggings", "_boots")
    }

    /** Statistics recorded for each player at the start of the game (so that counts are relative) */
    private class Baseline(
        val foodUsed: Map<Item, Int>,
        val crafted: Map<Item, Int>,
        val undeadKills: Int,
        val arthropodKills: Int,
    )

    private class PlayerState {
        var maxEffects = 0
        var pumpkinTicks = 0
        var maxLeashed = 0
        var lastDimension: String? = null
    }

    private val baselines = mutableMapOf<UUID, Baseline>()
    private val playerStates = mutableMapOf<UUID, PlayerState>()
    private val scoreHandles = mutableMapOf<String, IObjectiveHandle>()
    private var tickCount = 0

    private val foodItems by lazy {
        BuiltInRegistries.ITEM.filter { it.components().has(DataComponents.FOOD) }
    }
    private val undeadTypes by lazy {
        BuiltInRegistries.ENTITY_TYPE.filter { BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(it).`is`(EntityTypeTags.UNDEAD) }
    }
    private val arthropodTypes by lazy {
        BuiltInRegistries.ENTITY_TYPE.filter { BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(it).`is`(EntityTypeTags.ARTHROPOD) }
    }
    private val sulfurSpike: Block? by lazy {
        BuiltInRegistries.BLOCK.getOptional(Identifier.parse("minecraft:sulfur_spike")).orElse(null)
    }

    // ---------- helpers ----------

    private fun entityId(type: EntityType<*>?): String? = type?.let { BuiltInRegistries.ENTITY_TYPE.getKey(it).toString() }
    private fun entityId(entity: Entity?): String? = entityId(entity?.type)

    private fun score(name: String): IObjectiveHandle? {
        return scoreHandles.getOrPut(name) {
            runCatching { scoreboardManager.createDummyObjective(name) }
                .onFailure { log.error("[DraftoutTriggerController] Unable to create scoreboard $name", it) }
                .getOrNull()
                ?: return null
        }
    }

    private fun setScore(player: IPlayerHandle, name: String, value: Int) {
        val handle = score(name) ?: return
        if (handle.getForPlayer(player) != value) {
            handle.setPlayer(player, value)
        }
    }

    private fun grant(player: ServerPlayer, name: String) {
        val holder = server.advancements.get(Identifier.parse(DRAFTOUT_PREFIX + name)) ?: run {
            log.warn("[DraftoutTriggerController] Missing advancement $DRAFTOUT_PREFIX$name")
            return
        }
        if (!player.advancements.getOrStartProgress(holder).isDone) {
            player.advancements.award(holder, "granted")
        }
    }

    private fun countCriteria(player: ServerPlayer, name: String): Int {
        val holder = server.advancements.get(Identifier.parse(DRAFTOUT_PREFIX + name)) ?: return 0
        return player.advancements.getOrStartProgress(holder).completedCriteria.count { it != "never" }
    }

    /** Whether any card in the game uses draftout goals (otherwise all checks are skipped) */
    private fun hasDraftoutGoals(): Boolean {
        return state.cards.any { card -> card.objectives.keys.any { it.startsWith(DRAFTOUT_PREFIX) } }
    }

    private fun createBaseline(player: ServerPlayer): Baseline {
        val stats = player.stats
        return Baseline(
            foodUsed = foodItems.associateWith { stats.getValue(Stats.ITEM_USED, it) }.filterValues { it > 0 },
            crafted = BuiltInRegistries.ITEM.associateWith { stats.getValue(Stats.ITEM_CRAFTED, it) }.filterValues { it > 0 },
            undeadKills = undeadTypes.sumOf { stats.getValue(Stats.ENTITY_KILLED, it) },
            arthropodKills = arthropodTypes.sumOf { stats.getValue(Stats.ENTITY_KILLED, it) },
        )
    }

    // ---------- deaths ----------

    private fun onDeath(player: ServerPlayer, source: DamageSource) {
        val msgId = source.msgId
        val attacker = entityId(source.entity)
        val direct = entityId(source.directEntity)
        val deathKey = (player.combatTracker.deathMessage.contents as? TranslatableContents)?.key ?: ""

        val goals = buildList {
            if (msgId == "anvil") add("die_by_anvil")
            if (attacker == "minecraft:bee" || direct == "minecraft:bee") add("die_by_bee_sting")
            if (msgId == "sweetBerryBush") add("die_by_berry_bush")
            if (msgId == "cactus") add("die_by_cactus")
            if (deathKey.startsWith("death.fell.") && deathKey.contains("vines")) add("die_by_falling_off_vine")
            if (msgId == "fallingStalactite") add("die_by_falling_stalactite")
            if (msgId == "fireworks") add("die_by_firework")
            if (msgId == "badRespawnPoint") add("die_by_intentional_game_design")
            if (attacker == "minecraft:iron_golem") add("die_by_iron_golem")
            if (msgId == "drown") add("die_by_drowning")
            if (attacker == "minecraft:polar_bear") add("die_to_polar_bear")
            if (msgId == "magic" || msgId == "indirectMagic") add("die_by_magic")
            if (direct == "minecraft:tnt_minecart") add("die_by_tnt_minecart")
            if (msgId == "freeze") add("freeze_to_death")
            if (attacker == "minecraft:pufferfish") add("die_to_pufferfish")
            if (msgId == "trident") add("die_to_trident")
            if (attacker == "minecraft:warden") add("die_to_warden")
            if (msgId == "outOfWorld") add("die_to_void")
            if (attacker == "minecraft:sulfur_cube" || direct == "minecraft:sulfur_cube" || msgId == "sulfurCubeHot") add("die_to_sulfur_cube")
        }

        log.debug("[DraftoutTriggerController] {} died: msgId={} attacker={} direct={} key={} -> {}", player.scoreboardName, msgId, attacker, direct, deathKey, goals)
        for (goal in goals) grant(player, "death/$goal")
    }

    // ---------- per-tick checks ----------

    private fun armorMaterial(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val path = BuiltInRegistries.ITEM.getKey(stack.item).path
        val suffix = ARMOR_SUFFIXES.find { path.endsWith(it) } ?: return path
        return path.removeSuffix(suffix)
    }

    private fun checkEquipment(player: ServerPlayer, playerState: PlayerState) {
        val armor = ARMOR_SLOTS.map { player.getItemBySlot(it) }

        // 4 different armor materials
        if (armor.none { it.isEmpty }) {
            val materials = armor.mapNotNull { armorMaterial(it) }.toSet()
            if (materials.size == 4) grant(player, "wear_unique_armor")
        }

        // 4 leather armor pieces with 4 different (non-default) colors
        val leather = listOf(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS)
        if (armor.zip(leather).all { (stack, item) -> stack.`is`(item) }) {
            val colors = armor.mapNotNull { it.get(DataComponents.DYED_COLOR)?.rgb() }
            if (colors.size == 4 && colors.toSet().size == 4) grant(player, "wear_unique_colored_leather_armor")
        }

        // carved pumpkin worn for 5 minutes (cumulative)
        if (armor[0].`is`(Items.CARVED_PUMPKIN)) {
            playerState.pumpkinTicks += 10
        }
    }

    private fun checkInventory(player: ServerPlayer) {
        val inventory = player.inventory
        val main = (0 until 36).map { inventory.getItem(it) }

        if (main.any { it.count >= 64 }) grant(player, "stack_of_64")

        if (main.none { it.isEmpty } && main.map { it.item }.toSet().size == 36) grant(player, "fill_inventory_unique")

        val hasLockedMap = (0 until inventory.containerSize)
            .map { inventory.getItem(it) }
            .filter { it.`is`(Items.FILLED_MAP) }
            .any { MapItem.getSavedData(it, player.level())?.locked == true }
        if (hasLockedMap) grant(player, "lock_map")

        if (player.cooldowns.isOnCooldown(ItemStack(Items.SHIELD))) grant(player, "shield_disabled")
    }

    private fun checkNearbyEntities(player: ServerPlayer, playerState: PlayerState) {
        val level = player.level()
        val nearby = level.getEntities(player, AABB(player.blockPosition()).inflate(64.0))

        var leashedTypes = mutableSetOf<String>()
        for (entity in nearby) {
            val type = entityId(entity) ?: continue

            if (entity is Leashable && entity.leashHolder == player) {
                leashedTypes.add(type)
                if (type == "minecraft:iron_golem") grant(player, "leash_iron_golem")
            }

            if (entity is Mob && entity.target == player) {
                if (type == "minecraft:enderman") grant(player, "enrage_enderman")
                if (type == "minecraft:zombified_piglin") grant(player, "enrage_zombified_piglin")
            }

            if (entity.distanceToSqr(player) < 16.0 * 16.0) {
                val name = entity.customName?.string
                if (name == "Dinnerbone" || name == "Grumm") {
                    if (type == "minecraft:iron_golem") grant(player, "name_iron_golem_dinnerbone")
                    if (type == "minecraft:ghast") grant(player, "name_ghast_dinnerbone")
                }
                if (name == "jeb_" && type == "minecraft:sheep") grant(player, "name_sheep_jeb")

                if (entity is FireworkRocketEntity && entity.isShotAtAngle && entity.owner == player) {
                    grant(player, "crossbow_firework")
                }
            }
        }

        playerState.maxLeashed = maxOf(playerState.maxLeashed, leashedTypes.size)
    }

    private fun checkMisc(player: ServerPlayer, playerState: PlayerState) {
        if (player.foodData.foodLevel <= 0) grant(player, "empty_hunger")

        playerState.maxEffects = maxOf(playerState.maxEffects, player.activeEffects.size)

        // launched upwards by a geyser (sulfur spike)
        val spike = sulfurSpike
        if (spike != null && player.deltaMovement.y > 0.6 && !player.isFallFlying) {
            val pos = player.blockPosition()
            val nearSpike = (1..6).any { dy ->
                (-1..1).any { dx -> (-1..1).any { dz -> player.level().getBlockState(pos.offset(dx, -dy, dz)).`is`(spike) } }
            }
            if (nearSpike) grant(player, "ride_geyser")
        }
    }

    private fun checkDimension(player: ServerPlayer, playerState: PlayerState) {
        val dimension = player.level().dimension().toString()
        val previous = playerState.lastDimension
        playerState.lastDimension = dimension

        if (previous != null && previous != dimension) {
            val dimensions = setOf(previous, dimension)
            val isNetherTravel = dimensions.any { it.contains("the_nether") } && dimensions.any { it.contains("overworld") }
            val lastDamage = player.lastDamageSource?.typeHolder()?.registeredName
            if (isNetherTravel && lastDamage == "minecraft:ender_pearl") {
                grant(player, "pearl_portal")
            }
        }
    }

    private fun updateScores(handle: IPlayerHandle, player: ServerPlayer, playerState: PlayerState, includeSlow: Boolean) {
        setScore(handle, SCORE_BRED, countCriteria(player, "counter/bred"))
        setScore(handle, SCORE_SPY, countCriteria(player, "counter/spy"))
        setScore(handle, SCORE_COMPOST, countCriteria(player, "counter/compost"))
        setScore(handle, SCORE_EFFECTS, playerState.maxEffects)
        setScore(handle, SCORE_PUMPKIN, playerState.pumpkinTicks / 20)
        setScore(handle, SCORE_LEASH, playerState.maxLeashed)

        if (!includeSlow) return

        val baseline = baselines.getOrPut(player.uuid) { createBaseline(player) }
        val stats = player.stats

        setScore(handle, SCORE_FOOD, foodItems.count { stats.getValue(Stats.ITEM_USED, it) > (baseline.foodUsed[it] ?: 0) })
        setScore(handle, SCORE_CRAFTED, BuiltInRegistries.ITEM.count { stats.getValue(Stats.ITEM_CRAFTED, it) > (baseline.crafted[it] ?: 0) })
        setScore(handle, SCORE_UNDEAD, undeadTypes.sumOf { stats.getValue(Stats.ENTITY_KILLED, it) } - baseline.undeadKills)
        setScore(handle, SCORE_ARTHROPOD, arthropodTypes.sumOf { stats.getValue(Stats.ENTITY_KILLED, it) } - baseline.arthropodKills)

        val advancements = server.advancements.allAdvancements.count { holder ->
            holder.id.namespace == "minecraft" &&
                    !holder.id.path.startsWith("recipes/") &&
                    holder.value.display.isPresent &&
                    player.advancements.getOrStartProgress(holder).isDone
        }
        setScore(handle, SCORE_ADVANCEMENTS, advancements)
    }

    private fun resetPlayers() {
        baselines.clear()
        playerStates.clear()
        for (player in playerManager.getPlayers()) {
            for (name in SCORES) setScore(player, name, 0)
        }
    }

    init {
        // create the scoreboards up front, so that "scoreboard" objectives can find them
        for (name in SCORES) score(name)

        events.onEnter(GameState.PLAYING) { from ->
            // don't reset when resuming a game from POSTGAME
            if (from != GameState.POSTGAME) resetPlayers()
        }

        events.onGameTick {
            if (state.state != GameState.PLAYING) return@onGameTick
            tickCount++
            if (tickCount % 10 != 0) return@onGameTick
            if (!hasDraftoutGoals()) return@onGameTick

            for (handle in playerManager.getPlayers()) {
                if (!teamService.isPlaying(handle)) continue
                val player = handle.player
                if (!player.isAlive) continue
                val playerState = playerStates.getOrPut(player.uuid) { PlayerState() }

                try {
                    checkEquipment(player, playerState)
                    checkInventory(player)
                    checkNearbyEntities(player, playerState)
                    checkMisc(player, playerState)
                    checkDimension(player, playerState)
                    updateScores(handle, player, playerState, includeSlow = tickCount % 40 == 0)
                } catch (e: Exception) {
                    log.error("[DraftoutTriggerController] Error while checking ${player.scoreboardName}", e)
                }
            }
        }

        eventBus.register(PlayerDeathEvent) { event ->
            if (state.state != GameState.PLAYING) return@register
            if (!teamService.isPlaying(event.player)) return@register
            try {
                onDeath(event.player.player, event.source)
            } catch (e: Exception) {
                log.error("[DraftoutTriggerController] Error while handling death", e)
            }
        }
    }
}
