package org.academy.api.client.gui.command

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.gui.render.VertexWriter
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformPayload
import org.joml.Vector3f

abstract class PosTexColorRectDrawCommand : DrawCommand {
    protected val width: Float
    protected val height: Float
    protected val u0: Float
    protected val v0: Float

    protected val u1: Float
    protected val v1: Float

    protected val u2: Float
    protected val v2: Float

    protected val u3: Float
    protected val v3: Float

    protected val red: Float
    protected val green: Float
    protected val blue: Float
    protected val alpha: Float

    /** 预乘 alpha 管线 (如物品图标): rgb 已乘 alpha, 淡出时须连 rgb 一起缩放. */
    protected open val premultiplied: Boolean = false

    protected constructor(
        pipeline: RenderPipeline,
        width: Float,
        height: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        textures: List<TextureBinding>,
        uniforms: List<UniformPayload<*>>
    ) : this(
        pipeline, width, height,
        u0, v0, u0, v1, u1, v1, u1, v0,
        red, green, blue, alpha, textures, uniforms
    )

    protected constructor(
        pipeline: RenderPipeline,
        width: Float,
        height: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        u2: Float,
        v2: Float,
        u3: Float,
        v3: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        textures: List<TextureBinding>,
        uniforms: List<UniformPayload<*>>
    ) : super(pipeline, textures, uniforms) {
        this.width = width
        this.height = height
        this.u0 = u0
        this.v0 = v0

        this.u1 = u1
        this.v1 = v1

        this.u2 = u2
        this.v2 = v2

        this.u3 = u3
        this.v3 = v3

        this.red = red
        this.green = green
        this.blue = blue
        this.alpha = alpha
    }

    override fun generateVertices(writer: VertexWriter, pose: PoseStack.Pose, alphaMul: Float) {
        val matrix = pose.pose()
        val rgbMul = if (premultiplied) alphaMul else 1.0f
        val r = (red * rgbMul * 255.0f).toInt()
        val g = (green * rgbMul * 255.0f).toInt()
        val b = (blue * rgbMul * 255.0f).toInt()
        val a = (alpha * alphaMul * 255.0f).toInt()
        val dest = Vector3f()

        writer.beginVertex()
        matrix.transformPosition(0.0f, 0.0f, 0.0f, dest)
        writer.putVec3f(dest.x, dest.y, dest.z)
        writer.putVec2f(u0, v0)
        writer.putColor(r, g, b, a)

        writer.beginVertex()
        matrix.transformPosition(0.0f, height, 0.0f, dest)
        writer.putVec3f(dest.x, dest.y, dest.z)
        writer.putVec2f(u1, v1)
        writer.putColor(r, g, b, a)

        writer.beginVertex()
        matrix.transformPosition(width, height, 0.0f, dest)
        writer.putVec3f(dest.x, dest.y, dest.z)
        writer.putVec2f(u2, v2)
        writer.putColor(r, g, b, a)

        writer.beginVertex()
        matrix.transformPosition(width, 0.0f, 0.0f, dest)
        writer.putVec3f(dest.x, dest.y, dest.z)
        writer.putVec2f(u3, v3)
        writer.putColor(r, g, b, a)
    }
}
