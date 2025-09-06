package org.academy.internal.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.api.client.Resource;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.animation.AnimatorListener;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.gui.framework.CGuiScreen;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AcquireCategoryPacket;
import org.academy.api.common.ability.LearnSkillPacket;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class AbilityDeveloperScreen extends CGuiScreen {
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
    public static final float PANEL_RIGHT_SKILL_SIZE = 32f;
    public static final Function<AbilityCategory, ResourceLocation> ABILITY_ICON = abilityCategory ->
            ResourceLocation.fromNamespaceAndPath(abilityCategory.getKey().getNamespace(),
                    "textures/ability/" + abilityCategory.getKey().getPath() + "/icon_glow.png"
            );
    private PanelWidget screenWirelessPanel;
    private final SkillInfoPanel skillInfoPanel = new SkillInfoPanel();
    private AutoScaleLabelWidget wirelessNameLabel;

    public AbilityDeveloperScreen(BlockPos newMainPos) {
        super(Component.empty());
        mainPos = newMainPos;
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(newMainPos)
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
        rightPanel.addChild("output_list", outputList);
        TypewriterLabelWidget welcome = setupTerminal(outputList);

        setupSkillArea(rightPanel, !bootFailed);

        playAnimation(ObjectAnimator.ofFloat(leftPanel::setY, leftPanel.getY(), mainPanelY).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_CUBIC).setStartDelay(delay));
        playAnimation(ObjectAnimator.ofFloat(leftPanel::setAlpha, 0f, 1f).setDuration(childDuration).setInterpolator(EasingFunctions.LINEAR).setStartDelay(delay));

        var rightPanelAnimation = ObjectAnimator.ofFloat(rightPanel::setY, rightPanel.getY(), mainPanelY).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_CUBIC).setStartDelay(delay);
        playAnimation(rightPanelAnimation);
        playAnimation(ObjectAnimator.ofFloat(rightPanel::setAlpha, 0f, 1f).setDuration(childDuration).setInterpolator(EasingFunctions.LINEAR).setStartDelay(delay));

        playAnimation(ObjectAnimator.ofFloat(skillInfoPanel::setAlpha, 0f, 1f).setDuration(childDuration).setInterpolator(EasingFunctions.LINEAR));
        rightPanelAnimation.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NotNull Animator animation) {
                welcome.startAnimation();
            }
        });

        setupWirelessPanel();
        setupSkillInfoPanel();
    }

    @SubscribeEvent
    public void onConnectionStatusChanged(WirelessPanelWidget.ConnectionStatusChangedEvent event) {
        if (wirelessNameLabel != null)
            wirelessNameLabel.setText(event.nodeName);
    }

    private TypewriterLabelWidget setupTerminal(ScrollPanelWidget outputList) {
        var welcome = new TypewriterLabelWidget("Welcome to Academy OS, Ver 0.0.1", 0, 0);
        addOutput("welcome", welcome, outputList);
        String userName = "None";
        if (minecraft != null && minecraft.player != null)
            userName = minecraft.player.getName().getString();

        var copyright = new TypewriterLabelWidget("Copyright (C) 2025 AcademyCraft Dev Team - GPL v3", welcome.getX(), welcome.getY() + welcome.getHeight());
        addOutput("copyright", copyright, outputList);
        var userDetected = new TypewriterLabelWidget(String.format("User %s detected, System booting...", userName), copyright.getX(), copyright.getY() + copyright.getHeight());
        addOutput("user_detected", userDetected, outputList);
        var loadingBar = new BracketProgressBarWidget('#', 50, userDetected.getX(), userDetected.getY() + userDetected.getHeight());
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
            addOutput("os", os, outputList);

            var textBox = new TextBoxWidget(32, os.getX() + os.getWidth(), os.getY(), PANEL_RIGHT_WIDTH - 24, hint.getHeight());
            textBox.setForceScale(true, 0.75f);

            textBox.setWhenEnter(s -> {
                var learned = AbilitySystemClient.getCategory() != AbilityCategories.LEVEL0.get();
                var outputCommand = new LabelWidget("OS >" + s, 0, textBox.getY());
                addOutput("output_command_" + s + outputCommand.hashCode(), outputCommand, outputList);

                final var outputStartY = outputCommand.getY() + outputCommand.getHeight();
                String singleLineOutput;

                if ("learn".equals(s)) {
                    if (!learned) {
                        if (abilityDeveloperBlockEntity != null && abilityDeveloperBlockEntity.getEnergyStored() >= 10_000) {
                            var request = new AcquireCategoryPacket(mainPos.asLong());
                            AcademyCraftClient.CLIENT_FUTURE_MANAGER.sendRequestToServer(request,
                                    response -> {
                                        if (response != null && response.getMessages() != null) {
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
        var skillInfos = AbilitySystemClient.SKILL_INFOS.get(abilityCategory);
        if (skillInfos != null) {
            for (var skillInfo : skillInfos) {
                var skillWidget = new SkillWidget(skillInfo);
                skillWidget.setVisible(visible);
                skillWidget.setEnabled(visible);
                skillWidget.setZ(1);
                parent.addChild(skillInfo.skill().getKey().toString(), skillWidget);
            }
        }
    }

    private void setupWirelessPanel() {
        screenWirelessPanel = new PanelWidget(0, 0, width, height) {
            @Override
            protected void onMousePressed(@NotNull MouseEvent event) {
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

        var backgroundWidget = new AbstractWidget(0, 0, width, height) {
        };
        screenWirelessPanel.addChild("screen_back", backgroundWidget);

        var localWirelessPanel = new WirelessPanelWidget((width - WirelessPanelWidget.PANEL_WIDTH) / 2, (height - WirelessPanelWidget.PANEL_HEIGHT) / 2, mainPos);
        localWirelessPanel.setZ(10);
        screenWirelessPanel.addChild("panel_wireless", localWirelessPanel);

        screenWirelessPanel.setZ(10);
    }

    private void setupSkillInfoPanel() {
        rootContainer.addChild("panel_skill_info", skillInfoPanel);
        skillInfoPanel.setEnabled(false);
        skillInfoPanel.setVisible(false);
    }

    private static void addOutput(String name, LabelWidget labelWidget, AbstractContainerWidget abstractContainerWidget) {
        labelWidget.setScale(0.75f);
        abstractContainerWidget.addChild(name, labelWidget);
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
                var iconBack = new ImageWidget(0, 0, leftPanelInfoPanel.getHeight(), leftPanelInfoPanel.getHeight(), Resource.Textures.HUD_SKILL_FRAME);
                leftPanelInfoPanel.addChild("icon_back", iconBack);

                var icon = new ImageWidget(0, 0, iconBack.getHeight(), iconBack.getHeight(),
                        ABILITY_ICON.apply(abilityCategory));
                icon.setWidthScale(0.65f);
                icon.setHeightScale(0.65f);
                leftPanelInfoPanel.addChild("icon", icon);
                var name = new AutoScaleLabelWidget(abilityCategory.getDescriptionId(), leftPanelInfoPanel.getHeight(), 5,
                        leftPanelInfoPanel.getWidth() - leftPanelInfoPanel.getHeight() - 4);
                leftPanelInfoPanel.addChild("name", name);

                int learned = AbilitySystemClient.LEARNED_SKILLS.size();
                int all = abilityCategory.getSkills().size();
                float progressRatio = (all == 0) ? 1.0f : (float) learned / all;
                int percentage = (int) (progressRatio * 100);

                var learnProgress = new ProgressBarWidget(leftPanelInfoPanel.getHeight(), 16,
                        leftPanelInfoPanel.getWidth() - leftPanelInfoPanel.getHeight() - 4, 2,
                        () -> progressRatio);
                learnProgress.setProgressBarColor(java.awt.Color.WHITE.hashCode());
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
                        null, () -> {
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
                var progressBarWidget = new ProgressBarWidget(9.25f, 157f, 90f, 9, () -> (float) abilityDeveloperBlockEntity.getEnergyStored() / abilityDeveloperBlockEntity.getMaxEnergyStorage());
                localLeftPanel.addChild("progress_bar", progressBarWidget);
            }
        }
        return localLeftPanel;
    }

    void openSkillViewPanel(AbilitySystemClient.SkillInfo skill) {
        skillInfoPanel.skillInfo = skill;

        skillInfoPanel.background.setWidth(width);
        skillInfoPanel.background.setHeight(height);

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
        final ImageButtonWidget background;
        final ImageWidget iconBack = new ImageWidget(0, 0, 65, 65, Resource.Textures.UI_DEVELOPER_SKILL_ICON_BG);
        final ImageWidget icon = new ImageWidget(0, 0, 32.5f, 32.5f, (ResourceLocation) null);
        final AutoScaleLabelWidget nameLabel = new AutoScaleLabelWidget("", 0, 0, 200);
        final AutoScaleLabelWidget stateLabel = new AutoScaleLabelWidget("", 0, 0, 200);
        final PanelWidget depPanel = new PanelWidget(0, 0, 0, 16);
        final ImageButtonWidget learnButton;
        final AutoScaleLabelWidget energyLabel = new AutoScaleLabelWidget("", 0, 0, 100);

        public SkillInfoPanel() {
            super(0, 0, 0, 0);
            background = new ImageButtonWidget(0, 0, AbilityDeveloperScreen.this.width, AbilityDeveloperScreen.this.height, null, () -> {
                setEnabled(false);
                setVisible(false);
            });

            learnButton = new ImageButtonWidget(0, 0, 32, 16, Resource.Textures.UI_BUTTON_LEARN, () -> {
                if (skillInfo == null)
                    return;

                var request = new LearnSkillPacket(skillInfo.skill().getKey().toString(), mainPos.asLong());
                AcademyCraftClient.CLIENT_FUTURE_MANAGER.sendRequestToServer(request,
                        response -> {
                            if (response != null && response.isSuccess())
                                onInit();
                        });
            });

            addChild("back", background);
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
        public boolean dynamicFollow = true;
        public float xOffset, yOffset;
        public float targetProgress;
        public float progress;
        public final List<AbilitySystemClient.SkillInfo> dependencies = new ArrayList<>();
        private final AbilitySystemClient.SkillInfo skillInfo;

        SkillWidget(AbilitySystemClient.SkillInfo skillInfo) {
            super(skillInfo.x(), skillInfo.y(), PANEL_RIGHT_SKILL_SIZE, PANEL_RIGHT_SKILL_SIZE,
                    skillInfo.texture(),
                    () -> openSkillViewPanel(skillInfo));
            this.skillInfo = skillInfo;
            dependencies.addAll(skillInfo.dependencies());
        }

        @Override
        public void tick() {
            if (skillInfo != null)
                targetProgress = AbilitySystemClient.getSkillExp(skillInfo.skill()) / 100f;
        }

        @Override
        public void setHovered(boolean hovered) {
            super.setHovered(hovered);
            if (minecraft != null) {
                xOffset = -(dynamicFollow ? ((float) minecraft.mouseHandler.xpos() / width) : 0f);
                yOffset = -(dynamicFollow ? ((float) minecraft.mouseHandler.ypos() / height) : 0f);
            }
        }

        @Override
        public boolean isMouseOver(double checkX, double checkY) {
            var absX = getAbsoluteX();
            var absY = getAbsoluteY();
            var scaledWidth = getWidth() * currentScale;
            var scaledHeight = getHeight() * currentScale;
            var cornerX = absX + (getWidth() - scaledWidth) / 2f;
            var cornerY = absY + (getHeight() - scaledHeight) / 2f;
            return checkX >= cornerX && checkY >= cornerY && checkX < cornerX + scaledWidth && checkY < cornerY + scaledHeight;
        }

       /* @Override
        public void render(@NotNull MatrixStack stack, MultiBufferSource.@NotNull BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
            if (!isVisible()) return;

            stack.pushPose();
            stack.translate(this.getX(), this.getY(), this.getZ());
            stack.translate(xOffset, yOffset, 0f);

            targetScale = isHovered() ? 1.25f : 1.0f;
            currentScale = MathUtil.lerpStartEndFactor(currentScale, targetScale, ClientUtil.animationFactor(MathUtil.PI / 1.5f));
            this.setScale(currentScale, currentScale, true);

            final var cX = getWidth() / 2f;
            final var cY = getHeight() / 2f;

            var lineBuf = bufferSource.getBuffer(ELEMENT_LINE);
            final var thickness = 5f;
            for (var dep : dependencies) {
                stack.pushPose();
                var m = stack.lastMatrix();
                var dX = dep.x() + PANEL_RIGHT_SKILL_SIZE / 2f - this.getX();
                var dY = dep.y() + PANEL_RIGHT_SKILL_SIZE / 2f - this.getY();
                float dx = dX - cX, dy = dY - cY;
                var rawLen = (float) Math.hypot(dx, dy);
                var length = rawLen - PANEL_RIGHT_SKILL_SIZE + thickness;
                var angle = (float) Math.atan2(dy, dx);
                stack.translate(cX, cY, 0);
                stack.mulPose(Axis.ZP.rotation(angle));
                stack.translate(PANEL_RIGHT_SKILL_SIZE / 2f - thickness / 2, -thickness / 2f, 0f);
                stack.scale(length, thickness, 1f);
                lineBuf.addVertex(m, 0f, 0f, 0f).setColor(1, 1, 1, 1f).setUv(0f, 0f);
                lineBuf.addVertex(m, 0f, 1f, 0f).setColor(1, 1, 1, 1f).setUv(0f, 1f);
                lineBuf.addVertex(m, 1f, 1f, 0f).setColor(1, 1, 1, 1f).setUv(1f, 1f);
                lineBuf.addVertex(m, 1f, 0f, 0f).setColor(1, 1, 1, 1f).setUv(1f, 0f);
                stack.popPose();
            }

            var oldType = getRenderType();
            setRenderType(PANEL_RIGHT_SKILL_ICON_BACK);
            super.render(stack, bufferSource, mouseX, mouseY, partialTick);

            this.setColor(1f, 1f, 1f);

            setRenderType(oldType);
            this.setScale(currentScale * 0.5f, currentScale * 0.5f, true);
            super.render(stack, bufferSource, mouseX, mouseY, partialTick);

            this.setScale(currentScale, currentScale, true);

            //  setRenderType(GLOW_CIRCLE);
            progress = MathUtil.lerpStartEndFactor(progress, targetProgress,
                    ClientUtil.animationFactor(MathUtil.PI / 2));
*//*            var uniform = Shaders.GLOW_CIRCLE.getUniform("progress");
            if (uniform != null) {
                uniform.set(progress);
            }*//*
            super.render(stack, bufferSource, mouseX, mouseY, partialTick);
            bufferSource.endBatch(GLOW_CIRCLE);

            setRenderType(oldType);
            stack.popPose();
        }*/
    }
}