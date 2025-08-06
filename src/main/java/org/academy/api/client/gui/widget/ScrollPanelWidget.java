package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.gui.event.EventType;
import org.academy.api.client.gui.event.InputEvent;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.event.ScrollEvent;
import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.client.util.StencilUtil;
import org.academy.api.common.util.MathUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;

public class ScrollPanelWidget extends AbstractContainerWidget {
    protected float scrollTargetY;
    protected float scrollSpeed = 24f;

    public ScrollPanelWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
        this.scrollTargetY = 0f;
    }

    @Override
    public void dispatchEvent(@NotNull InputEvent event) {
        if (event instanceof MouseEvent me && !this.isMouseOver(me.getX(), me.getY())) {
            if (this.hoveredWidget != null) {
                if (this.hoveredWidget instanceof AbstractWidget oldHovered) {
                    oldHovered.setHovered(false);
                }
                this.hoveredWidget = null;
            }
            if (this.gestureTarget != null) {
                this.gestureTarget.dispatchEvent(event);
                if (event.getType() == EventType.MOUSE_RELEASED) {
                    this.gestureTarget = null;
                }
            }
            return;
        }
        super.dispatchEvent(event);
    }

    public float getMaxScroll() {
        var contentHeight = 0f;
        for (var child : this.getChildren().values()) {
            contentHeight = Math.max(contentHeight, child.getY() + child.getHeight());
        }
        return Math.max(0, contentHeight - this.getHeight());
    }

    public void scrollToBottom() {
        this.setScrollTargetY(this.getMaxScroll());
    }

    @Override
    public void render(@NotNull MatrixStack stack, MultiBufferSource.@NotNull BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        var newScrollY = MathUtil.lerpStartEndFactor(getScrollY(), scrollTargetY, ClientUtil.animationFactor(MathUtil.PI / 1.5f));
        scrollTo(getScrollX(), newScrollY);

        bufferSource.endBatch();
        StencilUtil.beginDrawMask();
        RenderUtil.fill(stack, bufferSource, 0, 0, this.getWidth(), this.getHeight(), 0xFFFFFFFF);
        bufferSource.endBatch();
        StencilUtil.useMask();

        super.renderChildren(stack, bufferSource, mouseX, mouseY, partialTick);

        bufferSource.endBatch();
        StencilUtil.end();
    }

    @Override
    protected void onMouseScrolled(@NotNull ScrollEvent event) {
        if (isMouseOver(event.getX(), event.getY())) {
            var childrenList = new ArrayList<>(this.children.values());
            Collections.reverse(childrenList);
            for (var child : childrenList) {
                child.dispatchEvent(event);
                if (event.isConsumed()) {
                    return;
                }
            }

            this.scrollTargetY -= (float) (event.getDelta() * this.scrollSpeed);
            this.scrollTargetY = MathUtil.clamp(this.scrollTargetY, 0, this.getMaxScroll());
            event.consume();
        }
    }

    @NotNull
    public ScrollPanelWidget setScrollTargetY(float scrollTargetY) {
        this.scrollTargetY = MathUtil.clamp(scrollTargetY, 0, this.getMaxScroll());
        return this;
    }

    @NotNull
    public ScrollPanelWidget setScrollSpeed(float scrollSpeed) {
        this.scrollSpeed = scrollSpeed;
        return this;
    }
}