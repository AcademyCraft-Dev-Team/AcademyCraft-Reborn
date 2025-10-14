package org.academy.api.client.gui.framework;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.event.*;
import org.academy.api.client.gui.framework.imgui.ImGuiUIDebugger;
import org.academy.api.client.gui.imgui.ImGuiUtilApi;
import org.academy.api.client.gui.widget.PanelWidget;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class CGuiScreen extends Screen implements IAnimationScreen {
    public final AbstractContainerWidget rootContainer = new PanelWidget(0, 0, 0, 0);
    private final List<Animator> screenAnimations = new ArrayList<>();
    private final Map<Widget, List<Animator>> trackedAnimations = new HashMap<>();
    private final UIRenderContext uiRenderContext = new UIRenderContext();
    private final RenderTarget renderTarget;

    {
        var window = Minecraft.getInstance().getWindow();
        renderTarget = new TextureTarget(null, window.getWidth(), window.getHeight(), true);
        ImGuiUtilApi.clearEventsQueue();
    }

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
        renderTarget.destroyBuffers();
        uiRenderContext.close();
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        var colorTexture = renderTarget.getColorTexture();
        var depthTexture = renderTarget.getDepthTexture();
        var colorTextureView = renderTarget.getColorTextureView();

        if (colorTexture == null || depthTexture == null || colorTextureView == null) return;

        commandEncoder.clearColorAndDepthTextures(colorTexture, 0, depthTexture, 1);

        uiRenderContext.renderFrame(rootContainer, renderTarget, mouseX, mouseY, partialTick);
        ImGuiUIDebugger.render(renderTarget, rootContainer);
        guiGraphics.submitBlit(RenderPipelines.GUI_TEXTURED, colorTextureView, 0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), 0, 1, 1, 0, -1);
    }

    @Override
    public void onClose() {
        super.onClose();
        uiRenderContext.close();
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
    public boolean mouseClicked(MouseButtonEvent e, boolean isDoubleClick) {
        var event = MouseEvent.createPressEvent(e.x(), e.y(), e.button());
        rootContainer.dispatchEvent(event);

        if (event.isConsumed()) return true;

        return super.mouseClicked(e, isDoubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        var event = MouseEvent.createReleaseEvent(e.x(), e.y(), e.button());
        rootContainer.dispatchEvent(event);
        if (event.isConsumed()) return true;

        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double mouseX, double mouseY) {
        var event = MouseEvent.createDragEvent(e.x(), e.y(), e.button(), mouseX, mouseY);
        rootContainer.dispatchEvent(event);
        if (event.isConsumed()) return true;

        return super.mouseDragged(e, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        var event = new ScrollEvent(mouseX, mouseY, scrollY);
        rootContainer.dispatchEvent(event);
        if (event.isConsumed()) return true;

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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

        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        var event = new CharTypedEvent(e.codepoint(), e.modifiers());
        rootContainer.dispatchEvent(event);
        if (event.isConsumed()) return true;

        return super.charTyped(e);
    }
}