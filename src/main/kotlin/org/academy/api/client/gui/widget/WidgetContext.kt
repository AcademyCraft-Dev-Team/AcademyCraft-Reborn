package org.academy.api.client.gui.widget

/**
 * 一般在添加时创建实例喵
 * <br></br>
 * 属于隐式生命周期喵(引用断开由 GC 清理喵)
 */
interface WidgetContext {
    fun get(): Widget
}