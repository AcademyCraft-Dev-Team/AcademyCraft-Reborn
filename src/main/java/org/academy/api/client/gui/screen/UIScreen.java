package org.academy.api.client.gui.screen;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.event.*;
import org.academy.api.client.gui.imgui.ImGuiUtilApi;
import org.academy.api.client.gui.render.UIRenderContext;
import org.academy.api.client.gui.widget.FrameLayoutWidget;
import org.academy.api.client.gui.widget.Widget;
import org.academy.api.client.gui.widget.WidgetContainer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class UIScreen extends Screen implements IUIScreen, IAnimationScreen {
    protected final FrameLayoutWidget rootContainer = new FrameLayoutWidget();
    private final List<Animator> screenAnimations = new ArrayList<>();
    private final Map<Widget, List<Animator>> trackedAnimations = new HashMap<>();
    private final UIRenderContext uiRenderContext = new UIRenderContext();
    @Nullable
    private RenderTarget renderTarget;

    protected UIScreen(Component title) {
        super(title);
    }

    @Override
    @Nullable
    public RenderTarget getRenderTarget() {
        return renderTarget;
    }

    @Override
    public WidgetContainer getRootContainer() {
        return rootContainer;
    }

    @Override
    public UIRenderContext getUIRenderContext() {
        return uiRenderContext;
    }

    @Override
    protected void init() {
        var minecraft = Minecraft.getInstance();
        minecraft.execute(this::initializeRenderResources);

        rootContainer.setName("root");
        rootContainer.clearChildren();

        onInit();
    }

    private void initializeRenderResources() {
        var window = Minecraft.getInstance().getWindow();
        if (renderTarget != null) {
            renderTarget.resize(window.getWidth(), window.getHeight());
        } else {
            renderTarget = new TextureTarget(null, window.getWidth(), window.getHeight(), true);
        }
        ImGuiUtilApi.clearEventsQueue();
    }

    protected abstract void onInit();

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
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

        if (renderTarget == null) return;

        var contextToClose = uiRenderContext;
        var targetToDestroy = renderTarget;

        renderTarget = null;

        Minecraft.getInstance().execute(() -> {
            contextToClose.close();
            targetToDestroy.destroyBuffers();
        });
    }

    @Override
    public void tick() {
        rootContainer.tick();
        super.tick();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (ImGuiUtilApi.wantCaptureMouse()) return;

        var event = MouseEvent.createMoveEvent(mouseX, mouseY);
        rootContainer.dispatchEvent(event);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean isDoubleClick) {
        if (ImGuiUtilApi.wantCaptureMouse()) return true;

        var event = MouseEvent.createPressEvent(e.x(), e.y(), e.button());
        rootContainer.dispatchEvent(event);

        if (event.isConsumed()) return true;

        return super.mouseClicked(e, isDoubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (ImGuiUtilApi.wantCaptureMouse()) return true;

        var event = MouseEvent.createReleaseEvent(e.x(), e.y(), e.button());
        rootContainer.dispatchEvent(event);
        if (event.isConsumed()) return true;

        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double mouseX, double mouseY) {
        if (ImGuiUtilApi.wantCaptureMouse()) return true;

        var event = MouseEvent.createDragEvent(e.x(), e.y(), e.button(), mouseX, mouseY);
        rootContainer.dispatchEvent(event);
        if (event.isConsumed()) return true;

        return super.mouseDragged(e, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (ImGuiUtilApi.wantCaptureMouse()) return true;

        var event = new ScrollEvent(mouseX, mouseY, scrollY);
        rootContainer.dispatchEvent(event);
        if (event.isConsumed()) return true;

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (ImGuiUtilApi.wantCaptureKeyboard()) return true;

        var event = new KeyEvent(EventType.KEY_PRESSED, e.key(), e.scancode(), e.modifiers());
        rootContainer.dispatchEvent(event);
        if (event.isConsumed()) return true;

        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (ImGuiUtilApi.wantCaptureKeyboard()) return true;

        var event = new CharTypedEvent(e.codepoint(), e.modifiers());
        rootContainer.dispatchEvent(event);
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