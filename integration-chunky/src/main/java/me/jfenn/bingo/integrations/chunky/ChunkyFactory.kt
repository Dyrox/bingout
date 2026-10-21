package me.jfenn.bingo.integrations.chunky

import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.IServerWorldFactory
import me.jfenn.bingo.platform.config.IConfigManager
import me.jfenn.bingo.platform.config.read
import me.jfenn.bingo.platform.config.write
import org.slf4j.Logger

internal object ChunkyFactory {
    private const val CONFIG_PATH = "yet-another-minecraft-bingo/chunky.json"

    fun create(
        environment: IModEnvironment,
        configManager: IConfigManager,
        serverWorldFactory: IServerWorldFactory,
        state: BingoState,
        log: Logger,
    ): IChunkyApi {
        return if (state.isLobbyMode && environment.isModLoaded("chunky")) {
            val config = try {
                configManager.read(CONFIG_PATH)
            } catch (e: Throwable) {
                ChunkyConfig()
            }
            configManager.write(CONFIG_PATH, config)

            ChunkyImpl(
                config = config,
                serverWorldFactory = serverWorldFactory,
                log = log,
            )
        } else {
            DummyChunky
        }
    }
}