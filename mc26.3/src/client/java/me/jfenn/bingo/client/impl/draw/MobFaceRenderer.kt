package me.jfenn.bingo.client.impl.draw

import com.mojang.blaze3d.vertex.PoseStack
import me.jfenn.bingo.client.impl.accessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.LivingEntity
import org.joml.Vector3f
import org.slf4j.LoggerFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws a mob's face (like the Minecraft wiki's mob icons), built from the mob's own model and texture:
 * the front faces of the cubes in its "head" part (plus hat/nose/ear parts) are drawn in an orthographic
 * front view. Resource packs that change the mob textures are reflected automatically.
 */
object MobFaceRenderer {

    private val log = LoggerFactory.getLogger("bingo")

    /** One front-facing cube face: its rectangle (in model pixels) and texture coordinates (0..1) */
    private class Quad(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val u1: Float, val v1: Float, val u2: Float, val v2: Float,
        val depth: Float,
        /** Whether the quad belongs to the "head" part itself (not a hat, ear, nose, ... child of it) */
        val isHead: Boolean,
    ) {
        /** This quad cut to a rectangle (with matching texture coordinates), or null if it lies outside of it */
        fun clip(cx1: Float, cy1: Float, cx2: Float, cy2: Float): Quad? {
            val nx1 = max(x1, cx1); val nx2 = min(x2, cx2)
            val ny1 = max(y1, cy1); val ny2 = min(y2, cy2)
            if (nx2 - nx1 < 0.01f || ny2 - ny1 < 0.01f) return null
            fun u(x: Float) = u1 + (u2 - u1) * (x - x1) / (x2 - x1)
            fun v(y: Float) = v1 + (v2 - v1) * (y - y1) / (y2 - y1)
            return Quad(nx1, ny1, nx2, ny2, u(nx1), v(ny1), u(nx2), v(ny2), depth, isHead)
        }
    }

    private class Face(
        val texture: Identifier,
        val quads: List<Quad>,
        val minX: Float, val minY: Float, val width: Float, val height: Float,
    ) {
        val size get() = max(width, height)
    }

    private val cache = mutableMapOf<String, Face?>()

    private const val PREVIEW_ENTITY_ID = -0x4D0B

    /** How far (relative to the head's size) hats, ears, beaks, ... may stick out of the head */
    private const val HEAD_MARGIN = 0.3f

    /** Parts that make up a face, besides the head itself */
    private val FACE_PARTS = setOf("head", "hat", "nose", "jaw", "beak", "snout", "mouth")

    private fun build(entityId: String): Face? {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return null
        val type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(entityId)).orElse(null) ?: return null
        val entity = type.create(level, EntitySpawnReason.LOAD) as? LivingEntity ?: return null
        // never added to the level, but render states read the id (e.g. for item model seeds)
        entity.id = PREVIEW_ENTITY_ID

        @Suppress("UNCHECKED_CAST")
        val renderer = mc.entityRenderDispatcher.getRenderer(entity)
            as? LivingEntityRenderer<LivingEntity, LivingEntityRenderState, *> ?: return null
        val state = renderer.createRenderState(entity, 0f)
        val texture = renderer.getTextureLocation(state)
        val root = renderer.model.root()
        root.getAllParts().forEach { it.resetPose() }

        // collect the cubes of the model, with their part path ("head/nose", ...)
        class PathCube(val path: List<String>, val pose: PoseStack.Pose, val cube: ModelPart.Cube)
        val cubes = mutableListOf<PathCube>()
        root.visit(PoseStack()) { pose, path, _, cube ->
            cubes += PathCube(path.split('/').filter { it.isNotEmpty() }, pose.copy(), cube)
        }

        // the head and its children, plus face-related parts next to it (e.g. a humanoid's "hat" layer);
        // mobs without a head (slimes, ghasts, ...) are drawn as a whole
        val headCubes = cubes.filter { c -> c.path.any { it in FACE_PARTS } }
            .ifEmpty { cubes }

        val quads = mutableListOf<Quad>()
        val position = Vector3f()
        val normal = Vector3f()
        for (c in headCubes) {
            for (polygon in c.cube.polygons) {
                // the front of a model faces north (-Z)
                c.pose.transformNormal(polygon.normal(), normal)
                if (normal.z > -0.9f) continue

                var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
                var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
                var depth = 0f
                var uAtMinX = 0f; var uAtMaxX = 0f; var vAtMinY = 0f; var vAtMaxY = 0f
                for (vertex in polygon.vertices()) {
                    c.pose.pose().transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), position)
                    val x = position.x * 16f
                    val y = position.y * 16f
                    depth += position.z
                    if (x < minX) { minX = x; uAtMinX = vertex.u() }
                    if (x > maxX) { maxX = x; uAtMaxX = vertex.u() }
                    if (y < minY) { minY = y; vAtMinY = vertex.v() }
                    if (y > maxY) { maxY = y; vAtMaxY = vertex.v() }
                }
                if (maxX - minX < 0.01f || maxY - minY < 0.01f) continue
                quads += Quad(minX, minY, maxX, maxY, uAtMinX, vAtMinY, uAtMaxX, vAtMaxY, depth / polygon.vertices().size,
                    isHead = c.path.lastOrNull() == "head")
            }
        }
        if (quads.isEmpty()) return null

        // keep the face around the head: tall hats (witch), wide brims (villagers) or long ears would
        // otherwise shrink the face to a few pixels
        val head = quads.filter { it.isHead }
        if (head.isNotEmpty()) {
            val hx1 = head.minOf { it.x1 }; val hx2 = head.maxOf { it.x2 }
            val hy1 = head.minOf { it.y1 }; val hy2 = head.maxOf { it.y2 }
            val margin = HEAD_MARGIN * max(hx2 - hx1, hy2 - hy1)
            val clipped = quads.mapNotNull { it.clip(hx1 - margin, hy1 - margin, hx2 + margin, hy2 + margin) }
            quads.clear()
            quads += clipped
        }

        val minX = quads.minOf { it.x1 }
        val minY = quads.minOf { it.y1 }
        val width = quads.maxOf { it.x2 } - minX
        val height = quads.maxOf { it.y2 } - minY
        // draw the faces furthest back first (front-most faces, e.g. a hat layer or a nose, on top)
        return Face(texture, quads.sortedByDescending { it.depth }, minX, minY, width, height)
    }

    private fun getFace(entityId: String): Face? {
        return cache.getOrPut(entityId) {
            runCatching { build(entityId) }
                .onFailure { log.warn("[MobFaceRenderer] Could not build the face of {}", entityId, it) }
                .getOrNull()
        }
    }

    /** Draws the face of [entityId] into a [size]x[size] square, returning false if it can't be drawn */
    fun draw(graphics: GuiGraphicsExtractor, entityId: String, x: Int, y: Int, size: Int = 16): Boolean {
        val face = getFace(entityId) ?: return false
        val texture = Minecraft.getInstance().textureManager.getTexture(face.texture)
        // render at 4x resolution so that half pixels (e.g. inflated hat layers) are kept
        val precision = 4f
        val scale = size / (face.size * precision)
        val pose = graphics.pose()
        pose.pushMatrix()
        // center faces that aren't square
        pose.translate(x + (face.size - face.width) / 2f * size / face.size, y + (face.size - face.height) / 2f * size / face.size)
        pose.scale(scale, scale)
        for (quad in face.quads) {
            val x1 = ((quad.x1 - face.minX) * precision).roundToInt()
            val y1 = ((quad.y1 - face.minY) * precision).roundToInt()
            val x2 = ((quad.x2 - face.minX) * precision).roundToInt()
            val y2 = ((quad.y2 - face.minY) * precision).roundToInt()
            graphics.accessor.invokeDrawTexturedQuad(
                RenderPipelines.GUI_TEXTURED, texture.textureView, texture.sampler,
                x1, y1, x2, y2, quad.u1, quad.u2, quad.v1, quad.v2, -1,
            )
        }
        pose.popMatrix()
        return true
    }

    fun clearCache() = cache.clear()
}
