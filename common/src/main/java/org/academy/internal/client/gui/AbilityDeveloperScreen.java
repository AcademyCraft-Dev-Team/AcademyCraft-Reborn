package org.academy.internal.client.gui;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.Tickable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.util.MathUtil;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class AbilityDeveloperScreen extends Screen {
    public static final float MAIN_PANEL_WIDTH = 400;
    public static final float MAIN_PANEL__HEIGHT = 187;
    public static final float PANEL_RIGHT_WIDTH = 278;
    public static final float PANEL_RIGHT_ZONE_LEFT = MAIN_PANEL_WIDTH - PANEL_RIGHT_WIDTH;
    public static final float PANEL_LEFT_WIDTH = 108.5f;
    public static final float SKILL_PANEL_WIDTH = 257;
    public static final float SKILL_PANEL_HEIGHT = 139;
    public static final float SKILL_PANEL_LEFT_ZONE = (PANEL_RIGHT_WIDTH - SKILL_PANEL_WIDTH) / 2;
    public static final float SKILL_PANEL_TOP_ZONE = 17;
    public BlockPos mainPos;
    public List<Skill> skillList = new ArrayList<>();

    public AbilityDeveloperScreen(BlockPos mainPos) {
        super(Component.empty());
        this.mainPos = mainPos;
    }

    @Override
    public void tick() {
        super.tick();
        for (Skill skill : skillList) {
            skill.tick();
        }
    }

    @Override
    protected void init() {
        super.init();
        addRenderableOnly(new LeftPanelBackground());
        addRenderableOnly(new RightPanelBackground());
        Skill skillA = new Skill(-20, 20, new ResourceLocation(AcademyCraft.MOD_ID, "textures/hud/ability/electromaster/icon_overlay.png"));
        Skill skillB = new Skill(80, 20, new ResourceLocation(AcademyCraft.MOD_ID, "textures/hud/ability/teleport/icon_overlay.png"));
        skillB.hasFathers = true;
        skillB.fathers.add(skillA);
        addSkill(skillA);
        addSkill(skillB);
    }

    public void addSkill(@NotNull Skill skill) {
        skillList.add(skill);
        addRenderableWidget(skill);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    public static class RightPanelBackground implements Renderable {
        public static final ResourceLocation TEXTURE_BACK = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/parent_background_developerright.png");
        public static final RenderType RENDER_TYPE_BACK = new RenderType.CompositeRenderType(
                "developer_right_panel_back",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TEXTURE_BACK,
                                true,
                                false
                        ))
                        .setShaderState(RenderUtil.RenderStates.POSITION_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );
        public static final ResourceLocation TEXTURE_INFO = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/ui_developerright.png");
        public static final RenderType RENDER_TYPE_INFO = new RenderType.CompositeRenderType(
                "developer_right_panel_info",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TEXTURE_INFO,
                                false,
                                false
                        ))
                        .setShaderState(RenderUtil.RenderStates.POSITION_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );
        public static final ResourceLocation TEXTURE_SKILL_PANEL_BACK = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/skill_panel_back.png");
        public static final RenderType RENDER_TYPE_SKILL_PANEL_BACK = new RenderType.CompositeRenderType(
                "developer_skill_panel_back",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TEXTURE_SKILL_PANEL_BACK,
                                true,
                                false
                        ))
                        .setShaderState(RenderUtil.RenderStates.POSITION_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );

        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            final int screenWidth = guiGraphics.guiWidth();
            final int screenHeight = guiGraphics.guiHeight();

            final float left = getMainPanelLeft(screenWidth) + PANEL_RIGHT_ZONE_LEFT;
            final float right = getMainPanelRight(screenWidth);
            final float top = getMainPaneTop(screenHeight);
            final float bottom = getMainPanelBottom(screenHeight);

            VertexConsumer vertexConsumerBack = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_BACK);
            vertexConsumerBack.vertex(left, top, 0).uv(0, 0).endVertex();
            vertexConsumerBack.vertex(left, bottom, 0).uv(0, 1).endVertex();
            vertexConsumerBack.vertex(right, bottom, 0).uv(1, 1).endVertex();
            vertexConsumerBack.vertex(right, top, 0).uv(1, 0).endVertex();

            VertexConsumer vertexConsumerInfo = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_INFO);
            vertexConsumerInfo.vertex(left, top, 0).uv(0, 0).endVertex();
            vertexConsumerInfo.vertex(left, bottom, 0).uv(0, 1).endVertex();
            vertexConsumerInfo.vertex(right, bottom, 0).uv(1, 1).endVertex();
            vertexConsumerInfo.vertex(right, top, 0).uv(1, 0).endVertex();

            VertexConsumer vertexConsumerSkillPanelBack = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_SKILL_PANEL_BACK);

            final float skillPanelBackLeft = left + SKILL_PANEL_LEFT_ZONE;
            final float skillPanelBackRight = skillPanelBackLeft + SKILL_PANEL_WIDTH;
            final float skillPanelBackTop = top + SKILL_PANEL_TOP_ZONE;
            final float skillPanelBackBottom = skillPanelBackTop + SKILL_PANEL_HEIGHT;

            final float uOffset = ((float) mouseX / screenWidth) / 20;
            final float vOffset = ((float) mouseY / screenHeight) / 20;

            vertexConsumerSkillPanelBack.vertex(skillPanelBackLeft, skillPanelBackTop, 0).uv(uOffset, vOffset).endVertex();
            vertexConsumerSkillPanelBack.vertex(skillPanelBackLeft, skillPanelBackBottom, 0).uv(uOffset, vOffset + 0.9f).endVertex();
            vertexConsumerSkillPanelBack.vertex(skillPanelBackRight, skillPanelBackBottom, 0).uv(uOffset + 0.9f, vOffset + 0.9f).endVertex();
            vertexConsumerSkillPanelBack.vertex(skillPanelBackRight, skillPanelBackTop, 0).uv(uOffset + 0.9f, vOffset).endVertex();
        }
    }

    public static class LeftPanelBackground implements Renderable {
        public static final ResourceLocation TEXTURE_TOP = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/ui_developerleft.png");
        public static final RenderType RENDER_TYPE_TOP = new RenderType.CompositeRenderType(
                "developer_left_panel_top",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TEXTURE_TOP,
                                false,
                                false
                        ))
                        .setShaderState(RenderUtil.RenderStates.POSITION_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );
        public static final ResourceLocation TEXTURE_MIDDLE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/parent_background_developerleft.png");
        public static final RenderType RENDER_TYPE_MIDDLE = new RenderType.CompositeRenderType(
                "developer_left_panel_middle",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TEXTURE_MIDDLE,
                                true,
                                false
                        ))
                        .setShaderState(RenderUtil.RenderStates.POSITION_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );
        public static final ResourceLocation TEXTURE_BOTTOM = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/parent_background_developermachine.png");
        public static final RenderType RENDER_TYPE_BOTTOM = new RenderType.CompositeRenderType(
                "developer_left_panel_bottom",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TEXTURE_BOTTOM,
                                true,
                                false
                        ))
                        .setShaderState(RenderUtil.RenderStates.POSITION_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );

        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            final int screenWidth = guiGraphics.guiWidth();
            final int screenHeight = guiGraphics.guiHeight();

            final float left = getMainPanelLeft(screenWidth);
            final float right = left + PANEL_LEFT_WIDTH;
            final float top = getMainPaneTop(screenHeight);
            final float bottom = getMainPanelBottom(screenHeight);

            VertexConsumer vertexConsumerBottom = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_BOTTOM);
            vertexConsumerBottom.vertex(left, top, 0).uv(0, 0).endVertex();
            vertexConsumerBottom.vertex(left, bottom, 0).uv(0, 1).endVertex();
            vertexConsumerBottom.vertex(right, bottom, 0).uv(1, 1).endVertex();
            vertexConsumerBottom.vertex(right, top, 0).uv(1, 0).endVertex();

            VertexConsumer vertexConsumerMiddle = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_MIDDLE);
            vertexConsumerMiddle.vertex(left, top, 0).uv(0, 0).endVertex();
            vertexConsumerMiddle.vertex(left, bottom, 0).uv(0, 1).endVertex();
            vertexConsumerMiddle.vertex(right, bottom, 0).uv(1, 1).endVertex();
            vertexConsumerMiddle.vertex(right, top, 0).uv(1, 0).endVertex();

            VertexConsumer vertexConsumerTop = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_TOP);
            vertexConsumerTop.vertex(left, top, 0).uv(0, 0).endVertex();
            vertexConsumerTop.vertex(left, bottom, 0).uv(0, 1).endVertex();
            vertexConsumerTop.vertex(right, bottom, 0).uv(1, 1).endVertex();
            vertexConsumerTop.vertex(right, top, 0).uv(1, 0).endVertex();
        }
    }

    public static class Skill implements Renderable, GuiEventListener, LayoutElement, NarratableEntry, Tickable {
        public static final ResourceLocation LINE_TEXTURE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/line.png");
        public static final RenderType RENDER_TYPE_LINE = new RenderType.CompositeRenderType(
                "developer_line",
                DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                LINE_TEXTURE,
                                true,
                                false
                        ))
                        .setCullState(RenderUtil.RenderStates.NO_CULL)
                        .setShaderState(RenderUtil.RenderStates.POSITION_COLOR_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );
        public static final ResourceLocation ICON_BACK = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/skill_back.png");
        public static final RenderType RENDER_TYPE_ICON_BACK = new RenderType.CompositeRenderType(
                "developer_skill_icon_back",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                ICON_BACK,
                                true,
                                false
                        ))
                        .setCullState(RenderUtil.RenderStates.NO_CULL)
                        .setShaderState(RenderUtil.RenderStates.POSITION_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );
        public static final Function<ResourceLocation, RenderType> RENDER_TYPE_SKILL_ICON = resourceLocation -> new RenderType.CompositeRenderType(
                "developer_skill",
                DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                resourceLocation,
                                false,
                                false
                        ))
                        .setCullState(RenderUtil.RenderStates.NO_CULL)
                        .setShaderState(RenderUtil.RenderStates.POSITION_COLOR_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );

        public static boolean dynamicFollow = true;
        public float size = 24f;
        public float offsetPosX, offsetPosY;
        public ResourceLocation texture;
        public boolean hasFathers = false;
        public final List<Skill> fathers = new ArrayList<>();
        public boolean focused;
        public float scale = 1.0f;
        public float targetScale = 1.0f;

        public Skill(float offsetPosX, float offsetPosY, @NotNull ResourceLocation texture) {
            this.offsetPosX = offsetPosX;
            this.offsetPosY = offsetPosY;
            this.texture = texture;
        }

        @Override
        public void tick() {
        }

        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            final int screenWidth = guiGraphics.guiWidth();
            final int screenHeight = guiGraphics.guiHeight();

            final float xOffset = (dynamicFollow ? ((float) mouseX / screenWidth) : 0) * -20 + offsetPosX;
            final float yOffset = (dynamicFollow ? ((float) mouseY / screenHeight) : 0) * -15 + offsetPosY;

            float xPos = xOffset + ((float) guiGraphics.guiWidth() / 2);
            float yPos = yOffset + ((float) guiGraphics.guiHeight() / 2);

            setFocused(isMouseInCircle(mouseX, mouseY, xPos, yPos, size / 2));
            targetScale = isFocused() ? 1.25f : 1.0f;

            scale = MathUtil.lerpStartEndFactor(scale, targetScale, partialTick);
            float renderSize = size * scale;
            yPos -= renderSize / 2;
            xPos -= renderSize / 2;
            Matrix4f matrix = new Matrix4f().translation(xPos, yPos, 0);
            if (hasFathers) {
                VertexConsumer vertexConsumerLines = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_LINE);
                for (Skill father : fathers) {
                    final float fatherRadius = father.size * father.scale / 2f;
                    float dx = offsetPosX - father.offsetPosX, dy = offsetPosY - father.offsetPosY;
                    float angleRad = (float) Math.atan2(dy, dx);
                    Matrix4f lineMatrix = new Matrix4f().mul(matrix).translate(0, renderSize / 2, 0).rotateZ(angleRad + 3.14f);
                    float distance = (float) Math.hypot(dx, dy) - fatherRadius - renderSize / 2f;
                    renderLine(vertexConsumerLines, lineMatrix, true, distance);
                }
            }

            matrix.scale(renderSize);

            VertexConsumer vertexConsumerBack = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_ICON_BACK);
            renderBackground(vertexConsumerBack, matrix);

            VertexConsumer vertexConsumerIcon = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_SKILL_ICON.apply(texture));
            renderIcon(vertexConsumerIcon, matrix, true);
        }

        public static void renderIcon(VertexConsumer vertexConsumer, Matrix4f matrix4f, boolean isLearned) {
            float alpha = isLearned ? 1.0f : 0.65f;
            // Left Top
            vertexConsumer.vertex(matrix4f, 0, 0, 2).color(1, 1, 1, alpha).uv(0, 0).endVertex();
            // Left Bottom
            vertexConsumer.vertex(matrix4f, 0, 1, 2).color(1, 1, 1, alpha).uv(0, 1).endVertex();
            // Right Bottom
            vertexConsumer.vertex(matrix4f, 1, 1, 2).color(1, 1, 1, alpha).uv(1, 1).endVertex();
            // Right Top
            vertexConsumer.vertex(matrix4f, 1, 0, 2).color(1, 1, 1, alpha).uv(1, 0).endVertex();
        }

        public static void renderBackground(VertexConsumer vertexConsumer, Matrix4f matrix4f) {
            // Left Top
            vertexConsumer.vertex(matrix4f, 0, 0, 1).uv(0, 0).endVertex();
            // Left Bottom
            vertexConsumer.vertex(matrix4f, 0, 1, 1).uv(0, 1).endVertex();
            // Right Bottom
            vertexConsumer.vertex(matrix4f, 1, 1, 1).uv(1, 1).endVertex();
            // Right Top
            vertexConsumer.vertex(matrix4f, 1, 0, 1).uv(1, 0).endVertex();
        }

        public static void renderLine(VertexConsumer vertexConsumer, Matrix4f matrix4f, boolean isLearned, float length) {
            float alpha = isLearned ? 1.0f : 0.65f;

            // Left Top
            vertexConsumer.vertex(matrix4f, 0, 0, 0).color(1, 1, 1, alpha).uv(0, 0).endVertex();
            // Left Bottom
            vertexConsumer.vertex(matrix4f, 0, 5, 0).color(1, 1, 1, alpha).uv(0, 1).endVertex();
            // Right Bottom
            vertexConsumer.vertex(matrix4f, length, 5, 0).color(1, 1, 1, alpha).uv(1, 1).endVertex();
            // Right Top
            vertexConsumer.vertex(matrix4f, length, 0, 0).color(1, 1, 1, alpha).uv(1, 0).endVertex();
        }

        @Override
        public void setX(int x) {
            this.offsetPosX = x;
        }

        @Override
        public void setY(int y) {
            this.offsetPosY = y;
        }

        @Override
        public int getX() {
            return (int) offsetPosX;
        }

        @Override
        public int getY() {
            return (int) offsetPosY;
        }

        @Override
        public int getWidth() {
            return (int) size;
        }

        @Override
        public int getHeight() {
            return (int) size;
        }

        @Override
        public void visitWidgets(@NotNull Consumer<AbstractWidget> consumer) {
        }

        @Override
        public void setFocused(boolean focused) {
            this.focused = focused;
        }

        @Override
        public boolean isFocused() {
            return focused;
        }

        @Override
        public @NotNull ScreenRectangle getRectangle() {
            return GuiEventListener.super.getRectangle();
        }

        @Override
        public @NotNull NarrationPriority narrationPriority() {
            return NarrationPriority.FOCUSED;
        }

        @Override
        public void updateNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        }

        public static boolean isMouseInCircle(float mx, float my, float cx, float cy, float r) {
            float dx = mx - cx;
            float dy = my - cy;
            return dx * dx + dy * dy < r * r;
        }
    }

    public static float getMainPanelLeft(float screenWidth) {
        return (screenWidth - MAIN_PANEL_WIDTH) / 2f;
    }

    public static float getMainPaneTop(float screenHeight) {
        return (screenHeight - MAIN_PANEL__HEIGHT) / 2f;
    }

    public static float getMainPanelRight(float screenWidth) {
        return getMainPanelLeft(screenWidth) + MAIN_PANEL_WIDTH;
    }

    public static float getMainPanelBottom(float screenHeight) {
        return getMainPaneTop(screenHeight) + MAIN_PANEL__HEIGHT;
    }
}