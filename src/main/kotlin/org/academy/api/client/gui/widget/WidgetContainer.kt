package org.academy.api.client.gui.widget

import org.academy.api.client.gui.event.InputEvent
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode

interface WidgetContainer : Widget {
    val isLayoutDirty: Boolean

    val dirtyChildren: Set<Widget>

    fun onChildInvalidated(child: Widget)

    fun addChild(name: String, child: Widget)

    fun addChild(name: String, child: Widget, runnable: () -> Unit) {
        addChild(name, child)
        runnable()
    }

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

    override fun hasPendingRender(): Boolean {
        return isRenderDirty || dirtyChildren.isNotEmpty()
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

        /** 锚点扩展（[AnchorLayoutWidget]/percent 布局）：以父内容区比例定位/拉伸。 */
        var stretchX: Boolean = false
        var stretchY: Boolean = false
        var anchorX: Float = 0f
        var anchorY: Float = 0f

        /** 拉伸的另一端锚点；<0 视为伸到内容区右/下边缘。 */
        var anchorX2: Float = -1f
        var anchorY2: Float = -1f
        var offsetX: Float = 0f
        var offsetY: Float = 0f
        var widthPercent: Float = 0f
        var heightPercent: Float = 0f

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
            stretchX = source.stretchX
            stretchY = source.stretchY
            anchorX = source.anchorX
            anchorY = source.anchorY
            anchorX2 = source.anchorX2
            anchorY2 = source.anchorY2
            offsetX = source.offsetX
            offsetY = source.offsetY
            widthPercent = source.widthPercent
            heightPercent = source.heightPercent
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

        /** 锚点 (点锚定): 子控件边/中心按 `x * (contentW - childW)` 摆放。 */
        fun anchors(x: Float, y: Float): LayoutParams {
            anchorX = x
            anchorY = y
            return this
        }

        /** 拉伸另一端的锚点; <0 表示伸到内容区右/下边缘。 */
        fun anchors2(x: Float, y: Float): LayoutParams {
            anchorX2 = x
            anchorY2 = y
            return this
        }

        /** 是否沿该轴从 [anchors] 拉伸到 [anchors2]。 */
        fun stretch(x: Boolean, y: Boolean): LayoutParams {
            stretchX = x
            stretchY = y
            return this
        }

        /** 像素偏移叠加在锚点/拉伸结果上。 */
        fun offset(x: Float, y: Float): LayoutParams {
            offsetX = x
            offsetY = y
            return this
        }

        /** 宽度按父内容区百分比 (0-100) 解析。 */
        fun widthPercent(percent: Float): LayoutParams {
            widthMode = SizeMode.PERCENT
            widthPercent = percent / 100f
            return this
        }

        /** 高度按父内容区百分比 (0-100) 解析。 */
        fun heightPercent(percent: Float): LayoutParams {
            heightMode = SizeMode.PERCENT
            heightPercent = percent / 100f
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
