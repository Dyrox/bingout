package me.jfenn.bingo.client.impl.draw

import me.jfenn.bingo.client.platform.renderer.TooltipIcon
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import kotlin.math.ceil

/**
 * Tooltip section listing entries as "icon + name" cells, filled column by column
 * (e.g. the animals bred for a "Breed N Unique Animals" goal).
 */
class IconGridTooltip(
    private val entries: List<TooltipIcon>,
) : ClientTooltipComponent {

    companion object {
        private const val ROW_HEIGHT = 18
        private const val ICON_SIZE = 16
        private const val TEXT_GAP = 2
        private const val COLUMN_GAP = 8
        private const val MAX_ROWS = 8
        private const val TOP_PADDING = 2
    }

    private val rows = minOf(MAX_ROWS, entries.size).coerceAtLeast(1)
    private val columns = ceil(entries.size / rows.toDouble()).toInt()

    private fun columnEntries(column: Int) = entries.drop(column * rows).take(rows)

    private fun columnWidth(font: Font, column: Int): Int {
        return ICON_SIZE + TEXT_GAP + (columnEntries(column).maxOfOrNull { font.width(it.text.value) } ?: 0)
    }

    override fun getHeight(font: Font): Int = TOP_PADDING + rows * ROW_HEIGHT

    override fun getWidth(font: Font): Int {
        return (0 until columns).sumOf { columnWidth(font, it) } + COLUMN_GAP * (columns - 1).coerceAtLeast(0)
    }

    /** Calls [action] with the top-left position of each cell */
    private fun forEachCell(font: Font, x: Int, y: Int, action: (TooltipIcon, Int, Int) -> Unit) {
        var columnX = x
        for (column in 0 until columns) {
            columnEntries(column).forEachIndexed { row, entry ->
                action(entry, columnX, y + TOP_PADDING + row * ROW_HEIGHT)
            }
            columnX += columnWidth(font, column) + COLUMN_GAP
        }
    }

    override fun extractText(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int) {
        forEachCell(font, x, y) { entry, cellX, cellY ->
            graphics.text(font, entry.text.value, cellX + ICON_SIZE + TEXT_GAP, cellY + (ICON_SIZE - 8) / 2, -1, true)
        }
    }

    override fun extractImage(font: Font, x: Int, y: Int, width: Int, height: Int, graphics: GuiGraphicsExtractor) {
        forEachCell(font, x, y) { entry, cellX, cellY ->
            val drewFace = entry.entityId?.let { MobFaceRenderer.draw(graphics, it, cellX, cellY, ICON_SIZE) } == true
            if (!drewFace) entry.item?.let { graphics.item(it.stack, cellX, cellY) }
        }
    }
}
