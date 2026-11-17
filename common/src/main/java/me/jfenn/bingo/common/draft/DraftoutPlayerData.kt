package me.jfenn.bingo.common.draft

import kotlinx.serialization.Serializable

/**
 * Per-player state of the draftout counters for the current game, saved with the game state so that progress
 * survives leaving and reloading the world / restarting the server (see DraftoutTriggerController).
 */
@Serializable
internal class DraftoutPlayerData(
    /** Statistics at the start of the game, so that counts are relative to it (registry id -> value) */
    var baseline: Baseline? = null,
    var maxEffects: Int = 0,
    var maxEffectIds: Map<String, Int> = emptyMap(),
    var pumpkinTicks: Int = 0,
    var maxLeashed: Int = 0,
    var maxLeashedIds: Map<String, Int> = emptyMap(),
    var lastDimension: String? = null,
) {
    @Serializable
    class Baseline(
        val crafted: Map<String, Int> = emptyMap(),
        val kills: Map<String, Int> = emptyMap(),
    )
}
