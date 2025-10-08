package org.academy.api.client.gui.framework;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.academy.AcademyCraft;
import org.academy.api.client.Resource;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.event.*;
import org.academy.api.client.gui.widget.BlendQuadWidget;
import org.academy.api.client.gui.widget.ImageWidget;
import org.academy.api.client.gui.widget.PanelWidget;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class CGuiContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> implements IAnimationScreen {
    @Nullable
    public BlendQuadWidget back;
    @Nullable
    public ImageWidget inventory;
    public final PanelWidget rootContainer = new PanelWidget(0, 0, 0, 0);
    public boolean handleContainer = true;
    public boolean renderInventory = true;
    private final List<Animator> screenAnimations = new ArrayList<>();
    private final Map<Widget, List<Animator>> trackedAnimations = new HashMap<>();
    private final UIRenderContext uiRenderContext = new UIRenderContext();
    private final RenderTarget renderTarget;

    {
        var window = Minecraft.getInstance().getWindow();
        renderTarget = new TextureTarget(null, window.getWidth(), window.getHeight(), true);
    }

    protected CGuiContainerScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    public List<Animator> getScreenAnimations() {
        return screenAnimations;
    }

    @Override
    public Map<Widget, List<Animator>> getTrackedAnimations() {
        return trackedAnimations;
    }

    @Override
    public void removed() {
        super.removed();
        cancelAllAnimations();
        renderTarget.destroyBuffers();
        uiRenderContext.close();
    }

    @Override
    protected void init() {
        super.init();
        var window = Minecraft.getInstance().getWindow();
        renderTarget.resize(window.getWidth(), window.getHeight());
        rootContainer.setWidth(width);
        rootContainer.setHeight(height);
        rootContainer.clearChildren();

        var finalHeight = 187f;

        back = new BlendQuadWidget(leftPos, topPos - 22, imageWidth, finalHeight);
        back.setHeight(0);
        back.setAlpha(0.5f);
        rootContainer.addChild("back", back);

        inventory = new ImageWidget(leftPos, topPos - 22, imageWidth, finalHeight, Resource.Textures.INVENTORY) {
            @Override
            public void render(WidgetRenderContext renderContext, double mouseX, double mouseY, float partialTick) {
                if (shouldRenderInventory()) {
                    super.render(renderContext, mouseX, mouseY, partialTick);
                }
            }
        };
        inventory.setHeight(0);
        inventory.setZ(1);
        rootContainer.addChild("inventory", inventory);

        var duration = 600L;
        playAnimation(ObjectAnimator.ofFloat(back::setHeight, 0, finalHeight).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_EXPO));
        playAnimation(ObjectAnimator.ofFloat(inventory::setHeight, 0, finalHeight).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_EXPO));
        playAnimation(ObjectAnimator.ofFloat(rootContainer::setAlpha, 0, 1.0f).setDuration(duration).setInterpolator(EasingFunctions.LINEAR));

        onInit();
    }

    protected abstract void onInit();

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        var colorTexture = renderTarget.getColorTexture();
        var depthTexture = renderTarget.getDepthTexture();
        var colorTextureView = renderTarget.getColorTextureView();

        if (colorTexture == null || depthTexture == null || colorTextureView == null) return;

        commandEncoder.clearColorAndDepthTextures(colorTexture, 0, depthTexture, 1);

        uiRenderContext.renderFrame(rootContainer, renderTarget, mouseX, mouseY, partialTick);
        guiGraphics.submitBlit(RenderPipelines.GUI_TEXTURED, colorTextureView, 0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), 0, 1, 1, 0, -1);

        if (shouldRenderInventory()) {
            var originHeight = 187f;
            var currentHeight = inventory.getHeight();
            var scaleY = currentHeight / originHeight;
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate(0, topPos);
            guiGraphics.pose().scale(1, scaleY);
            guiGraphics.pose().translate(0, -topPos);

            renderContents(guiGraphics, mouseX, mouseY, partialTick);
            renderCarriedItem(guiGraphics, mouseX, mouseY);
            renderSnapbackItem(guiGraphics);

            guiGraphics.pose().popMatrix();
        }

        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    protected void containerTick() {
        rootContainer.tick();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        var event = MouseEvent.createMoveEvent(mouseX, mouseY);
        rootContainer.dispatchEvent(event);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        var event = new ScrollEvent(mouseX, mouseY, scrollY);
        rootContainer.dispatchEvent(event);
        var rootResult = event.isConsumed();

        var superResult = false;
        if (shouldHandleContainer()) superResult = super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

        return rootResult || superResult;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        var event = MouseEvent.createReleaseEvent(e.x(), e.y(), e.button());
        rootContainer.dispatchEvent(event);
        var rootResult = event.isConsumed();

        var superResult = false;
        if (shouldHandleContainer()) superResult = super.mouseReleased(e);

        return rootResult || superResult;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double mouseX, double mouseY) {
        var event = MouseEvent.createDragEvent(e.x(), e.y(), e.button(), mouseX, mouseY);
        rootContainer.dispatchEvent(event);
        var rootResult = event.isConsumed();

        var superResult = false;
        if (shouldHandleContainer()) superResult = super.mouseDragged(e, mouseX, mouseY);

        return rootResult || superResult;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean isDoubleClick) {
        var event = MouseEvent.createPressEvent(e.x(), e.y(), e.button());
        rootContainer.dispatchEvent(event);
        var rootResult = event.isConsumed();

        var superResult = false;
        if (shouldHandleContainer()) superResult = super.mouseClicked(e, isDoubleClick);

        return rootResult || superResult;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_F12) {
            AcademyCraft.DEBUG_UI = !AcademyCraft.DEBUG_UI;
            return true;
        }

        var event = new KeyEvent(EventType.KEY_PRESSED, e.key(), e.scancode(), e.modifiers());
        rootContainer.dispatchEvent(event);
        if (event.isConsumed()) return true;
        if (e.key() == GLFW.GLFW_KEY_ESCAPE && shouldCloseOnEsc()) {
            onClose();
            return true;
        }
        return shouldHandleContainer() && super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        var event = new CharTypedEvent(e.codepoint(), e.modifiers());
        rootContainer.dispatchEvent(event);
        if (event.isConsumed()) return true;
        return shouldHandleContainer() && super.charTyped(e);
    }

    public boolean shouldHandleContainer() {
        return handleContainer;
    }

    public boolean shouldRenderInventory() {
        return renderInventory;
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop) {
        return mouseX < (double) guiLeft || mouseY < (double) guiTop - 22 || mouseX >= (double) (guiLeft + imageWidth) || mouseY >= (double) (guiTop + imageHeight);
    }

    public int getTopPos() {
        return topPos;
    }
}