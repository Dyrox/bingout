package me.jfenn.bingo.client.common.draft

import me.jfenn.bingo.client.common.hud.card.CardTileRenderer
import me.jfenn.bingo.client.common.packet.ClientPacketEvents
import me.jfenn.bingo.client.platform.IClient
import me.jfenn.bingo.client.platform.event.model.ClientServerEvent
import me.jfenn.bingo.client.platform.screen.IScreenFactory
import me.jfenn.bingo.common.draft.DraftPacket
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.platform.event.IEventBus
import net.minecraft.client.gui.screens.Screen
import org.slf4j.Logger

internal class BingoutDraftController(
    private val client: IClient,
    screenFactory: IScreenFactory,
    text: TextProvider,
    packets: ClientPacketEvents,
    renderer: CardTileRenderer,
    eventBus: IEventBus,
    log: Logger,
) {
    private var screen: Screen? = null
    private var view: BingoutDraftScreen? = null

    private fun close() {
        if (screen != null && client.screen === screen) client.screen = null
        forget()
    }

    private fun forget() {
        screen = null
        view = null
    }

    init {
        eventBus.register(packets.draftStateV1) { (packet) ->
            if (!packet.active) {
                close()
            } else {
                if (view == null) {
                    screen = screenFactory.build(text.literal("Draftout")) { helper ->
                        BingoutDraftScreen(helper, text, client, renderer).also { view = it }
                    }
                }
                if (view?.state?.turn != packet.turn) {
                    log.info("[Bingout] Draft pick {}/25, canPick={}, offers={}",
                        packet.picks.size + 1, packet.canPick, packet.offers.map { it.id })
                }
                view?.update(packet)
                if (client.screen !== screen) client.screen = screen
            }
        }
        // Minecraft replaces the screen itself when disconnecting (and this runs on the network thread, where closing
        // it throws "Trying to return to in-game GUI during disconnection")
        eventBus.register(ClientServerEvent.Disconnect) { forget() }
        eventBus.register(packets.gameResetV1) { close() }
    }
}
