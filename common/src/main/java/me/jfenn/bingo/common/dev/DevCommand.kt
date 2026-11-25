package me.jfenn.bingo.common.dev

import me.jfenn.bingo.api.state.GameState
import me.jfenn.bingo.common.NBT_BINGO_IGNORE
import me.jfenn.bingo.common.Permission
import me.jfenn.bingo.common.commands.hasPermission
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.draft.DraftoutTriggerController
import me.jfenn.bingo.common.map.CardViewService
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.OfflinePlayerCache
import me.jfenn.bingo.common.team.TeamService
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.platform.IAdvancementManager
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.IStatManager
import me.jfenn.bingo.platform.commands.ICommandManager
import me.jfenn.bingo.platform.commands.IExecutionContext
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.item.IItemStackFactory
import me.jfenn.bingo.platform.player.PlayerProfile
import me.jfenn.bingo.platform.scoreboard.IScoreboardManager
import net.minecraft.core.BlockPos
import net.minecraft.core.UUIDUtil
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.stats.Stats
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.inventory.CartographyTableMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.MapItem
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.slf4j.Logger

/**
 * `/bingo dev ...`: test helpers, only registered in development launches (see [DevScriptRunner]).
 *
 * They set up situations that are slow to reach by playing (offline team members, statistics reset by Bingo, a pearl
 * landing in another dimension, ...) and check the results (`expect ...`, tallied by the script runner).
 */
internal class DevCommand(
    commandManager: ICommandManager,
    environment: IModEnvironment,
    private val text: TextProvider,
    private val log: Logger,
) {
    private fun ids(raw: String) = raw.trim().let { if (':' in it) it else DraftoutTriggerController.DRAFTOUT_PREFIX + it }

    private val IExecutionContext.state get() = scope.get<BingoState>()

    private fun IExecutionContext.findTeam(name: String, create: Boolean = false): BingoTeam {
        state.teams.values.find { it.key.id == name || it.key.label == name }?.let { return it }
        if (!create) error(text.literal("No team '$name'"))
        val (key, preset) = scope.get<ScopedData>().teamPresets.entries.find { it.key.id == name || it.key.label == name }
            ?: error(text.literal("No team preset '$name'"))
        scope.get<TeamService>().restoreTeam(BingoTeam.fromPreset(key, preset))
        return state.teams.values.first { it.key == key }
    }

    /** An online player, or a (possibly made-up) offline one ("me": the player running the command) */
    private fun IExecutionContext.findPlayer(name: String): IPlayerHandle {
        if (name == "me") return playerOrThrow
        scope.get<IPlayerManager>().getPlayers().find { it.playerName == name }?.let { return it }
        val profile = state.teams.values.flatMap { it.players }.find { it.name == name }?.profile
            ?: PlayerProfile(UUIDUtil.createOfflinePlayerUUID(name), name)
        return scope.get<OfflinePlayerCache>().getOfflinePlayer(profile)
    }

    private fun IExecutionContext.expect(pass: Boolean, message: String) {
        scope.get<DevScriptRunner>().record(pass, message)
        sendFeedback(text.literal((if (pass) "PASS " else "FAIL ") + message))
    }

    private fun IExecutionContext.findObjective(teamName: String, rawId: String) =
        findTeam(teamName).let { team ->
            val card = state.getCard(team) ?: error(text.literal("Team $teamName has no card"))
            val objective = card.objectives[ids(rawId)] ?: error(text.literal("${ids(rawId)} is not on the card"))
            Triple(team, card, objective)
        }

    private fun IExecutionContext.report() {
        val cardViewService = scope.get<CardViewService>()
        for (team in state.getRegisteredTeams()) {
            val card = state.getCard(team) ?: continue
            for (entry in card.entries) {
                val objective = card.objectives[entry.objectiveId] ?: continue
                val progress = cardViewService.getProgressCount(team, objective, card)
                log.info(
                    "[bingout-dev] REPORT team={} {} achieved={} progress={}",
                    team.key.label, objective.id, objective.hasTeamAchieved(team.key), progress,
                )
            }
        }
        sendFeedback(text.literal("Card report written to the log"))
    }

    init {
        if (environment.isDevelopment) commandManager.register("bingo") {
            literal("dev") {
                requires { hasPermission(Permission.COMMAND_DEBUG) }

                // join a team yourself
                literal("team") {
                    string("team") { teamArg ->
                        executes {
                            scope.get<TeamService>().joinTeam(playerOrThrow, findTeam(getArgument(teamArg), create = true))
                        }
                    }
                }

                // a team member that is offline (never joins): their saved progress is what the card reads
                literal("fake") {
                    string("team") { teamArg ->
                        string("name") { nameArg ->
                            executes {
                                val name = getArgument(nameArg)
                                findTeam(getArgument(teamArg), create = true)
                                    .addPlayer(PlayerProfile(UUIDUtil.createOfflinePlayerUUID(name), name))
                                sendFeedback(text.literal("Added offline player $name"))
                            }
                        }
                    }
                }

                // complete an advancement (also for offline players, e.g. "progress from an earlier game")
                literal("grant") {
                    string("player") { playerArg ->
                        string("advancement", greedy = true) { advancementArg ->
                            executes {
                                val handle = findPlayer(getArgument(playerArg))
                                val id = ids(getArgument(advancementArg))
                                val holder = server.advancements.get(Identifier.parse(id)) ?: error(text.literal("No advancement $id"))
                                val advancements = handle.player.advancements
                                for (criterion in holder.value.criteria().keys) advancements.award(holder, criterion)
                                if (scope.get<IPlayerManager>().getPlayer(handle.uuid) == null) advancements.save()
                                sendFeedback(text.literal("Granted $id to ${handle.playerName}"))
                            }
                        }
                    }
                }

                // add to your statistics, the way crafting/killing does
                literal("stat") {
                    string("kind") { kindArg ->
                        integer("amount", min = 1) { amountArg ->
                            string("id", greedy = true) { idArg ->
                                executes {
                                    val player = playerOrThrow.player
                                    val id = Identifier.parse(getArgument(idArg).trim())
                                    val stat = when (getArgument(kindArg)) {
                                        "crafted" -> Stats.ITEM_CRAFTED.get(BuiltInRegistries.ITEM.getValue(id))
                                        "killed" -> Stats.ENTITY_KILLED.get(BuiltInRegistries.ENTITY_TYPE.getValue(id))
                                        else -> error(text.literal("Unknown statistic kind (crafted, killed)"))
                                    }
                                    player.awardStat(stat, getArgument(amountArg))
                                }
                            }
                        }
                    }
                }

                // what Bingo does to players joining a running game in lobby mode
                literal("resetplayer") {
                    executes {
                        val handle = playerOrThrow
                        scope.get<IStatManager>().list().forEach { it.reset(handle) }
                        scope.get<IAdvancementManager>().clearAdvancements(handle.player)
                    }
                }

                // use the held item like a right click (e.g. shoot a loaded crossbow, fill an empty map)
                literal("use") {
                    executes {
                        val player = playerOrThrow.player
                        player.gameMode.useItem(player, player.level(), player.mainHandItem, InteractionHand.MAIN_HAND)
                    }
                }

                // back to PREGAME from any state (e.g. a draft left over from an earlier test run)
                literal("abort") {
                    executes {
                        if (state.state != GameState.PREGAME) {
                            state.reset()
                            state.changeState(scope.get<IEventBus>(), GameState.PREGAME)
                        }
                    }
                }

                // lock the first unlocked map (filled or explorer map) in your inventory with a glass pane in a
                // cartography table menu, shift-clicking the result like a player
                literal("lockmap") {
                    executes {
                        val player = playerOrThrow.player
                        val inventory = player.inventory
                        val slot = (0 until inventory.containerSize).firstOrNull {
                            val stack = inventory.getItem(it)
                            stack.has(DataComponents.MAP_ID) && MapItem.getSavedData(stack, player.level())?.locked == false
                        } ?: error(text.literal("No unlocked map in your inventory"))
                        val map = inventory.removeItem(slot, 1)
                        log.info("[bingout-dev] Locking {} {}", BuiltInRegistries.ITEM.getKey(map.item), map.get(DataComponents.MAP_ID))
                        val access = ContainerLevelAccess.create(player.level(), player.blockPosition())
                        player.openMenu(SimpleMenuProvider({ id, inv, _ -> CartographyTableMenu(id, inv, access) }, Component.empty()))
                        val menu = player.containerMenu as? CartographyTableMenu ?: error(text.literal("Couldn't open a cartography table"))
                        menu.getSlot(0).set(map)
                        menu.getSlot(1).set(ItemStack(Items.GLASS_PANE))
                        if (!menu.getSlot(2).hasItem()) error(text.literal("The cartography table has no result"))
                        menu.quickMoveStack(player, 2)
                        player.closeContainer()
                    }
                }

                // a memento card like the one Bingo gives after a game: your team's (locked) card map, tagged
                // bingo_ignore (the real one needs the game over info)
                literal("memento") {
                    executes {
                        val player = playerOrThrow
                        val teamService = scope.get<TeamService>()
                        val team = teamService.getPlayerTeam(player) ?: error(text.literal("You're not in a team"))
                        val stack = scope.get<IItemStackFactory>().createFilledMap().apply {
                            mapId = teamService.getTeamMap(team).mapId
                            addCustomTag(NBT_BINGO_IGNORE)
                        }
                        player.giveItemStack(stack)
                        val map = player.player.inventory.let { inv -> (0 until inv.containerSize).map { inv.getItem(it) } }
                            .firstOrNull { it.has(DataComponents.MAP_ID) }
                        log.info("[bingout-dev] Memento card given, locked map: {}", map?.let { MapItem.getSavedData(it, player.player.level())?.locked })
                    }
                }

                // land one of your ender pearls in a dimension (on a platform at y=100, in a force-loaded chunk so that
                // the pearl ticks: in a real game, the portal it went through keeps the chunk loaded)
                literal("pearl") {
                    string("dimension", greedy = true) { dimensionArg ->
                        executes {
                            val player = playerOrThrow.player
                            val key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(getArgument(dimensionArg).trim()))
                            val level = server.getLevel(key) ?: error(text.literal("No dimension $key"))
                            val base = BlockPos(player.blockX, 100, player.blockZ)
                            level.setChunkForced(base.x shr 4, base.z shr 4, true)
                            for (pos in BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, 4, 1))) {
                                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())
                            }
                            level.setBlockAndUpdate(base.below(), Blocks.OBSIDIAN.defaultBlockState())
                            val pearl = ThrownEnderpearl(level, player, ItemStack(Items.ENDER_PEARL))
                            pearl.setPos(base.x + 0.5, base.y + 1.5, base.z + 0.5)
                            pearl.setDeltaMovement(0.0, -0.5, 0.0)
                            level.addFreshEntity(pearl)
                        }
                    }
                }

                // summon a mob next to you, on your lead
                literal("leash") {
                    string("type", greedy = true) { typeArg ->
                        executes {
                            val player = playerOrThrow.player
                            val type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(getArgument(typeArg).trim()))
                            val mob = type.create(player.level(), EntitySpawnReason.COMMAND) as? Mob
                                ?: error(text.literal("Not a mob: $type"))
                            mob.setPos(player.x + 1.5, player.y, player.z)
                            mob.setNoAi(true)
                            player.level().addFreshEntity(mob)
                            mob.setLeashedTo(player, true)
                        }
                    }
                }

                // right-click the nearest mob with the held item (e.g. a name tag)
                literal("interact") {
                    executes {
                        val player = playerOrThrow.player
                        val target = player.level().getEntitiesOfClass(LivingEntity::class.java, player.boundingBox.inflate(5.0)) { it !== player }
                            .minByOrNull { it.distanceToSqr(player) }
                            ?: error(text.literal("No mob nearby"))
                        player.interactOn(target, InteractionHand.MAIN_HAND, Vec3.ZERO)
                    }
                }

                literal("report") {
                    executes { report() }
                }

                literal("expect") {
                    literal("achieved") {
                        string("team") { teamArg ->
                            boolean("value") { valueArg ->
                                string("objective", greedy = true) { objectiveArg ->
                                    executes {
                                        val (team, _, objective) = findObjective(getArgument(teamArg), getArgument(objectiveArg))
                                        val actual = objective.hasTeamAchieved(team.key)
                                        expect(actual == getArgument(valueArg), "achieved ${team.key.label} ${objective.id} = $actual")
                                    }
                                }
                            }
                        }
                    }

                    literal("progress") {
                        string("team") { teamArg ->
                            integer("count", min = 0) { countArg ->
                                string("objective", greedy = true) { objectiveArg ->
                                    executes {
                                        val (team, card, objective) = findObjective(getArgument(teamArg), getArgument(objectiveArg))
                                        val actual = scope.get<CardViewService>().getProgressCount(team, objective, card)?.first
                                        expect(actual == getArgument(countArg), "progress ${team.key.label} ${objective.id} = $actual (expected ${getArgument(countArg)})")
                                    }
                                }
                            }
                        }
                    }

                    literal("advancement") {
                        string("player") { playerArg ->
                            boolean("value") { valueArg ->
                                string("advancement", greedy = true) { advancementArg ->
                                    executes {
                                        val handle = findPlayer(getArgument(playerArg))
                                        val id = ids(getArgument(advancementArg))
                                        val holder = server.advancements.get(Identifier.parse(id)) ?: error(text.literal("No advancement $id"))
                                        val actual = handle.player.advancements.getOrStartProgress(holder).isDone
                                        expect(actual == getArgument(valueArg), "advancement ${handle.playerName} $id = $actual")
                                    }
                                }
                            }
                        }
                    }

                    literal("score") {
                        string("player") { playerArg ->
                            integer("value", min = 0) { valueArg ->
                                string("scoreboard", greedy = true) { scoreboardArg ->
                                    executes {
                                        val handle = findPlayer(getArgument(playerArg))
                                        val name = getArgument(scoreboardArg).trim()
                                        val actual = scope.get<IScoreboardManager>().getByName(name)?.getForPlayer(handle) ?: 0
                                        expect(actual == getArgument(valueArg), "score ${handle.playerName} $name = $actual (expected ${getArgument(valueArg)})")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
