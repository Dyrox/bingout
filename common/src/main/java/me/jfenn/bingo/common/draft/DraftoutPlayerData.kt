package me.jfenn.bingo.common.draft

import kotlinx.serialization.Serializable

/**
 * Per-player state of the draftout counters for the current game, saved with the game state so that progress
 * survives leaving and reloading the world / restarting the server (see DraftoutTriggerController).
 *
 * A player having an entry means that their progress belongs to the current game: anything left over from an
 * earlier game (hidden draftout advancements, counter scores) was cleared when the entry was created.
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
) {
    /**
     * What the player had done before the game. A value that drops below the baseline lowers it: Bingo can reset
     * a player's statistics and advancements after the baseline was taken (in lobby mode, players joining a running
     * game or changing team are reset), and things done after that reset must count.
     */
    @Serializable
    class Baseline(
        val crafted: MutableMap<String, Int> = mutableMapOf(),
        val kills: MutableMap<String, Int> = mutableMapOf(),
        /** Vanilla advancements already made before the game ("Get N Advancements" counts the others) */
        val advancements: MutableSet<String> = mutableSetOf(),
    )
}
