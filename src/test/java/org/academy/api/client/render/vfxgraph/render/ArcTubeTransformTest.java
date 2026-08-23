package org.academy.api.client.render.vfxgraph.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.academy.api.client.render.vfxgraph.arc.ArcCurve;
import org.academy.api.client.render.vfxgraph.arc.CurveToMeshBuilder;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * 电弧管顶点烘焙（M22-Rev2）：WorldTransform + overall_scale + 相机相对坐标。
 * 视图为纯旋转矩阵，平移必须写进顶点——此前未烘焙导致编辑器缩放/移动对 arc 无效。
 *
 * <p>注意：LWJGL {@code BufferUtils} 创建的缓冲为 LITTLE_ENDIAN，{@code duplicate()} 会重置为
 * BIG_ENDIAN 导致读浮点错位，故测试直接用 {@code mesh.vertexBuffer()}（不复制）。</p>
 */
class ArcTubeTransformTest {

    private static CurveToMeshBuilder.MeshData twoPointTube() {
        var arc = new ArcCurve();
        arc.addPoint(0f, 0f, 0f, 0.01f, 0);
        arc.addPoint(0f, 1f, 0f, 0.01f, 0);
        arc.setColor(1f, 0.5f, 0.2f, 1f);
        return CurveToMeshBuilder.build(arc, 4, 1f, 0.5f, 0.2f, 1f, 0.6f);
    }

    /** 顶点 i 的位置（读取烘焙后的 buffer）。 */
    private static float[] pos(java.nio.ByteBuffer buf, int vertex) {
        int base = vertex * CurveToMeshBuilder.FLOATS_PER_VERTEX * 4;
        return new float[]{buf.getFloat(base), buf.getFloat(base + 4), buf.getFloat(base + 8)};
    }

    private static float[] normal(java.nio.ByteBuffer buf, int vertex) {
        int base = vertex * CurveToMeshBuilder.FLOATS_PER_VERTEX * 4;
        return new float[]{buf.getFloat(base + 12), buf.getFloat(base + 16), buf.getFloat(base + 20)};
    }

    private static void assertVec(float[] expected, float[] actual, float eps) {
        assertEquals(expected[0], actual[0], eps);
        assertEquals(expected[1], actual[1], eps);
        assertEquals(expected[2], actual[2], eps);
    }

    @Test
    void identityAndZeroCamPosLeavesPositionsUnchanged() {
        var mesh = twoPointTube();
        var buf = mesh.vertexBuffer();
        var before = pos(buf, 0);
        VfxGraphRenderer.transformArcTubeVertices(buf, mesh.vertexCount(),
                new Vector3f(), WorldTransform.identity(), 1f);
        assertVec(before, pos(buf, 0), 1e-6f);
    }

    @Test
    void cameraTranslationIsBakedIntoVertices() {
        var mesh = twoPointTube();
        var buf = mesh.vertexBuffer();
        var before = pos(buf, 0);
        // 相机在 (3,4,5)：顶点应变为 world - camPos
        VfxGraphRenderer.transformArcTubeVertices(buf, mesh.vertexCount(),
                new Vector3f(3f, 4f, 5f), WorldTransform.identity(), 1f);
        var after = pos(buf, 0);
        assertEquals(before[0] - 3f, after[0], 1e-5f);
        assertEquals(before[1] - 4f, after[1], 1e-5f);
        assertEquals(before[2] - 5f, after[2], 1e-5f);
    }

    @Test
    void overallScaleScalesLocalGeometry() {
        var mesh = twoPointTube();
        var buf = mesh.vertexBuffer();
        var before = pos(buf, 0);
        VfxGraphRenderer.transformArcTubeVertices(buf, mesh.vertexCount(),
                new Vector3f(), WorldTransform.identity(), 2f);
        assertVec(new float[]{before[0] * 2, before[1] * 2, before[2] * 2}, pos(buf, 0), 1e-5f);
    }

    @Test
    void worldTransformTranslateShiftsThenCamPosSubtracts() {
        var mesh = twoPointTube();
        var buf = mesh.vertexBuffer();
        var before = pos(buf, 0);
        var t = new WorldTransform(new Vector3f(10f, 20f, 30f), new Quaternionf(), 1f);
        VfxGraphRenderer.transformArcTubeVertices(buf, mesh.vertexCount(),
                new Vector3f(0f, 0f, 0f), t, 1f);
        var after = pos(buf, 0);
        assertEquals(before[0] + 10f, after[0], 1e-5f);
        assertEquals(before[1] + 20f, after[1], 1e-5f);
        assertEquals(before[2] + 30f, after[2], 1e-5f);
    }

    @Test
    void overallScaleAndWorldTransformCompose() {
        var mesh = twoPointTube();
        var buf = mesh.vertexBuffer();
        var before = pos(buf, 0);
        var t = new WorldTransform(new Vector3f(5f, 0f, 5f), new Quaternionf(), 1f);
        VfxGraphRenderer.transformArcTubeVertices(buf, mesh.vertexCount(),
                new Vector3f(1f, 1f, 1f), t, 3f);
        // expected = apply(k * local) - camPos，k=overall_scale
        var expected = new float[3];
        t.apply(before[0] * 3f, before[1] * 3f, before[2] * 3f, expected);
        expected[0] -= 1f;
        expected[1] -= 1f;
        expected[2] -= 1f;
        assertVec(expected, pos(buf, 0), 1e-4f);
    }

    @Test
    void rotatedTransformRotatesNormals() {
        var mesh = twoPointTube();
        var buf = mesh.vertexBuffer();
        var beforeN = normal(buf, 0);
        // 绕 X 轴 90°
        var rot = new Quaternionf().rotateX((float) Math.PI / 2f);
        var t = new WorldTransform(new Vector3f(), rot, 1f);
        VfxGraphRenderer.transformArcTubeVertices(buf, mesh.vertexCount(),
                new Vector3f(), t, 1f);
        var expected = new float[3];
        t.applyDirection(beforeN[0], beforeN[1], beforeN[2], expected);
        float len = (float) Math.sqrt(expected[0] * expected[0] + expected[1] * expected[1] + expected[2] * expected[2]);
        expected[0] /= len;
        expected[1] /= len;
        expected[2] /= len;
        assertVec(expected, normal(buf, 0), 1e-4f);
    }
}