package org.academy.api.client.gui.framework;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.Tickable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.widget.BlendQuadWidget;
import org.academy.api.client.gui.widget.ImageWidget;
import org.academy.api.client.gui.widget.PanelWidget;
import org.academy.api.client.renderer.RenderTypes;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class CGuiContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    public BlendQuadWidget back;
    public ImageWidget inventory;
    public final AbstractContainerWidget rootContainer = new PanelWidget(0, 0, 0, 0);
    public boolean handleContainer = true;
    public boolean renderInventory = true;
    private final List<Animator> screenAnimations = new ArrayList<>();
    private final Map<Widget, List<Animator>> trackedAnimations = new HashMap<>();

    protected CGuiContainerScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    protected void playAnimation(Animator animator) {
        screenAnimations.add(animator);
        animator.start();
    }

    public void playTrackedAnimation(Widget widget, Animator animator) {
        playAnimation(animator);
        trackedAnimations.computeIfAbsent(widget, k -> new ArrayList<>()).add(animator);
    }

    public void cancelAnimations(Widget widget) {
        if (trackedAnimations.containsKey(widget)) {
            var animators = new ArrayList<>(trackedAnimations.get(widget));
            for (var anim : animators) {
                anim.cancel();
                screenAnimations.remove(anim);
            }
            trackedAnimations.get(widget).clear();
        }
    }

    @Override
    public void removed() {
        super.removed();
        for (var anim : screenAnimations) {
            anim.cancel();
        }
        screenAnimations.clear();
        trackedAnimations.clear();
    }

    @Override
    protected void init() {
        super.init();
        rootContainer.setWidth(width);
        rootContainer.setHeight(height);

        var finalHeight = 187f;

        back = new BlendQuadWidget(leftPos, topPos - 22, imageWidth, finalHeight);
        back.red = 0;
        back.green = 0;
        back.blue = 0;
        back.setHeight(0);
        back.setAlpha(0f);

        inventory = new ImageWidget(leftPos, topPos - 22, imageWidth, finalHeight,
                RenderTypes.RENDER_TYPE_INVENTORY);
        inventory.setHeight(0);
        inventory.setAlpha(0f);

        var duration = 600L;
        playAnimation(ObjectAnimator.ofFloat(back::setHeight, 0, finalHeight).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_EXPO));
        playAnimation(ObjectAnimator.ofFloat(inventory::setHeight, 0, finalHeight).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_EXPO));
        playAnimation(ObjectAnimator.ofFloat(back::setAlpha, 0, 0.5f).setDuration(duration - 200).setInterpolator(EasingFunctions.LINEAR).setStartDelay(150));
        playAnimation(ObjectAnimator.ofFloat(inventory::setAlpha, 0, 1.0f).setDuration(duration - 200).setInterpolator(EasingFunctions.LINEAR).setStartDelay(150));

        onInit();
    }

    protected abstract void onInit();

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        if (renderInventory) {
            back.render(guiGraphics, mouseX, mouseY, partialTick);
            inventory.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        rootContainer.render(guiGraphics, mouseX, mouseY, partialTick);
        if (shouldRenderInventory()) {
            var originHeight = 187f;
            var currentHeight = inventory.getHeight();
            if (currentHeight > 1e-6f) {
                var scaleY = currentHeight / originHeight;
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, topPos, 0);
                guiGraphics.pose().scale(1, scaleY, 1);
                guiGraphics.pose().translate(0, -topPos, 0);

                super.render(guiGraphics, mouseX, mouseY, partialTick);
                guiGraphics.pose().popPose();
            }
        }
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    }

    @Override
    protected void containerTick() {
        for (var widget : rootContainer.getAllWidgets()) {
            if (widget instanceof Tickable tickable) {
                tickable.tick();
            }
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        rootContainer.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        org.academy.AcademyCraft.LOGGER.debug("CGuiContainerScreen mouseClicked at ({}, {})", mouseX, mouseY);
        var rootResult = rootContainer.mousePressed(mouseX, mouseY, button);
        if (rootResult) {
            org.academy.AcademyCraft.LOGGER.debug("rootContainer consumed the click.");
        }
        var superResult = shouldHandleContainer() && super.mouseClicked(mouseX, mouseY, button);
        if (superResult) {
            org.academy.AcademyCraft.LOGGER.debug("super (vanilla container) consumed the click.");
        }
        return rootResult || superResult;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        var rootResult = rootContainer.mouseReleased(mouseX, mouseY, button);
        var superResult = shouldHandleContainer() && super.mouseReleased(mouseX, mouseY, button);
        return superResult || rootResult;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        var rootResult = rootContainer.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        var superResult = shouldHandleContainer() && super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        return superResult || rootResult;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        var rootResult = rootContainer.mouseScrolled(mouseX, mouseY, delta);
        var superResult = shouldHandleContainer() && super.mouseScrolled(mouseX, mouseY, delta);
        return superResult || rootResult;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (rootContainer.keyPressed(keyCode, scanCode, modifiers)) {
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
        if (rootContainer.charTyped(codePoint, modifiers)) {
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

    public int getLeftPos() {
        return leftPos;
    }

    public int getTopPos() {
        return topPos;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }
}