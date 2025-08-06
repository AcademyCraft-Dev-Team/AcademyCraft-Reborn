package org.academy.api.client.gui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.Tickable;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.event.EventType;
import org.academy.api.client.gui.event.InputEvent;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.util.RenderUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public abstract class AbstractContainerWidget extends AbstractWidget implements WidgetContainer, Tickable {
    protected final Map<String, Widget> children = new LinkedHashMap<>();
    protected final List<Tickable> tickableChildren = new ArrayList<>();

    protected Widget focusedChild = null;
    protected Widget hoveredWidget = null;
    protected Widget gestureTarget = null;

    public AbstractContainerWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
        this.clickable = true;
    }

    @Override
    public void dispatchEvent(@NotNull InputEvent event) {
        if (!isAbsoluteEnabled() || !isVisible()) {
            return;
        }

        if (AcademyCraft.DEBUG_UI && event instanceof MouseEvent) {
            AcademyCraft.LOGGER.debug("[UI Event] Dispatching {} to Container '{}'", event.getType(), this.getName());
        }

        if (event.getType() == EventType.MOUSE_MOVED) {
            Widget newHoveredWidget = findTopWidgetAt(((MouseEvent) event).getX(), ((MouseEvent) event).getY());
            if (hoveredWidget != newHoveredWidget) {
                if (AcademyCraft.DEBUG_UI) {
                    String oldName = hoveredWidget != null ? hoveredWidget.getName() : "null";
                    String newName = newHoveredWidget != null ? newHoveredWidget.getName() : "null";
                    AcademyCraft.LOGGER.debug("[UI Hover] Hover changed from '{}' to '{}'", oldName, newName);
                }
                if (hoveredWidget instanceof AbstractWidget oldHovered) {
                    oldHovered.setHovered(false);
                }
                hoveredWidget = newHoveredWidget;
                if (hoveredWidget instanceof AbstractWidget newHovered) {
                    newHovered.setHovered(true);
                }
            }
        }

        if (gestureTarget != null) {
            if (AcademyCraft.DEBUG_UI) {
                AcademyCraft.LOGGER.debug("[UI Event] Event routed to gestureTarget '{}'", gestureTarget.getName());
            }
            gestureTarget.dispatchEvent(event);
            if (event.getType() == EventType.MOUSE_RELEASED) {
                if (AcademyCraft.DEBUG_UI) {
                    AcademyCraft.LOGGER.debug("[UI Event] gestureTarget released.");
                }
                gestureTarget = null;
            }
            return;
        }

        var childrenList = new ArrayList<>(this.children.values());
        Collections.reverse(childrenList);

        for (var child : childrenList) {
            if (!child.isVisible() || !child.isAbsoluteEnabled()) {
                continue;
            }

            child.dispatchEvent(event);

            if (event.isConsumed()) {
                if (AcademyCraft.DEBUG_UI) {
                    AcademyCraft.LOGGER.debug("[UI Event] Event consumed by child '{}'. Stopping propagation in '{}'.", child.getName(), this.getName());
                }
                if (event.getType() == EventType.MOUSE_PRESSED) {
                    this.gestureTarget = child;
                    setFocusedChild(child.canFocus() ? child : this);
                }
                /*
                 * This is the fix. A parent container should not attempt to re-handle an event
                 * that has already been fully consumed by one of its children. Doing so
                 * creates a confusing and incorrect event flow.
                 */
                // super.dispatchEvent(event); // BUG: This line was removed.
                return;
            }
        }

        if (AcademyCraft.DEBUG_UI && event.getType() != EventType.MOUSE_MOVED) {
            AcademyCraft.LOGGER.debug("[UI Event] No child consumed event. '{}' is handling it.", this.getName());
        }
        super.dispatchEvent(event);
        if (event.isConsumed() && event.getType() == EventType.MOUSE_PRESSED) {
            setFocusedChild(this);
        }
    }

    private static void drawInfo(Widget widget, MatrixStack stack, MultiBufferSource.BufferSource bufferSource) {
        if (!(widget instanceof AbstractWidget aw)) {
            return;
        }

        stack.pushPose();
        stack.translate(5, 5, 500);

        var font = Minecraft.getInstance().font;
        var namePart = aw.getName().isEmpty() ? "" : "'" + aw.getName() + "'";
        var infoText = String.format(
                "[%s] %s\nPos: (%.1f, %.1f) Size: (%.1f, %.1f) Alpha: %.2f",
                aw.getClass().getSimpleName(),
                namePart,
                aw.getX(), aw.getY(),
                aw.getWidth(), aw.getHeight(),
                aw.getAbsoluteAlpha()
        );

        var textScale = 0.8f;
        var textColor = 0xA0FFFFFF;

        stack.pushPose();
        stack.scale(textScale, textScale, 1.0f);
        RenderUtil.drawString(stack, bufferSource, font, infoText, textColor, true);
        stack.popPose();

        stack.popPose();
    }

    private void renderChildDebugInfo(Widget child, MatrixStack stack, MultiBufferSource.BufferSource bufferSource) {
        var outlineColor = 0xFFFF0000;
        if (child.isFocused()) {
            outlineColor = 0xFF00FF00;
        } else if (child.isHovered()) {
            outlineColor = 0xFF0000FF;
        }
        RenderUtil.drawOutline(stack, bufferSource, 0, 0, child.getWidth(), child.getHeight(), outlineColor, 1);
    }

    protected void renderChildren(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        for (var child : this.children.values()) {
            if (child.isVisible()) {
                stack.pushPose();
                stack.translate(child.getX() - this.getScrollX(), child.getY() - this.getScrollY(), child.getZ());

                child.render(stack, bufferSource, mouseX, mouseY, partialTick);

                if (AcademyCraft.DEBUG_UI) {
                    renderChildDebugInfo(child, stack, bufferSource);
                }

                stack.popPose();
            }
        }
    }

    @Override
    public void render(@NotNull MatrixStack stack, MultiBufferSource.@NotNull BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!this.isVisible()) return;

        if (AcademyCraft.DEBUG_UI) {
            var color = isFocused() ? 0xFF00FF00 : 0xFFFF0000;
            RenderUtil.drawOutline(stack, bufferSource, 0, 0, getWidth(), getHeight(), color, 1);

            if (isHovered()) {
                drawInfo(this, stack, bufferSource);
            }
        }

        this.renderChildren(stack, bufferSource, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        for (var tickable : this.tickableChildren) {
            tickable.tick();
        }
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    public void setFocusedChild(@Nullable Widget child) {
        if (child != null && child.getParent() != this) {
            if (child.getParent() instanceof AbstractContainerWidget parentContainer) {
                parentContainer.setFocusedChild(child);
            }
            return;
        }

        var containerSetFocusedChildEvent = new ContainerSetFocusedChildEvent(child);
        NeoForge.EVENT_BUS.post(containerSetFocusedChildEvent);
        if (containerSetFocusedChildEvent.isCanceled()) return;
        child = containerSetFocusedChildEvent.child;

        if (this.focusedChild == child) {
            return;
        }

        if (this.focusedChild != null) {
            this.focusedChild.setFocused(false);
        }

        this.focusedChild = child;

        if (this.focusedChild != null) {
            this.focusedChild.setFocused(true);
            if (this.getParent() instanceof AbstractContainerWidget parentContainer) {
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
        child.setName(name);
        this.children.put(name, child);
        if (child instanceof Tickable tickable) {
            this.tickableChildren.add(tickable);
        }
    }

    @Override
    public void removeChild(String name) {
        if (this.children.containsKey(name)) {
            var widget = this.children.get(name);
            widget.setParent(null);
            if (this.focusedChild == widget) {
                this.focusedChild = null;
            }
            if (this.hoveredWidget == widget) {
                this.hoveredWidget = null;
            }
            if (this.gestureTarget == widget) {
                this.gestureTarget = null;
            }
            if (widget instanceof Tickable tickable) {
                this.tickableChildren.remove(tickable);
            }
            this.children.remove(name);
        }
    }

    @Override
    public void clearChildren() {
        var childList = new ArrayList<>(this.children.values());
        for (var child : childList) {
            this.removeChild(child.getName());
        }
    }

    @Override
    public @NotNull Map<String, Widget> getChildren() {
        return Collections.unmodifiableMap(this.children);
    }

    private Widget findTopWidgetAt(double mouseX, double mouseY) {
        var childrenList = new ArrayList<>(children.values());
        Collections.reverse(childrenList);

        if (AcademyCraft.DEBUG_UI) {
            String childNames = childrenList.stream().map(Widget::getName).collect(Collectors.joining(", "));
            AcademyCraft.LOGGER.debug("[UI Find] Searching in '{}' for widget at ({}, {}). Children (top to bottom): [{}]", this.getName(), mouseX, mouseY, childNames);
        }

        for (var child : childrenList) {
            if (!child.isVisible() || !child.isAbsoluteEnabled()) {
                continue;
            }
            if (child.isMouseOver(mouseX, mouseY)) {
                if (AcademyCraft.DEBUG_UI) {
                    AcademyCraft.LOGGER.debug("[UI Find] Mouse is over child '{}'.", child.getName());
                }
                if (child instanceof AbstractContainerWidget acw) {
                    Widget nestedChild = acw.findTopWidgetAt(mouseX, mouseY);
                    return nestedChild != null ? nestedChild : acw;
                } else {
                    return child;
                }
            }
        }

        if (this.isMouseOver(mouseX, mouseY)) {
            if (AcademyCraft.DEBUG_UI) {
                AcademyCraft.LOGGER.debug("[UI Find] Mouse is over container '{}' itself.", this.getName());
            }
            return this;
        }

        return null;
    }

    @Override
    public @NotNull Widget setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused && this.focusedChild != null) {
            this.focusedChild.setFocused(false);
            this.focusedChild = null;
        }
        return this;
    }

    public static class ContainerSetFocusedChildEvent extends Event implements ICancellableEvent {
        @Nullable
        public Widget child;

        public ContainerSetFocusedChildEvent(@Nullable Widget widget) {
            this.child = widget;
        }
    }
}