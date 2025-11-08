package org.academy.api.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.event.*;
import org.academy.api.client.gui.imgui.ImGuiUtilApi;
import org.academy.api.client.gui.widget.FrameLayoutWidget;
import org.academy.api.client.gui.widget.Widget;
import org.academy.api.client.gui.widget.WidgetContainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class UIScreen extends Screen implements RenderRoot, IAnimationScreen {
    protected final FrameLayoutWidget root = new FrameLayoutWidget();
    private final List<Animator> screenAnimations = new ArrayList<>();
    private final Map<Widget, List<Animator>> trackedAnimations = new HashMap<>();

    protected UIScreen(Component title) {
        super(title);
    }

    @Override
    public WidgetContainer getRoot() {
        return root;
    }

    @Override
    protected void init() {
        ImGuiUtilApi.clearEventsQueue();

        root.setName("root");
        root.clearChildren();

        onInit();
    }

    protected abstract void onInit();

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        var renderTarget = ScreenDispatcher.getRenderTarget();
        if (renderTarget == null) return;
        var colorTextureView = renderTarget.getColorTextureView();
        if (colorTextureView == null) return;

        guiGraphics.submitBlit(
                RenderPipelines.GUI_TEXTURED,
                colorTextureView,
                0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(),
                0, 1, 1, 0, -1
        );
    }

    @Override
    public void removed() {
        super.removed();
        cancelAllAnimations();
    }

    @Override
    public void tick() {
        root.tick();
        super.tick();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (ImGuiUtilApi.wantCaptureMouse()) return;

        var event = MouseEvent.createMoveEvent(mouseX, mouseY);
        root.dispatchEvent(event);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean isDoubleClick) {
        if (ImGuiUtilApi.wantCaptureMouse()) return true;

        var event = MouseEvent.createPressEvent(e.x(), e.y(), e.button());
        root.dispatchEvent(event);

        if (event.isConsumed()) return true;

        return super.mouseClicked(e, isDoubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (ImGuiUtilApi.wantCaptureMouse()) return true;

        var event = MouseEvent.createReleaseEvent(e.x(), e.y(), e.button());
        root.dispatchEvent(event);
        if (event.isConsumed()) return true;

        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double mouseX, double mouseY) {
        if (ImGuiUtilApi.wantCaptureMouse()) return true;

        var event = MouseEvent.createDragEvent(e.x(), e.y(), e.button(), mouseX, mouseY);
        root.dispatchEvent(event);
        if (event.isConsumed()) return true;

        return super.mouseDragged(e, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (ImGuiUtilApi.wantCaptureMouse()) return true;

        var event = new ScrollEvent(mouseX, mouseY, scrollY);
        root.dispatchEvent(event);
        if (event.isConsumed()) return true;

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (ImGuiUtilApi.wantCaptureKeyboard()) return true;

        var event = new KeyEvent(EventType.KEY_PRESSED, e.key(), e.scancode(), e.modifiers());
        root.dispatchEvent(event);
        if (event.isConsumed()) return true;

        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (ImGuiUtilApi.wantCaptureKeyboard()) return true;

        var event = new CharTypedEvent(e.codepoint(), e.modifiers());
        root.dispatchEvent(event);
        if (event.isConsumed()) return true;

        return super.charTyped(e);
    }

    @Override
    public List<Animator> getScreenAnimations() {
        return screenAnimations;
    }

    @Override
    public Map<Widget, List<Animator>> getTrackedAnimations() {
        return trackedAnimations;
    }
}