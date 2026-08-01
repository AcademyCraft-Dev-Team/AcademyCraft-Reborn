package org.academy.internal.client.gui.screen

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.common.NeoForge
import org.academy.AcademyCraft
import org.academy.api.client.ability.AbilitySystemClient
import org.academy.api.client.gui.animation.*
import org.academy.api.client.gui.command.*
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.util.GlyphCommandGenerator
import org.academy.api.client.gui.util.WirelessPanelUtil
import org.academy.api.client.gui.widget.*
import org.academy.api.client.resources.R
import org.academy.api.common.ability.*
import org.academy.api.common.util.L10n
import org.academy.api.common.wireless.GetCurrentNodePacket
import org.academy.internal.common.ability.level0.Level0
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity
import org.apache.commons.lang3.RandomStringUtils
import org.misaka.MisakaNetworkClient
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class AbilityDeveloperScreen(val mainPos: BlockPos) : UiScreen(Component.empty()) {
    private val blockEntity: AbilityDeveloperBlockEntity
    private lateinit var area: FrameLayoutWidget
    private lateinit var mainWidget: FrameLayoutWidget
    private var isConsoleMode: Boolean = false
    private lateinit var consoleOutputs: LinearLayoutWidget
    private lateinit var consoleScrollPanel: ScrollPanelWidget
    private var activeCover: FrameLayoutWidget? = null

    private val maxDuSkills = 10f

    init {
        val level = minecraft.level ?: throw RuntimeException("Level is null")
        val entity = level.getBlockEntity(mainPos)
        if (entity is AbilityDeveloperBlockEntity) {
            blockEntity = entity
            entity.setOpen(true)
        } else {
            throw RuntimeException("Invalid block entity at $mainPos")
        }
    }

    override fun isPauseScreen(): Boolean = false

    override fun onClose() {
        super.onClose()
        blockEntity.setOpen(false)
        NeoForge.EVENT_BUS.unregister(this)
        MisakaNetworkClient.send(StopDevPacket(mainPos))
        AbilitySystemClient.resetDevState()
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        super.mouseMoved(mouseX, mouseY)
    }

    override fun onInit() {
        val main = FrameLayoutWidget()
        main.layoutParams = FrameLayoutWidget.LayoutParams()
            .gravity(Gravity.CENTER)
            .size(PANEL_MAIN_WIDTH, PANEL_MAIN_HEIGHT)
        mainWidget = main

        val anim = ObjectAnimator.ofFloat(
            { main.translationY = it },
            -PANEL_MAIN_HEIGHT, 0f
        ).setDuration(500L).setInterpolator(EasingFunctions.EASE_OUT_EXPO)

        root.addChild("main", main) {
            val parentLeft = FrameLayoutWidget()
            parentLeft.layoutParams = FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
                .margin(4f, 0f, 0f, 0f)
                .size(108.5f, 187f)
            main.addChild("parent_left", parentLeft) {
                val leftBg =
                    ImageWidget(AcademyCraft.academy("textures/gui/developer/parent_background_developerleft.png"))
                leftBg.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
                parentLeft.addChild("left_bg", leftBg)

                val uiLeft = ImageWidget(R.textures.UI_DEVELOPER_PANEL_LEFT)
                uiLeft.layoutParams = WidgetContainer.LayoutParams()
                    .gravity(Gravity.RIGHT or Gravity.CENTER_VERTICAL)
                    .size(108.5f, 187f)
                parentLeft.addChild("ui_left", uiLeft)

                val panelMachine = FrameLayoutWidget()
                panelMachine.layoutParams = WidgetContainer.LayoutParams()
                    .gravity(Gravity.TOP_LEFT)
                    .size(108.5f, 187f)
                parentLeft.addChild("panel_machine", panelMachine) {
                    fillMachinePanel(panelMachine)
                }

                val panelAbility = FrameLayoutWidget()
                panelAbility.layoutParams = WidgetContainer.LayoutParams()
                    .gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
                    .margin(2f, -20f, 0f, 0f)
                    .size(104f, 32f)
                parentLeft.addChild("panel_ability", panelAbility) {
                    fillAbilityPanel(panelAbility)
                }
            }

            val parentRight = FrameLayoutWidget()
            parentRight.layoutParams = FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.RIGHT or Gravity.CENTER_VERTICAL)
                .margin(0f, 0f, 4f, 0f)
                .size(278f, 187f)
            main.addChild("parent_right", parentRight) {
                val rightBg =
                    ImageWidget(AcademyCraft.academy("textures/gui/developer/parent_background_developerright.png"))
                rightBg.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
                parentRight.addChild("right_bg", rightBg)

                val uiRight = ImageWidget(R.textures.UI_DEVELOPER_PANEL_RIGHT)
                uiRight.layoutParams = WidgetContainer.LayoutParams()
                    .gravity(Gravity.RIGHT or Gravity.CENTER_VERTICAL)
                    .size(278f, 187f)
                parentRight.addChild("ui_right", uiRight)

                val a = FrameLayoutWidget()
                a.layoutParams = WidgetContainer.LayoutParams()
                    .gravity(Gravity.TOP_LEFT)
                    .margin(10f, 18f, 0f, 0f)
                    .size(257f, 139f)
                area = a
                parentRight.addChild("area", a) {
                    val category = AbilitySystemClient.getCategory()
                    if (category !is Level0) {
                        fillSkillTreeArea(area)
                    } else {
                        isConsoleMode = true
                        fillConsoleArea(area)
                    }
                }
            }
        }

        anim.addListener(object : AnimatorListener {
            override fun onAnimationEnd(animation: Animator) {
                if (isConsoleMode) {
                    startConsoleBoot()
                }
            }
        })
        main.startAnimation(anim)
    }

    private fun fillAbilityPanel(panel: FrameLayoutWidget) {
        val category = AbilitySystemClient.getCategory()
        val level = AbilitySystemClient.getLevel()
        val levelProgress = AbilitySystemClient.getAbilityExp()

        val logoAbility = FrameLayoutWidget()
        logoAbility.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
            .size(32f, 32f)
        panel.addChild("logo_ability", logoAbility) {
            val icon = ImageWidget(category.getDeveloperIcon())
            icon.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.CENTER)
                .size(32f, 32f)
            logoAbility.addChild("icon", icon)
        }

        val nameLabel = LabelWidget(category.getDisplayName())
        nameLabel.baseFontSize = 13f
        nameLabel.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.TOP_LEFT)
            .margin(31f, 2f, 0f, 0f)
            .size(70f, 12f)
        panel.addChild("text_abilityname", nameLabel)

        val progBack = ProgressBarWidget()
        progBack.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.TOP_LEFT)
            .margin(31f, 13.25f, 0f, 0f)
            .size(70f, 1.5f)
        progBack.backgroundColor = 0x4C666666.toInt()
        progBack.setProgressColor(0x4C666666.toInt())
        progBack.setProgress(100f)
        panel.addChild("logo_progress_back", progBack)

        val progFore = ProgressBarWidget()
        progFore.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.TOP_LEFT)
            .margin(31f, 13.25f, 0f, 0f)
            .size(70f, 1.5f)
        progFore.backgroundColor = 0x00000000.toInt()
        progFore.setProgressColor(-0x1)
        progFore.setProgress(levelProgress * 100f)
        panel.addChild("logo_progress", progFore)

        val expLabel = LabelWidget("EXP ${(levelProgress * 100f).toInt()}%")
        expLabel.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.TOP_LEFT)
            .margin(30f, 15.5f, 0f, 0f)
            .size(42f, 10f)
        panel.addChild("text_exp", expLabel)

        if (AbilitySystemClient.canLevelUp()) {
            val upgradeBtn = ButtonWidget()
            upgradeBtn.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.TOP_LEFT)
                .margin(60f, 14.5f, 0f, 0f)
                .size(48f, 15f)
            upgradeBtn.onClickListener = OnClickListener { addCover(createLevelUpCover()) }
            panel.addChild("btn_upgrade", upgradeBtn) {
                val btnTex = ImageWidget(AcademyCraft.academy("textures/gui/developer/button_learn.png"))
                btnTex.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
                upgradeBtn.addChild("tex", btnTex)
            }
        } else {
            val levelLabel = LabelWidget("Level ${level.levelCode}")
            levelLabel.baseFontSize = 9f
            levelLabel.setRed(0.09f)
            levelLabel.setGreen(0.46f)
            levelLabel.setBlue(0.84f)
            levelLabel.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.TOP_RIGHT)
                .margin(0f, 16f, 3f, 0f)
                .size(42f, 12f)
            panel.addChild("text_level", levelLabel)
        }
    }

    private fun fillMachinePanel(panel: FrameLayoutWidget) {
        val machineBg =
            ImageWidget(AcademyCraft.academy("textures/gui/developer/parent_background_developermachine.png"))
        machineBg.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        panel.addChild("machine_bg", machineBg)

        val wirelessLabel = LabelWidget("Current Node:")
        wirelessLabel.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
            .margin(4.25f, 34f, 0f, 0f)
            .size(100f, 12f)
        panel.addChild("text_wireless", wirelessLabel)

        val nodeBtn = ButtonWidget()
        nodeBtn.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
            .margin(4.25f, 58f, 0f, 0f)
            .size(100f, 16f)
        nodeBtn.onClickListener = {
            val cover = createCover()
            run {
                val wirelessPage = WirelessPanelUtil.create(blockEntity.blockPos, true)
                wirelessPage.layoutParams.gravity(Gravity.CENTER)
                cover.addChild("wireless_page", wirelessPage)
            }
            addCover(cover)
        }
        panel.addChild("button_wireless", nodeBtn) {
            val bar = ImageWidget(AcademyCraft.academy("textures/gui/element/element_background300x32.png"))
            bar.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            nodeBtn.addChild("bar", bar)

            val nodeName = LabelWidget("N/A")
            nodeName.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.CENTER_LEFT)
                .margin(26f, 0f, 0f, 0f)
                .size(70f, 12f)
            nodeBtn.addChild("text_nodename", nodeName)

            val nodeIcon = ImageWidget(R.textures.ICON_NODE)
            nodeIcon.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.TOP_LEFT)
                .margin(7f, 2f, 0f, 0f)
                .size(12f, 12f)
            nodeBtn.addChild("logo_node", nodeIcon)

            MisakaNetworkClient.FUTURE_MANAGER.send(GetCurrentNodePacket(blockEntity.blockPos)) {
                if (it != null) nodeName.text = it.nodeName
            }
        }

        val powerLabel = LabelWidget("Power:")
        powerLabel.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
            .margin(4.25f, 86f, 0f, 0f)
            .size(100f, 12f)
        panel.addChild("text_power", powerLabel)

        val powerBar = object : ProgressBarWidget() {
            override fun tick() {
                super.tick()
                setProgress(
                    if (blockEntity.maxEnergyStorage > 0)
                        blockEntity.energyStored.toFloat() / blockEntity.maxEnergyStorage * 100f
                    else 0f
                )
            }
        }
        powerBar.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
            .margin(5.75f, 111f, 0f, 0f)
            .size(97f, 8f)
        powerBar.backgroundColor = 0x40000000
        powerBar.setProgressColor(0xFFFCC532.toInt())
        panel.addChild("progress_power", powerBar)

        val syncLabel = LabelWidget("Sync Rate:")
        syncLabel.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
            .margin(4.25f, 132f, 0f, 0f)
            .size(100f, 12f)
        panel.addChild("text_syncrate", syncLabel)

        val syncBar = ProgressBarWidget()
        syncBar.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
            .margin(5.75f, 155f, 0f, 0f)
            .size(97f, 8f)
        syncBar.backgroundColor = 0x40000000
        syncBar.setProgressColor(0xFF32A4FC.toInt())
        syncBar.setProgress(100f)
        panel.addChild("progress_syncrate", syncBar)
    }

    private fun fillConsoleArea(area: FrameLayoutWidget) {
        val scrollPanel = ScrollPanelWidget()
        scrollPanel.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        consoleScrollPanel = scrollPanel
        area.addChild("scroll_panel", scrollPanel) {
            consoleOutputs = LinearLayoutWidget()
            consoleOutputs.layoutParams = LinearLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .padding(4f)
            consoleOutputs.spacing = 4f
            scrollPanel.addChild("outputs", consoleOutputs)
        }
    }

    private fun startConsoleBoot() {
        val outputs = consoleOutputs
        val welcomeString = "Welcome to Academy OS, Ver 0.0.1"
        addOutput(outputs, welcomeString) {
            addOutput(outputs, "Copyright (c) Academy Tech. All rights reserved.") {
                val playerName = Minecraft.getInstance().player?.name?.string ?: "Unknown"
                addOutput(outputs, "User $playerName detected, System booting......") {
                    val label = LabelWidget("")
                    label.layoutParams = WidgetContainer.LayoutParams()
                        .gravity(Gravity.BOTTOM_LEFT)
                    val list = (10..100 step 10).toMutableList()
                    for (i in 8 downTo 0) {
                        val min = if (i == 0) 10 else list[i - 1] + 1
                        val max = list[i + 1] - 1
                        list[i] = (min..max).random()
                    }

                    val bootAnim = ObjectAnimator.ofFloat(
                        { f ->
                            val value = f.toInt()
                            val progress = list.minBy { abs(it - value) }
                            label.text = if (progress == 100) "Boot Failed." else "$progress%"
                        }, 0f, 100f
                    ).setDuration(2500)

                    bootAnim.addListener(object : AnimatorListener {
                        override fun onAnimationEnd(animation: Animator) {
                            addOutput(outputs, "FATAL: User's ability category is invalid, booting aborted.") {
                                addOutput(outputs, "Type `learn` to acquire new category.") {
                                    val initialInputArea = createCommandInputArea(outputs)
                                    outputs.addChild("input_area", initialInputArea)
                                }
                            }
                        }
                    })
                    label.startAnimation(bootAnim)
                    outputs.addChild("label_progress", label)
                }
            }
        }
    }

    private fun addOutput(outputs: LinearLayoutWidget, text: String, onEnd: () -> Unit = {}) {
        val label = object : LabelWidget(text) {
            var progress = 0f
            override fun generateDrawCommands(
                text: String, fontSize: Float, thickness: Float,
                red: Float, green: Float, blue: Float, alpha: Float
            ): MutableList<GlyphDrawCommand> {
                val list = super.generateDrawCommands(text, fontSize, thickness, red, green, blue, alpha)
                return list.subList(0, (list.size * progress).toInt().coerceIn(0, list.size))
            }
        }
        label.layoutParams = WidgetContainer.LayoutParams().gravity(Gravity.BOTTOM_LEFT)
        outputs.addChild("label_${text.hashCode()}_${RandomStringUtils.insecure().nextAlphabetic(4)}", label)

        label.startAnimation(
            ObjectAnimator.ofFloat({ label.progress = it; label.invalidate() }, 0f, 1f)
                .setDuration(text.length * 25L)
                .addListener(object : AnimatorListener {
                    override fun onAnimationEnd(animation: Animator) {
                        consoleScrollPanel.scrollToEnd()
                        onEnd()
                    }
                })
        )
    }

    private fun createCommandInputArea(outputs: LinearLayoutWidget): LinearLayoutWidget {
        val inputArea = LinearLayoutWidget()
        inputArea.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.BOTTOM_LEFT)
            .height(8f)
            .widthMode(SizeMode.MATCH_PARENT)
        inputArea.orientation = Orientation.HORIZONTAL
        run {
            val label = LabelWidget("OS >")
            label.layoutParams = WidgetContainer.LayoutParams().gravity(Gravity.BOTTOM_LEFT)
            inputArea.addChild("label", label)

            val textBox = TextBoxWidget(8)
            textBox.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.BOTTOM_LEFT)
                .sizeMode(SizeMode.MATCH_PARENT)
            textBox.background = null
            textBox.setWhenEnter { input ->
                outputs.removeChild("input_area")
                when (input) {
                    "learn" -> {
                        AbilitySystemClient.resetDevState()
                        MisakaNetworkClient.FUTURE_MANAGER.send(StartLevelDevPacket(mainPos.asLong())) { response ->
                            if (response != null && response.isSuccess) {
                                val progressLabel = LabelWidget(
                                    L10n["academy.ability_developer.progress"] + " 0%"
                                )
                                progressLabel.layoutParams = WidgetContainer.LayoutParams().gravity(Gravity.BOTTOM_LEFT)
                                outputs.addChild("dev_progress", progressLabel)
                                consoleScrollPanel.scrollToEnd()

                                fun poll() {
                                    when (AbilitySystemClient.getDevState()) {
                                        DevState.DEVELOPING -> {
                                            progressLabel.text =
                                                L10n["academy.ability_developer.progress"] + " " + (AbilitySystemClient.getDevProgress() * 100).toInt() + "%"
                                            consoleScrollPanel.pollNextFrame { poll() }
                                        }

                                        DevState.DONE -> {
                                            progressLabel.text = L10n["academy.ability_developer.dev_successful"]
                                            rebuildAfterCategoryLearned()
                                        }

                                        DevState.FAILED -> {
                                            progressLabel.text = L10n["academy.ability_developer.dev_failed"]
                                            val newInputArea = createCommandInputArea(outputs)
                                            outputs.addChild("input_area", newInputArea)
                                        }

                                        else -> {
                                            consoleScrollPanel.pollNextFrame { poll() }
                                        }
                                    }
                                }
                                poll()
                            } else {
                                addOutputLine(outputs, response?.message ?: "Unknown error")
                                val newInputArea = createCommandInputArea(outputs)
                                outputs.addChild("input_area", newInputArea)
                            }
                        }
                    }

                    "exit" -> {
                        onClose()
                    }

                    else -> {
                        addOutputLine(outputs, "Invalid command.")
                        val newInputArea = createCommandInputArea(outputs)
                        outputs.addChild("input_area", newInputArea)
                    }
                }
            }
            inputArea.addChild("text_box", textBox)
            inputArea.focusedChild = textBox
        }
        return inputArea
    }

    private fun addOutputLine(outputs: LinearLayoutWidget, text: String) {
        val label = LabelWidget(text)
        label.layoutParams = WidgetContainer.LayoutParams().gravity(Gravity.BOTTOM_LEFT)
        outputs.addChild("output_${RandomStringUtils.insecure().nextAlphabetic(8)}", label)
        consoleScrollPanel.scrollToEnd()
    }

    private fun rebuildAfterCategoryLearned() {
        var attempts = 0
        fun poll() {
            if (AbilitySystemClient.getCategory() !is Level0 || attempts++ >= 1200) {
                init()
            } else {
                consoleScrollPanel.pollNextFrame { poll() }
            }
        }
        poll()
    }

    private fun fillSkillTreeArea(area: FrameLayoutWidget) {
        val category = AbilitySystemClient.getCategory()
        val skillInfos = AbilitySystemClient.getSkillInfos()[category] ?: emptyList()

        val bg = ParallaxImageWidget(R.textures.UI_DEVELOPER_SKILL_AREA_BG)
        bg.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        bg.setSampler(FilterMode.LINEAR, true)
        bg.setImageToViewRatio(0.9f, 0.9f)
        area.addChild("area_bg", bg)

        val lineMap = mutableMapOf<String, Widget>()
        for (info in skillInfos) {
            for (dep in info.dependencies) {
                val line = createSkillLine(dep, info)
                val key = "line_${info.skill.getKeyString()}_${dep.skill.getKeyString()}"
                area.addChild(key, line)
                lineMap[key] = line
            }
        }

        val nodeMap = mutableMapOf<String, Widget>()
        val nodeList = mutableListOf<Pair<AbilitySystemClient.SkillInfo, Widget>>()
        for (idx in skillInfos.indices) {
            val info = skillInfos[idx]
            val node = createSkillNode(info)
            val key = "node_${info.skill.getKeyString()}"
            area.addChild(key, node)
            nodeMap[key] = node
            nodeList.add(info to node)
        }

        val nodeStagger = 50L
        val nodeFadeDuration = 400L
        for (idx in skillInfos.indices) {
            val info = skillInfos[idx]
            val key = "node_${info.skill.getKeyString()}"
            val node = nodeMap[key] ?: continue
            val targetAlpha = node.alpha
            node.alpha = 0f
            val fadeIn = ObjectAnimator.ofFloat({ node.alpha = it }, 0f, targetAlpha)
                .setDuration(nodeFadeDuration)
                .setStartDelay(nodeStagger * idx + 200L)
                .setInterpolator(EasingFunctions.EASE_OUT_SINE)
            node.startAnimation(fadeIn)
        }

        val lineStagger = 60L
        val lineFadeDuration = 300L
        var lineIdx = 0
        for ((_, line) in lineMap) {
            val targetAlpha = line.alpha
            val targetWidth = line.layoutParams.width
            line.alpha = 0f
            line.width = 0f
            val anim = ObjectAnimator.ofFloat({ p ->
                line.alpha = p * targetAlpha
                line.width = p * targetWidth
            }, 0f, 1f)
                .setDuration(lineFadeDuration)
                .setStartDelay(lineStagger * lineIdx + 200L)
                .setInterpolator(EasingFunctions.EASE_OUT_SINE)
            line.startAnimation(anim)
            lineIdx++
        }
    }

    private val lineTex = AcademyCraft.academy("textures/gui/developer/line.png")
    private val outlineTex = AcademyCraft.academy("textures/gui/developer/skill_outline.png")
    private val radialMaskTex = AcademyCraft.academy("textures/gui/developer/skill_radial_mask.png")

    private class CircleImageWidget(texture: Identifier) : ImageWidget(texture) {
        override fun generateDrawCommand(
            texture: GpuTextureView, sampler: GpuSampler,
            width: Float, height: Float,
            u0: Float, v0: Float, u1: Float, v1: Float, u2: Float, v2: Float, u3: Float, v3: Float,
            red: Float, green: Float, blue: Float, alpha: Float
        ): DrawCommand {
            return ImageCircleDrawCommand(
                texture, sampler, width, height, u0, v0, u1, v1, u2, v2, u3, v3, red, green, blue, alpha
            )
        }
    }

    private fun createSkillLine(child: AbilitySystemClient.SkillInfo, dep: AbilitySystemClient.SkillInfo): Widget {
        val childCx = child.x + 8f
        val childCy = child.y + 8f
        val depCx = dep.x + 8f
        val depCy = dep.y + 8f

        val dx = depCx - childCx
        val dy = depCy - childCy
        val dist = sqrt(dx * dx + dy * dy)
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

        val ux = dx / dist * 12.2f
        val uy = dy / dist * 12.2f
        val shortDist = (dist - 24.4f).coerceAtLeast(0f)
        if (shortDist <= 0f) return EmptyWidget()

        val isChildLearned = AbilitySystemClient.isSkillLearned(child.skill)
        val isDepLearned = AbilitySystemClient.isSkillLearned(dep.skill)
        val mAlpha = when {
            isChildLearned -> 1.0f
            isDepLearned -> 0.7f
            else -> 0.25f
        }
        val alpha = mAlpha * (if (isChildLearned) 1.0f else 0.4f)

        val line = object : ImageWidget(lineTex) {
            override fun render(context: RenderContext) {
                val mc = Minecraft.getInstance()
                val mh = mc.mouseHandler
                val w = mc.window
                val width = w.width
                val height = w.height
                val mouseX = mh.getScaledXPos(w)
                val mouseY = mh.getScaledYPos(w)
                val skillTreeMouseX = (mouseX / width).toFloat().coerceIn(0f, 1f)
                val skillTreeMouseY = (mouseY / height).toFloat().coerceIn(0f, 1f)
                val dx = skillTreeMouseX - 0.5f
                val dy = skillTreeMouseY - 0.5f
                translationX = -(dx * maxDuSkills)
                translationY = -(dy * maxDuSkills)
                super.render(context)
            }

            override fun onMouseMoved(event: MouseEvent) {
                super.onMouseMoved(event)
                invalidate()
            }
        }
        line.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.TOP_LEFT)
            .size(shortDist, 5.5f)
            .margin(childCx + ux, childCy + uy - 2.25f, 0f, 0f)
        line.originX = 0f
        line.originY = 0.5f
        line.rotation = angle
        line.alpha = alpha
        return line
    }

    private fun createSkillNode(info: AbilitySystemClient.SkillInfo): ButtonWidget {
        val isLearned = AbilitySystemClient.isSkillLearned(info.skill)
        val hasDepsLearned = info.dependencies.isEmpty() || info.dependencies.all {
            AbilitySystemClient.isSkillLearned(it.skill)
        }
        val mAlpha = when {
            isLearned -> 1.0f
            hasDepsLearned -> 0.7f
            else -> 0.25f
        }

        val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
        var outlineTexView: GpuTextureView? = null
        var maskTexView: GpuTextureView? = null

        val node = object : ButtonWidget() {
            override fun render(context: RenderContext) {
                val mc = Minecraft.getInstance()
                val mh = mc.mouseHandler
                val w = mc.window
                val width = w.width
                val height = w.height
                val mouseX = mh.getScaledXPos(w)
                val mouseY = mh.getScaledYPos(w)
                val skillTreeMouseX = (mouseX / width).toFloat().coerceIn(0f, 1f)
                val skillTreeMouseY = (mouseY / height).toFloat().coerceIn(0f, 1f)
                val dx = skillTreeMouseX - 0.5f
                val dy = skillTreeMouseY - 0.5f
                translationX = -(dx * maxDuSkills)
                translationY = -(dy * maxDuSkills)
                super.render(context)
            }

            override fun onMouseMoved(event: MouseEvent) {
                super.onMouseMoved(event)
                invalidate()
            }
        }
        node.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.TOP_LEFT)
            .margin(info.x, info.y, 0f, 0f)
            .size(16f, 16f)
        node.alpha = mAlpha
        run {
            val iconBg = ImageWidget(R.textures.UI_DEVELOPER_SKILL_ICON_BG)
            iconBg.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.CENTER)
                .size(23f, 23f)
            node.addChild("icon_bg", iconBg)

            val outlineBg = ImageWidget(outlineTex)
            outlineBg.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.CENTER)
                .size(31f, 31f)
            outlineBg.setBrightness(0.2f)
            outlineBg.alpha = mAlpha * 0.6f
            node.addChild("outline_bg", outlineBg)

            val icon = CircleImageWidget(info.texture)
            icon.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.CENTER)
                .size(14f, 14f)
            node.addChild("icon", icon)

            val outline = object : AbstractWidget() {
                override fun renderInternal(context: RenderContext) {
                    if (!isLearned) return
                    val skillExp = AbilitySystemClient.getSkillExp(info.skill)
                    if (skillExp <= 0f) return

                    val texManager = Minecraft.getInstance().textureManager
                    if (outlineTexView?.isClosed != false) {
                        outlineTexView = texManager.getTexture(outlineTex).getTextureView()
                    }
                    if (maskTexView?.isClosed != false) {
                        maskTexView = texManager.getTexture(radialMaskTex).getTextureView()
                    }

                    val o = outlineTexView
                    val m = maskTexView
                    if (o == null || m == null) return

                    val lp = layoutParams
                    val paddedWidth = width - lp.paddingLeft - lp.paddingRight
                    val paddedHeight = height - lp.paddingTop - lp.paddingBottom

                    if (paddedWidth <= 0 || paddedHeight <= 0) return

                    val finalAlpha = alpha * context.accumulatedAlpha

                    context.pose().pushPose()
                    run {
                        context.pose().translate(lp.paddingLeft, lp.paddingTop)
                        val command = SkillProgressDrawCommand(
                            o, m, sampler,
                            width, height, skillExp * finalAlpha, finalAlpha
                        )
                        context.submit(command)
                    }
                    context.pose().popPose()
                }
            }
            outline.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.CENTER)
                .size(31f, 31f)
            node.addChild("outline", outline)
        }

        val progressRef = AtomicReference(0f)
        val updater = { p: Float ->
            progressRef.set(p)
            val s = 1.0f + 0.2f * p
            node.scaleX = s
            node.scaleY = s
        }
        val animator = StateListAnimator()
        animator.addState(
            Widget.HOVERED,
            ObjectAnimator.ofFloat({ progressRef.get() }, updater, 1.0f)
                .setDuration(100).setInterpolator(EasingFunctions.EASE_OUT_SINE)
        )
        animator.addState(
            Widget.NONE,
            ObjectAnimator.ofFloat({ progressRef.get() }, updater, 0.0f)
                .setDuration(100).setInterpolator(EasingFunctions.EASE_OUT_SINE)
        )
        node.stateListAnimator = animator
        node.onClickListener = { addCover(createSkillViewCover(info)) }

        return node
    }

    private fun addCover(cover: FrameLayoutWidget) {
        activeCover?.let { root.removeChild("cover") }
        activeCover = cover
        root.addChild("cover", cover)
        mainWidget.startAnimation(
            ObjectAnimator.ofFloat(
                { mainWidget.translationY = it },
                mainWidget.translationY, -PANEL_MAIN_HEIGHT * 2
            ).setDuration(500).setInterpolator(EasingFunctions.EASE_OUT_EXPO)
        )
    }

    private fun removeCover(rebuild: Boolean = false) {
        val cover = activeCover ?: return
        if (AbilitySystemClient.getDevState() == DevState.DEVELOPING) {
            MisakaNetworkClient.send(StopDevPacket(mainPos))
            AbilitySystemClient.resetDevState()
        }
        mainWidget.startAnimation(
            ObjectAnimator.ofFloat(
                { mainWidget.translationY = it },
                mainWidget.translationY, 0f
            ).setDuration(500).setInterpolator(EasingFunctions.EASE_OUT_EXPO)
        )
        cover.startAnimation(
            ObjectAnimator.ofFloat({ cover.alpha = it }, cover.alpha, 0f)
                .setDuration(150)
                .addListener(object : AnimatorListener {
                    override fun onAnimationEnd(animation: Animator) {
                        root.removeChild("cover")
                        activeCover = null
                        if (rebuild) rebuildSkillTree()
                    }
                })
        )
    }

    private fun createCover(onClick: () -> Unit = { removeCover() }): FrameLayoutWidget {
        val cover = FrameLayoutWidget()
        cover.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        run {
            val bg = ButtonWidget()
            bg.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            bg.onClickListener = OnClickListener { onClick() }
            cover.addChild("bg", bg)
        }
        cover.startAnimation(
            ObjectAnimator.ofFloat({ cover.alpha = it }, 0f, 1f).setDuration(500)
                .setInterpolator(EasingFunctions.EASE_OUT_SINE)
        )
        return cover
    }

    private fun rebuildSkillTree() {
        area.clearChildren()
        val category = AbilitySystemClient.getCategory()
        val skillInfos = AbilitySystemClient.getSkillInfos()[category] ?: emptyList()
        for (info in skillInfos) {
            for (dep in info.dependencies) {
                val line = createSkillLine(info, dep)
                val key = "line_${info.skill.getKeyString()}_${dep.skill.getKeyString()}"
                area.addChild(key, line)
            }
        }
        for (idx in skillInfos.indices) {
            val info = skillInfos[idx]
            val node = createSkillNode(info)
            val key = "node_${info.skill.getKeyString()}"
            area.addChild(key, node)
        }
    }

    private fun createDevButton(brightnessRef: AtomicReference<Float> = AtomicReference(0.6f)): ButtonWidget {
        val btnTex = ImageWidget(AcademyCraft.academy("textures/gui/developer/button.png"))
        val btnWid = object : ButtonWidget() {
            override fun render(context: RenderContext) {
                val target = if (isHovered) 1.0f else 0.6f
                if (btnTex.brightness != target) {
                    brightnessRef.set(target)
                    btnTex.setBrightness(target)
                }
                super.render(context)
            }
        }
        btnWid.layoutParams = WidgetContainer.LayoutParams().size(32f, 16f)
        run {
            btnTex.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            btnTex.setBrightness(0.6f)
            btnWid.addChild("tex", btnTex)
        }
        return btnWid
    }

    private fun createSkillViewCover(info: AbilitySystemClient.SkillInfo): FrameLayoutWidget {
        val isLearned = AbilitySystemClient.isSkillLearned(info.skill)
        val skill = info.skill

        var canClose = true
        var shouldRebuild = false

        val cover = FrameLayoutWidget()
        cover.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        run {
            val bg = ButtonWidget()
            bg.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            bg.onClickListener = OnClickListener {
                if (canClose) {
                    if (shouldRebuild) removeCover(true)
                    else removeCover()
                }
            }
            cover.addChild("bg", bg)
        }
        cover.startAnimation(
            ObjectAnimator.ofFloat({ cover.alpha = it }, 0f, 1f).setDuration(500)
                .setInterpolator(EasingFunctions.EASE_OUT_SINE)
        )

        val iconProgressRef = AtomicReference(0f)

        val coverCenter = LinearLayoutWidget()
        coverCenter.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.CENTER)
        coverCenter.orientation = Orientation.VERTICAL
        cover.addChild("cover_center", coverCenter) {
            val iconWid = object : FrameLayoutWidget() {
                override fun render(context: RenderContext) {
                    val finalAlpha = alpha * context.accumulatedAlpha
                    val progress = iconProgressRef.get()
                    val texManager = Minecraft.getInstance().textureManager
                    val outlineTex = if (progress >= 1.0f) viewOutlineGlowTex else viewOutlineTex
                    val backView =
                        texManager.getTexture(R.textures.UI_DEVELOPER_SKILL_ICON_BG).getTextureView()
                    val outlineView = texManager.getTexture(outlineTex).getTextureView()
                    val maskView = texManager.getTexture(radialMaskTex).getTextureView()
                    val iconView = texManager.getTexture(info.texture).getTextureView()
                    val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)

                    context.pose().pushPose()
                    context.submit(
                        ImageDrawCommand(
                            backView,
                            sampler,
                            50f,
                            50f,
                            0f,
                            0f,
                            1f,
                            1f,
                            1f,
                            1f,
                            1f,
                            finalAlpha
                        )
                    )
                    context.pose().translate(11.5f, 11.5f)
                    context.submit(
                        ImageDrawCommand(
                            iconView,
                            sampler,
                            27f,
                            27f,
                            0f,
                            0f,
                            1f,
                            1f,
                            1f,
                            1f,
                            1f,
                            finalAlpha
                        )
                    )
                    context.pose().translate(-11.5f, -11.5f)
                    context.submit(
                        SkillProgressDrawCommand(
                            outlineView,
                            maskView,
                            sampler,
                            50f,
                            50f,
                            progress,
                            finalAlpha
                        )
                    )
                    context.pose().popPose()
                }
            }

            iconWid.layoutParams = WidgetContainer.LayoutParams().gravity(Gravity.CENTER).size(50f, 50f)
            coverCenter.addChild("skill_wid", iconWid)

            val textArea = LinearLayoutWidget()
            textArea.layoutParams = WidgetContainer.LayoutParams().gravity(Gravity.CENTER)
            textArea.orientation = Orientation.VERTICAL
            coverCenter.addChild("text_area", textArea) {
                if (isLearned) {
                    val nameLabel = LabelWidget(skill.translatedName)
                    nameLabel.baseFontSize = 12f
                    nameLabel.layoutParams = WidgetContainer.LayoutParams()
                        .gravity(Gravity.CENTER)
                    textArea.addChild("name", nameLabel)

                    val expLabel =
                        LabelWidget(
                            L10n["academy.ability_developer.skill_exp"] + (AbilitySystemClient.getSkillExp(
                                skill
                            ) * 100).toInt() + "%"
                        )
                    expLabel.baseFontSize = 8f
                    expLabel.setRed(0.63f)
                    expLabel.setGreen(0.88f)
                    expLabel.setBlue(1.0f)
                    expLabel.layoutParams = WidgetContainer.LayoutParams()
                        .gravity(Gravity.CENTER)
                    textArea.addChild("exp", expLabel)

                    val descLabel = LabelWidget(skill.translatedDescription)
                    descLabel.baseFontSize = 9f
                    descLabel.layoutParams = WidgetContainer.LayoutParams()
                        .gravity(Gravity.CENTER)
                        .width(200f)
                    textArea.addChild("desc", descLabel)
                } else {
                    val lvlLabel = LabelWidget("${skill.translatedName} (LV ${skill.recommendedLevel.levelCode})")
                    lvlLabel.baseFontSize = 12f
                    lvlLabel.layoutParams = WidgetContainer.LayoutParams()
                        .gravity(Gravity.CENTER)
                    textArea.addChild("lvl_name", lvlLabel)

                    val notLearnedLabel = LabelWidget(L10n["academy.ability_developer.skill_not_learned"])
                    notLearnedLabel.baseFontSize = 10f
                    notLearnedLabel.setRed(1.0f)
                    notLearnedLabel.setGreen(0.33f)
                    notLearnedLabel.setBlue(0.33f)
                    notLearnedLabel.layoutParams = WidgetContainer.LayoutParams()
                        .gravity(Gravity.CENTER)
                    textArea.addChild("not_learned", notLearnedLabel)

                    val conditions = skill.devConditions.filter { it.shouldDisplay() }

                    val req = object : LinearLayoutWidget() {
                        var hintText: String = ""
                        var hintRed: Float = 0.93f
                        var hintGreen: Float = 0.35f
                        var hintBlue: Float = 0.35f

                        override fun render(context: RenderContext) {
                            hintText = ""
                            super.render(context)
                            if (hintText.isEmpty()) return
                            val finalAlpha = alpha * context.accumulatedAlpha
                            val textHeight = LabelWidget.getTextHeight(hintText, 9f)
                            val y = (height - textHeight) / 2f
                            context.pose().pushPose()
                            context.pose().translate(width, y)
                            val commands = GlyphCommandGenerator.generate(
                                hintText, 9f, 0f, hintRed, hintGreen, hintBlue, finalAlpha
                            )
                            for (cmd in commands) context.submit(cmd)
                            context.pose().popPose()
                        }
                    }
                    req.layoutParams = WidgetContainer.LayoutParams()
                        .gravity(Gravity.CENTER)
                    req.orientation = Orientation.HORIZONTAL
                    textArea.addChild("req", req) {
                        val reqLabel = LabelWidget(L10n["academy.ability_developer.req"])
                        reqLabel.layoutParams = WidgetContainer.LayoutParams()
                            .gravity(Gravity.CENTER_BOTTOM)
                        reqLabel.baseFontSize = 9f
                        reqLabel.alpha = 0.66f
                        req.addChild("label", reqLabel)

                        for ((idx, cond) in conditions.withIndex()) {
                            val accepted = cond.accepts()
                            val condWid = object : FrameLayoutWidget() {
                                var condAccepted = accepted
                                override fun render(context: RenderContext) {
                                    if (isHovered) {
                                        req.hintText = "(${cond.getHintText()})"
                                        if (condAccepted) {
                                            req.hintRed = 0.93f; req.hintGreen = 1.0f; req.hintBlue = 1.0f
                                        } else {
                                            req.hintRed = 0.93f; req.hintGreen = 0.35f; req.hintBlue = 0.35f
                                        }
                                        req.invalidate()
                                    }
                                    super.render(context)
                                }
                            }
                            condWid.layoutParams = WidgetContainer.LayoutParams()
                                .gravity(Gravity.CENTER)
                                .size(14f, 14f)
                            val condIcon = if (!accepted) MonochromeImageWidget(
                                cond.getIcon() ?: R.textures.ICON_CLOSE
                            ) else ImageWidget(cond.getIcon() ?: R.textures.ICON_CLOSE)
                            condIcon.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
                            condWid.addChild("icon", condIcon)
                            req.addChild("cond_$idx", condWid)
                        }
                    }

                    val messageLabel = LabelWidget(
                        L10n["academy.ability_developer.learn_question"].format(
                            LearningHelper.getEstimatedSkillConsumption(
                                skill
                            )
                        )
                    )
                    messageLabel.baseFontSize = 10f
                    messageLabel.alpha = 0.66f
                    messageLabel.layoutParams = WidgetContainer.LayoutParams()
                        .gravity(Gravity.CENTER_HORIZONTAL)
                    textArea.addChild("message", messageLabel)

                    val learnBtn = createDevButton()
                    learnBtn.layoutParams.gravity(Gravity.CENTER_HORIZONTAL)
                    learnBtn.onClickListener = OnClickListener {
                        if (blockEntity.energyStored < LearningHelper.getEstimatedSkillConsumption(skill)) {
                            messageLabel.text = L10n["academy.ability_developer.noenergy"]
                        } else if (skill.recommendedLevel.levelCode > AbilitySystemClient.getLevel().levelCode) {
                            messageLabel.text =
                                L10n["academy.ability_developer.level_fail"].format(skill.recommendedLevel.levelCode)
                        } else if (info.dependencies.any { !AbilitySystemClient.isSkillLearned(it.skill) }) {
                            messageLabel.text = L10n["academy.ability_developer.condition_fail"]
                        } else if (skill.devConditions.any { !it.accepts() }) {
                            messageLabel.text = L10n["academy.ability_developer.condition_fail"]
                        } else {
                            AbilitySystemClient.resetDevState()
                            canClose = false

                            MisakaNetworkClient.FUTURE_MANAGER.send(
                                StartSkillDevPacket(skill.getKeyString(), mainPos.asLong())
                            ) { response ->
                                if (response != null && response.isSuccess) {
                                    fun poll() {
                                        val state = AbilitySystemClient.getDevState()
                                        when (state) {
                                            DevState.DEVELOPING -> {
                                                messageLabel.text =
                                                    L10n["academy.ability_developer.progress"] + " " + (AbilitySystemClient.getDevProgress() * 100).toInt() + "%"
                                                iconProgressRef.set(AbilitySystemClient.getDevProgress())
                                                cover.pollNextFrame { poll() }
                                            }

                                            DevState.DONE -> {
                                                iconProgressRef.set(1.0f)
                                                messageLabel.text = L10n["academy.ability_developer.dev_successful"]
                                                shouldRebuild = true
                                                canClose = true
                                            }

                                            DevState.FAILED -> {
                                                messageLabel.text = L10n["academy.ability_developer.dev_failed"]
                                                canClose = true
                                            }

                                            else -> {
                                                cover.pollNextFrame { poll() }
                                            }
                                        }
                                    }
                                    poll()
                                } else {
                                    messageLabel.text =
                                        response?.message ?: L10n["academy.ability_developer.dev_failed"]
                                }
                            }
                        }
                        textArea.removeChild("learn_btn")
                    }
                    textArea.addChild("learn_btn", learnBtn)
                }
            }
        }
        return cover
    }

    private fun AbstractWidget.pollNextFrame(action: () -> Unit) {
        val anim = ObjectAnimator.ofFloat({ }, 0f, 0f).setDuration(1)
        anim.addListener(object : AnimatorListener {
            override fun onAnimationEnd(animation: Animator) {
                action()
            }
        })
        startAnimation(anim)
    }

    private fun createLevelUpCover(): FrameLayoutWidget {
        val level = AbilitySystemClient.getLevel()
        val cost = LearningHelper.getEstimatedLevelUpConsumption(level.levelCode)

        var shouldRebuild = false

        val cover = FrameLayoutWidget()
        cover.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        run {
            val bg = ButtonWidget()
            bg.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            bg.onClickListener = OnClickListener {
                if (shouldRebuild) onClose()
                else removeCover()
            }
            cover.addChild("bg", bg)
        }
        cover.startAnimation(
            ObjectAnimator.ofFloat({ cover.alpha = it }, 0f, 1f).setDuration(500)
                .setInterpolator(EasingFunctions.EASE_OUT_SINE)
        )

        val iconProgressRef = AtomicReference(0f)
        val levelIconPath =
            AcademyCraft.academy("textures/abilities/condition/any${(level.levelCode + 1).coerceIn(1, 5)}.png")

        val coverCenter = LinearLayoutWidget()
        coverCenter.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.CENTER)
        coverCenter.orientation = Orientation.VERTICAL
        cover.addChild("cover_center", coverCenter) {
            val iconWid = object : FrameLayoutWidget() {
                override fun renderInternal(context: RenderContext) {
                    super.renderInternal(context)
                    val finalAlpha = alpha * context.accumulatedAlpha
                    val progress = iconProgressRef.get()
                    try {
                        val texManager = Minecraft.getInstance().textureManager
                        val outlineTex = if (progress >= 1.0f) viewOutlineGlowTex else viewOutlineTex
                        val backView = texManager.getTexture(R.textures.UI_DEVELOPER_SKILL_ICON_BG).getTextureView()
                        val outlineView = texManager.getTexture(outlineTex).getTextureView()
                        val maskView = texManager.getTexture(radialMaskTex).getTextureView()
                        val levelView = texManager.getTexture(levelIconPath).getTextureView()
                        val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
                        context.pose().pushPose()
                        context.submit(
                            ImageDrawCommand(
                                backView,
                                sampler,
                                50f,
                                50f,
                                0f,
                                0f,
                                1f,
                                1f,
                                1f,
                                1f,
                                1f,
                                finalAlpha
                            )
                        )
                        context.pose().translate(11.5f, 11.5f)
                        context.submit(
                            ImageDrawCommand(
                                levelView,
                                sampler,
                                27f,
                                27f,
                                0f,
                                0f,
                                1f,
                                1f,
                                1f,
                                1f,
                                1f,
                                finalAlpha
                            )
                        )
                        context.pose().translate(-11.5f, -11.5f)
                        context.submit(
                            SkillProgressDrawCommand(
                                outlineView,
                                maskView,
                                sampler,
                                50f,
                                50f,
                                progress,
                                finalAlpha
                            )
                        )
                        context.pose().popPose()
                    } catch (_: Exception) {
                    }
                }
            }
            iconWid.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.CENTER)
                .size(50f, 50f)
            coverCenter.addChild("skill_wid", iconWid)

            val textArea = LinearLayoutWidget()
            textArea.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.CENTER)
            textArea.orientation = Orientation.VERTICAL
            coverCenter.addChild("text_area", textArea) {
                val title = LabelWidget(L10n["academy.ability_developer.uplevel"].format(level.levelCode + 1))
                title.baseFontSize = 12f
                title.layoutParams = WidgetContainer.LayoutParams()
                    .gravity(Gravity.CENTER_HORIZONTAL)
                textArea.addChild("title", title)

                val reqLabel = LabelWidget(L10n["academy.ability_developer.req"] + " " + cost)
                reqLabel.baseFontSize = 9f
                reqLabel.layoutParams = WidgetContainer.LayoutParams()
                    .gravity(Gravity.CENTER_HORIZONTAL)
                textArea.addChild("req", reqLabel)

                val hintLabel = LabelWidget("")
                hintLabel.baseFontSize = 9f
                hintLabel.text = L10n["academy.ability_developer.level_question"]
                hintLabel.layoutParams =
                    WidgetContainer.LayoutParams().gravity(Gravity.CENTER_HORIZONTAL)
                textArea.addChild("hint", hintLabel)

                val upgBtn = createDevButton()
                upgBtn.layoutParams.gravity(Gravity.CENTER_HORIZONTAL)
                upgBtn.onClickListener = {
                    if (blockEntity.energyStored < cost) {
                        hintLabel.text = L10n["academy.ability_developer.noenergy"]
                    } else {
                        AbilitySystemClient.resetDevState()

                        MisakaNetworkClient.FUTURE_MANAGER.send(
                            StartLevelDevPacket(mainPos.asLong())
                        ) {
                            if (it != null && it.isSuccess) {
                                fun poll() {
                                    val state = AbilitySystemClient.getDevState()
                                    when (state) {
                                        DevState.DEVELOPING -> {
                                            hintLabel.text = L10n["academy.ability_developer.dev_developing"]
                                            iconProgressRef.set(AbilitySystemClient.getDevProgress())
                                            cover.pollNextFrame { poll() }
                                        }

                                        DevState.DONE -> {
                                            iconProgressRef.set(1.0f)
                                            hintLabel.text = L10n["academy.ability_developer.dev_successful"]
                                            shouldRebuild = true
                                        }

                                        DevState.FAILED -> {
                                            hintLabel.text = L10n["academy.ability_developer.dev_failed"]
                                        }

                                        else -> {
                                            cover.pollNextFrame { poll() }
                                        }
                                    }
                                }
                                poll()
                            } else {
                                hintLabel.text = it?.message ?: L10n["academy.ability_developer.dev_failed"]
                            }
                        }
                    }
                    textArea.removeChild("upgrade_btn")
                }
                textArea.addChild("upgrade_btn", upgBtn)
            }
        }

        return cover
    }

    private val viewOutlineTex = AcademyCraft.academy("textures/gui/developer/skill_view_outline.png")
    private val viewOutlineGlowTex = AcademyCraft.academy("textures/gui/developer/skill_view_outline_glow.png")

    companion object {
        const val PANEL_MAIN_WIDTH: Float = 400f
        const val PANEL_MAIN_HEIGHT: Float = 187f
    }
}
