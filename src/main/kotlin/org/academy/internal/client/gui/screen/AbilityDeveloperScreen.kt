package org.academy.internal.client.gui.screen

import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.common.NeoForge
import org.academy.AcademyCraft
import org.academy.api.client.Resource
import org.academy.api.client.gui.animation.Animator
import org.academy.api.client.gui.animation.AnimatorListener
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.command.GlyphDrawCommand
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.widget.*
import org.academy.api.common.ability.AcquireCategoryPacket
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity
import org.apache.commons.lang3.RandomStringUtils
import org.misaka.MisakaNetworkClient
import kotlin.math.abs

class AbilityDeveloperScreen(val mainPos: BlockPos) : UiScreen(Component.empty()) {
    var abilityDeveloperBlockEntity: AbilityDeveloperBlockEntity? = null

    init {
        val level = Minecraft.getInstance().level
        val entity = level?.getBlockEntity(mainPos)
        if (entity is AbilityDeveloperBlockEntity) {
            abilityDeveloperBlockEntity = entity
            entity.setOpen(true)
        } else {
            onClose()
        }
    }

    override fun isPauseScreen(): Boolean = false

    override fun onClose() {
        super.onClose()
        abilityDeveloperBlockEntity?.setOpen(false)
        NeoForge.EVENT_BUS.unregister(this)
    }

    override fun onInit() {
        val duration = 500L

        val main = FrameLayoutWidget()
        main.layoutParams = FrameLayoutWidget.LayoutParams()
            .gravity(Gravity.CENTER)
            .size(PANEL_MAIN_WIDTH, PANEL_MAIN_HEIGHT)

        root.addChild("main", main)
        main.startAnimation(
            ObjectAnimator.ofFloat(
                { main.layoutParams = main.layoutParams.padding(it, PANEL_MAIN_HEIGHT / 2) },
                PANEL_MAIN_WIDTH / 2, 0f
            ).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_EXPO)
        )

        val back = BlendQuadWidget()
        back.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)
        back.alpha = 0.5f
        main.addChild("back", back)

        val content = FrameLayoutWidget()
        content.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)

        val anim = ObjectAnimator.ofFloat(
            { main.layoutParams = main.layoutParams.padding(0f, it) },
            PANEL_MAIN_HEIGHT / 2, 0f
        ).setDuration(duration).setStartDelay(duration).setInterpolator(EasingFunctions.EASE_OUT_EXPO)

        val finalAnim = ObjectAnimator.ofFloat({ content.alpha = it }, 0f, 1f).setDuration(duration)

        anim.addListener(object : AnimatorListener {
            override fun onAnimationEnd(animation: Animator) {
                main.addChild("content", content)
                content.startAnimation(finalAnim)
            }
        })
        main.startAnimation(anim)

        val leftContent = createLeftContent()
        content.addChild("left_content", leftContent)

        val rightContent = createRightContent(anim, finalAnim)
        content.addChild("right_content", rightContent)

        val logo = createLogo()
        content.addChild("logo", logo)
    }

    private fun createLeftContent(): LinearLayoutWidget {
        val leftContent = LinearLayoutWidget()
        leftContent.layoutParams = FrameLayoutWidget.LayoutParams()
            .gravity(Gravity.START)
            .width(PANEL_MAIN_WIDTH / 4)
            .heightMode(SizeMode.MATCH_PARENT)
            .padding(6f, 8f, 6f, 0f)

        val playerInfoContent = createPlayerInfoContent()
        leftContent.addChild("player_info_content", playerInfoContent)

        val skillInfoContent = LinearLayoutWidget()
        skillInfoContent.layoutParams = WidgetContainer.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
        leftContent.addChild("skill_info_content", skillInfoContent)

        return leftContent
    }

    private fun createPlayerInfoContent(): LinearLayoutWidget {
        val playerInfoContent = LinearLayoutWidget()
        playerInfoContent.layoutParams = LinearLayoutWidget.LayoutParams()
            .heightMode(SizeMode.WRAP_CONTENT)
            .widthMode(SizeMode.MATCH_PARENT)

        val topLine = ImageWidget(Resource.Textures.ELEMENT_LINE)
        topLine.layoutParams = LinearLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(4f)
        playerInfoContent.addChild("top_line", topLine)

        val infoArea = createInfoArea()
        playerInfoContent.addChild("info_area", infoArea)

        val bottomLine = ImageWidget(Resource.Textures.ELEMENT_LINE)
        bottomLine.layoutParams = WidgetContainer.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(4f)
        playerInfoContent.addChild("bottom_line", bottomLine)

        return playerInfoContent
    }

    private fun createInfoArea(): RelativeLayoutWidget {
        val infoArea = RelativeLayoutWidget()
        infoArea.layoutParams = LinearLayoutWidget.LayoutParams()
            .heightMode(SizeMode.WRAP_CONTENT)
            .widthMode(SizeMode.MATCH_PARENT)

        val icon = FrameLayoutWidget()
        icon.layoutParams = RelativeLayoutWidget.LayoutParams()
            .size(32f, 32f)
            .margin(0f, 2f)
        infoArea.addChild("icon", icon)

        val frame = ImageWidget(Resource.Textures.ICON_BOX)
        frame.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)
        icon.addChild("frame", frame)

        val ability = ImageWidget(AcademyCraft.academy("textures/ability/accelerator/icon.png"))
        ability.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(16f, 16f)
            .gravity(Gravity.CENTER)
        icon.addChild("ability", ability)

        val info = LinearLayoutWidget()
        info.orientation = Orientation.VERTICAL
        info.layoutParams = RelativeLayoutWidget.LayoutParams()
            .addRule(RelativeLayoutWidget.RIGHT_OF, icon)
            .addRule(RelativeLayoutWidget.ALIGN_TOP, icon)
            .addRule(RelativeLayoutWidget.ALIGN_BOTTOM, icon)
            .margin(8f, 0f, 0f, 0f)
            .sizeMode(SizeMode.WRAP_CONTENT, SizeMode.MATCH_PARENT)
        infoArea.addChild("info", info)

        val abilityName = LabelWidget("Accelerator")
        abilityName.layoutParams = LinearLayoutWidget.LayoutParams()
            .weight(0.5f)
            .gravity(Gravity.CENTER_LEFT)
        info.addChild("ability_name", abilityName)

        val levelInfo = LinearLayoutWidget()
        levelInfo.orientation = Orientation.HORIZONTAL
        levelInfo.layoutParams = LinearLayoutWidget.LayoutParams()
            .weight(0.5f)
            .widthMode(SizeMode.MATCH_PARENT)
        info.addChild("level_info", levelInfo)

        val lv = LabelWidget("LV 5")
        lv.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.CENTER_LEFT)
        levelInfo.addChild("lv", lv)

        return infoArea
    }

    private fun createRightContent(anim: ObjectAnimator, finalAnim: ObjectAnimator): FrameLayoutWidget {
        val rightContent = FrameLayoutWidget()
        rightContent.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.END)
            .width(PANEL_MAIN_WIDTH / 4 * 3)
            .heightMode(SizeMode.MATCH_PARENT)
            .padding(5f, 8f, 10f, 20f)

        val leftLine = ImageWidget(Resource.Textures.ELEMENT_LINE)
        leftLine.layoutParams = WidgetContainer.LayoutParams()
            .heightMode(SizeMode.MATCH_PARENT)
            .gravity(Gravity.LEFT)
            .width(4f)
            .margin(-1.5f, 0f, 0f, 0f)
            .padding(0f, 1.5f, 0f, 1.8f)
        leftLine.rotateUv()
        rightContent.addChild("left_line", leftLine)

        val rightLine = ImageWidget(Resource.Textures.ELEMENT_LINE)
        rightLine.layoutParams = WidgetContainer.LayoutParams()
            .heightMode(SizeMode.MATCH_PARENT)
            .gravity(Gravity.RIGHT)
            .width(4f)
            .margin(0f, 0f, -2f, 0f)
            .padding(0f, 1.5f, 0f, 1.8f)
        rightLine.rotateUv()
        rightContent.addChild("right_line", rightLine)

        val topLine = ImageWidget(Resource.Textures.ELEMENT_LINE)
        topLine.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.TOP)
            .widthMode(SizeMode.MATCH_PARENT)
            .height(4f)
        rightContent.addChild("top_line", topLine)

        val bottomLine = ImageWidget(Resource.Textures.ELEMENT_LINE)
        bottomLine.layoutParams = WidgetContainer.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .gravity(Gravity.BOTTOM)
            .height(4f)
        rightContent.addChild("bottom_line", bottomLine)

        val terminalArea = createTerminalArea(anim, finalAnim)
        rightContent.addChild("terminal_area", terminalArea)

        val skillArea = createSkillArea()
        rightContent.addChild("skill_area", skillArea)

        return rightContent
    }

    private fun createTerminalArea(anim: ObjectAnimator, finalAnim: ObjectAnimator): FrameLayoutWidget {
        val terminalArea = FrameLayoutWidget()
        terminalArea.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.BOTTOM)
            .sizeMode(SizeMode.MATCH_PARENT)
            .padding(8f)

        val scrollPanel = ScrollPanelWidget()
        scrollPanel.layoutParams = WidgetContainer.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)
        terminalArea.addChild("scroll_panel", scrollPanel)

        val outputs = object :LinearLayoutWidget(){
            override fun onLayout() {
                super.onLayout()
                scrollPanel.scrollToEnd()
            }
        }
        outputs.layoutParams = LinearLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)
        outputs.spacing = 4f
        scrollPanel.addChild("outputs", outputs)

        createBootSequence(outputs, anim, finalAnim)

        return terminalArea
    }

    private fun createBootSequence(
        outputs: LinearLayoutWidget,
        anim: ObjectAnimator,
        finalAnim: ObjectAnimator
    ) {
        fun addOutput(output: String, onEnd: () -> Unit) {
            val label = object : LabelWidget(output) {
                var progress = 0f
                override fun generateDrawCommands(
                    text: String,
                    fontSize: Float,
                    thickness: Float,
                    red: Float,
                    green: Float,
                    blue: Float,
                    alpha: Float
                ): MutableList<GlyphDrawCommand> {
                    val list = super.generateDrawCommands(text, fontSize, thickness, red, green, blue, alpha)
                    return list.subList(0, (list.size * progress).toInt())
                }
            }
            label.layoutParams = WidgetContainer.LayoutParams()
                .gravity(Gravity.BOTTOM_LEFT)
            outputs.addChild("label_${output}_${RandomStringUtils.insecure().nextAlphabetic(4)}", label)

            fun start() {
                label.startAnimation(
                    ObjectAnimator.ofFloat({ label.progress = it; label.invalidate() }, 0f, 1f)
                        .setDuration(output.length * 25L)
                        .addListener(object : AnimatorListener {
                            override fun onAnimationEnd(animation: Animator) {
                                onEnd()
                            }
                        })
                )
            }

            if (!anim.isRunning && !finalAnim.isRunning) {
                start()
            } else {
                finalAnim.addListener(object : AnimatorListener {
                    override fun onAnimationEnd(animation: Animator) {
                        start()
                    }
                })
            }
        }

        val welcomeString = "Welcome to Academy OS, Ver 0.0.1"
        addOutput(welcomeString) {
            addOutput("Copyright (c) Academy Tech. All rights reserved.") {
                val playerName = Minecraft.getInstance().player?.name?.string ?: "Unknown"
                addOutput("User $playerName detected, System booting......") {
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
                            addOutput("FATAL: User's ability category is invalid, booting aborted.") {
                                addOutput("Type `learn` to acquire new category.") {
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

    private fun createCommandInputArea(
        outputs: LinearLayoutWidget
    ): LinearLayoutWidget {
        val inputArea = LinearLayoutWidget()
        inputArea.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.BOTTOM_LEFT)
            .height(8f)
            .widthMode(SizeMode.MATCH_PARENT)
        inputArea.orientation = Orientation.HORIZONTAL

        val label = LabelWidget("OS >")
        label.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.BOTTOM_LEFT)
        inputArea.addChild("label", label)

        val textBox = TextBoxWidget(8)
        textBox.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.BOTTOM_LEFT)
            .sizeMode(SizeMode.MATCH_PARENT)
        textBox.background = null
        textBox.setWhenEnter { input ->
            outputs.removeChild("input_area")
            if (input == "learn") {
                MisakaNetworkClient.FUTURE_MANAGER.send(AcquireCategoryPacket(mainPos)) {
                    if (it === null) {
                        addOutputLine(outputs, "Unknown error")
                    } else {
                        for (string in it.messages) {
                            addOutputLine(outputs, string)
                        }
                    }

                    val newInputArea = createCommandInputArea(outputs)
                    outputs.addChild("input_area", newInputArea)
                }
            } else {
                addOutputLine(outputs, "Invalid command.")

                val newInputArea = createCommandInputArea(outputs)
                outputs.addChild("input_area", newInputArea)
            }
        }
        inputArea.addChild("text_box", textBox)
        inputArea.focusedChild = textBox

        return inputArea
    }

    private fun addOutputLine(outputs: LinearLayoutWidget, text: String) {
        val label = LabelWidget(text)
        label.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.BOTTOM_LEFT)
        outputs.addChild("output_${RandomStringUtils.insecure().nextAlphabetic(8)}", label)
    }

    private fun createSkillArea(): FrameLayoutWidget {
        val skillArea = FrameLayoutWidget()
        skillArea.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.BOTTOM)
            .sizeMode(SizeMode.MATCH_PARENT)
            .padding(1f)
        skillArea.visibility = Widget.Visibility.INVISIBLE

        val back = ParallaxImageWidget(Resource.Textures.UI_DEVELOPER_SKILL_AREA_BG)
        back.layoutParams = WidgetContainer.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)
        back.setSampler(FilterMode.LINEAR, false)
        skillArea.addChild("back", back)

        return skillArea
    }

    private fun createLogo(): ImageWidget {
        val logo = ImageWidget(Resource.Textures.LOGO_TECH)
        logo.layoutParams = WidgetContainer.LayoutParams()
            .gravity(Gravity.BOTTOM_RIGHT)
            .size(66f, 18f)
            .margin(0f, 0f, 4f, 4f)
        return logo
    }

    companion object {
        const val PANEL_MAIN_WIDTH: Float = 400f
        const val PANEL_MAIN_HEIGHT: Float = 187f
    }
}
