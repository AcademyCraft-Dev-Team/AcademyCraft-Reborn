package org.academy.api.client.hud.terminal

import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.platform.Window
import com.mojang.blaze3d.resource.RenderTargetDescriptor
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.client.Minecraft
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.PreeditEvent
import net.minecraft.client.renderer.DynamicGpuDataStorage.DynamicGpuData
import net.minecraft.util.Mth
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge
import org.academy.AcademyCraftClient
import org.academy.AcademyCraftConfig
import org.academy.api.client.app.App
import org.academy.api.client.gui.animation.*
import org.academy.api.client.gui.animation.ObjectAnimator.Companion.ofFloat
import org.academy.api.client.gui.animation.ValueAnimator.Companion.ofFloat
import org.academy.api.client.gui.command.LocalBounds
import org.academy.api.client.gui.command.PosTexRectDrawCommand
import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.event.CharTypedEvent
import org.academy.api.client.gui.event.EventType
import org.academy.api.client.gui.event.KeyEvent
import org.academy.api.client.gui.event.MouseEvent.Companion.createDragEvent
import org.academy.api.client.gui.event.MouseEvent.Companion.createMoveEvent
import org.academy.api.client.gui.event.MouseEvent.Companion.createPressEvent
import org.academy.api.client.gui.event.MouseEvent.Companion.createReleaseEvent
import org.academy.api.client.gui.event.ScrollEvent
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.render.UiContext
import org.academy.api.client.gui.text.model.Ellipsize
import org.academy.api.client.gui.widget.*
import org.academy.api.client.input.*
import org.academy.api.client.render.Render
import org.academy.api.client.render.UniformPayload
import org.academy.api.client.resources.R
import org.academy.api.client.thread.MainThread
import org.academy.api.client.util.ClientUtil
import org.academy.api.common.util.MathUtil
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.sdl.SDLMouse
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tan

class TerminalHud private constructor() {
    private var context: Context = Context()
    private val uiContext: UiContext
    private val config: TerminalConfig
    private var pendingToggle: PendingToggle? = null

    val root: WidgetContainer
        get() = context.get()

    /**
     * 0.0f : 面向鼠标喵
     *
     * 1.0f : 平行于屏幕喵
     */
    private var viewStateProgress = 0.0f
    private var viewMarginRight = 32f

    @Volatile
    private var xPos = 0.0

    @Volatile
    private var yPos = 0.0
    private var startXPos = 0.0
    private var startYPos = 0.0

    @Volatile
    private var posChanged = false

    init {
        AcademyCraftConfig.registerTypeHandler(CONFIG_KEY, TerminalConfig.Action.INSTANCE)
        config = AcademyCraftClient.Config.INSTANCE.getConfig(CONFIG_KEY)

        val defaultKey = InputSystem.combo(
            InputSystem.InputType.KEYBOARD,
            InputConstants.KEY_RALT,
            InputConstants.PRESS
        )
        InputSystem.addKeyBinding(
            KEY_NAME_TOGGLE,
            config.getKeyBinding(KEY_NAME_TOGGLE, defaultKey)
        ) { bindingContext ->
            INSTANCE.requestToggle(bindingContext)
        }

        uiContext = createUiContext()
    }

    private fun createUiContext(): UiContext {
        return object : UiContext() {
            override fun generateCommands(
                context: Canvas, rootWidget: WidgetContainer, mouseX: Double, mouseY: Double, partialTick: Float
            ) {
                super.generateCommands(context, rootWidget, mouseX, mouseY, partialTick)

                context.pose().pushPose()
                run {
                    context.pose().translate(xPos.toFloat(), yPos.toFloat())

                    var sdfData = SDFData(Vector4f(0f, 0f, 0f, 0.75f), 0.5f, 0.5f)

                    context.pose().pushPose()
                    run {
                        context.pose().translate(-2f, -2f)
                        submitGlowCommand(context, 4f, sdfData)
                    }
                    context.pose().popPose()

                    context.pose().pushPose()
                    run {
                        context.pose().translate(-1.5f, -1.5f)
                        sdfData = SDFData(Vector4f(1f, 1f, 1f, 1f), 0.5f, 0.25f)
                        submitGlowCommand(context, 3f, sdfData)
                    }
                    context.pose().popPose()
                }
                context.pose().popPose()
            }

            fun submitGlowCommand(context: Canvas, size: Float, sdfData: SDFData) {
                val glowCommand: PosTexRectDrawCommand = object : PosTexRectDrawCommand(
                    Render.RenderPipelines.SDF_CIRCLE_GLOW,
                    size,
                    size,
                    0f,
                    0f,
                    1f,
                    1f,
                    mutableListOf(),
                    listOf(
                        UniformPayload(
                            "GlowUniforms", SDFData::class.java, sdfData, SDFData.UBO_SIZE
                        )
                    )
                ) {
                    // SDF 辉光会画到标称尺寸之外, 保守起见视为无界 (不参与合并).
                    override fun localBounds(): LocalBounds? = null
                }
                context.submit(glowCommand)
            }
        }
    }

    @MainThread
    fun toggleActive() {
        if (ClientUtil.hasScreen()) return

        pendingToggle = null
        val last: Boolean = isActive
        isActive = !last
        if (!isActive) InputSystem.cancelRebind()

        val mc = Minecraft.getInstance()
        val w = mc.window
        context.reset()
        if (isActive) {
            ClientUtil.playDownSound()
            val m = mc.mouseHandler
            val width = w.guiScaledWidth
            val height = w.guiScaledHeight
            xPos = width / 2.0
            yPos = height / 2.0
            SDLMouse.SDL_WarpMouseInWindow(w.handle(), (w.width / 2.0).toFloat(), (w.height / 2.0).toFloat())
            startXPos = m.xpos
            startYPos = m.ypos
            context.get().requestLayout()
        } else SDLMouse.SDL_WarpMouseInWindow(w.handle(), startXPos.toFloat(), startYPos.toFloat())
    }

    @MainThread
    private fun requestToggle(bindingContext: InputSystem.BindingContext) {
        if (isActive || bindingContext.action != InputConstants.PRESS) {
            toggleActive()
            return
        }
        pendingToggle = PendingToggle(bindingContext.type, bindingContext.input)
    }

    @MainThread
    fun perform(mouseX: Double, mouseY: Double, deltaPartialTick: Float) {
        if (!isActive) return
        if (posChanged) {
            context.get().dispatchEvent(createMoveEvent(xPos, yPos))
        }
        uiContext.perform(context.get(), mouseX, mouseY, deltaPartialTick)
        posChanged = false
    }

    fun render(
        width: Int, height: Int,
        color: GpuTextureView,
        depth: GpuTextureView,
        drewStencil: AtomicBoolean
    ) {
        if (!isActive) return

        val desc = RenderTargetDescriptor(
            width, height,
            RenderTargetDescriptor.TextureProperties(Vector4f(0f), GpuFormat.RGBA8_UNORM),
            RenderTargetDescriptor.TextureProperties(Vector4f(0f), GpuFormat.D32_FLOAT_S8_UINT)
        )
        val terminalTarget = Render.Buffers.getResourcePool().acquire(desc)

        try {
            uiContext.upload(terminalTarget, false)

            val terminalView = terminalTarget.getColorTextureView() ?: return

            val mc = Minecraft.getInstance()
            val window = mc.window

            val aspectRatio = window.width.toFloat() / window.height
            val fovY = MathUtil.calculateVerticalFov(80.0, aspectRatio.toDouble()).toFloat()

            val viewMatrix = calculateViewMatrix(window, fovY)
            val dynamicTransformsSlice: GpuBufferSlice = createDynamicTransformsSlice(viewMatrix)
            val projectionUBSlice: GpuBufferSlice = createProjectionUboSlice(fovY, aspectRatio)

            val commandEncoder = RenderSystem.getDevice().createCommandEncoder()

            commandEncoder.createRenderPass(
                { "Blit Pass to $color $depth" },
                color, Optional.empty(), depth, OptionalDouble.of(1.0)
            ).use { renderPass ->
                renderPass.setPipeline(RenderSystem.getCompiledPipeline(Render.RenderPipelines.IMAGE_STENCIL_PREMULTIPLIED_ALPHA))
                renderPass.setUniform(
                    "Sampler0",
                    terminalView,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                )
                renderPass.setUniform("Projection", projectionUBSlice)
                renderPass.setUniform("DynamicTransforms", dynamicTransformsSlice)

                renderPass.setVertexBuffer(0, Render.Buffers.getInstance().fsQuadUvColorVBSDC.slice())
                val sequentialBuffer = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
                renderPass.setIndexBuffer(sequentialBuffer.getBuffer(6), sequentialBuffer.type())
                renderPass.drawIndexed(6, 1, 0, 0, 0)
            }
            drewStencil.set(true)
        } finally {
            Render.Buffers.getResourcePool().release(desc, terminalTarget)
        }
    }

    private fun calculateViewMatrix(window: Window, fovY: Float): Matrix4f {
        val guiWidth = window.guiScaledWidth
        val guiHeight = window.guiScaledHeight
        val viewMatrix = Matrix4f().identity()

        val z = -2.5125f
        val scale = (2 * abs(z) * tan((fovY / 2).toDouble()) / guiHeight).toFloat()

        viewMatrix.translate(0.0f, 0.0f, z)
        viewMatrix.scale(scale, scale, scale)

        viewMatrix.translate(0f, 0f, 0f)

        val dx = (xPos - guiWidth - viewMarginRight - R.ui.terminal_hud.main_width / 2f).toFloat()
        val dy = (yPos - guiHeight / 2.0f).toFloat()

        val rotateY = Mth.lerp(viewStateProgress, dx * 0.05f - 5, 0f)
        val rotateX = Mth.lerp(viewStateProgress, -dy * 0.05f - 1, 0f)
        val centerX: Float = (guiWidth / 2.0f) - viewMarginRight - R.ui.terminal_hud.main_width / 2f

        viewMatrix.translate(centerX, 0f, 0.0f)
        viewMatrix.rotate(
            Quaternionf().fromAxisAngleDeg(Vector3f(0.0f, 1.0f, 0.0f), rotateY)
        )
        viewMatrix.rotate(
            Quaternionf().fromAxisAngleDeg(Vector3f(1.0f, 0.0f, 0.0f), rotateX)
        )
        viewMatrix.translate(-centerX, 0f, 0.0f)

        viewMatrix.translate(-(guiWidth / 2.0f), -(guiHeight / 2.0f), 0.0f)

        return viewMatrix
    }

    fun closeApp() {
        context.closeApp()
    }

    @SubscribeEvent
    fun onMouseMove(event: MouseMoveEvent) {
        if (isActive && ClientUtil.hasNoScreen()) {
            val guiScale = Minecraft.getInstance().window.guiScale
            val deltaGuiX = event.xpos / guiScale
            val deltaGuiY = event.ypos / guiScale
            val window = Minecraft.getInstance().window
            val newX = Mth.clamp(deltaGuiX, 0.0, window.guiScaledWidth.toDouble())
            val newY = Mth.clamp(deltaGuiY, 0.0, window.guiScaledHeight.toDouble())
            if (newX != xPos || newY != yPos) {
                xPos = newX
                yPos = newY
                posChanged = true
            }
            if (InputSystem.currentMouseAction == 1 || InputSystem.currentMouseAction == 2) {
                context.get().dispatchEvent(
                    createDragEvent(
                        xPos, yPos, InputSystem.currentMouseButton, deltaGuiX, deltaGuiY
                    )
                )
            }

            SDLMouse.SDL_WarpMouseInWindow(
                Minecraft.getInstance().window.handle(),
                Mth.clamp(event.xpos, 0.0, window.width.toDouble()).toFloat(),
                Mth.clamp(event.ypos, 0.0, window.height.toDouble()).toFloat()
            )

            event.setCanceled(true)
        }
    }

    @SubscribeEvent
    fun onMouseButton(event: MouseButtonEvent) {
        val pending = pendingToggle
        if (!isActive && pending != null) {
            if (pending.type == InputSystem.InputType.MOUSE
                && event.button == pending.input
                && event.action == InputConstants.RELEASE
            ) {
                pendingToggle = null
                toggleActive()
                event.setCanceled(true)
                return
            }
            if (event.action == InputConstants.PRESS) pendingToggle = null
        }
        if (isActive && Minecraft.getInstance().gui.screen() == null) {
            if (InputSystem.matchesKeyBinding(
                    KEY_NAME_TOGGLE,
                    InputSystem.InputType.MOUSE,
                    event.button,
                    event.action,
                    event.modifiers
                )
            ) {
                toggleActive()
                event.setCanceled(true)
                return
            }
            InputSystem.currentMouseButton = event.button
            InputSystem.currentMouseAction = event.action
            InputSystem.currentMouseModifier = event.modifiers
            val inputEvent =
                if (event.action == 1)
                    createPressEvent(xPos, yPos, event.button)
                else
                    createReleaseEvent(xPos, yPos, event.button)
            context.get().dispatchEvent(inputEvent)
            event.setCanceled(true)
        }
    }

    @SubscribeEvent
    fun onMouseScroll(event: MouseScrollEvent) {
        if (!isActive) pendingToggle = null
        if (isActive && ClientUtil.hasNoScreen()) {
            val options = Minecraft.getInstance().options
            val d0 = ((if (options.discreteMouseScroll().get()) sign(event.yOffset) else
                event.yOffset
                    ) * options.mouseWheelSensitivity().get())
            context.get().dispatchEvent(ScrollEvent(xPos, yPos, d0))
            event.setCanceled(true)
        }
    }

    @SubscribeEvent
    fun onKey(event: KeyInputEvent) {
        val pending = pendingToggle
        if (!isActive && pending != null) {
            if (pending.type == InputSystem.InputType.KEYBOARD
                && event.key == pending.input
                && event.action == InputConstants.RELEASE
            ) {
                pendingToggle = null
                toggleActive()
                event.setCanceled(true)
                return
            }
            if (event.action == InputConstants.PRESS && event.key != pending.input) {
                pendingToggle = null
            }
        }
        if (!isActive || !ClientUtil.hasNoScreen()) return
        if (TextInputFocus.isActive() && event.key != InputConstants.KEY_ESCAPE) {
            if (!ClientUtil.isControlKey(event.key, event.scanCode, event.modifiers)) {
                context.get().dispatchEvent(
                    KeyEvent(
                        if (event.action == InputConstants.RELEASE) EventType.KEY_RELEASED
                        else EventType.KEY_PRESSED,
                        event.key,
                        event.scanCode,
                        event.modifiers
                    )
                )
            }
            event.setCanceled(true)
            return
        }
        if (InputSystem.matchesKeyBinding(
                KEY_NAME_TOGGLE,
                InputSystem.InputType.KEYBOARD,
                event.key,
                event.action,
                event.modifiers
            )
        ) {
            toggleActive()
            event.setCanceled(true)
            return
        }
        if (event.action == InputConstants.PRESS) {
            val vanillaEvent = net.minecraft.client.input.KeyEvent(
                event.key,
                event.scanCode,
                event.modifiers
            )
            if (event.key == InputConstants.KEY_ESCAPE) {
                val routed = KeyEvent(
                    EventType.KEY_PRESSED,
                    event.key,
                    event.scanCode,
                    event.modifiers
                )
                context.get().dispatchEvent(routed)
                if (routed.isConsumed) {
                    event.setCanceled(true)
                    return
                }
                toggleActive()
                event.setCanceled(true)
                return
            }
            if (Minecraft.getInstance().options.keyInventory.matches(vanillaEvent)) {
                toggleActive()
                event.setCanceled(true)
                return
            }
        }
        if (!ClientUtil.isControlKey(event.key, event.scanCode, event.modifiers)) {
            val keyEvent = KeyEvent(
                if (event.action == InputConstants.RELEASE) EventType.KEY_RELEASED else EventType.KEY_PRESSED,
                event.key, event.scanCode, event.modifiers
            )
            context.get().dispatchEvent(keyEvent)
            event.setCanceled(true)
        }
    }

    private fun onCharacterInput(event: CharacterEvent): Boolean {
        if (!isActive || !ClientUtil.hasNoScreen()) return false
        val inputEvent = CharTypedEvent(event.codepoint())
        context.get().dispatchEvent(inputEvent)
        return inputEvent.isConsumed
    }

    private fun onPreeditInput(event: PreeditEvent?): Boolean {
        return !(!isActive || !ClientUtil.hasNoScreen()) && TextInputFocus.handlePreedit(event)
    }

    @SubscribeEvent
    fun onScreenChange(@Suppress("unused") event: ScreenEvent.Opening) {
        if (isActive) toggleActive()
    }

    private data class SDFData(
        val color: Vector4f, val radius: Float,
        val softness: Float
    ) : DynamicGpuData {
        override fun write(buffer: ByteBuffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec4(color)
                .putFloat(radius)
                .putFloat(softness)
        }

        companion object {
            val UBO_SIZE: Int = Std140SizeCalculator().putVec4().putFloat().putFloat().get()
        }
    }

    private data class PendingToggle(
        val type: InputSystem.InputType,
        val input: Int
    )

    inner class Context : WidgetContext {
        private var main = FrameLayoutWidget()
        private var content = LinearLayoutWidget()
        private var appContainer = FrameLayoutWidget()
        private var root = createRoot().also { it.dispatchAttached() }

        override fun get(): WidgetContainer {
            return root
        }

        fun reset() {
            viewStateProgress = 0f
            viewMarginRight = 32f

            if (root.isAttached()) root.dispatchDetached()
            main.cancelAnimations()
            main = FrameLayoutWidget()
            content.cancelAnimations()
            content = LinearLayoutWidget()
            appContainer.cancelAnimations()
            appContainer = FrameLayoutWidget()
            root.cancelAnimations()
            root = createRoot().also { it.dispatchAttached() }
        }

        fun createRoot(): FrameLayoutWidget {
            val root = FrameLayoutWidget()

            val appRows = standaloneColumn(spacing = 4f) {
                lp {
                    sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                }
            }

            (APPS + LAST_APPS).chunked(R.ui.terminal_hud.apps_per_row).forEachIndexed { rowIndex, rowApps ->
                val row = appRows.row("app_row_$rowIndex") {
                    lp {
                        sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    }
                }
                rowApps.forEach { app ->
                    row.add(app.name(), createApp(app))
                }
            }

            root.apply {
                main = frame("main") {
                    lp {
                        gravity(Gravity.CENTER_RIGHT)
                        margin(0f, 0f, 32f, 0f)
                        size(R.ui.terminal_hud.main_width, R.ui.terminal_hud.main_height)
                    }

                    fill(R.ui.terminal_hud.background_color, "back")

                    content = column("content", spacing = 2f) {
                        image(R.textures.gui.terminal.icon, "icon") {
                            lp {
                                size(16f, 16f)
                                gravity(Gravity.START)
                                margin(2f, 2f, 0f, 0f)
                            }
                        }

                        fill(R.ui.terminal_hud.primary_color, "split_line") {
                            lp {
                                height(1f)
                                widthMode(SizeMode.MATCH_PARENT)
                                padding(2f, 0f)
                            }
                        }

                        scrollPanel(name = "apps", content = appRows) {
                            lp {
                                widthMode(SizeMode.MATCH_PARENT)
                                gravity(Gravity.CENTER)
                                padding(4f, 4f, 4f, 2f)
                            }

                            weight(1f)
                        }
                    }

                    appContainer = frame("app_container") {
                        lp {
                            sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT)
                        }

                        isEnabled = false
                        visibility = Widget.Visibility.INVISIBLE
                        alpha = 0f
                    }
                }
            }

            return root
        }

        fun openApp(app: App) {
            content.isEnabled = false

            val fadeOut = animateAlpha(content, 0f, baseDuration = 100)
            fadeOut.addListener(object : AnimatorListener {
                override fun onAnimationEnd(animation: Animator) {
                    content.visibility = Widget.Visibility.INVISIBLE
                }
            })

            appContainer.isEnabled = true
            appContainer.visibility = Widget.Visibility.VISIBLE
            appContainer.clearChildren()
            appContainer.addChild("current_app", app.createContext().get())
            animateAlpha(appContainer, 1f, baseDuration = 100, startDelay = 100)

            animateMain(1f, root, main)
        }

        fun closeApp() {
            content.visibility = Widget.Visibility.VISIBLE
            animateAlpha(content, 1f, baseDuration = 75, startDelay = 75)
            content.isEnabled = true

            if (appContainer.visibility == Widget.Visibility.INVISIBLE) return

            appContainer.isEnabled = false
            val fadeOut = animateAlpha(appContainer, 0f, baseDuration = 75)
            fadeOut.addListener(object : AnimatorListener {
                override fun onAnimationEnd(animation: Animator) {
                    appContainer.visibility = Widget.Visibility.INVISIBLE
                }
            })

            animateMain(0f, root, main)
        }

        fun animateMain(target: Float, root: Widget, main: Widget) {
            val currentProgress = viewStateProgress
            main.cancelAnimations()

            val distance = abs(target - currentProgress)
            val baseDuration = 400L
            val newDuration = (baseDuration * distance).toLong()

            val animator = ofFloat(currentProgress, target)
                .setDuration(newDuration)
                .setInterpolator(EasingFunctions.EASE_OUT_CUBIC)

            animator.addUpdateListener { anim ->
                applyViewState(anim.animatedValue, root, main)
            }

            animator.addListener(object : AnimatorListener {
                override fun onAnimationEnd(animation: Animator) {
                    viewStateProgress = target
                    applyViewState(target, root, main)
                    main.translationX = 0f
                    val lp = main.layoutParams as FrameLayoutWidget.LayoutParams
                    lp.gravity = if (target == 1f) Gravity.CENTER else Gravity.CENTER_RIGHT
                    main.layoutParams = lp
                    content.isEnabled = target != 1f
                }
            })

            main.startAnimation(animator)
        }

        private fun applyViewState(progress: Float, root: Widget, main: Widget) {
            viewStateProgress = progress

            main.width = Mth.lerp(progress, R.ui.terminal_hud.main_width, R.ui.terminal_hud.unfolded_main_width)

            viewMarginRight = (1f - progress) * 32f
            val lp = main.layoutParams
            if (lp.marginRight != viewMarginRight) {
                lp.marginRight = viewMarginRight
                main.layoutParams = lp
            }

            val parentWidth = root.width
            val rightAlignedX = parentWidth - viewMarginRight - main.width
            val centerX = (parentWidth - main.width) / 2f
            val desiredX = Mth.lerp(progress, rightAlignedX, centerX)
            main.translationX = desiredX - main.x
        }

        private fun animateAlpha(
            widget: Widget,
            targetAlpha: Float,
            baseDuration: Long,
            startDelay: Long = 0
        ): ObjectAnimator {
            val currentAlpha = widget.alpha
            val distance = abs(targetAlpha - currentAlpha)
            val duration = (baseDuration * distance).toLong().coerceAtLeast(1)

            val anim = ofFloat({ widget.alpha = it }, currentAlpha, targetAlpha)
                .setDuration(duration)
                .setStartDelay(startDelay)

            widget.cancelAnimations()
            widget.startAnimation(anim)
            return anim
        }

        fun createApp(app: App): LinearLayoutWidget {
            val icon = app.icon()
            val name = app.name()

            val size = 48
            val height = 62

            return standaloneColumn(spacing = 1f) {
                lp {
                    size(size.toFloat(), height.toFloat())
                }

                button("icon_area") {
                    lp {
                        size(size.toFloat(), size.toFloat())
                    }

                    onClick { openApp(app) }

                    val back = image(R.textures.gui.element.app_back, "back") {
                        rgb(0.8f, 0.8f, 0.8f)
                    }

                    val iconWidget = image(icon, "icon") {
                        lp {
                            size(size / 2f, size / 2f)
                            gravity(Gravity.CENTER)
                        }

                        rgb(0.9f, 0.9f, 0.9f)
                    }

                    val progressState = AtomicReference(0f)
                    val updateState = Consumer { progress: Float ->
                        progressState.set(progress)
                        scaleX = 1.0f + 0.2f * progress
                        scaleY = 1.0f + 0.2f * progress
                        back.setBrightness(0.8f + 0.2f * progress)
                        iconWidget.setBrightness(0.9f + 0.1f * progress)
                    }

                    stateListAnimator = StateListAnimator().apply {
                        addState(
                            Widget.HOVERED,
                            ofFloat({ progressState.get()!! }, updateState, 1.0f)
                                .setDuration(100)
                                .setInterpolator(EasingFunctions.EASE_OUT_SINE)
                        )
                        addState(
                            Widget.NONE,
                            ofFloat({ progressState.get()!! }, updateState, 0.0f)
                                .setDuration(100)
                                .setInterpolator(EasingFunctions.EASE_OUT_SINE)
                        )
                    }
                }

                text(name, "name") {
                    lp {
                        height(0f)
                        gravity(Gravity.CENTER)
                    }

                    weight(1f)

                    gravity = Gravity.CENTER
                    ellipsize = Ellipsize.MARQUEE
                    marqueeFadeSize = 6f
                }
            }
        }
    }

    companion object {
        const val CONFIG_KEY: String = "terminal_hud_config"
        const val KEY_NAME_TOGGLE: String = "terminal_hud_config_toggle"

        @Volatile
        var isActive: Boolean = false
            private set

        lateinit var INSTANCE: TerminalHud

        fun handleCharacterInput(event: CharacterEvent): Boolean {
            return this::INSTANCE.isInitialized && INSTANCE.onCharacterInput(event)
        }

        fun handlePreeditInput(event: PreeditEvent?): Boolean {
            return this::INSTANCE.isInitialized && INSTANCE.onPreeditInput(event)
        }

        private val APPS: MutableList<App> = ArrayList<App>()
        private val LAST_APPS: MutableList<App> = ArrayList<App>()

        fun addApp(app: App) {
            if (app !in APPS && app !in LAST_APPS) APPS.add(app)
        }

        fun initMain() {
            INSTANCE = TerminalHud()
            NeoForge.EVENT_BUS.register(INSTANCE)
        }

        fun getBlurRadius(): Float = INSTANCE.config.blurRadius.coerceIn(0f, 20f)

        fun setBlurRadius(value: Float) {
            val terminal = INSTANCE
            terminal.config.blurRadius = value.coerceIn(0f, 20f)
            AcademyCraftClient.Config.INSTANCE.save()
        }

        private fun createDynamicTransformsSlice(viewMatrix: Matrix4f): GpuBufferSlice {
            return RenderSystem.getDynamicUniforms()
                .writeTransform(
                    viewMatrix,
                    Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
                    Vector3f(),
                    Matrix4f()
                )
        }

        private fun createProjectionUboSlice(fovY: Float, aspectRatio: Float): GpuBufferSlice {
            val projectionMatrix = Matrix4f().perspective(fovY, aspectRatio, 1.0f, 1000.0f)
            return Render.Buffers.getInstance().getProjectionUB(projectionMatrix).slice()
        }
    }
}
