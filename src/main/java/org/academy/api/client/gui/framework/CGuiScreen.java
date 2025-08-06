package org.academy.api.client.gui.framework;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.event.*;
import org.academy.api.client.gui.widget.PanelWidget;
import org.academy.api.client.render.MatrixStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class CGuiScreen extends Screen implements IAnimationScreen {
    public final AbstractContainerWidget rootContainer = new PanelWidget(0, 0, 0, 0);
    private final List<Animator> screenAnimations = new ArrayList<>();
    private final Map<Widget, List<Animator>> trackedAnimations = new HashMap<>();

    protected CGuiScreen(Component title) {
        super(title);
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
        rootContainer.clearChildren();
        rootContainer.setWidth(width);
        rootContainer.setHeight(height);
        onInit();
    }

    protected abstract void onInit();

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        var stack = new MatrixStack();
        var bufferSource = guiGraphics.bufferSource();
        stack.pushPose();
        rootContainer.render(stack, bufferSource, mouseX, mouseY, partialTick);
        stack.popPose();
    }

    @Override
    public void tick() {
        rootContainer.tick();
        super.tick();
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
        
        if (event.isConsumed()) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        var event = MouseEvent.createReleaseEvent(mouseX, mouseY, button);
        rootContainer.dispatchEvent(event);
        if (event.isConsumed()) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        var event = MouseEvent.createDragEvent(mouseX, mouseY, button, dragX, dragY);
        rootContainer.dispatchEvent(event);
        if (event.isConsumed()) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        
        var event = new ScrollEvent(mouseX, mouseY, scrollY);
        rootContainer.dispatchEvent(event);
        if (event.isConsumed()) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        var event = new CharTypedEvent(codePoint, modifiers);
        rootContainer.dispatchEvent(event);
        if (event.isConsumed()) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }
}