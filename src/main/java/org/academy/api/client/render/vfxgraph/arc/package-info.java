/**
 * Blender 式电弧子系统（M22-Rev2）：复刻 Blender "闪电附着" Geometry Nodes 流水线。
 *
 * <p>三层流水线：① CPU 表面布点 + 递归曲线生成（含 Bezier 平滑）→
 * ② 噪声动画 + 表面约束（每帧 tick）→ ③ 管网格构建 + emission 着色器。</p>
 *
 * <p>数据驱动：所有参数来自 VFX 图块属性，经 RenderSpec 传递到渲染器，零硬编码常量。</p>
 *
 * @see org.academy.api.client.render.vfxgraph.render.VfxGraphRenderer#drawArcTubes
 */
@org.jspecify.annotations.NullMarked
package org.academy.api.client.render.vfxgraph.arc;
