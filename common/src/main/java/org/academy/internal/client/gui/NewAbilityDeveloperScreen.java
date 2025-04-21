package org.academy.internal.client.gui;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.gui.framework.CGuiScreen;
import org.academy.api.client.gui.framework.Widget;
import org.academy.api.client.gui.widgets.*;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.Packets;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.ability.builtin.electromaster.skills.ArcGenerate;
import org.academy.internal.common.ability.builtin.electromaster.skills.Railgun;
import org.academy.internal.common.ability.builtin.level0.Level0;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static org.academy.internal.client.gui.NewAbilityDeveloperScreen.RenderTypes.*;
import static org.academy.internal.client.gui.NewAbilityDeveloperScreen.Textures.*;

public class NewAbilityDeveloperScreen extends CGuiScreen {
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
    public static final float PANEL_WIRELESS_WIDTH = 176f;
    public static final String PANEL_WIRELESS_NAME = "panel_wireless";

    public NewAbilityDeveloperScreen(@NotNull BlockPos mainPos) {
        this.mainPos = mainPos;
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(mainPos)
                instanceof AbilityDeveloperBlockEntity entity) {
            this.abilityDeveloperBlockEntity = entity;
        } else {
            Minecraft.getInstance().setScreen(null);
        }
        abilityDeveloperBlockEntity.setOpen(true);
    }

    public static void initPacket() {
        NetworkSystemClient.registerS2CPacketHandler(
                Packets.S2C_OPEN_ABILITY_DEVELOPER_SCREEN,
                (listener, packet) -> {
                    BlockPos mainPos = packet.friendlyByteBuf.readBlockPos();
                    Minecraft.getInstance().setScreen(new NewAbilityDeveloperScreen(mainPos));
                }
        );
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
        {
            PanelWidget mainPanel = new PanelWidget(
                    width / 2f - PANEL_MAIN_WIDTH / 2f, height / 2f - PANEL_MAIN_HEIGHT / 2f,
                    PANEL_MAIN_WIDTH, PANEL_MAIN_HEIGHT
            );
            rootContainer.addChild("panel_main", mainPanel);
            {
                PanelWidget leftPanel = getLeftPanel();
                mainPanel.addChild("panel_left", leftPanel);

                PanelWidget rightPanel = new PanelWidget(PANEL_LEFT_WIDTH, 0, PANEL_RIGHT_WIDTH, PANEL_MAIN_HEIGHT);
                mainPanel.addChild("panel_right", rightPanel);
                {
                    ImageWidget rightPanelBack = new ImageWidget(0, 0, PANEL_RIGHT_WIDTH, PANEL_MAIN_HEIGHT,
                            RENDER_TYPE_PANEL_RIGHT_BACK
                    );
                    rightPanel.addChild("panel_right_back", rightPanelBack);

                    ImageWidget rightPanelInfo = new ImageWidget(0, 0, PANEL_RIGHT_WIDTH, PANEL_MAIN_HEIGHT,
                            RENDER_TYPE_SKILL_PANEL_INFO
                    );
                    rightPanel.addChild("panel_right_info", rightPanelInfo);

                    boolean bootFailed = AbilitySystemClient.getCategory() == Level0.INSTANCE;

                    PanelWidget bootPanel = new PanelWidget(0, 0, PANEL_RIGHT_WIDTH, PANEL_MAIN_HEIGHT);
                    bootPanel.setEnabled(bootFailed);
                    bootPanel.setVisible(bootFailed);
                    rightPanel.addChild("panel_boot", bootPanel);
                    {
                        TypewriterLabelWidget welcome =
                                new TypewriterLabelWidget("Welcome to Academy OS, Ver 0.0.1",
                                        PANEL_RIGHT_SKILL_BACK_X, PANEL_RIGHT_SKILL_BACK_Y);
                        bootPanel.addChild("welcome", welcome);

                        String userName = "None";
                        if (Minecraft.getInstance().player != null) {
                            userName = Minecraft.getInstance().player.getName().getString();
                        }
                        TypewriterLabelWidget userDetected =
                                new TypewriterLabelWidget(String.format("User %s detected, ", userName),
                                        welcome.getX(), welcome.getY() + welcome.getHeight());
                        bootPanel.addChild("user_detected", userDetected);

                        TypewriterLabelWidget systemBooting =
                                new TypewriterLabelWidget("System booting...",
                                        userDetected.getX(), userDetected.getY() + userDetected.getHeight());
                        bootPanel.addChild("system_booting", systemBooting);

                        TypewriterLabelWidget loadingText =
                                new TypewriterLabelWidget("Loading: ",
                                        systemBooting.getX(), systemBooting.getY() + systemBooting.getHeight());
                        bootPanel.addChild("loading_text", loadingText);

                        BracketProgressBarWidget loadingBar =
                                new BracketProgressBarWidget('#', 10,
                                        loadingText.getX(), loadingText.getY() + loadingText.getHeight());
                        loadingBar.updateInterval = 2;
                        bootPanel.addChild("loading_bar", loadingBar);

                        TypewriterLabelWidget invalid =
                                new TypewriterLabelWidget("Invalid ability category, boot failed",
                                        loadingBar.getX(), loadingBar.getY() + loadingBar.getHeight());
                        bootPanel.addChild("invalid", invalid);

                        welcome.afterFinished = userDetected::start;
                        userDetected.afterFinished = systemBooting::start;
                        systemBooting.afterFinished = loadingText::start;
                        loadingText.afterFinished = loadingBar::start;
                        loadingBar.afterFinished = invalid::start;
                        invalid.afterFinished = () -> {
                            UnlockSliderWidget textBox = new UnlockSliderWidget(invalid.getX(), invalid.getY() + invalid.getHeight(), 100, 16);
                            bootPanel.addChild("text_box", textBox);
                        };

                        welcome.start();
                    }

                    PanelWidget skillPanel = new PanelWidget(0, 0, PANEL_RIGHT_WIDTH, PANEL_MAIN_HEIGHT);
                    skillPanel.setEnabled(!bootFailed);
                    skillPanel.setVisible(!bootFailed);
                    rightPanel.addChild("panel_skill", skillPanel);
                    {
                        ParallaxImageWidget parallaxImageWidget = new ParallaxImageWidget(
                                PANEL_RIGHT_SKILL_BACK_X, PANEL_RIGHT_SKILL_BACK_Y,
                                PANEL_RIGHT_SKILL_BACK_WIDTH, PANEL_RIGHT_SKILL_BACK_HEIGHT,
                                RENDER_TYPE_SKILL_PANEL_BACK, width, height
                        );
                        skillPanel.addChild("skill_area_back", parallaxImageWidget);


                        SkillInfo skillInfo = new SkillInfo(ArcGenerate.INSTANCE, new ArrayList<>(),
                                new ResourceLocation(AcademyCraft.MOD_ID,
                                        "textures/ability/electromaster/skill/arc_generate/icon.png"),
                                50, 55);
                        SKILL_INFOS.add(skillInfo);
                        SKILL_INFOS.add(new SkillInfo(Railgun.INSTANCE, List.of(skillInfo),
                                new ResourceLocation(AcademyCraft.MOD_ID,
                                        "textures/ability/electromaster/skill/railgun/icon.png"),
                                100, 45));


                        for (SkillInfo skill : SKILL_INFOS) {
                            SkillWidget skillWidget = new SkillWidget(skill);
                            skillPanel.addChild(skill.skill.name, skillWidget);
                        }
                    }
                }
            }
        }

        {
            PanelWidget wirelessPanel = new PanelWidget(
                    0, 0, width, height
            );
            rootContainer.addChild(PANEL_WIRELESS_NAME, wirelessPanel);
            wirelessPanel.setVisible(false);
            wirelessPanel.setEnabled(false);
            {
                BackgroundWidget backgroundWidget = new BackgroundWidget(this);
                wirelessPanel.addChild("back", backgroundWidget);
                PanelWidget viewListPanel = new PanelWidget(
                        width / 2f - PANEL_WIRELESS_WIDTH / 2f, height / 2f - PANEL_MAIN_HEIGHT / 2f,
                        PANEL_WIRELESS_WIDTH, PANEL_MAIN_HEIGHT);
                wirelessPanel.addChild("view", viewListPanel);
                {
                    ImageWidget back = new ImageWidget(0, 0, PANEL_WIRELESS_WIDTH, PANEL_MAIN_HEIGHT, RENDER_TYPE_ELEMENT_BACK_DARK);
                    viewListPanel.addChild("back", back);
                    ImageWidget icon = new ImageWidget(10, 10, 16, 16, RENDER_TYPE_WIRELESS_PANEL_VIEW_ICON);
                    viewListPanel.addChild("icon", icon);
                }
            }
        }
    }

    private static @NotNull PanelWidget getLeftPanel() {
        PanelWidget leftPanel = new PanelWidget(0, 0, PANEL_LEFT_WIDTH, PANEL_MAIN_HEIGHT);
        {
            ImageWidget leftPanelBackMiddle = new ImageWidget(0, 0, PANEL_LEFT_WIDTH, PANEL_MAIN_HEIGHT,
                    RENDER_TYPE_PANEL_LEFT_BACK_MIDDLE
            );
            leftPanel.addChild("left_panel_back_middle", leftPanelBackMiddle);
            ImageWidget leftPanelBackBottom = new ImageWidget(0, 0, PANEL_LEFT_WIDTH, PANEL_MAIN_HEIGHT,
                    RENDER_TYPE_PANEL_LEFT_BACK_BOTTOM
            );
            leftPanel.addChild("left_panel_back_bottom", leftPanelBackBottom);
            ImageWidget leftPanelBackTop = new ImageWidget(0, 0, PANEL_LEFT_WIDTH, PANEL_MAIN_HEIGHT,
                    RENDER_TYPE_PANEL_LEFT_BACK_TOP
            );
            leftPanel.addChild("left_panel_back_top", leftPanelBackTop);
        }
        return leftPanel;
    }

    static final class Textures {
        static final ResourceLocation PANEL_LEFT_BACK_TOP_TEXTURE = new ResourceLocation(AcademyCraft.MOD_ID,
                "textures/gui/developer/ui_developerleft.png");
        static final ResourceLocation TEXTURE_PANEL_LEFT_BACK_MIDDLE = new ResourceLocation(AcademyCraft.MOD_ID,
                "textures/gui/developer/parent_background_developermachine.png");
        static final ResourceLocation TEXTURE_PANEL_LEFT_BACK_BOTTOM = new ResourceLocation(AcademyCraft.MOD_ID,
                "textures/gui/developer/parent_background_developerleft.png");
        static final ResourceLocation TEXTURE_PANEL_RIGHT_BACK = new ResourceLocation(AcademyCraft.MOD_ID,
                "textures/gui/developer/parent_background_developerright.png");
        static final ResourceLocation TEXTURE_PANEL_RIGHT_INFO = new ResourceLocation(AcademyCraft.MOD_ID,
                "textures/gui/developer/ui_developerright.png");
        static final ResourceLocation TEXTURE_PANEL_RIGHT_SKILL_BACK = new ResourceLocation(AcademyCraft.MOD_ID,
                "textures/gui/developer/skill_panel_back.png");
        static final ResourceLocation TEXTURE_PANEL_RIGHT_SKILL_ICON_BACK = new ResourceLocation(AcademyCraft.MOD_ID,
                "textures/gui/developer/skill_back.png"
        );
        static final ResourceLocation TEXTURE_ELEMENT_LINE = new ResourceLocation(AcademyCraft.MOD_ID,
                "textures/gui/element/line.png"
        );
        static final ResourceLocation TEXTURE_ELEMENT_BACK_DARK = new ResourceLocation(AcademyCraft.MOD_ID,
                "textures/gui/element/element_background_dark.png"
        );
        static final ResourceLocation TEXTURE_WIRELESS_PANEL_VIEW_ICON = new ResourceLocation(AcademyCraft.MOD_ID,
                "textures/gui/icon/icon_tonode.png"
        );
    }

    static final class RenderTypes {
        static final RenderType RENDER_TYPE_PANEL_LEFT_BACK_TOP = RenderUtil.getPositionTexRenderType(
                "panel_left_back_top", PANEL_LEFT_BACK_TOP_TEXTURE, false
        );
        static final RenderType RENDER_TYPE_PANEL_LEFT_BACK_MIDDLE = RenderUtil.getPositionTexRenderType(
                "panel_left_back_middle", TEXTURE_PANEL_LEFT_BACK_MIDDLE, false
        );
        static final RenderType RENDER_TYPE_PANEL_LEFT_BACK_BOTTOM = RenderUtil.getPositionTexRenderType(
                "panel_left_back_bottom", TEXTURE_PANEL_LEFT_BACK_BOTTOM, false
        );
        static final RenderType RENDER_TYPE_SKILL_PANEL_BACK = RenderUtil.getPositionTexRenderType(
                "skill_panel_back", TEXTURE_PANEL_RIGHT_SKILL_BACK, true
        );
        static final RenderType RENDER_TYPE_SKILL_PANEL_INFO = RenderUtil.getPositionTexRenderType(
                "skill_panel_info", TEXTURE_PANEL_RIGHT_INFO, false
        );
        static final RenderType RENDER_TYPE_PANEL_RIGHT_BACK = RenderUtil.getPositionTexRenderType(
                "panel_right_back", TEXTURE_PANEL_RIGHT_BACK, false
        );
        static final BiFunction<String, ResourceLocation, RenderType> RENDER_TYPE_SKILL_ICON =
                (string, resourceLocation) ->
                        RenderUtil.getPositionTexRenderType(string, resourceLocation, false);
        static final RenderType RENDER_TYPE_PANEL_RIGHT_SKILL_ICON_BACK = RenderUtil.getPositionTexRenderType(
                "panel_right_skill_icon_back", TEXTURE_PANEL_RIGHT_SKILL_ICON_BACK, false
        );
        static final RenderType DEBUG = new RenderType.CompositeRenderType(
                "debug_aaa",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderUtil.RenderStates.DEBUG)
                        .setCullState(RenderUtil.RenderStates.NO_CULL)
                        .setWriteMaskState(RenderUtil.RenderStates.COLOR_WRITE)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false));
        static final RenderType RENDER_TYPE_ELEMENT_LINE = RenderUtil.getPositionTexRenderType(
                "element_line", TEXTURE_ELEMENT_LINE, true
        );
        static final RenderType RENDER_TYPE_ELEMENT_BACK_DARK = RenderUtil.getPositionTexRenderType(
                "element_back_dark", TEXTURE_ELEMENT_BACK_DARK, true
        );
        static final RenderType RENDER_TYPE_WIRELESS_PANEL_VIEW_ICON = RenderUtil.getPositionTexRenderType(
                "wireless_panel_view_icon", TEXTURE_WIRELESS_PANEL_VIEW_ICON, true
        );
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
                    imageButtonWidget -> NewAbilityDeveloperScreen.this.openSkillViewPanel(skillInfo.skill));
            this.dependencies.addAll(skillInfo.dependencies);
        }

        @Override
        public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
            guiGraphics.pose().pushPose();
            xOffset = -(dynamicFollow ? ((float) mouseX / NewAbilityDeveloperScreen.this.width) : 0f);
            yOffset = -(dynamicFollow ? ((float) mouseY / NewAbilityDeveloperScreen.this.height) : 0f);
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

                buf.vertex(m, 0f, 0f, 0f).uv(0f, 0f).endVertex();
                buf.vertex(m, 0f, 1f, 0f).uv(0f, 1f).endVertex();
                buf.vertex(m, 1f, 1f, 0f).uv(1f, 1f).endVertex();
                buf.vertex(m, 1f, 0f, 0f).uv(1f, 0f).endVertex();

                guiGraphics.pose().popPose();
            }

            renderType = RENDER_TYPE_PANEL_RIGHT_SKILL_ICON_BACK;
            super.render(guiGraphics, mouseX, mouseY, partialTick);

            renderType = oldType;
            widthScale *= 0.5f;
            heightScale *= 0.5f;
            super.render(guiGraphics, mouseX, mouseY, partialTick);
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

    void openSkillViewPanel(Skill skill) {
        Widget widget = rootContainer.getChildren().get(PANEL_WIRELESS_NAME);
        widget.setVisible(true);
        widget.setEnabled(true);
    }

    void closeSkillViewPanel() {
        Widget widget = rootContainer.getChildren().get(PANEL_WIRELESS_NAME);
        widget.setVisible(false);
        widget.setEnabled(false);
    }

    public record SkillInfo(Skill skill, List<SkillInfo> dependencies, ResourceLocation texture, float x, float y) {
    }
}