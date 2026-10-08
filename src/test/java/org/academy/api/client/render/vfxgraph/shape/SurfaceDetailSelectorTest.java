package org.academy.api.client.render.vfxgraph.shape;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SurfaceDetailSelectorTest {
    private static final Matrix4f PROJECTION = new Matrix4f().setPerspective((float) Math.PI / 2, 16f / 9, .01f, 256);

    @Test void cameraJitterAcrossEitherBoundaryDoesNotOscillateSurfaceMeshes() {
        var selector = new SurfaceDetailSelector();
        assertEquals(SurfaceDetailSelector.FULL, atSize(selector, .11f));
        assertEquals(SurfaceDetailSelector.MEDIUM, atSize(selector, .09f));
        for (float size : new float[]{.099f, .101f, .119f, .105f})
            assertEquals(SurfaceDetailSelector.MEDIUM, atSize(selector, size));
        assertEquals(SurfaceDetailSelector.FULL, atSize(selector, .121f));
        assertEquals(SurfaceDetailSelector.FAR, atSize(selector, .04f));
        for (float size : new float[]{.044f, .046f, .054f, .049f})
            assertEquals(SurfaceDetailSelector.FAR, atSize(selector, size));
        assertEquals(SurfaceDetailSelector.MEDIUM, atSize(selector, .056f));
        assertEquals(SurfaceDetailSelector.FULL, atSize(selector, .13f));
    }

    @Test void largerWingsAndCameraZoomKeepFullDetailAtTheSameActorDistance() {
        var projection = new Matrix4f().setPerspective((float) Math.toRadians(70), 16f / 9, .01f, 256);
        assertEquals(SurfaceDetailSelector.MEDIUM, new SurfaceDetailSelector().update(1.8f, 8.25f, 50, projection));
        assertEquals(SurfaceDetailSelector.FULL, new SurfaceDetailSelector().update(3.6f, 16.5f, 50, projection));
        var zoom = new Matrix4f().setPerspective((float) Math.toRadians(20), 16f / 9, .01f, 256);
        assertEquals(SurfaceDetailSelector.FULL, new SurfaceDetailSelector().update(1.8f, 8.25f, 50, zoom));
        assertEquals(new SurfaceDetailSelector().update(1.8f, 8.25f, 50, projection),
                new SurfaceDetailSelector().update(3.6f, 16.5f, 100, projection), "Uniform scaling preserves projected geometry");
    }

    @Test void inspectionAndCamerasInsideCompleteBoundsRestoreFullGeometry() {
        var selector = new SurfaceDetailSelector();
        assertEquals(SurfaceDetailSelector.FAR, selector.update(1.8f, 8.25f, 120, PROJECTION));
        assertEquals(SurfaceDetailSelector.FULL, selector.update(1.8f, 8.25f, 7, PROJECTION));
        selector.update(1.8f, 8.25f, 120, PROJECTION);
        assertEquals(SurfaceDetailSelector.FULL, selector.update(1.8f, 8.25f, 120,
                new Matrix4f().setOrtho(-8, 8, -4.5f, 4.5f, .01f, 256)));
        assertEquals(SurfaceDetailSelector.FULL, selector.update(Float.NaN, 8.25f, 120, PROJECTION));
        assertEquals(SurfaceDetailSelector.FULL, selector.update(1.8f, 8.25f, Float.POSITIVE_INFINITY, PROJECTION));
    }

    @Test void selectorsDoNotShareStateBetweenNearbyAndDistantActors() {
        var near = new SurfaceDetailSelector();
        var far = new SurfaceDetailSelector();
        assertEquals(SurfaceDetailSelector.FAR, atSize(far, .03f));
        assertEquals(SurfaceDetailSelector.FULL, atSize(near, .11f));
        assertEquals(SurfaceDetailSelector.FAR, atSize(far, .05f));
        assertEquals(SurfaceDetailSelector.FULL, near.detail());
    }

    private static int atSize(SurfaceDetailSelector selector, float size) {
        return selector.update(1, 2, 2 + Math.abs(PROJECTION.m11()) / size, PROJECTION);
    }
}
