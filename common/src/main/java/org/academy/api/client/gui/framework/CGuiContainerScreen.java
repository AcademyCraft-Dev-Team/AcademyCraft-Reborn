package org.academy.api.client.gui.framework;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.Tickable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.academy.api.client.gui.animation.AnimationTopToBottom;
import org.academy.api.client.gui.widget.ImageWidget;
import org.academy.api.client.gui.widget.PanelWidget;
import org.academy.api.client.resource.TextureResources;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

public abstract class CGuiContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    public ImageWidget back;
    public ImageWidget inventory;
    public final AbstractContainerWidget rootContainer = new PanelWidget(0, 0, 0, 0);
    public boolean handleContainer = true;
    public boolean renderInventory = true;

    protected CGuiContainerScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        back = new ImageWidget(leftPos, topPos - 22, imageWidth, 187,
                TextureResources.RenderTypes.RENDER_TYPE_ELEMENT_BACK_DARK);
        inventory = new ImageWidget(leftPos, topPos - 22, imageWidth, 187,
                TextureResources.RenderTypes.RENDER_TYPE_INVENTORY);
        AnimationTopToBottom invAnim = new AnimationTopToBottom(inventory);
        invAnim.currentHeight = inventory.height / 1.25f;
        inventory.animation = invAnim;
        back.animation = new AnimationTopToBottom(back);
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
            if (inventory.animation instanceof AnimationTopToBottom animationTopToBottom) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().scale(1, animationTopToBottom.currentHeight / animationTopToBottom.originHeight, 1);
                super.render(guiGraphics, mouseX, mouseY, partialTick);
                guiGraphics.pose().popPose();
            } else {
                super.render(guiGraphics, mouseX, mouseY, partialTick);
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
        for (Widget widget : rootContainer.getAllWidgets()) {
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
        boolean rootResult = rootContainer.mouseClicked(mouseX, mouseY, button);
        boolean superResult = shouldHandleContainer() && super.mouseClicked(mouseX, mouseY, button);
        return rootResult || superResult;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean rootResult = rootContainer.mouseReleased(mouseX, mouseY, button);
        boolean superResult = shouldHandleContainer() && super.mouseReleased(mouseX, mouseY, button);
        return superResult || rootResult;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        boolean rootResult = rootContainer.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        boolean superResult = shouldHandleContainer() && super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        return superResult || rootResult;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        boolean rootResult = rootContainer.mouseScrolled(mouseX, mouseY, delta);
        boolean superResult = shouldHandleContainer() && super.mouseScrolled(mouseX, mouseY, delta);
        return superResult || rootResult;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.shouldCloseOnEsc()) {
            this.onClose();
            return true;
        }
        boolean rootResult = rootContainer.keyPressed(keyCode, scanCode, modifiers);
        boolean superResult = shouldHandleContainer() && super.keyPressed(keyCode, scanCode, modifiers);
        return superResult || rootResult;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        boolean rootResult = rootContainer.charTyped(codePoint, modifiers);
        boolean superResult = shouldHandleContainer() && super.charTyped(codePoint, modifiers);
        return superResult || rootResult;
    }

    public boolean shouldHandleContainer() {
        return handleContainer;
    }

    public boolean shouldRenderInventory() {
        return renderInventory;
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        return mouseX < (double) guiLeft || mouseY < (double) guiTop - 22 || mouseX >= (double) (guiLeft + this.imageWidth) || mouseY >= (double) (guiTop + this.imageHeight);
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