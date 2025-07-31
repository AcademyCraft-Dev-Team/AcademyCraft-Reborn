package org.academy.internal.client.gui.screen;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.animation.AnimatorListener;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.framework.CGuiScreen;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AcquireCategoryPacket;
import org.academy.api.common.ability.LearnSkillPacket;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.renderer.Shaders;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.academy.AcademyCraft.getResourceLocation;
import static org.academy.api.client.render.RenderTypes.*;

public final class AbilityDeveloperScreen extends CGuiScreen {
    public final BlockPos mainPos;
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
    public static final Function<AbilityCategory, RenderType> ABILITY_ICON = abilityCategory ->
            RenderUtil.getPositionColorTexRenderTypeFull("ability_icon_glow", getResourceLocation(
                    "textures/ability/" + abilityCategory.getDescriptionId() + "/icon_glow.png"
            ), false);
    private PanelWidget screenWirelessPanel;
    private final SkillInfoPanel skillInfoPanel = new SkillInfoPanel();
    private AutoScaleLabelWidget wirelessNameLabel;

    public AbilityDeveloperScreen(@NotNull BlockPos newMainPos) {
        super(Component.empty());
        mainPos = newMainPos;
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(newMainPos)
                instanceof AbilityDeveloperBlockEntity entity) {
            abilityDeveloperBlockEntity = entity;
        } else {
            onClose();
        }
        if (abilityDeveloperBlockEntity != null) {
            abilityDeveloperBlockEntity.setOpen(true);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
        if (abilityDeveloperBlockEntity != null) {
            abilityDeveloperBlockEntity.setOpen(false);
        }
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

        var rightPanelBack = new ImageWidget(0, 0, PANEL_RIGHT_WIDTH, PANEL_MAIN_HEIGHT, PANEL_RIGHT_BACK);
        rightPanel.addChild("panel_right_back", rightPanelBack);
        var rightPanelInfo = new ImageWidget(0, 0, PANEL_RIGHT_WIDTH, PANEL_MAIN_HEIGHT, SKILL_PANEL_INFO);
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
            public void onAnimationEnd(Animator animation) {
                welcome.start();
            }
        });

        setupWirelessPanel();
        setupSkillInfoPanel();
    }

    @SubscribeEvent
    public void onConnectionStatusChanged(WirelessPanelWidget.ConnectionStatusChangedEvent event) {
        if (this.wirelessNameLabel != null) {
            this.wirelessNameLabel.setText(event.nodeName);
        }
    }

    private TypewriterLabelWidget setupTerminal(ScrollPanelWidget outputList) {
        TypewriterLabelWidget welcome = new TypewriterLabelWidget("Welcome to Academy OS, Ver 0.0.1", 0, 0);
        addOutput("welcome", welcome, outputList);
        String userName = "None";
        if (Minecraft.getInstance().player != null) {
            userName = Minecraft.getInstance().player.getName().getString();
        }
        var copyright = new TypewriterLabelWidget("Copyright (C) 2025 Academy Tech - GPL v3", welcome.getX(), welcome.getY() + welcome.getHeight());
        addOutput("copyright", copyright, outputList);
        var userDetected = new TypewriterLabelWidget(String.format("User %s detected, System booting...", userName), copyright.getX(), copyright.getY() + copyright.getHeight());
        addOutput("user_detected", userDetected, outputList);
        var loadingBar = new BracketProgressBarWidget('#', 50, userDetected.getX(), userDetected.getY() + userDetected.getHeight());
        addOutput("loading_bar", loadingBar, outputList);
        var invalid = new TypewriterLabelWidget("FATAL: User's ability category is invalid, booting aborted.", loadingBar.getX(), loadingBar.getY() + loadingBar.getHeight());
        addOutput("invalid", invalid, outputList);
        var hint = new TypewriterLabelWidget("Type 'learn' to acquire new category.", invalid.getX(), invalid.getY() + invalid.getHeight());
        addOutput("hint", hint, outputList);
        welcome.afterFinished = copyright::start;
        copyright.afterFinished = userDetected::start;
        userDetected.afterFinished = loadingBar::start;
        loadingBar.afterFinished = invalid::start;
        invalid.afterFinished = hint::start;
        hint.afterFinished = () -> {
            var os = new LabelWidget("OS >", hint.getX(), hint.getY() + hint.getHeight());
            addOutput("os", os, outputList);

            var textBox = new TextBoxWidget(32, os.getX() + os.getWidth(), os.getY(), PANEL_RIGHT_WIDTH - 24, hint.getHeight());
            textBox.scale = 0.75f;

            textBox.whenEnter = s -> {
                var learned = AbilitySystemClient.getCategory() != AbilityCategories.LEVEL0.get();
                var outputCommand = new LabelWidget("OS >" + s, 0, textBox.getY());
                addOutput("output_command_" + s + outputCommand.hashCode(), outputCommand, outputList);

                final var outputStartY = outputCommand.getY() + outputCommand.getHeight();
                String singleLineOutput;

                if ("learn".equals(s)) {
                    if (!learned) {
                        if (abilityDeveloperBlockEntity.getEnergyStored() >= 10_000) {
                            var request = new AcquireCategoryPacket(mainPos);
                            AcademyCraftClient.CLIENT_FUTURE_MANAGER.sendRequestToServer(request,
                                    (var response) -> {
                                        if (response != null && response.messages != null) {
                                            var lastWidget = outputCommand;
                                            for (var string : response.messages) {
                                                var newOutput = new LabelWidget(string, 0, lastWidget.getY() + lastWidget.getHeight());
                                                addOutput("output_info_" + string + newOutput.hashCode(), newOutput, outputList);
                                                os.setY(newOutput.getY() + newOutput.getHeight());
                                                textBox.setY(newOutput.getY() + newOutput.getHeight());
                                                outputList.scrollToBottom();
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
                outputList.scrollToBottom();
            };

            outputList.addChild("text_box", textBox);
            rootContainer.setFocusedChild(textBox);
        };
        return welcome;
    }

    private void setupSkillArea(PanelWidget parent, boolean visible) {
        var parallaxImageWidget = new ParallaxImageWidget(PANEL_RIGHT_SKILL_BACK_X, PANEL_RIGHT_SKILL_BACK_Y, PANEL_RIGHT_SKILL_BACK_WIDTH, PANEL_RIGHT_SKILL_BACK_HEIGHT, SKILL_PANEL_BACK, width, height);
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
                parent.addChild(skillInfo.skill().getDescriptionId(), skillWidget);
            }
        }
    }

    private void setupWirelessPanel() {
        screenWirelessPanel = new PanelWidget(0, 0, width, height);
        screenWirelessPanel.setZ(100);
        screenWirelessPanel.setVisible(false);
        screenWirelessPanel.setEnabled(false);
        rootContainer.addChild("panel_screen_wireless", screenWirelessPanel);

        var backgroundWidget = new BackgroundWidget(this);
        backgroundWidget.runnable = () -> {
            screenWirelessPanel.setVisible(false);
            screenWirelessPanel.setEnabled(false);
        };
        screenWirelessPanel.addChild("screen_back", backgroundWidget);

        var localWirelessPanel = new WirelessPanelWidget((width - WirelessPanelWidget.PANEL_WIDTH) / 2, (height - WirelessPanelWidget.PANEL_HEIGHT) / 2, mainPos);
        screenWirelessPanel.addChild("panel_wireless", localWirelessPanel);
    }

    private void setupSkillInfoPanel() {
        rootContainer.addChild("panel_skill_info", skillInfoPanel);
        skillInfoPanel.setZ(150);
        skillInfoPanel.setEnabled(false);
        skillInfoPanel.setVisible(false);
    }

    public static AbilitySystemClient.SkillInfo registerSkillInfo(AbilityCategory abilityCategory, Skill skill, List<AbilitySystemClient.SkillInfo> dependencies, ResourceLocation icon, float x, float y) {
        var info = new AbilitySystemClient.SkillInfo(skill, dependencies, icon, x, y);
        if (!AbilitySystemClient.SKILL_INFOS.containsKey(abilityCategory)) {
            AbilitySystemClient.SKILL_INFOS.put(abilityCategory, new ArrayList<>());
        }
        AbilitySystemClient.SKILL_INFOS.get(abilityCategory).add(info);
        return info;
    }

    private static void addOutput(String name, LabelWidget labelWidget, AbstractContainerWidget abstractContainerWidget) {
        labelWidget.scale = 0.75f;
        labelWidget.setWidth(labelWidget.getWidth() * labelWidget.scale);
        abstractContainerWidget.addChild(name, labelWidget);
    }

    private @NotNull PanelWidget getLeftPanel() {
        var localLeftPanel = new PanelWidget(0, 0, PANEL_LEFT_WIDTH, PANEL_MAIN_HEIGHT);
        {
            var leftPanelBack = new BlendQuadWidget(0, 70, PANEL_LEFT_WIDTH, 115);
            leftPanelBack.setAlpha(0.5f);
            localLeftPanel.addChild("left_panel_back", leftPanelBack);
            var leftPanelBackBottom = new ImageWidget(4.25f, 0, 100, PANEL_MAIN_HEIGHT, PANEL_LEFT_BACK_MIDDLE);
            localLeftPanel.addChild("left_panel_back_bottom", leftPanelBackBottom);
            var leftPanelBackTop = new ImageWidget(0, 0, PANEL_LEFT_WIDTH, PANEL_MAIN_HEIGHT, PANEL_LEFT_BACK_TOP);
            localLeftPanel.addChild("left_panel_back_top", leftPanelBackTop);
            var leftPanelInfoPanel = new PanelWidget(0, 70, PANEL_LEFT_WIDTH, 32);
            localLeftPanel.addChild("left_panel_info", leftPanelInfoPanel);
            {
                var iconBack = new ImageWidget(0, 0, leftPanelInfoPanel.getHeight(), leftPanelInfoPanel.getHeight(), ICON_BOX);
                leftPanelInfoPanel.addChild("icon_back", iconBack);
                var abilityCategory = AbilitySystemClient.getCategory();
                var icon = new ImageWidget(0, 0, iconBack.getHeight(), iconBack.getHeight(),
                        ABILITY_ICON.apply(abilityCategory));
                icon.widthScale = 0.65f;
                icon.heightScale = 0.65f;
                leftPanelInfoPanel.addChild("icon", icon);
                var name = new AutoScaleLabelWidget(abilityCategory.getDescriptionId(), leftPanelInfoPanel.getHeight(), 5,
                        leftPanelInfoPanel.getWidth() - leftPanelInfoPanel.getHeight() - 4);
                leftPanelInfoPanel.addChild("name", name);
                var learnProgress = new ProgressBarWidget(leftPanelInfoPanel.getHeight(), 16,
                        leftPanelInfoPanel.getWidth() - leftPanelInfoPanel.getHeight() - 4, 2,
                        () -> {
                            int learned = AbilitySystemClient.LEARNED_SKILLS.size();
                            int all = abilityCategory.getSkills().size();
                            if (all == 0) return 100.0f;
                            else return (float) learned / all;
                        });
                learnProgress.progressBarColor = Color.WHITE.hashCode();
                leftPanelInfoPanel.addChild("learn_progress", learnProgress);
                var progress = new AutoScaleLabelWidget("LEARNED " + 100 + "%", leftPanelInfoPanel.getHeight(), 18,
                        learnProgress.getWidth() * 0.5f);
                leftPanelInfoPanel.addChild("progress", progress);
                var levelLabel = new AutoScaleLabelWidget(abilityCategory.getDescriptionId(), progress.getX() + learnProgress.getWidth() * 0.5f, 18, learnProgress.getWidth() * 0.5f - 4);
                levelLabel.setX(leftPanelInfoPanel.getWidth() - levelLabel.getWidth() - 4);
                levelLabel.color = 0xFF1177D6;
                leftPanelInfoPanel.addChild("label_level", levelLabel);
            }
            var wirelessLabel = new AutoScaleLabelWidget("Current Node:", 8, 110, 90);
            localLeftPanel.addChild("label_wireless", wirelessLabel);
            var wirelessButtonPanel = new PanelWidget(8, 120, 90, 16);
            localLeftPanel.addChild("button_wireless", wirelessButtonPanel);
            {
                var button = new ImageButtonWidget(0, 0,
                        wirelessButtonPanel.getWidth(), wirelessButtonPanel.getHeight(),
                        null, () -> {
                    var wirelessRoot = screenWirelessPanel;
                    wirelessRoot.setVisible(true);
                    wirelessRoot.setEnabled(true);
                });
                wirelessButtonPanel.addChild("button", button);

                var back = new ImageWidget(0, 0, 90, 14, ELEMENT_BACK_LIGHT);
                wirelessButtonPanel.addChild("back", back);
                var icon = new ImageWidget(6, 1, 12, 12, ICON_NODE);
                wirelessButtonPanel.addChild("icon", icon);
                var nameLabel = new AutoScaleLabelWidget("None", 24, 3,
                        wirelessButtonPanel.getWidth() - 24 - 4);
                this.wirelessNameLabel = nameLabel;
                wirelessButtonPanel.addChild("label_name", nameLabel);
            }
            var powerLabel = new AutoScaleLabelWidget("Current Power:", 8, 142.5f, 90);
            localLeftPanel.addChild("power_label", powerLabel);
            var progressBarWidget = new ProgressBarWidget(9.25f, 157f, 90f, 9, () -> (float) abilityDeveloperBlockEntity.getEnergyStored() / abilityDeveloperBlockEntity.getMaxEnergyStorage());
            localLeftPanel.addChild("progress_bar", progressBarWidget);
        }
        return localLeftPanel;
    }

    void openSkillViewPanel(AbilitySystemClient.SkillInfo skill) {
        skillInfoPanel.skillInfo = skill;

        skillInfoPanel.background.setWidth(AbilityDeveloperScreen.this.width);
        skillInfoPanel.background.setHeight(AbilityDeveloperScreen.this.height);

        skillInfoPanel.setWidth(AbilityDeveloperScreen.this.width);
        skillInfoPanel.setHeight(AbilityDeveloperScreen.this.height);
        skillInfoPanel.setEnabled(true);
        skillInfoPanel.setVisible(true);

        skillInfoPanel.icon.setX((float) AbilityDeveloperScreen.this.width / 2 - skillInfoPanel.icon.getWidth() / 2);
        skillInfoPanel.icon.setY(((float) AbilityDeveloperScreen.this.height / 2 - skillInfoPanel.icon.getHeight() / 2) - 25);
        skillInfoPanel.iconBack.setX((float) AbilityDeveloperScreen.this.width / 2 - skillInfoPanel.iconBack.getWidth() / 2);
        skillInfoPanel.iconBack.setY(((float) AbilityDeveloperScreen.this.height / 2 - skillInfoPanel.iconBack.getHeight() / 2) - 25);
        skillInfoPanel.icon.renderType = RenderUtil.getPositionColorTexRenderTypeFull("skill_icon", skill.texture(), false);

        skillInfoPanel.nameLabel.setText("Skill: %s".formatted(skill.skill().getTranslatedName()));
        skillInfoPanel.nameLabel.setX((float) AbilityDeveloperScreen.this.width / 2 - skillInfoPanel.nameLabel.getWidth() / 2);
        skillInfoPanel.nameLabel.setY(skillInfoPanel.icon.getY() + 50);

        var lacked = ClientUtil.lacksSkill(skill.skill());
        skillInfoPanel.stateLabel.setText(lacked ? "Skill not learned" : "Skill learned");
        skillInfoPanel.stateLabel.color = lacked ? 0XFFFF0000 : 0xFFFFFFFF;
        skillInfoPanel.stateLabel.setX((float) AbilityDeveloperScreen.this.width / 2 - skillInfoPanel.stateLabel.getWidth() / 2);
        skillInfoPanel.stateLabel.setY(skillInfoPanel.nameLabel.getY() + 12);

        skillInfoPanel.learnButton.setEnabled(lacked);
        skillInfoPanel.learnButton.setVisible(lacked);

        skillInfoPanel.depPanel.clearChildren();
        skillInfoPanel.depPanel.setY(skillInfoPanel.stateLabel.getY() + 10);
        if (lacked) {
            var x = 0;
            var labelWidget = new LabelWidget("Dep.", x, 3.5f);
            x += (int) labelWidget.getWidth();
            skillInfoPanel.depPanel.addChild("label_name", labelWidget);
            if (skill.dependencies().isEmpty()) {
                var empty = new LabelWidget("Empty", x, 3.5f);
                skillInfoPanel.depPanel.addChild("label_empty", empty);
                x += (int) empty.getWidth();
            }
            for (var dependency : skill.dependencies()) {
                var name = "dep_skill_icon_" + skill.skill().getDescriptionId();
                var icon = new ImageWidget(x, 4, 8, 8, RenderUtil.getPositionTexRenderType(name, dependency.texture(), false));
                skillInfoPanel.depPanel.addChild(name, icon);
                x += 12;
            }
            skillInfoPanel.depPanel.setWidth(x);
        }
        skillInfoPanel.depPanel.setX((float) AbilityDeveloperScreen.this.width / 2 - skillInfoPanel.depPanel.getWidth() / 2);

        skillInfoPanel.learnButton.setX((float) AbilityDeveloperScreen.this.width / 2 - skillInfoPanel.learnButton.getWidth() / 2);
        skillInfoPanel.learnButton.setY(skillInfoPanel.depPanel.getY() + 18);

        skillInfoPanel.energyLabel.setText(!lacked ? "" : "%d AF".formatted(skill.skill().getEnergyCostToLearn()));
        skillInfoPanel.energyLabel.setX((float) AbilityDeveloperScreen.this.width / 2 - skillInfoPanel.energyLabel.getWidth() / 2);
        skillInfoPanel.energyLabel.setY(skillInfoPanel.learnButton.getY() + 18);
    }

    final class SkillInfoPanel extends PanelWidget {
        AbilitySystemClient.SkillInfo skillInfo;
        final BackgroundWidget background = new BackgroundWidget(AbilityDeveloperScreen.this);
        final ImageWidget iconBack = new ImageWidget(0, 0, 65, 65, PANEL_RIGHT_SKILL_ICON_BACK);
        final ImageWidget icon = new ImageWidget(0, 0, 32.5f, 32.5f, null);
        final AutoScaleLabelWidget nameLabel = new AutoScaleLabelWidget("", 0, 0, 200, true);
        final AutoScaleLabelWidget stateLabel = new AutoScaleLabelWidget("", 0, 0, 200, true);
        final PanelWidget depPanel = new PanelWidget(0, 0, 0, 16);
        final ImageButtonWidget learnButton = new ImageButtonWidget(0, 0, 32, 16, BUTTON, () -> {
            if (skillInfo == null) return;
            LearnSkillPacket request = new LearnSkillPacket(skillInfo.skill().getDescriptionId(), mainPos);
            AcademyCraftClient.CLIENT_FUTURE_MANAGER.sendRequestToServer(request,
                    (LearnSkillPacket.Response response) -> {
                        if (response != null && response.success) {
                            init();
                        }
                    });
        });
        final AutoScaleLabelWidget energyLabel = new AutoScaleLabelWidget("", 0, 0, 100, true);

        public SkillInfoPanel() {
            super(0, 0, 0, 0);
            addChild("back", background);
            addChild("icon_back", iconBack);
            addChild("icon", icon);
            addChild("name_label", nameLabel);
            addChild("state_label", stateLabel);
            addChild("panel_dep", depPanel);
            addChild("learn_button", learnButton);
            addChild("energy_label", energyLabel);
            background.runnable = () -> {
                setEnabled(false);
                setVisible(false);
            };
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

        @SuppressWarnings("SuspiciousNameCombination")
        SkillWidget(AbilitySystemClient.SkillInfo skillInfo) {
            super(skillInfo.x(), skillInfo.y(), PANEL_RIGHT_SKILL_SIZE, PANEL_RIGHT_SKILL_SIZE,
                    SKILL_ICON.apply(skillInfo.skill().getDescriptionId(), skillInfo.texture()),
                    () -> AbilityDeveloperScreen.this.openSkillViewPanel(skillInfo));
            dependencies.addAll(skillInfo.dependencies());
            targetProgress = AbilitySystemClient.getSkillExp(skillInfo.skill()) / 100f;
        }

        @Override
        public void mouseMoved(double mouseX, double mouseY) {
            super.mouseMoved(mouseX, mouseY);
            this.xOffset = -(dynamicFollow ? ((float) mouseX / AbilityDeveloperScreen.this.width) : 0f);
            this.yOffset = -(dynamicFollow ? ((float) mouseY / AbilityDeveloperScreen.this.height) : 0f);
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

        @Override
        public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
            if (!isVisible()) return;

            stack.pushPose();
            stack.translate(xOffset, yOffset, 0f);

            targetScale = isHovered() ? 1.25f : 1.0f;
            currentScale = MathUtil.lerpStartEndFactor(currentScale, targetScale, ClientUtil.animationFactor(MathUtil.PI / 1.5f));
            widthScale = currentScale;
            heightScale = currentScale;

            final var cX = x + PANEL_RIGHT_SKILL_SIZE / 2f;
            final var cY = y + PANEL_RIGHT_SKILL_SIZE / 2f;

            var lineBuf = bufferSource.getBuffer(ELEMENT_LINE);
            final var thickness = 5f;
            for (var dep : dependencies) {
                stack.pushPose();
                var m = stack.lastMatrix();
                var dX = dep.x() + PANEL_RIGHT_SKILL_SIZE / 2f;
                var dY = dep.y() + PANEL_RIGHT_SKILL_SIZE / 2f;
                float dx = dX - cX, dy = dY - cY;
                var rawLen = (float) Math.hypot(dx, dy);
                var length = rawLen - PANEL_RIGHT_SKILL_SIZE + thickness;
                var angle = (float) Math.atan2(dy, dx);
                stack.translate(cX, cY, getZ());
                stack.mulPose(Axis.ZP.rotation(angle));
                stack.translate(PANEL_RIGHT_SKILL_SIZE / 2f - thickness / 2, -thickness / 2f, 0f);
                stack.scale(length, thickness, 1f);
                lineBuf.addVertex(m, 0f, 0f, 0f).setColor(1, 1, 1, 1f).setUv(0f, 0f);
                lineBuf.addVertex(m, 0f, 1f, 0f).setColor(1, 1, 1, 1f).setUv(0f, 1f);
                lineBuf.addVertex(m, 1f, 1f, 0f).setColor(1, 1, 1, 1f).setUv(1f, 1f);
                lineBuf.addVertex(m, 1f, 0f, 0f).setColor(1, 1, 1, 1f).setUv(1f, 0f);
                stack.popPose();
            }

            var oldType = renderType;
            renderType = PANEL_RIGHT_SKILL_ICON_BACK;
            super.render(stack, bufferSource, mouseX, mouseY, partialTick);

            var oRed = red;
            var oGreen = green;
            var oBlue = blue;
            red = 1f;
            green = 1f;
            blue = 1f;

            renderType = oldType;
            widthScale *= 0.5f;
            heightScale *= 0.5f;
            super.render(stack, bufferSource, mouseX, mouseY, partialTick);

            red = oRed;
            green = oGreen;
            blue = oBlue;
            widthScale /= 0.5f;
            heightScale /= 0.5f;

            renderType = GLOW_CIRCLE;
            progress = MathUtil.lerpStartEndFactor(progress, targetProgress,
                    ClientUtil.animationFactor(MathUtil.PI / 2));
            var uniform = Shaders.GLOW_CIRCLE.getUniform("progress");
            if (uniform != null) {
                uniform.set(progress);
            }
            super.render(stack, bufferSource, mouseX, mouseY, partialTick);
            bufferSource.endBatch(GLOW_CIRCLE);

            renderType = oldType;
            stack.popPose();
        }
    }
}