package org.academy.internal.client.gui;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.Tickable;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
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
import org.academy.api.common.wireless.WirelessManager;
import org.academy.api.common.wireless.WirelessNode;
import org.academy.internal.common.ability.builtin.level0.Level0;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class AbilityDeveloperScreen extends Screen {
    public static final ResourceLocation TEXTURE_BUTTON_BACK = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/element/element_background_light.png");
    public static final RenderType RENDER_TYPE_BUTTON_BACK = new RenderType.CompositeRenderType(
            "developer_wireless_button_back",
            DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS,
            16,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            TEXTURE_BUTTON_BACK,
                            true,
                            false
                    ))
                    .setShaderState(RenderUtil.RenderStates.POSITION_COLOR_TEX_SHADER)
                    .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                    .createCompositeState(false)
    );
    public static final ResourceLocation TEXTURE_WIRELESS_BUTTON_ICON = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_node.png");
    public static final RenderType RENDER_TYPE_WIRELESS_BUTTON_ICON = new RenderType.CompositeRenderType(
            "developer_wireless_button_icon",
            DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS,
            32,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            TEXTURE_WIRELESS_BUTTON_ICON,
                            false,
                            false
                    ))
                    .setShaderState(RenderUtil.RenderStates.POSITION_COLOR_TEX_SHADER)
                    .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                    .createCompositeState(false)
    );

    public static final float MAIN_PANEL_WIDTH = 400;
    public static final float MAIN_PANEL__HEIGHT = 187;
    public static final float PANEL_RIGHT_WIDTH = 278;
    public static final float PANEL_RIGHT_ZONE_LEFT = MAIN_PANEL_WIDTH - PANEL_RIGHT_WIDTH;
    public static final float PANEL_LEFT_WIDTH = 108.5f;
    public static final float SKILL_PANEL_WIDTH = 257;
    public static final float SKILL_PANEL_HEIGHT = 139;
    public static final float SKILL_PANEL_LEFT_ZONE = (PANEL_RIGHT_WIDTH - SKILL_PANEL_WIDTH) / 2;
    public static final float SKILL_PANEL_TOP_ZONE = 17;
    public static final int TEXT_COLOR_DARK = -3092272;
    public static final int TEXT_COLOR_LIGHT = -524296;
    public static final int TEXT_HEIGHT = 9;
    public final RightPanelBackground rightPanelBackground = new RightPanelBackground();
    public final SkillInfoPanel skillInfoPanel = new SkillInfoPanel();
    public final WirelessViewPanel wirelessViewPanel = new WirelessViewPanel();
    public final List<Skill> skillList = new ArrayList<>();
    public final BlockPos mainPos;
    @Nullable
    public ContainerEventHandler currentPanel;
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
    public void tick() {
        rightPanelBackground.tick();
        for (Skill skill : skillList) {
            skill.tick();
        }
        if (rightPanelBackground.editBox != null) {
            rightPanelBackground.editBox.active = currentPanel == null &&
                    rightPanelBackground.bootFailed &&
                    rightPanelBackground.printFinished;
        }
        if (currentPanel != null && currentPanel.getFocused() instanceof Tickable tickable) {
            tickable.tick();
        }
    }

    @Override
    public void onClose() {
        if (this.currentPanel instanceof WirelessViewPanel wvp) {
            wvp.hide();
        } else if (this.currentPanel instanceof SkillInfoPanel sip) {
            sip.hide();
        }
        this.currentPanel = null;
        super.onClose();
        abilityDeveloperBlockEntity.setOpen(false);
    }

    @Override
    protected void init() {
        this.clearWidgets();
        addRenderableOnly(new LeftPanelBackground());
        addRenderableOnly(rightPanelBackground);
        skillList.clear();
        Skill skillA = new Skill(-20, 20, new ResourceLocation(AcademyCraft.MOD_ID, "textures/hud/ability/electromaster/icon_overlay.png"));
        Skill skillB = new Skill(80, 45, new ResourceLocation(AcademyCraft.MOD_ID, "textures/hud/ability/teleport/icon_overlay.png"));
        skillB.hasFathers = true;
        skillB.fathers.add(skillA);
        addSkill(skillA);
        addSkill(skillB);
        addRenderableWidget(new LeftPanelInfo());

        this.rightPanelBackground.editBoxAdded = false;
        if (rightPanelBackground.printFinished && rightPanelBackground.bootFailed) {
            rightPanelBackground.tryInitializeEditBox(this.width, this.height);
        }
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        ContainerEventHandler oldPanel = this.currentPanel;
        Skill oldSkill = null;
        if (oldPanel instanceof SkillInfoPanel sip) {
            oldSkill = sip.shownSkill;
        }

        this.currentPanel = null;
        this.init(minecraft, width, height);

        if (oldPanel instanceof WirelessViewPanel) {
            wirelessViewPanel.show();
            this.currentPanel = wirelessViewPanel;
            this.setFocused(this.currentPanel);
        } else if (oldPanel instanceof SkillInfoPanel && oldSkill != null) {
            skillInfoPanel.showSkillInfoPanel(oldSkill);
            this.currentPanel = skillInfoPanel;
            this.setFocused(this.currentPanel);
        }

        if (rightPanelBackground.editBoxAdded && rightPanelBackground.bootFailed) {
            rightPanelBackground.resize(width, height);
            if (this.currentPanel == null) {
                this.setFocused(rightPanelBackground.editBox);
            }
        }
    }

    public void addSkill(@NotNull Skill skill) {
        skillList.add(skill);
        addRenderableWidget(skill);
    }


    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (currentPanel instanceof Renderable renderable) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 100);
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.currentPanel != null) {
            boolean panelHandledClick = this.currentPanel.mouseClicked(mouseX, mouseY, button);
            if (panelHandledClick) {
                this.setFocused(this.currentPanel);
                if (this.currentPanel == null) {
                    if (rightPanelBackground.editBox != null && rightPanelBackground.editBox.active) {
                        this.setFocused(rightPanelBackground.editBox);
                    } else {
                        this.setFocused(null);
                    }
                }
                return true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void setFocused(@Nullable GuiEventListener listener) {
        if (currentPanel instanceof ContainerEventHandler && listener != currentPanel) {
            currentPanel.setFocused(listener);
        } else {
            super.setFocused(listener);
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.currentPanel != null) {
            return this.currentPanel.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.currentPanel != null) {
            return this.currentPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.currentPanel != null) {
            return this.currentPanel.keyPressed(keyCode, scanCode, modifiers);
        }

        if (rightPanelBackground.editBox != null && this.getFocused() == rightPanelBackground.editBox) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (rightPanelBackground.processCommand()) {
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (this.currentPanel != null) {
            return this.currentPanel.keyReleased(keyCode, scanCode, modifiers);
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }


    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.currentPanel != null) {
            return this.currentPanel.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (currentPanel != null) {
            currentPanel.mouseMoved(mouseX, mouseY);
            return;
        }
        super.mouseMoved(mouseX, mouseY);
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
                    guiGraphics.drawString(font, OUTPUT_LINES.get(i), renderAreaX, currentY, TEXT_COLOR_DARK);
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
                        guiGraphics.drawString(font, osText, renderAreaX, promptLineY, TEXT_COLOR_DARK);
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

        private void tryInitializeEditBox(int screenWidth, int screenHeight) {
            if (editBoxAdded || !printFinished || !bootFailed) return;

            final float left = getMainPanelLeft(screenWidth) + PANEL_RIGHT_ZONE_LEFT;
            final float top = getMainPaneTop(screenHeight);
            final float skillPanelBackLeft = left + SKILL_PANEL_LEFT_ZONE;
            final float skillPanelBackTop = top + SKILL_PANEL_TOP_ZONE;
            final float skillPanelBackBottom = top + SKILL_PANEL_TOP_ZONE + SKILL_PANEL_HEIGHT;
            final float textPadding = 2;
            final int lineHeight = font.lineHeight;
            final int renderAreaX = (int) (skillPanelBackLeft + textPadding);
            final int outputAreaTopY = (int) (skillPanelBackTop + textPadding);
            final int renderAreaBottomLimit = (int) (skillPanelBackBottom - textPadding - lineHeight);
            final int maxVisibleLinesInPanel = Math.max(1, (renderAreaBottomLimit - outputAreaTopY) / lineHeight);
            int startLineIndex = Math.max(0, OUTPUT_LINES.size() - maxVisibleLinesInPanel);
            int currentY = outputAreaTopY;
            for (int i = startLineIndex; i < OUTPUT_LINES.size(); i++) {
                if (currentY + lineHeight > renderAreaBottomLimit) break;
                currentY += lineHeight;
            }
            int promptLineY = Math.max(currentY, renderAreaBottomLimit);
            if (promptLineY + lineHeight > skillPanelBackBottom - textPadding) {
                promptLineY = (int) (skillPanelBackBottom - textPadding - lineHeight);
            }

            final int editBoxX = renderAreaX + font.width(osText);
            final int editBoxWidth = (int) (skillPanelBackLeft + SKILL_PANEL_WIDTH - textPadding - editBoxX);

            editBox = new EditBox(font, editBoxX, promptLineY, editBoxWidth, lineHeight - 1, Component.empty());
            editBox.setBordered(false);
            editBox.setTextColor(TEXT_COLOR_DARK);
            editBox.active = false;

            AbilityDeveloperScreen.this.addRenderableWidget(editBox);
            editBoxAdded = true;
        }

        public void resize(int screenWidth, int screenHeight) {
            if (editBoxAdded && editBox != null) {
                final float left = getMainPanelLeft(screenWidth) + PANEL_RIGHT_ZONE_LEFT;
                final float top = getMainPaneTop(screenHeight);
                final float skillPanelBackLeft = left + SKILL_PANEL_LEFT_ZONE;
                final float skillPanelBackTop = top + SKILL_PANEL_TOP_ZONE;
                final float skillPanelBackBottom = top + SKILL_PANEL_TOP_ZONE + SKILL_PANEL_HEIGHT;
                final float textPadding = 2;
                final int lineHeight = font.lineHeight;
                final int renderAreaX = (int) (skillPanelBackLeft + textPadding);
                final int outputAreaTopY = (int) (skillPanelBackTop + textPadding);
                final int renderAreaBottomLimit = (int) (skillPanelBackBottom - textPadding - lineHeight);
                final int maxVisibleLinesInPanel = Math.max(1, (renderAreaBottomLimit - outputAreaTopY) / lineHeight);
                int startLineIndex = Math.max(0, OUTPUT_LINES.size() - maxVisibleLinesInPanel);
                int currentY = outputAreaTopY;
                for (int i = startLineIndex; i < OUTPUT_LINES.size(); i++) {
                    if (currentY + lineHeight > renderAreaBottomLimit) break;
                    currentY += lineHeight;
                }
                int promptLineY = Math.max(currentY, renderAreaBottomLimit);
                if (promptLineY + lineHeight > skillPanelBackBottom - textPadding) {
                    promptLineY = (int) (skillPanelBackBottom - textPadding - lineHeight);
                }

                final int editBoxX = renderAreaX + font.width(osText);
                final int editBoxWidth = (int) (skillPanelBackLeft + SKILL_PANEL_WIDTH - textPadding - editBoxX);

                editBox.setPosition(editBoxX, promptLineY);
                editBox.setWidth(editBoxWidth);
            }
        }

        @Override
        public void tick() {
            bootFailed = AbilitySystemClient.getCategory() == Level0.INSTANCE;
            if (!bootFailed) {
                if (!printFinished) {
                    OUTPUT_LINES.clear();
                    printFinished = true;
                    if (editBox != null) {
                        editBox.setVisible(false);
                        editBox.active = false;
                        AbilityDeveloperScreen.this.removeWidget(editBox);
                        editBox = null;
                        editBoxAdded = false;
                    }
                }
                return;
            }

            if (!printFinished) {
                Minecraft minecraft = Minecraft.getInstance();
                String playerName = minecraft.player != null ? minecraft.player.getGameProfile().getName() : "<?>";
                int typingSpeed = 1;
                boolean updated = false;

                if (!welcomeDone) {
                    int target = Math.min(welcomeText.length(), currentWelcomeTextNum + typingSpeed);
                    if (target > currentWelcomeTextNum) {
                        if (currentWelcomeTextNum == 0) addOutputLine("");
                        updateLastOutputLine(welcomeText.substring(0, target));
                        currentWelcomeTextNum = target;
                        updated = true;
                    }
                    if (!updated && currentWelcomeTextNum == welcomeText.length()) welcomeDone = true;
                } else if (!bootingDone) {
                    String dynamicBootingText = String.format(bootingText, playerName);
                    int target = Math.min(dynamicBootingText.length(), currentBootingTextNum + typingSpeed);
                    if (target > currentBootingTextNum) {
                        if (currentBootingTextNum == 0) addOutputLine("");
                        updateLastOutputLine(dynamicBootingText.substring(0, target));
                        currentBootingTextNum = target;
                        updated = true;
                    }
                    if (!updated && currentBootingTextNum == dynamicBootingText.length()) bootingDone = true;
                } else if (!bootingProgressDone) {
                    boolean firstProgressTick = (bootingProgress == 0);
                    if (bootingProgress < 100) {
                        bootingProgress = Math.min(100, bootingProgress + 5);
                        if (firstProgressTick) addOutputLine(getProgressBar(bootingProgress));
                        else updateLastOutputLine(getProgressBar(bootingProgress));
                        updated = true;
                    }
                    if (!updated && bootingProgress == 100) {
                        updateLastOutputLine(getProgressBar(100));
                        bootingProgressDone = true;
                    }
                } else if (!failDone) {
                    int target = Math.min(failText.length(), currentFailTextNum + typingSpeed);
                    if (target > currentFailTextNum) {
                        if (currentFailTextNum == 0) addOutputLine("");
                        updateLastOutputLine(failText.substring(0, target));
                        currentFailTextNum = target;
                        updated = true;
                    }
                    if (!updated && currentFailTextNum == failText.length()) failDone = true;
                } else {
                    int target = Math.min(hintText.length(), currentHintTextNum + typingSpeed);
                    if (target > currentHintTextNum) {
                        if (currentHintTextNum == 0) addOutputLine("");
                        updateLastOutputLine(hintText.substring(0, target));
                        currentHintTextNum = target;
                        updated = true;
                    }
                    if (!updated && currentHintTextNum == hintText.length()) {
                        printFinished = true;
                        tryInitializeEditBox(AbilityDeveloperScreen.this.width, AbilityDeveloperScreen.this.height);
                    }
                }
            } else if (!editBoxAdded) {
                tryInitializeEditBox(AbilityDeveloperScreen.this.width, AbilityDeveloperScreen.this.height);
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

        public boolean processCommandInternal() {
            if (editBox == null) return false;
            String command = editBox.getValue();
            addOutputLine(osText + command);

            switch (command.trim().toLowerCase()) {
                case "learn": {
                    if (abilityDeveloperBlockEntity.energyStored < 15000) {
                        addOutputLine("Energy is not enough.");
                    } else {
                        addOutputLine("Requesting category acquisition...");
                        NetworkSystemClient.sendPacket(new C2SPacket(Packets.C2S_ACQUIRE_CATEGORY, mainPos));
                    }
                    AcademyCraft.LOGGER.info("Learn command entered");
                    break;
                }
                case "exit": {
                    Minecraft.getInstance().setScreen(null);
                    break;
                }
                case "clear": {
                    OUTPUT_LINES.clear();
                    addOutputLine("Console cleared.");
                    break;
                }
                default:
                    addOutputLine("Invalid command: " + command);
            }

            editBox.setValue("");
            return true;
        }

        public boolean processCommand() {
            if (printFinished && editBox != null && bootFailed) {
                return processCommandInternal();
            }
            return false;
        }
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
        public static final int WIRELESS_BUTTON_TITLE_X = 5;
        public static final int WIRELESS_BUTTON_TITLE_Y = 108;
        public static final int WIRELESS_BUTTON_BACK_X = 6;
        public static final int WIRELESS_BUTTON_BACK_Y = 116;
        public static final int WIRELESS_BUTTON_BACK_WIDTH = 96;
        public static final int WIRELESS_BUTTON_BACK_HEIGHT = 16;
        public static final int WIRELESS_BUTTON_ICON_SIZE = 10;
        public static final int WIRELESS_BUTTON_ICON_X = 6;
        public static final int WIRELESS_BUTTON_ICON_Y = 3;
        public boolean wirelessButtonCovered = false;

        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            renderWirelessButton(guiGraphics);
        }

        public void renderWirelessButton(@NotNull GuiGraphics guiGraphics) {
            VertexConsumer wirelessButtonBack = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_BUTTON_BACK);

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

            guiGraphics.drawString(font, "Current Node:", (int) (getMainPanelLeft(width) + WIRELESS_BUTTON_TITLE_X), (int) (getMainPaneTop(height) + WIRELESS_BUTTON_TITLE_Y), TEXT_COLOR_DARK);

            VertexConsumer wirelessButtonIcon = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_WIRELESS_BUTTON_ICON);
            final float iconLeft = left + WIRELESS_BUTTON_ICON_X;
            final float iconTop = top + WIRELESS_BUTTON_ICON_Y;
            final float iconRight = iconLeft + WIRELESS_BUTTON_ICON_SIZE;
            final float iconBottom = iconTop + WIRELESS_BUTTON_ICON_SIZE;
            final float iconColor = abilityDeveloperBlockEntity.getWirelessNode() != null ? 1.0f : 0.75f;
            wirelessButtonIcon.vertex(iconLeft, iconTop, 4).color(iconColor, iconColor, iconColor, 1.0f).uv(0, 0).endVertex();
            wirelessButtonIcon.vertex(iconLeft, iconBottom, 4).color(iconColor, iconColor, iconColor, 1.0f).uv(0, 1).endVertex();
            wirelessButtonIcon.vertex(iconRight, iconBottom, 4).color(iconColor, iconColor, iconColor, 1.0f).uv(1, 1).endVertex();
            wirelessButtonIcon.vertex(iconRight, iconTop, 4).color(iconColor, iconColor, iconColor, 1.0f).uv(1, 0).endVertex();
            String name = "N/A";
            if (abilityDeveloperBlockEntity.getWirelessNode() != null) {
                name = abilityDeveloperBlockEntity.getWirelessNode().getNodeName();
            }
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 4);
            guiGraphics.drawString(font, name, (int) (iconLeft + 15), (int) (top + (float) (WIRELESS_BUTTON_BACK_HEIGHT - TEXT_HEIGHT) / 2), TEXT_COLOR_DARK);
            guiGraphics.pose().popPose();
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
            final float buttonLeft = getMainPanelLeft(width) + WIRELESS_BUTTON_BACK_X;
            final float buttonTop = getMainPaneTop(height) + WIRELESS_BUTTON_BACK_Y;
            boolean covered = isMouseInside(mouseX, mouseY, buttonLeft, buttonTop, WIRELESS_BUTTON_BACK_WIDTH, WIRELESS_BUTTON_BACK_HEIGHT);

            if (button == 0 && covered) {
                if (currentPanel == null) {
                    wirelessViewPanel.show();
                    currentPanel = wirelessViewPanel;
                    AbilityDeveloperScreen.this.setFocused(wirelessViewPanel.getFocused());
                    AcademyCraft.LOGGER.info("Clicked on wireless button - Opened Panel");
                    return true;
                }
            }
            return false;
        }
    }

    public class WirelessViewPanel implements Renderable, ContainerEventHandler, NarratableEntry {
        public static final ResourceLocation TEXTURE_ICON = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_tonode.png");
        public static final RenderType RENDER_TYPE_ICON = new RenderType.CompositeRenderType(
                "developer_wireless_view_icon", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 16, false, true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(TEXTURE_ICON, true, false))
                        .setShaderState(RenderUtil.RenderStates.POSITION_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false));
        public static final ResourceLocation TEXTURE_BACK = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/element/element_background_dark.png");
        public static final RenderType RENDER_TYPE_BACK = new RenderType.CompositeRenderType(
                "developer_wireless_view_back", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 16, false, true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(TEXTURE_BACK, false, false))
                        .setShaderState(RenderUtil.RenderStates.POSITION_COLOR_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false));
        public static final int WIRELESS_VIEW_BACK_WIDTH = 176;
        public static final int WIRELESS_VIEW_BACK_HEIGHT = 187;
        public static final int WIRELESS_VIEW_ICON_OFFSET = 10;
        public static final int WIRELESS_VIEW_ICON_SIZE = 16;
        public static final int WIRELESS_NODE_BUTTON_BACK_WIDTH = 150;
        public static final int WIRELESS_NODE_BUTTON_BACK_HEIGHT = 16;
        public static final int WIRELESS_NODE_BUTTON_BACK_BLANK_HEIGHT = 5;
        public static final int WIRELESS_NODE_BUTTON_ICON_SIZE = 10;
        public static final int PADDING = 10;
        public static final int EDIT_BOX_WIDTH = 60;
        public static final int EDIT_BOX_HEIGHT = 14;
        public static final int ACTION_BUTTON_HEIGHT = 14;

        private final Map<WirelessNode, Pair<EditBox, ConnectButton>> availableNodeWidgets = new LinkedHashMap<>();
        private final List<GuiEventListener> panelChildren = new ArrayList<>();
        @Nullable
        private GuiEventListener focusedChild = null;
        private boolean isDragging = false;

        public void show() {
            hide();
            resize(AbilityDeveloperScreen.this.width, AbilityDeveloperScreen.this.height);
        }

        public void resize(int screenWidth, int screenHeight) {
            final float panelLeft = (screenWidth - WIRELESS_VIEW_BACK_WIDTH) / 2.0f;
            final float panelTop = (screenHeight - WIRELESS_VIEW_BACK_HEIGHT) / 2.0f;
            final float listContentX = panelLeft + PADDING;

            final float iconTitleTop = panelTop + WIRELESS_VIEW_ICON_OFFSET;
            final int connectedNodeY = (int) iconTitleTop + WIRELESS_VIEW_ICON_SIZE + 5;
            final int availableTitleY = connectedNodeY + WIRELESS_NODE_BUTTON_BACK_HEIGHT + 5;

            float currentY = availableTitleY + TEXT_HEIGHT + 5;

            if (panelChildren.isEmpty() && availableNodeWidgets.isEmpty()) {
                List<WirelessNode> nodes = WirelessManager.getAvailableWirelessMasters(mainPos);
                for (WirelessNode wirelessNode : nodes) {
                    if (Objects.equals(abilityDeveloperBlockEntity.getWirelessNode(), wirelessNode)) continue;

                    final EditBox editBox = new EditBox(font, 0, 0, EDIT_BOX_WIDTH, EDIT_BOX_HEIGHT, Component.empty());
                    editBox.setTextColor(TEXT_COLOR_DARK);
                    editBox.setBordered(false);

                    ConnectButton button = new ConnectButton(editBox);

                    Pair<EditBox, ConnectButton> widgetPair = Pair.of(editBox, button);
                    availableNodeWidgets.put(wirelessNode, widgetPair);
                    panelChildren.add(editBox);
                    panelChildren.add(button);
                    AbilityDeveloperScreen.this.addRenderableWidget(editBox);
                    AbilityDeveloperScreen.this.addRenderableWidget(button);
                }
            }

            for (WirelessNode wirelessNode : availableNodeWidgets.keySet()) {
                Pair<EditBox, ConnectButton> widgets = availableNodeWidgets.get(wirelessNode);
                EditBox editBox = widgets.getLeft();
                ConnectButton actionButton = widgets.getRight();

                final float nodeEntryY = currentY;
                final int totalControlWidth = EDIT_BOX_WIDTH + actionButton.getWidth() + 5;
                final int buttonX = (int) (listContentX + WIRELESS_NODE_BUTTON_BACK_WIDTH - totalControlWidth - 5) + EDIT_BOX_WIDTH;

                editBox.setPosition((int) (listContentX + WIRELESS_NODE_BUTTON_BACK_WIDTH - totalControlWidth - 5), (int) (nodeEntryY + (TEXT_HEIGHT / 2f)));
                actionButton.setPosition(buttonX, (int) (nodeEntryY + (WIRELESS_NODE_BUTTON_BACK_HEIGHT - ACTION_BUTTON_HEIGHT) / 2.0f));

                currentY += WIRELESS_NODE_BUTTON_BACK_HEIGHT + WIRELESS_NODE_BUTTON_BACK_BLANK_HEIGHT;
            }

            if (!panelChildren.isEmpty() && AbilityDeveloperScreen.this.getFocused() == null) {
                this.setFocused(panelChildren.get(0));
                AbilityDeveloperScreen.this.setFocused(this.focusedChild);
            } else if (panelChildren.isEmpty()) {
                this.setFocused(null);
            }
        }

        public void hide() {
            for (Pair<EditBox, ConnectButton> widgetPair : availableNodeWidgets.values()) {
                AbilityDeveloperScreen.this.removeWidget(widgetPair.getLeft());
                AbilityDeveloperScreen.this.removeWidget(widgetPair.getRight());
            }
            availableNodeWidgets.clear();
            panelChildren.clear();
            focusedChild = null;
            isDragging = false;
        }

        private void closePanel() {
            hide();
            AbilityDeveloperScreen.this.currentPanel = null;

            if (rightPanelBackground.editBox != null && rightPanelBackground.bootFailed && rightPanelBackground.printFinished) {
                rightPanelBackground.editBox.active = true;
                AbilityDeveloperScreen.this.setFocused(rightPanelBackground.editBox);
            } else {
                AbilityDeveloperScreen.this.setFocused(null);
            }
        }


        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(guiGraphics);

            VertexConsumer wirelessViewBack = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_BACK);
            final float panelLeft = (AbilityDeveloperScreen.this.width - WIRELESS_VIEW_BACK_WIDTH) / 2.0f;
            final float panelRight = panelLeft + WIRELESS_VIEW_BACK_WIDTH;
            final float panelTop = (AbilityDeveloperScreen.this.height - WIRELESS_VIEW_BACK_HEIGHT) / 2.0f;
            final float panelBottom = panelTop + WIRELESS_VIEW_BACK_HEIGHT;
            float color = 1.0f;
            wirelessViewBack.vertex(panelLeft, panelTop, 100).color(color, color, color, 1f).uv(0, 0).endVertex();
            wirelessViewBack.vertex(panelLeft, panelBottom, 100).color(color, color, color, 1f).uv(0, 1).endVertex();
            wirelessViewBack.vertex(panelRight, panelBottom, 100).color(color, color, color, 1f).uv(1, 1).endVertex();
            wirelessViewBack.vertex(panelRight, panelTop, 100).color(color, color, color, 1f).uv(1, 0).endVertex();

            final float contentLeft = panelLeft + PADDING;
            final float iconTitleTop = panelTop + WIRELESS_VIEW_ICON_OFFSET;
            final float iconTitleRight = contentLeft + WIRELESS_VIEW_ICON_SIZE;
            final float iconTitleBottom = iconTitleTop + WIRELESS_VIEW_ICON_SIZE;

            VertexConsumer wirelessViewIcon = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_ICON);
            wirelessViewIcon.vertex(contentLeft, iconTitleTop, 100).uv(0, 0).endVertex();
            wirelessViewIcon.vertex(contentLeft, iconTitleBottom, 100).uv(0, 1).endVertex();
            wirelessViewIcon.vertex(iconTitleRight, iconTitleBottom, 100).uv(1, 1).endVertex();
            wirelessViewIcon.vertex(iconTitleRight, iconTitleTop, 100).uv(1, 0).endVertex();

            final int connectedTitleY = (int) iconTitleTop + (WIRELESS_VIEW_ICON_SIZE - TEXT_HEIGHT) / 2;
            final int connectedNodeY = (int) iconTitleTop + WIRELESS_VIEW_ICON_SIZE + 5;
            final int availableTitleY = connectedNodeY + WIRELESS_NODE_BUTTON_BACK_HEIGHT + 5;
            final int availableListStartY = availableTitleY + TEXT_HEIGHT + 5;

            guiGraphics.drawString(font, "Connected", (int) (contentLeft + WIRELESS_VIEW_ICON_SIZE + 5), connectedTitleY, TEXT_COLOR_DARK);
            guiGraphics.drawString(font, "Available", (int) contentLeft, availableTitleY, TEXT_COLOR_DARK);

            renderWirelessButtonVisual(guiGraphics, abilityDeveloperBlockEntity.getWirelessNode(), contentLeft, connectedNodeY, true, mouseX, mouseY);

            float currentListY = availableListStartY;
            for (WirelessNode wirelessNode : availableNodeWidgets.keySet()) {
                renderWirelessButtonVisual(guiGraphics, wirelessNode, contentLeft, currentListY, false, mouseX, mouseY);
                currentListY += WIRELESS_NODE_BUTTON_BACK_HEIGHT + WIRELESS_NODE_BUTTON_BACK_BLANK_HEIGHT;
            }

            for (GuiEventListener child : panelChildren) {
                if (child instanceof Renderable renderable) {
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(0, 0, 1);
                    renderable.render(guiGraphics, mouseX, mouseY, partialTick);
                    guiGraphics.pose().popPose();
                }
            }
        }

        private void renderWirelessButtonVisual(@NotNull GuiGraphics guiGraphics, @Nullable WirelessNode wirelessNode, float x, float y, boolean selectedStyle, int mouseX, int mouseY) {
            VertexConsumer backgroundConsumer = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_BUTTON_BACK);
            boolean isHovered = !selectedStyle && wirelessNode != null && isMouseInside(mouseX, mouseY, x, y, WIRELESS_NODE_BUTTON_BACK_WIDTH, WIRELESS_NODE_BUTTON_BACK_HEIGHT);
            final float bgColor = selectedStyle ? 1.0f : (isHovered ? 0.9f : 0.75f);
            backgroundConsumer.vertex(x, y, 100).color(bgColor, bgColor, bgColor, 1f).uv(0, 0).endVertex();
            backgroundConsumer.vertex(x, y + WIRELESS_NODE_BUTTON_BACK_HEIGHT, 100).color(bgColor, bgColor, bgColor, 1f).uv(0, 1).endVertex();
            backgroundConsumer.vertex(x + WIRELESS_NODE_BUTTON_BACK_WIDTH, y + WIRELESS_NODE_BUTTON_BACK_HEIGHT, 100).color(bgColor, bgColor, bgColor, 1f).uv(1, 1).endVertex();
            backgroundConsumer.vertex(x + WIRELESS_NODE_BUTTON_BACK_WIDTH, y, 100).color(bgColor, bgColor, bgColor, 1f).uv(1, 0).endVertex();

            String name = "N/A";
            float iconColor = 0.75f;
            if (wirelessNode != null) {
                name = wirelessNode.getNodeName();
            } else if (selectedStyle) {
                name = "None";
                iconColor = 1f;
            }

            VertexConsumer iconConsumer = guiGraphics.bufferSource().getBuffer(RENDER_TYPE_WIRELESS_BUTTON_ICON);
            final float iconLeft = x + 5;
            final float nodeIconSize = WIRELESS_NODE_BUTTON_ICON_SIZE;
            final float iconTop = y + (WIRELESS_NODE_BUTTON_BACK_HEIGHT - nodeIconSize) / 2.0f;
            final float iconRight = iconLeft + nodeIconSize;
            final float iconBottom = iconTop + nodeIconSize;
            iconConsumer.vertex(iconLeft, iconTop, 100).color(iconColor, iconColor, iconColor, 1f).uv(0, 0).endVertex();
            iconConsumer.vertex(iconLeft, iconBottom, 100).color(iconColor, iconColor, iconColor, 1f).uv(0, 1).endVertex();
            iconConsumer.vertex(iconRight, iconBottom, 100).color(iconColor, iconColor, iconColor, 1f).uv(1, 1).endVertex();
            iconConsumer.vertex(iconRight, iconTop, 100).color(iconColor, iconColor, iconColor, 1f).uv(1, 0).endVertex();

            guiGraphics.drawString(font, name, (int) (iconLeft + nodeIconSize + 5), (int) (y + (WIRELESS_NODE_BUTTON_BACK_HEIGHT - TEXT_HEIGHT) / 2.0f) + 1, TEXT_COLOR_LIGHT);
        }


        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return panelChildren;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            for (GuiEventListener child : this.children()) {
                if (child.mouseClicked(mouseX, mouseY, button)) {
                    setFocused(child);
                    return true;
                }
            }
            final float panelLeft = (AbilityDeveloperScreen.this.width - WIRELESS_VIEW_BACK_WIDTH) / 2.0f;
            final float panelTop = (AbilityDeveloperScreen.this.height - WIRELESS_VIEW_BACK_HEIGHT) / 2.0f;
            if (!isMouseInside(mouseX, mouseY, panelLeft, panelTop, WIRELESS_VIEW_BACK_WIDTH, WIRELESS_VIEW_BACK_HEIGHT)) {
                this.closePanel();
                return true;
            }
            this.setFocused(null);
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            boolean wasDragging = this.isDragging;
            this.isDragging = false;
            if (this.focusedChild != null) return this.focusedChild.mouseReleased(mouseX, mouseY, button);
            return wasDragging;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (this.isDragging && this.focusedChild != null)
                return this.focusedChild.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            return false;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.closePanel();
                return true;
            }
            if (this.focusedChild != null) {
                if (this.focusedChild.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
            return true;
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            if (this.focusedChild != null) return this.focusedChild.charTyped(codePoint, modifiers);
            return true;
        }

        @Override
        public boolean isDragging() {
            return this.isDragging;
        }

        @Override
        public void setDragging(boolean isDragging) {
            this.isDragging = isDragging;
        }

        @Override
        public @Nullable GuiEventListener getFocused() {
            return this.focusedChild;
        }

        @Override
        public void setFocused(@Nullable GuiEventListener focused) {
            if (this.focusedChild != null) {
                focusedChild.setFocused(false);
            }
            if (focused != null) {
                focused.setFocused(true);
            }

            this.focusedChild = focused;
        }

        @Override
        public boolean isFocused() {
            return focusedChild != null;
        }

        @Override
        public @NotNull NarrationPriority narrationPriority() {
            return NarrationPriority.NONE;
        }

        @Override
        public void updateNarration(@NotNull NarrationElementOutput narrationElementOutput) {
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
        public boolean isFocused = false;

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
            isFocused = focused;
        }

        @Override
        public boolean isFocused() {
            return isFocused;
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
            if (isFocused && !rightPanelBackground.bootFailed && currentPanel == null && button == 0) {
                skillInfoPanel.showSkillInfoPanel(this);
                currentPanel = skillInfoPanel;
                AbilityDeveloperScreen.this.setFocused(skillInfoPanel.getFocused());
                return true;
            }
            return false;
        }
    }

    public class SkillInfoPanel implements Renderable, ContainerEventHandler, NarratableEntry {
        public static final float WIDTH = 256.0f;
        public static final float HEIGHT = 135.0f;
        public static final float BUTTON_WIDTH = 32.0f;
        public static final float BUTTON_HEIGHT = 16.0f;
        public static final ResourceLocation BUTTON_TEXTURE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/developer/button.png");

        @Nullable
        public Skill shownSkill;

        private final List<GuiEventListener> panelChildren = new ArrayList<>();
        @Nullable
        private GuiEventListener focusedChild = null;
        private boolean isDragging = false;
        @Nullable
        private Button learnButton;

        public void showSkillInfoPanel(@NotNull Skill skill) {
            hide();
            this.shownSkill = skill;
            resize(AbilityDeveloperScreen.this.width, AbilityDeveloperScreen.this.height);
        }

        public void resize(int screenWidth, int screenHeight) {
            final float panelLeft = (screenWidth - WIDTH) / 2.0f;
            final float panelTop = (screenHeight - HEIGHT) / 2.0f;
            final int buttonX = (int) (panelLeft + (WIDTH - BUTTON_WIDTH) / 2.0f);
            final int buttonY = (int) (panelTop + HEIGHT - BUTTON_HEIGHT - 10);

            if (learnButton == null) {
                learnButton = new ImageButton(buttonX, buttonY, (int) BUTTON_WIDTH, (int) BUTTON_HEIGHT,
                        0, 0,
                        (int) BUTTON_HEIGHT,
                        BUTTON_TEXTURE,
                        (int) BUTTON_WIDTH * 2, (int) BUTTON_HEIGHT * 2,
                        b -> {
                            AcademyCraft.LOGGER.info("Learn button clicked for skill: " + (shownSkill != null ? shownSkill.texture.getPath() : "null"));
                            this.closePanel();
                        },
                        Component.literal("Learn Skill")
                );
                panelChildren.add(learnButton);
                AbilityDeveloperScreen.this.addRenderableWidget(learnButton);
            } else {
                learnButton.setPosition(buttonX, buttonY);
            }

            if (this.focusedChild == null) {
                setFocused(learnButton);
                AbilityDeveloperScreen.this.setFocused(this.focusedChild);
            }
        }

        public void hide() {
            if (learnButton != null) {
                AbilityDeveloperScreen.this.removeWidget(learnButton);
            }
            panelChildren.clear();
            learnButton = null;
            shownSkill = null;
            focusedChild = null;
            isDragging = false;
        }

        private void closePanel() {
            hide();
            AbilityDeveloperScreen.this.currentPanel = null;
            if (rightPanelBackground.editBox != null && rightPanelBackground.editBox.active) {
                AbilityDeveloperScreen.this.setFocused(rightPanelBackground.editBox);
            } else {
                AbilityDeveloperScreen.this.setFocused(null);
            }
            AcademyCraft.LOGGER.info("Closed Skill Info Panel");
        }

        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            if (AbilityDeveloperScreen.this.currentPanel != this || shownSkill == null) return;

            final float panelLeft = (AbilityDeveloperScreen.this.width - WIDTH) / 2.0f;
            final float panelTop = (AbilityDeveloperScreen.this.height - HEIGHT) / 2.0f;
            renderBackground(guiGraphics);

            if (shownSkill != null) {
                float iconSize = 32;
                Matrix4f iconMatrix = new Matrix4f().translation(panelLeft + 10, panelTop + 10, 0.1f).scale(iconSize);
                Skill.renderIcon(guiGraphics.bufferSource().getBuffer(Skill.RENDER_TYPE_SKILL_ICON.apply(shownSkill.texture)), iconMatrix, true);

                Component skillName = Component.literal(shownSkill.texture.getPath().substring(shownSkill.texture.getPath().lastIndexOf('/') + 1));
                guiGraphics.drawString(font, skillName, (int) (panelLeft + 10 + iconSize + 5), (int) panelTop + 15, TEXT_COLOR_DARK);

                Component description = Component.literal("This is a placeholder description for the skill. It should explain what the skill does and how to use it.");
                int descX = (int) panelLeft + 10;
                int descY = (int) panelTop + 10 + (int) iconSize + 10;
                int wrapWidth = (int) (WIDTH - 20);
                for (FormattedCharSequence line : font.split(description, wrapWidth)) {
                    guiGraphics.drawString(font, line, descX, descY, TEXT_COLOR_DARK);
                    descY += font.lineHeight;
                }
            }

            for (GuiEventListener child : panelChildren) {
                if (child instanceof Renderable renderable) {
                    renderable.render(guiGraphics, mouseX, mouseY, partialTick);
                }
            }
        }

        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return Collections.unmodifiableList(panelChildren);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (learnButton != null && learnButton.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(learnButton);
                if (button == 0) this.setDragging(true);
                return true;
            }

            final float panelLeft = (AbilityDeveloperScreen.this.width - WIDTH) / 2.0f;
            final float panelTop = (AbilityDeveloperScreen.this.height - HEIGHT) / 2.0f;
            if (!isMouseInside(mouseX, mouseY, panelLeft, panelTop, WIDTH, HEIGHT)) {
                this.closePanel();
                return true;
            }

            this.setFocused(null);
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            boolean wasDragging = this.isDragging;
            this.isDragging = false;
            if (this.focusedChild != null) return this.focusedChild.mouseReleased(mouseX, mouseY, button);
            return wasDragging;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (this.isDragging && this.focusedChild != null)
                return this.focusedChild.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            return false;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.closePanel();
                return true;
            }
            if (this.focusedChild != null) return this.focusedChild.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            if (this.focusedChild != null) return this.focusedChild.charTyped(codePoint, modifiers);
            return true;
        }

        @Override
        public boolean isDragging() {
            return this.isDragging;
        }

        @Override
        public void setDragging(boolean isDragging) {
            this.isDragging = isDragging;
        }

        @Override
        public @Nullable GuiEventListener getFocused() {
            return this.focusedChild;
        }

        @Override
        public void setFocused(@Nullable GuiEventListener focused) {
            if (this.focusedChild != focused) {
                if (focused == null || this.panelChildren.contains(focused)) {
                    if (this.focusedChild != null) this.focusedChild.setFocused(false);
                    this.focusedChild = focused;
                    if (this.focusedChild != null) this.focusedChild.setFocused(true);
                } else {
                    if (this.focusedChild != null) this.focusedChild.setFocused(false);
                    this.focusedChild = null;
                }
            }
        }

        @Override
        public boolean isFocused() {
            return focusedChild != null;
        }

        @Override
        public @NotNull NarrationPriority narrationPriority() {
            return NarrationPriority.NONE;
        }

        @Override
        public void updateNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        }
    }

    public static class ConnectButton extends AbstractButton {
        public static final ResourceLocation TEXTURE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/gui/icon/icon_unconnected.png");
        public static final RenderType RENDER_TYPE = new RenderType.CompositeRenderType(
                "developer_button_connect",
                DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS,
                16,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                TEXTURE,
                                true,
                                false
                        ))
                        .setShaderState(RenderUtil.RenderStates.POSITION_COLOR_TEX_SHADER)
                        .setTransparencyState(RenderUtil.RenderStates.TRANSLUCENT_TRANSPARENCY)
                        .createCompositeState(false)
        );
        private final EditBox editBox;

        public ConnectButton(@NotNull EditBox editBox) {
            super(0, 0, 14, 14, Component.empty());
            this.editBox = editBox;
        }

        @Override
        public void onPress() {
            AcademyCraft.LOGGER.info("Connect button pressed." + editBox.getValue());
        }

        @Override
        public void playDownSound(@NotNull SoundManager handler) {
            handler.play(SimpleSoundInstance.forUI(AcademyCraftSoundEvents.SELECT, 1.0F));
        }

        @Override
        protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(RENDER_TYPE);
            float color = isHovered() ? 1.0F : 0.75F;

            final float left = getX();
            final float top = getY();
            final float right = left + getWidth();
            final float bottom = top + getHeight();
            // Left Top
            vertexConsumer.vertex(left, top, 101).color(color, color, color, 1f).uv(0, 0).endVertex();
            // Left Bottom
            vertexConsumer.vertex(left, bottom, 101).color(color, color, color, 1f).uv(0, 1).endVertex();
            // Right Bottom
            vertexConsumer.vertex(right, bottom, 101).color(color, color, color, 1f).uv(1, 1).endVertex();
            // Right Top
            vertexConsumer.vertex(right, top, 101).color(color, color, color, 1f).uv(1, 0).endVertex();
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