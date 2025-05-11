package org.academy.internal.client.gui.screen;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.gui.WirelessPanelHelper;
import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.framework.CGuiScreen;
import org.academy.api.client.gui.framework.Widget;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.network.FutureManagerClient;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.Packets;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.ability.builtin.level0.Level0;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.academy.api.client.gui.ImageResources.RenderTypes.*;

public class AbilityDeveloperScreen extends CGuiScreen implements WirelessPanelHelper.WirelessPanel {
    public final BlockPos mainPos;
    public AbilityDeveloperBlockEntity abilityDeveloperBlockEntity;
    public static final ArrayList<SkillInfo> SKILL_INFOS = new ArrayList<>();
    public static final float PANEL_MAIN_WIDTH = 400;
    public static final float PANEL_MAIN_HEIGHT = 187;
    public static final float PANEL_LEFT_WIDTH = 108.5f;
    public static final float PANEL_RIGHT_WIDTH = 278;
    public static final float PANEL_RIGHT_SKILL_BACK_X = 11;
    public static final float PANEL_RIGHT_SKILL_BACK_Y = 17.5f;
    public static final float PANEL_RIGHT_SKILL_BACK_WIDTH = 256;
    public static final float PANEL_RIGHT_SKILL_BACK_HEIGHT = 139.5f;
    public static final float PANEL_RIGHT_SKILL_SIZE = 32f;
    private String currentlyConnectedNodeName = "None";
    private PanelWidget screenWirelessPanel;
    private PanelWidget wirelessPanel;
    private SmoothScrollPanelWidget nodeListPanel;
    private PanelWidget leftPanel;
    private final SkillInfoPanel skillInfoPanel = new SkillInfoPanel();

    public AbilityDeveloperScreen(@NotNull BlockPos mainPos) {
        super(Component.empty());
        this.mainPos = mainPos;
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(mainPos)
                instanceof AbilityDeveloperBlockEntity entity) {
            this.abilityDeveloperBlockEntity = entity;
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
    }

    @Override
    protected void onInit() {
        PanelWidget mainPanel = new PanelWidget(width / 2f - PANEL_MAIN_WIDTH / 2f, height / 2f - PANEL_MAIN_HEIGHT / 2f, PANEL_MAIN_WIDTH, PANEL_MAIN_HEIGHT);
        rootContainer.addChild("panel_main", mainPanel);
        {
            leftPanel = getLeftPanel();
            mainPanel.addChild("panel_left", leftPanel);
            PanelWidget rightPanel = new PanelWidget(PANEL_LEFT_WIDTH, 0, PANEL_RIGHT_WIDTH, PANEL_MAIN_HEIGHT);
            mainPanel.addChild("panel_right", rightPanel);
            {
                ImageWidget rightPanelBack = new ImageWidget(0, 0, PANEL_RIGHT_WIDTH, PANEL_MAIN_HEIGHT, RENDER_TYPE_PANEL_RIGHT_BACK);
                rightPanel.addChild("panel_right_back", rightPanelBack);
                ImageWidget rightPanelInfo = new ImageWidget(0, 0, PANEL_RIGHT_WIDTH, PANEL_MAIN_HEIGHT, RENDER_TYPE_SKILL_PANEL_INFO);
                rightPanel.addChild("panel_right_info", rightPanelInfo);
                boolean bootFailed = AbilitySystemClient.getCategory() == Level0.INSTANCE;
                SmoothScrollPanelWidget outputList = new SmoothScrollPanelWidget(
                        PANEL_RIGHT_SKILL_BACK_X + 5, PANEL_RIGHT_SKILL_BACK_Y + 5, PANEL_RIGHT_WIDTH - 32, 132);
                outputList.setEnabled(bootFailed);
                outputList.setVisible(bootFailed);
                rightPanel.addChild("output_list", outputList);
                {
                    TypewriterLabelWidget welcome = new TypewriterLabelWidget("Welcome to Academy OS, Ver 0.0.1", 0, 0);
                    addOutput("welcome", welcome, outputList);
                    String userName = "None";
                    if (Minecraft.getInstance().player != null) {
                        userName = Minecraft.getInstance().player.getName().getString();
                    }
                    TypewriterLabelWidget copyright = new TypewriterLabelWidget("Copyright (C) 2025 Academy Tech - GPL v3", welcome.getX(), welcome.getY() + welcome.getHeight());
                    addOutput("copyright", copyright, outputList);
                    TypewriterLabelWidget userDetected = new TypewriterLabelWidget(String.format("User %s detected, System booting...", userName), copyright.getX(), copyright.getY() + copyright.getHeight());
                    addOutput("user_detected", userDetected, outputList);
                    BracketProgressBarWidget loadingBar = new BracketProgressBarWidget('#', 50, userDetected.getX(), userDetected.getY() + userDetected.getHeight());
                    addOutput("loading_bar", loadingBar, outputList);
                    TypewriterLabelWidget invalid = new TypewriterLabelWidget("FATAL: User's ability category is invalid, booting aborted.", loadingBar.getX(), loadingBar.getY() + loadingBar.getHeight());
                    addOutput("invalid", invalid, outputList);
                    TypewriterLabelWidget hint = new TypewriterLabelWidget("Type 'learn' to acquire new category.", invalid.getX(), invalid.getY() + invalid.getHeight());
                    addOutput("hint", hint, outputList);
                    welcome.afterFinished = copyright::start;
                    copyright.afterFinished = userDetected::start;
                    userDetected.afterFinished = loadingBar::start;
                    loadingBar.afterFinished = invalid::start;
                    invalid.afterFinished = hint::start;
                    hint.afterFinished = () -> {
                        LabelWidget os = new LabelWidget("OS >", hint.getX(), hint.getY() + hint.getHeight());
                        addOutput("os", os, outputList);

                        TextBoxWidget textBox = new TextBoxWidget(32, os.getX() + os.getWidth(), os.getY(), PANEL_RIGHT_WIDTH - 24, hint.getHeight());
                        textBox.scale = 0.75f;

                        textBox.whenEnter = s -> {
                            boolean learned = AbilitySystemClient.getCategory() != Level0.INSTANCE;
                            LabelWidget outputCommand = new LabelWidget("OS >" + s, 0, textBox.getY());
                            addOutput("output_command_" + s + outputCommand.hashCode(), outputCommand, outputList);

                            final float outputStartY = outputCommand.getY() + outputCommand.getHeight();
                            String singleLineOutput;

                            if ("learn".equals(s)) {
                                if (!learned) {
                                    if (abilityDeveloperBlockEntity.getEnergyStored() >= 10_000) {
                                        FutureManagerClient.sendFuturePacket(Packets.C2S_ACQUIRE_CATEGORY, (Consumer<ArrayList<String>>) strings -> {
                                            Widget lastWidget = outputCommand;
                                            for (String string : strings) {
                                                LabelWidget newOutput = new LabelWidget(string, 0, lastWidget.getY() + lastWidget.getHeight());
                                                addOutput("output_info_" + string + newOutput.hashCode(), newOutput, outputList);
                                                os.setY(newOutput.getY() + newOutput.getHeight());
                                                textBox.setY(newOutput.getY() + newOutput.getHeight());
                                                outputList.scrollToBottom();
                                                lastWidget = newOutput;
                                            }
                                        }, mainPos);
                                        return;
                                    } else {
                                        singleLineOutput = "Insufficient energy available.";
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

                            LabelWidget outputInfo = new LabelWidget(singleLineOutput, 0, outputStartY);
                            addOutput("output_info_" + singleLineOutput + outputInfo.hashCode(), outputInfo, outputList);
                            os.setY(outputInfo.getY() + outputInfo.getHeight());
                            textBox.setY(outputInfo.getY() + outputInfo.getHeight());
                            outputList.scrollToBottom();
                        };

                        outputList.addChild("text_box", textBox);
                        rootContainer.setFocusedChild(textBox);
                    };
                    welcome.start();
                }
                PanelWidget skillPanel = new PanelWidget(0, 0, PANEL_RIGHT_WIDTH, PANEL_MAIN_HEIGHT);
                skillPanel.setEnabled(!bootFailed);
                skillPanel.setVisible(!bootFailed);
                rightPanel.addChild("panel_skill", skillPanel);
                {
                    ParallaxImageWidget parallaxImageWidget = new ParallaxImageWidget(PANEL_RIGHT_SKILL_BACK_X, PANEL_RIGHT_SKILL_BACK_Y, PANEL_RIGHT_SKILL_BACK_WIDTH, PANEL_RIGHT_SKILL_BACK_HEIGHT, RENDER_TYPE_SKILL_PANEL_BACK, width, height);
                    skillPanel.addChild("skill_area_back", parallaxImageWidget);
                    for (SkillInfo skill : SKILL_INFOS) {
                        SkillWidget skillWidget = new SkillWidget(skill);
                        skillPanel.addChild(skill.skill.name, skillWidget);
                    }
                }
            }
        }

        screenWirelessPanel = new PanelWidget(0, 0, width, height);
        screenWirelessPanel.setZ(100);
        screenWirelessPanel.setVisible(false);
        screenWirelessPanel.setEnabled(false);
        rootContainer.addChild("panel_screen_wireless", screenWirelessPanel);
        {
            BackgroundWidget backgroundWidget = new BackgroundWidget(this);
            backgroundWidget.runnable = () -> {
                screenWirelessPanel.setVisible(false);
                screenWirelessPanel.setEnabled(false);
            };
            screenWirelessPanel.addChild("screen_back", backgroundWidget);

            PanelWidget wirelessPanel = WirelessPanelHelper.getWirelessPanel((width - WirelessPanelHelper.PANEL_WIDTH) / 2, (height - WirelessPanelHelper.PANEL_HEIGHT) / 2);
            this.wirelessPanel = wirelessPanel;
            screenWirelessPanel.addChild(WirelessPanelHelper.PANEL_WIRELESS_NAME, wirelessPanel);
            {
                nodeListPanel = wirelessPanel.getChildUnSafe("node_list");
            }
        }
        rootContainer.addChild("panel_skill_info", skillInfoPanel);
        skillInfoPanel.setZ(150);
        skillInfoPanel.setEnabled(false);
        skillInfoPanel.setVisible(false);
        requestCurrentNodeStatus();
        requestAvailableNodes(getNodeList());
    }

    public static SkillInfo registerSkillInfo(Skill skill, List<SkillInfo> dependencies, ResourceLocation icon, float x, float y) {
        SkillInfo info = new SkillInfo(skill, dependencies, icon, x, y, 0);
        SKILL_INFOS.add(info);
        return info;
    }

    private static void addOutput(String name, LabelWidget labelWidget, AbstractContainerWidget abstractContainerWidget) {
        labelWidget.scale = 0.75f;
        labelWidget.setWidth(labelWidget.getWidth() * labelWidget.scale);
        abstractContainerWidget.addChild(name, labelWidget);
    }

    private @NotNull PanelWidget getLeftPanel() {
        PanelWidget leftPanel = new PanelWidget(0, 0, PANEL_LEFT_WIDTH, PANEL_MAIN_HEIGHT);
        {
            ImageWidget leftPanelBackMiddle = new ImageWidget(0, 70, PANEL_LEFT_WIDTH, 115, RENDER_TYPE_ELEMENT_BACK_DARK);
            leftPanel.addChild("left_panel_back_middle", leftPanelBackMiddle);
            ImageWidget leftPanelBackBottom = new ImageWidget(4.25f, 0, 100, PANEL_MAIN_HEIGHT, RENDER_TYPE_PANEL_LEFT_BACK_MIDDLE);
            leftPanel.addChild("left_panel_back_bottom", leftPanelBackBottom);
            ImageWidget leftPanelBackTop = new ImageWidget(0, 0, PANEL_LEFT_WIDTH, PANEL_MAIN_HEIGHT, RENDER_TYPE_PANEL_LEFT_BACK_TOP);
            leftPanel.addChild("left_panel_back_top", leftPanelBackTop);
            LabelWidget wirelessLabel = new LabelWidget("Current Node:", 8, 110);
            leftPanel.addChild("label_wireless", wirelessLabel);
            PanelButtonWidget wirelessButtonPanel = new PanelButtonWidget(8, 120, 90, 16, () -> {
                AbstractContainerWidget wirelessRoot = screenWirelessPanel;
                wirelessRoot.setVisible(true);
                wirelessRoot.setEnabled(true);
                requestCurrentNodeStatus();
                requestAvailableNodes(getNodeList());
            });
            leftPanel.addChild("button_wireless", wirelessButtonPanel);
            {
                ImageWidget back = new ImageWidget(0, 0, 90, 14, RENDER_TYPE_ELEMENT_BACK_LIGHT);
                wirelessButtonPanel.addChild("back", back);
                ImageWidget icon = new ImageWidget(6, 1, 12, 12, RENDER_TYPE_ICON_NODE);
                wirelessButtonPanel.addChild("icon", icon);
                LabelWidget nameLabel = new LabelWidget(this.currentlyConnectedNodeName, 24, 3);
                wirelessButtonPanel.addChild("label_name", nameLabel);
            }
            LabelWidget powerLabel = new LabelWidget("Current Power:", 8, 142.5f);
            leftPanel.addChild("power_label", powerLabel);
            ProgressBarWidget progressBarWidget = new ProgressBarWidget(9.25f, 157f, 90f, 9, () -> (float) abilityDeveloperBlockEntity.getEnergyStored() / abilityDeveloperBlockEntity.getMaxEnergyStorage());
            leftPanel.addChild("progress_bar", progressBarWidget);
        }
        return leftPanel;
    }

    @Override
    public void updateConnectedNodeDisplay(boolean isNull, String nodeName) {
        WirelessPanelHelper.WirelessPanel.super.updateConnectedNodeDisplay(isNull, nodeName);
        if (leftPanel.<PanelButtonWidget>getChildUnSafe("button_wireless").getChildren().get("label_name") instanceof LabelWidget labelWidget) {
            labelWidget.value = getConnectedNodeName();
        }
    }

    void openSkillViewPanel(SkillInfo skill) {
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
        skillInfoPanel.icon.renderType = RenderUtil.getPositionColorTexRenderTypeFull("skill_icon", skill.texture, false);

        skillInfoPanel.nameLabel.value = "Skill: %s".formatted(skill.skill.name);
        skillInfoPanel.nameLabel.setWidth(Minecraft.getInstance().font.width(FormattedText.of(skillInfoPanel.nameLabel.value)));
        skillInfoPanel.nameLabel.setX((float) AbilityDeveloperScreen.this.width / 2 - skillInfoPanel.nameLabel.getWidth() / 2);
        skillInfoPanel.nameLabel.setY(skillInfoPanel.icon.getY() + 50);

        boolean lacked = ClientUtil.lacksSkill(skill.skill);
        skillInfoPanel.stateLabel.value = lacked ? "Skill not learned" : "Skill learned";
        skillInfoPanel.stateLabel.color = lacked ? 0XFFFF0000 : 0xFFFFFFFF;
        skillInfoPanel.stateLabel.setWidth(Minecraft.getInstance().font.width(skillInfoPanel.stateLabel.value));
        skillInfoPanel.stateLabel.setX((float) AbilityDeveloperScreen.this.width / 2 - skillInfoPanel.stateLabel.getWidth() / 2);
        skillInfoPanel.stateLabel.setY(skillInfoPanel.nameLabel.getY() + 12);

        skillInfoPanel.learnButton.setEnabled(lacked);
        skillInfoPanel.learnButton.setVisible(lacked);

        skillInfoPanel.depPanel.clearChildren();
        skillInfoPanel.depPanel.setY(skillInfoPanel.stateLabel.getY() + 10);
        if (lacked) {
            float x = 0;
            LabelWidget labelWidget = new LabelWidget("Dep.", x, 3.5f);
            x += labelWidget.getWidth();
            skillInfoPanel.depPanel.addChild("label_name", labelWidget);
            if (skill.dependencies.isEmpty()) {
                LabelWidget empty = new LabelWidget("Empty", x, 3.5f);
                skillInfoPanel.depPanel.addChild("label_empty", empty);
                x += empty.getWidth();
            }
            for (SkillInfo dependency : skill.dependencies) {
                String name = "dep_skill_icon_" + skill.skill.name;
                ImageWidget icon = new ImageWidget(x, 4, 8, 8, RenderUtil.getPositionTexRenderType(name, dependency.texture, false));
                skillInfoPanel.depPanel.addChild(name, icon);
                x += 12;
            }
            skillInfoPanel.depPanel.setWidth(x);
        }
        skillInfoPanel.depPanel.setX((float) AbilityDeveloperScreen.this.width / 2 - skillInfoPanel.depPanel.getWidth() / 2);

        skillInfoPanel.learnButton.setX((float) AbilityDeveloperScreen.this.width / 2 - skillInfoPanel.learnButton.getWidth() / 2);
        skillInfoPanel.learnButton.setY(skillInfoPanel.depPanel.getY() + 18);

        skillInfoPanel.energyLabel.value = !lacked ? "" : "%d AF".formatted(skill.energy);
        skillInfoPanel.energyLabel.setWidth(Minecraft.getInstance().font.width(skillInfoPanel.energyLabel.value));
        skillInfoPanel.energyLabel.setX((float) AbilityDeveloperScreen.this.width / 2 - skillInfoPanel.energyLabel.getWidth() * skillInfoPanel.energyLabel.scale / 2);
        skillInfoPanel.energyLabel.setY(skillInfoPanel.learnButton.getY() + 18);
    }

    @Override
    public SmoothScrollPanelWidget getNodeList() {
        return nodeListPanel;
    }

    @Override
    public PanelWidget getWirelessPanel() {
        return wirelessPanel;
    }

    @Override
    public String getConnectedNodeName() {
        return currentlyConnectedNodeName;
    }

    @Override
    public void setConnectedNodeName(String connectedNodeName) {
        this.currentlyConnectedNodeName = connectedNodeName;
    }

    @Override
    public BlockPos getPosition() {
        return mainPos;
    }

    final class SkillInfoPanel extends PanelWidget {
        SkillInfo skillInfo;
        final BackgroundWidget background = new BackgroundWidget(AbilityDeveloperScreen.this);
        final ImageWidget iconBack = new ImageWidget(0, 0, 65, 65, RENDER_TYPE_PANEL_RIGHT_SKILL_ICON_BACK);
        final ImageWidget icon = new ImageWidget(0, 0, 32.5f, 32.5f, null);
        final LabelWidget nameLabel = new LabelWidget("", 0, 0);
        final LabelWidget stateLabel = new LabelWidget("", 0, 0);
        final PanelWidget depPanel = new PanelWidget(0, 0, 0, 16);
        final ImageButtonWidget learnButton = new ImageButtonWidget(0, 0, 32, 16, RENDER_TYPE_BUTTON, new Runnable() {
            @Override
            public void run() {
                if (skillInfo == null) return;
                FutureManagerClient.<Boolean>sendFuturePacket(Packets.C2S_LEARN_SKILL, aBoolean -> {
                    if (aBoolean) {
                        init();
                    }
                }, skillInfo.skill.name, mainPos);
            }
        });
        final LabelWidget energyLabel = new LabelWidget("", 0, 0);

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
            energyLabel.scale = 0.75f;
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
        public final List<SkillInfo> dependencies = new ArrayList<>();

        @SuppressWarnings("SuspiciousNameCombination")
        SkillWidget(SkillInfo skillInfo) {
            super(skillInfo.x, skillInfo.y, PANEL_RIGHT_SKILL_SIZE, PANEL_RIGHT_SKILL_SIZE,
                    RENDER_TYPE_SKILL_ICON.apply(skillInfo.skill.name, skillInfo.texture),
                    () -> AbilityDeveloperScreen.this.openSkillViewPanel(skillInfo));
            this.dependencies.addAll(skillInfo.dependencies);
        }

        @Override
        public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
            guiGraphics.pose().pushPose();
            xOffset = -(dynamicFollow ? ((float) mouseX / AbilityDeveloperScreen.this.width) : 0f);
            yOffset = -(dynamicFollow ? ((float) mouseY / AbilityDeveloperScreen.this.height) : 0f);
            Matrix4f root = guiGraphics.pose().last().pose();
            root.translate(xOffset, yOffset, 0f);
            targetScale = isHovered() ? 1.25f : 1.0f;
            currentScale = MathUtil.lerpStartEndFactor(currentScale, targetScale, MathUtil.animationFactor(0.25f, partialTick));
            widthScale = currentScale;
            heightScale = currentScale;
            RenderType oldType = renderType;
            renderType = RENDER_TYPE_ELEMENT_LINE;
            VertexConsumer buf = guiGraphics.bufferSource().getBuffer(renderType);
            final float thickness = 5f;
            final float cX = x + PANEL_RIGHT_SKILL_SIZE / 2f;
            final float cY = y + PANEL_RIGHT_SKILL_SIZE / 2f;
            for (SkillInfo dep : dependencies) {
                guiGraphics.pose().pushPose();
                Matrix4f m = guiGraphics.pose().last().pose();
                float dX = dep.x + PANEL_RIGHT_SKILL_SIZE / 2f;
                float dY = dep.y + PANEL_RIGHT_SKILL_SIZE / 2f;
                float dx = dX - cX, dy = dY - cY;
                float rawLen = (float) Math.hypot(dx, dy);
                float length = rawLen - PANEL_RIGHT_SKILL_SIZE + thickness;
                float angle = (float) Math.atan2(dy, dx);
                m.translate(cX, cY, getZ());
                m.rotateZ(angle);
                m.translate(PANEL_RIGHT_SKILL_SIZE / 2f - thickness / 2, -thickness / 2f, 0f);
                m.scale(length, thickness, 1f);
                buf.vertex(m, 0f, 0f, 0f).color(1, 1, 1, 1f).uv(0f, 0f).endVertex();
                buf.vertex(m, 0f, 1f, 0f).color(1, 1, 1, 1f).uv(0f, 1f).endVertex();
                buf.vertex(m, 1f, 1f, 0f).color(1, 1, 1, 1f).uv(1f, 1f).endVertex();
                buf.vertex(m, 1f, 0f, 0f).color(1, 1, 1, 1f).uv(1f, 0f).endVertex();
                guiGraphics.pose().popPose();
            }
            renderType = RENDER_TYPE_PANEL_RIGHT_SKILL_ICON_BACK;
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            float oRed = red;
            float oGreen = green;
            float oBlue = blue;
            red = 1f;
            green = 1f;
            blue = 1f;
            renderType = oldType;
            widthScale *= 0.5f;
            heightScale *= 0.5f;
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            red = oRed;
            green = oGreen;
            blue = oBlue;
            widthScale /= 0.5f;
            heightScale /= 0.5f;
            renderType = DEBUG;
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            renderType = oldType;
            guiGraphics.pose().popPose();
        }

        @Override
        public float getAbsoluteY() {
            return super.getAbsoluteY() + yOffset;
        }

        @Override
        public float getAbsoluteX() {
            return super.getAbsoluteX() + xOffset;
        }
    }

    public record SkillInfo(Skill skill, List<SkillInfo> dependencies, ResourceLocation texture, float x, float y,
                            int energy) {
    }
}