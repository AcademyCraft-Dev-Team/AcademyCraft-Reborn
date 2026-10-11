package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfxgraph.shape.SurfaceSampler;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CowReconstructionSurfaceTest {
    private final Vector3f min = new Vector3f(), max = new Vector3f();
    private final Matrix4f transform = new Matrix4f();

    @Test void rearPatchClearsTheRenderedTorsoInsteadOfSamplingInsideTheCollisionBox() {
        CowReconstructionSurface.prepare(false, false, 1, 0, 0, min, max, transform);
        var point = new Vector3f();
        var normal = new Vector3f();
        for (float u : new float[]{.04f, .5f, .96f}) {
            for (float v : new float[]{.04f, .5f, .96f}) {
                SurfaceSampler.box(5, u, v, min, max, transform, point, normal);
                assertTrue(point.z < -10f / 16, "Rear surface must clear the model, which extends beyond z=-.45");
                assertTrue(point.y > .751f && point.y < 1.376f, "The patch stays on the torso above the legs");
                assertEquals(-1, normal.z, 1e-5f);
            }
        }
    }

    @Test void theSamePatchTurnsAndScalesWithTheBodyRatherThanChangingFaces() {
        CowReconstructionSurface.prepare(false, false, 1, 0, 0, min, max, transform);
        var original = new Vector3f();
        var normal = new Vector3f();
        SurfaceSampler.box(5, .38f, .63f, min, max, transform, original, normal);
        CowReconstructionSurface.prepare(false, false, 2, 90, 0, min, max, transform);
        var turned = new Vector3f();
        SurfaceSampler.box(5, .38f, .63f, min, max, transform, turned, normal);
        assertEquals(-original.z * 2, turned.x, 1e-5f);
        assertEquals(original.y * 2, turned.y, 1e-5f);
        assertEquals(original.x * 2, turned.z, 1e-5f);
        assertEquals(1, normal.x, 1e-5f);
        var local = new Matrix4f(transform).invert().transformPosition(new Vector3f(turned));
        assertEquals(max.z, local.z, 1e-5f);
    }

    @Test void coldAdultsClearTheFurLayerAndCalvesUseTheirOwnUnscaledModel() {
        CowReconstructionSurface.prepare(false, true, 1, 0, 0, min, max, transform);
        assertTrue(max.z > 10.5f / 16);
        assertTrue(min.x < -6.5f / 16);
        CowReconstructionSurface.prepare(true, true, 1, 0, 0, min, max, transform);
        var point = new Vector3f();
        SurfaceSampler.box(5, .5f, .5f, min, max, transform, point, new Vector3f());
        assertEquals(1.501f - 15f / 16, point.y, 1e-5f);
        assertTrue(point.z < -6f / 16 && point.z > -.4f);
        assertTrue(max.x - min.x > .5f && max.x - min.x < .52f);
    }

    @Test void terminalSurfaceFollowsTheVanillaDeathRoll() {
        CowReconstructionSurface.prepare(false, false, 1, 0, 20, min, max, transform);
        var point = new Vector3f();
        var normal = new Vector3f();
        SurfaceSampler.box(0, .5f, .5f, min, max, transform, point, normal);
        assertEquals(0, normal.x, 1e-5f);
        assertEquals(1, normal.y, 1e-5f);
        assertTrue(Float.isFinite(point.x) && Float.isFinite(point.y) && Float.isFinite(point.z));
    }
}
