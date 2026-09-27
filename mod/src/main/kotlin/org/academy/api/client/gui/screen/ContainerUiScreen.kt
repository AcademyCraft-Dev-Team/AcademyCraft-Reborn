package org.academy.api.client.gui.screen

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.textures.FilterMode
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
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.drawable.TextureDrawable
import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.event.CharTypedEvent
import org.academy.api.client.gui.event.EventType
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.event.ScrollEvent
import org.academy.api.client.gui.imgui.ImGuiUtilApi
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.*
import org.academy.api.client.render.Render
import org.academy.api.client.resources.R

abstract class ContainerUiScreen<T : AbstractContainerMenu> protected constructor(
    menu: T,
    playerInventory: Inventory,
    title: Component
) : AbstractContainerScreen<T>(menu, playerInventory, title), RenderRoot {
    override val root = standaloneFrame {
        name = "root"
    }

    var isHandleContainer: Boolean = true

    private val handleContainer: Boolean
        get() = isHandleContainer && !TextInputFocus.isActiveWithin(root)
    var isRenderInventory: Boolean = true
        set(renderInventory) {
            field = renderInventory
            invVisible.invoke(renderInventory)
        }
    private var invHeight: () -> Float = { 1f }
    private var invTranslationY: () -> Float = { 1f }
    private var invVisible = { _: Boolean -> }

    override fun init() {
        super.init()
        ImGuiUtilApi.clearEventsQueue()

        root.apply {
            clearChildren()

            val finalHeight = 187f
            val duration = 600L

            lateinit var buttonGroupPage: RadioGroupWidget
            lateinit var buttonInv: RadioButtonWidget
            lateinit var content: FrameLayoutWidget
            lateinit var pageInv: FrameLayoutWidget

            row("main") {
                lp {
                    widthMode(SizeMode.WRAP_CONTENT)
                    heightMode(SizeMode.WRAP_CONTENT)
                    margin((leftPos - 16).toFloat(), (topPos - 22).toFloat(), 0f, 0f)
                }

                startAnimation(
                    ObjectAnimator.ofFloat({ alpha = it }, 0f, 1.0f)
                        .setDuration(duration)
                        .setInterpolator(EasingFunctions.EASE_OUT_EXPO)
                )

                startAnimation(
                    ObjectAnimator.ofFloat({ height = it }, 0f, finalHeight)
                        .setDuration(duration)
                        .setInterpolator(EasingFunctions.EASE_OUT_EXPO)
                )

                buttonGroupPage = radioGroup("button_group_page") {
                    lp {
                        width(16f)
                        heightMode(SizeMode.WRAP_CONTENT)
                    }

                    orientation = Orientation.VERTICAL

                    buttonInv = add(
                        "inv", createButtonPage(
                            R.textures.gui.icon.icon_inv
                        ).apply {
                            lp {
                                widthMode(SizeMode.MATCH_PARENT)
                                height(16f)
                            }
                            selectButton(this)
                        }
                    )
                }

                content = frame("content") {
                    lp {
                        width(imageWidth.toFloat())
                        height(187f)
                    }

                    pageInv = frame("page_inv") {
                        lp {
                            matchParent()
                        }

                        invTranslationY = { translationY }
                        invHeight = { height }
                        invVisible = {
                            visibility = (if (it) Widget.Visibility.VISIBLE else Widget.Visibility.GONE)
                        }
                        startAnimation(
                            ObjectAnimator.ofFloat({
                                height = it

                            }, 0f, finalHeight).setDuration(duration)
                                .setInterpolator(EasingFunctions.EASE_OUT_EXPO)
                        )

                        blendQuad("back") {
                            lp {
                                matchParent()
                            }

                            alpha = 0.5f
                        }

                        image(R.textures.gui.element.ui_inventory, "inv") {
                            lp {
                                matchParent()
                            }
                        }
                    }
                }
            }

            onInit(buttonGroupPage, buttonInv, content, pageInv)

            if (!isAttached()) dispatchAttached()
        }
    }

    protected abstract fun onInit(
        buttonGroupPage: RadioGroupWidget,
        buttonInv: RadioButtonWidget,
        content: FrameLayoutWidget,
        pageInv: FrameLayoutWidget
    )

    protected fun createButtonPage(textureLocation: Identifier): RadioButtonWidget {
        return RadioButtonWidget().apply {
            background = StateListDrawable().apply {
                setDefault(TextureDrawable(textureLocation).apply {
                    tintColor = 0xFFE6E6E6.toInt()
                })

                val hoveredDrawable = TextureDrawable(textureLocation).apply {
                    tintColor = -0x1
                }

                addState(Widget.FOCUSED, hoveredDrawable)
                addState(Widget.SELECTED, hoveredDrawable)
                addState(Widget.HOVERED, hoveredDrawable)
                addState(Widget.PRESSED, hoveredDrawable)
            }
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        val renderTarget: RenderTarget = ScreenDispatcher.getRenderTarget()
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
            val currentHeight = invHeight()
            val scaleY = currentHeight / originHeight
            graphics.pose().pushMatrix()
            graphics.pose().translate(0f, topPos.toFloat())
            graphics.pose().scale(1f, scaleY)
            graphics.pose().translate(0f, -topPos + invTranslationY())

            extractContents(graphics, mouseX, mouseY, a)
            extractCarriedItem(graphics, mouseX, mouseY)

            graphics.pose().popMatrix()
        }
        extractTooltip(graphics, mouseX, mouseY)
    }

    override fun removed() {
        super.removed()
        root.apply {
            if (isAttached()) dispatchDetached()
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
        minecraft.gui.hud.extractDeferredSubtitles()
    }

    override fun extractLabels(graphics: GuiGraphicsExtractor, xm: Int, ym: Int) {
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

        val event: MouseEvent = MouseEvent.createDragEvent(
            e.x(), e.y(), e.button(), mouseX, mouseY
        )
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

        val event = org.academy.api.client.gui.event.KeyEvent(
            EventType.KEY_PRESSED, e.key(), e.keycode(), e.modifiers()
        )
        root.dispatchEvent(event)
        if (event.isConsumed) return true
        if (e.key() == InputConstants.KEY_ESCAPE && shouldCloseOnEsc()) {
            onClose()
            return true
        }
        return this.handleContainer && super.keyPressed(e)
    }

    override fun charTyped(e: CharacterEvent): Boolean {
        if (ImGuiUtilApi.wantCaptureKeyboard()) return true

        val event = CharTypedEvent(e.codepoint())
        root.dispatchEvent(event)
        return event.isConsumed || this.handleContainer && super.charTyped(e)
    }

    override fun hasClickedOutside(mouseX: Double, mouseY: Double, guiLeft: Int, guiTop: Int): Boolean {
        return mouseX < guiLeft || mouseY < guiTop - 22 || mouseX >= guiLeft + imageWidth || mouseY >= guiTop + 187
    }
}
