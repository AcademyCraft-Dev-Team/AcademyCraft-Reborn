package org.academy.api.client.gui.framework;

import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractContainerWidget extends AbstractWidget implements WidgetContainer {
    protected final Map<String, Widget> children = new LinkedHashMap<>();
    protected Widget focusedChild = null;

    public AbstractContainerWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    @Override
    public void addChild(String name, Widget child) {
        if (child.getParent() != null) {
            child.getParent().removeChild(name);
        }
        this.children.put(name, child);
        child.setParent(this);
    }

    @Override
    public void removeChild(String name) {
        if (children.containsKey(name)) {
            Widget widget = children.get(name);
            widget.setParent(null);
            if (focusedChild == widget) {
                focusedChild = null;
            }
            children.remove(name);
        }
    }

    @Override
    public void clearChildren() {
        children.clear();
    }


    @Override
    public Map<String, Widget> getChildren() {
        return children;
    }

    @NotNull
    @Override
    public Iterator<Widget> iterator() {
        return children.values().iterator();
    }

    protected void setFocusedChild(Widget child) {
        if (focusedChild != null) {
            focusedChild.setFocused(false);
        }
        focusedChild = child;
        if (focusedChild != null) {
            focusedChild.setFocused(true);
        }
    }

    @Override
    public Widget getWidgetAt(double mouseX, double mouseY) {
        for (Widget child : children.values()) {
            if (child.isVisible() && child.isMouseOver(mouseX, mouseY)) {
                if (child instanceof WidgetContainer containerChild) {
                    Widget inner = containerChild.getWidgetAt(mouseX, mouseY);
                    if (inner != null) {
                        return inner;
                    }
                }
                return child;
            }
        }
        return null;
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        if (!isVisible()) return;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.x, this.y, 0);

        for (Widget child : children.values()) {
            child.render(guiGraphics, mouseX, mouseY, partialTicks);
        }

        guiGraphics.pose().popPose();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!isVisible() || !isEnabled()) return;
        for (Widget child : children.values()) {
            child.setHovered(child.isMouseOver(mouseX, mouseY));
            if (child.shouldFocus()) {
                if (child.isFocused() && child.isEnabled()) {
                    child.mouseMoved(mouseX, mouseY);
                }
            } else {
                child.mouseMoved(mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isEnabled()) return false;

        Widget target = getWidgetAt(mouseX, mouseY);
        if (target != null) {
            if (target.canFocus()) {
                setFocusedChild(target);
            }
        }

        for (Widget child : children.values()) {
            child.setHovered(child.isMouseOver(mouseX, mouseY));
            if (!child.shouldFocus()) {
                child.mouseClicked(mouseX, mouseY, button);
            } else {
                if (child.isFocused() && child.isEnabled()) {
                    child.mouseClicked(mouseX, mouseY, button);
                }
            }
        }

        if (isMouseOver(mouseX, mouseY)) {
            if (this.canFocus()) {
                setFocusedChild(null);
            }
        }

        if (target == null && focusedChild != null) {
            setFocusedChild(null);
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isVisible() || !isEnabled()) return false;

        if (focusedChild != null && focusedChild.isEnabled()) {
            return focusedChild.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!isVisible() || !isEnabled()) return false;

        if (focusedChild != null && focusedChild.isEnabled()) {
            return focusedChild.charTyped(codePoint, modifiers);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isEnabled()) return false;
        Widget target = getWidgetAt(mouseX, mouseY);
        if (target != null && target != this && target.isEnabled()) {
            return target.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!isVisible() || !isEnabled()) return false;
        Widget target = getWidgetAt(mouseX, mouseY);
        if (target != null && target != this && target.isEnabled()) {
            return target.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isVisible() || !isEnabled()) return false;
        Widget target = getWidgetAt(mouseX, mouseY);
        if (target != null && target != this && target.isEnabled()) {
            return target.mouseScrolled(mouseX, mouseY, delta);
        }
        return false;
    }


    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused && focusedChild != null) {
            setFocusedChild(null);
        }
    }
}