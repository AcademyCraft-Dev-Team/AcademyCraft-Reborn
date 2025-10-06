package org.academy.api.client.gui.framework;

import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import javax.annotation.Nullable;
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
        return this.position;
    }

    public float getWidth() {
        return this.width;
    }

    public float getHeight() {
        return this.height;
    }

    public ScissorRect step(Direction2D pDirection) {
        return new ScissorRect(this.position.step(pDirection), this.width, this.height);
    }

    public float getLength(Axis2D axis) {
        return switch (axis) {
            case HORIZONTAL -> this.width;
            case VERTICAL -> this.height;
        };
    }

    public float getBoundInDirection(Direction2D pDirection) {
        var screenaxis = pDirection.getAxis();
        return pDirection.isPositive() ? this.position.getCoordinate(screenaxis) + this.getLength(screenaxis) - 1 : this.position.getCoordinate(screenaxis);
    }

    public ScissorRect getBorder(Direction2D pDirection) {
        var i = this.getBoundInDirection(pDirection);
        var screenaxis = pDirection.getAxis().orthogonal();
        var j = this.getBoundInDirection(screenaxis.getNegative());
        var k = this.getLength(screenaxis);
        return of(pDirection.getAxis(), i, j, 1, k).step(pDirection);
    }

    public boolean overlaps(ScissorRect pRectangle) {
        return this.overlapsInAxis(pRectangle, Axis2D.HORIZONTAL) && this.overlapsInAxis(pRectangle, Axis2D.VERTICAL);
    }

    public boolean overlapsInAxis(ScissorRect pRectangle, Axis2D pAxis) {
        var i = this.getBoundInDirection(pAxis.getNegative());
        var j = pRectangle.getBoundInDirection(pAxis.getNegative());
        var k = this.getBoundInDirection(pAxis.getPositive());
        var l = pRectangle.getBoundInDirection(pAxis.getPositive());
        return Math.max(i, j) <= Math.min(k, l);
    }

    public float getCenterInAxis(Axis2D pAxis) {
        return (this.getBoundInDirection(pAxis.getPositive()) + this.getBoundInDirection(pAxis.getNegative())) / 2;
    }

    @Nullable
    public ScissorRect intersection(ScissorRect pRectangle) {
        var i = Math.max(this.getLeft(), pRectangle.getLeft());
        var j = Math.max(this.getTop(), pRectangle.getTop());
        var k = Math.min(this.getRight(), pRectangle.getRight());
        var l = Math.min(this.getBottom(), pRectangle.getBottom());
        return i < k && j < l ? new ScissorRect(i, j, k - i, l - j) : null;
    }

    public boolean intersects(ScissorRect pRectangle) {
        return this.getLeft() < pRectangle.getRight()
                && this.getRight() > pRectangle.getLeft()
                && this.getTop() < pRectangle.getBottom()
                && this.getBottom() > pRectangle.getTop();
    }

    public boolean encompasses(ScissorRect pRectangle) {
        return pRectangle.getLeft() >= this.getLeft()
                && pRectangle.getTop() >= this.getTop()
                && pRectangle.getRight() <= this.getRight()
                && pRectangle.getBottom() <= this.getBottom();
    }

    public float getTop() {
        return this.position.y;
    }

    public float getBottom() {
        return this.position.y + this.height;
    }

    public float getLeft() {
        return this.position.x;
    }

    public float getRight() {
        return this.position.x + this.width;
    }

    public boolean containsPoint(int pX, int pY) {
        return pX >= this.getLeft() && pX < this.getRight() && pY >= this.getTop() && pY < this.getBottom();
    }

    public ScissorRect transformAxisAligned(Matrix3x2f pPos) {
        var vector2f = pPos.transformPosition(this.getLeft(), this.getTop(), new Vector2f());
        var vector2f1 = pPos.transformPosition(this.getRight(), this.getBottom(), new Vector2f());
        return new ScissorRect(
                Mth.floor(vector2f.x), Mth.floor(vector2f.y), Mth.floor(vector2f1.x - vector2f.x), Mth.floor(vector2f1.y - vector2f.y)
        );
    }

    public ScissorRect transformMaxBounds(Matrix3x2f pPos) {
        var vector2f = pPos.transformPosition(this.getLeft(), this.getTop(), new Vector2f());
        var vector2f1 = pPos.transformPosition(this.getRight(), this.getTop(), new Vector2f());
        var vector2f2 = pPos.transformPosition(this.getLeft(), this.getBottom(), new Vector2f());
        var vector2f3 = pPos.transformPosition(this.getRight(), this.getBottom(), new Vector2f());
        var f = Math.min(Math.min(vector2f.x(), vector2f2.x()), Math.min(vector2f1.x(), vector2f3.x()));
        var f1 = Math.max(Math.max(vector2f.x(), vector2f2.x()), Math.max(vector2f1.x(), vector2f3.x()));
        var f2 = Math.min(Math.min(vector2f.y(), vector2f2.y()), Math.min(vector2f1.y(), vector2f3.y()));
        var f3 = Math.max(Math.max(vector2f.y(), vector2f2.y()), Math.max(vector2f1.y(), vector2f3.y()));
        return new ScissorRect(Mth.floor(f), Mth.floor(f2), Mth.ceil(f1 - f), Mth.ceil(f3 - f2));
    }

    @Override
    public int compareTo(ScissorRect other) {
        int cmp = Float.compare(this.position.y, other.position.y);
        if (cmp != 0)
            return cmp;
        cmp = Float.compare(this.position.x, other.position.x);
        if (cmp != 0)
            return cmp;
        cmp = Float.compare(this.height, other.height);
        if (cmp != 0)
            return cmp;
        return Float.compare(this.width, other.width);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        var that = (ScissorRect) o;
        return Float.compare(that.width, this.width) == 0
                && Float.compare(that.height, this.height) == 0
                && Objects.equals(this.position, that.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.position, this.width, this.height);
    }

    @Override
    public String toString() {
        return "ScissorRect[" + "position=" + this.position + ", " + "width=" + this.width + ", " + "height=" + this.height + ']';
    }
}