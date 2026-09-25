package org.academy.desktop.grapheditor.viewport

import org.academy.api.client.render.vfxgraph.render.GraphCamera
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * 轨道相机（M14-02）：围绕 [target] 的 yaw/pitch/distance 球面坐标。
 * 产出与 [GraphCamera] 兼容的纯旋转视图矩阵 + 透视投影。
 */
class OrbitCamera {
    var yaw = 0.6f
    var pitch = 0.35f
    var distance = 8f
    var targetX = 0f
    var targetY = 0f
    var targetZ = 0f
    var fov = Math.toRadians(60.0).toFloat()

    fun eyePosition(): Vector3f {
        val cp = kotlin.math.cos(pitch)
        val x = targetX + distance * kotlin.math.cos(yaw) * cp
        val y = targetY + distance * kotlin.math.sin(pitch)
        val z = targetZ + distance * kotlin.math.sin(yaw) * cp
        return Vector3f(x, y, z)
    }

    /** 纯旋转视图矩阵（行 = [right, up, -forward]）。 */
    fun viewRotation(): Matrix4f {
        val eye = eyePosition()
        val f = Vector3f(targetX - eye.x, targetY - eye.y, targetZ - eye.z)
        if (f.lengthSquared() < 1e-6f) {
            f.set(0f, 0f, -1f)
        } else {
            f.normalize()
        }
        val worldUp = Vector3f(0f, 1f, 0f)
        val r = Vector3f(f).cross(worldUp)
        if (r.lengthSquared() < 1e-6f) {
            r.set(1f, 0f, 0f)
        } else {
            r.normalize()
        }
        val u = Vector3f(r).cross(f)
        // JOML Matrix4f.set 的参数按列主序（m00,m01,.. 为第一列），故须按列传：列0=r、列1=u、列2=-f。
        // 旧实现按行传导致矩阵被转置，特效原点映射到相机背后（需旋转~90°才可见）。
        return Matrix4f().set(
            r.x, u.x, -f.x, 0f,
            r.y, u.y, -f.y, 0f,
            r.z, u.z, -f.z, 0f,
            0f, 0f, 0f, 1f
        )
    }

    fun projection(aspect: Float): Matrix4f =
        Matrix4f().setPerspective(fov, if (aspect <= 0f) 1f else aspect, 0.1f, 1000f)

    fun toGraphCamera(aspect: Float): GraphCamera =
        GraphCamera(eyePosition(), viewRotation(), projection(aspect))
}
