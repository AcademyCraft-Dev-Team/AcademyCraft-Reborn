package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.gui.framework.MouseButtonState;
import org.academy.api.client.util.ClientUtil;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractButtonWidget extends AbstractWidget {
    public Runnable onPress;
    public MouseButtonState state = MouseButtonState.PRESSED;

    public AbstractButtonWidget(float x, float y, float width, float height, Runnable onPress) {
        super(x, y, width, height);
        this.onPress = onPress;
        this.clickable = true;
    }

    @Override
    protected void onMousePressed(@NotNull MouseEvent event) {
        if (this.state == MouseButtonState.PRESSED
                && event.getButton() == 0
                && this.isMouseOver(event.getX(), event.getY())) {
            this.handlePress(event);
        }
    }

    @Override
    protected void onMouseReleased(@NotNull MouseEvent event) {
        if (this.state == MouseButtonState.RELEASED
                && event.getButton() == 0
                && this.isMouseOver(event.getX(), event.getY())) {
            this.handlePress(event);
        }
    }

    protected void handlePress(@NotNull MouseEvent event) {
        ClientUtil.playDownSound();
        if (this.onPress != null) {
            this.onPress.run();
        }
        event.consume();
    }

    @Override
    public boolean canFocus() {
        return this.isAbsoluteEnabled();
    }
}