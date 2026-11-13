package me.jfenn.bingo.client.common.draft

import me.jfenn.bingo.client.common.hud.card.CardTileRenderer
import me.jfenn.bingo.client.platform.IClient
import org.slf4j.LoggerFactory
import me.jfenn.bingo.client.platform.renderer.IDrawService
import me.jfenn.bingo.client.platform.renderer.use
import me.jfenn.bingo.client.platform.screen.IMutableScreenHelper
import me.jfenn.bingo.client.platform.screen.IKeyInput
import me.jfenn.bingo.client.platform.screen.IScreen
import me.jfenn.bingo.common.draft.DraftActionPacket
import me.jfenn.bingo.common.draft.DraftPacket
import me.jfenn.bingo.common.map.CardTile
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.Identifier
import me.jfenn.bingo.common.MOD_ID_BINGO
import kotlin.math.ceil
import kotlin.math.min

/** Compact icon choices and a shared 5x5 board, inspired by Draftout's DraftScreen. */
internal class BingoutDraftScreen(
    private val helper: IMutableScreenHelper,
    private val text: TextProvider,
    private val client: IClient,
    private val tiles: CardTileRenderer,
) : IScreen {
    var state = DraftPacket()
        private set
    private var receivedAt = System.nanoTime()
    private var pendingTurn: String? = null
    private var sentAt = 0L
    private val log = LoggerFactory.getLogger("bingo")
    private var lastClickAt = 0L
    private val scale get() = min(1.5f, min((helper.height - 16) / 310f, (helper.width - 16) / 300f)).coerceAtLeast(.1f)
    private val left get() = helper.width / 2f
    private val top get() = (helper.height - 310 * scale) / 2f

    fun update(packet: DraftPacket) {
        state = packet
        receivedAt = System.nanoTime()
        if (pendingTurn != packet.turn || System.nanoTime() - sentAt > 1_000_000_000L) pendingTurn = null
    }

    private val canAct get() = state.canPick && pendingTurn == null
    private fun hit(x: Double, y: Double, rx: Int, ry: Int, w: Int, h: Int) =
        x >= rx && x < rx + w && y >= ry && y < ry + h

    override fun shouldCloseOnEsc() = false
    override fun getBlurriness() = 0f

    override fun render(drawService: IDrawService, mouseX: Int, mouseY: Int, delta: Float) {
        val d = drawService

        d.fill(0, 0, helper.width, helper.height, 0x990b1019.toInt())
        val mx = (mouseX - left) / scale
        val my = (mouseY - top) / scale
        var tooltip: List<IText>? = null
        val remaining = (state.ticksRemaining / 20.0 - (System.nanoTime() - receivedAt) / 1e9).coerceAtLeast(0.0)
        val accent = if (state.canPick) 0xff79df8b.toInt() else 0xff8db9eb.toInt()

        d.matrices.use {
            translate(left, top, 0f)
            scale(scale, scale, 1f)

            fun centered(label: IText, x: Int, y: Int, color: Int = -1) {
                d.drawText(label, x - d.font.getTextWidth(label) / 2, y, color, true)
            }
            fun panel(x: Int, y: Int, w: Int, h: Int, fill: Int = 0xee535963.toInt()) {
                d.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xff11151b.toInt())
                d.fill(x, y, x + w, y + h, fill)
                d.fill(x, y, x + w, y + 1, 0xffd3d7dd.toInt())
                d.fill(x, y, x + 1, y + h, 0xffd3d7dd.toInt())
                d.fill(x, y + h - 1, x + w, y + h, 0xff272c34.toInt())
                d.fill(x + w - 1, y, x + w, y + h, 0xff272c34.toInt())
            }
            fun slot(tile: CardTile?, x: Int, y: Int, size: Int, selectable: Boolean = false, next: Boolean = false) {
                val hovered = hit(mx.toDouble(), my.toDouble(), x, y, size, size)
                d.fill(x, y, x + size, y + size,
                    if (next || (hovered && selectable && canAct)) accent else 0xff272d36.toInt())
                d.fill(x + 1, y + 1, x + size - 1, y + size - 1,
                    if (hovered && tile != null) 0xff7b8795.toInt() else 0xff414953.toInt())
                d.fill(x + 1, y + size - 1, x + size, y + size, 0xffb3bac3.toInt())
                d.fill(x + size - 1, y + 1, x + size, y + size, 0xffb3bac3.toInt())
                if (tile != null) {
                    d.matrices.use {
                        translate(x + 3f, y + 3f, 0f)
                        val iconScale = (size - 6) / 16f
                        scale(iconScale, iconScale, 1f)
                        tiles.renderTile(d, tile, 0, 0)
                    }
                    if (hovered) tooltip = listOfNotNull(tile.name) + tile.lore
                }
            }

            d.matrices.use {
                scale(2.4f, 2.4f, 1f)
                centered(text.literal("DRAFTOUT"), 0, 0)
            }
            centered(text.string(StringKey.DraftPickNumber, state.picks.size + 1, 25), 0, 28, 0xffc2cbd6.toInt())
            panel(-118, 46, 236, 26, if (state.canPick) 0xe52b5339.toInt() else 0xe52d4159.toInt())
            val status = if (state.canPick) text.string(StringKey.DraftYourTurn, ceil(remaining).toInt())
                else text.string(StringKey.DraftWaiting, state.teams.getOrNull(state.currentTeam) ?: text.empty(), ceil(remaining).toInt())
            // Fit long team names without spilling into the rest of the interface.
            d.matrices.use {
                translate(0f, 55f, 0f)
                val statusScale = min(1f, 220f / d.font.getTextWidth(status).coerceAtLeast(1))
                scale(statusScale, statusScale, 1f)
                centered(status, 0, 0)
            }
            d.fill(-118, 73, 118, 76, 0xff202832.toInt())
            d.fill(-118, 73, -118 + (236 * remaining / 10).toInt().coerceIn(0, 236), 76, accent)

            panel(-78, 87, 156, 46)
            state.offers.forEachIndexed { i, tile -> slot(tile, if (i == 0) -69 else 33, 92, 36, selectable = true) }
            centered(text.string(StringKey.DraftOr), 0, 106)
            // Both teams' reroll availability stays visible. Only the current team can use its button.
            for (i in state.teams.indices) {
                val x = if (i == 0) -116 else 88
                val enabled = canAct && state.currentTeam == i && state.rerolls.getOrElse(i) { 0 } > 0
                panel(x, 96, 28, 28, if (enabled) 0xee536956.toInt() else 0xee343b45.toInt())
                d.drawGuiTexture(
                    Identifier.of(MOD_ID_BINGO, "draft/" + if (state.rerolls.getOrElse(i) { 0 } > 0) "reroll" else "reroll_used"),
                    x = x + 7, y = 98, u = 0f, v = 0f,
                    width = 14, height = 14, textureWidth = 14, textureHeight = 14,
                )
                centered(text.literal("${state.rerolls.getOrElse(i) { 0 }}"), x + 14, 114, if (enabled) accent else 0xff818a97.toInt())
                if (hit(mx.toDouble(), my.toDouble(), x, 96, 28, 28)) {
                    tooltip = listOf(state.teams[i], text.string(StringKey.DraftReroll, state.rerolls.getOrElse(i) { 0 }))
                }
            }

            if (state.canPick) {
                d.matrices.use {
                    translate(0f, 135f, 0f)
                    scale(0.5f, 0.5f, 1f)
                    centered(text.literal("[1] / [2] / [R]"), 0, 0, 0xff9aa4b1.toInt())
                }
            }
            panel(-80, 143, 160, 160)
            for (i in 0 until 25) {
                slot(state.picks.getOrNull(i), -75 + (i % 5) * 30, 148 + (i / 5) * 30, 30, next = i == state.picks.size)
            }
        }
        tooltip?.let { d.drawTooltip(it, mouseX, mouseY) }
    }

    /** Returns the option under the cursor (0, 1, or REROLL), or null */
    private fun optionAt(mouseX: Double, mouseY: Double): Int? {
        val x = (mouseX - left) / scale
        val y = (mouseY - top) / scale
        return when {
            hit(x, y, -69, 92, 36, 36) && state.offers.isNotEmpty() -> 0
            hit(x, y, 33, 92, 36, 36) && state.offers.size > 1 -> 1
            hit(x, y, if (state.currentTeam == 0) -116 else 88, 96, 28, 28)
                && state.rerolls.getOrElse(state.currentTeam) { 0 } > 0 -> DraftActionPacket.REROLL
            else -> null
        }
    }

    private fun submit(option: Int, source: String) {
        // ignore accidental double submits (e.g. double click / key repeat)
        val now = System.nanoTime()
        if (now - lastClickAt < 250_000_000L) return
        if (!canAct) {
            log.info("[Bingout] Click ignored ({}): canPick={}, pending={}", source, state.canPick, pendingTurn != null)
            return
        }
        lastClickAt = now
        log.info("[Bingout] Click: pick={}, option={}, via={}", state.picks.size + 1, option, source)
        client.player.sendCommand("bingo draft choose ${state.turn} $option")
        pendingTurn = state.turn
        sentAt = now
    }

    private fun tryClick(mouseX: Double, mouseY: Double, source: String): Boolean {
        val option = optionAt(mouseX, mouseY)
        log.info("[Bingout] Mouse press ({}) at {},{} -> option={}, canAct={}", source, mouseX.toInt(), mouseY.toInt(), option, canAct)
        if (option == null) return false
        submit(option, source)
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // 0 = left (ScreenImpl normalizes SDL/GLFW button numbers)
        if (button != 0) return false
        return tryClick(mouseX, mouseY, "mouse")
    }

    /** Keyboard shortcuts: 1 / 2 pick an option, R rerolls */
    override fun keyPressed(input: IKeyInput): Boolean {
        val option = when (input.keyCode) {
            49, 321 -> 0 // 1 / numpad 1
            50, 322 -> 1 // 2 / numpad 2
            82 -> DraftActionPacket.REROLL // R
            else -> return false
        }
        if (option == DraftActionPacket.REROLL && state.rerolls.getOrElse(state.currentTeam) { 0 } <= 0) return true
        if (option >= 0 && option >= state.offers.size) return true
        submit(option, "key")
        return true
    }
}
