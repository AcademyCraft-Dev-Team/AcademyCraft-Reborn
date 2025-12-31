package org.academy.internal.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraft;
import org.academy.api.client.Resource;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.animation.AnimatorListener;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.screen.UiScreen;
import org.academy.api.client.gui.widget.*;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jspecify.annotations.Nullable;

public final class AbilityDeveloperScreen extends UiScreen {
    public final BlockPos mainPos;
    @Nullable
    public AbilityDeveloperBlockEntity abilityDeveloperBlockEntity;
    public static final float PANEL_MAIN_WIDTH = 400;
    public static final float PANEL_MAIN_HEIGHT = 187;

    public AbilityDeveloperScreen(BlockPos mainPos) {
        super(Component.empty());
        this.mainPos = mainPos;
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(mainPos)
                instanceof AbilityDeveloperBlockEntity entity) {
            abilityDeveloperBlockEntity = entity;
        } else {
            onClose();
            return;
        }
        abilityDeveloperBlockEntity.setOpen(true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
        if (abilityDeveloperBlockEntity != null)
            abilityDeveloperBlockEntity.setOpen(false);
        NeoForge.EVENT_BUS.unregister(this);
    }

    @Override
    protected void onInit() {
        var duration = 500L;

        var main = new FrameLayoutWidget();
        main.setLayoutParams(
                new FrameLayoutWidget.LayoutParams()
                        .gravity(Gravity.CENTER)
                        .size(PANEL_MAIN_WIDTH, PANEL_MAIN_HEIGHT)
        );
        root.addChild("main", main);
        main.startAnimation(
                ObjectAnimator.ofFloat(
                        aFloat ->
                                main.setLayoutParams(
                                        main.getLayoutParams()
                                                .padding(aFloat, PANEL_MAIN_HEIGHT / 2)
                                ),
                        PANEL_MAIN_WIDTH / 2, 0
                ).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_EXPO)
        );
        {
            var back = new BlendQuadWidget();
            back.setLayoutParams(
                    new FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
            );
            back.setAlpha(0.5f);
            main.addChild("back", back);

            var content = new FrameLayoutWidget();
            content.setLayoutParams(
                    new FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
            );
            var anim = ObjectAnimator.ofFloat(
                    aFloat ->
                            main.setLayoutParams(
                                    main.getLayoutParams()
                                            .padding(0, aFloat)
                            ),
                    PANEL_MAIN_HEIGHT / 2, 0
            ).setDuration(duration).setStartDelay(duration).setInterpolator(EasingFunctions.EASE_OUT_EXPO);
            anim.addListener(new AnimatorListener() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    main.addChild("content", content);
                    content.startAnimation(ObjectAnimator.ofFloat(content::setAlpha, 0f, 1f).setDuration(duration));
                }
            });
            main.startAnimation(anim);
            {
                var leftContent = new LinearLayoutWidget();
                leftContent.setLayoutParams(
                        new FrameLayoutWidget.LayoutParams()
                                .sizeMode(SizeMode.MATCH_PARENT)
                                .padding(16, 8, 256, 8)
                );
                content.addChild("left_content", leftContent);
                {
                    var playerInfoContent = new LinearLayoutWidget();
                    playerInfoContent.setLayoutParams(
                            new LinearLayoutWidget.LayoutParams()
                                    .heightMode(SizeMode.WRAP_CONTENT)
                                    .widthMode(SizeMode.MATCH_PARENT)
                    );
                    leftContent.addChild("player_info_content", playerInfoContent);
                    {
                        var topLine = new ImageWidget(Resource.Textures.ELEMENT_LINE);
                        topLine.setLayoutParams(
                                new LinearLayoutWidget.LayoutParams()
                                        .widthMode(SizeMode.MATCH_PARENT)
                                        .height(4)
                        );
                        playerInfoContent.addChild("top_line", topLine);

                        var infoArea = new RelativeLayoutWidget();
                        infoArea.setLayoutParams(
                                new LinearLayoutWidget.LayoutParams()
                                        .heightMode(SizeMode.WRAP_CONTENT)
                                        .widthMode(SizeMode.MATCH_PARENT)
                        );
                        playerInfoContent.addChild("info_area", infoArea);
                        {
                            var icon = new FrameLayoutWidget();
                            icon.setLayoutParams(
                                    new RelativeLayoutWidget.LayoutParams()
                                            .size(32, 32)
                                            .margin(0, 2)
                            );
                            infoArea.addChild("icon", icon);
                            {
                                var frame = new ImageWidget(Resource.Textures.ICON_BOX);
                                frame.setLayoutParams(
                                        new FrameLayoutWidget.LayoutParams()
                                                .sizeMode(SizeMode.MATCH_PARENT)
                                );
                                icon.addChild("frame", frame);

                                var ability = new ImageWidget(AcademyCraft.academy("textures/ability/accelerator/icon.png"));
                                ability.setLayoutParams(
                                        new FrameLayoutWidget.LayoutParams()
                                                .size(16, 16)
                                                .gravity(Gravity.CENTER)
                                );
                                icon.addChild("ability", ability);
                            }

                            var info = new LinearLayoutWidget();
                            info.setOrientation(Orientation.VERTICAL);
                            info.setLayoutParams(
                                    new RelativeLayoutWidget.LayoutParams()
                                            .addRule(RelativeLayoutWidget.RIGHT_OF, icon)
                                            .addRule(RelativeLayoutWidget.ALIGN_TOP, icon)
                                            .addRule(RelativeLayoutWidget.ALIGN_BOTTOM, icon)
                                            .margin(8, 0, 0, 0)
                                            .sizeMode(SizeMode.WRAP_CONTENT, SizeMode.MATCH_PARENT)
                            );
                            infoArea.addChild("info", info);
                            {
                                var abilityName = new LabelWidget("Accelerator");
                                abilityName.setLayoutParams(
                                        new LinearLayoutWidget.LayoutParams()
                                                .weight(0.5f)
                                                .gravity(Gravity.CENTER_LEFT)
                                );
                                info.addChild("ability_name", abilityName);

                                var levelInfo = new LinearLayoutWidget();
                                levelInfo.setOrientation(Orientation.HORIZONTAL);
                                levelInfo.setLayoutParams(
                                        new LinearLayoutWidget.LayoutParams()
                                                .weight(0.5f)
                                                .widthMode(SizeMode.MATCH_PARENT)
                                );
                                info.addChild("level_info", levelInfo);
                                {
                                    var lv = new LabelWidget("LV 5");
                                    lv.setLayoutParams(
                                            new LinearLayoutWidget.LayoutParams()
                                                    .gravity(Gravity.CENTER_LEFT)
                                    );
                                    levelInfo.addChild("lv", lv);
                                }
                            }
                        }

                        var bottomLine = new ImageWidget(Resource.Textures.ELEMENT_LINE);
                        bottomLine.setLayoutParams(
                                new LinearLayoutWidget.LayoutParams()
                                        .widthMode(SizeMode.MATCH_PARENT)
                                        .height(4)
                        );
                        playerInfoContent.addChild("bottom_line", bottomLine);
                    }

                    var skillInfoContent = new LinearLayoutWidget();
                    skillInfoContent.setLayoutParams(
                            new LinearLayoutWidget.LayoutParams()
                                    .widthMode(SizeMode.MATCH_PARENT)
                    );
                    leftContent.addChild("skill_info_content", skillInfoContent);
                }

                var logo = new ImageWidget(Resource.Textures.LOGO_TECH);
                logo.setLayoutParams(
                        new FrameLayoutWidget.LayoutParams()
                                .gravity(Gravity.BOTTOM_RIGHT)
                                .size(88, 24)
                                .margin(0, 0, 4, 4)
                );
                content.addChild("logo", logo);
            }
        }
    }
}