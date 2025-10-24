package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.util.ClientUtil;

public abstract class AbstractButtonWidget extends AbstractWidget {
    protected Runnable onPress;
    protected MouseButtonState state = MouseButtonState.PRESSED;

    public AbstractButtonWidget(Runnable onPress) {
        this.onPress = onPress;
        setClickable(true);
    }

    @Override
    protected void onMousePressed(MouseEvent event) {
        if (state == MouseButtonState.PRESSED
                && event.getButton() == 0
                && isMouseOver(event.getX(), event.getY())) {
            handlePress(event);
        }
    }

    @Override
    protected void onMouseReleased(MouseEvent event) {
        if (state == MouseButtonState.RELEASED
                && event.getButton() == 0
                && isMouseOver(event.getX(), event.getY())) {
            handlePress(event);
        }
    }

    protected void handlePress(MouseEvent event) {
        ClientUtil.playDownSound();
        onPress.run();
        event.consume();
    }

    @Override
    public boolean canFocus() {
        return isAbsoluteEnabled();
    }

    public void setOnPress(Runnable onPress) {
        this.onPress = onPress;
    }

    public enum MouseButtonState {
        PRESSED,
        RELEASED
    }
}