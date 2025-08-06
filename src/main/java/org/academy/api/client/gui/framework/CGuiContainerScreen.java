package org.academy.api.client.gui.framework;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.event.*;
import org.academy.api.client.gui.widget.BlendQuadWidget;
import org.academy.api.client.gui.widget.ImageWidget;
import org.academy.api.client.gui.widget.PanelWidget;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.render.RenderTypes;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class CGuiContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> implements IAnimationScreen {
    public BlendQuadWidget back;
    public ImageWidget inventory;
    public final PanelWidget rootContainer = new PanelWidget(0, 0, 0, 0);
    public boolean handleContainer = true;
    public boolean renderInventory = true;
    private final List<Animator> screenAnimations = new ArrayList<>();
    private final Map<Widget, List<Animator>> trackedAnimations = new HashMap<>();

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
    }

    @Override
    protected void init() {
        super.init();
        rootContainer.setWidth(width);
        rootContainer.setHeight(height);

        var finalHeight = 187f;

        back = new BlendQuadWidget(0, 0, imageWidth, finalHeight);
        back.setHeight(0);
        back.setAlpha(0f);

        inventory = new ImageWidget(0, 0, imageWidth, finalHeight,
                RenderTypes.INVENTORY);
        inventory.setHeight(0);
        inventory.setAlpha(0f);

        var duration = 600L;
        playAnimation(ObjectAnimator.ofFloat(back::setHeight, 0, finalHeight).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_EXPO));
        playAnimation(ObjectAnimator.ofFloat(inventory::setHeight, 0, finalHeight).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_EXPO));
        playAnimation(ObjectAnimator.ofFloat(back::setAlpha, 0, 0.5f).setDuration(duration).setInterpolator(EasingFunctions.LINEAR));
        playAnimation(ObjectAnimator.ofFloat(inventory::setAlpha, 0, 1.0f).setDuration(duration).setInterpolator(EasingFunctions.LINEAR));

        onInit();
    }

    protected abstract void onInit();

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBlurredBackground(partialTick);
        renderMenuBackground(guiGraphics);

        var stack = new MatrixStack();
        var bufferSource = guiGraphics.bufferSource();
        if (shouldRenderInventory()) {
            var originHeight = 187f;
            var currentHeight = inventory.getHeight();
            var scaleY = currentHeight / originHeight;
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, topPos, 0);
            guiGraphics.pose().scale(1, scaleY, 1);
            guiGraphics.pose().translate(0, -topPos, 0);

            stack.pushPose();
            stack.translate(leftPos, topPos - 22, 0);
            back.render(stack, bufferSource, mouseX, mouseY, partialTick);
            inventory.render(stack, bufferSource, mouseX, mouseY, partialTick);
            stack.popPose();
            rootContainer.render(stack, bufferSource, mouseX, mouseY, partialTick);

            super.render(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.pose().popPose();
        } else {
            rootContainer.render(stack, bufferSource, mouseX, mouseY, partialTick);
        }
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var event = MouseEvent.createPressEvent(mouseX, mouseY, button);
        rootContainer.dispatchEvent(event);
        var rootResult = event.isConsumed();

        var superResult = false;
        if (shouldHandleContainer()) {
            superResult = super.mouseClicked(mouseX, mouseY, button);
        }

        return rootResult || superResult;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        var event = MouseEvent.createReleaseEvent(mouseX, mouseY, button);
        rootContainer.dispatchEvent(event);
        var rootResult = event.isConsumed();

        var superResult = false;
        if (shouldHandleContainer()) {
            superResult = super.mouseReleased(mouseX, mouseY, button);
        }

        return rootResult || superResult;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        var event = MouseEvent.createDragEvent(mouseX, mouseY, button, dragX, dragY);
        rootContainer.dispatchEvent(event);
        var rootResult = event.isConsumed();

        var superResult = false;
        if (shouldHandleContainer()) {
            superResult = super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        return rootResult || superResult;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        var event = new ScrollEvent(mouseX, mouseY, scrollY);
        rootContainer.dispatchEvent(event);
        var rootResult = event.isConsumed();

        var superResult = false;
        if (shouldHandleContainer()) {
            superResult = super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        return rootResult || superResult;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F12) {
            AcademyCraft.DEBUG_UI = !AcademyCraft.DEBUG_UI;
            return true;
        }

        var event = new KeyEvent(EventType.KEY_PRESSED, keyCode, scanCode, modifiers);
        rootContainer.dispatchEvent(event);
        if (event.isConsumed()) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && shouldCloseOnEsc()) {
            onClose();
            return true;
        }
        return shouldHandleContainer() && super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        var event = new CharTypedEvent(codePoint, modifiers);
        rootContainer.dispatchEvent(event);
        if (event.isConsumed()) {
            return true;
        }
        return shouldHandleContainer() && super.charTyped(codePoint, modifiers);
    }

    public boolean shouldHandleContainer() {
        return handleContainer;
    }

    public boolean shouldRenderInventory() {
        return renderInventory;
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        return mouseX < (double) guiLeft || mouseY < (double) guiTop - 22 || mouseX >= (double) (guiLeft + imageWidth) || mouseY >= (double) (guiTop + imageHeight);
    }

    public int getTopPos() {
        return topPos;
    }
}