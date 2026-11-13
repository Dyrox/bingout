package me.jfenn.bingo.platform.event.model

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.event.IEvent
import net.minecraft.world.damagesource.DamageSource

/**
 * Emitted after a player has died (after their combat tracker has recorded the death).
 */
data class PlayerDeathEvent(
    val player: IPlayerHandle,
    val source: DamageSource,
) {
    companion object : IEvent<PlayerDeathEvent>
}
