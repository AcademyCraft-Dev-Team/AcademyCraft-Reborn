package org.academy.api.client.gui.widget

import org.academy.api.client.gui.event.InputEvent
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode

interface WidgetContainer : Widget {
    val isLayoutDirty: Boolean

    fun addChild(name: String, child: Widget)

    fun removeChild(name: String)

    fun clearChildren()

    val children: Map<String, Widget>

    val hoveredWidget: Widget?

    var focusedChild: Widget?

    fun generateDefaultLayoutParams(): LayoutParams

    fun generateLayoutParams(p: LayoutParams): LayoutParams

    fun checkLayoutParams(p: LayoutParams): Boolean

    fun onInterceptEvent(event: InputEvent): Boolean {
        return false
    }

    open class LayoutParams {
        var widthMode: SizeMode = SizeMode.WRAP_CONTENT
        var heightMode: SizeMode = SizeMode.WRAP_CONTENT
        var gravity: Int = Gravity.TOP_LEFT

        var width: Float = 0f
        var height: Float = 0f

        var marginLeft: Float = 0f
        var marginTop: Float = 0f
        var marginRight: Float = 0f
        var marginBottom: Float = 0f

        var paddingLeft: Float = 0f
        var paddingTop: Float = 0f
        var paddingRight: Float = 0f
        var paddingBottom: Float = 0f

        constructor()

        constructor(source: LayoutParams) {
            widthMode = source.widthMode
            heightMode = source.heightMode
            gravity = source.gravity
            width = source.width
            height = source.height
            marginLeft = source.marginLeft
            marginTop = source.marginTop
            marginRight = source.marginRight
            marginBottom = source.marginBottom
            paddingLeft = source.paddingLeft
            paddingTop = source.paddingTop
            paddingRight = source.paddingRight
            paddingBottom = source.paddingBottom
        }

        fun widthMode(mode: SizeMode): LayoutParams {
            widthMode = mode
            return this
        }

        fun heightMode(mode: SizeMode): LayoutParams {
            heightMode = mode
            return this
        }

        fun sizeMode(mode: SizeMode): LayoutParams {
            widthMode = mode
            heightMode = mode
            return this
        }

        fun sizeMode(widthMode: SizeMode, heightMode: SizeMode): LayoutParams {
            this.widthMode = widthMode
            this.heightMode = heightMode
            return this
        }

        fun size(width: Float, height: Float): LayoutParams {
            widthMode = SizeMode.FIXED
            heightMode = SizeMode.FIXED
            this.width = width
            this.height = height
            return this
        }


        fun width(width: Float): LayoutParams {
            widthMode = SizeMode.FIXED
            this.width = width
            return this
        }

        fun height(height: Float): LayoutParams {
            heightMode = SizeMode.FIXED
            this.height = height
            return this
        }

        fun gravity(gravity: Int): LayoutParams {
            this.gravity = gravity
            return this
        }

        fun margin(all: Float): LayoutParams {
            marginLeft = all
            marginTop = all
            marginRight = all
            marginBottom = all
            return this
        }

        fun margin(horizontal: Float, vertical: Float): LayoutParams {
            marginLeft = horizontal
            marginRight = horizontal
            marginTop = vertical
            marginBottom = vertical
            return this
        }

        fun margin(left: Float, top: Float, right: Float, bottom: Float): LayoutParams {
            marginLeft = left
            marginTop = top
            marginRight = right
            marginBottom = bottom
            return this
        }

        fun marginLeft(left: Float): LayoutParams {
            marginLeft = left
            return this
        }

        fun marginTop(top: Float): LayoutParams {
            marginTop = top
            return this
        }

        fun marginRight(right: Float): LayoutParams {
            marginRight = right
            return this
        }

        fun marginBottom(bottom: Float): LayoutParams {
            marginBottom = bottom
            return this
        }

        fun marginHorizontal(horizontal: Float): LayoutParams {
            marginLeft = horizontal
            marginRight = horizontal
            return this
        }

        fun marginVertical(vertical: Float): LayoutParams {
            marginTop = vertical
            marginBottom = vertical
            return this
        }

        fun padding(all: Float): LayoutParams {
            paddingLeft = all
            paddingTop = all
            paddingRight = all
            paddingBottom = all
            return this
        }

        fun padding(horizontal: Float, vertical: Float): LayoutParams {
            paddingLeft = horizontal
            paddingRight = horizontal
            paddingTop = vertical
            paddingBottom = vertical
            return this
        }

        fun padding(left: Float, top: Float, right: Float, bottom: Float): LayoutParams {
            paddingLeft = left
            paddingTop = top
            paddingRight = right
            paddingBottom = bottom
            return this
        }

        fun paddingLeft(left: Float): LayoutParams {
            paddingLeft = left
            return this
        }

        fun paddingTop(top: Float): LayoutParams {
            paddingTop = top
            return this
        }

        fun paddingRight(right: Float): LayoutParams {
            paddingRight = right
            return this
        }

        fun paddingBottom(bottom: Float): LayoutParams {
            paddingBottom = bottom
            return this
        }

        fun paddingHorizontal(horizontal: Float): LayoutParams {
            paddingLeft = horizontal
            paddingRight = horizontal
            return this
        }

        fun paddingVertical(vertical: Float): LayoutParams {
            paddingTop = vertical
            paddingBottom = vertical
            return this
        }

        companion object {
            /**
             * 代表 null 喵
             */
            val NONE: LayoutParams = LayoutParams()
        }
    }
}