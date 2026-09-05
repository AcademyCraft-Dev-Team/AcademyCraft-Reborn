package org.academy.api.client.gui.command

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.gui.render.VertexWriter
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformPayload
import org.joml.Vector3f

abstract class PosColorRectDrawCommand protected constructor(
    pipeline: RenderPipeline,
    protected val width: Float,
    protected val height: Float,
    protected val red: Float,
    protected val green: Float,
    protected val blue: Float,
    protected val alpha: Float,
    textures: List<TextureBinding>,
    uniforms: List<UniformPayload<*>>
) : DrawCommand(pipeline, textures, uniforms) {
    override fun generateVertices(writer: VertexWriter, pose: PoseStack.Pose, alphaMul: Float) {
        val matrix = pose.pose()
        val r = (red * 255.0f).toInt()
        val g = (green * 255.0f).toInt()
        val b = (blue * 255.0f).toInt()
        val a = (alpha * alphaMul * 255.0f).toInt()
        val dest = Vector3f()

        writer.beginVertex()
        matrix.transformPosition(0.0f, 0.0f, 0.0f, dest)
        writer.putVec3f(dest.x, dest.y, dest.z)
        writer.putColor(r, g, b, a)

        writer.beginVertex()
        matrix.transformPosition(0.0f, height, 0.0f, dest)
        writer.putVec3f(dest.x, dest.y, dest.z)
        writer.putColor(r, g, b, a)

        writer.beginVertex()
        matrix.transformPosition(width, height, 0.0f, dest)
        writer.putVec3f(dest.x, dest.y, dest.z)
        writer.putColor(r, g, b, a)

        writer.beginVertex()
        matrix.transformPosition(width, 0.0f, 0.0f, dest)
        writer.putVec3f(dest.x, dest.y, dest.z)
        writer.putColor(r, g, b, a)
    }
}
