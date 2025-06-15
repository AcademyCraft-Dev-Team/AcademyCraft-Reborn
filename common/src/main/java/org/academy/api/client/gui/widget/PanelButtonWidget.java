package org.academy.api.client.gui.widget;

import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.framework.MouseButtonState;
import org.academy.api.client.gui.framework.Widget;
import org.academy.api.client.util.ClientUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PanelButtonWidget extends AbstractContainerWidget {
    public Runnable onActive;
    public MouseButtonState state = MouseButtonState.PRESSED;

    public PanelButtonWidget(float x, float y, float width, float height, Runnable onActive) {
        super(x, y, width, height);
        this.onActive = onActive;
    }

    @Override
    public boolean mousePressed(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isEnabled()) {
            return false;
        }

        List<Widget> childrenList = new ArrayList<>(children.values());
        Collections.reverse(childrenList);
        for (Widget child : childrenList) {
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
                if (isAbsoluteEnabled() && state == MouseButtonState.PRESSED) {
                    return handlePress();
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        List<Widget> childrenList = new ArrayList<>(children.values());
        Collections.reverse(childrenList);
        for (Widget child : childrenList) {
            if (child.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (isAbsoluteEnabled() && isAbsoluteMouseOver(mouseX, mouseY) && button == 0 && state == MouseButtonState.RELEASED) {
            return handlePress();
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    protected boolean handlePress() {
        ClientUtil.playDownSound();
        if (onActive != null) {
            onActive.run();
        }
        return true;
    }

    @Override
    public boolean canFocus() {
        return this.enabled;
    }
}