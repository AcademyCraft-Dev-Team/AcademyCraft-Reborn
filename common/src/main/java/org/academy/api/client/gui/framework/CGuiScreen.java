package org.academy.api.client.gui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.Tickable;
import net.minecraft.network.chat.Component;
import org.academy.api.client.gui.animation.Animator;
import org.academy.api.client.gui.widget.PanelWidget;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class CGuiScreen extends Screen {
    public final AbstractContainerWidget rootContainer = new PanelWidget(0, 0, 0, 0);
    private final List<Animator> screenAnimations = new ArrayList<>();
    private final Map<Widget, List<Animator>> trackedAnimations = new HashMap<>();

    protected CGuiScreen(Component title) {
        super(title);
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
        rootContainer.clearChildren();
        rootContainer.setWidth(width);
        rootContainer.setHeight(height);
        onInit();
    }

    protected abstract void onInit();

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        final var mouseHandler = Minecraft.getInstance().mouseHandler;
        guiGraphics.pose().pushPose();
        rootContainer.render(guiGraphics, mouseHandler.xpos(), mouseHandler.ypos(), partialTick);
        guiGraphics.pose().popPose();
    }

    @Override
    public void tick() {
        for (var widget : rootContainer.getAllWidgets()){
            if (widget instanceof Tickable tickable){
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
        return rootContainer.mousePressed(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return rootContainer.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return rootContainer.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return rootContainer.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (rootContainer.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (rootContainer.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }
}