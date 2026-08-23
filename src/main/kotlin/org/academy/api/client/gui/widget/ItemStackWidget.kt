package org.academy.api.client.gui.widget

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.item.TrackingItemStackRenderState
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.academy.AcademyCraft
import org.academy.api.client.gui.command.ItemStackDrawCommand
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.render.RenderContext

open class ItemStackWidget(stack: ItemStack) : AbstractWidget() {
    var stack: ItemStack = stack.copy()
        set(value) {
            field = value.copy()
            requestLayout()
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        val lp = layoutParams
        setMeasuredDimension(
            resolveSize(ITEM_SIZE + lp.paddingLeft + lp.paddingRight, widthMeasureSpec),
            resolveSize(ITEM_SIZE + lp.paddingTop + lp.paddingBottom, heightMeasureSpec)
        )
    }

    override fun renderInternal(context: RenderContext) {
        background?.draw(context, this)
        if (stack.isEmpty) return

        val lp = layoutParams
        val paddedWidth = width - lp.paddingLeft - lp.paddingRight
        val paddedHeight = height - lp.paddingTop - lp.paddingBottom
        if (paddedWidth <= 0f || paddedHeight <= 0f) return

        val minecraft = Minecraft.getInstance()
        val itemState = TrackingItemStackRenderState()
        try {
            minecraft.itemModelResolver.updateForTopItem(
                itemState,
                stack,
                ItemDisplayContext.GUI,
                minecraft.level,
                minecraft.player,
                0
            )
        } catch (exception: Throwable) {
            LOGGER.error("Failed to resolve item model for {}", stack, exception)
            return
        }

        context.pose().pushPose()
        context.pose().translate(lp.paddingLeft, lp.paddingTop)
        context.submit(
            ItemStackDrawCommand(
                itemState,
                paddedWidth,
                paddedHeight,
                alpha * context.accumulatedAlpha
            )
        )
        context.pose().popPose()
        foreground?.draw(context, this)
    }

    companion object {
        const val ITEM_SIZE = 16f
        private val LOGGER = AcademyCraft.getLogger()
    }
}
