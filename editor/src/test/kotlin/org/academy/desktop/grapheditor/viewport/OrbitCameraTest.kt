package org.academy.desktop.grapheditor.viewport

import org.joml.Vector4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OrbitCameraTest {

    @Test
    fun eyePositionOnSphere() {
        val camera = OrbitCamera()
        camera.yaw = 0f
        camera.pitch = 0f
        camera.distance = 10f
        val eye = camera.eyePosition()
        assertEquals(10f, eye.x, 1e-4f)
        assertEquals(0f, eye.y, 1e-4f)
        assertEquals(0f, eye.z, 1e-4f)
    }

    @Test
    fun eyePositionAtPitch() {
        val camera = OrbitCamera()
        camera.yaw = 0f
        camera.pitch = Math.toRadians(90.0).toFloat()
        camera.distance = 10f
        val eye = camera.eyePosition()
        assertEquals(10f, eye.y, 1e-4f)
        assertEquals(0f, eye.x, 1e-4f)
    }

    @Test
    fun viewRotationIsOrthonormal() {
        val camera = OrbitCamera()
        camera.yaw = 0.5f
        camera.pitch = 0.4f
        val m = camera.viewRotation()
        // 行向量两两正交且单位长（旋转矩阵）
        val r0 = FloatArray(3) { m[it, 0] }
        val r1 = FloatArray(3) { m[it, 1] }
        val r2 = FloatArray(3) { m[it, 2] }
        assertTrue(dot(r0, r1).let { kotlin.math.abs(it) } < 1e-4f)
        assertTrue(dot(r0, r2).let { kotlin.math.abs(it) } < 1e-4f)
        assertTrue(dot(r1, r2).let { kotlin.math.abs(it) } < 1e-4f)
        assertTrue(kotlin.math.abs(length(r0) - 1f) < 1e-3f)
        assertTrue(kotlin.math.abs(length(r1) - 1f) < 1e-3f)
        assertTrue(kotlin.math.abs(length(r2) - 1f) < 1e-3f)
    }

    @Test
    fun toGraphCameraProducesPerspective() {
        val camera = OrbitCamera()
        val gc = camera.toGraphCamera(16f / 9f)
        assertEquals(camera.eyePosition().x, gc.position().x, 1e-4f)
        assertEquals(camera.eyePosition().y, gc.position().y, 1e-4f)
    }

    @Test
    fun viewRotationCentersOriginInFront() {
        val camera = OrbitCamera()
        camera.yaw = 0.6f
        camera.pitch = 0.35f
        camera.distance = 8f
        val eye = camera.eyePosition()
        // 特效中心（原点）变换到相机空间：应水平/垂直居中、且在相机前方（z<0）、距离=相机距离。
        // 曾因 Matrix4f.set 按行传参被转置，原点映射到相机背后（z>0），导致默认视角看不到特效。
        val p = Vector4f(-eye.x, -eye.y, -eye.z, 1f)
        p.mul(camera.viewRotation())
        assertEquals(0f, p.x, 1e-2f)
        assertEquals(0f, p.y, 1e-2f)
        assertTrue(p.z < 0f, "origin must be in front of camera, was z=${p.z}")
        assertEquals(-camera.distance, p.z, 0.1f)
    }

    private fun dot(a: FloatArray, b: FloatArray): Float = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun length(a: FloatArray): Float = kotlin.math.sqrt(dot(a, a))
}
