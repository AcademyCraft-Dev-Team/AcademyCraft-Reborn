package org.academy.internal.client.gui;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
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
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.academy.AcademyCraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.network.Packets;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.ability.builtin.level0.Level0;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedList;
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
    public static final int TEXT_COLOR = -3092272;
    public final RightPanelBackground rightPanelBackground = new RightPanelBackground();
    public final SkillInfoPanel skillInfoPanel = new SkillInfoPanel();
    public final WirelessViewPanel wirelessViewPanel = new WirelessViewPanel();
    public final List<Skill> skillList = new ArrayList<>();
    public final BlockPos mainPos;
    @Nullable
    public Renderable currentPanel;
    public AbilityDeveloperBlockEntity abilityDeveloperBlockEntity;

    static {
        NetworkSystemClient.registerS2CPacketHandler(Packets.S2C_ABILITY_DEVELOPER_SCREEN_RESPONSE, (listener, packet) -> {
            if (Minecraft.getInstance().screen instanceof AbilityDeveloperScreen abilityDeveloperScreen) {
                String response = packet.friendlyByteBuf.readUtf();
                abilityDeveloperScreen.rightPanelBackground.addOutputLine(response);
            }
        });
    }

    public AbilityDeveloperScreen(BlockPos mainPos) {
        super(Component.empty());
        this.mainPos = mainPos;
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(mainPos)
                instanceof AbilityDeveloperBlockEntity entity) {
            this.abilityDeveloperBlockEntity = entity;
        }else {
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public void tick() {
        rightPanelBackground.tick();
        for (Skill skill : skillList) {
            skill.tick();
        }
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    protected void init() {
        addRenderableOnly(new LeftPanelBackground());
        addRenderableOnly(rightPanelBackground);
        Skill skillA = new Skill(-20, 20, new ResourceLocation(AcademyCraft.MOD_ID, "textures/hud/ability/electromaster/icon_overlay.png"));
        Skill skillB = new Skill(80, 45, new ResourceLocation(AcademyCraft.MOD_ID, "textures/hud/ability/teleport/icon_overlay.png"));
        skillB.hasFathers = true;
        skillB.fathers.add(skillA);
        addSkill(skillA);
        addSkill(skillB);
        addRenderableWidget(new LeftPanelInfo());
        addRenderableWidget(wirelessViewPanel);
        addRenderableWidget(skillInfoPanel);
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

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        for (GuiEventListener listener : children()) {
            listener.mouseMoved(mouseX, mouseY);
        }
    }

    public class RightPanelBackground implements Renderable, Tickable, GuiEventListener {
        public static final ResourceLocation TEXTURE_BACK = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/parent_background_developerright.png");
        public static final RenderType RENDER_TYPE_BACK = new RenderType.CompositeRenderType(
                "developer_right_panel_back", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 16, false, true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(TEXTURE_BACK, true, false))
                        .setShaderState(RenderUtil.RenderStates.POSITION_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false));
        public static final ResourceLocation TEXTURE_INFO = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/ui_developerright.png");
        public static final RenderType RENDER_TYPE_INFO = new RenderType.CompositeRenderType(
                "developer_right_panel_info",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(TEXTURE_INFO, false, false))
                        .setShaderState(RenderUtil.RenderStates.POSITION_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false));
        public static final ResourceLocation TEXTURE_SKILL_PANEL_BACK = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/skill_panel_back.png");
        public static final RenderType RENDER_TYPE_SKILL_PANEL_BACK = new RenderType.CompositeRenderType(
                "developer_skill_panel_back",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(TEXTURE_SKILL_PANEL_BACK, true, false))
                        .setShaderState(RenderUtil.RenderStates.POSITION_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false));

        public boolean bootFailed = true;
        public boolean printFinished = false;
        public boolean editBoxAdded = false;
        public static final String welcomeText = "Welcome to Academy OS, Ver 0.0.1";
        public int currentWelcomeTextNum = 0;
        public boolean welcomeDone = false;
        public static final String bootingText = "User %s detected, System booting...";
        public int currentBootingTextNum = 0;
        public boolean bootingDone = false;
        public int bootingProgress = 0;
        public boolean bootingProgressDone = false;
        public static final String failText = "Invalid ability category, boot failed";
        public int currentFailTextNum = 0;
        public boolean failDone = false;
        public static final String hintText = "Type 'learn' to acquire new category.";
        public int currentHintTextNum = 0;

        public static final String osText = "OS > ";
        public final List<FormattedCharSequence> OUTPUT_LINES = new LinkedList<>();
        public EditBox editBox;

        private void addOutputLine(String text) {
            List<FormattedCharSequence> linesToAdd = font.split(FormattedText.of(text), (int) (SKILL_PANEL_WIDTH - 4));
            for (FormattedCharSequence line : linesToAdd) {
                OUTPUT_LINES.add(line);
                int lineLimit = 100;
                while (OUTPUT_LINES.size() > lineLimit) {
                    OUTPUT_LINES.remove(0);
                }
            }
        }

        private void updateLastOutputLine(String text) {
            if (!OUTPUT_LINES.isEmpty()) {
                OUTPUT_LINES.set(OUTPUT_LINES.size() - 1, Component.literal(text).getVisualOrderText());
            } else {
                addOutputLine(text);
            }
        }

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

            final float skillPanelBackLeft = left + SKILL_PANEL_LEFT_ZONE;
            final float skillPanelBackTop = top + SKILL_PANEL_TOP_ZONE;
            final float skillPanelBackBottom = skillPanelBackTop + SKILL_PANEL_HEIGHT;
            final float textPadding = 2;
            final int lineHeight = font.lineHeight;

            if (bootFailed) {
                final int renderAreaX = (int) (skillPanelBackLeft + textPadding);
                final int outputAreaTopY = (int) (skillPanelBackTop + textPadding);
                final int renderAreaBottomLimit = (int) (skillPanelBackBottom - textPadding);
                final int maxVisibleLinesInPanel = Math.max(1, (renderAreaBottomLimit - outputAreaTopY) / lineHeight);

                int linesToRenderCount = Math.min(OUTPUT_LINES.size(), maxVisibleLinesInPanel);
                if (printFinished && linesToRenderCount == maxVisibleLinesInPanel) {
                    linesToRenderCount--;
                }

                int startLineIndex = Math.max(0, OUTPUT_LINES.size() - linesToRenderCount);
                int currentY = outputAreaTopY;
                int linesDrawn = 0;

                for (int i = startLineIndex; i < OUTPUT_LINES.size(); i++) {
                    if (currentY + lineHeight > renderAreaBottomLimit) break;
                    guiGraphics.drawString(font, OUTPUT_LINES.get(i), renderAreaX, currentY, TEXT_COLOR);
                    currentY += lineHeight;
                    linesDrawn++;
                    if (printFinished && linesDrawn >= linesToRenderCount) break;
                }

                int promptLineY = currentY;
                if (promptLineY + lineHeight > renderAreaBottomLimit) {
                    promptLineY = renderAreaBottomLimit - lineHeight;
                }

                if (printFinished) {
                    if (promptLineY < renderAreaBottomLimit) {
                        guiGraphics.drawString(font, osText, renderAreaX, promptLineY, TEXT_COLOR);
                        if (editBox != null) {
                            editBox.setX(renderAreaX + font.width(osText));
                            editBox.setY(promptLineY);
                            editBox.setVisible(true);
                        }
                    } else if (editBox != null) {
                        editBox.setVisible(false);
                    }
                }
            } else {
                final float skillPanelBackRight = skillPanelBackLeft + SKILL_PANEL_WIDTH;
                VertexConsumer vertexConsumerSkillPanelBack = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_SKILL_PANEL_BACK);
                final float uOffset = ((float) mouseX / screenWidth) / 20;
                final float vOffset = ((float) mouseY / screenHeight) / 20;
                vertexConsumerSkillPanelBack.vertex(skillPanelBackLeft, skillPanelBackTop, 0).uv(uOffset, vOffset).endVertex();
                vertexConsumerSkillPanelBack.vertex(skillPanelBackLeft, skillPanelBackBottom, 0).uv(uOffset, vOffset + 0.9f).endVertex();
                vertexConsumerSkillPanelBack.vertex(skillPanelBackRight, skillPanelBackBottom, 0).uv(uOffset + 0.9f, vOffset + 0.9f).endVertex();
                vertexConsumerSkillPanelBack.vertex(skillPanelBackRight, skillPanelBackTop, 0).uv(uOffset + 0.9f, vOffset).endVertex();
            }
        }

        @Override
        public void tick() {
            bootFailed = AbilitySystemClient.getCategory() == Level0.INSTANCE;
            if (!bootFailed) {
                return;
            }

            if (!printFinished) {
                Minecraft minecraft = Minecraft.getInstance();
                String playerName = minecraft.player != null ? minecraft.player.getGameProfile().getName() : "<?>";

                if (!welcomeDone) {
                    if (currentWelcomeTextNum < welcomeText.length()) {
                        if (currentWelcomeTextNum == 0) addOutputLine("");
                        updateLastOutputLine(welcomeText.substring(0, currentWelcomeTextNum + 1));
                        currentWelcomeTextNum++;
                    } else {
                        welcomeDone = true;
                        currentBootingTextNum = 0;
                    }
                } else if (!bootingDone) {
                    String dynamicBootingText = String.format(bootingText, playerName);
                    if (currentBootingTextNum < dynamicBootingText.length()) {
                        if (currentBootingTextNum == 0) addOutputLine("");
                        updateLastOutputLine(dynamicBootingText.substring(0, currentBootingTextNum + 1));
                        currentBootingTextNum++;
                    } else {
                        bootingDone = true;
                        bootingProgress = 0;
                    }
                } else if (!bootingProgressDone) {
                    boolean firstProgressTick = (bootingProgress == 0);
                    if (bootingProgress < 100) {
                        bootingProgress++;
                        if (firstProgressTick) addOutputLine(getProgressBar(bootingProgress));
                        else updateLastOutputLine(getProgressBar(bootingProgress));
                    } else {
                        updateLastOutputLine(getProgressBar(100));
                        bootingProgressDone = true;
                        currentFailTextNum = 0;
                    }
                } else if (!failDone) {
                    if (currentFailTextNum < failText.length()) {
                        if (currentFailTextNum == 0) addOutputLine("");
                        updateLastOutputLine(failText.substring(0, currentFailTextNum + 1));
                        currentFailTextNum++;
                    } else {
                        failDone = true;
                        currentHintTextNum = 0;
                    }
                } else {
                    if (currentHintTextNum < hintText.length()) {
                        if (currentHintTextNum == 0) addOutputLine("");
                        updateLastOutputLine(hintText.substring(0, currentHintTextNum + 1));
                        currentHintTextNum++;
                    } else {
                        printFinished = true;
                    }
                }
            }

            if (printFinished && !editBoxAdded) {
                final float left = getMainPanelLeft(width) + PANEL_RIGHT_ZONE_LEFT;
                final float top = getMainPaneTop(height);
                final float skillPanelBackLeft = left + SKILL_PANEL_LEFT_ZONE;
                final float skillPanelBackTop = top + SKILL_PANEL_TOP_ZONE;
                final float skillPanelBackBottom = top + SKILL_PANEL_TOP_ZONE + SKILL_PANEL_HEIGHT;
                final float textPadding = 2;
                final int lineHeight = font.lineHeight;

                final int renderAreaX = (int) (skillPanelBackLeft + textPadding);
                final int outputAreaTopY = (int) (skillPanelBackTop + textPadding);
                final int renderAreaBottomLimit = (int) (skillPanelBackBottom - textPadding);
                final int maxVisibleLinesInPanel = Math.max(1, (renderAreaBottomLimit - outputAreaTopY) / lineHeight);
                int linesToRenderCount = Math.min(OUTPUT_LINES.size(), maxVisibleLinesInPanel - 1);
                int startLineIndex = Math.max(0, OUTPUT_LINES.size() - linesToRenderCount);
                int currentY = outputAreaTopY;
                for (int i = startLineIndex; i < OUTPUT_LINES.size(); i++) {
                    if (currentY + lineHeight > renderAreaBottomLimit) break;
                    currentY += lineHeight;
                }
                int promptLineY = currentY;
                if (promptLineY + lineHeight > renderAreaBottomLimit) {
                    promptLineY = renderAreaBottomLimit - lineHeight;
                }

                final int editBoxX = renderAreaX + font.width(osText);
                final int editBoxWidth = (int) (SKILL_PANEL_WIDTH - (textPadding * 2) - font.width(osText));

                editBox = new EditBox(font, editBoxX, promptLineY, editBoxWidth, lineHeight - 1, Component.empty());
                editBox.setBordered(false);

                AbilityDeveloperScreen.this.setFocused(editBox);
                AbilityDeveloperScreen.this.addRenderableWidget(editBox);
                editBoxAdded = true;
            }
        }

        private static String getProgressBar(int progress) {
            int barLength = 20;
            int filled = Math.max(0, Math.min(barLength, progress * barLength / 100));
            int displayProgress = Math.min(100, progress);
            return "[" + "=".repeat(filled) + " ".repeat(barLength - filled) + "] " + displayProgress + "%";
        }

        @Override
        public void setFocused(boolean focused) {
        }

        @Override
        public boolean isFocused() {
            return false;
        }

        @Override
        public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
            if (printFinished && editBox != null) {
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    if (AbilityDeveloperScreen.this.getFocused() == editBox) {
                        String command = editBox.getValue();
                        addOutputLine(osText + command);

                        switch (command.trim()) {
                            case "learn": {
                                if (abilityDeveloperBlockEntity.energyStored < 15000) {
                                    addOutputLine("Energy is not enough.");
                                } else {
                                    NetworkSystemClient.sendPacket(new C2SPacket(Packets.C2S_ACQUIRE_CATEGORY, mainPos));
                                }
                                AcademyCraft.LOGGER.info("Learn command entered");
                                break;
                            }
                            case "exit": {
                                Minecraft.getInstance().setScreen(null);
                                break;
                            }
                            default:
                                addOutputLine("Invalid command: " + command);
                        }

                        editBox.setValue("");
                        return true;
                    }
                }
            }
            return GuiEventListener.super.keyReleased(keyCode, scanCode, modifiers);
        }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return rightPanelBackground.keyReleased(keyCode, scanCode, modifiers) || super.keyReleased(keyCode, scanCode, modifiers);
    }

    public class LeftPanelBackground implements Renderable {
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
            final float left = getMainPanelLeft(width);
            final float right = left + PANEL_LEFT_WIDTH;
            final float top = getMainPaneTop(height);
            final float bottom = getMainPanelBottom(height);

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

    public class LeftPanelInfo implements Renderable, GuiEventListener, NarratableEntry {
        public static final ResourceLocation TEXTURE_WIRELESS_BUTTON_ICON = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_node.png");
        public static final RenderType RENDER_TYPE_WIRELESS_BUTTON_ICON = new RenderType.CompositeRenderType(
                "developer_left_panel_bottom",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TEXTURE_WIRELESS_BUTTON_ICON,
                                true,
                                false
                        ))
                        .setShaderState(RenderUtil.RenderStates.POSITION_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );
        public static final ResourceLocation TEXTURE_WIRELESS_BUTTON_BACK = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/element/element_background_light.png");
        public static final RenderType RENDER_TYPE_WIRELESS_BUTTON_BACK = new RenderType.CompositeRenderType(
                "developer_wireless_button_back",
                DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TEXTURE_WIRELESS_BUTTON_BACK,
                                true,
                                false
                        ))
                        .setShaderState(RenderUtil.RenderStates.POSITION_COLOR_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );

        public static final int WIRELESS_BUTTON_TITLE_X = 5;
        public static final int WIRELESS_BUTTON_TITLE_Y = 108;
        public static final int WIRELESS_BUTTON_BACK_X = 6;
        public static final int WIRELESS_BUTTON_BACK_Y = 116;
        public static final int WIRELESS_BUTTON_BACK_WIDTH = 96;
        public static final int WIRELESS_BUTTON_BACK_HEIGHT = 16;
        public static final int WIRELESS_BUTTON_ICON_SIZE = 10;
        public static final int WIRELESS_BUTTON_ICON_X = 8;
        public static final int WIRELESS_BUTTON_ICON_Y = 3;
        public boolean wirelessButtonCovered = false;

        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            renderWirelessButton(guiGraphics, mouseX, mouseY, partialTick);
        }

        public void renderWirelessButton(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            VertexConsumer wirelessButtonBack = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_WIRELESS_BUTTON_BACK);

            final float left = getMainPanelLeft(width) + WIRELESS_BUTTON_BACK_X;
            final float right = left + WIRELESS_BUTTON_BACK_WIDTH;
            final float top = getMainPaneTop(height) + WIRELESS_BUTTON_BACK_Y;
            final float bottom = top + WIRELESS_BUTTON_BACK_HEIGHT;

            float color = wirelessButtonCovered ? 1.0f : 0.75f;

            // Left Top
            wirelessButtonBack.vertex(left, top, 3).color(color, color, color, 1f).uv(0, 0).endVertex();
            // Left Bottom
            wirelessButtonBack.vertex(left, bottom, 3).color(color, color, color, 1f).uv(0, 1).endVertex();
            // Right Bottom
            wirelessButtonBack.vertex(right, bottom, 3).color(color, color, color, 1f).uv(1, 1).endVertex();
            // Right Top
            wirelessButtonBack.vertex(right, top, 3).color(color, color, color, 1f).uv(1, 0).endVertex();

            guiGraphics.drawString(font, "Current Node:", (int) (getMainPanelLeft(width) + WIRELESS_BUTTON_TITLE_X), (int) (getMainPaneTop(height) + WIRELESS_BUTTON_TITLE_Y), TEXT_COLOR);

            VertexConsumer wirelessButtonIcon = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_WIRELESS_BUTTON_ICON);
            final float iconLeft = left + WIRELESS_BUTTON_ICON_X;
            final float iconTop = top + WIRELESS_BUTTON_ICON_Y;
            final float iconRight = iconLeft + WIRELESS_BUTTON_ICON_SIZE;
            final float iconBottom = iconTop + WIRELESS_BUTTON_ICON_SIZE;

            wirelessButtonIcon.vertex(iconLeft, iconTop, 4).uv(0, 0).endVertex();
            wirelessButtonIcon.vertex(iconLeft, iconBottom, 4).uv(0, 1).endVertex();
            wirelessButtonIcon.vertex(iconRight, iconBottom, 4).uv(1, 1).endVertex();
            wirelessButtonIcon.vertex(iconRight, iconTop, 4).uv(1, 0).endVertex();
        }

        @Override
        public void setFocused(boolean focused) {
        }

        @Override
        public boolean isFocused() {
            return false;
        }

        @Override
        public @NotNull NarrationPriority narrationPriority() {
            return NarrationPriority.FOCUSED;
        }

        @Override
        public void updateNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        }

        @Override
        public void mouseMoved(double mouseX, double mouseY) {
            final float left = getMainPanelLeft(width) + WIRELESS_BUTTON_BACK_X;
            final float top = getMainPaneTop(height) + WIRELESS_BUTTON_BACK_Y;
            wirelessButtonCovered = isMouseInside(mouseX, mouseY, left, top, WIRELESS_BUTTON_BACK_WIDTH, WIRELESS_BUTTON_BACK_HEIGHT);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (currentPanel != null) return false;
            final float left = getMainPanelLeft(width) + WIRELESS_BUTTON_BACK_X;
            final float top = getMainPaneTop(height) + WIRELESS_BUTTON_BACK_Y;
            if (isMouseInside(mouseX, mouseY, left, top, WIRELESS_BUTTON_BACK_WIDTH, WIRELESS_BUTTON_BACK_HEIGHT)) {
                currentPanel = wirelessViewPanel;
                AcademyCraft.LOGGER.info("Clicked on wireless button");
                return true;
            } else {
                return false;
            }
        }
    }

    public class WirelessViewPanel implements Renderable, GuiEventListener, NarratableEntry {
        public static final ResourceLocation TEXTURE_ICON = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_tonode.png");
        public static final RenderType RENDER_TYPE_ICON = new RenderType.CompositeRenderType(
                "developer_wireless_view_icon",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TEXTURE_ICON,
                                true,
                                false
                        ))
                        .setShaderState(RenderUtil.RenderStates.POSITION_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );

        public static final ResourceLocation TEXTURE_BACK = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/element/element_background_dark.png");
        public static final RenderType RENDER_TYPE_BACK = new RenderType.CompositeRenderType(
                "developer_wireless_view_back",
                DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TEXTURE_BACK,
                                false,
                                false
                        ))
                        .setShaderState(RenderUtil.RenderStates.POSITION_COLOR_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );
        public static final int WIRELESS_VIEW_BACK_WIDTH = 176;
        public static final int WIRELESS_VIEW_BACK_HEIGHT = 187;
        public static final int WIRELESS_VIEW_ICON_OFFSET = 10;
        public static final int WIRELESS_VIEW_ICON_SIZE = 16;

        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            if (currentPanel != this) return;
            renderBackground(guiGraphics);
            VertexConsumer wirelessViewBack = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_BACK);

            final float left = (width - WIRELESS_VIEW_BACK_WIDTH) / 2.0f;
            final float right = left + WIRELESS_VIEW_BACK_WIDTH;
            final float top = (height - WIRELESS_VIEW_BACK_HEIGHT) / 2.0f;
            final float bottom = top + WIRELESS_VIEW_BACK_HEIGHT;

            float color = 1.0f;

            // Left Top
            wirelessViewBack.vertex(left, top, 3).color(color, color, color, 1f).uv(0, 0).endVertex();
            // Left Bottom
            wirelessViewBack.vertex(left, bottom, 3).color(color, color, color, 1f).uv(0, 1).endVertex();
            // Right Bottom
            wirelessViewBack.vertex(right, bottom, 3).color(color, color, color, 1f).uv(1, 1).endVertex();
            // Right Top
            wirelessViewBack.vertex(right, top, 3).color(color, color, color, 1f).uv(1, 0).endVertex();
            VertexConsumer wirelessViewIcon = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_ICON);
            final float iconLeft = left + WIRELESS_VIEW_ICON_OFFSET;
            final float iconTop = top + WIRELESS_VIEW_ICON_OFFSET;
            final float iconRight = iconLeft + WIRELESS_VIEW_ICON_SIZE;
            final float iconBottom = iconTop + WIRELESS_VIEW_ICON_SIZE;

            wirelessViewIcon.vertex(iconLeft, iconTop, 4).uv(0, 0).endVertex();
            wirelessViewIcon.vertex(iconLeft, iconBottom, 4).uv(0, 1).endVertex();
            wirelessViewIcon.vertex(iconRight, iconBottom, 4).uv(1, 1).endVertex();
            wirelessViewIcon.vertex(iconRight, iconTop, 4).uv(1, 0).endVertex();


        }

        @Override
        public void setFocused(boolean focused) {
        }

        @Override
        public boolean isFocused() {
            return false;
        }

        @Override
        public @NotNull NarrationPriority narrationPriority() {
            return NarrationPriority.FOCUSED;
        }

        @Override
        public void updateNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (currentPanel != this) return false;
            final float left = (width - WIRELESS_VIEW_BACK_WIDTH) / 2.0f;
            final float top = (height - WIRELESS_VIEW_BACK_HEIGHT) / 2.0f;

            if (!isMouseInside(mouseX, mouseY, left, top, WIRELESS_VIEW_BACK_WIDTH, WIRELESS_VIEW_BACK_HEIGHT)) {
                currentPanel = null;
                return true;
            } else {
                return false;
            }
        }
    }

    public class Skill implements Renderable, GuiEventListener, LayoutElement, NarratableEntry, Tickable {
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
            if (rightPanelBackground.bootFailed) return;
            final float xOffset = (dynamicFollow ? ((float) mouseX / width) : 0) * -20 + offsetPosX;
            final float yOffset = (dynamicFollow ? ((float) mouseY / height) : 0) * -15 + offsetPosY;

            float xPos = xOffset + ((float) width / 2);
            float yPos = yOffset + ((float) height / 2);

            setFocused(isMouseInCircle(mouseX, mouseY, xPos, yPos, size / 2));
            targetScale = isFocused() ? 1.25f : 1.0f;
            double animationDuration = MathUtil.PI / 10;
            float deltaTime = partialTick * (1f / 20f);
            float factor = 1f - (float) Math.exp(-Math.log(20f) * deltaTime / animationDuration);
            scale = MathUtil.lerpStartEndFactor(scale, targetScale, factor);

            if (Float.isNaN(scale)) {
                scale = 1.0f;
            }
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
        }

        @Override
        public boolean isFocused() {
            return false;
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

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (rightPanelBackground.bootFailed) return false;
            if (isFocused()) {
                skillInfoPanel.showSkillInfoPanel(this);
                return true;
            } else {
                return false;
            }
        }
    }

    public class SkillInfoPanel implements Renderable, GuiEventListener, NarratableEntry {
        public static final float WIDTH = 256.0f;
        public static final float HEIGHT = 135.0f;
        public static final float BUTTON_WIDTH = 32.0f;
        public static final float BUTTON_HEIGHT = 16.0f;
        public static final ResourceLocation BUTTON_TEXTURE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/button.png");
        public static final RenderType RENDER_TYPE_LEARN_BUTTON = new RenderType.CompositeRenderType(
                "developer_learn_button",
                DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                BUTTON_TEXTURE,
                                false,
                                false
                        ))
                        .setShaderState(RenderUtil.RenderStates.POSITION_COLOR_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );

        public Skill shownSkill;
        public boolean isShowing = false;

        public SkillInfoPanel() {
        }

        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            if (currentPanel == this && shownSkill != null) {
                renderBackground(guiGraphics);
                renderLearnButton(guiGraphics, isMouseInsideButton(mouseX, mouseY));
            }
        }

        public void renderLearnButton(@NotNull GuiGraphics guiGraphics, boolean isFocused) {
            final VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_LEARN_BUTTON);
            final float centerX = (float) width / 2.0f;
            final float centerY = (float) height / 2.0f;
            final float left = centerX - BUTTON_WIDTH / 2;
            final float top = centerY - BUTTON_HEIGHT / 2;
            final float right = left + BUTTON_WIDTH;
            final float bottom = top + BUTTON_HEIGHT;
            float color = isFocused ? 1.0f : 0.75f;
            // Left Top
            vertexConsumer.vertex(left, top, 3).color(color, color, color, 1f).uv(0, 0).endVertex();
            // Left Bottom
            vertexConsumer.vertex(left, bottom, 3).color(color, color, color, 1f).uv(0, 1).endVertex();
            // Right Bottom
            vertexConsumer.vertex(right, bottom, 3).color(color, color, color, 1f).uv(1, 1).endVertex();
            // Right Top
            vertexConsumer.vertex(right, top, 3).color(color, color, color, 1f).uv(1, 0).endVertex();
        }

        @Override
        public void setFocused(boolean focused) {
        }

        @Override
        public boolean isFocused() {
            return false;
        }

        @Override
        public @NotNull ScreenRectangle getRectangle() {
            return ScreenRectangle.empty();
        }

        @Override
        public @NotNull NarrationPriority narrationPriority() {
            return NarrationPriority.FOCUSED;
        }

        @Override
        public void updateNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        }

        public boolean isMouseInsideButton(double mouseX, double mouseY) {
            return isMouseInside(mouseX, mouseY, ((double) width / 2) - (BUTTON_WIDTH / 2), ((double) height / 2) - (BUTTON_HEIGHT / 2), BUTTON_WIDTH, BUTTON_HEIGHT);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (currentPanel == this) {
                if (isMouseInside(mouseX, mouseY, ((double) width / 2) - (WIDTH / 2), ((double) height / 2) - (HEIGHT / 2), WIDTH, HEIGHT)) {
                    if (isMouseInsideButton(mouseX, mouseY)) {
                        AcademyCraft.LOGGER.info("Learn button clicked");
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    currentPanel = null;
                    return true;
                }
            } else {
                return false;
            }
        }

        public void showSkillInfoPanel(@NotNull Skill skill) {
            this.isShowing = true;
            this.shownSkill = skill;
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

    public static boolean isMouseInside(double mouseX, double mouseY,
                                        double rectX, double rectY,
                                        double rectWidth, double rectHeight) {
        return mouseX >= rectX && mouseX <= rectX + rectWidth &&
                mouseY >= rectY && mouseY <= rectY + rectHeight;
    }
}