package org.academy.internal.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
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
import org.academy.api.client.gui.screen.UIScreen;
import org.academy.api.client.gui.util.WirelessPanelUtil;
import org.academy.api.client.gui.widget.*;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class AbilityDeveloperScreen extends UIScreen {
    public final BlockPos mainPos;
    @Nullable
    public AbilityDeveloperBlockEntity abilityDeveloperBlockEntity;
    public static final float PANEL_MAIN_WIDTH = 400;
    public static final float PANEL_MAIN_HEIGHT = 187;
    public static final float PANEL_LEFT_WIDTH = 108.5f;
    public static final float PANEL_RIGHT_WIDTH = 278;
    public static final float PANEL_RIGHT_SKILL_BACK_X = 11;
    public static final float PANEL_RIGHT_SKILL_BACK_Y = 17.5f;
    public static final float PANEL_RIGHT_SKILL_BACK_WIDTH = 256;
    public static final float PANEL_RIGHT_SKILL_BACK_HEIGHT = 139.5f;
    public static final float PANEL_RIGHT_SKILL_SIZE = 24f;
    public static final Function<AbilityCategory, ResourceLocation> ABILITY_ICON = abilityCategory ->
            ResourceLocation.fromNamespaceAndPath(abilityCategory.getKey().getNamespace(),
                    "textures/ability/" + abilityCategory.getKey().getPath() + "/icon_glow.png"
            );
/*    private PanelWidget screenWirelessPanel = new PanelWidget(0, 0, 0, 0);
    private final SkillInfoPanel skillInfoPanel = new SkillInfoPanel();*/

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

   /* @Override
    protected void onInit() {
        NeoForge.EVENT_BUS.register(this);
        final float startYOffset = 20f;
        final long duration = 300L;
        final long delay = 150L;
        final long childDuration = duration - 100;

        final float mainPanelX = width / 2f - PANEL_MAIN_WIDTH / 2f;
        final float mainPanelY = height / 2f - PANEL_MAIN_HEIGHT / 2f;

        var leftPanel = getLeftPanel();
        leftPanel.setX(mainPanelX);
        leftPanel.setY(mainPanelY + startYOffset);
        leftPanel.setAlpha(0f);
        rootContainer.addChild("panel_left", leftPanel);

        var rightPanel = new PanelWidget(mainPanelX + PANEL_LEFT_WIDTH, mainPanelY + startYOffset, PANEL_RIGHT_WIDTH, PANEL_MAIN_HEIGHT);
        rightPanel.setAlpha(0f);
        rootContainer.addChild("panel_right", rightPanel);

        var rightPanelBack = new ImageWidget(0, 0, PANEL_RIGHT_WIDTH, PANEL_MAIN_HEIGHT, Resource.Textures.PANEL_RIGHT_BACK);
        rightPanel.addChild("panel_right_back", rightPanelBack);
        var rightPanelInfo = new ImageWidget(0, 0, PANEL_RIGHT_WIDTH, PANEL_MAIN_HEIGHT, Resource.Textures.UI_DEVELOPER_PANEL_RIGHT);
        rightPanelInfo.setZ(1);
        rightPanel.addChild("panel_right_info", rightPanelInfo);

        var bootFailed = AbilitySystemClient.getCategory() == AbilityCategories.LEVEL0.get();

        var outputList = new ScrollPanelWidget(
                PANEL_RIGHT_SKILL_BACK_X + 5, PANEL_RIGHT_SKILL_BACK_Y + 5, PANEL_RIGHT_WIDTH - 32, 132);
        outputList.setEnabled(bootFailed);
        outputList.setVisible(bootFailed);
        outputList.setZ(1);
        rightPanel.addChild("output_list", outputList);
        var welcome = setupTerminal(outputList);

        setupSkillArea(rightPanel, !bootFailed);

        playAnimation(ObjectAnimator.ofFloat(leftPanel::setY, leftPanel.getY(), mainPanelY).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_CUBIC).setStartDelay(delay));
        playAnimation(ObjectAnimator.ofFloat(leftPanel::setAlpha, 0f, 1f).setDuration(childDuration).setInterpolator(EasingFunctions.LINEAR).setStartDelay(delay));

        var rightPanelAnimation = ObjectAnimator.ofFloat(rightPanel::setY, rightPanel.getY(), mainPanelY).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_CUBIC).setStartDelay(delay);
        playAnimation(rightPanelAnimation);
        playAnimation(ObjectAnimator.ofFloat(rightPanel::setAlpha, 0f, 1f).setDuration(childDuration).setInterpolator(EasingFunctions.LINEAR).setStartDelay(delay));

        playAnimation(ObjectAnimator.ofFloat(skillInfoPanel::setAlpha, 0f, 1f).setDuration(childDuration).setInterpolator(EasingFunctions.LINEAR));
        rightPanelAnimation.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                welcome.startAnimation();
            }
        });

        setupWirelessPanel();
        setupSkillInfoPanel();
    }
*/

    @Override
    protected void onInit() {
        var duration = 500L;

        var main = new FrameLayoutWidget();
        main.setLayoutParams(
                new FrameLayoutWidget.LayoutParams()
                        .gravity(Gravity.CENTER)
                        .size(PANEL_MAIN_WIDTH, PANEL_MAIN_HEIGHT)
        );
        rootContainer.addChild("main", main);
        playAnimation(
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
            back.setLayoutParams(
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
                    playAnimation(ObjectAnimator.ofFloat(content::setAlpha, 0f, 1f).setDuration(duration));
                }
            });
            playAnimation(anim);
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

    @SubscribeEvent
    public void onConnectionStatusChanged(WirelessPanelUtil.ConnectionStatusChangedEvent event) {
    }
/*
    private TypewriterLabelWidget setupTerminal(ScrollPanelWidget outputList) {
        var welcome = new TypewriterLabelWidget("Welcome to Academy OS, Ver 0.0.1", 0, 0);
        addOutput("welcome", welcome, outputList);
        var userName = "None";
        if (minecraft != null && minecraft.player != null)
            userName = minecraft.player.getName().getString();

        var copyright = new TypewriterLabelWidget("Copyright (C) 2025 AcademyCraft Dev Team - GPL v3", welcome.getX(), welcome.getY() + welcome.getHeight());
        addOutput("copyright", copyright, outputList);
        var userDetected = new TypewriterLabelWidget(String.format("User %s detected, System booting...", userName), copyright.getX(), copyright.getY() + copyright.getHeight());
        addOutput("user_detected", userDetected, outputList);
        var loadingBar = new BracketProgressBarWidget('#', 50);
        addOutput("loading_bar", loadingBar, outputList);
        var invalid = new TypewriterLabelWidget("FATAL: User's ability category is invalid, booting aborted.", loadingBar.getX(), loadingBar.getY() + loadingBar.getHeight());
        addOutput("invalid", invalid, outputList);
        var hint = new TypewriterLabelWidget("Type 'learn' to acquire new category.", invalid.getX(), invalid.getY() + invalid.getHeight());
        addOutput("hint", hint, outputList);
        welcome.setOnAnimationFinished(copyright::startAnimation);
        copyright.setOnAnimationFinished(userDetected::startAnimation);
        userDetected.setOnAnimationFinished(loadingBar::startAnimation);
        loadingBar.setOnAnimationFinished(invalid::startAnimation);
        invalid.setOnAnimationFinished(hint::startAnimation);
        hint.setOnAnimationFinished(() -> {
            var os = new LabelWidget("OS >", hint.getX(), hint.getY() + hint.getHeight());
            os.setAlignment(LabelWidget.Alignment.CENTER);
            os.setVerticalAlignment(LabelWidget.VerticalAlignment.MIDDLE);
            addOutput("os", os, outputList);

            var textBox = new TextBoxWidget(32, os.getX() + os.getWidth(), os.getY(), PANEL_RIGHT_WIDTH - 24, hint.getHeight());

            textBox.setWhenEnter(s -> {
                var learned = AbilitySystemClient.getCategory() != AbilityCategories.LEVEL0.get();
                var outputCommand = new LabelWidget("OS >" + s, 0, textBox.getY());
                addOSOutput("output_command_" + s + outputCommand.hashCode(), os, textBox, outputList);
                final var outputStartY = outputCommand.getY() + outputCommand.getHeight();
                String singleLineOutput;

                if ("learn".equals(s)) {
                    if (!learned) {
                        if (abilityDeveloperBlockEntity != null && abilityDeveloperBlockEntity.getEnergyStored() >= 10_000) {
                            var request = new AcquireCategoryPacket(mainPos.asLong());
                            MisakaNetworkClient.FUTURE_MANAGER.sendRequestToServer(request,
                                    response -> {
                                        if (response != null) {
                                            var lastWidget = outputCommand;
                                            for (var string : response.getMessages()) {
                                                var newOutput = new LabelWidget(string, 0, lastWidget.getY() + lastWidget.getHeight());
                                                addOutput("output_info_" + string + newOutput.hashCode(), newOutput, outputList);
                                                os.setY(newOutput.getY() + newOutput.getHeight());
                                                textBox.setY(newOutput.getY() + newOutput.getHeight());
                                                outputList.scrollToEnd();
                                                lastWidget = newOutput;
                                            }
                                        }
                                    });
                            return;
                        } else {
                            singleLineOutput = "Insufficient energyCost available.";
                        }
                    } else {
                        singleLineOutput = "You are learned,you can't learn again.";
                    }
                } else if ("exit".equals(s)) {
                    onClose();
                    return;
                } else {
                    singleLineOutput = "Invalid command.";
                }

                var outputInfo = new LabelWidget(singleLineOutput, 0, outputStartY);
                addOutput("output_info_" + singleLineOutput + outputInfo.hashCode(), outputInfo, outputList);
                os.setY(outputInfo.getY() + outputInfo.getHeight());
                textBox.setY(outputInfo.getY() + outputInfo.getHeight());
                outputList.scrollToEnd();
            });

            outputList.addChild("text_box", textBox);
            outputList.setFocusedChild(textBox);
        });
        return welcome;
    }

    private void setupSkillArea(PanelWidget parent, boolean visible) {
        var parallaxImageWidget = new ParallaxImageWidget(PANEL_RIGHT_SKILL_BACK_X, PANEL_RIGHT_SKILL_BACK_Y, PANEL_RIGHT_SKILL_BACK_WIDTH, PANEL_RIGHT_SKILL_BACK_HEIGHT, Resource.Textures.UI_DEVELOPER_SKILL_AREA_BG);
        parallaxImageWidget.setVisible(visible);
        parallaxImageWidget.setEnabled(visible);
        parent.addChild("skill_area_back", parallaxImageWidget);

        var abilityCategory = AbilitySystemClient.getCategory();
        var skillInfos = AbilitySystemClient.getSkillInfos().get(abilityCategory);
        if (skillInfos != null) {
            var skillWidgetMap = new HashMap<ResourceLocation, SkillWidget>();

            for (var skillInfo : skillInfos) {
                var skillWidget = new SkillWidget(skillInfo);
                skillWidget.setVisible(visible);
                skillWidget.setEnabled(visible);
                skillWidget.setZ(1);
                parent.addChild(skillInfo.skill().getKey().toString(), skillWidget);
                skillWidgetMap.put(skillInfo.skill().getKey(), skillWidget);
            }

            for (var skillWidget : skillWidgetMap.values()) {
                skillWidget.resolveDependencies(skillWidgetMap);
            }
        }
    }

    private void setupWirelessPanel() {
        screenWirelessPanel = new PanelWidget(0, 0, width, height) {
            @Override
            protected void onMousePressed(MouseEvent event) {
                setVisible(false);
                setEnabled(false);
                event.consume();
            }

            @Override
            public boolean isClickable() {
                return true;
            }
        };
        screenWirelessPanel.setVisible(false);
        screenWirelessPanel.setEnabled(false);
        rootContainer.addChild("panel_screen_wireless", screenWirelessPanel);

        var backgroundWidget = new FillWidget(0, 0, width, height, 0xFF000000);
        backgroundWidget.setAlpha(0.75f);
        screenWirelessPanel.addChild("screen_back", backgroundWidget);

        var localWirelessPanel = WirelessPanelUtil.create((width - WirelessPanelUtil.PANEL_WIDTH) / 2, (height - WirelessPanelUtil.PANEL_HEIGHT) / 2, mainPos, true);
        localWirelessPanel.setZ(10);
        screenWirelessPanel.addChild("panel_wireless", localWirelessPanel);

        screenWirelessPanel.setZ(10);
    }

    private void setupSkillInfoPanel() {
        skillInfoPanel.setEnabled(false);
        skillInfoPanel.setVisible(false);
        skillInfoPanel.setZ(100);
        rootContainer.addChild("panel_skill_info", skillInfoPanel);
    }

    private static void addOutput(String name, LabelWidget labelWidget, AbstractContainerWidget abstractContainerWidget) {
        labelWidget.setScale(0.75f);
        abstractContainerWidget.addChild(name, labelWidget);
    }

    private static void addOSOutput(String name, LabelWidget osLabel, TextBoxWidget textBoxWidget, AbstractContainerWidget abstractContainerWidget) {
        var os = new LabelWidget(osLabel.getText(), osLabel.getX(), osLabel.getY() + 1);
        os.setAlignment(osLabel.getAlignment());
        osLabel.setVerticalAlignment(osLabel.getVerticalAlignment());
        os.setScale(osLabel.getScale());

        var text = new LabelWidget(textBoxWidget.getText(), textBoxWidget.getX(), textBoxWidget.getY());
        text.setAlignment(textBoxWidget.getAlignment());
        osLabel.setVerticalAlignment(textBoxWidget.getVerticalAlignment());

        abstractContainerWidget.addChild(name + "_os", os);
        abstractContainerWidget.addChild(name, text);
    }

    private PanelWidget getLeftPanel() {
        var localLeftPanel = new PanelWidget(0, 0, PANEL_LEFT_WIDTH, PANEL_MAIN_HEIGHT);
        var abilityCategory = AbilitySystemClient.getCategory();
        {
            var leftPanelBack = new BlendQuadWidget(0, 70, PANEL_LEFT_WIDTH, 115);
            leftPanelBack.setAlpha(0.5f);
            localLeftPanel.addChild("left_panel_back", leftPanelBack);
            var leftPanelBackBottom = new ImageWidget(4.25f, 0, 100, PANEL_MAIN_HEIGHT, Resource.Textures.PANEL_LEFT_BACK_MIDDLE);
            leftPanelBackBottom.setZ(1);
            localLeftPanel.addChild("left_panel_back_bottom", leftPanelBackBottom);
            var leftPanelBackTop = new ImageWidget(0, 0, PANEL_LEFT_WIDTH, PANEL_MAIN_HEIGHT, Resource.Textures.UI_DEVELOPER_PANEL_LEFT);
            leftPanelBackTop.setZ(1);
            localLeftPanel.addChild("left_panel_back_top", leftPanelBackTop);
            var leftPanelInfoPanel = new PanelWidget(0, 70, PANEL_LEFT_WIDTH, 32);
            leftPanelInfoPanel.setZ(1);
            localLeftPanel.addChild("left_panel_info", leftPanelInfoPanel);
            {
                var iconBack = new ImageWidget(0, 0, leftPanelInfoPanel.getHeight(), leftPanelInfoPanel.getHeight(), Resource.Textures.ICON_BOX);
                leftPanelInfoPanel.addChild("icon_back", iconBack);

                var icon = new ImageWidget(0, 0, iconBack.getHeight(), iconBack.getHeight(),
                        ABILITY_ICON.apply(abilityCategory));
                icon.setWidthScale(0.65f);
                icon.setHeightScale(0.65f);
                leftPanelInfoPanel.addChild("icon", icon);
                var name = new AutoScaleLabelWidget(abilityCategory.getDescriptionId(), leftPanelInfoPanel.getHeight(), 5,
                        leftPanelInfoPanel.getWidth() - leftPanelInfoPanel.getHeight() - 4);
                leftPanelInfoPanel.addChild("name", name);

                var learned = AbilitySystemClient.LEARNED_SKILLS.size();
                var all = abilityCategory.getSkills().size();
                var progressRatio = (all == 0) ? 1.0f : (float) learned / all;
                var percentage = (int) (progressRatio * 100);

                var learnProgress = new ProgressBarWidget(leftPanelInfoPanel.getHeight(), 16,
                        leftPanelInfoPanel.getWidth() - leftPanelInfoPanel.getHeight() - 4, 2,
                        () -> progressRatio);
                learnProgress.setProgressBarColor(Color.WHITE.hashCode());
                leftPanelInfoPanel.addChild("learn_progress", learnProgress);
                var progress = new AutoScaleLabelWidget("LEARNED " + percentage + "%", leftPanelInfoPanel.getHeight(), 18,
                        learnProgress.getWidth() * 0.5f);
                leftPanelInfoPanel.addChild("progress", progress);
                var levelLabel = new AutoScaleLabelWidget(abilityCategory.getDescriptionId(), progress.getX() + learnProgress.getWidth() * 0.5f, 18, learnProgress.getWidth() * 0.5f - 4);
                levelLabel.setX(leftPanelInfoPanel.getWidth() - levelLabel.getWidth() - 4);
                levelLabel.setColor(0xFF1177D6);
                leftPanelInfoPanel.addChild("label_level", levelLabel);
            }
            var wirelessLabel = new AutoScaleLabelWidget("Current Node:", 8, 110, 90);
            wirelessLabel.setZ(1);
            localLeftPanel.addChild("label_wireless", wirelessLabel);
            var wirelessButtonPanel = new PanelWidget(8, 120, 90, 16);
            wirelessButtonPanel.setZ(1);
            localLeftPanel.addChild("button_wireless", wirelessButtonPanel);
            {
                var button = new ImageButtonWidget(0, 0,
                        wirelessButtonPanel.getWidth(), wirelessButtonPanel.getHeight(),
                        (ResourceLocation) null, () -> {
                    screenWirelessPanel.setVisible(true);
                    screenWirelessPanel.setEnabled(true);
                });
                wirelessButtonPanel.addChild("button", button);

                var back = new ImageWidget(0, 0, 90, 14, Resource.Textures.UI_BACKGROUND_LIGHT);
                wirelessButtonPanel.addChild("back", back);
                var icon = new ImageWidget(6, 1, 12, 12, Resource.Textures.ICON_NODE);
                wirelessButtonPanel.addChild("icon", icon);
                var nameLabel = new AutoScaleLabelWidget("None", 24, 3,
                        wirelessButtonPanel.getWidth() - 24 - 4);
                wirelessNameLabel = nameLabel;
                wirelessButtonPanel.addChild("label_name", nameLabel);
            }
            var powerLabel = new AutoScaleLabelWidget("Current Power:", 8, 142.5f, 90);
            powerLabel.setZ(1);
            localLeftPanel.addChild("power_label", powerLabel);

            if (abilityDeveloperBlockEntity != null) {
                var progressBarWidget = new ProgressBarWidget(
                        9.25f,
                        157f,
                        90f,
                        9,
                        () -> (float) abilityDeveloperBlockEntity.getEnergyStored() / abilityDeveloperBlockEntity.getMaxEnergyStorage()
                );
                localLeftPanel.addChild("progress_bar", progressBarWidget);
            }
        }
        return localLeftPanel;
    }

    void openSkillViewPanel(AbilitySystemClient.SkillInfo skill) {
        skillInfoPanel.skillInfo = skill;

        skillInfoPanel.closeBack.setWidth(width);
        skillInfoPanel.closeBack.setHeight(height);

        skillInfoPanel.setWidth(width);
        skillInfoPanel.setHeight(height);
        skillInfoPanel.setEnabled(true);
        skillInfoPanel.setVisible(true);

        skillInfoPanel.icon.setX(width / 2.0f - skillInfoPanel.icon.getWidth() / 2.0f);
        skillInfoPanel.icon.setY((height / 2.0f - skillInfoPanel.icon.getHeight() / 2.0f) - 25.0f);
        skillInfoPanel.iconBack.setX(width / 2.0f - skillInfoPanel.iconBack.getWidth() / 2.0f);
        skillInfoPanel.iconBack.setY((height / 2.0f - skillInfoPanel.iconBack.getHeight() / 2.0f) - 25.0f);
        skillInfoPanel.icon.setTexture(skill.texture());

        skillInfoPanel.nameLabel.setText("Skill: %s".formatted(skill.skill().getTranslatedName()));
        skillInfoPanel.nameLabel.setX(width / 2.0f - skillInfoPanel.nameLabel.getWidth() / 2.0f);
        skillInfoPanel.nameLabel.setY(skillInfoPanel.icon.getY() + 50.0f);

        var lacked = ClientUtil.lacksSkill(skill.skill());
        skillInfoPanel.stateLabel.setText(lacked ? "Skill not learned" : "Skill learned");
        skillInfoPanel.stateLabel.setColor(lacked ? 0XFFFF0000 : 0xFFFFFFFF);
        skillInfoPanel.stateLabel.setX(width / 2.0f - skillInfoPanel.stateLabel.getWidth() / 2.0f);
        skillInfoPanel.stateLabel.setY(skillInfoPanel.nameLabel.getY() + 12.0f);

        skillInfoPanel.learnButton.setEnabled(lacked);
        skillInfoPanel.learnButton.setVisible(lacked);

        skillInfoPanel.depPanel.clearChildren();
        skillInfoPanel.depPanel.setY(skillInfoPanel.stateLabel.getY() + 10.0f);
        if (lacked) {
            var x = 0f;
            var labelWidget = new LabelWidget("Dep.", x, 3.5f);
            x += labelWidget.getWidth();
            skillInfoPanel.depPanel.addChild("label_name", labelWidget);
            if (skill.dependencies().isEmpty()) {
                var empty = new LabelWidget("Empty", x, 3.5f);
                skillInfoPanel.depPanel.addChild("label_empty", empty);
                x += empty.getWidth();
            }
            for (var dependency : skill.dependencies()) {
                var name = "dep_skill_icon_" + dependency.skill().getKey();
                var icon = new ImageWidget(x, 4, 8, 8, dependency.texture());
                skillInfoPanel.depPanel.addChild(name, icon);
                x += 12;
            }
            skillInfoPanel.depPanel.setWidth(x);
        }
        skillInfoPanel.depPanel.setX(width / 2.0f - skillInfoPanel.depPanel.getWidth() / 2.0f);

        skillInfoPanel.learnButton.setX(width / 2.0f - skillInfoPanel.learnButton.getWidth() / 2.0f);
        skillInfoPanel.learnButton.setY(skillInfoPanel.depPanel.getY() + 18.0f);

        skillInfoPanel.energyLabel.setText(!lacked ? "" : "%d AF".formatted(skill.skill().getEnergyCostToLearn()));
        skillInfoPanel.energyLabel.setX(width / 2.0f - skillInfoPanel.energyLabel.getWidth() / 2.0f);
        skillInfoPanel.energyLabel.setY(skillInfoPanel.learnButton.getY() + 18.0f);
    }

    final class SkillInfoPanel extends PanelWidget {
        @Nullable
        AbilitySystemClient.SkillInfo skillInfo;
        final ImageButtonWidget closeBack;
        final ImageWidget iconBack = new ImageWidget(0, 0, 65, 65, Resource.Textures.UI_DEVELOPER_SKILL_ICON_BG);
        final ImageWidget icon = new ImageWidget(0, 0, 32.5f, 32.5f, (ResourceLocation) null);
        final AutoScaleLabelWidget nameLabel = new AutoScaleLabelWidget("", 0, 0, 200);
        final AutoScaleLabelWidget stateLabel = new AutoScaleLabelWidget("", 0, 0, 200);
        final PanelWidget depPanel = new PanelWidget(0, 0, 0, 16);
        final ImageButtonWidget learnButton;
        final AutoScaleLabelWidget energyLabel = new AutoScaleLabelWidget("", 0, 0, 100);

        public SkillInfoPanel() {
            super(0, 0, 0, 0);
            closeBack = new ImageButtonWidget(0, 0, AbilityDeveloperScreen.this.width, AbilityDeveloperScreen.this.height, (ResourceLocation) null, () -> {
                setEnabled(false);
                setVisible(false);
            });

            learnButton = new ImageButtonWidget(0, 0, 32, 16, Resource.Textures.UI_BUTTON_LEARN, () -> {
                if (skillInfo == null)
                    return;

                var request = new LearnSkillPacket(skillInfo.skill().getKey().toString(), mainPos.asLong());
                MisakaNetworkClient.FUTURE_MANAGER.sendRequestToServer(request,
                        response -> {
                            if (response != null && response.isSuccess())
                                onInit();
                        });
            });

            var backgroundWidget = new FillWidget(0, 0, AbilityDeveloperScreen.this.width, AbilityDeveloperScreen.this.height, 0xFF000000);
            backgroundWidget.setAlpha(0.75f);

            addChild("back", backgroundWidget);
            addChild("close_back", closeBack);
            addChild("icon_back", iconBack);
            addChild("icon", icon);
            addChild("name_label", nameLabel);
            addChild("state_label", stateLabel);
            addChild("panel_dep", depPanel);
            addChild("learn_button", learnButton);
            addChild("energy_label", energyLabel);
        }
    }

    final class SkillWidget extends ImageButtonWidget {
        public float targetScale = 1.0f;
        public float currentScale = 1.0f;
        public float targetProgress;
        public float progress;
        private final AbilitySystemClient.SkillInfo skillInfo;
        private final List<SkillWidget> resolvedDependencies = new ArrayList<>();

        SkillWidget(AbilitySystemClient.SkillInfo skillInfo) {
            super(skillInfo.x(), skillInfo.y(), PANEL_RIGHT_SKILL_SIZE, PANEL_RIGHT_SKILL_SIZE,
                    skillInfo.texture(),
                    () -> openSkillViewPanel(skillInfo));
            this.skillInfo = skillInfo;
        }

        public void resolveDependencies(Map<ResourceLocation, SkillWidget> allWidgets) {
            resolvedDependencies.clear();
            for (var depInfo : skillInfo.dependencies()) {
                var depWidget = allWidgets.get(depInfo.skill().getKey());
                if (depWidget != null) {
                    resolvedDependencies.add(depWidget);
                }
            }
        }

        @Override
        public void tick() {
            targetProgress = AbilitySystemClient.getSkillExp(skillInfo.skill()) / 100f;
        }

        @Override
        public boolean isMouseOver(double checkX, double checkY) {
            var absX = getAbsoluteX();
            var absY = getAbsoluteY();
            var scaledWidth = getWidth() * currentScale;
            var scaledHeight = getHeight() * currentScale;
            var cornerX = absX + (getWidth() - scaledWidth) / 2f;
            var cornerY = absY + (getHeight() - scaledHeight) / 2f;
            return checkX >= cornerX && checkY >= cornerY && checkX < cornerX + scaledWidth && checkY < cornerY + scaledHeight && !skillInfoPanel.isVisible() && !screenWirelessPanel.isVisible();
        }

        @Override
        public void render(WidgetRenderContext context, double mouseX, double mouseY, float partialTick) {
            var oT = textureLocation;
            setTexture(Resource.Textures.UI_DEVELOPER_SKILL_ICON_BG);
            targetScale = isHovered() ? 1.25f : 1.0f;
            currentScale = MathUtil.lerpStartEndFactor(currentScale, targetScale, ClientUtil.animationFactor(MathUtil.PI / 1.5f));

            context.pose().pushPose();
            context.pose().translate(0, 0, 0.1f);
            setScale(currentScale, currentScale, true);
            super.render(context, mouseX, mouseY, partialTick);
            context.pose().popPose();

            context.pose().pushPose();
            {
                context.pose().translate(0, 0, getZ());

                var thisCenterX = getX() + getWidth() / 2f;
                var thisCenterY = getY() + getHeight() / 2f;
                var thisRadius = (PANEL_RIGHT_SKILL_SIZE / 2f) * currentScale * 0.9f;
                var lineThickness = 4.0f;

                for (var depWidget : resolvedDependencies) {
                    var depCenterX = depWidget.getX() + depWidget.getWidth() / 2f;
                    var depCenterY = depWidget.getY() + depWidget.getHeight() / 2f;
                    var depRadius = (PANEL_RIGHT_SKILL_SIZE / 2f) * depWidget.currentScale * 0.9f;

                    var vecX = depCenterX - thisCenterX;
                    var vecY = depCenterY - thisCenterY;
                    var distBetweenCenters = (float) Math.hypot(vecX, vecY);

                    if (distBetweenCenters <= thisRadius + depRadius) continue;

                    var dirX = vecX / distBetweenCenters;
                    var dirY = vecY / distBetweenCenters;

                    var lineStartX = thisCenterX + dirX * thisRadius;
                    var lineStartY = thisCenterY + dirY * thisRadius;

                    var lineLength = distBetweenCenters - thisRadius - depRadius;
                    var angle = (float) Math.atan2(dirY, dirX);

                    context.pose().pushPose();
                    {
                        context.pose().translate(lineStartX, lineStartY, 0);
                        context.pose().mulPose(Axis.ZP.rotation(angle));
                        context.pose().translate(0, -lineThickness / 2f, 0);
                        context.pose().scale(lineLength, lineThickness, 1f);

                        var textureManager = Minecraft.getInstance().getTextureManager();
                        var texture = textureManager.getTexture(Resource.Textures.ELEMENT_LINE).getTextureView();
                        var command = new ImageDrawCommand(texture, 1, 1, 0, 0, 1, 1, 1, 1, 1, context.getAccumulatedAlpha() * getAlpha());
                        context.submit(command);
                    }
                    context.pose().popPose();
                }
            }
            context.pose().popPose();

            setTexture(oT);
            context.pose().pushPose();
            {
                context.pose().translate(0, 0, 0.2f);
                setScale(currentScale * 0.6f, currentScale * 0.6f, true);
                super.render(context, mouseX, mouseY, partialTick);
            }
            context.pose().popPose();

            context.pose().pushPose();
            {
                context.pose().translate(getX(), getY(), getZ() + 0.3f);
                context.pose().translate(getWidth() / 2, getHeight() / 2, 0);
                context.pose().scale(widthScale * 2, heightScale * 2, 1);
                context.pose().translate(-getWidth() / 2, -getHeight() / 2, 0);

                progress = MathUtil.lerpStartEndFactor(progress, targetProgress,
                        ClientUtil.animationFactor(MathUtil.PI / 2));
                var data = new UniformData(
                        new Vector2f(0.5f, 0.5f),
                        0.35f,
                        0.35f,
                        0.275f,
                        0.375f,
                        0.75f,
                        new Vector4f(1, 1, 1, getAlpha() * context.getAccumulatedAlpha()),
                        -MathUtil.PI / 2
                );
                var command = new PosTexRectDrawCommand(
                        Render.RenderPipelines.GLOW_CIRCLE,
                        getWidth(),
                        getHeight(),
                        0,
                        0,
                        1,
                        1
                ) {
                    @Override
                    public Map<String, GpuTextureView> getSamplers() {
                        return Collections.emptyMap();
                    }

                    @Override
                    public Map<String, GpuBufferSlice> getUniforms() {
                        var uboStorage = context.getDynamicUbo(UniformData.class, UniformData.UBO_SIZE);
                        var uboSlice = uboStorage.writeUniform(data);
                        return Map.of("Uniforms", uboSlice);
                    }
                };
                context.submit(command);
            }
            context.pose().popPose();
        }

        public record UniformData(Vector2f ringCenter, float innerRadius, float outerRadius, float innerGlowRadius,
                                  float outerGlowRadius, float progress, Vector4f ringColor,
                                  float startAngle) implements DynamicUniformStorage.DynamicUniform {
            public static final int UBO_SIZE = new Std140SizeCalculator()
                    .putVec2()
                    .putFloat()
                    .putFloat()
                    .putFloat()
                    .putFloat()
                    .putFloat()
                    .putVec4()
                    .putFloat()
                    .get();

            @Override
            public void write(ByteBuffer buffer) {
                Std140Builder.intoBuffer(buffer)
                        .putVec2(ringCenter)
                        .putFloat(innerRadius)
                        .putFloat(outerRadius)
                        .putFloat(innerGlowRadius)
                        .putFloat(outerGlowRadius)
                        .putFloat(progress)
                        .putVec4(ringColor)
                        .putFloat(startAngle);
            }
        }
    }*/
}