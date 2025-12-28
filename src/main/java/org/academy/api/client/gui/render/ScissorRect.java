package org.academy.api.client.gui.render;

import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

import static org.academy.api.common.util.MathUtil.Axis2D;
import static org.academy.api.common.util.MathUtil.Direction2D;

public final class ScissorRect implements Comparable<ScissorRect> {
    private static final ScissorRect EMPTY = new ScissorRect(0, 0, 0, 0);

    private final Position2D position;
    private final float width;
    private final float height;

    public ScissorRect(Position2D position, float width, float height) {
        this.position = position;
        this.width = width;
        this.height = height;
    }

    public ScissorRect(float x, float y, float width, float height) {
        this(new Position2D(x, y), width, height);
    }

    public static ScissorRect empty() {
        return EMPTY;
    }

    public static ScissorRect of(Axis2D pAxis, float pPrimaryPosition, float pSecondaryPosition, float pPrimaryLength, float pSecondaryLength) {
        return switch (pAxis) {
            case HORIZONTAL -> new ScissorRect(pPrimaryPosition, pSecondaryPosition, pPrimaryLength, pSecondaryLength);
            case VERTICAL -> new ScissorRect(pSecondaryPosition, pPrimaryPosition, pSecondaryLength, pPrimaryLength);
        };
    }

    public Position2D getPosition() {
        return position;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public ScissorRect step(Direction2D pDirection) {
        return new ScissorRect(position.step(pDirection), width, height);
    }

    public float getLength(Axis2D axis) {
        return switch (axis) {
            case HORIZONTAL -> width;
            case VERTICAL -> height;
        };
    }

    public float getBoundInDirection(Direction2D pDirection) {
        var screenaxis = pDirection.getAxis();
        return pDirection.isPositive() ? position.getCoordinate(screenaxis) + getLength(screenaxis) - 1 : position.getCoordinate(screenaxis);
    }

    public ScissorRect getBorder(Direction2D pDirection) {
        var i = getBoundInDirection(pDirection);
        var screenaxis = pDirection.getAxis().orthogonal();
        var j = getBoundInDirection(screenaxis.getNegative());
        var k = getLength(screenaxis);
        return of(pDirection.getAxis(), i, j, 1, k).step(pDirection);
    }

    public boolean overlaps(ScissorRect pRectangle) {
        return overlapsInAxis(pRectangle, Axis2D.HORIZONTAL) && overlapsInAxis(pRectangle, Axis2D.VERTICAL);
    }

    public boolean overlapsInAxis(ScissorRect pRectangle, Axis2D pAxis) {
        var i = getBoundInDirection(pAxis.getNegative());
        var j = pRectangle.getBoundInDirection(pAxis.getNegative());
        var k = getBoundInDirection(pAxis.getPositive());
        var l = pRectangle.getBoundInDirection(pAxis.getPositive());
        return Math.max(i, j) <= Math.min(k, l);
    }

    public float getCenterInAxis(Axis2D pAxis) {
        return (getBoundInDirection(pAxis.getPositive()) + getBoundInDirection(pAxis.getNegative())) / 2;
    }

    @Nullable
    public ScissorRect intersection(ScissorRect pRectangle) {
        var i = Math.max(getLeft(), pRectangle.getLeft());
        var j = Math.max(getTop(), pRectangle.getTop());
        var k = Math.min(getRight(), pRectangle.getRight());
        var l = Math.min(getBottom(), pRectangle.getBottom());
        return i < k && j < l ? new ScissorRect(i, j, k - i, l - j) : null;
    }

    public boolean intersects(ScissorRect pRectangle) {
        return getLeft() < pRectangle.getRight()
                && getRight() > pRectangle.getLeft()
                && getTop() < pRectangle.getBottom()
                && getBottom() > pRectangle.getTop();
    }

    public boolean encompasses(ScissorRect pRectangle) {
        return pRectangle.getLeft() >= getLeft()
                && pRectangle.getTop() >= getTop()
                && pRectangle.getRight() <= getRight()
                && pRectangle.getBottom() <= getBottom();
    }

    public float getTop() {
        return position.y;
    }

    public float getBottom() {
        return position.y + height;
    }

    public float getLeft() {
        return position.x;
    }

    public float getRight() {
        return position.x + width;
    }

    public boolean containsPoint(int pX, int pY) {
        return pX >= getLeft() && pX < getRight() && pY >= getTop() && pY < getBottom();
    }

    public ScissorRect transformAxisAligned(Matrix3x2f pPos) {
        var vector2f = pPos.transformPosition(getLeft(), getTop(), new Vector2f());
        var vector2f1 = pPos.transformPosition(getRight(), getBottom(), new Vector2f());
        return new ScissorRect(
                Mth.floor(vector2f.x), Mth.floor(vector2f.y), Mth.floor(vector2f1.x - vector2f.x), Mth.floor(vector2f1.y - vector2f.y)
        );
    }

    public ScissorRect transformMaxBounds(Matrix3x2f pPos) {
        var vector2f = pPos.transformPosition(getLeft(), getTop(), new Vector2f());
        var vector2f1 = pPos.transformPosition(getRight(), getTop(), new Vector2f());
        var vector2f2 = pPos.transformPosition(getLeft(), getBottom(), new Vector2f());
        var vector2f3 = pPos.transformPosition(getRight(), getBottom(), new Vector2f());
        var f = Math.min(Math.min(vector2f.x(), vector2f2.x()), Math.min(vector2f1.x(), vector2f3.x()));
        var f1 = Math.max(Math.max(vector2f.x(), vector2f2.x()), Math.max(vector2f1.x(), vector2f3.x()));
        var f2 = Math.min(Math.min(vector2f.y(), vector2f2.y()), Math.min(vector2f1.y(), vector2f3.y()));
        var f3 = Math.max(Math.max(vector2f.y(), vector2f2.y()), Math.max(vector2f1.y(), vector2f3.y()));
        return new ScissorRect(Mth.floor(f), Mth.floor(f2), Mth.ceil(f1 - f), Mth.ceil(f3 - f2));
    }

    @Override
    public int compareTo(ScissorRect other) {
        var cmp = Float.compare(position.y, other.position.y);
        if (cmp != 0)
            return cmp;
        cmp = Float.compare(position.x, other.position.x);
        if (cmp != 0)
            return cmp;
        cmp = Float.compare(height, other.height);
        if (cmp != 0)
            return cmp;
        return Float.compare(width, other.width);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        var that = (ScissorRect) o;
        return Float.compare(that.width, width) == 0
                && Float.compare(that.height, height) == 0
                && Objects.equals(position, that.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, width, height);
    }

    @Override
    public String toString() {
        return "ScissorRect[" + "position=" + position + ", " + "width=" + width + ", " + "height=" + height + ']';
    }
}