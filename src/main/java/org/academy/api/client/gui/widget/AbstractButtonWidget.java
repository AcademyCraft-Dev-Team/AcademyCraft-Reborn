package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.event.OnClickListener;
import org.academy.api.client.util.ClientUtil;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractButtonWidget extends AbstractWidget {
    @Nullable
    protected OnClickListener onClickListener;
    protected MouseButtonState state = MouseButtonState.PRESSED;
    protected boolean isPointerDown = false;

    public AbstractButtonWidget(@Nullable OnClickListener onClickListener) {
        this.onClickListener = onClickListener;
        setClickable(true);
    }

    @Override
    protected void onMousePressed(MouseEvent event) {
        if (state == MouseButtonState.PRESSED
                && event.getButton() == 0
                && isMouseOver(event.getX(), event.getY())) {
            isPointerDown = true;
            handlePress(event);
        }
    }

    @Override
    protected void onMouseReleased(MouseEvent event) {
        if (event.getButton() == 0) {
            isPointerDown = false;
        }

        if (state == MouseButtonState.RELEASED
                && event.getButton() == 0
                && isMouseOver(event.getX(), event.getY())) {
            handlePress(event);
        }
    }

    protected void handlePress(MouseEvent event) {
        ClientUtil.playDownSound();
        if (onClickListener != null) {
            onClickListener.onClick(this);
        }
        event.consume();
    }

    @Override
    public boolean isPressed() {
        return isPointerDown && isHovered();
    }

    @Override
    public boolean canFocus() {
        return isAbsoluteEnabled();
    }

    public void setOnClickListener(@Nullable OnClickListener onClickListener) {
        this.onClickListener = onClickListener;
    }

    /**
     * @deprecated Use {@link #setOnClickListener(OnClickListener)} instead.
     */
    @Deprecated
    public void setOnPress(Runnable onPress) {
        this.onClickListener = widget -> onPress.run();
    }

    public enum MouseButtonState {
        PRESSED,
        RELEASED
    }
}