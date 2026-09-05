package org.academy.api.client.gui.render

import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.gui.command.SubmittedCommand
import org.joml.Matrix4f

/**
 * 缓存的子树渲染数据, 对齐安卓的 DisplayList/RenderNode 模型喵.
 *
 * [commands] 的位姿按"世界位姿"录制 (与录制帧提交时一致), [recordOrigin] 为该缓存
 * 录制时的祖先位姿. 祖先位姿未变时回放直接复用世界位姿 (零分配 fast path);
 * 祖先平移/缩放/旋转变化时用 `current * invRecordOrigin * worldPose` 逐命令重组,
 * 不再需要重新录制整棵子树. 这与"把完整 pose 烘焙进每条命令"的旧缓存不同——后者在
 * 祖先变换变化时会整体过期, 只能靠深失效重录兜底喵.
 *
 * P3-11 (矩阵 CPU 重组 → GPU, 延期文档化): 当前 [RenderContext.addRecomposed] 对每条
 * 命令做 CPU `current * invRecordOrigin * worldPose` 并分配新 PoseStack.Pose. 若把顶点
 * 保留在本地空间, 每条命令仅存 recordOrigin 引用, 上传期用 per-draw `DynamicTransforms`
 * 类 uniform (见 UiContext 的 dynamicTransformsUbo: mat4 + vec4 + vec3 + mat4 + float)
 * 传当前矩阵, 由 GPU 完成变换, 可消除重组热点. 前提是 P0-1 层缓存使重组路径不再位于
 * 每帧热点; 亦可作为 `addRecomposed` 的替代实现, 将 pose 字段降级为"是否需重组"标记.
 */
class SubtreeCache(
    /** 命令以 [baseDrawOrder] 为基准的相对渲染序; 回放时按当前遍历深度重定位 (对齐 P2-6). */
    val commands: List<SubmittedCommand>,
    val regions: List<BlurRegion>,
    val recordOrigin: Matrix4f,
    val coverBaseRecordedMax: Long,
    /** 录制期累积祖先 alpha (含自身), 供回放时计算校正乘子, 对齐 RenderNode alpha 合成. */
    val recordAlphaMul: Float,
    /** 录制期本控件自身的 drawOrder 基准, 命令存储相对序. */
    val baseDrawOrder: Long
) {
    companion object {
        /**
         * 回放位姿 = current * invRecordOrigin * worldPose 喵.
         * [worldPose] 为缓存内按世界位姿录制的命令 pose; [invRecordOrigin] = recordOrigin⁻¹,
         * 由 [RenderContext.addRecomposed] 每次调用预计算一次.
         */
        fun recomposePose(worldPose: PoseStack.Pose, current: Matrix4f, invRecordOrigin: Matrix4f): PoseStack.Pose {
            val result = PoseStack.Pose()
            val local = Matrix4f()
            invRecordOrigin.mul(worldPose.pose(), local)
            current.mul(local, result.pose())
            result.normal().set(worldPose.normal())
            return result
        }

        fun sameMatrix(a: Matrix4f, b: Matrix4f): Boolean {
            return a.m00() == b.m00() && a.m01() == b.m01() && a.m02() == b.m02() && a.m03() == b.m03() &&
                a.m10() == b.m10() && a.m11() == b.m11() && a.m12() == b.m12() && a.m13() == b.m13() &&
                a.m20() == b.m20() && a.m21() == b.m21() && a.m22() == b.m22() && a.m23() == b.m23() &&
                a.m30() == b.m30() && a.m31() == b.m31() && a.m32() == b.m32() && a.m33() == b.m33()
        }
    }
}