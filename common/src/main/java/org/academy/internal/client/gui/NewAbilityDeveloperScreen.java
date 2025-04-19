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
import org.academy.api.client.gui.framework.CGuiScreen;
import org.academy.api.client.gui.widgets.ImageButtonWidget;
import org.academy.api.client.gui.widgets.ImageWidget;
import org.academy.api.client.gui.widgets.PanelWidget;
import org.academy.api.client.gui.widgets.ParallaxImageWidget;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.ability.builtin.electromaster.skills.Railgun;
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
    public static final List<SkillInfo> SKILL_INFOS = new ArrayList<>();
    public static final float PANEL_MAIN_WIDTH = 400;
    public static final float PANEL_MAIN_HEIGHT = 187;
    public static final float PANEL_LEFT_WIDTH = 108.5f;
    public static final float PANEL_RIGHT_WIDTH = 278;
    public static final float PANEL_RIGHT_SKILL_BACK_X = 11;
    public static final float PANEL_RIGHT_SKILL_BACK_Y = 17.5f;
    public static final float PANEL_RIGHT_SKILL_BACK_WIDTH = 256;
    public static final float PANEL_RIGHT_SKILL_BACK_HEIGHT = 139.5f;

    public NewAbilityDeveloperScreen(BlockPos mainPos) {
        this.mainPos = mainPos;
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(mainPos)
                instanceof AbilityDeveloperBlockEntity entity) {
            this.abilityDeveloperBlockEntity = entity;
        } else {
            Minecraft.getInstance().setScreen(null);
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
        if (abilityDeveloperBlockEntity != null) {
            abilityDeveloperBlockEntity.setOpen(false);
        }
    }

    @Override
    protected void onInit() {
        PanelWidget mainPanel = new PanelWidget(
                width / 2f - PANEL_MAIN_WIDTH / 2f, height / 2f - PANEL_MAIN_HEIGHT / 2f,
                PANEL_MAIN_WIDTH, PANEL_MAIN_HEIGHT
        );
        rootContainer.addChild("panel_main", mainPanel);

        PanelWidget leftPanel = getLeftPanel();
        mainPanel.addChild("panel_left", leftPanel);

        PanelWidget rightPanel = new PanelWidget(PANEL_LEFT_WIDTH, 0, PANEL_RIGHT_WIDTH, PANEL_MAIN_HEIGHT);
        {
            ImageWidget rightPanelBack = new ImageWidget(0, 0, PANEL_RIGHT_WIDTH, PANEL_MAIN_HEIGHT,
                    RENDER_TYPE_PANEL_RIGHT_BACK
            );
            rightPanel.addChild("right_panel_back", rightPanelBack);

            ImageWidget rightPanelInfo = new ImageWidget(0, 0, PANEL_RIGHT_WIDTH, PANEL_MAIN_HEIGHT,
                    RENDER_TYPE_SKILL_PANEL_INFO
            );
            rightPanel.addChild("right_panel_info", rightPanelInfo);
            ParallaxImageWidget parallaxImageWidget = new ParallaxImageWidget(
                    PANEL_RIGHT_SKILL_BACK_X, PANEL_RIGHT_SKILL_BACK_Y,
                    PANEL_RIGHT_SKILL_BACK_WIDTH, PANEL_RIGHT_SKILL_BACK_HEIGHT,
                    RENDER_TYPE_SKILL_PANEL_BACK, width, height
            );
            rightPanel.addChild("skill_area_back", parallaxImageWidget);
            SKILL_INFOS
                    .add(new SkillInfo(Railgun.INSTANCE, AbilityDeveloperScreen.TEXTURE_BUTTON_BACK, 50, 35));
            for (SkillInfo skill : SKILL_INFOS) {
                SkillWidget skillWidget = new SkillWidget(skill);
                rightPanel.addChild("skill_area", skillWidget);
            }
        }
        mainPanel.addChild("panel_right", rightPanel);
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
                "skill_panel_info", TEXTURE_PANEL_RIGHT_INFO, true
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
                        .setDepthTestState(RenderUtil.RenderStates.NO_DEPTH_TEST)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false));
    }

    final class SkillWidget extends ImageButtonWidget {
        public float targetScale = 1.0f;
        public float currentScale = 1.0f;
        public boolean dynamicFollow = true;
        public float xOffset, yOffset;

        SkillWidget(SkillInfo skillInfo) {
            super(skillInfo.x, skillInfo.y, 16, 16,
                    RENDER_TYPE_SKILL_ICON.apply(skillInfo.skill.name, skillInfo.texture),
                    imageButtonWidget -> onPress());
        }

        @Override
        public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTicks) {
            guiGraphics.pose().pushPose();
            xOffset = -(dynamicFollow ? ((float) mouseX / getX()) : 0) / 2;
            yOffset = -(dynamicFollow ? ((float) mouseY / getY()) : 0) / 2;
            Matrix4f matrix4f = guiGraphics.pose().last().pose();
            matrix4f.translate(xOffset, yOffset, 0);
            if (hovered) {
                targetScale = 1.25f;
            } else {
                targetScale = 1.0f;
            }
            currentScale = MathUtil.lerpStartEndFactor(currentScale, targetScale, partialTicks);
            widthScale = currentScale;
            heightScale = currentScale;
            RenderType renderIcon = renderType;
            renderType = RENDER_TYPE_PANEL_RIGHT_SKILL_ICON_BACK;
            super.render(guiGraphics, mouseX, mouseY, partialTicks);
            renderType = renderIcon;
            super.render(guiGraphics, mouseX, mouseY, partialTicks);

            VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(DEBUG);
            vertexConsumer.vertex(0.0f, 0.0f, 0.0f)
                    .uv(0.0f, 1.0f)
                    .endVertex();

            vertexConsumer.vertex(100.0f, 0.0f, 0.0f)
                    .uv(1.0f, 1.0f)
                    .endVertex();

            vertexConsumer.vertex(100.0f, 100.0f, 0.0f)
                    .uv(1.0f, 0.0f)
                    .endVertex();
            vertexConsumer.vertex(0.0f, 100.0f, 0.0f)
                    .uv(0.0f, 0.0f)
                    .endVertex();

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

        static void onPress() {
        }
    }

    public record SkillInfo(Skill skill, ResourceLocation texture, float x, float y) {
    }
}