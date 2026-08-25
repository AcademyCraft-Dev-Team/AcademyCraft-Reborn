package org.academy.api.client.gui.dsl

import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.ButtonWidget
import org.academy.api.client.gui.widget.ToggleButtonWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer

/**
 * 声明式 UI DSL 喵.
 *
 * 核心思路: 在 [WidgetContainer] 上提供创建子控件的扩展函数, 容器工厂在
 * `addChild` 之后执行配置 lambda, 因此 lambda 内 `layoutParams` 已是容器生成的
 * 正确子类, 可直接读写字段与使用 [lp] 辅助.
 */

// ============ 通用布局辅助 (Widget 扩展) ============

/** 打开当前 [layoutParams] 并按 [init] 配置字段. 若尚未分配则先分配. */
fun Widget.lp(init: WidgetContainer.LayoutParams.() -> Unit): Widget {
    if (layoutParams === WidgetContainer.LayoutParams.NONE) {
        layoutParams = WidgetContainer.LayoutParams()
    }
    layoutParams.init()
    return this
}

/** FIXED 尺寸. */
fun Widget.size(width: Float, height: Float): Widget {
    lp {
        this.width = width
        this.height = height
        widthMode = SizeMode.FIXED
        heightMode = SizeMode.FIXED
    }
    return this
}

fun Widget.size(widthMode: SizeMode, heightMode: SizeMode): Widget {
    lp {
        this.widthMode = widthMode
        this.heightMode = heightMode
    }
    return this
}

fun Widget.matchWidth(): Widget {
    lp { widthMode = SizeMode.MATCH_PARENT }
    return this
}

fun Widget.matchHeight(): Widget {
    lp { heightMode = SizeMode.MATCH_PARENT }
    return this
}

fun Widget.matchParent(): Widget {
    lp {
        widthMode = SizeMode.MATCH_PARENT
        heightMode = SizeMode.MATCH_PARENT
    }
    return this
}

/** 线性布局权重 (仅对 LinearLayout 子级生效). */
fun Widget.weight(weight: Float): Widget {
    (layoutParams as? org.academy.api.client.gui.widget.LinearLayoutWidget.LayoutParams)?.weight = weight
    return this
}

fun Widget.gravity(gravity: Int): Widget {
    lp { this.gravity = gravity }
    return this
}

fun Widget.margin(all: Float): Widget {
    lp {
        marginLeft = all
        marginTop = all
        marginRight = all
        marginBottom = all
    }
    return this
}

fun Widget.margin(horizontal: Float, vertical: Float): Widget {
    lp {
        marginLeft = horizontal
        marginRight = horizontal
        marginTop = vertical
        marginBottom = vertical
    }
    return this
}

fun Widget.margin(left: Float, top: Float, right: Float, bottom: Float): Widget {
    lp {
        marginLeft = left
        marginTop = top
        marginRight = right
        marginBottom = bottom
    }
    return this
}

fun Widget.padding(all: Float): Widget {
    lp {
        paddingLeft = all
        paddingTop = all
        paddingRight = all
        paddingBottom = all
    }
    return this
}

fun Widget.padding(horizontal: Float, vertical: Float): Widget {
    lp {
        paddingLeft = horizontal
        paddingRight = horizontal
        paddingTop = vertical
        paddingBottom = vertical
    }
    return this
}

fun Widget.padding(left: Float, top: Float, right: Float, bottom: Float): Widget {
    lp {
        paddingLeft = left
        paddingTop = top
        paddingRight = right
        paddingBottom = bottom
    }
    return this
}

// ============ 锚点约束辅助 ============

fun Widget.anchors(h: Float, v: Float): Widget {
    lp {
        anchorX = h
        anchorY = v
    }
    return this
}

fun Widget.anchors2(h: Float, v: Float): Widget {
    lp {
        anchorX2 = h
        anchorY2 = v
    }
    return this
}

fun Widget.offset(x: Float, y: Float): Widget {
    lp {
        offsetX = x
        offsetY = y
    }
    return this
}

fun Widget.stretch(h: Boolean, v: Boolean): Widget {
    lp {
        stretchX = h
        stretchY = v
    }
    return this
}

fun Widget.widthPercent(p: Float): Widget {
    lp {
        widthMode = SizeMode.PERCENT
        widthPercent = p
    }
    return this
}

fun Widget.heightPercent(p: Float): Widget {
    lp {
        heightMode = SizeMode.PERCENT
        heightPercent = p
    }
    return this
}

// ============ 交互辅助 ============

/** 给按钮类控件设置点击回调. 非按钮控件无操作. */
fun Widget.onClick(handler: () -> Unit): Widget {
    (this as? ButtonWidget)?.onClickListener = OnClickListener { handler() }
    return this
}

fun Widget.onCheckedChange(handler: (Boolean) -> Unit): Widget {
    (this as? ToggleButtonWidget)?.onCheckedChangeListener =
        object : ToggleButtonWidget.OnCheckedChangeListener {
            override fun onCheckedChanged(toggle: ToggleButtonWidget, isChecked: Boolean) {
                handler(isChecked)
            }
        }
    return this
}

// ============ 名称生成 ============

/** 生成容器内唯一的默认子控件名: `base`, `base_1`, `base_2`... */
fun WidgetContainer.nextChildName(base: String): String {
    if (base !in children) return base
    var n = 1
    while ("${base}_$n" in children) n++
    return "${base}_$n"
}
