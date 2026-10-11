package me.jfenn.bingo.integrations.waystones

import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.game.GameResetEvent
import net.blay09.mods.waystones.api.WaystonesAPI
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger

internal interface IWaystonesController

internal object WaystonesControllerStub : IWaystonesController

internal class WaystonesController(
    private val log: Logger,
    private val server: MinecraftServer,
    events: IEventBus,
) : IWaystonesController {
    init {
        events.register(GameResetEvent.After) {
            log.info("[WaystonesPlugin] Game resetting...")

            WaystonesAPI.getAllWaystones(server)
                .toList()
                .forEach {
                    log.debug("[WaystonesPlugin] Removing waystone {}", it.waystoneUid)
                    WaystonesAPI.removeWaystoneFromDatabase(server, it)
                }
        }
    }
}

internal fun createWaystonesController(
    log: Logger,
    server: MinecraftServer,
    events: IEventBus,
    environment: IModEnvironment
): IWaystonesController {
    return if (environment.isModLoaded("waystones")) {
        WaystonesController(log, server, events)
    } else WaystonesControllerStub
}
