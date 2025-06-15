package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.framework.AbstractWidget;
import org.academy.api.client.gui.framework.MouseButtonState;
import org.academy.api.client.util.ClientUtil;

public abstract class AbstractButtonWidget extends AbstractWidget {
    public Runnable onPress;
    public MouseButtonState state = MouseButtonState.PRESSED;

    public AbstractButtonWidget(float x, float y, float width, float height, Runnable onPress) {
        super(x, y, width, height);
        this.onPress = onPress;
    }

    @Override
    public boolean mousePressed(double mouseX, double mouseY, int button) {
        if (isAbsoluteMouseOver(mouseX, mouseY) && button == 0 && isAbsoluteEnabled() && state == MouseButtonState.PRESSED) {
            return handlePress();
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isAbsoluteMouseOver(mouseX, mouseY) && button == 0 && isAbsoluteEnabled() && state == MouseButtonState.RELEASED) {
            return handlePress();
        }
        return false;
    }

    protected boolean handlePress() {
        ClientUtil.playDownSound();
        if (onPress != null) {
            onPress.run();
        }
        return true;
    }

    @Override
    public boolean canFocus() {
        return this.enabled;
    }
}