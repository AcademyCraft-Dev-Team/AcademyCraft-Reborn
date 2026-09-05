package org.academy.api.client.gui.command

import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.render.GuiItemAtlas
import net.minecraft.client.renderer.item.TrackingItemStackRenderState
import org.academy.api.client.gui.render.VertexWriter
import org.academy.api.client.render.Render
import org.academy.api.client.render.TextureBinding

class ItemStackDrawCommand(
    val itemState: TrackingItemStackRenderState,
    val width: Float,
    val height: Float,
    val alpha: Float
) : DrawCommand(Render.RenderPipelines.IMAGE_PREMULTIPLIED_ALPHA, emptyList(), emptyList()) {
    override fun generateVertices(writer: VertexWriter, pose: PoseStack.Pose, alphaMul: Float) {
        error("ItemStackDrawCommand must be resolved by UiContext before batching")
    }

    fun resolve(slot: GuiItemAtlas.SlotView, sampler: GpuSampler): DrawCommand {
        return ItemAtlasDrawCommand(
            slot,
            sampler,
            width,
            height,
            alpha
        )
    }

    private class ItemAtlasDrawCommand(
        slot: GuiItemAtlas.SlotView,
        sampler: GpuSampler,
        width: Float,
        height: Float,
        alpha: Float
    ) : PosTexColorRectDrawCommand(
        Render.RenderPipelines.IMAGE_PREMULTIPLIED_ALPHA,
        width,
        height,
        slot.u0(),
        slot.v0(),
        slot.u1(),
        slot.v1(),
        alpha,
        alpha,
        alpha,
        alpha,
        listOf(TextureBinding("Sampler0", slot.textureView(), sampler)),
        emptyList()
    ) {
        override val premultiplied: Boolean get() = true
    }
}
