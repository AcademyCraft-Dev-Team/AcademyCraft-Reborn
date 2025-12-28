package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.event.EventType;
import org.academy.api.client.gui.event.InputEvent;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.event.OnClickListener;
import org.academy.api.client.util.ClientUtil;
import org.jspecify.annotations.Nullable;

/**
 * A universal button widget that acts as a container.
 * Its appearance is controlled by a background Drawable, and its content can be any Widget.
 * This class encapsulates button behavior (clicking, press state) and separates it from presentation.
 */
public class ButtonWidget extends FrameLayoutWidget {
    @Nullable
    protected OnClickListener onClickListener;
    protected boolean isPointerDown = false;

    /**
     * Creates an empty button. Content can be added later with {@link #addChild(String, Widget)}.
     */
    public ButtonWidget() {
        setClickable(true);
    }

    /**
     * Creates a button with the given widget as its content.
     * @param content The widget to be displayed inside the button.
     */
    public ButtonWidget(Widget content) {
        this();
        addChild("content", content);
    }

    public void setOnClickListener(@Nullable OnClickListener onClickListener) {
        this.onClickListener = onClickListener;
    }

    @Override
    public boolean isPressed() {
        return isPointerDown && isHovered();
    }

    @Override
    public boolean canFocus() {
        return isAbsoluteEnabled();
    }

    @Override
    public boolean onInterceptEvent(InputEvent event) {
        return event.getType() == EventType.MOUSE_PRESSED
                || event.getType() == EventType.MOUSE_RELEASED;
    }

    @Override
    protected void onMousePressed(MouseEvent event) {
        if (event.getButton() == 0 && isMouseOver(event.getX(), event.getY())) {
            isPointerDown = true;
            handlePress(event);
        }
    }

    @Override
    protected void onMouseReleased(MouseEvent event) {
        if (event.getButton() == 0) isPointerDown = false;
    }

    protected void handlePress(MouseEvent event) {
        ClientUtil.playDownSound();
        if (onClickListener != null) onClickListener.onClick(this);
        event.consume();
    }
}