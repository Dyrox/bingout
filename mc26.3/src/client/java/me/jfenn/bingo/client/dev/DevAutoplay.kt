package me.jfenn.bingo.client.dev

import me.jfenn.bingo.common.dev.DevSignals
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.server.MinecraftServer
import net.minecraft.util.HttpUtil
import org.slf4j.LoggerFactory
import kotlin.io.path.exists

/**
 * Dev test tool, only in development launches with `<game dir>/bingout-dev/autoplay` present:
 * - opens the plain test world [WORLD] from the title screen (creating it the first time; without Bingo's lobby data
 *   pack, so isLobbyMode=false),
 * - publishes it to LAN, so that the integrated server keeps running behind menus (Bingo's screens pause the game),
 * - respawns right away after dying (tests can kill the player, and a test script only starts with a live player),
 * - quits the game when a test script asks to (see DevScriptRunner).
 */
object DevAutoplay {
    private const val WORLD = "BingoutDev"
    private val log = LoggerFactory.getLogger("bingo")

    fun init() {
        val loader = FabricLoader.getInstance()
        if (!loader.isDevelopmentEnvironment) return
        if (!loader.gameDir.resolve("bingout-dev").resolve("autoplay").exists()) return
        log.info("[DevAutoplay] Opening the test world '{}'", WORLD)

        var opened = false
        var published = false
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (DevSignals.exitRequested) {
                DevSignals.exitRequested = false
                log.info("[DevAutoplay] Test script done, quitting")
                mc.stop()
                return@register
            }

            val screen = mc.gui.screen()
            if (!opened && screen is TitleScreen) {
                opened = true
                if (mc.levelSource.levelExists(WORLD)) {
                    mc.createWorldOpenFlows().openWorld(WORLD) { mc.gui.setScreen(TitleScreen()) }
                } else {
                    CreateWorldScreen.openFresh(mc) { mc.gui.setScreen(TitleScreen()) }
                }
            }
            if (screen is CreateWorldScreen) {
                screen.uiState.name = WORLD
                // commands are needed by the test scripts
                screen.uiState.isAllowCommands = true
                Screens.getWidgets(screen)
                    .find { (it.message.contents as? TranslatableContents)?.key == "selectWorld.create" }
                    ?.onClick(MouseButtonEvent(0.0, 0.0, MouseButtonInfo(0, 0)), false)
            }

            if (screen is DeathScreen) {
                mc.player?.respawn()
                mc.gui.setScreen(null)
            }

            val server = mc.singleplayerServer
            if (!published && server != null && mc.player != null) {
                published = server.publishServer(MinecraftServer.MultiplayerScope.LAN, false, HttpUtil.getAvailablePort())
                log.info("[DevAutoplay] Published to LAN: {}", published)
            }
        }
    }
}
