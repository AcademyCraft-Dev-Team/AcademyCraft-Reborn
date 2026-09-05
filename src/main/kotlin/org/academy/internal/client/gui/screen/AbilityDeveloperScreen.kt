package org.academy.internal.client.gui.screen

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.Minecraft
import net.minecraft.client.input.KeyEvent
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.neoforged.neoforge.common.NeoForge
import org.academy.api.client.ability.AbilitySystemClient
import org.academy.api.client.gui.animation.*
import org.academy.api.client.gui.command.GlyphDrawCommand
import org.academy.api.client.gui.command.ImageDrawCommand
import org.academy.api.client.gui.command.SkillProgressDrawCommand
import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.util.GlyphCommandGenerator
import org.academy.api.client.gui.util.WirelessPanelUtil
import org.academy.api.client.gui.widget.*
import org.academy.api.client.resources.R
import org.academy.api.client.resources.R.textures.gui.developer.*
import org.academy.api.common.ability.*
import org.academy.api.common.util.L10n
import org.academy.api.common.wireless.GetCurrentNodePacket
import org.academy.internal.common.ability.AbilityDevelopmentAccess
import org.academy.internal.common.ability.ProficiencyPolicy
import org.academy.internal.common.ability.level0.Level0
import org.academy.internal.common.world.item.AbilityControlTabletItem
import org.academy.internal.common.world.item.Items
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity
import org.apache.commons.lang3.RandomStringUtils
import org.misaka.MisakaNetworkClient
import java.util.concurrent.atomic.AtomicReference

internal enum class PropsConfirmationAnswer {
    ACCEPT,
    RANDOM,
    INVALID
}

internal fun parsePropsConfirmation(input: String): PropsConfirmationAnswer = when (input.trim().lowercase()) {
    "y" -> PropsConfirmationAnswer.ACCEPT
    "n" -> PropsConfirmationAnswer.RANDOM
    else -> PropsConfirmationAnswer.INVALID
}

class AbilityDeveloperScreen(val developmentSource: DevelopmentSource) : UiScreen(Component.empty()) {
    private val blockEntity: AbilityDeveloperBlockEntity? = when (developmentSource) {
        is DevelopmentSource.TabletDevelopmentSource -> {
            val player = minecraft.player ?: throw RuntimeException("Player is null")
            if (!player.getItemInHand(developmentSource.hand).`is`(Items.ABILITY_CONTROL_TABLET.get())) {
                throw RuntimeException("Ability control tablet is no longer held")
            }
            null
        }

        is DevelopmentSource.BlockDevelopmentSource -> {
            val level = minecraft.level ?: throw RuntimeException("Level is null")
            val entity = level.getBlockEntity(developmentSource.blockPos)
            if (entity is AbilityDeveloperBlockEntity) {
                entity.setOpen(true)
                entity
            } else {
                throw RuntimeException("Invalid block entity at ${developmentSource.blockPos}")
            }
        }
    }
    private lateinit var mainWidget: FrameLayoutWidget
    private lateinit var area: FrameLayoutWidget
    private var isConsoleMode: Boolean = false
    private lateinit var consoleOutputs: LinearLayoutWidget
    private lateinit var consoleScrollPanel: ScrollPanelWidget
    private var pendingPropsRecommendation: Identifier? = null
    private var activeCover: FrameLayoutWidget? = null
    private val skillLineBindings = mutableListOf<SkillLineBinding>()
    private var coursePage = CoursePage.ABILITY
    private var viewedSkillInfo: AbilitySystemClient.SkillInfo? = null

    private val maxDuSkills = 10f

    private fun currentEnergy(): Int {
        val developer = blockEntity
        if (developer != null) return developer.energyStored
        val player = minecraft.player ?: return 0
        return AbilityControlTabletItem.storedEnergy(
            player.getItemInHand((developmentSource as DevelopmentSource.TabletDevelopmentSource).hand)
        )
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
        pendingPropsRecommendation = null
        skillLineBindings.clear()
        viewedSkillInfo = null

        mainWidget = root.frame("main") {
            gravity(Gravity.CENTER)
            size(PANEL_MAIN_WIDTH, PANEL_MAIN_HEIGHT)

            frame("parent_left") {
                gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
                margin(4f, 0f, 0f, 0f)
                size(108.5f, 187f)
                image(
                    parent_background_developerleft,
                    "left_bg"
                ) {
                    matchParent()
                }
                image(ui_developerleft, "ui_left") {
                    gravity(Gravity.RIGHT or Gravity.CENTER_VERTICAL)
                    size(108.5f, 187f)
                }
                frame("panel_machine") {
                    gravity(Gravity.TOP_LEFT)
                    size(108.5f, 187f)
                    fillMachinePanel(this)
                }
                frame("panel_ability") {
                    gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
                    margin(2f, -20f, 0f, 0f)
                    size(104f, 32f)
                    fillAbilityPanel(this)
                }
            }

            frame("parent_right") {
                gravity(Gravity.RIGHT or Gravity.CENTER_VERTICAL)
                margin(0f, 0f, 4f, 0f)
                size(278f, 187f)
                image(
                    parent_background_developerright,
                    "right_bg"
                ) {
                    matchParent()
                }
                image(ui_developerright, "ui_right") {
                    gravity(Gravity.RIGHT or Gravity.CENTER_VERTICAL)
                    size(278f, 187f)
                }
                val category = AbilitySystemClient.getCategory()
                frame("area") {
                    gravity(Gravity.TOP_LEFT)
                    margin(10f, 18f, 0f, 0f)
                    size(257f, 139f)
                    area = this
                    if (category !is Level0) {
                        fillSkillTreeArea(area)
                    } else {
                        isConsoleMode = true
                        fillConsoleArea(area)
                    }
                }
            }
        }

        val anim = ObjectAnimator.ofFloat(
            { mainWidget.translationY = it },
            -PANEL_MAIN_HEIGHT, 0f
        ).setDuration(500L).setInterpolator(EasingFunctions.EASE_OUT_EXPO)
        anim.addListener(object : AnimatorListener {
            override fun onAnimationEnd(animation: Animator) {
                if (isConsoleMode) {
                    startConsoleBoot()
                }
            }
        })
        mainWidget.startAnimation(anim)
    }

    private fun fillAbilityPanel(panel: FrameLayoutWidget) {
        val category = AbilitySystemClient.getCategory()
        val level = AbilitySystemClient.getLevel()
        val isLevel0 = category is Level0
        val displayedLevel = if (isLevel0) AbilityLevel.LEVEL0 else level
        val levelProgress = if (isLevel0) 0f else AbilitySystemClient.getAbilityProgress()

        panel.frame("logo_ability") {
            gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
            size(32f, 32f)
            image(category.getDeveloperIcon(), "icon") {
                gravity(Gravity.CENTER)
                size(32f, 32f)
            }
        }

        val categoryKey = category.key
        val translationKey = "ability_category.${categoryKey.namespace}.${categoryKey.path}"
        val translatedName = Language.getInstance().getOrDefault(translationKey)
            .takeUnless { it == translationKey }
            ?: category.getDisplayName()
        panel.label(translatedName, "text_abilityname") {
            baseFontSize = 13f
            gravity(Gravity.TOP_LEFT)
            margin(31f, 2f, 0f, 0f)
            size(70f, 12f)
        }

        panel.progress("logo_progress_back") {
            gravity(Gravity.TOP_LEFT)
            margin(31f, 13.25f, 0f, 0f)
            size(70f, 1.5f)
            colors(0x4C666666, 0x4C666666)
            value(100f)
        }
        panel.progress("logo_progress") {
            gravity(Gravity.TOP_LEFT)
            margin(31f, 13.25f, 0f, 0f)
            size(70f, 1.5f)
            colors(0x00000000, -0x1)
            value(levelProgress * 100f)
        }

        panel.label("EXP ${(levelProgress * 100f).toInt()}%", "text_exp") {
            gravity(Gravity.TOP_LEFT)
            margin(30f, 15.5f, 0f, 0f)
            size(42f, 10f)
        }

        if (!isLevel0 && AbilitySystemClient.canLevelUp()) {
            val targetLevel = level.levelCode + 1
            val machineRequired = !AbilityDevelopmentAccess.canDevelopAbilityLevel(
                developmentSource, targetLevel
            )
            panel.button("btn_upgrade") {
                gravity(Gravity.TOP_LEFT)
                margin(60f, 14.5f, 0f, 0f)
                size(48f, 15f)
                onClick { addCover(createLevelUpCover()) }
                if (machineRequired) {
                    tooltipText = L10n["academy.ability_developer.portable.level_restricted"]
                }
                image(button_learn, "tex") {
                    matchParent()
                    alpha = if (machineRequired) 0.4f else 1.0f
                }
            }
        } else {
            panel.label("Level ${displayedLevel.levelCode}", "text_level") {
                baseFontSize = 9f
                rgb(0.09f, 0.46f, 0.84f)
                gravity(Gravity.TOP_RIGHT)
                margin(0f, 16f, 3f, 0f)
                size(42f, 12f)
            }
        }
    }

    private fun fillMachinePanel(panel: FrameLayoutWidget) {
        panel.image(
            parent_background_developermachine,
            "machine_bg"
        ) {
            matchParent()
        }

        val developer = blockEntity
        if (developer != null) {
            panel.label("Current Node:", "text_wireless") {
                gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
                margin(4.25f, 34f, 0f, 0f)
                size(100f, 12f)
            }

            panel.button("button_wireless") {
                gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
                margin(4.25f, 58f, 0f, 0f)
                size(100f, 16f)
                onClick {
                    val cover = createCover()
                    val wirelessPage = WirelessPanelUtil.create(developer.blockPos, true).apply {
                        lp {
                            gravity(Gravity.CENTER)
                        }
                    }
                    cover.addChild("wireless_page", wirelessPage)
                    addCover(cover)
                }
                image(
                    R.textures.gui.element.element_background300x32,
                    "bar"
                ) {
                    matchParent()
                }
                val nodeName = label("N/A", "text_nodename") {
                    gravity(Gravity.CENTER_LEFT)
                    margin(26f, 0f, 0f, 0f)
                    size(70f, 12f)
                }
                image(R.textures.gui.icon.icon_node, "logo_node") {
                    gravity(Gravity.TOP_LEFT)
                    margin(7f, 2f, 0f, 0f)
                    size(12f, 12f)
                }

                MisakaNetworkClient.FUTURE_MANAGER.send(GetCurrentNodePacket(developer.blockPos)) {
                    if (it != null) nodeName.text = it.nodeName
                }
            }
        } else {
            panel.label(L10n["academy.ability_developer.energy_source"], "text_energy_source") {
                gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
                margin(4.25f, 34f, 0f, 0f)
                size(100f, 12f)
            }

            panel.label(L10n["academy.ability_developer.energy_source.tablet"], "text_tablet") {
                gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
                margin(4.25f, 58f, 0f, 0f)
                size(100f, 16f)
            }
        }

        panel.label("Power:", "text_power") {
            gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
            margin(4.25f, 86f, 0f, 0f)
            size(100f, 12f)
        }

        panel.add("progress_power", ProgressBarWidget().apply {
            setFrameUpdate {
                val capacity = maxEnergy()
                setProgress(
                    if (capacity > 0)
                        currentEnergy().toFloat() / capacity * 100f
                    else 0f
                )
                true
            }
        }) {
            gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
            margin(5.75f, 111f, 0f, 0f)
            size(97f, 8f)
            colors(0x40000000, 0xFFFFD45A.toInt())
        }

        panel.label("Sync Rate:", "text_syncrate") {
            gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
            margin(4.25f, 132f, 0f, 0f)
            size(100f, 12f)
        }

        panel.progress("progress_syncrate") {
            gravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
            margin(5.75f, 155f, 0f, 0f)
            size(97f, 8f)
            colors(0x40000000, 0xFF64C0FF.toInt())
            value(100f)
        }
    }

    private fun fillConsoleArea(area: FrameLayoutWidget) {
        consoleScrollPanel = area.scrollPanel(name = "scroll_panel") {
            matchParent()
        }
        consoleOutputs = consoleScrollPanel.column("outputs", spacing = 4f) {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .padding(4f)
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
                    val progressSequence = (1..6).map { it * 10 + (-3..2).random() } + (64..67).random()

                    val label = outputs.label("", "label_progress") {
                        gravity(Gravity.BOTTOM_LEFT)
                    }

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
                }
            }
        }
    }

    private fun addOutput(outputs: LinearLayoutWidget, text: String, onEnd: () -> Unit = {}) {
        val label = outputs.add(
            "label_${text.hashCode()}_${RandomStringUtils.insecure().nextAlphabetic(4)}",
            object : LabelWidget(text) {
                var progress = 0f

                fun setRevealProgress(value: Float) {
                    progress = value.coerceIn(0f, 1f)
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
        ) {
            gravity(Gravity.BOTTOM_LEFT)
        }

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
        return standaloneRow {
            gravity(Gravity.BOTTOM_LEFT)
            height(8f)
            widthMode(SizeMode.MATCH_PARENT)
            label(L10n["academy.ability_developer.console.prompt"], "label") {
                gravity(Gravity.BOTTOM_LEFT)
            }

            textBox(8, "text_box") {
                gravity(Gravity.BOTTOM_LEFT)
                width(0f)
                heightMode(SizeMode.MATCH_PARENT)
                weight(1f)
                background = null
                enter { input ->
                    outputs.removeChild("input_area")
                    addOutputLine(
                        outputs,
                        "${L10n["academy.ability_developer.console.prompt"]} $input"
                    )
                    val normalizedInput = input.trim().lowercase()
                    if (pendingPropsRecommendation != null) {
                        when (parsePropsConfirmation(normalizedInput)) {
                            PropsConfirmationAnswer.ACCEPT -> {
                                pendingPropsRecommendation = null
                                requestInitialDevelopment(outputs, StartLevelDevPacket.Mode.ACCEPT_PROPS)
                            }

                            PropsConfirmationAnswer.RANDOM -> {
                                pendingPropsRecommendation = null
                                requestInitialDevelopment(outputs, StartLevelDevPacket.Mode.RANDOM)
                            }

                            PropsConfirmationAnswer.INVALID -> {
                                addOutputLine(
                                    outputs,
                                    L10n["academy.ability_developer.console.props_invalid_answer"]
                                )
                                attachCommandInput(outputs)
                            }
                        }
                        return@enter
                    }

                    when (normalizedInput) {
                        "learn" -> {
                            addOutputLine(outputs, L10n["academy.ability_developer.console.dev_begin"])
                            AbilitySystemClient.resetDevState()
                            requestInitialDevelopment(outputs, StartLevelDevPacket.Mode.DIRECT)
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
            }
        }
    }

    private fun requestInitialDevelopment(
        outputs: LinearLayoutWidget,
        mode: StartLevelDevPacket.Mode
    ) {
        MisakaNetworkClient.FUTURE_MANAGER.send(StartLevelDevPacket(developmentSource, mode)) { response ->
            when {
                response == null -> {
                    addOutputLine(outputs, "Unknown error")
                    attachCommandInput(outputs)
                }

                response.isSuccess -> startInitialDevelopmentProgress(outputs)

                response.requiresConfirmation() -> {
                    val recommendation = response.recommendedCategory
                    if (recommendation == null) {
                        addOutputLine(outputs, response.message)
                        attachCommandInput(outputs)
                        return@send
                    }
                    pendingPropsRecommendation = recommendation
                    addOutputLine(
                        outputs,
                        L10n["academy.ability_developer.console.props_expected"].format(
                            localizedAbilityCategoryName(recommendation)
                        )
                    )
                    addOutputLine(outputs, L10n["academy.ability_developer.console.props_confirm"])
                    attachCommandInput(outputs)
                }

                response.message == "P.R.O.P.S recommendation expired" -> {
                    pendingPropsRecommendation = null
                    addOutputLine(outputs, L10n["academy.ability_developer.console.props_expired"])
                    attachCommandInput(outputs)
                }

                else -> {
                    pendingPropsRecommendation = null
                    addOutputLine(outputs, response.message)
                    attachCommandInput(outputs)
                }
            }
        }
    }

    private fun startInitialDevelopmentProgress(outputs: LinearLayoutWidget) {
        pendingPropsRecommendation = null
        val progressLabel = outputs.label(L10n["academy.ability_developer.progress"] + " 0%", "dev_progress") {
            gravity(Gravity.BOTTOM_LEFT)
        }
        consoleScrollPanel.scrollToEnd()

        fun poll() {
            when (AbilitySystemClient.getDevState()) {
                DevState.DEVELOPING -> {
                    progressLabel.text =
                        L10n["academy.ability_developer.progress"] + " " +
                                (AbilitySystemClient.getDevProgress() * 100).toInt() + "%"
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

                else -> consoleScrollPanel.pollNextFrame { poll() }
            }
        }
        poll()
    }

    private fun localizedAbilityCategoryName(category: Identifier): String {
        val key = "ability_category.${category.namespace}.${category.path}"
        return Language.getInstance().getOrDefault(key).takeUnless { it == key }
            ?: category.path
    }

    private fun attachCommandInput(outputs: LinearLayoutWidget) {
        outputs.removeChild("input_area")
        val inputArea = outputs.add("input_area", createCommandInputArea(outputs))
        inputArea.children["text_box"]?.let { inputArea.focusedChild = it }
        scrollConsoleToEndAfterLayout()
    }

    private fun addOutputLine(outputs: LinearLayoutWidget, text: String) {
        outputs.label(text) {
            gravity(Gravity.BOTTOM_LEFT)
        }
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

        area.add("area_bg", object : ParallaxImageWidget(skill_panel_back) {
            override fun render(context: RenderContext) {
                setParallaxEnabled(!AbilityDeveloperLayoutEditor.isDebugMode())
                super.render(context)
            }
        }) {
            imageToViewRatio(0.9f, 0.9f)
            matchParent()
            sampler(FilterMode.LINEAR, true)
        }

        val pager = area.add("pager", PagerLayoutWidget()) {
            matchParent()
            switchDuration(150)
            interpolator(EasingFunctions.EASE_OUT_CUBIC)
        }

        skillLineBindings.clear()
        for (page in CoursePage.entries) {
            val pageContainer = pager.add("page_${page.name.lowercase()}", FrameLayoutWidget()) {
                matchParent()
            }
            fillSkillPage(pageContainer, page, category)
        }

        pager.jumpToPage(coursePage.ordinal)

        val leftBtn = createSkillPagerButton(-1) { switchSkillPage(pager, -1) }
        val rightBtn = createSkillPagerButton(1) { switchSkillPage(pager, 1) }
        area.add("pager_left", leftBtn)
        area.add("pager_right", rightBtn)
        updatePagerButtons(pager, leftBtn, rightBtn)
    }

    private fun fillSkillPage(
        container: FrameLayoutWidget,
        page: CoursePage,
        category: AbilityCategory
    ) {
        val skillInfos = when (page) {
            CoursePage.COMMON -> AbilitySystemClient.getCommonSkillInfos()
            CoursePage.ABILITY -> AbilitySystemClient.getCategorySkillInfos(category)
        }.filter { info ->
            SkillTreeVisibility.shouldDisplay(
                info.skill.scope,
                AbilitySystemClient.isSkillLearned(info.skill),
                info.dependencies.all { AbilitySystemClient.isSkillLearned(it.skill) },
                info.skill.devConditions.all { it.accepts() }
            )
        }

        val lineMap = mutableMapOf<String, Widget>()
        for (info in skillInfos) {
            for (dep in info.dependencies) {
                val line = createSkillLine(category, dep, info)
                val key = "line_${info.skill.getKeyString()}_${dep.skill.getKeyString()}"
                container.add(key, line)
                lineMap[key] = line
                skillLineBindings.add(SkillLineBinding(line, category, dep, info))
            }
        }

        val nodeMap = mutableMapOf<String, Widget>()
        for (idx in skillInfos.indices) {
            val info = skillInfos[idx]
            val node = createSkillNode(category, info)
            val key = "node_${info.skill.getKeyString()}"
            container.add(key, node)
            nodeMap[key] = node
        }

        container.add("layout_debug_status", object : LabelWidget("") {
            override fun render(context: RenderContext) {
                text = if (AbilityDeveloperLayoutEditor.isDebugMode()) {
                    "LAYOUT: ${category.key} / ${page.name.lowercase()}  (drag icons; snap 0.5px)"
                } else {
                    ""
                }
                super.render(context)
            }
        }) {
            baseFontSize = 6f
            isEnabled = false
            gravity(Gravity.TOP_LEFT)
            margin(2f, 1f, 0f, 0f)
            size(250f, 8f)
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

        val line = object : ImageWidget(R.textures.gui.element.line) {
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
        line.lp {
            gravity(Gravity.TOP_LEFT)
            size(0f, 5.5f)
        }
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
        for ((widget, category, child, dependency) in skillLineBindings) {
            updateSkillLineGeometry(widget, category, child, dependency)
        }
    }

    private fun createSkillNode(category: AbilityCategory, info: AbilitySystemClient.SkillInfo): ButtonWidget {
        return SkillNode(category, info).apply { buildNode() }
    }

    private inner class SkillNode(
        private val category: AbilityCategory,
        private val info: AbilitySystemClient.SkillInfo
    ) : ButtonWidget() {
        private val isLearned = AbilitySystemClient.isSkillLearned(info.skill)
        private val machineRequired = !isLearned && !AbilityDevelopmentAccess.canLearnSkill(
            developmentSource, info.skill.recommendedLevel.levelCode
        )
        private val hasDepsLearned = info.dependencies.isEmpty() || info.dependencies.all {
            AbilitySystemClient.isSkillLearned(it.skill)
        }
        private val baseAlpha = when {
            isLearned -> 1.0f
            hasDepsLearned -> 0.7f
            else -> 0.25f
        }
        private val mAlpha = if (machineRequired) minOf(baseAlpha, 0.4f) else baseAlpha

        private var isLayoutDragging = false
        private var dragOffsetX = 0f
        private var dragOffsetY = 0f
        private lateinit var outlineTexView: GpuTextureView
        private lateinit var maskTexView: GpuTextureView

        fun buildNode() {
            val initialPosition = AbilityDeveloperLayoutEditor.getPosition(category, info)
            lp {
                gravity(Gravity.TOP_LEFT)
                margin(initialPosition.x(), initialPosition.y(), 0f, 0f)
                size(16f, 16f)
            }
            alpha = mAlpha

            image(skill_back, "icon_bg") {
                gravity(Gravity.CENTER)
                size(23f, 23f)
            }

            if (isLearned) {
                image(skill_back, "learned_highlight") {
                    gravity(Gravity.CENTER)
                    size(25f, 25f)
                    brightnessOf(1.25f)
                    alpha = 0.32f
                }
            }

            add("outline_bg", ImageWidget(skill_outline).apply {
                setFrameUpdate {
                    val full = isLearned && AbilitySystemClient.getSkillProficiencyProgress(info.skill) >= 1f
                    setBrightness(if (full) 1.4f else 0.2f)
                    alpha = if (full) 1f else mAlpha * 0.6f
                    true
                }
            }) {
                gravity(Gravity.CENTER)
                size(31f, 31f)
                brightnessOf(0.2f)
                alpha = mAlpha * 0.6f
            }

            add("icon", CircleImageWidget(info.texture)) {
                gravity(Gravity.CENTER)
                size(14f, 14f)
            }

            add("outline", object : AbstractWidget() {
                override fun renderInternal(context: RenderContext) {
                    if (!isLearned) return
                    val skillProgress = AbilitySystemClient.getSkillProficiencyProgress(info.skill)
                    if (skillProgress <= 0f) return

                    val texManager = Minecraft.getInstance().textureManager
                    if (!::outlineTexView.isInitialized || outlineTexView.isClosed) {
                        outlineTexView = texManager.getTexture(skill_outline).getTextureView()
                    }
                    if (!::maskTexView.isInitialized || maskTexView.isClosed) {
                        maskTexView = texManager.getTexture(skill_radial_mask).getTextureView()
                    }

                    val lp = layoutParams
                    val paddedWidth = width - lp.paddingLeft - lp.paddingRight
                    val paddedHeight = height - lp.paddingTop - lp.paddingBottom
                    if (paddedWidth <= 0 || paddedHeight <= 0) return

                    val finalAlpha = alpha * context.accumulatedAlpha

                    context.pose().pushPose()
                    context.pose().translate(lp.paddingLeft, lp.paddingTop)
                    context.submit(
                        SkillProgressDrawCommand(
                            outlineTexView, maskTexView,
                            width, height, skillProgress, finalAlpha
                        )
                    )
                    context.pose().popPose()
                }
            }) {
                gravity(Gravity.CENTER)
                size(31f, 31f)
            }

            val progressRef = AtomicReference(0f)
            val updater = { p: Float ->
                progressRef.set(p)
                val s = 1.0f + 0.2f * p
                scaleX = s
                scaleY = s
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
            stateListAnimator = animator

            onClick {
                viewedSkillInfo = info
                area.clearChildren()
                area.add("skill_view", createSkillViewCover(info))
            }
        }

        override fun render(context: RenderContext) {
            val position = AbilityDeveloperLayoutEditor.getPosition(category, info)
            if (layoutParams.marginLeft != position.x() || layoutParams.marginTop != position.y()) {
                applyPosition(position.x(), position.y())
            }
            tooltipText = buildTooltip()
            updateParallax()
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
            applyPosition(x, y)
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

        private fun applyPosition(x: Float, y: Float) {
            layoutParams.marginLeft = x
            layoutParams.marginTop = y
            updateSkillLines()
            area.requestLayout()
        }

        private fun buildTooltip(): String {
            if (AbilityDeveloperLayoutEditor.isDebugMode()) {
                val position = AbilityDeveloperLayoutEditor.getPosition(category, info)
                return "${category.key}\n${info.skill.getKeyString()}  (${position.x()}, ${position.y()})"
            }
            if (isLearned) {
                val proficiency = AbilitySystemClient.getSkillProficiency(info.skill)
                return "${info.skill.translatedName}\n${L10n["academy.ability_developer.skill_exp"]}" +
                        String.format("%.2f/3000 (%.2f%%)", proficiency, proficiency / 30f)
            }
            if (machineRequired) {
                return "${info.skill.translatedName}\n" +
                        L10n["academy.ability_developer.portable.skill_restricted"]
            }
            return info.skill.translatedName
        }

        private fun updateParallax() {
            if (AbilityDeveloperLayoutEditor.isDebugMode()) {
                translationX = 0f
                translationY = 0f
                return
            }
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
        mainWidget.isEnabled = false
        val blur = cover.children[BLUR_KEY] as BlurPanelWidget
        cover.startAnimation(
            ObjectAnimator.ofFloat({ cover.alpha = it }, 0f, 1f)
                .setDuration(COVER_ANIM_MS)
                .setInterpolator(EasingFunctions.EASE_OUT_CUBIC)
        )
        blur.startAnimation(
            ObjectAnimator.ofFloat({ blur.blurRadius = it }, 0f, BLUR_MAX_RADIUS)
                .setDuration(COVER_ANIM_MS)
                .setInterpolator(EasingFunctions.EASE_OUT_CUBIC)
        )
    }

    private fun removeCover(rebuild: Boolean = false) {
        val cover = activeCover ?: return
        if (AbilitySystemClient.getDevState() == DevState.DEVELOPING) {
            MisakaNetworkClient.send(StopDevPacket(developmentSource))
            AbilitySystemClient.resetDevState()
        }
        val blur = cover.children[BLUR_KEY] as BlurPanelWidget
        cover.cancelAnimations()
        blur.cancelAnimations()
        cover.isEnabled = false
        cover.startAnimation(
            ObjectAnimator.ofFloat({ cover.alpha = it }, cover.alpha, 0f)
                .setDuration(COVER_ANIM_MS / 2)
                .addListener(object : AnimatorListener {
                    override fun onAnimationEnd(animation: Animator) {
                        root.removeChild("cover")
                        activeCover = null
                        mainWidget.isEnabled = true
                        if (rebuild) rebuildSkillTree()
                    }
                })
        )
        blur.startAnimation(
            ObjectAnimator.ofFloat({ blur.blurRadius = it }, blur.blurRadius, 0f)
                .setDuration(COVER_ANIM_MS / 2)
        )
    }

    private fun createCover(onClick: () -> Unit = { removeCover() }): FrameLayoutWidget =
        standaloneFrame {
            matchParent()
            blurPanel(name = BLUR_KEY) {
                matchParent()
                onClick {
                    onClick()
                }
            }
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

    private fun switchSkillPage(pager: PagerLayoutWidget, delta: Int) {
        val target = (pager.currentPage + delta).coerceIn(0, pager.pageCount - 1)
        if (target == pager.currentPage) return
        coursePage = CoursePage.entries[target]
        pager.switchToPage(target)
        val leftBtn = area.children["pager_left"] as? ButtonWidget
        val rightBtn = area.children["pager_right"] as? ButtonWidget
        if (leftBtn != null && rightBtn != null) {
            updatePagerButtons(pager, leftBtn, rightBtn)
        }
    }

    private fun updatePagerButtons(pager: PagerLayoutWidget, left: ButtonWidget, right: ButtonWidget) {
        left.visibility = if (pager.currentPage > 0) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
        right.visibility =
            if (pager.currentPage < pager.pageCount - 1) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
    }

    private fun createSkillPagerButton(direction: Int, onClick: () -> Unit): ButtonWidget {
        val label = LabelWidget(if (direction < 0) "‹" else "›")
        val button = ButtonWidget()

        button.lp {
            gravity(if (direction < 0) Gravity.LEFT or Gravity.CENTER_VERTICAL else Gravity.RIGHT or Gravity.CENTER_VERTICAL)
            margin(2f, 0f, 2f, 0f)
            size(12f, 20f)
        }
        button.tooltipText = if (direction < 0) {
            L10n["academy.ability_developer.course.common"]
        } else {
            L10n["academy.ability_developer.course.ability"]
        }
        button.onClick { onClick() }
        label.baseFontSize = 12f
        label.isEnabled = false
        button.add("label", label) {
            gravity(Gravity.CENTER)
            size(10f, 14f)
        }
        return button
    }

    private fun createDevButton(brightnessRef: AtomicReference<Float> = AtomicReference(0.85f)): ButtonWidget {
        val btnTex = ImageWidget(button)
        val btnWid = object : ButtonWidget() {
            override fun render(context: RenderContext) {
                val target = if (isHovered || isFocused || isPressed) 1.1f else 0.85f
                if (btnTex.red != target) {
                    brightnessRef.set(target)
                    btnTex.setBrightness(target)
                }
                super.render(context)
            }
        }
        btnWid.size(32f, 16f)
        btnWid.add("tex", btnTex) {
            matchParent()
            brightnessOf(0.85f)
        }
        return btnWid
    }

    private fun createSkillBackButton(): ButtonWidget {
        val label = LabelWidget("<")
        val button = ButtonWidget()
        button.lp {
            gravity(Gravity.TOP_RIGHT)
            margin(0f, 3f, 3f, 0f)
            size(22f, 14f)
        }
        button.tooltipText = L10n["academy.ability_developer.back"]
        button.onClick { rebuildSkillTree() }

        label.baseFontSize = 9f
        label.isEnabled = false
        button.add("label", label) {
            gravity(Gravity.CENTER)
            size(10f, 10f)
        }
        return button
    }

    private fun createSkillViewCover(info: AbilitySystemClient.SkillInfo): FrameLayoutWidget {
        val isLearned = AbilitySystemClient.isSkillLearned(info.skill)
        val skill = info.skill
        val skillId = skill.getKeyString()
        val machineRequired = !isLearned && !AbilityDevelopmentAccess.canLearnSkill(
            developmentSource, skill.recommendedLevel.levelCode
        )

        val cover = standaloneFrame {
            matchParent()
            button("bg") {
                matchParent()
                onClick { rebuildSkillTree() }
            }
        }
        cover.startAnimation(
            ObjectAnimator.ofFloat({ cover.alpha = it }, 0f, 1f).setDuration(500)
                .setInterpolator(EasingFunctions.EASE_OUT_SINE)
        )

        val proficiency = AbilitySystemClient.getSkillProficiency(skill)
        val iconProgressRef = AtomicReference(
            if (isLearned) AbilitySystemClient.getSkillProficiencyProgress(skill) else 0f
        )

        cover.column("cover_center") {
            gravity(Gravity.CENTER)
            add("skill_wid", object : FrameLayoutWidget() {
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
                    val outlineTex = if (progress >= 1.0f) skill_view_outline_glow else skill_view_outline
                    val backView =
                        texManager.getTexture(skill_back).getTextureView()
                    val outlineView = texManager.getTexture(outlineTex).getTextureView()
                    val maskView = texManager.getTexture(skill_radial_mask).getTextureView()
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
                            50f,
                            50f,
                            progress,
                            finalAlpha
                        )
                    )
                    context.pose().popPose()
                }
            }) {
                gravity(Gravity.CENTER)
                size(50f, 50f)
            }

            column("text_area") {
                gravity(Gravity.CENTER)
                if (isLearned) {
                    label(skill.translatedName, "name") {
                        baseFontSize = 10f
                        wrapText = true
                        gravity(Gravity.CENTER)
                        width(240f)
                    }

                    label(
                        L10n["academy.ability_developer.skill_exp"] +
                                String.format("%.2f/3000 (%.2f%%)", proficiency, proficiency / 30f),
                        "exp"
                    ) {
                        baseFontSize = 8f
                        rgb(0.63f, 0.88f, 1.0f)
                        gravity(Gravity.CENTER)
                    }

                    val details = standaloneColumn(spacing = 2f) {
                        sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    }

                    details.label(skill.translatedDescription, "desc") {
                        baseFontSize = 7f
                        wrapText = true
                        gravity(Gravity.CENTER)
                        width(228f)
                    }

                    if (SkillProficiencyProfiles.isDeclared(skill.keyString)) {
                        val milestone = AbilitySystemClient.getSkillProficiencyMilestone(skill)
                        listOf(1000, 2000, 3000).forEachIndexed { index, threshold ->
                            val reached = milestone > index
                            val next = milestone == index
                            val marker = if (reached) "✓" else if (next) "→" else "•"
                            val key = "${skill.descriptionId}.proficiency.$threshold"
                            details.label(
                                "$marker $threshold  ${Language.getInstance().getOrDefault(key)}",
                                "proficiency_$threshold"
                            ) {
                                baseFontSize = 5f
                                wrapText = true
                                gravity(Gravity.LEFT)
                                width(228f)
                                when {
                                    reached -> rgb(0.35f, 0.95f, 1.0f)
                                    next -> rgb(1.0f, 0.78f, 0.25f)
                                    else -> rgb(0.55f, 0.58f, 0.63f)
                                }
                            }
                        }
                        if (ProficiencyPolicy.clientHasRestriction(skill)) {
                            details.label(
                                L10n["academy.ability_developer.proficiency_restricted"],
                                "proficiency_restricted"
                            ) {
                                baseFontSize = 8f
                                wrapText = true
                                rgb(1.0f, 0.38f, 0.3f)
                                gravity(Gravity.LEFT)
                                width(228f)
                            }
                        }
                    }
                    scrollPanel(name = "details", content = details) {
                        gravity(Gravity.CENTER)
                        size(240f, 104f)
                    }
                } else {
                    label("${skill.translatedName} (LV ${skill.recommendedLevel.levelCode})", "lvl_name") {
                        baseFontSize = 10f
                        wrapText = true
                        gravity(Gravity.CENTER)
                        width(240f)
                    }

                    label(L10n["academy.ability_developer.skill_not_learned"], "not_learned") {
                        baseFontSize = 10f
                        wrapText = true
                        rgb(1.0f, 0.33f, 0.33f)
                        gravity(Gravity.CENTER)
                        width(240f)
                    }

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
                    req.orientation = Orientation.HORIZONTAL
                    add("req", req) {
                        gravity(Gravity.CENTER)
                    }
                    req.label(L10n["academy.ability_developer.req"], "label") {
                        gravity(Gravity.CENTER_BOTTOM)
                        baseFontSize = 9f
                        alpha = 0.66f
                    }

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
                        val condIcon = if (!accepted) MonochromeImageWidget(
                            cond.getIcon() ?: R.textures.gui.icon.close
                        ) else ImageWidget(cond.getIcon() ?: R.textures.gui.icon.close)
                        condWid.add("icon", condIcon) {
                            matchParent()
                        }
                        req.add("cond_$idx", condWid) {
                            gravity(Gravity.CENTER)
                            size(14f, 14f)
                        }
                    }

                    val learnQuestion = if (machineRequired) {
                        L10n["academy.ability_developer.portable.skill_restricted"]
                    } else {
                        L10n["academy.ability_developer.learn_question"].format(
                            LearningHelper.getEstimatedSkillConsumption(skill)
                        )
                    }
                    val messageLabel = LabelWidget(learnQuestion).apply {
                        setFrameUpdate {
                            if (machineRequired) {
                                text = L10n["academy.ability_developer.portable.skill_restricted"]
                                return@setFrameUpdate true
                            }
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
                            true
                        }
                    }
                    add("message", messageLabel) {
                        baseFontSize = if (machineRequired) 8f else 10f
                        wrapText = true
                        alpha = 0.66f
                        gravity(Gravity.CENTER_HORIZONTAL)
                        width(240f)
                    }

                    if (!AbilitySystemClient.isDevelopmentActive() && !machineRequired) {
                        val learnBtn = createDevButton()
                        learnBtn.layoutParams.gravity(Gravity.CENTER_HORIZONTAL)
                        learnBtn.onClick {
                            if (AbilitySystemClient.getDevTargetId() == skillId
                                && AbilitySystemClient.getDevState() == DevState.FAILED
                            ) {
                                AbilitySystemClient.resetDevState()
                            }
                            if (!AbilityDevelopmentAccess.canLearnSkill(
                                    developmentSource, skill.recommendedLevel.levelCode
                                )
                            ) {
                                messageLabel.text =
                                    L10n["academy.ability_developer.portable.skill_restricted"]
                            } else if (currentEnergy() < LearningHelper.getEstimatedSkillConsumption(skill)) {
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
                                        removeChild("learn_btn")
                                    } else {
                                        val failure = response?.message
                                            ?: L10n["academy.ability_developer.dev_failed"]
                                        AbilitySystemClient.rejectDevelopmentRequest(skillId, failure)
                                        learnBtn.isEnabled = true
                                    }
                                }
                            }
                        }
                        add("learn_btn", learnBtn)
                    }
                }
            }
        }
        cover.add("back", createSkillBackButton())
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
        val targetLevel = level.levelCode + 1
        val machineRequired = !AbilityDevelopmentAccess.canDevelopAbilityLevel(
            developmentSource, targetLevel
        )
        val cost = LearningHelper.getEstimatedLevelUpConsumption(level.levelCode)

        var shouldRebuild = false

        val iconProgressRef = AtomicReference(0f)
        val levelIconPath = when (level) {
            AbilityLevel.LEVEL1 -> R.textures.abilities.condition.any2
            AbilityLevel.LEVEL2 -> R.textures.abilities.condition.any3
            AbilityLevel.LEVEL3 -> R.textures.abilities.condition.any4
            else -> R.textures.abilities.condition.any5
        }

        val cover = createCover {
            if (shouldRebuild) onClose()
            else removeCover()
        }

        cover.apply {
            gravity(Gravity.CENTER)
            column("cover_center") {
                gravity(Gravity.CENTER)
                sizeMode(SizeMode.WRAP_CONTENT)

                add("skill_wid", object : FrameLayoutWidget() {
                    override fun renderInternal(context: RenderContext) {
                        super.renderInternal(context)
                        val finalAlpha = alpha * context.accumulatedAlpha
                        val progress = iconProgressRef.get()
                        try {
                            val texManager = Minecraft.getInstance().textureManager
                            val outlineTex = if (progress >= 1.0f) skill_view_outline_glow else skill_view_outline
                            val backView = texManager.getTexture(skill_back).getTextureView()
                            val outlineView = texManager.getTexture(outlineTex).getTextureView()
                            val maskView = texManager.getTexture(skill_radial_mask).getTextureView()
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
                }) {
                    gravity(Gravity.CENTER)
                    size(50f, 50f)
                }

                column("text_area") {
                    gravity(Gravity.CENTER)
                    label(L10n["academy.ability_developer.uplevel"].format(targetLevel), "title") {
                        baseFontSize = 10f
                        wrapText = true
                        gravity(Gravity.CENTER_HORIZONTAL)
                        width(240f)
                    }

                    label(L10n["academy.ability_developer.req"] + " " + cost, "req") {
                        baseFontSize = 9f
                        gravity(Gravity.CENTER_HORIZONTAL)
                    }

                    val hintLabel = label(
                        if (machineRequired) {
                            L10n["academy.ability_developer.portable.level_restricted"]
                        } else {
                            L10n["academy.ability_developer.level_question"]
                        },
                        "hint"
                    ) {
                        baseFontSize = if (machineRequired) 8f else 9f
                        wrapText = true
                        gravity(Gravity.CENTER_HORIZONTAL)
                        width(240f)
                    }

                    if (!machineRequired) {
                        val upgBtn = createDevButton()
                        upgBtn.layoutParams.gravity(Gravity.CENTER_HORIZONTAL)
                        upgBtn.onClick {
                            if (!AbilityDevelopmentAccess.canDevelopAbilityLevel(
                                    developmentSource, targetLevel
                                )
                            ) {
                                hintLabel.text =
                                    L10n["academy.ability_developer.portable.level_restricted"]
                            } else if (currentEnergy() < cost) {
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
                        }
                        removeChild("upgrade_btn")
                        add("upgrade_btn", upgBtn)
                    }
                }
            }
        }

        return cover
    }

    companion object {
        const val PANEL_MAIN_WIDTH: Float = 400f
        const val PANEL_MAIN_HEIGHT: Float = 187f
        private const val BLUR_KEY = "blur"
        private const val COVER_ANIM_MS = 300L
        private const val BLUR_MAX_RADIUS = 8f
        private const val CONSOLE_CHAR_DELAY_MS: Long = 10L
    }
}
