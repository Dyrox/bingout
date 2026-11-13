package me.jfenn.bingo.platform.event.model

import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.IServerWorld
import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.event.IEvent
import net.minecraft.server.MinecraftServer

class BreakBlockEvent(
    val server: MinecraftServer,
    val player: IPlayerHandle,
    val world: IServerWorld,
    val blockPos: BlockPosition,
    /** Registry id of the block that was broken (e.g. "minecraft:obsidian"); the world is already air at this point */
    val blockId: String? = null,
) {
    companion object After : IEvent<BreakBlockEvent>
}
