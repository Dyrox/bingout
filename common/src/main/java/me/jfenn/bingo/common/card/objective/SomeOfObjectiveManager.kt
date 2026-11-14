package me.jfenn.bingo.common.card.objective

import me.jfenn.bingo.common.card.BingoCard
import me.jfenn.bingo.common.card.CardGeneratorState
import me.jfenn.bingo.common.card.TagExpansionService
import me.jfenn.bingo.common.card.data.ObjectiveData
import me.jfenn.bingo.common.card.data.rootObjectives
import me.jfenn.bingo.common.card.objective.display.Decoration
import me.jfenn.bingo.common.card.objective.display.ItemStyle
import me.jfenn.bingo.common.card.objective.display.ObjectiveDisplay
import me.jfenn.bingo.common.card.objective.display.ObjectiveDisplay.Companion.FORMAT_MAX
import me.jfenn.bingo.common.card.objective.display.ObjectiveDisplay.Companion.FORMAT_MIN
import me.jfenn.bingo.common.card.objective.display.ObjectiveDisplayService
import me.jfenn.bingo.common.data.ScopedData
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.generated.StringKey
import org.slf4j.Logger
import java.util.*

internal class SomeOfObjectiveManager(
    private val log: Logger,
    private val data: ScopedData,
    private val objectiveDisplayService: ObjectiveDisplayService,
    private val objectiveService: ObjectiveService,
    private val tagExpansionService: TagExpansionService,
    private val text: TextProvider,
) : IObjectiveManager {
    override fun list(): Iterable<String> {
        return data.objectives.rootObjectives
            .filter { (_, it) -> it is ObjectiveData.SomeOfBase }
            .map { it.key }
            .toList()
    }

    override fun find(id: String, state: CardGeneratorState): BingoObjective? {
        val data = data.objectives[id]
        if (data !is ObjectiveData.SomeOfBase)
            return null

        val someOfObjectives = data.objectives
            .map { data.expandReference(id, it) }
            .let { tagExpansionService.expandItemTags(it) }

        if (someOfObjectives.isEmpty()) {
            log.error("[SomeOfObjectiveManager] $id 'objectives' cannot be empty!")
            return null
        }

        return BingoObjective.SomeOfEntry(
            id = id,
            data = data,
            someOfObjectives = someOfObjectives,
            valueMin = data.min.resolve(state).coerceIn(0, someOfObjectives.size),
            valueMax = data.max.resolve(state).coerceIn(0, someOfObjectives.size),
        )
    }

    override fun init(card: BingoCard) {
        for (objective in card.objectivesByInstance<BingoObjective.SomeOfEntry>()) {
            val objectives = objective.someOfObjectives.mapNotNull { card.objectives[it] }

            val isOneOf = objective.valueMin == 1 && objective.valueMax >= objective.someOfObjectives.size
            val isAllOf = objective.valueMin == objective.someOfObjectives.size
            val contentsName = text.empty().append(objectives.firstOrNull()?.display?.name ?: text.empty()).append(", …")
            val display = ObjectiveDisplay.Resolved(
                name = when {
                    isOneOf -> text.string(StringKey.ObjectiveOneOf, contentsName)
                    isAllOf -> text.string(StringKey.ObjectiveAllOf, contentsName)
                    else -> text.string(StringKey.ObjectiveSomeOf, FORMAT_MIN, contentsName)
                },
                item = objectives.firstNotNullOfOrNull { it.display.item },
                itemStyle = when {
                    isOneOf -> ItemStyle.ONE_OF
                    else -> ItemStyle.ALL_OF
                },
                decoration = Decoration.MULTI_ITEM,
            )

            objective.display = objectiveDisplayService.resolve(
                id = objective.id,
                data = objective.data.display,
                fallback = display,
                substitutions = mapOf(
                    FORMAT_MIN to objective.valueMin.toString(),
                    FORMAT_MAX to objective.valueMax.toString(),
                ),
            )
        }
    }

    /** A single player must satisfy enough of the inner objectives at once */
    private fun tickHolding(
        card: BingoCard,
        objective: BingoObjective.SomeOfEntry,
        objectives: List<BingoObjective>,
    ): Pair<Map<BingoTeamKey, Float>, Map<UUID, BingoObjectiveCapture>> {
        val capturesByPlayer = objectives
            .flatMap { inner -> inner.playersHolding.entries.map { inner.id to it } }
            .groupBy({ it.second.key }, { it.first to it.second.value })

        val progressTotal = objective.valueMin.coerceIn(1, objectives.size).toFloat()

        val teamsCounted = mutableMapOf<BingoTeamKey, List<String>>()
        val teamsProgress = objectiveService.getPlayersByTeam(card)
            .mapKeys { it.key.key }
            .mapValues { (team, players) ->
                val best = players.map { capturesByPlayer[it.uuid].orEmpty() }.maxByOrNull { it.size }.orEmpty()
                teamsCounted[team] = best.map { it.first }
                best.size / progressTotal
            }
        objective.teamsCounted = teamsCounted

        // ensure that the minimum value is at most the # of successfully loaded objectives
        val valueMin = objective.valueMin.coerceIn(0, objectives.size)
        val playersHolding = capturesByPlayer
            .filter { it.value.size in valueMin..objective.valueMax } // in order to capture, must be holding every objective
            .mapValues { (_, captures) ->
                captures.map { it.second }
                    .sortedBy { it.instant }[valueMin.coerceAtLeast(1) - 1]
            }
            .let {
                when {
                    objective.data.permanent -> it + objective.playersHolding
                    else -> it
                }
            }
        return teamsProgress to playersHolding
    }

    /**
     * Inner objectives add up over time and across the team: everything any teammate has satisfied so far counts,
     * even if they no longer hold it (e.g. "Obtain 3 Unique Banner Patterns" with patterns obtained one by one).
     */
    private fun tickCumulative(
        card: BingoCard,
        objective: BingoObjective.SomeOfEntry,
        objectives: List<BingoObjective>,
    ): Pair<Map<BingoTeamKey, Float>, Map<UUID, BingoObjectiveCapture>> {
        // remember what each player has satisfied (inner objectives' own captures can't be used: in lockout mode,
        // they only keep the first team, while the goal is about what each team has done)
        for (inner in objectives) {
            for ((uuid, capture) in inner.playersHolding) {
                objective.playersObtained.getOrPut(uuid) { mutableMapOf() }.putIfAbsent(inner.id, capture)
            }
        }

        val innerIds = objectives.map { it.id }.toSet()
        val valueMin = objective.valueMin.coerceIn(1, objectives.size.coerceAtLeast(1))

        val teamsCounted = mutableMapOf<BingoTeamKey, List<String>>()
        val teamsProgress = mutableMapOf<BingoTeamKey, Float>()
        val playersHolding = mutableMapOf<UUID, BingoObjectiveCapture>()
        for ((team, players) in objectiveService.getPlayersByTeam(card)) {
            // the team's first capture of each inner objective, oldest first
            val captures = players
                .flatMap { objective.playersObtained[it.uuid].orEmpty().entries }
                .filter { it.key in innerIds }
                .groupBy({ it.key }, { it.value })
                .mapValues { (_, it) -> it.minBy { capture -> capture.instant } }
                .entries.sortedBy { it.value.instant }

            teamsCounted[team.key] = captures.map { it.key }
            teamsProgress[team.key] = captures.size / valueMin.toFloat()
            if (captures.size >= valueMin) {
                // credited to the player that completed the goal, at the time they did
                val completing = captures[valueMin - 1].value
                playersHolding[completing.player.uuid] = completing.copy(team = team.key)
            }
        }
        objective.teamsCounted = teamsCounted
        return teamsProgress to playersHolding
    }

    override fun tick(card: BingoCard) {
        for (objective in card.objectivesByInstance<BingoObjective.SomeOfEntry>()) {
            val objectives = objective.someOfObjectives.mapNotNull { card.objectives[it] }

            objectiveService.updateTeamsOnceAchieved(objective)

            objectiveService.updateTeamsSeen(card, objective) { team ->
                objectives.any { it.hasSeen(team) }
            }

            val (teamsProgress, playersHolding) = when {
                objective.isCumulative && !card.options.isInventoryMode -> tickCumulative(card, objective, objectives)
                else -> tickHolding(card, objective, objectives)
            }

            val players = objectiveService.getPlayers(
                card = card,
                playersHolding = playersHolding,
                players = objective.players,
            )

            val teams = objectiveService.getTeams(
                players = players
            )

            objectiveService.update(
                objective = objective,
                teamsProgress = teamsProgress,
                playersHolding = playersHolding,
                players = players,
                teams = teams
            )
        }
    }
}