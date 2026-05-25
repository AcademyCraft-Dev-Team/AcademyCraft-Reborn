package org.academy.api.client.gui.screen

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import org.academy.AcademyCraft
import org.academy.api.client.Render
import org.academy.api.client.Resource
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.drawable.TextureDrawable
import org.academy.api.client.gui.event.CharTypedEvent
import org.academy.api.client.gui.event.EventType
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.event.ScrollEvent
import org.academy.api.client.gui.imgui.ImGuiUtilApi
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.*

abstract class ContainerUiScreen<T : AbstractContainerMenu> protected constructor(
    menu: T,
    playerInventory: Inventory,
    title: Component
) : AbstractContainerScreen<T>(menu, playerInventory, title), RenderRoot {
    override val root: FrameLayoutWidget = FrameLayoutWidget()

    var isHandleContainer: Boolean = true
    var isRenderInventory: Boolean = true
        set(renderInventory) {
            field = renderInventory
            invVisibleSetter.invoke(renderInventory)
        }
    private var invHeightSupplier: () -> Float = { 1f }
    private var invTranslationYSupplier: () -> Float = { 1f }
    private var invVisibleSetter = { _: Boolean -> }

    override fun init() {
        super.init()
        ImGuiUtilApi.clearEventsQueue()

        root.name = "root"
        root.clearChildren()

        val finalHeight = 187f
        val duration = 600L

        val main = LinearLayoutWidget()
        main.setOrientation(Orientation.HORIZONTAL)
        main.layoutParams = FrameLayoutWidget.LayoutParams()
            .widthMode(SizeMode.WRAP_CONTENT)
            .heightMode(SizeMode.WRAP_CONTENT)
            .margin((leftPos - 16).toFloat(), (topPos - 22).toFloat(), 0f, 0f)

        root.addChild("main", main)
        main.startAnimation(
            ObjectAnimator.ofFloat({ alpha -> main.alpha = alpha }, 0f, 1.0f)
                .setDuration(duration)
                .setInterpolator(EasingFunctions.EASE_OUT_EXPO)
        )
        main.startAnimation(
            ObjectAnimator.ofFloat({ height -> main.height = height }, 0f, finalHeight)
                .setDuration(duration)
                .setInterpolator(EasingFunctions.EASE_OUT_EXPO)
        )
        run {
            val pageButtons = RadioGroupWidget()
            pageButtons.setOrientation(Orientation.VERTICAL)
            pageButtons.layoutParams = LinearLayoutWidget.LayoutParams()
                .width(16f)
                .heightMode(SizeMode.WRAP_CONTENT)

            main.addChild("radio_group_page_button", pageButtons)
            val invButton: RadioButtonWidget
            run {
                invButton = createButton(Resource.Textures.ICON_INV)
                invButton.layoutParams = WidgetContainer.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(16f)

                pageButtons.addChild("inv", invButton)
                pageButtons.selectButton(invButton)
            }

            val content = FrameLayoutWidget()
            content.layoutParams = LinearLayoutWidget.LayoutParams()
                .width(imageWidth.toFloat())
                .height(187f)

            main.addChild("content", content)
            run {
                val invPage = FrameLayoutWidget()
                invTranslationYSupplier = { invPage.translationY }
                invHeightSupplier = { invPage.height }

                invVisibleSetter = { visible ->
                    invPage.visibility = (if (visible) Widget.Visibility.VISIBLE else Widget.Visibility.GONE)
                }
                invPage.layoutParams = FrameLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .heightMode(SizeMode.MATCH_PARENT)

                content.addChild("page_inv", invPage)
                invPage.startAnimation(ObjectAnimator.ofFloat({ height ->
                    invPage.height = height

                }, 0f, finalHeight).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_EXPO))
                run {
                    val back = BlendQuadWidget()
                    back.layoutParams = FrameLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .heightMode(SizeMode.MATCH_PARENT)
                    back.alpha = 0.5f
                    invPage.addChild("back", back)

                    val inv = ImageWidget(Resource.Textures.UI_INVENTORY)
                    inv.layoutParams = FrameLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .heightMode(SizeMode.MATCH_PARENT)
                    invPage.addChild("inv", inv)
                    onInit(pageButtons, invButton, content, invPage)
                }
            }
        }

        if (!root.isAttached()) {
            root.dispatchAttached()
        }
    }

    protected abstract fun onInit(
        pageButtons: RadioGroupWidget,
        invButton: RadioButtonWidget,
        content: FrameLayoutWidget,
        invPage: FrameLayoutWidget
    )

    protected fun createButton(textureLocation: Identifier): RadioButtonWidget {
        val widget = RadioButtonWidget()
        val defaultDrawable = TextureDrawable(textureLocation)
        defaultDrawable.tintColor = -0x444445

        val hoveredDrawable = TextureDrawable(textureLocation)
        hoveredDrawable.tintColor = -0x1

        val sld = StateListDrawable()
        sld.setDefault(defaultDrawable)
        sld.addState(Widget.FOCUSED, hoveredDrawable)
        sld.addState(Widget.SELECTED, hoveredDrawable)
        sld.addState(Widget.HOVERED, hoveredDrawable)
        sld.addState(Widget.PRESSED, hoveredDrawable)

        widget.background = sld
        return widget
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        val renderTarget: RenderTarget = ScreenDispatcher.getRenderTarget() ?: return
        val colorTextureView = renderTarget.getColorTextureView() ?: return

        graphics.innerBlit(
            Render.RenderPipelines.IMAGE_PREMULTIPLIED_ALPHA,
            colorTextureView,
            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
            0, 0, graphics.guiWidth(), graphics.guiHeight(),
            0f, 1f, 1f, 0f, -1
        )

        if (this.isRenderInventory) {
            val originHeight = 187f
            val currentHeight = invHeightSupplier()
            val scaleY = currentHeight / originHeight
            graphics.pose().pushMatrix()
            graphics.pose().translate(0f, topPos.toFloat())
            graphics.pose().scale(1f, scaleY)
            graphics.pose().translate(0f, -topPos + invTranslationYSupplier())

            extractContents(graphics, mouseX, mouseY, a)
            extractCarriedItem(graphics, mouseX, mouseY)
            extractSnapbackItem(graphics)

            graphics.pose().popMatrix()
        }
        extractTooltip(graphics, mouseX, mouseY)
    }

    override fun removed() {
        super.removed()
        if (root.isAttached()) {
            root.dispatchDetached()
        }
    }

    override fun renderSlotContents(
        graphics: GuiGraphicsExtractor,
        itemstack: ItemStack,
        slot: Slot,
        itemCount: String?
    ) {
        val pose = graphics.pose()
        pose.pushMatrix()

        pose.translate(slot.x.toFloat(), slot.y.toFloat())
        pose.translate(8.0f, 8.0f)

        pose.scale(2 / 3f)

        pose.translate(-8.0f, -8.0f)
        pose.translate(-slot.x.toFloat(), -slot.y.toFloat())

        super.renderSlotContents(graphics, itemstack, slot, itemCount)

        pose.popMatrix()
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        extractBlurredBackground(graphics)
        extractTransparentBackground(graphics)
        minecraft.gui.extractDeferredSubtitles()
    }

    override fun extractLabels(graphics: GuiGraphicsExtractor, xm: Int, ym: Int) {
    }

    override fun containerTick() {
        root.tick()
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        if (ImGuiUtilApi.wantCaptureMouse()) return

        val event: MouseEvent = MouseEvent.createMoveEvent(mouseX, mouseY)
        root.dispatchEvent(event)
        super.mouseMoved(mouseX, mouseY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (ImGuiUtilApi.wantCaptureMouse()) return true

        val event = ScrollEvent(mouseX, mouseY, scrollY)
        root.dispatchEvent(event)
        val rootResult = event.isConsumed

        var superResult = false
        if (this.isHandleContainer) superResult = super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)

        return rootResult || superResult
    }

    override fun mouseReleased(e: MouseButtonEvent): Boolean {
        if (ImGuiUtilApi.wantCaptureMouse()) return true

        val event: MouseEvent = MouseEvent.createReleaseEvent(e.x(), e.y(), e.button())
        root.dispatchEvent(event)
        val rootResult = event.isConsumed

        var superResult = false
        if (this.isHandleContainer) superResult = super.mouseReleased(e)

        return rootResult || superResult
    }

    override fun mouseDragged(e: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        if (ImGuiUtilApi.wantCaptureMouse()) return true

        val event: MouseEvent = MouseEvent.createDragEvent(e.x(), e.y(), e.button(), mouseX, mouseY)
        root.dispatchEvent(event)
        val rootResult = event.isConsumed

        var superResult = false
        if (this.isHandleContainer) superResult = super.mouseDragged(e, mouseX, mouseY)

        return rootResult || superResult
    }

    override fun mouseClicked(e: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (ImGuiUtilApi.wantCaptureMouse()) return true

        val event: MouseEvent = MouseEvent.createPressEvent(e.x(), e.y(), e.button())
        root.dispatchEvent(event)
        val rootResult = event.isConsumed

        var superResult = false
        if (this.isHandleContainer) superResult = super.mouseClicked(e, isDoubleClick)

        return rootResult || superResult
    }

    override fun keyPressed(e: KeyEvent): Boolean {
        if (ImGuiUtilApi.wantCaptureKeyboard()) return true

        if (e.key() == InputConstants.KEY_F12) {
            AcademyCraft.DEBUG_UI = !AcademyCraft.DEBUG_UI
            return true
        }

        val event =
            org.academy.api.client.gui.event.KeyEvent(EventType.KEY_PRESSED, e.key(), e.scancode(), e.modifiers())
        root.dispatchEvent(event)
        if (event.isConsumed) return true
        if (e.key() == InputConstants.KEY_ESCAPE && shouldCloseOnEsc()) {
            onClose()
            return true
        }
        return this.isHandleContainer && super.keyPressed(e)
    }

    override fun charTyped(e: CharacterEvent): Boolean {
        if (ImGuiUtilApi.wantCaptureKeyboard()) return true

        val event = CharTypedEvent(e.codepoint())
        root.dispatchEvent(event)
        if (event.isConsumed) return true
        return this.isHandleContainer && super.charTyped(e)
    }

    override fun hasClickedOutside(mouseX: Double, mouseY: Double, guiLeft: Int, guiTop: Int): Boolean {
        return mouseX < guiLeft || mouseY < guiTop - 22 || mouseX >= guiLeft + imageWidth || mouseY >= guiTop + 187
    }
}