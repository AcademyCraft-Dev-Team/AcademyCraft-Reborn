package org.academy.api.client.gui.widget;

import com.mojang.math.Axis;
import net.minecraft.util.ARGB;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.command.FillRectDrawCommand;
import org.academy.api.client.gui.event.EventType;
import org.academy.api.client.gui.event.InputEvent;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.MeasureSpec;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.render.RenderContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractWidgetContainer extends AbstractWidget implements WidgetContainer {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    
    protected final Map<String, Widget> children = new LinkedHashMap<>();

    protected boolean isLayoutDirty = true;

    @Nullable
    protected Widget focusedChild = null;
    @Nullable
    protected Widget hoveredWidget = null;
    @Nullable
    protected Widget gestureTarget = null;

    public AbstractWidgetContainer() {
        setClickable(true);
    }

    @Override
    public boolean isLayoutDirty() {
        return isLayoutDirty;
    }

    private void renderDebugLayoutBounds(Widget widget, RenderContext context) {
        var outlineColor = 0xFFFF0000;
        if (widget.isFocused()) {
            outlineColor = 0xFF00FF00;
        } else if (widget.isHovered()) {
            outlineColor = 0xFF0000FF;
        }

        var red = ARGB.red(outlineColor) / 255.0f;
        var green = ARGB.green(outlineColor) / 255.0f;
        var blue = ARGB.blue(outlineColor) / 255.0f;
        var alpha = 0.8f;
        var thickness = 0.5f;

        var width = widget.getWidth();
        var height = widget.getHeight();

        context.submit(new FillRectDrawCommand(width, thickness, red, green, blue, alpha));
        context.pose().pushPose();
        context.pose().translate(0, height - thickness, 0);
        context.submit(new FillRectDrawCommand(width, thickness, red, green, blue, alpha));
        context.pose().popPose();
        context.submit(new FillRectDrawCommand(thickness, height, red, green, blue, alpha));
        context.pose().pushPose();
        context.pose().translate(width - thickness, 0, 0);
        context.submit(new FillRectDrawCommand(thickness, height, red, green, blue, alpha));
        context.pose().popPose();

        if (widget.isHovered()) {
            renderDebugInfo(widget, context);
        }
    }

    private void renderDebugInfo(Widget widget, RenderContext context) {
      /*  var font = Minecraft.getInstance().font;
        var namePart = widget.getName().isEmpty() ? "" : "'" + widget.getName() + "'";
        var infoText = String.format(
                "[%s] %s\nPos: (%.1f, %.1f) Size: (%.1f, %.1f) Alpha: %.2f",
                widget.getClass().getSimpleName(),
                namePart,
                widget.getAbsoluteX(), widget.getAbsoluteY(),
                widget.getWidth(), widget.getHeight(),
                widget.getAbsoluteAlpha()
        );

        var textScale = 0.8f;
        var textColor = 0xD0FFFFFF;
        var backColor = 0x90000000;
        var padding = 2.0f;

        var textCommands = GlyphCommandGenerator.generate(font, infoText, 0, 0, textColor, true);
        var textWidth = font.width(infoText);
        var textHeight = font.wordWrapHeight(FormattedText.of(infoText), (int) (textWidth / textScale));

        context.pose().pushPose();
        {
            context.pose().translate(padding, padding, 500);

            var backRed = ARGB.red(backColor) / 255.0f;
            var backGreen = ARGB.green(backColor) / 255.0f;
            var backBlue = ARGB.blue(backColor) / 255.0f;
            var backAlpha = ARGB.alpha(backColor) / 255.0f;
            context.submit(new FillRectDrawCommand(textWidth * textScale + padding * 2, textHeight * textScale + padding * 2, backRed, backGreen, backBlue, backAlpha));

            context.pose().pushPose();
            {
                context.pose().translate(padding, padding, 0.1f);
                context.pose().scale(textScale, textScale, 1.0f);
                for (var command : textCommands) {
                    context.submit(command);
                }
            }
            context.pose().popPose();
        }
        context.pose().popPose();*/
    }

    @Nullable
    private Widget findTopWidgetAt(double mouseX, double mouseY) {
        var childrenList = new ArrayList<>(children.values());
        Collections.reverse(childrenList);

        for (var child : childrenList) {
            if (!child.isVisible() || !child.isAbsoluteEnabled()) {
                continue;
            }
            if (child.isMouseOver(mouseX, mouseY)) {
                if (child instanceof AbstractWidgetContainer acw) {
                    var nestedChild = acw.findTopWidgetAt(mouseX, mouseY);
                    return nestedChild != null ? nestedChild : acw;
                } else {
                    return child;
                }
            }
        }

        if (isMouseOver(mouseX, mouseY)) {
            return this;
        }

        return null;
    }

    public static MeasureSpec getChildMeasureSpec(MeasureSpec spec, float padding, float childDimension, SizeMode childMode) {
        var specSize = Math.max(0, spec.getSize() - padding);

        var resultSize = 0.0f;
        var resultMode = MeasureSpec.Mode.UNSPECIFIED;

        switch (spec.getMode()) {
            case EXACTLY -> {
                if (childMode == SizeMode.FIXED) {
                    resultSize = childDimension;
                    resultMode = MeasureSpec.Mode.EXACTLY;
                } else if (childMode == SizeMode.MATCH_PARENT) {
                    resultSize = specSize;
                    resultMode = MeasureSpec.Mode.EXACTLY;
                } else if (childMode == SizeMode.WRAP_CONTENT) {
                    resultSize = specSize;
                    resultMode = MeasureSpec.Mode.AT_MOST;
                }
            }
            case AT_MOST -> {
                if (childMode == SizeMode.FIXED) {
                    resultSize = childDimension;
                    resultMode = MeasureSpec.Mode.EXACTLY;
                } else if (childMode == SizeMode.MATCH_PARENT) {
                    resultSize = specSize;
                    resultMode = MeasureSpec.Mode.AT_MOST;
                } else if (childMode == SizeMode.WRAP_CONTENT) {
                    resultSize = specSize;
                    resultMode = MeasureSpec.Mode.AT_MOST;
                }
            }
            case UNSPECIFIED -> {
                if (childMode == SizeMode.FIXED) {
                    resultSize = childDimension;
                    resultMode = MeasureSpec.Mode.EXACTLY;
                } else if (childMode == SizeMode.MATCH_PARENT) {
                    resultSize = 0;
                } else if (childMode == SizeMode.WRAP_CONTENT) {
                    resultSize = 0;
                }
            }
        }
        return new MeasureSpec(resultMode, resultSize);
    }

    @Override
    public void requestLayout() {
        isLayoutDirty = true;
        super.requestLayout();
    }

    @Override
    public void render(RenderContext context) {
        if (visibility != Visibility.VISIBLE) {
            return;
        }

        var pivotX = width * originX;
        var pivotY = height * originY;

        var hasTransform = scaleX != 1.0f || scaleY != 1.0f || rotation != 0.0f;

        context.pose().pushPose();
        {
            if (hasTransform) {
                context.pose().translate(pivotX, pivotY, 0);
                if (rotation != 0.0f) {
                    context.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
                }
                if (scaleX != 1.0f || scaleY != 1.0f) {
                    context.pose().scale(scaleX, scaleY, 1.0f);
                }
                context.pose().translate(-pivotX, -pivotY, 0);
            }

            context.alpha().push(getAlpha());
            {
                if (AcademyCraft.DEBUG_UI) {
                    renderDebugLayoutBounds(this, context);
                }

                renderInternal(context);
                renderChildren(context);
            }
            context.alpha().pop();
        }
        context.pose().popPose();
    }

    protected void renderChildren(RenderContext context) {
        for (var child : children.values()) {
            if (child.isVisible()) {
                context.pose().pushPose();
                {
                    context.pose().translate(child.getX(), child.getY(), child.getZ());
                    context.pose().translate(child.getTranslationX(), child.getTranslationY(), 0);

                    child.render(context);
                }
                context.pose().popPose();
            }
        }

        if (AcademyCraft.DEBUG_UI) {
            for (var child : children.values()) {
                if (child.isVisible() && !(child instanceof WidgetContainer)) {
                    context.pose().pushPose();
                    {
                        context.pose().translate(child.getX(), child.getY(), child.getZ() + 0.1f);
                        renderDebugLayoutBounds(child, context);
                    }
                    context.pose().popPose();
                }
            }
        }
    }

    @Override
    protected void onMeasure(MeasureSpec widthMeasureSpec, MeasureSpec heightMeasureSpec) {
        var maxWidth = 0.0f;
        var maxHeight = 0.0f;

        for (var child : children.values()) {
            if (!child.isVisible()) continue;
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            var lp = child.getLayoutParams();
            maxWidth = Math.max(maxWidth, child.getMeasuredWidth() + lp.marginLeft + lp.marginRight);
            maxHeight = Math.max(maxHeight, child.getMeasuredHeight() + lp.marginTop + lp.marginBottom);
        }

        var lp = getLayoutParams();
        maxWidth += lp.paddingLeft + lp.paddingRight;
        maxHeight += lp.paddingTop + lp.paddingBottom;

        setMeasuredDimension(
                resolveSize(maxWidth, widthMeasureSpec),
                resolveSize(maxHeight, heightMeasureSpec)
        );
    }

    @Override
    public void layout(float left, float top, float right, float bottom) {
        super.layout(left, top, right, bottom);
        onLayout();
        isLayoutDirty = false;
    }

    protected void onLayout() {
        var lp = getLayoutParams();
        var parentLeft = lp.paddingLeft;
        var parentTop = lp.paddingTop;
        var parentRight = getWidth() - lp.paddingRight;
        var parentBottom = getHeight() - lp.paddingBottom;
        var availableWidth = parentRight - parentLeft;
        var availableHeight = parentBottom - parentTop;

        for (var child : children.values()) {
            if (!child.isVisible()) continue;

            var childLp = child.getLayoutParams();
            var childWidth = child.getMeasuredWidth();
            var childHeight = child.getMeasuredHeight();

            var childLeft = parentLeft + childLp.marginLeft;
            var childTop = parentTop + childLp.marginTop;

            var verticalGravity = childLp.gravity >> Gravity.AXIS_Y_SHIFT & 0x7;
            var horizontalGravity = childLp.gravity >> Gravity.AXIS_X_SHIFT & 0x7;

            if (horizontalGravity == Gravity.AXIS_SPECIFIED) {
                childLeft += (availableWidth - childWidth - childLp.marginLeft - childLp.marginRight) / 2.0f;
            } else if ((horizontalGravity & Gravity.AXIS_PULL_AFTER) != 0) {
                childLeft = parentRight - childWidth - childLp.marginRight;
            }

            if (verticalGravity == Gravity.AXIS_SPECIFIED) {
                childTop += (availableHeight - childHeight - childLp.marginTop - childLp.marginBottom) / 2.0f;
            } else if ((verticalGravity & Gravity.AXIS_PULL_AFTER) != 0) {
                childTop = parentBottom - childHeight - childLp.marginBottom;
            }

            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
        }
    }

    protected void measureChild(Widget child, MeasureSpec parentWidthSpec, MeasureSpec parentHeightSpec) {
        var lp = child.getLayoutParams();
        var childWidthSpec = getChildMeasureSpec(parentWidthSpec,
                getLayoutParams().paddingLeft + getLayoutParams().paddingRight + lp.marginLeft + lp.marginRight, lp.width, lp.widthMode);
        var childHeightSpec = getChildMeasureSpec(parentHeightSpec,
                getLayoutParams().paddingTop + getLayoutParams().paddingBottom + lp.marginTop + lp.marginBottom, lp.height, lp.heightMode);
        child.measure(childWidthSpec, childHeightSpec);
    }

    @Override
    public void dispatchEvent(InputEvent event) {
        if (!isAbsoluteEnabled() || visibility != Visibility.VISIBLE) return;

        var intercepted = onInterceptEvent(event);
        if (!intercepted) {
            if (event.getType() == EventType.MOUSE_MOVED) {
                var newHoveredWidget = findTopWidgetAt(((MouseEvent) event).getX(), ((MouseEvent) event).getY());
                if (hoveredWidget != newHoveredWidget) {
                    var current = hoveredWidget;
                    while (current != null) {
                        if (newHoveredWidget != null && isAncestor(current, newHoveredWidget)) {
                            break;
                        }
                        if (current instanceof AbstractWidget aw) {
                            aw.setHovered(false);
                        }
                        current = current.getParent();
                    }

                    hoveredWidget = newHoveredWidget;

                    current = hoveredWidget;
                    while (current != null) {
                        if (current instanceof AbstractWidget aw) {
                            aw.setHovered(true);
                        }
                        current = current.getParent();
                    }
                }
            }

            if (gestureTarget != null) {
                gestureTarget.dispatchEvent(event);
                if (event.getType() == EventType.MOUSE_RELEASED) {
                    if (AcademyCraft.DEBUG_UI) {
                        LOGGER.debug("[UI Event] gestureTarget released.");
                    }
                    gestureTarget = null;
                }
                return;
            }

            var childrenList = new ArrayList<>(children.values());
            Collections.reverse(childrenList);

            for (var child : childrenList) {
                if (!child.isVisible() || !child.isAbsoluteEnabled()) {
                    continue;
                }

                child.dispatchEvent(event);

                if (event.isConsumed()) {
                    if (AcademyCraft.DEBUG_UI) {
                        LOGGER.debug("[UI Event] Event consumed by child '{}'. Stopping propagation in '{}'.", child.getName(), getName());
                    }
                    if (event.getType() == EventType.MOUSE_PRESSED) {
                        gestureTarget = child;
                        setFocusedChild(child.canFocus() ? child : this);
                    }
                    return;
                }
            }
        } else {
            if (hoveredWidget != null) {
                var current = hoveredWidget;
                while (current != null && current != this) {
                    if (current instanceof AbstractWidget aw) {
                        aw.setHovered(false);
                    }
                    current = current.getParent();
                }
                hoveredWidget = null;
            }
        }

        super.dispatchEvent(event);
        if (event.isConsumed() && event.getType() == EventType.MOUSE_PRESSED) {
            setFocusedChild(this);
        }
    }

    private boolean isAncestor(@Nullable Widget ancestor, @Nullable Widget descendant) {
        if (ancestor == null || descendant == null) return false;
        var current = descendant;
        while (current != null) {
            if (current == ancestor) return true;
            current = current.getParent();
        }
        return false;
    }

    @Override
    public void addChild(String name, Widget child) {
        if (child.getParent() != null) {
            child.getParent().removeChild(name);
        }

        var lp = child.getLayoutParams();
        if (lp == LayoutParams.NONE) {
            lp = generateDefaultLayoutParams();
        }
        if (!checkLayoutParams(lp)) {
            lp = generateLayoutParams(lp);
        }
        child.setLayoutParams(lp);

        child.setParent(this);
        child.setName(name);
        children.put(name, child);

        if (isAttached()) {
            child.dispatchAttached();
        }

        requestLayout();
    }

    @Override
    public void removeChild(String name) {
        if (children.containsKey(name)) {
            var widget = children.get(name);

            if (widget.isAttached()) {
                widget.dispatchDetached();
            }

            widget.setParent(null);
            if (focusedChild == widget) {
                focusedChild = null;
            }
            if (hoveredWidget == widget) {
                hoveredWidget = null;
            }
            if (gestureTarget == widget) {
                gestureTarget = null;
            }
            children.remove(name);
            requestLayout();
        }
    }

    @Override
    public void clearChildren() {
        var childList = new ArrayList<>(children.values());
        for (var child : childList) {
            removeChild(child.getName());
        }
        requestLayout();
    }

    @Override
    @Nullable
    public Widget getHoveredWidget() {
        return hoveredWidget;
    }

    @Override
    public void tick() {
        for (var tickable : getChildren().values()) {
            tickable.tick();
        }
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    @Override
    public void setFocusedChild(@Nullable Widget child) {
        if (child != null && child.getParent() != this) {
            if (child.getParent() instanceof WidgetContainer parentContainer) {
                parentContainer.setFocusedChild(child);
            }
            return;
        }

        if (focusedChild == child) {
            return;
        }

        if (focusedChild != null) {
            focusedChild.setFocused(false);
        }

        focusedChild = child;

        if (focusedChild != null) {
            focusedChild.setFocused(true);
            if (getParent() instanceof WidgetContainer parentContainer) {
                parentContainer.setFocusedChild(this);
            }
        }
    }

    @Override
    public Map<String, Widget> getChildren() {
        return Collections.unmodifiableMap(children);
    }

    @Override
    public Widget setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused && focusedChild != null) {
            focusedChild.setFocused(false);
            focusedChild = null;
        }
        return this;
    }

    @Override
    public void dispatchAttached() {
        super.dispatchAttached();
        for (var child : children.values()) {
            child.dispatchAttached();
        }
    }

    @Override
    public void dispatchDetached() {
        for (var child : children.values()) {
            child.dispatchDetached();
        }
        super.dispatchDetached();
    }
}