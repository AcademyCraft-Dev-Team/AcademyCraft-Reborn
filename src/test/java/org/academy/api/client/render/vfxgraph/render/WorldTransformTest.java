package org.academy.api.client.render.vfxgraph.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class WorldTransformTest {
    @Test
    void identityLeavesPositionUnchanged() {
        var out = new float[3];
        WorldTransform.identity().apply(1f, 2f, 3f, out);
        assertEquals(1f, out[0], 1e-5f);
        assertEquals(2f, out[1], 1e-5f);
        assertEquals(3f, out[2], 1e-5f);
    }

    @Test
    void translateShiftsPosition() {
        var out = new float[3];
        var t = new WorldTransform(new Vector3f(10f, 20f, 30f), new Quaternionf(), 1f);
        t.apply(1f, 2f, 3f, out);
        assertEquals(11f, out[0], 1e-5f);
        assertEquals(22f, out[1], 1e-5f);
        assertEquals(33f, out[2], 1e-5f);
    }

    @Test
    void scaleScalesAroundOrigin() {
        var out = new float[3];
        var t = new WorldTransform(new Vector3f(0f, 0f, 0f), new Quaternionf(), 2f);
        t.apply(1f, 2f, 3f, out);
        assertEquals(2f, out[0], 1e-5f);
        assertEquals(4f, out[1], 1e-5f);
        assertEquals(6f, out[2], 1e-5f);
    }

    @Test
    void rotationRotatesPosition() {
        var out = new float[3];
        // 绕 Y 轴 90°
        var rot = new Quaternionf().rotateY((float) Math.PI / 2f);
        var t = new WorldTransform(new Vector3f(0f, 0f, 0f), rot, 1f);
        t.apply(1f, 0f, 0f, out);
        assertEquals(0f, out[0], 1e-4f);
        assertEquals(0f, out[1], 1e-4f);
        assertEquals(-1f, out[2], 1e-4f);
    }

    @Test
    void compositionTranslateThenRotate() {
        var out = new float[3];
        var rot = new Quaternionf().rotateY((float) Math.PI / 2f);
        var t = new WorldTransform(new Vector3f(5f, 0f, 5f), rot, 2f);
        // local (1,0,0) → rotate → (0,0,-2) → translate → (5,0,3)
        t.apply(1f, 0f, 0f, out);
        assertEquals(5f, out[0], 1e-4f);
        assertEquals(0f, out[1], 1e-4f);
        assertEquals(3f, out[2], 1e-4f);
    }

    @Test
    void identityDetection() {
        assertTrue(WorldTransform.identity().isIdentity());
        assertTrue(!new WorldTransform(new Vector3f(1f, 0f, 0f), new Quaternionf(), 1f).isIdentity());
        assertTrue(!new WorldTransform(new Vector3f(), new Quaternionf(), 0.5f).isIdentity());
    }

    @Test
    void directionIgnoresTranslationButAppliesRotationAndScale() {
        var out = new float[3];
        // 旋转 + 缩放 + 平移：方向只受旋转/缩放影响，不含平移
        var rot = new Quaternionf().rotateY((float) Math.PI / 2f);
        var t = new WorldTransform(new Vector3f(5f, 0f, 5f), rot, 2f);
        t.applyDirection(1f, 0f, 0f, out);
        assertEquals(0f, out[0], 1e-4f);
        assertEquals(0f, out[1], 1e-4f);
        assertEquals(-2f, out[2], 1e-4f);
    }

    @Test
    void identityDirectionUnchanged() {
        var out = new float[3];
        WorldTransform.identity().applyDirection(0f, 3f, 0f, out);
        assertEquals(0f, out[0], 1e-5f);
        assertEquals(3f, out[1], 1e-5f);
        assertEquals(0f, out[2], 1e-5f);
    }
}
