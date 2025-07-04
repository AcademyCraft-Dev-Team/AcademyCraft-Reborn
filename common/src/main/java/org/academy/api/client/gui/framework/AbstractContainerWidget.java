package org.academy.api.client.gui.framework;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.academy.AcademyCraft;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class AbstractContainerWidget extends AbstractWidget implements WidgetContainer {
    protected final Map<String, Widget> children = new LinkedHashMap<>();
    protected Widget focusedChild = null;
    private Widget hoveredWidget = null;

    public AbstractContainerWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    public void setFocusedChild(Widget child) {
        var containerSetFocusedChildEvent = new ContainerSetFocusedChildEvent(child);
        AcademyCraft.EVENT_BUS.post(containerSetFocusedChildEvent);
        if (containerSetFocusedChildEvent.isCanceled()) return;
        if (child == this) return;
        child = containerSetFocusedChildEvent.child;

        if (focusedChild == child) {
            return;
        }

        if (focusedChild != null) {
            focusedChild.setFocused(false);
        }

        focusedChild = child;

        if (focusedChild != null) {
            focusedChild.setFocused(true);
            if (getParent() instanceof AbstractContainerWidget parentContainer) {
                parentContainer.setFocusedChild(this);
            }
        }
    }

    @Override
    public void addChild(String name, Widget child) {
        if (child.getParent() != null) {
            child.getParent().removeChild(name);
        }
        child.setParent(this);
        children.put(name, child);
    }

    @Override
    public void removeChild(String name) {
        if (children.containsKey(name)) {
            var widget = children.get(name);
            widget.setParent(null);
            if (focusedChild == widget) {
                focusedChild = null;
            }
            if (hoveredWidget == widget) {
                hoveredWidget = null;
            }
            children.remove(name);
        }
    }

    @Override
    public void clearChildren() {
        children.clear();
        focusedChild = null;
        hoveredWidget = null;
    }

    @Override
    public Map<String, Widget> getChildren() {
        return Collections.unmodifiableMap(children);
    }

    public Widget getWidgetAt(double mouseX, double mouseY) {
        return findTopWidgetAt(mouseX, mouseY, null);
    }

    private Widget findTopWidgetAt(double mouseX, double mouseY, Widget bestCandidate) {
        for (var child : children.values()) {
            if (!child.isVisible() || !child.isEnabled()) {
                continue;
            }

            if (child.isAbsoluteMouseOver(mouseX, mouseY)) {
                if (child instanceof AbstractContainerWidget container) {
                    bestCandidate = container.findTopWidgetAt(mouseX, mouseY, bestCandidate);
                } else {
                    if (bestCandidate == null || child.getAbsoluteZ() > bestCandidate.getAbsoluteZ()) {
                        bestCandidate = child;
                    }
                }
            }
        }
        return bestCandidate;
    }

    public List<Widget> getAllWidgets() {
        var result = new ArrayList<Widget>();
        var stack = new Stack<Widget>();
        stack.addAll(getChildren().values());

        while (!stack.isEmpty()) {
            var widget = stack.pop();
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

        setHovered(isAbsoluteMouseOver(mouseX, mouseY));
        var newHoveredWidget = isHovered() ? getWidgetAt(mouseX, mouseY) : null;

        if (hoveredWidget != newHoveredWidget) {
            if (hoveredWidget != null) {
                hoveredWidget.setHovered(false);
            }
            hoveredWidget = newHoveredWidget;
            if (hoveredWidget != null) {
                hoveredWidget.setHovered(true);
            }
        }

        for (var child : getChildren().values()) {
            child.mouseMoved(mouseX, mouseY);
        }
    }

    @Override
    public boolean mousePressed(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isEnabled()) {
            return false;
        }

        var childrenList = new ArrayList<>(children.values());
        Collections.reverse(childrenList);

        for (var child : childrenList) {
            if (child.mousePressed(mouseX, mouseY, button)) {
                if (button == 0) {
                    setFocusedChild(child.canFocus() ? child : null);
                }
                return true;
            }
        }

        if (isAbsoluteMouseOver(mouseX, mouseY)) {
            if (button == 0) {
                setFocusedChild(null);
            }
            return true;
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
        if (!isVisible() || !isEnabled()) {
            return false;
        }
        if (focusedChild != null && focusedChild.isEnabled()) {
            return focusedChild.charTyped(codePoint, modifiers);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isEnabled()) {
            return false;
        }
        var childrenList = new ArrayList<>(children.values());
        Collections.reverse(childrenList);
        for (var child : childrenList) {
            if (child.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!isVisible() || !isEnabled()) {
            return false;
        }
        var childrenList = new ArrayList<>(children.values());
        Collections.reverse(childrenList);
        for (var child : childrenList) {
            if (child.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isVisible() || !isEnabled()) {
            return false;
        }
        if (isAbsoluteMouseOver(mouseX, mouseY)) {
            var childrenList = new ArrayList<>(children.values());
            Collections.reverse(childrenList);

            for (var child : childrenList) {
                if (child.mouseScrolled(mouseX, mouseY, delta)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    @Override
    public Widget setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused && focusedChild != null) {
            setFocusedChild(null);
        }
        return this;
    }

    public static class ContainerSetFocusedChildEvent extends Event implements ICancellableEvent {
        @Nullable
        public Widget child;

        public ContainerSetFocusedChildEvent(@Nullable Widget widget) {
            child = widget;
        }
    }
}