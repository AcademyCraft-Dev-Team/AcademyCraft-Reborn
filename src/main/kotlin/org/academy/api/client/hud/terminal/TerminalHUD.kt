package org.academy.api.client.hud.terminal

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.platform.Window
import com.mojang.blaze3d.resource.RenderTargetDescriptor
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.DynamicUniformStorage.DynamicUniform
import net.minecraft.util.Mth
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge
import org.academy.AcademyCraftClient
import org.academy.AcademyCraftConfig
import org.academy.api.client.Render
import org.academy.api.client.Resource
import org.academy.api.client.app.App
import org.academy.api.client.gui.animation.*
import org.academy.api.client.gui.animation.ObjectAnimator.Companion.ofFloat
import org.academy.api.client.gui.animation.ValueAnimator.Companion.ofFloat
import org.academy.api.client.gui.command.PosTexRectDrawCommand
import org.academy.api.client.gui.event.EventType
import org.academy.api.client.gui.event.KeyEvent
import org.academy.api.client.gui.event.MouseEvent.Companion.createDragEvent
import org.academy.api.client.gui.event.MouseEvent.Companion.createMoveEvent
import org.academy.api.client.gui.event.MouseEvent.Companion.createPressEvent
import org.academy.api.client.gui.event.MouseEvent.Companion.createReleaseEvent
import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.gui.event.ScrollEvent
import org.academy.api.client.gui.imgui.ImGuiUIDebugger
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.render.UiContext
import org.academy.api.client.gui.widget.*
import org.academy.api.client.input.*
import org.academy.api.client.input.InputSystem.InputPair
import org.academy.api.client.render.UniformPayload
import org.academy.api.client.thread.MainThread
import org.academy.api.client.util.ClientUtil
import org.academy.api.common.util.MathUtil
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.glfw.GLFW
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tan

class TerminalHUD private constructor() {
    private var context: Context = Context()
    private val uiContext: UiContext

    /**
     * 0.0f : 面向鼠标喵
     * <br></br>
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

    init {
        AcademyCraftConfig.registerTypeHandler(CONFIG_KEY, TerminalConfig.Action.INSTANCE)
        val config: TerminalConfig = AcademyCraftClient.Config.INSTANCE.getConfig(CONFIG_KEY)

        val defaultKey = InputPair(
            InputSystem.InputType.KEYBOARD,
            InputSystem.KeyInfo(
                LinkedHashSet(setOf(GLFW.GLFW_KEY_RIGHT_ALT)),
                GLFW.GLFW_RELEASE
            )
        )
        InputSystem.addKeyBinding(
            KEY_NAME_TOGGLE,
            config.getKeyBinding(KEY_NAME_TOGGLE, defaultKey)
        ) {
            if (INSTANCE != null) INSTANCE!!.toggleActive()
        }

        uiContext = createUiContext()
    }

    private fun createUiContext(): UiContext {
        return object : UiContext() {
            override fun generateCommands(
                context: RenderContext, rootWidget: WidgetContainer, mouseX: Double, mouseY: Double, partialTick: Float
            ) {
                super.generateCommands(context, rootWidget, mouseX, mouseY, partialTick)

                context.pose().pushPose()
                context.drawOrder().push()
                run {
                    val max = context.commands.maxByOrNull { it.drawOrder }?.drawOrder ?: 0
                    context.drawOrder().advance(max + 1)
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
                        context.drawOrder().advance()
                        context.pose().translate(-1.5f, -1.5f)
                        sdfData = SDFData(Vector4f(1f, 1f, 1f, 1f), 0.5f, 0.25f)
                        submitGlowCommand(context, 3f, sdfData)
                    }
                    context.pose().popPose()
                }
                context.drawOrder().pop()
                context.pose().popPose()
            }

            fun submitGlowCommand(context: RenderContext, size: Float, sdfData: SDFData) {
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
                ) {}
                context.submit(glowCommand)
            }
        }
    }

    @MainThread
    fun toggleActive() {
        if (ClientUtil.hasScreen()) return

        val last: Boolean = isActive
        isActive = !last

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
            GLFW.glfwSetCursorPos(w.handle(), w.width / 2.0, w.height / 2.0)
            startXPos = m.xpos
            startYPos = m.ypos
            context.get().requestLayout()
        } else GLFW.glfwSetCursorPos(w.handle(), startXPos, startYPos)
    }

    @MainThread
    fun perform(mouseX: Double, mouseY: Double, deltaPartialTick: Float) {
        if (!isActive) return
        uiContext.perform(context.get(), mouseX, mouseY, deltaPartialTick)
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
            true, Vector4f(0f), GpuFormat.RGBA8_UNORM
        )
        val terminalTarget = Render.Buffers.getResourcePool().acquire(desc)

        try {
            uiContext.upload(terminalTarget, false)
            ImGuiUIDebugger.render(terminalTarget, context.get())

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
                color, Optional.empty(), depth, OptionalDouble.empty()
            ).use { renderPass ->
                renderPass.setPipeline(Render.RenderPipelines.IMAGE_STENCIL_PREMULTIPLIED_ALPHA)
                renderPass.bindTexture(
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

        val dx = (xPos - guiWidth - viewMarginRight - MAIN_WIDTH / 2f).toFloat()
        val dy = (yPos - guiHeight / 2.0f).toFloat()

        val rotateY = Mth.lerp(viewStateProgress, dx * 0.05f - 5, 0f)
        val rotateX = Mth.lerp(viewStateProgress, -dy * 0.05f - 1, 0f)
        val centerX: Float = (guiWidth / 2.0f) - viewMarginRight - MAIN_WIDTH / 2f

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
    fun onTick(@Suppress("unused") event: ClientTickEvent.Post) {
        context.get().tick()
    }

    @SubscribeEvent
    fun onMouseMove(event: MouseMoveEvent) {
        if (isActive && ClientUtil.hasNoScreen()) {
            val guiScale = Minecraft.getInstance().window.guiScale
            val deltaGuiX = event.xpos / guiScale
            val deltaGuiY = event.ypos / guiScale
            val window = Minecraft.getInstance().window
            xPos = Mth.clamp(deltaGuiX, 0.0, window.guiScaledWidth.toDouble())
            yPos = Mth.clamp(deltaGuiY, 0.0, window.guiScaledHeight.toDouble())
            context.get().dispatchEvent(createMoveEvent(xPos, yPos))
            if (InputSystem.currentMouseAction == 1 || InputSystem.currentMouseAction == 2) {
                context.get().dispatchEvent(
                    createDragEvent(
                        xPos, yPos, InputSystem.currentMouseButton, deltaGuiX, deltaGuiY
                    )
                )
            }

            GLFW.glfwSetCursorPos(
                Minecraft.getInstance().window.handle(),
                Mth.clamp(event.xpos, 0.0, window.width.toDouble()),
                Mth.clamp(event.ypos, 0.0, window.height.toDouble())
            )

            event.setCanceled(true)
        }
    }

    @SubscribeEvent
    fun onMouseButton(event: MouseButtonEvent) {
        if (isActive && Minecraft.getInstance().gui.screen() == null) {
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
        if (isActive
            && ClientUtil.hasNoScreen()
            && !ClientUtil.isControlKey(event.key, event.scanCode, event.modifiers)
        ) {
            val keyEvent = KeyEvent(
                if (event.action == InputConstants.RELEASE) EventType.KEY_RELEASED else EventType.KEY_PRESSED,
                event.key, event.scanCode, event.modifiers
            )
            context.get().dispatchEvent(keyEvent)
            if (event.action == InputConstants.RELEASE && !keyEvent.isConsumed) toggleActive()
            event.setCanceled(true)
        }
    }

    @SubscribeEvent
    fun onScreenChange(@Suppress("unused") event: ScreenEvent.Opening) {
        if (isActive) toggleActive()
    }

    private data class SDFData(
        val color: Vector4f, val radius: Float,
        val softness: Float
    ) : DynamicUniform {
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

    inner class Context : WidgetContext {
        private var main = FrameLayoutWidget()
        private var content = LinearLayoutWidget()
        private var appContainer = FrameLayoutWidget()
        private var root = createRoot()

        override fun get(): WidgetContainer {
            return root
        }

        fun reset() {
            viewStateProgress = 0f
            viewMarginRight = 32f

            main.cancelAnimations()
            main = FrameLayoutWidget()
            content.cancelAnimations()
            content = LinearLayoutWidget()
            appContainer.cancelAnimations()
            appContainer = FrameLayoutWidget()
            root.cancelAnimations()
            root = createRoot()
        }

        fun createRoot(): FrameLayoutWidget {
            val root = FrameLayoutWidget()
            run {
                main.layoutParams = FrameLayoutWidget.LayoutParams()
                    .gravity(Gravity.CENTER_RIGHT)
                    .margin(0f, 0f, 32f, 0f)
                    .size(MAIN_WIDTH, MAIN_HEIGHT)
                root.addChild("main", main)
                run {
                    val background = FillWidget(COLOR)
                    main.addChild("back", background)
                    content.setOrientation(Orientation.VERTICAL)
                    content.setSpacing(2f)
                    main.addChild("content", content)
                    run {
                        val logo = ImageWidget(Resource.Textures.ICON_TERMINAL)
                        logo.layoutParams = LinearLayoutWidget.LayoutParams()
                            .size(16f, 16f)
                            .gravity(Gravity.START)
                            .margin(2f, 2f, 0f, 0f)

                        content.addChild("icon", logo)

                        val splitLine = FillWidget(-0x1)
                        splitLine.layoutParams = LinearLayoutWidget.LayoutParams()
                            .height(1f)
                            .widthMode(SizeMode.MATCH_PARENT)
                            .padding(2f, 0f)

                        content.addChild("split_line", splitLine)

                        val apps = ScrollPanelWidget()
                        apps.layoutParams = LinearLayoutWidget.LayoutParams()
                            .weight(1f)
                            .widthMode(SizeMode.MATCH_PARENT)
                            .gravity(Gravity.CENTER)
                            .padding(4f, 4f, 4f, 2f)

                        content.addChild("apps", apps)
                        run {
                            val appRows = LinearLayoutWidget()
                            appRows.setOrientation(Orientation.VERTICAL)
                            appRows.setSpacing(4f)
                            appRows.layoutParams = WidgetContainer.LayoutParams()
                                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)

                            apps.addChild("app_rows", appRows)
                            run {
                                val rowOne = LinearLayoutWidget()
                                rowOne.setOrientation(Orientation.HORIZONTAL)
                                rowOne.layoutParams = LinearLayoutWidget.LayoutParams()
                                    .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)

                                appRows.addChild("row_one", rowOne)
                                run {
                                    for (app in APPS) {
                                        rowOne.addChild(app.name(), createApp(app))
                                    }
                                }
                            }
                        }
                    }
                    appContainer.layoutParams = FrameLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT)

                    appContainer.isEnabled = false
                    appContainer.visibility = Widget.Visibility.INVISIBLE
                    appContainer.alpha = 0f
                    main.addChild("app_container", appContainer)
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

            main.width = Mth.lerp(progress, MAIN_WIDTH, UNFOLDED_MAIN_WIDTH)

            viewMarginRight = (1f - progress) * 32f
            val lp = main.layoutParams
            main.layoutParams = lp.marginRight(viewMarginRight)

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
            val layout = LinearLayoutWidget()
            layout.setSpacing(1f)
            layout.setOrientation(Orientation.VERTICAL)
            layout.layoutParams = LinearLayoutWidget.LayoutParams()
                .size(size.toFloat(), height.toFloat())

            run {
                val iconArea = ButtonWidget()
                iconArea.layoutParams = LinearLayoutWidget.LayoutParams()
                    .size(size.toFloat(), size.toFloat())

                iconArea.onClickListener = OnClickListener { openApp(app) }
                layout.addChild("icon_area", iconArea)
                run {
                    val back = ImageWidget(Resource.Textures.APP_BACK)
                    back.setColor(0.8f, 0.8f, 0.8f)
                    iconArea.addChild("back", back)

                    val iconWidget = ImageWidget(icon)
                    iconWidget.setColor(0.9f, 0.9f, 0.9f)
                    iconWidget.layoutParams = FrameLayoutWidget.LayoutParams()
                        .size(size / 2f, size / 2f)
                        .gravity(Gravity.CENTER)

                    iconArea.addChild("icon", iconWidget)

                    val progressState = AtomicReference(0f)
                    val updateState = Consumer { progress: Float ->
                        progressState.set(progress)
                        iconArea.scale = 1.0f + 0.2f * progress
                        back.setBrightness(0.8f + 0.2f * progress)
                        iconWidget.setBrightness(0.9f + 0.1f * progress)
                    }

                    val animator = StateListAnimator()
                    animator.addState(
                        Widget.HOVERED,
                        ofFloat({ progressState.get()!! }, updateState, 1.0f)
                            .setDuration(100)
                            .setInterpolator(EasingFunctions.EASE_OUT_SINE)
                    )
                    animator.addState(
                        Widget.NONE,
                        ofFloat({ progressState.get()!! }, updateState, 0.0f)
                            .setDuration(100)
                            .setInterpolator(EasingFunctions.EASE_OUT_SINE)
                    )
                    iconArea.stateListAnimator = animator
                }
                val nameWidget = LabelWidget(name)
                nameWidget.layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .height(0f)
                    .gravity(Gravity.CENTER)
                layout.addChild("name", nameWidget)
            }
            return layout
        }
    }

    companion object {
        const val COLOR: Int = 0x40000000
        const val MAIN_WIDTH: Float = 150f
        const val UNFOLDED_MAIN_WIDTH: Float = 384f
        const val MAIN_HEIGHT: Float = 200f
        const val CONFIG_KEY: String = "terminal_hud_config"
        const val KEY_NAME_TOGGLE: String = "terminal_hud_config_toggle"

        @Volatile
        var isActive: Boolean = false
            private set

        private var INSTANCE: TerminalHUD? = null

        private val APPS: MutableList<App> = ArrayList<App>()

        val instance: TerminalHUD
            get() {
                checkNotNull(INSTANCE) { "TerminalHUD has not been initialized." }
                return INSTANCE!!
            }

        fun addApp(app: App) {
            APPS.add(app)
        }

        fun initMain() {
            INSTANCE = TerminalHUD()
            NeoForge.EVENT_BUS.register(INSTANCE)
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