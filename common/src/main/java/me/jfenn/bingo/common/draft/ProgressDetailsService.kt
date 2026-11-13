package me.jfenn.bingo.common.draft

import java.util.*

/**
 * Remembers *which* things each player has done for the draftout counters (e.g. which animals were bred,
 * which foods were eaten), so that the card tooltip can list them. Filled by [DraftoutTriggerController].
 *
 * Values are registry ids (entities, items, mob effects, ...) or advancement ids, each with a count.
 */
internal class ProgressDetailsService {

    class Details(
        val values: Map<String, Int>,
        /** Whether the counts are meaningful (e.g. "Zombie x12"), rather than each value counting once */
        val showCounts: Boolean,
    )

    private val players = mutableMapOf<UUID, MutableMap<String, Details>>()

    fun set(player: UUID, scoreboardName: String, values: Map<String, Int>, showCounts: Boolean = false) {
        players.getOrPut(player) { mutableMapOf() }[scoreboardName] = Details(values, showCounts)
    }

    /** Combined details of the given players (e.g. a team) for one counter */
    fun get(playerIds: Iterable<UUID>, scoreboardName: String): Details? {
        val all = playerIds.mapNotNull { players[it]?.get(scoreboardName) }
        if (all.isEmpty()) return null
        val merged = linkedMapOf<String, Int>()
        for (details in all) {
            for ((id, count) in details.values) merged.merge(id, count, Int::plus)
        }
        return Details(merged, all.any { it.showCounts })
    }

    fun clear() = players.clear()
}
