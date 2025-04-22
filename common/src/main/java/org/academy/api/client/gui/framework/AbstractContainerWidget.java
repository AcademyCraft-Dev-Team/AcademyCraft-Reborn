package org.academy.api.client.gui.framework;

import org.jetbrains.annotations.NotNull;

import java.util.*;

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
        child.setParent(this);
        this.children.put(name, child);
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

    @SuppressWarnings("unchecked")
    @NotNull
    public <T extends Widget> T getChildUnSafe(String name) {
        if (!children.containsKey(name)) {
            throw new NoSuchElementException("No such child: " + name);
        }
        return (T) children.get(name);
    }

    @NotNull
    @Override
    public Iterator<Widget> iterator() {
        return children.values().iterator();
    }

    protected void setFocusedChild(Widget child) {
        if (child == this) return;

        focusedChild = child;
        if (focusedChild != null) {
            focusedChild.setFocused(true);
        }
    }

    public Widget getWidgetAt(double mouseX, double mouseY) {
        List<Widget> widgetList = getAllWidgets();

        widgetList.sort(Comparator.comparing(Widget::getAbsoluteZ).reversed());

        for (Widget widget : widgetList) {
        //    AcademyCraft.LOGGER.info(widget + " Z : " + widget.getAbsoluteZ() + " Enable : " + widget.isAbsoluteEnabled() + " Overed : " + widget.isMouseOver(mouseX, mouseY));
        }

        for (Widget widget : widgetList) {
            if (widget.isAbsoluteEnabled() && widget.isMouseOver(mouseX, mouseY)) {
         //       AcademyCraft.LOGGER.info("Widget " + widget + " is focused");
                return widget;
            }
        }
        return null;
    }

    public List<Widget> getAllWidgets() {
        List<Widget> result = new ArrayList<>();
        Stack<Widget> stack = new Stack<>();
        stack.addAll(getChildren().values());

        while (!stack.isEmpty()) {
            Widget widget = stack.pop();
            if (widget instanceof AbstractContainerWidget container) {
                stack.addAll(container.getChildren().values());
            } else {
                result.add(widget);
            }
        }

        return result;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!isVisible() || !isEnabled()) return;

        for (Widget child : getAllWidgets()) {
            child.setHovered(false);
        }

        setHovered(isMouseOver(mouseX, mouseY));

        Widget widget = getWidgetAt(mouseX, mouseY);
        if (widget != null) {
            widget.setHovered(true);
        }

        for (Widget child : getChildren().values()) {
            child.mouseMoved(mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isEnabled()) return false;
        List<Widget> widgetList = getAllWidgets();

        for (Widget child : widgetList) {
            child.setHovered(false);
            child.setFocused(false);
        }

        if (button == 0) {
            Widget target = getWidgetAt(mouseX, mouseY);
            if (target != null) {
                target.setHovered(true);
                if (target.canFocus()) {
                    setFocusedChild(target);
                }
            } else {
                setFocusedChild(null);
            }
        }

        for (Widget child : widgetList) {
            child.mouseClicked(mouseX, mouseY, button);
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

        for (Widget widget : children.values()) {
            widget.charTyped(codePoint, modifiers);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (Widget widget : children.values()) {
            widget.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (Widget widget : children.values()) {
            widget.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        for (Widget widget : children.values()) {
            widget.mouseScrolled(mouseX, mouseY, delta);
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