package org.academy.api.client.vanilla;

import net.neoforged.bus.api.Event;
import org.academy.api.client.thread.RenderThread;

/**
 * GUI 渲染完成后由渲染线程发布的合成事件喵.
 * 语义: 主缓冲已含世界 + 原版屏幕背景 + Academy 下方内容, 即模糊面板背后的完整背景,
 * 可供 UI 模糊就地烘焙.
 */
@RenderThread
public final class WorldCompositeEvent extends Event {
}
