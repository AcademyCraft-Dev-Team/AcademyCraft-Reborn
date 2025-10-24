package org.academy.api.client.gui.framework.render;

import org.joml.Vector2f;

import static org.academy.api.common.util.MathUtil.Axis2D;
import static org.academy.api.common.util.MathUtil.Direction2D;

public final class Position2D extends Vector2f {
    public Position2D(float x, float y) {
        super(x, y);
    }

    public Position2D() {
    }

    public static Position2D of(Axis2D axis, float primaryPosition, float secondaryPosition) {
        return switch (axis) {
            case HORIZONTAL -> new Position2D(primaryPosition, secondaryPosition);
            case VERTICAL -> new Position2D(secondaryPosition, primaryPosition);
        };
    }

    public Position2D step(Direction2D direction) {
        return switch (direction) {
            case DOWN -> new Position2D(x, y + 1);
            case UP -> new Position2D(x, y - 1);
            case LEFT -> new Position2D(x - 1, y);
            case RIGHT -> new Position2D(x + 1, y);
        };
    }

    public float getCoordinate(Axis2D axis) {
        return switch (axis) {
            case HORIZONTAL -> x;
            case VERTICAL -> y;
        };
    }
}