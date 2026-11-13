package me.jfenn.bingo.platform

import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.text.IText
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

interface IAdvancementManager {

    fun listAdvancements(server: MinecraftServer): List<String>

    fun getAdvancement(server: MinecraftServer, id: String): IAdvancementHandle?

    fun getProgress(player: ServerPlayer, advancement: IAdvancementHandle): Float

    fun isAnyObtained(player: ServerPlayer, advancement: IAdvancementHandle): Boolean

    fun isDone(player: ServerPlayer, advancement: IAdvancementHandle): Boolean

    fun clearAdvancements(player: ServerPlayer)

    /** Names of the criteria the player has completed for this advancement */
    fun getCompletedCriteria(player: ServerPlayer, advancement: IAdvancementHandle): List<String> = emptyList()

}

interface IAdvancementHandle {
    val name: IText?
    val description: IText?
    val displayIcon: IItemStack?
    /** Number of requirement groups that must all be met (e.g. 5 for "visit all 5 nether biomes") */
    val requirementCount: Int get() = 1
}
