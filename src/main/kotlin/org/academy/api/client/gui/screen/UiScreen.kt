package org.academy.api.client.gui.screen

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.academy.api.client.Render
import org.academy.api.client.gui.event.CharTypedEvent
import org.academy.api.client.gui.event.EventType
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.event.ScrollEvent
import org.academy.api.client.gui.imgui.ImGuiUtilApi
import org.academy.api.client.gui.widget.FrameLayoutWidget

abstract class UiScreen protected constructor(title: Component) : Screen(title), RenderRoot {
    override val root: FrameLayoutWidget = FrameLayoutWidget()

    override fun init() {
        ImGuiUtilApi.clearEventsQueue()

        root.name = "root"
        root.clearChildren()

        onInit()

        if (!root.isAttached()) root.dispatchAttached()
    }

    protected abstract fun onInit()

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        val renderTarget = ScreenDispatcher.getRenderTarget() ?: return
        val colorTextureView = renderTarget.getColorTextureView() ?: return

        graphics.innerBlit(
            Render.RenderPipelines.IMAGE_PREMULTIPLIED_ALPHA,
            colorTextureView,
            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
            0, 0, graphics.guiWidth(), graphics.guiHeight(),
            0f, 1f, 1f, 0f, -1
        )
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        extractBlurredBackground(graphics)
        extractTransparentBackground(graphics)
        minecraft.gui.extractDeferredSubtitles()
    }

    override fun removed() {
        super.removed()
        if (root.isAttached()) root.dispatchDetached()
    }

    override fun tick() {
        root.tick()
        super.tick()
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        if (ImGuiUtilApi.wantCaptureMouse()) return

        val event: MouseEvent = MouseEvent.createMoveEvent(mouseX, mouseY)
        root.dispatchEvent(event)
        super.mouseMoved(mouseX, mouseY)
    }

    override fun mouseClicked(e: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (ImGuiUtilApi.wantCaptureMouse()) return true

        val event: MouseEvent = MouseEvent.createPressEvent(e.x(), e.y(), e.button())
        root.dispatchEvent(event)

        if (event.isConsumed) return true

        return super.mouseClicked(e, isDoubleClick)
    }

    override fun mouseReleased(e: MouseButtonEvent): Boolean {
        if (ImGuiUtilApi.wantCaptureMouse()) return true

        val event: MouseEvent = MouseEvent.createReleaseEvent(e.x(), e.y(), e.button())
        root.dispatchEvent(event)
        if (event.isConsumed) return true

        return super.mouseReleased(e)
    }

    override fun mouseDragged(e: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        if (ImGuiUtilApi.wantCaptureMouse()) return true

        val event: MouseEvent = MouseEvent.createDragEvent(e.x(), e.y(), e.button(), mouseX, mouseY)
        root.dispatchEvent(event)
        if (event.isConsumed) return true

        return super.mouseDragged(e, mouseX, mouseY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (ImGuiUtilApi.wantCaptureMouse()) return true

        val event = ScrollEvent(mouseX, mouseY, scrollY)
        root.dispatchEvent(event)
        if (event.isConsumed) return true

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(e: KeyEvent): Boolean {
        if (ImGuiUtilApi.wantCaptureKeyboard()) return true

        val event =
            org.academy.api.client.gui.event.KeyEvent(EventType.KEY_PRESSED, e.key(), e.scancode(), e.modifiers())
        root.dispatchEvent(event)
        if (event.isConsumed) return true

        return super.keyPressed(e)
    }

    override fun charTyped(e: CharacterEvent): Boolean {
        if (ImGuiUtilApi.wantCaptureKeyboard()) return true

        val event = CharTypedEvent(e.codepoint())
        root.dispatchEvent(event)
        if (event.isConsumed) return true

        return super.charTyped(e)
    }
}