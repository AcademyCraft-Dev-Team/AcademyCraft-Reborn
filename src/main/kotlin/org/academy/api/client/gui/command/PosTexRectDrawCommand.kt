package org.academy.api.client.gui.command

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.gui.render.VertexWriter
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformPayload
import org.joml.Vector3f

open class PosTexRectDrawCommand(
    pipeline: RenderPipeline,
    protected val width: Float,
    protected val height: Float,
    protected val u0: Float,
    protected val v0: Float,
    protected val u1: Float,
    protected val v1: Float,
    textures: List<TextureBinding>,
    uniforms: List<UniformPayload<*>>
) : DrawCommand(pipeline, textures, uniforms) {
    override fun generateVertices(writer: VertexWriter, pose: PoseStack.Pose) {
        val matrix = pose.pose()
        val dest = Vector3f()

        writer.beginVertex()
        matrix.transformPosition(0.0f, 0.0f, 0.0f, dest)
        writer.putVec3f(dest.x, dest.y, dest.z)
        writer.putVec2f(u0, v0)

        writer.beginVertex()
        matrix.transformPosition(0.0f, height, 0.0f, dest)
        writer.putVec3f(dest.x, dest.y, dest.z)
        writer.putVec2f(u0, v1)

        writer.beginVertex()
        matrix.transformPosition(width, height, 0.0f, dest)
        writer.putVec3f(dest.x, dest.y, dest.z)
        writer.putVec2f(u1, v1)

        writer.beginVertex()
        matrix.transformPosition(width, 0.0f, 0.0f, dest)
        writer.putVec3f(dest.x, dest.y, dest.z)
        writer.putVec2f(u1, v0)
    }
}
