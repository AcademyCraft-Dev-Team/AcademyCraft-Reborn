package org.academy.internal.client.gui.screen

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.input.KeyEvent
import net.minecraft.core.BlockPos
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.InteractionHand
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
import org.academy.internal.common.ability.AbilityCategories
import org.academy.internal.common.ability.ProficiencyPolicy
import org.academy.internal.common.ability.level0.Level0
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity
import org.academy.internal.common.world.item.AbilityControlTabletItem
import org.academy.internal.common.world.item.Items
import org.apache.commons.lang3.RandomStringUtils
import org.misaka.MisakaNetworkClient
import java.util.concurrent.atomic.AtomicReference
import net.minecraft.util.Mth

class AbilityDeveloperScreen(val developmentSource: DevelopmentSource) : UiScreen(Component.empty()) {
    private val blockEntity: AbilityDeveloperBlockEntity?
    private lateinit var area: FrameLayoutWidget
    private lateinit var mainWidget: FrameLayoutWidget
    private var isConsoleMode: Boolean = false
    private lateinit var consoleOutputs: LinearLayoutWidget
    private lateinit var consoleScrollPanel: ScrollPanelWidget
    private var activeCover: FrameLayoutWidget? = null
    private val skillLineBindings = mutableListOf<SkillLineBinding>()
    private var coursePage = CoursePage.ABILITY
    private var viewedSkillInfo: AbilitySystemClient.SkillInfo? = null

    private val maxDuSkills = 10f

    init {
        if (developmentSource.portable()) {
            val player = minecraft.player ?: throw RuntimeException("Player is null")
            if (!player.getItemInHand(developmentSource.hand()!!).`is`(Items.ABILITY_CONTROL_TABLET.get())) {
                throw RuntimeException("Ability control tablet is no longer held")
            }
            blockEntity = null
        } else {
            val level = minecraft.level ?: throw RuntimeException("Level is null")
            val entity = level.getBlockEntity(developmentSource.blockPos()!!)
            if (entity is AbilityDeveloperBlockEntity) {
                blockEntity = entity
                entity.setOpen(true)
            } else {
                throw RuntimeException("Invalid block entity at ${developmentSource.blockPos()}")
            }
        }
    }

    constructor(mainPos: BlockPos) : this(DevelopmentSource.block(mainPos))

    constructor(hand: InteractionHand) : this(DevelopmentSource.tablet(hand))

    private fun currentEnergy(): Int {
        val developer = blockEntity
        if (developer != null) return developer.energyStored
        val player = minecraft.player ?: return 0
        return AbilityControlTabletItem.getEnergyStored(player.getItemInHand(developmentSource.hand()!!))
    }

    private fun maxEnergy(): Int = blockEntity?.maxEnergyStorage ?: AbilityControlTabletItem.ENERGY_CAPACITY

    override fun isPauseScreen(): Boolean = false

    override fun onClose() {
        super.onClose()
        blockEntity?.setOpen(false)
        NeoForge.EVENT_BUS.unregister(this)
        MisakaNetworkClient.send(StopDevPacket(developmentSource))
        AbilitySystemClient.resetDevState()
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        super.mouseMoved(mouseX, mouseY)
    }

    override fun tick() {
        super.tick()
        refreshCompletedSkillDevelopment()
    }

    override fun keyPressed(e: KeyEvent): Boolean {
        if (e.key() == InputConstants.KEY_ESCAPE && viewedSkillInfo != null) {
            rebuildSkillTree()
            return true
        }
        return super.keyPressed(e)
    }

    override fun onInit() {
        activeCover = null
        isConsoleMode = false
        skillLineBindings.clear()
        viewedSkillInfo = null

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

                val uiLeft = ImageWidget(R.textures.gui.developer.ui_developerleft)
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

                val uiRight = ImageWidget(R.textures.gui.developer.ui_developerright)
                uiRight.layoutParams = WidgetContainer.LayoutParams()
                    .gravity(Gravity.RIGHT or Gravity.CENTER_VERTICAL)
                    .size(278f, 187f)
                parentRight.addChild("ui_right", uiRight)

                val category = AbilitySystemClient.getCategory()
                val a = FrameLayoutWidget()
                a.layoutParams = WidgetContainer.LayoutParams()
                    .gravity(Gravity.TOP_LEFT)
                    .margin(if (category is Level0) 10f else 14f, 18f, 0f, 0f)
                    .size(257f, 139f)
                area = a
                if (category !is Level0) {
                    val courseTabs = LinearLayoutWidget()
                    courseTabs.layoutParams = WidgetContainer.LayoutParams()
                        .gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
                        .margin(1f, 0f, 0f, 0f)
                        .size(COURSE_TAB_WIDTH, COURSE_TABS_HEIGHT)
                    courseTabs.orientation = Orientation.VERTICAL
                    courseTabs.spacing = COURSE_TAB_GAP
                    parentRight.addChild("course_tabs", courseTabs) {
                        fillCourseTabs(courseTabs)
                    }
                }
                parentRight.addChild("area", a) {
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
        val isLevel0 = category is Level0
        val displayedLevel = if (isLevel0) AbilityLevel.LEVEL0 else level
        val levelProgress = if (isLevel0) 0f else AbilitySystemClient.getAbilityProgress()

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

        val categoryKey = category.key
        val translationKey = "ability_category.${categoryKey.namespace}.${categoryKey.path}"
        val translatedName = Language.getInstance().getOrDefault(translationKey)
            .takeUnless { it == translationKey }
            ?: category.getDisplayName()
        val nameLabel = LabelWidget(translatedName)
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

        if (!isLevel0 && AbilitySystemClient.canLevelUp()) {
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
            val levelLabel = LabelWidget("Level ${displayedLevel.levelCode}")
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

        val developer = blockEntity
        if (developer != null) {
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
                    val wirelessPage = WirelessPanelUtil.create(developer.blockPos, true)
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

                val nodeIcon = ImageWidget(R.textures.gui.icon.icon_node)
                nodeIcon.layoutParams = WidgetContainer.LayoutParams()
                    .gravity(Gravity.TOP_LEFT)
                    .margin(7f, 2f, 0f, 0f)
                    .size(12f, 12f)
                nodeBtn.addChild("logo_node", nodeIcon)

                MisakaNetworkClient.FUTURE_MANAGER.send(GetCurrentNodePacket(developer.blockPos)) {
                    if (it != null) nodeName.text = it.nodeName
                }
            }
        } else {
            val sourceLabel = LabelWidget(L10n["academy.ability_developer.energy_source"])
            sourceLabel.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
                .margin(4.25f, 34f, 0f, 0f)
                .size(100f, 12f)
            panel.addChild("text_energy_source", sourceLabel)

            val tabletLabel = LabelWidget(L10n["academy.ability_developer.energy_source.tablet"])
            tabletLabel.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
                .margin(4.25f, 58f, 0f, 0f)
                .size(100f, 16f)
            panel.addChild("text_tablet", tabletLabel)
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
                val capacity = maxEnergy()
                setProgress(
                    if (capacity > 0)
                        currentEnergy().toFloat() / capacity * 100f
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
        addOutput(outputs, L10n["academy.ability_developer.console.welcome"]) {
            addOutput(outputs, L10n["academy.ability_developer.console.copyright"]) {
                val playerName = Minecraft.getInstance().player?.name?.string ?: "Unknown"
                addOutput(
                    outputs,
                    L10n["academy.ability_developer.console.user_detected"].format(playerName)
                ) {
                    val label = LabelWidget("")
                    label.layoutParams = WidgetContainer.LayoutParams()
                        .gravity(Gravity.BOTTOM_LEFT)
                    val progressSequence = (1..6).map { it * 10 + (-3..2).random() } + (64..67).random()

                    val bootAnim = ObjectAnimator.ofFloat(
                        { f ->
                            val index = f.toInt().coerceIn(0, progressSequence.size)
                            label.text = if (index < progressSequence.size) {
                                "${progressSequence[index]}%"
                            } else {
                                L10n["academy.ability_developer.console.boot_failed"]
                            }
                            label.invalidate()
                            consoleScrollPanel.scrollToEnd()
                        }, 0f, progressSequence.size.toFloat()
                    ).setDuration((progressSequence.size + 1) * 300L).setStartDelay(400L)

                    bootAnim.addListener(object : AnimatorListener {
                        override fun onAnimationEnd(animation: Animator) {
                            addOutput(
                                outputs,
                                L10n["academy.ability_developer.console.invalid_category"]
                            ) {
                                addOutput(outputs, L10n["academy.ability_developer.console.learn_hint"]) {
                                    attachCommandInput(outputs)
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

            fun setRevealProgress(value: Float) {
                progress = value.coerceIn(0f, 1f)
                // LabelWidget caches generated glyph commands. Force regeneration while
                // the typewriter reveal changes even though the backing text is unchanged.
                lastText = null
                invalidate()
            }

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
            ObjectAnimator.ofFloat(label::setRevealProgress, 0f, 1f)
                .setDuration(text.length * CONSOLE_CHAR_DELAY_MS)
                .addListener(object : AnimatorListener {
                    override fun onAnimationEnd(animation: Animator) {
                        label.setRevealProgress(1f)
                        scrollConsoleToEndAfterLayout()
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
            val label = LabelWidget(L10n["academy.ability_developer.console.prompt"])
            label.layoutParams = WidgetContainer.LayoutParams().gravity(Gravity.BOTTOM_LEFT)
            inputArea.addChild("label", label)

            val textBox = TextBoxWidget(8)
            textBox.layoutParams = LinearLayoutWidget.LayoutParams().apply {
                gravity(Gravity.BOTTOM_LEFT)
                width(0f)
                heightMode(SizeMode.MATCH_PARENT)
                weight(1f)
            }
            textBox.background = null
            textBox.setWhenEnter { input ->
                outputs.removeChild("input_area")
                addOutputLine(
                    outputs,
                    "${L10n["academy.ability_developer.console.prompt"]} $input"
                )
                when (input.trim().lowercase()) {
                    "learn" -> {
                        addOutputLine(outputs, L10n["academy.ability_developer.console.dev_begin"])
                        AbilitySystemClient.resetDevState()
                        MisakaNetworkClient.FUTURE_MANAGER.send(StartLevelDevPacket(developmentSource)) { response ->
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
                                            progressLabel.text = developmentFailureMessage()
                                            attachCommandInput(outputs)
                                        }

                                        else -> {
                                            consoleScrollPanel.pollNextFrame { poll() }
                                        }
                                    }
                                }
                                poll()
                            } else {
                                addOutputLine(outputs, response?.message ?: "Unknown error")
                                attachCommandInput(outputs)
                            }
                        }
                    }

                    "exit" -> {
                        onClose()
                    }

                    else -> {
                        addOutputLine(outputs, L10n["academy.ability_developer.console.invalid_command"])
                        attachCommandInput(outputs)
                    }
                }
            }
            inputArea.addChild("text_box", textBox)
        }
        return inputArea
    }

    private fun attachCommandInput(outputs: LinearLayoutWidget) {
        outputs.removeChild("input_area")
        val inputArea = createCommandInputArea(outputs)
        outputs.addChild("input_area", inputArea)
        inputArea.children["text_box"]?.let { inputArea.focusedChild = it }
        scrollConsoleToEndAfterLayout()
    }

    private fun addOutputLine(outputs: LinearLayoutWidget, text: String) {
        val label = LabelWidget(text)
        label.layoutParams = WidgetContainer.LayoutParams().gravity(Gravity.BOTTOM_LEFT)
        outputs.addChild("output_${RandomStringUtils.insecure().nextAlphabetic(8)}", label)
        scrollConsoleToEndAfterLayout()
    }

    private fun scrollConsoleToEndAfterLayout() {
        consoleOutputs.requestLayout()
        consoleScrollPanel.pollNextFrame { consoleScrollPanel.scrollToEnd() }
    }

    private fun rebuildAfterCategoryLearned() {
        var attempts = 0
        fun poll() {
            val categoryReady = AbilitySystemClient.getCategory() !is Level0
            val levelReady = AbilitySystemClient.getLevel().levelCode >= AbilityLevel.LEVEL1.levelCode
            if ((categoryReady && levelReady) || attempts++ >= 1200) {
                init()
            } else {
                consoleScrollPanel.pollNextFrame { poll() }
            }
        }
        poll()
    }

    private fun fillSkillTreeArea(area: FrameLayoutWidget) {
        val category = AbilitySystemClient.getCategory()
        val layoutCategory = if (coursePage == CoursePage.COMMON) {
            AbilityCategories.LEVEL0.get()
        } else {
            category
        }
        val skillInfos = when (coursePage) {
            CoursePage.COMMON -> AbilitySystemClient.getCommonSkillInfos()
            CoursePage.ABILITY -> AbilitySystemClient.getCategorySkillInfos(category)
        }
            .filter { info ->
                SkillTreeVisibility.shouldDisplay(
                    info.skill.scope,
                    AbilitySystemClient.isSkillLearned(info.skill),
                    info.dependencies.all { AbilitySystemClient.isSkillLearned(it.skill) },
                    info.skill.devConditions.all { it.accepts() }
                )
            }

        val bg = object : ParallaxImageWidget(R.textures.gui.developer.skill_panel_back) {
            override fun render(context: RenderContext) {
                setParallaxEnabled(!AbilityDeveloperLayoutEditor.isDebugMode())
                super.render(context)
            }
        }
        bg.setImageToViewRatio(0.9f, 0.9f)
        bg.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        bg.setSampler(FilterMode.LINEAR, true)
        area.addChild("area_bg", bg)

        skillLineBindings.clear()
        val lineMap = mutableMapOf<String, Widget>()
        for (info in skillInfos) {
            for (dep in info.dependencies) {
                val line = createSkillLine(layoutCategory, dep, info)
                val key = "line_${info.skill.getKeyString()}_${dep.skill.getKeyString()}"
                area.addChild(key, line)
                lineMap[key] = line
                skillLineBindings.add(SkillLineBinding(line, layoutCategory, dep, info))
            }
        }

        val nodeMap = mutableMapOf<String, Widget>()
        val nodeList = mutableListOf<Pair<AbilitySystemClient.SkillInfo, Widget>>()
        for (idx in skillInfos.indices) {
            val info = skillInfos[idx]
            val node = createSkillNode(layoutCategory, info)
            val key = "node_${info.skill.getKeyString()}"
            area.addChild(key, node)
            nodeMap[key] = node
            nodeList.add(info to node)
        }

        val debugLabel = object : LabelWidget("") {
            override fun render(context: RenderContext) {
                text = if (AbilityDeveloperLayoutEditor.isDebugMode()) {
                    "LAYOUT: ${layoutCategory.key} / ${coursePage.name.lowercase()}  (drag icons; snap 0.5px)"
                } else {
                    ""
                }
                super.render(context)
            }
        }
        debugLabel.baseFontSize = 6f
        debugLabel.isEnabled = false
        debugLabel.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.TOP_LEFT)
            .margin(2f, 1f, 0f, 0f)
            .size(250f, 8f)
        area.addChild("layout_debug_status", debugLabel)

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

    private fun createSkillLine(
        category: AbilityCategory,
        child: AbilitySystemClient.SkillInfo,
        dep: AbilitySystemClient.SkillInfo
    ): Widget {
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
                updateSkillLineGeometry(this, category, child, dep)
                if (AbilityDeveloperLayoutEditor.isDebugMode()) {
                    translationX = 0f
                    translationY = 0f
                } else {
                    val mc = Minecraft.getInstance()
                    val mh = mc.mouseHandler
                    val w = mc.window
                    val mouseX = mh.getScaledXPos(w)
                    val mouseY = mh.getScaledYPos(w)
                    val skillTreeMouseX = (mouseX / w.width).toFloat().coerceIn(0f, 1f)
                    val skillTreeMouseY = (mouseY / w.height).toFloat().coerceIn(0f, 1f)
                    translationX = -((skillTreeMouseX - 0.5f) * maxDuSkills)
                    translationY = -((skillTreeMouseY - 0.5f) * maxDuSkills)
                }
                super.render(context)
            }

            override fun onMouseMoved(event: MouseEvent) {
                super.onMouseMoved(event)
                invalidate()
            }
        }
        line.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.TOP_LEFT)
            .size(0f, 5.5f)
        line.originX = 0f
        line.originY = 0.5f
        line.alpha = alpha
        updateSkillLineGeometry(line, category, child, dep)
        return line
    }

    private fun updateSkillLineGeometry(
        line: Widget,
        category: AbilityCategory,
        child: AbilitySystemClient.SkillInfo,
        dep: AbilitySystemClient.SkillInfo
    ) {
        val childPos = AbilityDeveloperLayoutEditor.getPosition(category, child)
        val depPos = AbilityDeveloperLayoutEditor.getPosition(category, dep)
        val childCx = childPos.x() + 8f
        val childCy = childPos.y() + 8f
        val depCx = depPos.x() + 8f
        val depCy = depPos.y() + 8f
        val dx = depCx - childCx
        val dy = depCy - childCy
        val dist = Mth.sqrt(dx * dx + dy * dy)
        val shortDist = (dist - 24.4f).coerceAtLeast(0f)
        val ux = if (dist > 0f) dx / dist * 12.2f else 0f
        val uy = if (dist > 0f) dy / dist * 12.2f else 0f
        val lp = line.layoutParams
        lp.marginLeft = childCx + ux
        lp.marginTop = childCy + uy - 2.25f
        line.width = shortDist
        line.rotation = if (dist > 0f) (Mth.atan2(dy.toDouble(), dx.toDouble()) * Mth.RAD_TO_DEG).toFloat() else 0f
    }

    private fun updateSkillLines() {
        for (binding in skillLineBindings) {
            updateSkillLineGeometry(binding.widget, binding.category, binding.child, binding.dependency)
        }
    }

    private fun createSkillNode(category: AbilityCategory, info: AbilitySystemClient.SkillInfo): ButtonWidget {
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
            private var isLayoutDragging = false
            private var dragOffsetX = 0f
            private var dragOffsetY = 0f

            override fun render(context: RenderContext) {
                val position = AbilityDeveloperLayoutEditor.getPosition(category, info)
                if (layoutParams.marginLeft != position.x() || layoutParams.marginTop != position.y()) {
                    layoutParams.marginLeft = position.x()
                    layoutParams.marginTop = position.y()
                    updateSkillLines()
                    area.requestLayout()
                }
                tooltipText = if (AbilityDeveloperLayoutEditor.isDebugMode()) {
                    "${category.key}\n${info.skill.getKeyString()}  (${position.x()}, ${position.y()})"
                } else if (isLearned) {
                    val proficiency = AbilitySystemClient.getSkillProficiency(info.skill)
                    "${info.skill.translatedName}\n${L10n["academy.ability_developer.skill_exp"]}" +
                            String.format("%.2f/3000 (%.2f%%)", proficiency, proficiency / 30f)
                } else {
                    info.skill.translatedName
                }
                if (AbilityDeveloperLayoutEditor.isDebugMode()) {
                    translationX = 0f
                    translationY = 0f
                } else {
                    val mc = Minecraft.getInstance()
                    val mh = mc.mouseHandler
                    val w = mc.window
                    val mouseX = mh.getScaledXPos(w)
                    val mouseY = mh.getScaledYPos(w)
                    val skillTreeMouseX = (mouseX / w.width).toFloat().coerceIn(0f, 1f)
                    val skillTreeMouseY = (mouseY / w.height).toFloat().coerceIn(0f, 1f)
                    translationX = -((skillTreeMouseX - 0.5f) * maxDuSkills)
                    translationY = -((skillTreeMouseY - 0.5f) * maxDuSkills)
                }
                super.render(context)
            }

            override fun onMouseMoved(event: MouseEvent) {
                super.onMouseMoved(event)
                invalidate()
            }

            override fun onMousePressed(event: MouseEvent) {
                if (!AbilityDeveloperLayoutEditor.isDebugMode()) {
                    super.onMousePressed(event)
                    return
                }
                if (event.button == 0 && isMouseOver(event.x, event.y)) {
                    val position = AbilityDeveloperLayoutEditor.getPosition(category, info)
                    dragOffsetX = event.x.toFloat() - area.getAbsoluteX() - position.x()
                    dragOffsetY = event.y.toFloat() - area.getAbsoluteY() - position.y()
                    isLayoutDragging = true
                    event.consume()
                }
            }

            override fun onMouseDragged(event: MouseEvent) {
                if (!isLayoutDragging || event.button != 0) return
                val maxX = (area.width - 16f).coerceAtLeast(0f)
                val maxY = (area.height - 16f).coerceAtLeast(0f)
                val x = AbilityDeveloperLayoutEditor.snap(
                    event.x.toFloat() - area.getAbsoluteX() - dragOffsetX
                ).coerceIn(0f, maxX)
                val y = AbilityDeveloperLayoutEditor.snap(
                    event.y.toFloat() - area.getAbsoluteY() - dragOffsetY
                ).coerceIn(0f, maxY)
                AbilityDeveloperLayoutEditor.setPosition(category, info, x, y)
                layoutParams.marginLeft = x
                layoutParams.marginTop = y
                updateSkillLines()
                area.requestLayout()
                event.consume()
            }

            override fun onMouseReleased(event: MouseEvent) {
                if (AbilityDeveloperLayoutEditor.isDebugMode() && isLayoutDragging) {
                    isLayoutDragging = false
                    event.consume()
                    return
                }
                super.onMouseReleased(event)
            }
        }
        val initialPosition = AbilityDeveloperLayoutEditor.getPosition(category, info)
        node.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.TOP_LEFT)
            .margin(initialPosition.x(), initialPosition.y(), 0f, 0f)
            .size(16f, 16f)
        node.alpha = mAlpha
        run {
            val iconBg = ImageWidget(R.textures.gui.developer.skill_back)
            iconBg.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.CENTER)
                .size(23f, 23f)
            node.addChild("icon_bg", iconBg)

            if (isLearned) {
                val learnedHighlight = ImageWidget(R.textures.gui.developer.skill_back)
                learnedHighlight.layoutParams = WidgetContainer.LayoutParams()
                    .gravity(Gravity.CENTER)
                    .size(25f, 25f)
                learnedHighlight.setBrightness(1.25f)
                learnedHighlight.alpha = 0.32f
                node.addChild("learned_highlight", learnedHighlight)
            }

            val outlineBg = object : ImageWidget(outlineTex) {
                override fun tick() {
                    super.tick()
                    val full = isLearned && AbilitySystemClient.getSkillProficiencyProgress(info.skill) >= 1f
                    setBrightness(if (full) 1.4f else 0.2f)
                    alpha = if (full) 1f else mAlpha * 0.6f
                }
            }
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
                    val skillProgress = AbilitySystemClient.getSkillProficiencyProgress(info.skill)
                    if (skillProgress <= 0f) return

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
                            width, height, skillProgress, finalAlpha
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
        node.onClickListener = {
            viewedSkillInfo = info
            area.clearChildren()
            area.addChild("skill_view", createSkillViewCover(info))
        }

        return node
    }

    private data class SkillLineBinding(
        val widget: Widget,
        val category: AbilityCategory,
        val child: AbilitySystemClient.SkillInfo,
        val dependency: AbilitySystemClient.SkillInfo
    )

    private enum class CoursePage {
        COMMON,
        ABILITY
    }

    private fun addCover(cover: FrameLayoutWidget) {
        if (activeCover != null) return
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
            MisakaNetworkClient.send(StopDevPacket(developmentSource))
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
        viewedSkillInfo = null
        area.clearChildren()
        fillSkillTreeArea(area)
    }

    private fun refreshCompletedSkillDevelopment() {
        if (AbilitySystemClient.getDevState() != DevState.DONE) return
        val targetId = AbilitySystemClient.getDevTargetId()
        if (targetId.isBlank() || targetId == DevelopAction.LEVEL_TARGET_ID) return
        if (!AbilitySystemClient.isSkillLearned(targetId)) return

        AbilitySystemClient.resetDevState()
        if (::area.isInitialized && !isConsoleMode) {
            rebuildSkillTree()
        }
    }

    private fun fillCourseTabs(tabs: LinearLayoutWidget) {
        tabs.addChild(
            "common_course",
            createCourseTab(CoursePage.COMMON, L10n["academy.ability_developer.course.common"])
        )
        tabs.addChild(
            "ability_course",
            createCourseTab(CoursePage.ABILITY, L10n["academy.ability_developer.course.ability"])
        )
    }

    private fun createCourseTab(page: CoursePage, labelText: String): ButtonWidget {
        val background = FillWidget(COURSE_TAB_IDLE_COLOR)
        val edge = FillWidget(COURSE_TAB_EDGE_IDLE_COLOR)
        val topLine = FillWidget(COURSE_TAB_LINE_COLOR)
        val bottomLine = FillWidget(COURSE_TAB_LINE_COLOR)
        val label = LabelWidget(labelText)
        val button = object : ButtonWidget() {
            override fun render(context: RenderContext) {
                val selected = coursePage == page
                background.setColor(
                    when {
                        selected -> COURSE_TAB_SELECTED_COLOR
                        isHovered -> COURSE_TAB_HOVER_COLOR
                        else -> COURSE_TAB_IDLE_COLOR
                    }
                )
                edge.setColor(if (selected) COURSE_TAB_EDGE_SELECTED_COLOR else COURSE_TAB_EDGE_IDLE_COLOR)
                label.setRed(if (selected) 0.72f else 0.78f)
                label.setGreen(if (selected) 0.93f else 0.82f)
                label.setBlue(if (selected) 1.0f else 0.85f)
                super.render(context)
            }
        }
        button.layoutParams = WidgetContainer.LayoutParams().size(COURSE_TAB_WIDTH, COURSE_TAB_HEIGHT)
        button.tooltipText = labelText
        background.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        button.addChild("background", background)

        label.baseFontSize = 5.5f
        label.isEnabled = false
        label.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.CENTER)
            .margin(1.5f, 0f, 0.5f, 0f)
            .size(COURSE_TAB_WIDTH - 2f, 9f)
        edge.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
            .size(1.5f, COURSE_TAB_HEIGHT - 6f)
        topLine.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            .size(COURSE_TAB_WIDTH - 2f, 0.5f)
        bottomLine.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            .size(COURSE_TAB_WIDTH - 2f, 0.5f)
        button.addChild("edge", edge)
        button.addChild("top_line", topLine)
        button.addChild("bottom_line", bottomLine)
        button.addChild("label", label)
        button.onClickListener = OnClickListener {
            if (coursePage == page) return@OnClickListener
            coursePage = page
            viewedSkillInfo = null
            skillLineBindings.clear()
            area.clearChildren()
            fillSkillTreeArea(area)
        }
        return button
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

    private fun createSkillBackButton(): ButtonWidget {
        val background = FillWidget(COURSE_TAB_SELECTED_COLOR)
        val label = LabelWidget("<")
        val button = object : ButtonWidget() {
            override fun render(context: RenderContext) {
                background.setColor(if (isHovered) COURSE_TAB_HOVER_COLOR else COURSE_TAB_SELECTED_COLOR)
                super.render(context)
            }
        }
        button.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.TOP_RIGHT)
            .margin(0f, 3f, 3f, 0f)
            .size(22f, 14f)
        button.tooltipText = L10n["academy.ability_developer.back"]
        button.onClickListener = OnClickListener { rebuildSkillTree() }

        background.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        button.addChild("background", background)
        label.baseFontSize = 9f
        label.isEnabled = false
        label.layoutParams = WidgetContainer.LayoutParams().gravity(Gravity.CENTER).size(10f, 10f)
        button.addChild("label", label)
        return button
    }

    private fun createSkillViewCover(info: AbilitySystemClient.SkillInfo): FrameLayoutWidget {
        val isLearned = AbilitySystemClient.isSkillLearned(info.skill)
        val skill = info.skill
        val skillId = skill.getKeyString()

        val cover = FrameLayoutWidget()
        cover.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        run {
            val bg = ButtonWidget()
            bg.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            bg.onClickListener = OnClickListener { rebuildSkillTree() }
            cover.addChild("bg", bg)
        }
        cover.startAnimation(
            ObjectAnimator.ofFloat({ cover.alpha = it }, 0f, 1f).setDuration(500)
                .setInterpolator(EasingFunctions.EASE_OUT_SINE)
        )

        val proficiency = AbilitySystemClient.getSkillProficiency(skill)
        val iconProgressRef = AtomicReference(
            if (isLearned) AbilitySystemClient.getSkillProficiencyProgress(skill) else 0f
        )

        val coverCenter = LinearLayoutWidget()
        coverCenter.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.CENTER)
        coverCenter.orientation = Orientation.VERTICAL
        cover.addChild("cover_center", coverCenter) {
            val iconWid = object : FrameLayoutWidget() {
                override fun render(context: RenderContext) {
                    val finalAlpha = alpha * context.accumulatedAlpha
                    val tracksDevelopment = AbilitySystemClient.getDevTargetId() == skillId
                    val progress = if (tracksDevelopment) {
                        when (AbilitySystemClient.getDevState()) {
                            DevState.DONE -> 1.0f
                            DevState.DEVELOPING -> AbilitySystemClient.getDevProgress()
                            else -> iconProgressRef.get()
                        }
                    } else {
                        iconProgressRef.get()
                    }
                    val texManager = Minecraft.getInstance().textureManager
                    val outlineTex = if (progress >= 1.0f) viewOutlineGlowTex else viewOutlineTex
                    val backView =
                        texManager.getTexture(R.textures.gui.developer.skill_back).getTextureView()
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
                            L10n["academy.ability_developer.skill_exp"] +
                                    String.format("%.2f/3000 (%.2f%%)", proficiency, proficiency / 30f)
                        )
                    expLabel.baseFontSize = 8f
                    expLabel.setRed(0.63f)
                    expLabel.setGreen(0.88f)
                    expLabel.setBlue(1.0f)
                    expLabel.layoutParams = WidgetContainer.LayoutParams()
                        .gravity(Gravity.CENTER)
                    textArea.addChild("exp", expLabel)

                    val detailsPanel = ScrollPanelWidget()
                    detailsPanel.layoutParams = LinearLayoutWidget.LayoutParams()
                        .gravity(Gravity.CENTER)
                        .size(240f, 112f)
                    val details = LinearLayoutWidget()
                    details.orientation = Orientation.VERTICAL
                    details.spacing = 2f
                    details.layoutParams = WidgetContainer.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)

                    val descLabel = LabelWidget(skill.translatedDescription)
                    descLabel.baseFontSize = 8f
                    descLabel.layoutParams = WidgetContainer.LayoutParams()
                        .gravity(Gravity.CENTER)
                        .width(228f)
                    details.addChild("desc", descLabel)

                    if (SkillProficiencyProfiles.isDeclared(skill.keyString)) {
                        val milestone = AbilitySystemClient.getSkillProficiencyMilestone(skill)
                        listOf(1000, 2000, 3000).forEachIndexed { index, threshold ->
                            val reached = milestone > index
                            val next = milestone == index
                            val marker = if (reached) "✓" else if (next) "→" else "•"
                            val key = "${skill.descriptionId}.proficiency.$threshold"
                            val label = LabelWidget("$marker $threshold  ${Language.getInstance().getOrDefault(key)}")
                            label.baseFontSize = 7f
                            label.layoutParams = WidgetContainer.LayoutParams()
                                .gravity(Gravity.LEFT)
                                .width(228f)
                            when {
                                reached -> {
                                    label.setRed(0.35f); label.setGreen(0.95f); label.setBlue(1.0f)
                                }
                                next -> {
                                    label.setRed(1.0f); label.setGreen(0.78f); label.setBlue(0.25f)
                                }
                                else -> {
                                    label.setRed(0.55f); label.setGreen(0.58f); label.setBlue(0.63f)
                                }
                            }
                            details.addChild("proficiency_$threshold", label)
                        }
                        if (ProficiencyPolicy.clientHasRestriction(skill)) {
                            val restricted = LabelWidget(L10n["academy.ability_developer.proficiency_restricted"])
                            restricted.baseFontSize = 7f
                            restricted.setRed(1.0f); restricted.setGreen(0.38f); restricted.setBlue(0.3f)
                            restricted.layoutParams = WidgetContainer.LayoutParams()
                                .gravity(Gravity.LEFT)
                                .width(228f)
                            details.addChild("proficiency_restricted", restricted)
                        }
                    }
                    detailsPanel.setContent(details)
                    textArea.addChild("details", detailsPanel)
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
                                cond.getIcon() ?: R.textures.gui.icon.close
                            ) else ImageWidget(cond.getIcon() ?: R.textures.gui.icon.close)
                            condIcon.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
                            condWid.addChild("icon", condIcon)
                            req.addChild("cond_$idx", condWid)
                        }
                    }

                    val learnQuestion = L10n["academy.ability_developer.learn_question"].format(
                        LearningHelper.getEstimatedSkillConsumption(skill)
                    )
                    val messageLabel = object : LabelWidget(learnQuestion) {
                        override fun tick() {
                            super.tick()
                            val targetId = AbilitySystemClient.getDevTargetId()
                            if (targetId == skillId) {
                                text = when {
                                    AbilitySystemClient.isDevRequestPending() ->
                                        L10n["academy.ability_developer.dev_developing"]

                                    AbilitySystemClient.getDevState() == DevState.DEVELOPING ->
                                        L10n["academy.ability_developer.progress"] + " " +
                                            (AbilitySystemClient.getDevProgress() * 100).toInt() + "%"

                                    AbilitySystemClient.getDevState() == DevState.DONE ->
                                        L10n["academy.ability_developer.dev_successful"]

                                    AbilitySystemClient.getDevState() == DevState.FAILED ->
                                        developmentFailureMessage()

                                    else -> learnQuestion
                                }
                            } else if (AbilitySystemClient.isDevelopmentActive()) {
                                text = L10n["academy.ability_developer.already_developing"]
                            }
                        }
                    }
                    messageLabel.baseFontSize = 10f
                    messageLabel.alpha = 0.66f
                    messageLabel.layoutParams = WidgetContainer.LayoutParams()
                        .gravity(Gravity.CENTER_HORIZONTAL)
                    textArea.addChild("message", messageLabel)

                    if (!AbilitySystemClient.isDevelopmentActive()) {
                        val learnBtn = createDevButton()
                        learnBtn.layoutParams.gravity(Gravity.CENTER_HORIZONTAL)
                        learnBtn.onClickListener = OnClickListener {
                            if (AbilitySystemClient.getDevTargetId() == skillId
                                && AbilitySystemClient.getDevState() == DevState.FAILED
                            ) {
                                AbilitySystemClient.resetDevState()
                            }
                            if (currentEnergy() < LearningHelper.getEstimatedSkillConsumption(skill)) {
                                messageLabel.text = L10n["academy.ability_developer.noenergy"]
                            } else if (skill.recommendedLevel.levelCode > AbilitySystemClient.getLevel().levelCode) {
                                messageLabel.text =
                                    L10n["academy.ability_developer.level_fail"].format(skill.recommendedLevel.levelCode)
                            } else if (info.dependencies.any { !AbilitySystemClient.isSkillLearned(it.skill) }) {
                                messageLabel.text = L10n["academy.ability_developer.condition_fail"]
                            } else if (skill.devConditions.any { !it.accepts() }) {
                                messageLabel.text = L10n["academy.ability_developer.condition_fail"]
                            } else if (AbilitySystemClient.isDevelopmentActive()) {
                                messageLabel.text = L10n["academy.ability_developer.already_developing"]
                            } else {
                                learnBtn.isEnabled = false
                                AbilitySystemClient.beginDevelopmentRequest(skillId)
                                MisakaNetworkClient.FUTURE_MANAGER.send(
                                    StartSkillDevPacket(skillId, developmentSource)
                                ) { response ->
                                    if (response != null && response.isSuccess) {
                                        textArea.removeChild("learn_btn")
                                    } else {
                                        val failure = response?.message
                                            ?: L10n["academy.ability_developer.dev_failed"]
                                        AbilitySystemClient.rejectDevelopmentRequest(skillId, failure)
                                        learnBtn.isEnabled = true
                                    }
                                }
                            }
                        }
                        textArea.addChild("learn_btn", learnBtn)
                    }
                }
            }
        }
        cover.addChild("back", createSkillBackButton())
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

    private fun developmentFailureMessage(): String {
        val detail = AbilitySystemClient.getDevMessage()
        return if (detail.isBlank() || detail == "Failed") {
            L10n["academy.ability_developer.dev_failed"]
        } else {
            "${L10n["academy.ability_developer.dev_failed"]}: $detail"
        }
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
            AcademyCraft.academy("textures/ability/condition/any${(level.levelCode + 1).coerceIn(1, 5)}.png")

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
                        val backView = texManager.getTexture(R.textures.gui.developer.skill_back).getTextureView()
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
                    if (currentEnergy() < cost) {
                        hintLabel.text = L10n["academy.ability_developer.noenergy"]
                    } else {
                        AbilitySystemClient.resetDevState()

                        MisakaNetworkClient.FUTURE_MANAGER.send(
                            StartLevelDevPacket(developmentSource)
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
                                            hintLabel.text = developmentFailureMessage()
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
        private const val COURSE_TAB_WIDTH = 12f
        private const val COURSE_TAB_HEIGHT = 30f
        private const val COURSE_TAB_GAP = 3f
        private const val COURSE_TABS_HEIGHT = COURSE_TAB_HEIGHT * 2f + COURSE_TAB_GAP
        private val COURSE_TAB_IDLE_COLOR = 0xA0161D21.toInt()
        private val COURSE_TAB_HOVER_COLOR = 0xC024343C.toInt()
        private val COURSE_TAB_SELECTED_COLOR = 0xE02B4652.toInt()
        private val COURSE_TAB_EDGE_IDLE_COLOR = 0x80687579.toInt()
        private val COURSE_TAB_EDGE_SELECTED_COLOR = 0xFF8EDCF3.toInt()
        private val COURSE_TAB_LINE_COLOR = 0x7093ABB3.toInt()
        private const val CONSOLE_CHAR_DELAY_MS: Long = 10L
    }
}
