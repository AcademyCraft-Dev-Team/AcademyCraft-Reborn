package org.academy.api.client.gui.framework.layout;

public final class Gravity {
    public static final int NO_GRAVITY = 0;

    public static final int AXIS_SPECIFIED = 0x0001;
    public static final int AXIS_PULL_BEFORE = 0x0002;
    public static final int AXIS_PULL_AFTER = 0x0004;
    public static final int AXIS_X_SHIFT = 0;
    public static final int AXIS_Y_SHIFT = 4;

    public static final int TOP = (AXIS_PULL_BEFORE | AXIS_SPECIFIED) << AXIS_Y_SHIFT;
    public static final int BOTTOM = (AXIS_PULL_AFTER | AXIS_SPECIFIED) << AXIS_Y_SHIFT;
    public static final int LEFT = (AXIS_PULL_BEFORE | AXIS_SPECIFIED) << AXIS_X_SHIFT;
    public static final int RIGHT = (AXIS_PULL_AFTER | AXIS_SPECIFIED) << AXIS_X_SHIFT;

    public static final int CENTER_VERTICAL = AXIS_SPECIFIED << AXIS_Y_SHIFT;
    public static final int CENTER_HORIZONTAL = AXIS_SPECIFIED << AXIS_X_SHIFT;
    public static final int CENTER = CENTER_VERTICAL | CENTER_HORIZONTAL;

    public static final int TOP_LEFT = TOP | LEFT;

    public static final int HORIZONTAL_GRAVITY_MASK = (AXIS_SPECIFIED | AXIS_PULL_BEFORE | AXIS_PULL_AFTER) << AXIS_X_SHIFT;
    public static final int VERTICAL_GRAVITY_MASK = (AXIS_SPECIFIED | AXIS_PULL_BEFORE | AXIS_PULL_AFTER) << AXIS_Y_SHIFT;

    public static final int FILL_HORIZONTAL = HORIZONTAL_GRAVITY_MASK;
    public static final int FILL_VERTICAL = VERTICAL_GRAVITY_MASK;
    public static final int FILL = FILL_HORIZONTAL | FILL_VERTICAL;

    public static final int RELATIVE_LAYOUT_DIRECTION = 0x00800000;

    public static final int START = RELATIVE_LAYOUT_DIRECTION | LEFT;
    public static final int END = RELATIVE_LAYOUT_DIRECTION | RIGHT;

    public static final int RELATIVE_HORIZONTAL_GRAVITY_MASK = START | END;

    public static final int TOP_RIGHT = TOP | RIGHT;
    public static final int BOTTOM_LEFT = BOTTOM | LEFT;
    public static final int BOTTOM_RIGHT = BOTTOM | RIGHT;

    public static final int CENTER_TOP = CENTER_HORIZONTAL | TOP;
    public static final int CENTER_BOTTOM = CENTER_HORIZONTAL | BOTTOM;
    public static final int CENTER_LEFT = CENTER_VERTICAL | LEFT;
    public static final int CENTER_RIGHT = CENTER_VERTICAL | RIGHT;

    private Gravity() {
    }
}