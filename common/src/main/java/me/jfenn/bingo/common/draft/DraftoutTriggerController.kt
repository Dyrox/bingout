package me.jfenn.bingo.common.draft

import me.jfenn.bingo.api.state.GameState
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.NBT_BINGO_IGNORE
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.event.model.TeamChangedEvent
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.OfflinePlayerCache
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.game.ScopeStopped
import me.jfenn.bingo.platform.event.model.BreakBlockEvent
import me.jfenn.bingo.platform.scoreboard.IObjectiveHandle
import me.jfenn.bingo.platform.scoreboard.IScoreboardManager
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerAdvancementManager
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraft.tags.EntityTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AnvilMenu
import net.minecraft.world.inventory.CartographyTableMenu
import net.minecraft.world.inventory.GrindstoneMenu
import net.minecraft.world.inventory.LoomMenu
import net.minecraft.world.inventory.SmithingMenu
import net.minecraft.world.inventory.StonecutterMenu
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.MapItem
import net.minecraft.world.level.Level
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
 *
 * Registered before ScoredItemCheck (CommonModule), so that the card is checked against this tick's counters, and
 * players from an earlier game are cleared before the card reads their progress.
 */
internal class DraftoutTriggerController(
    events: ScopedEvents,
    eventBus: IEventBus,
    private val log: Logger,
    private val server: MinecraftServer,
    private val state: BingoState,
    private val teamService: TeamService,
    private val playerManager: IPlayerManager,
    private val offlinePlayerCache: OfflinePlayerCache,
    private val scoreboardManager: IScoreboardManager,
    private val details: ProgressDetailsService,
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

        const val SCORE_MOB_KILLS = "draftout_mob_kills"

        val SCORES = listOf(
            SCORE_BRED, SCORE_SPY, SCORE_COMPOST, SCORE_FOOD, SCORE_CRAFTED, SCORE_UNDEAD, SCORE_ARTHROPOD,
            SCORE_ADVANCEMENTS, SCORE_EFFECTS, SCORE_PUMPKIN, SCORE_LEASH, SCORE_MOB_KILLS,
        )

        /**
         * Counters combined over the whole team: distinct things are the union of what every teammate did
         * (5 + 10 crafted items with 3 in common = 12), kill counts are summed. The other counters
         * ("at once" / worn time) are per player.
         */
        val TEAM_SCORES = listOf(
            SCORE_BRED, SCORE_SPY, SCORE_COMPOST, SCORE_FOOD, SCORE_CRAFTED, SCORE_UNDEAD, SCORE_ARTHROPOD,
            SCORE_ADVANCEMENTS, SCORE_MOB_KILLS,
        )

        /** Prefix of the draftout objectives and their hidden advancements */
        const val DRAFTOUT_PREFIX = "$MOD_ID_BINGO:draftout/"

        /** Interval (in ticks) between the heavier world/inventory/statistics scans */
        private const val CHECK_INTERVAL = 5
        private val ARMOR_SLOTS = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
        private val ARMOR_SUFFIXES = listOf("_helmet", "_chestplate", "_leggings", "_boots")
        private val ARMOR_MATERIALS = setOf("leather", "chainmail", "copper", "golden", "iron", "diamond", "netherite")

        /** How far leashed mobs can be (leads break further away) */
        private const val LEASH_RANGE = 16.0
        /** How far mobs targeting the player are looked for */
        private const val TARGET_RANGE = 64.0

        /** The controller of the running server, for the mixin hooks below */
        private var instance: DraftoutTriggerController? = null

        /**
         * Runs a mixin hook for a player of the running game. Errors are logged instead of being thrown into the
         * vanilla code that called the hook (which could disconnect the player).
         */
        private inline fun hook(entity: Entity?, what: String, action: DraftoutTriggerController.(ServerPlayer) -> Unit) {
            val player = entity as? ServerPlayer ?: return
            val controller = instance ?: return
            try {
                if (controller.isInGame(player)) controller.action(player)
            } catch (e: Exception) {
                controller.log.error("[DraftoutTriggerController] Error while handling $what", e)
            }
        }

        /**
         * The player took the output of a stonecutter, anvil, grindstone, loom, smithing table or cartography table
         * (just opening the block doesn't count, like Draftout).
         */
        @JvmStatic
        fun onWorkstationResultTaken(player: Player) = hook(player, "a workstation result") { onWorkstationUsed(it) }

        /**
         * The player named a mob with a name tag: "Name a Sheep 'jeb_'", "Name a Ghast 'Dinnerbone'"
         * (only the player who used the name tag, like Draftout - not everyone near a named mob)
         */
        @JvmStatic
        fun onNameTagUsed(player: Player, target: Entity) = hook(player, "a name tag") { onNamed(it, target) }

        /** The player put an item into a decorated pot */
        @JvmStatic
        fun onDecoratedPotFilled(player: Player, item: ItemStack) = hook(player, "a decorated pot") {
            // "full" like Draftout: as many items as the item stacks to (64, 16 or 1)
            if (!item.isEmpty && item.count >= item.maxStackSize) grant(it, "fill_decorated_pot")
        }

        /** A firework rocket was shot from a crossbow */
        @JvmStatic
        fun onCrossbowFirework(shooter: Entity) = hook(shooter, "a crossbow shot") { grant(it, "crossbow_firework") }

        /**
         * An erupting geyser (potent sulfur) is launching the player. Players are moved by their own client, so this is
         * the server's only sign of it: their velocity on the server doesn't change.
         */
        @JvmStatic
        fun onGeyserLaunch(entity: Entity) = hook(entity, "a geyser") { player ->
            if (!player.abilities.flying && !player.isPassenger) grant(player, "ride_geyser")
        }

        /** An ender pearl is about to teleport its owner to where it landed, possibly through a nether portal */
        @JvmStatic
        fun onPearlTeleport(owner: Entity?, pearlLevel: Level) = hook(owner, "an ender pearl") { player ->
            val dimensions = setOf(player.level().dimension(), pearlLevel.dimension())
            if (dimensions == setOf(Level.OVERWORLD, Level.NETHER)) grant(player, "pearl_portal")
        }

        /**
         * The player is dying (no totem saved them): the death goals. Not on Fabric's death event, which comes at the
         * end of ServerPlayer.die: by then the combat log is cleared (CombatTracker.recheckStatus), and only the log
         * knows that the fall started on vines ("fell off some vines").
         */
        @JvmStatic
        fun onPlayerDie(player: ServerPlayer, source: DamageSource) = hook(player, "a death") { onDeath(it, source) }
    }

    /** Saved with the game state, so that counters survive reloading the world / restarting the server */
    private val playerStates get() = state.draftoutPlayers
    private var tickCount = 0

    /**
     * A value derived from the server's advancements, recomputed when they change (data pack reload):
     * the controller lives as long as the server, so cached advancement holders could otherwise go stale.
     */
    private inner class AdvancementCache<T>(private val compute: (ServerAdvancementManager) -> T) {
        private var source: ServerAdvancementManager? = null
        private var value: T? = null

        fun get(): T {
            val current = server.advancements
            @Suppress("UNCHECKED_CAST")
            if (source !== current || value == null) {
                value = compute(current)
                source = current
            }
            return value as T
        }
    }

    /** Vanilla advancements counted by "Get N Advancements" (displayed, non-recipe) */
    private val countedAdvancementsCache = AdvancementCache { advancements ->
        advancements.allAdvancements.filter { holder ->
            holder.id.namespace == "minecraft" &&
                    !holder.id.path.startsWith("recipes/") &&
                    holder.value.display.isPresent
        }
    }

    /** All hidden draftout advancements (counters and goals granted by code or data) */
    private val draftoutAdvancementsCache = AdvancementCache { advancements ->
        advancements.allAdvancements.filter { it.id.namespace == MOD_ID_BINGO && it.id.path.startsWith("draftout/") }
    }

    /**
     * Goals made of several criteria (e.g. "Visit All Nether Biomes": one per biome) count what the whole team
     * has done, like the counters: each criterion is shared with the online teammates, so the goal completes
     * once the team has covered all of them. (Counters are combined through their scores instead.)
     */
    private val teamAdvancementsCache = AdvancementCache { advancements ->
        advancements.allAdvancements.filter { holder ->
            holder.id.namespace == MOD_ID_BINGO && holder.id.path.startsWith("draftout/") &&
                !holder.id.path.startsWith("draftout/counter/") && holder.value.requirements().size() > 1
        }
    }

    /**
     * "draftout/mine/..." advancements, by the block criteria they contain (e.g. "obsidian" -> mine/obsidian).
     * Mining goals count any break by the player, regardless of the tool (like Draftout).
     */
    private val mineAdvancementsCache = AdvancementCache { advancements ->
        advancements.allAdvancements
            .filter { it.id.namespace == MOD_ID_BINGO && it.id.path.startsWith("draftout/mine/") }
            .flatMap { holder -> holder.value.criteria().keys.map { it to holder } }
            .groupBy({ it.first }, { it.second })
    }

    // registry contents, with their ids (looked up once: the counters scan them several times a second)

    private val items: List<Pair<Item, String>> by lazy {
        BuiltInRegistries.ITEM.map { it to BuiltInRegistries.ITEM.getKey(it).toString() }
    }

    private fun entityTypes(filter: (EntityType<*>) -> Boolean): List<Pair<EntityType<*>, String>> =
        BuiltInRegistries.ENTITY_TYPE.filter(filter).map { it to BuiltInRegistries.ENTITY_TYPE.getKey(it).toString() }

    /** Every entity type except players (for "Kill 100 Mobs") */
    private val mobTypes by lazy { entityTypes { BuiltInRegistries.ENTITY_TYPE.getKey(it).toString() != "minecraft:player" } }
    private val undeadTypes by lazy { entityTypes { BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(it).`is`(EntityTypeTags.UNDEAD) } }
    private val arthropodTypes by lazy { entityTypes { BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(it).`is`(EntityTypeTags.ARTHROPOD) } }

    // ---------- helpers ----------

    private fun entityId(entity: Entity?): String? = entity?.let { BuiltInRegistries.ENTITY_TYPE.getKey(it.type).toString() }

    /** Whether [player] is playing in the running game */
    private fun isInGame(player: ServerPlayer): Boolean {
        if (state.state != GameState.PLAYING) return false
        val handle = playerManager.getPlayer(player.uuid) ?: return false
        return teamService.isPlaying(handle)
    }

    /**
     * The counter's scoreboard objective, looked up by name each time: resetting the worlds between rounds removes
     * all scoreboard objectives, so a handle kept from an earlier round would point to a deleted objective
     * (scores written to it were never seen by the card - every counter stayed at 0 from the second round on).
     */
    private fun score(name: String): IObjectiveHandle? {
        return scoreboardManager.getByName(name)
            ?: runCatching { scoreboardManager.createDummyObjective(name) }
                .onFailure { log.error("[DraftoutTriggerController] Unable to create scoreboard $name", it) }
                .getOrNull()
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

    /** Whether any card in the game uses draftout goals (otherwise all checks are skipped) */
    private fun hasDraftoutGoals(): Boolean {
        return state.cards.any { card -> card.objectives.keys.any { it.startsWith(DRAFTOUT_PREFIX) } }
    }

    // ---------- players of the current game ----------

    private fun revokeDraftoutAdvancements(player: ServerPlayer) {
        for (holder in draftoutAdvancementsCache.get()) {
            val progress = player.advancements.getOrStartProgress(holder)
            for (criterion in progress.completedCriteria.toList()) player.advancements.revoke(holder, criterion)
        }
    }

    /**
     * Clears what the player did in earlier games - the hidden draftout advancements and the counter scores - and
     * starts tracking them in this one. Bingo only resets online players' advancements, and only in lobby mode, while
     * the card also reads the saved progress of offline team members: without this, last game's "Eat 15 Unique Food"
     * was completed as soon as the next game started.
     */
    private fun startTracking(handle: IPlayerHandle) {
        if (playerStates.containsKey(handle.uuid)) return
        val player = handle.player
        revokeDraftoutAdvancements(player)
        for (name in SCORES) setScore(handle, name, 0)
        // an offline player's advancements were loaded from their save file; nothing else would write them back
        if (playerManager.getPlayer(handle.uuid) == null) player.advancements.save()
        playerStates[handle.uuid] = DraftoutPlayerData()
    }

    /** Team members (online or offline) that aren't tracked yet, e.g. added to a team while offline */
    private fun trackNewTeamMembers() {
        for (team in state.getRegisteredTeams()) {
            for (member in team.players) {
                if (!playerStates.containsKey(member.uuid)) {
                    startTracking(offlinePlayerCache.getOfflinePlayer(member.profile))
                }
            }
        }
    }

    /** A new game: forget everything from the previous one, for everyone the card can read */
    private fun resetPlayers() {
        details.clear()
        playerStates.clear()
        for (player in playerManager.getPlayers()) startTracking(player)
        trackNewTeamMembers()
    }

    private fun createBaseline(player: ServerPlayer): DraftoutPlayerData.Baseline {
        val stats = player.stats
        return DraftoutPlayerData.Baseline(
            crafted = items.associate { (item, id) -> id to stats.getValue(Stats.ITEM_CRAFTED, item) }
                .filterValues { it > 0 }.toMutableMap(),
            kills = mobTypes.associate { (type, id) -> id to stats.getValue(Stats.ENTITY_KILLED, type) }
                .filterValues { it > 0 }.toMutableMap(),
            // vanilla advancements are only reset by Bingo in lobby mode
            advancements = countedAdvancementsCache.get()
                .filter { player.advancements.getOrStartProgress(it).isDone }
                .map { it.id.toString() }
                .toMutableSet(),
        )
    }

    // ---------- events ----------

    private fun onBlockBroken(player: ServerPlayer, blockId: String) {
        val criterion = blockId.removePrefix("minecraft:")
        for (holder in mineAdvancementsCache.get()[criterion].orEmpty()) {
            player.advancements.award(holder, criterion)
        }
    }

    private fun onWorkstationUsed(player: ServerPlayer) {
        val station = when (player.containerMenu) {
            is StonecutterMenu -> "stonecutter"
            is CartographyTableMenu -> "cartography_table"
            is AnvilMenu -> "anvil"
            is GrindstoneMenu -> "grindstone"
            is LoomMenu -> "loom"
            is SmithingMenu -> "smithing_table"
            else -> return
        }
        grant(player, "use/$station")
    }

    private fun onNamed(player: ServerPlayer, target: Entity) {
        val name = target.customName?.string ?: return
        val type = entityId(target) ?: return
        if (name == "Dinnerbone" || name == "Grumm") {
            if (type == "minecraft:iron_golem") grant(player, "name_iron_golem_dinnerbone")
            if (type == "minecraft:ghast") grant(player, "name_ghast_dinnerbone")
        }
        if (name == "jeb_" && type == "minecraft:sheep") grant(player, "name_sheep_jeb")
    }

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

    // ---------- periodic checks ----------

    /**
     * The material of an armor piece worn in the slot ARMOR_SLOTS[slot] (e.g. "iron" for iron_chestplate), or null
     * for anything that isn't a real armor piece of that slot (elytra, carved pumpkin, heads, ...)
     */
    private fun armorMaterial(stack: ItemStack, slot: Int): String? {
        if (stack.isEmpty) return null
        val path = BuiltInRegistries.ITEM.getKey(stack.item).path
        val suffix = ARMOR_SUFFIXES[slot]
        if (!path.endsWith(suffix)) return null
        return path.removeSuffix(suffix).takeIf { it in ARMOR_MATERIALS }
    }

    private fun checkEquipment(player: ServerPlayer, playerState: DraftoutPlayerData) {
        val armor = ARMOR_SLOTS.map { player.getItemBySlot(it) }

        // 4 different armor materials
        if (armor.none { it.isEmpty }) {
            val materials = armor.mapIndexedNotNull { slot, stack -> armorMaterial(stack, slot) }.toSet()
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
            playerState.pumpkinTicks += CHECK_INTERVAL
        }
    }

    private val shieldStack = ItemStack(Items.SHIELD)

    private fun isBingoItem(stack: ItemStack) =
        stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.contains(NBT_BINGO_IGNORE) == true

    private fun checkInventory(player: ServerPlayer) {
        val inventory = player.inventory
        val main = (0 until 36).map { inventory.getItem(it) }

        if (main.any { it.count >= 64 }) grant(player, "stack_of_64")

        if (main.none { it.isEmpty } && main.map { it.item }.toSet().size == 36) grant(player, "fill_inventory_unique")

        // Any map item: explorer maps (buried treasure, woodland mansion, ...) are items of their own in 26.x.
        // Bingo's own card maps (the team card of players without the card HUD, the memento card after a game) are
        // locked maps too.
        val hasLockedMap = (0 until inventory.containerSize)
            .map { inventory.getItem(it) }
            .filter { it.has(DataComponents.MAP_ID) && !isBingoItem(it) }
            .any { MapItem.getSavedData(it, player.level())?.locked == true }
        if (hasLockedMap) grant(player, "lock_map")

        if (player.cooldowns.isOnCooldown(shieldStack)) grant(player, "shield_disabled")
    }

    private fun checkNearbyEntities(player: ServerPlayer, playerState: DraftoutPlayerData) {
        val level = player.level()
        val box = AABB(player.blockPosition())

        // "Leash N unique Mobs at once" (boats can be leashed too, but aren't mobs)
        val leashedTypes = level.getEntitiesOfClass(Mob::class.java, box.inflate(LEASH_RANGE)) { it.leashHolder == player }
            .mapNotNull { entityId(it) }
            .toSet()
        if ("minecraft:iron_golem" in leashedTypes) grant(player, "leash_iron_golem")
        if (leashedTypes.size > playerState.maxLeashed) {
            playerState.maxLeashed = leashedTypes.size
            playerState.maxLeashedIds = leashedTypes.associateWith { 1 }
        }

        for (mob in level.getEntitiesOfClass(Mob::class.java, box.inflate(TARGET_RANGE)) { it.target == player }) {
            when (entityId(mob)) {
                "minecraft:enderman" -> grant(player, "enrage_enderman")
                "minecraft:zombified_piglin" -> grant(player, "enrage_zombified_piglin")
            }
        }
    }

    private fun updateMaxEffects(player: ServerPlayer, playerState: DraftoutPlayerData) {
        // the infinite night vision given by Bingo's "Night Vision" feature doesn't count
        val effects = player.activeEffects
            .filterNot { it.isInfiniteDuration && it.effect == MobEffects.NIGHT_VISION }
        if (effects.size > playerState.maxEffects) {
            playerState.maxEffects = effects.size
            playerState.maxEffectIds = effects
                .mapNotNull { BuiltInRegistries.MOB_EFFECT.getKey(it.effect.value())?.toString() }
                .associateWith { 1 }
        }
    }

    // ---------- counters ----------

    private fun completedCriteria(player: ServerPlayer, name: String): Map<String, Int> {
        val holder = server.advancements.get(Identifier.parse(DRAFTOUT_PREFIX + name)) ?: return emptyMap()
        return player.advancements.getOrStartProgress(holder).completedCriteria
            .filter { it != "never" }
            .associate { "minecraft:$it" to 1 }
    }

    /**
     * Remembers which things the player counted (for the card tooltip); the scores of the team counters
     * are set for the whole team in [updateTeamScores].
     */
    private fun setCounter(handle: IPlayerHandle, name: String, values: Map<String, Int>, showCounts: Boolean = false) {
        details.set(handle.uuid, name, values, showCounts)
    }

    /** Counters that are cheap to compute (advancement criteria, per-player state): updated every tick */
    private fun updateFastCounters(handle: IPlayerHandle, player: ServerPlayer, playerState: DraftoutPlayerData) {
        setCounter(handle, SCORE_BRED, completedCriteria(player, "counter/bred"))
        setCounter(handle, SCORE_SPY, completedCriteria(player, "counter/spy"))
        setCounter(handle, SCORE_COMPOST, completedCriteria(player, "counter/compost"))
        // foods actually eaten ("consume_item"): the "used" statistic also counts planting carrots, placing berries,
        // composting, filling decorated pots or emptying fish buckets
        setCounter(handle, SCORE_FOOD, completedCriteria(player, "counter/food"))
        details.set(handle.uuid, SCORE_EFFECTS, playerState.maxEffectIds)
        setScore(handle, SCORE_EFFECTS, playerState.maxEffects)
        setScore(handle, SCORE_PUMPKIN, playerState.pumpkinTicks / 20)
        details.set(handle.uuid, SCORE_LEASH, playerState.maxLeashedIds)
        setScore(handle, SCORE_LEASH, playerState.maxLeashed)
    }

    /** Counters relative to the statistics/advancements at the start of the game (every CHECK_INTERVAL ticks) */
    private fun updateStatCounters(handle: IPlayerHandle, player: ServerPlayer, baseline: DraftoutPlayerData.Baseline) {
        val stats = player.stats

        // how much the value went up since the baseline; a value below the baseline lowers it (the player was reset)
        fun sinceBaseline(base: MutableMap<String, Int>, id: String, value: Int): Int {
            val before = base[id] ?: 0
            if (value < before) {
                if (value > 0) base[id] = value else base.remove(id)
                return 0
            }
            return value - before
        }

        setCounter(handle, SCORE_CRAFTED, items
            .filter { (item, id) -> sinceBaseline(baseline.crafted, id, stats.getValue(Stats.ITEM_CRAFTED, item)) > 0 }
            .associate { (_, id) -> id to 1 })

        fun kills(types: List<Pair<EntityType<*>, String>>) = types
            .associate { (type, id) -> id to sinceBaseline(baseline.kills, id, stats.getValue(Stats.ENTITY_KILLED, type)) }
            .filterValues { it > 0 }
        setCounter(handle, SCORE_UNDEAD, kills(undeadTypes), showCounts = true)
        setCounter(handle, SCORE_MOB_KILLS, kills(mobTypes), showCounts = true)
        setCounter(handle, SCORE_ARTHROPOD, kills(arthropodTypes), showCounts = true)

        val made = mutableMapOf<String, Int>()
        for (holder in countedAdvancementsCache.get()) {
            val id = holder.id.toString()
            when {
                // revoked since the baseline (e.g. by Bingo's player reset): counts once it is made again
                !player.advancements.getOrStartProgress(holder).isDone -> baseline.advancements.remove(id)
                id !in baseline.advancements -> made[id] = 1
            }
        }
        setCounter(handle, SCORE_ADVANCEMENTS, made)
    }

    /**
     * Sets every online teammate's score to the team's combined value, so that the scoreboard goal
     * (which uses the best player of the team) counts what the whole team did.
     */
    private fun updateTeamScores() {
        for (team in state.getRegisteredTeams()) {
            val playerIds = team.players.map { it.uuid }
            val online = playerIds.mapNotNull { playerManager.getPlayer(it) }
            if (online.isEmpty()) continue
            for (name in TEAM_SCORES) {
                val combined = details.get(playerIds, name) ?: continue
                val value = if (combined.showCounts) combined.values.values.sum() else combined.values.size
                for (handle in online) setScore(handle, name, value)
            }
        }
    }

    private fun shareTeamCriteria() {
        for (team in state.getRegisteredTeams()) {
            val online = team.players.mapNotNull { playerManager.getPlayer(it.uuid)?.player }
            if (online.size < 2) continue
            for (holder in teamAdvancementsCache.get()) {
                val done = online.flatMap { it.advancements.getOrStartProgress(holder).completedCriteria }.toSet()
                for (player in online) {
                    val progress = player.advancements.getOrStartProgress(holder)
                    if (progress.isDone) continue
                    for (criterion in done) {
                        if (progress.getCriterion(criterion)?.isDone == false) player.advancements.award(holder, criterion)
                    }
                }
            }
        }
    }

    private fun tickPlayer(handle: IPlayerHandle, runChecks: Boolean) {
        val player = handle.player
        startTracking(handle)
        val playerState = playerStates.getValue(player.uuid)
        // taken right away, so that nothing done in the first ticks of the game is part of it
        val baseline = playerState.baseline ?: createBaseline(player).also { playerState.baseline = it }

        if (runChecks) {
            checkEquipment(player, playerState)
            checkInventory(player)
            checkNearbyEntities(player, playerState)
            if (player.foodData.foodLevel <= 0) grant(player, "empty_hunger")
            updateStatCounters(handle, player, baseline)
        }
        updateMaxEffects(player, playerState)
        updateFastCounters(handle, player, playerState)
    }

    init {
        instance = this
        eventBus.register(ScopeStopped) {
            if (instance === this) instance = null
        }

        // create the scoreboards up front, so that "scoreboard" objectives can find them
        for (name in SCORES) score(name)

        // a new game: reset before the countdown, so that nothing from the previous game is on the new card
        // when it starts being checked
        events.onEnter(GameState.COUNTDOWN) { from ->
            if (from != GameState.COUNTDOWN) resetPlayers()
        }

        events.onEnter(GameState.PLAYING) { from ->
            // only reset when a new game starts: not when resuming from POSTGAME, nor when the state is re-entered
            // after loading the world / restarting the server (PLAYING -> PLAYING), which used to clear the counters
            if (from != GameState.POSTGAME && from != GameState.PLAYING) resetPlayers()
        }

        // players that weren't part of the game yet (joining for the first time, or put on a team)
        events.onPlayerJoin { (player) ->
            if (state.state == GameState.PLAYING || state.state == GameState.COUNTDOWN) startTracking(player)
        }
        eventBus.register(TeamChangedEvent) { (player, team) ->
            if (team != null && (state.state == GameState.PLAYING || state.state == GameState.COUNTDOWN)) startTracking(player)
        }

        events.onGameTick {
            if (state.state != GameState.PLAYING) return@onGameTick
            tickCount++
            if (!hasDraftoutGoals()) return@onGameTick
            // Counters are updated every tick (so that the progress on the card is live);
            // the heavier world/inventory/statistics scans run every CHECK_INTERVAL ticks.
            val runChecks = tickCount % CHECK_INTERVAL == 0

            try {
                trackNewTeamMembers()
            } catch (e: Exception) {
                log.error("[DraftoutTriggerController] Error while clearing new team members", e)
            }

            for (handle in playerManager.getPlayers()) {
                if (!teamService.isPlaying(handle) || !handle.player.isAlive) continue
                try {
                    tickPlayer(handle, runChecks)
                } catch (e: Exception) {
                    log.error("[DraftoutTriggerController] Error while checking ${handle.playerName}", e)
                }
            }

            try {
                updateTeamScores()
                if (runChecks) shareTeamCriteria()
            } catch (e: Exception) {
                log.error("[DraftoutTriggerController] Error while updating team scores", e)
            }
        }

        eventBus.register(BreakBlockEvent.After) { event ->
            val blockId = event.blockId ?: return@register
            if (state.state != GameState.PLAYING) return@register
            if (!teamService.isPlaying(event.player)) return@register
            try {
                onBlockBroken(event.player.player, blockId)
            } catch (e: Exception) {
                log.error("[DraftoutTriggerController] Error while handling block break", e)
            }
        }
    }
}
