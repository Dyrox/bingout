package me.jfenn.bingo.common.card.objective

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import me.jfenn.bingo.common.card.data.ObjectiveData
import me.jfenn.bingo.common.card.data.ObjectiveDataReference
import me.jfenn.bingo.common.card.objective.display.ItemStyle
import me.jfenn.bingo.common.utils.jsonStrict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

@OptIn(ExperimentalSerializationApi::class)
class DraftoutObjectiveDataTest {
    @TestFactory
    fun `all generated draftout objectives load with the production serializer`(): List<DynamicTest> {
        val resource = requireNotNull(javaClass.getResource(
            "/data/yet-another-minecraft-bingo/yet-another-minecraft-bingo/objectives/draftout"
        ))
        val files = Files.walk(Path.of(resource.toURI())).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".json") }
                .sorted().toList()
        }
        // 316 goal types expand into 406 objectives, plus the unachievable draft placeholder.
        assertEquals(407, files.size, "Generated Draftout resources must not be missing")
        return files.map { file ->
            DynamicTest.dynamicTest(file.fileName.toString()) {
                val data = Files.newInputStream(file).use { jsonStrict.decodeFromStream<ObjectiveData>(it) }
                data.display?.image?.let { id ->
                    val namespace = id.substringBefore(':')
                    val path = id.substringAfter(':')
                    val stream = requireNotNull(javaClass.getResourceAsStream(
                        "/data/$namespace/yet-another-minecraft-bingo/images/$path.png"
                    )) { "Missing card image: $id" }
                    val image = stream.use { requireNotNull(ImageIO.read(it)) { "Invalid PNG: $id" } }
                    assertTrue(image.width > 0 && image.height > 0, id)
                    assertTrue((0 until image.width).any { x ->
                        (0 until image.height).any { y -> image.getRGB(x, y).ushr(24) != 0 }
                    }, "Card image must not be fully transparent: $id")
                    assertEquals(ItemStyle.NORMAL, data.display?.itemStyle,
                        "Custom art must not be replaced by the sub-objective grid: $id")
                }
                if (data.innerObjectives.any {
                    it is ObjectiveDataReference.Inline && it.data is ObjectiveData.Stats
                }) {
                    assertEquals(ItemStyle.NORMAL, data.display?.itemStyle,
                        "Stat-based groups need an explicit icon instead of an empty item grid")
                }
            }
        }
    }
}
