package org.academy.internal.client.gui.screen

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
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.widget.*
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity

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

    override fun isPauseScreen(): Boolean {
        return false
    }

    override fun onClose() {
        super.onClose()
        if (abilityDeveloperBlockEntity != null) abilityDeveloperBlockEntity!!.setOpen(false)
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
                {
                    main.layoutParams = main.layoutParams
                        .padding(it, PANEL_MAIN_HEIGHT / 2)
                },
                PANEL_MAIN_WIDTH / 2, 0f
            ).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_EXPO)
        )
        run {
            val back = BlendQuadWidget()
            back.layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)

            back.alpha = 0.5f
            main.addChild("back", back)

            val content = FrameLayoutWidget()
            content.layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)

            val anim = ObjectAnimator.ofFloat(
                {
                    main.layoutParams = main.layoutParams
                        .padding(0f, it)
                },
                PANEL_MAIN_HEIGHT / 2, 0f
            ).setDuration(duration).setStartDelay(duration).setInterpolator(EasingFunctions.EASE_OUT_EXPO)
            anim.addListener(object : AnimatorListener {
                override fun onAnimationEnd(animation: Animator) {
                    main.addChild("content", content)
                    content.startAnimation(ObjectAnimator.ofFloat({ content.alpha = it }, 0f, 1f).setDuration(duration))
                }
            })
            main.startAnimation(anim)
            run {
                val leftContent = LinearLayoutWidget()
                leftContent.layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                    .padding(16f, 8f, 256f, 8f)

                content.addChild("left_content", leftContent)
                run {
                    val playerInfoContent = LinearLayoutWidget()
                    playerInfoContent.layoutParams = LinearLayoutWidget.LayoutParams()
                        .heightMode(SizeMode.WRAP_CONTENT)
                        .widthMode(SizeMode.MATCH_PARENT)

                    leftContent.addChild("player_info_content", playerInfoContent)
                    run {
                        val topLine = ImageWidget(Resource.Textures.ELEMENT_LINE)
                        topLine.layoutParams = LinearLayoutWidget.LayoutParams()
                            .widthMode(SizeMode.MATCH_PARENT)
                            .height(4f)

                        playerInfoContent.addChild("top_line", topLine)

                        val infoArea = RelativeLayoutWidget()
                        infoArea.layoutParams = LinearLayoutWidget.LayoutParams()
                            .heightMode(SizeMode.WRAP_CONTENT)
                            .widthMode(SizeMode.MATCH_PARENT)

                        playerInfoContent.addChild("info_area", infoArea)
                        run {
                            val icon = FrameLayoutWidget()
                            icon.layoutParams = RelativeLayoutWidget.LayoutParams()
                                .size(32f, 32f)
                                .margin(0f, 2f)

                            infoArea.addChild("icon", icon)
                            run {
                                val frame = ImageWidget(Resource.Textures.ICON_BOX)
                                frame.layoutParams = FrameLayoutWidget.LayoutParams()
                                    .sizeMode(SizeMode.MATCH_PARENT)

                                icon.addChild("frame", frame)

                                val ability = ImageWidget(AcademyCraft.academy("textures/ability/accelerator/icon.png"))
                                ability.layoutParams = FrameLayoutWidget.LayoutParams()
                                    .size(16f, 16f)
                                    .gravity(Gravity.CENTER)
                                icon.addChild("ability", ability)
                            }

                            val info = LinearLayoutWidget()
                            info.setOrientation(Orientation.VERTICAL)
                            info.layoutParams = RelativeLayoutWidget.LayoutParams()
                                .addRule(RelativeLayoutWidget.RIGHT_OF, icon)
                                .addRule(RelativeLayoutWidget.ALIGN_TOP, icon)
                                .addRule(RelativeLayoutWidget.ALIGN_BOTTOM, icon)
                                .margin(8f, 0f, 0f, 0f)
                                .sizeMode(SizeMode.WRAP_CONTENT, SizeMode.MATCH_PARENT)

                            infoArea.addChild("info", info)
                            run {
                                val abilityName = LabelWidget("Accelerator")
                                abilityName.layoutParams = LinearLayoutWidget.LayoutParams()
                                    .weight(0.5f)
                                    .gravity(Gravity.CENTER_LEFT)

                                info.addChild("ability_name", abilityName)

                                val levelInfo = LinearLayoutWidget()
                                levelInfo.setOrientation(Orientation.HORIZONTAL)
                                levelInfo.layoutParams = LinearLayoutWidget.LayoutParams()
                                    .weight(0.5f)
                                    .widthMode(SizeMode.MATCH_PARENT)

                                info.addChild("level_info", levelInfo)
                                run {
                                    val lv = LabelWidget("LV 5")
                                    lv.layoutParams = LinearLayoutWidget.LayoutParams()
                                        .gravity(Gravity.CENTER_LEFT)
                                    levelInfo.addChild("lv", lv)
                                }
                            }
                        }

                        val bottomLine = ImageWidget(Resource.Textures.ELEMENT_LINE)
                        bottomLine.layoutParams = LinearLayoutWidget.LayoutParams()
                            .widthMode(SizeMode.MATCH_PARENT)
                            .height(4f)
                        playerInfoContent.addChild("bottom_line", bottomLine)
                    }

                    val skillInfoContent = LinearLayoutWidget()
                    skillInfoContent.layoutParams = LinearLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                    leftContent.addChild("skill_info_content", skillInfoContent)
                }

                val logo = ImageWidget(Resource.Textures.LOGO_TECH)
                logo.layoutParams = FrameLayoutWidget.LayoutParams()
                    .gravity(Gravity.BOTTOM_RIGHT)
                    .size(88f, 24f)
                    .margin(0f, 0f, 4f, 4f)
                content.addChild("logo", logo)
            }
        }
    }

    companion object {
        const val PANEL_MAIN_WIDTH: Float = 400f
        const val PANEL_MAIN_HEIGHT: Float = 187f
    }
}