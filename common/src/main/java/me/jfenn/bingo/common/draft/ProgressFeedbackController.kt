package me.jfenn.bingo.common.draft

import me.jfenn.bingo.api.state.GameState
import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.objective.BingoObjective
import me.jfenn.bingo.common.event.ScopedEvents
import me.jfenn.bingo.common.map.CardViewService
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.team.BingoTeam
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.platform.IPlayerManager
import me.jfenn.bingo.platform.PlayerSoundCategory
import me.jfenn.bingo.platform.PlayerSoundEvent
import me.jfenn.bingo.platform.text.Formatting

/**
 * Feedback when a counting goal goes up by one (e.g. "Kill 15 Unique Hostile Mobs" 3 -> 4), like Draftout:
 * a short sound and an action bar message ("<goal>: 4/15"), sent only to the players of the team that made
 * progress (not broadcast). Completing the goal is left to Bingo's own completion feedback.
 *
 * Only "discrete" counters are covered (unique kills/crafts/foods/spied mobs/..., kill counts) - not item stacks
 * or continuous stats (damage, distance, time), which would trigger on every single item/tick.
 */
internal class ProgressFeedbackController(
    events: ScopedEvents,
    private val state: BingoState,
    private val cardViewService: CardViewService,
    private val playerManager: IPlayerManager,
    private val text: TextProvider,
) : BingoComponent() {

    companion object {
        private const val DRAFTOUT_PREFIX = "$MOD_ID_BINGO:draftout/"

        /** Counters that change continuously rather than one event at a time */
        private val CONTINUOUS_SCOREBOARDS = setOf(DraftoutTriggerController.SCORE_PUMPKIN)
    }

    /** Highest count seen so far, per team and objective */
    private val lastCounts = mutableMapOf<Pair<BingoTeamKey, String>, Int>()

    /** Things already counted (e.g. bred animals), to name the new one in the message */
    private val lastDetailIds = mutableMapOf<Pair<BingoTeamKey, String>, Set<String>>()

    private fun isCountedGoal(objective: BingoObjective): Boolean {
        if (!objective.id.startsWith(DRAFTOUT_PREFIX)) return false
        return when (objective) {
            is BingoObjective.ScoreboardEntry -> objective.data.scoreboardName !in CONTINUOUS_SCOREBOARDS
            is BingoObjective.SomeOfEntry -> objective.valueMin > 1
            // e.g. "Kill 100 Mobs"; damage/distance stats are continuous
            is BingoObjective.StatsEntry -> objective.data.statName.orEmpty().endsWith("mob_kills")
            // e.g. "Visit All Nether Biomes" (one criterion per biome)
            is BingoObjective.AdvancementEntry -> true
            else -> false
        }
    }

    private fun soundFor(objective: BingoObjective, count: Int, target: Int): Pair<PlayerSoundEvent, Float> {
        // Draftout: amethyst resonance when spying on a new mob, iron xylophone for other counters.
        // The xylophone pitch rises as the goal gets closer to completion.
        if (objective is BingoObjective.ScoreboardEntry && objective.data.scoreboardName == DraftoutTriggerController.SCORE_SPY) {
            return PlayerSoundEvent.BLOCK_AMETHYST_BLOCK_RESONATE to 1f
        }
        val pitch = 1f + count.toFloat() / target.coerceAtLeast(1)
        return PlayerSoundEvent.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE to pitch.coerceIn(0.5f, 2f)
    }

    private fun checkTeam(team: BingoTeam, card: BingoCard) {
        val seen = mutableSetOf<String>()
        for (entry in card.entries) {
            val objective = card.objectives[entry.objectiveId] ?: continue
            if (!seen.add(objective.id) || !isCountedGoal(objective)) continue
            if (objective.hasTeamAchieved(team.key)) continue
            // in lockout, a goal taken by another team can't be completed anymore
            if (card.options.isLockoutMode && objective.hasAnyAchieved()) continue

            val (count, target) = cardViewService.getProgressCount(team, objective, card) ?: continue
            val key = team.key to objective.id
            val previous = lastCounts[key]
            if (previous == null || count <= previous) {
                // first observation is the baseline; decreases (e.g. items leaving the inventory) are ignored
                lastCounts[key] = maxOf(previous ?: count, count)
                if (previous == null) {
                    lastDetailIds[key] = cardViewService.getProgressDetails(team, objective, card).orEmpty().map { it.id }.toSet()
                }
                continue
            }
            lastCounts[key] = count

            val details = cardViewService.getProgressDetails(team, objective, card).orEmpty()
            val previousIds = lastDetailIds[key].orEmpty()
            val added = details.filter { it.id !in previousIds }
            lastDetailIds[key] = previousIds + details.map { it.id }
            // reaching the target completes the goal, which has its own sound
            if (count >= target) continue

            val message = text.empty()
                .append((entry.display.name ?: objective.display.name ?: text.literal(objective.id)).copy().resetStyle())
                .append(text.literal(": ").formatted(Formatting.GRAY))
                .append(text.literal("$count/$target").formatted(Formatting.GOLD))
            if (added.isNotEmpty()) {
                // e.g. "+ Sheep"
                message.append(text.literal("  + ").formatted(Formatting.GREEN))
                    .append(text.joinText(added.take(3).map { it.name.copy() }).formatted(Formatting.GREEN))
            }
            val (sound, pitch) = soundFor(objective, count, target)

            for (teamPlayer in team.players) {
                val player = playerManager.getPlayer(teamPlayer.uuid) ?: continue
                player.playSound(sound, PlayerSoundCategory.MAIN, 1f, pitch)
                player.sendHotbarMessage(message.copy())
            }
        }
    }

    init {
        events.onEnter(GameState.PLAYING) { from ->
            if (from != GameState.POSTGAME) {
                lastCounts.clear()
                lastDetailIds.clear()
            }
        }

        events.onGameTick {
            if (state.state != GameState.PLAYING) return@onGameTick
            for (team in state.getRegisteredTeams()) {
                val card = state.getCard(team) ?: continue
                checkTeam(team, card)
            }
        }
    }
}
