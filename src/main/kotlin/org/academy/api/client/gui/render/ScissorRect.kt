package org.academy.api.client.gui.render

import net.minecraft.util.Mth
import org.academy.api.common.util.MathUtil.Axis2D
import org.academy.api.common.util.MathUtil.Direction2D
import org.joml.Matrix3x2f
import org.joml.Vector2f
import java.util.*
import kotlin.math.max
import kotlin.math.min

class ScissorRect(val position: Position2D, val width: Float, val height: Float) : Comparable<ScissorRect> {
    constructor(x: Float, y: Float, width: Float, height: Float) : this(Position2D(x, y), width, height)

    fun step(pDirection: Direction2D): ScissorRect {
        return ScissorRect(position.step(pDirection), width, height)
    }

    fun getLength(axis: Axis2D): Float {
        return when (axis) {
            Axis2D.HORIZONTAL -> width
            Axis2D.VERTICAL -> height
        }
    }

    fun getBoundInDirection(pDirection: Direction2D): Float {
        val screenaxis = pDirection.axis
        return if (pDirection.isPositive) position.getCoordinate(screenaxis) + getLength(screenaxis) - 1 else position.getCoordinate(
            screenaxis
        )
    }

    fun getBorder(pDirection: Direction2D): ScissorRect {
        val i = getBoundInDirection(pDirection)
        val screenaxis = pDirection.axis.orthogonal()
        val j = getBoundInDirection(screenaxis.negative)
        val k = getLength(screenaxis)
        return of(pDirection.axis, i, j, 1f, k).step(pDirection)
    }

    fun overlaps(pRectangle: ScissorRect): Boolean {
        return overlapsInAxis(pRectangle, Axis2D.HORIZONTAL) && overlapsInAxis(pRectangle, Axis2D.VERTICAL)
    }

    fun overlapsInAxis(pRectangle: ScissorRect, pAxis: Axis2D): Boolean {
        val i = getBoundInDirection(pAxis.negative)
        val j = pRectangle.getBoundInDirection(pAxis.negative)
        val k = getBoundInDirection(pAxis.positive)
        val l = pRectangle.getBoundInDirection(pAxis.positive)
        return max(i, j) <= min(k, l)
    }

    fun getCenterInAxis(pAxis: Axis2D): Float {
        return (getBoundInDirection(pAxis.positive) + getBoundInDirection(pAxis.negative)) / 2
    }

    fun intersection(pRectangle: ScissorRect): ScissorRect? {
        val i = max(this.left, pRectangle.left)
        val j = max(this.top, pRectangle.top)
        val k = min(this.right, pRectangle.right)
        val l = min(this.bottom, pRectangle.bottom)
        return if (i < k && j < l) ScissorRect(i, j, k - i, l - j) else null
    }

    fun intersects(pRectangle: ScissorRect): Boolean {
        return this.left < pRectangle.right && this.right > pRectangle.left && this.top < pRectangle.bottom && this.bottom > pRectangle.top
    }

    fun encompasses(pRectangle: ScissorRect): Boolean {
        return pRectangle.left >= this.left && pRectangle.top >= this.top && pRectangle.right <= this.right && pRectangle.bottom <= this.bottom
    }

    val top: Float
        get() = position.y

    val bottom: Float
        get() = position.y + height

    val left: Float
        get() = position.x

    val right: Float
        get() = position.x + width

    fun containsPoint(pX: Int, pY: Int): Boolean {
        return pX >= this.left && pX < this.right && pY >= this.top && pY < this.bottom
    }

    fun transformAxisAligned(pPos: Matrix3x2f): ScissorRect {
        val vector2f = pPos.transformPosition(this.left, this.top, Vector2f())
        val vector2f1 = pPos.transformPosition(this.right, this.bottom, Vector2f())
        return ScissorRect(
            Mth.floor(vector2f.x).toFloat(),
            Mth.floor(vector2f.y).toFloat(),
            Mth.floor(vector2f1.x - vector2f.x).toFloat(),
            Mth.floor(vector2f1.y - vector2f.y).toFloat()
        )
    }

    fun transformMaxBounds(pPos: Matrix3x2f): ScissorRect {
        val vector2f = pPos.transformPosition(this.left, this.top, Vector2f())
        val vector2f1 = pPos.transformPosition(this.right, this.top, Vector2f())
        val vector2f2 = pPos.transformPosition(this.left, this.bottom, Vector2f())
        val vector2f3 = pPos.transformPosition(this.right, this.bottom, Vector2f())
        val f = min(min(vector2f.x(), vector2f2.x()), min(vector2f1.x(), vector2f3.x()))
        val f1 = max(max(vector2f.x(), vector2f2.x()), max(vector2f1.x(), vector2f3.x()))
        val f2 = min(min(vector2f.y(), vector2f2.y()), min(vector2f1.y(), vector2f3.y()))
        val f3 = max(max(vector2f.y(), vector2f2.y()), max(vector2f1.y(), vector2f3.y()))
        return ScissorRect(
            Mth.floor(f).toFloat(),
            Mth.floor(f2).toFloat(),
            Mth.ceil(f1 - f).toFloat(),
            Mth.ceil(f3 - f2).toFloat()
        )
    }

    override fun compareTo(other: ScissorRect): Int {
        var cmp = position.y.compareTo(other.position.y)
        if (cmp != 0) return cmp
        cmp = position.x.compareTo(other.position.x)
        if (cmp != 0) return cmp
        cmp = height.compareTo(other.height)
        if (cmp != 0) return cmp
        return width.compareTo(other.width)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ScissorRect
        return that.width.compareTo(width) == 0 && that.height.compareTo(height) == 0 && position == that.position
    }

    override fun hashCode(): Int {
        return Objects.hash(position, width, height)
    }

    override fun toString(): String {
        return "ScissorRect[position=$position, width=$width, height=$height]"
    }

    companion object {
        private val EMPTY = ScissorRect(0f, 0f, 0f, 0f)

        fun empty(): ScissorRect {
            return EMPTY
        }

        fun of(
            pAxis: Axis2D,
            pPrimaryPosition: Float,
            pSecondaryPosition: Float,
            pPrimaryLength: Float,
            pSecondaryLength: Float
        ): ScissorRect {
            return when (pAxis) {
                Axis2D.HORIZONTAL -> ScissorRect(pPrimaryPosition, pSecondaryPosition, pPrimaryLength, pSecondaryLength)
                Axis2D.VERTICAL -> ScissorRect(pSecondaryPosition, pPrimaryPosition, pSecondaryLength, pPrimaryLength)
            }
        }
    }
}