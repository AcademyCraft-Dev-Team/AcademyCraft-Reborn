package org.academy.api.client.gui.framework;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.Tickable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.academy.api.client.gui.ImageResources;
import org.academy.api.client.gui.widgets.ImageWidget;
import org.academy.api.client.gui.widgets.PanelWidget;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

public abstract class CGuiContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    private ImageWidget back;
    private ImageWidget inventory;
    public final AbstractContainerWidget rootContainer = new PanelWidget(0, 0, 0, 0);
    public boolean containerActive = true;

    protected CGuiContainerScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        back = new ImageWidget(leftPos, topPos - 22, imageWidth, 187,
                ImageResources.RenderTypes.RENDER_TYPE_ELEMENT_BACK_DARK);
        inventory = new ImageWidget(leftPos, topPos - 22, imageWidth, 187,
                ImageResources.RenderTypes.RENDER_TYPE_INVENTORY);
        onInit();
    }

    protected abstract void onInit();

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (isContainerActive()) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        } else {
            renderBg(guiGraphics, partialTick, mouseX, mouseY);
        }
        rootContainer.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        renderBackground(guiGraphics);
        if (back != null && inventory != null) {
            if (isContainerActive()) {
                back.render(guiGraphics, mouseX, mouseY, partialTick);
                inventory.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
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
        boolean superResult = isContainerActive() && super.mouseClicked(mouseX, mouseY, button);
        return rootResult || superResult;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean rootResult = rootContainer.mouseReleased(mouseX, mouseY, button);
        boolean superResult = isContainerActive() && super.mouseReleased(mouseX, mouseY, button);
        return superResult || rootResult;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        boolean rootResult = rootContainer.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        boolean superResult = isContainerActive() && super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        return superResult || rootResult;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        boolean rootResult = rootContainer.mouseScrolled(mouseX, mouseY, delta);
        boolean superResult = isContainerActive() && super.mouseScrolled(mouseX, mouseY, delta);
        return superResult || rootResult;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.shouldCloseOnEsc()) {
            this.onClose();
            return true;
        }
        boolean rootResult = rootContainer.keyPressed(keyCode, scanCode, modifiers);
        boolean superResult = isContainerActive() && super.keyPressed(keyCode, scanCode, modifiers);
        return superResult || rootResult;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        boolean rootResult = rootContainer.charTyped(codePoint, modifiers);
        boolean superResult = isContainerActive() && super.charTyped(codePoint, modifiers);
        return superResult || rootResult;
    }

    public boolean isContainerActive() {
        return containerActive;
    }
}