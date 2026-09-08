package org.academy.api.client.render.vfxgraph.shape;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FirstPersonBodyOcclusionTest {
    @Test
    void onlyPointsBehindTheBodyFromTheEyeAreHidden() {
        var clip = new FirstPersonBodyOcclusion();
        clip.eye.set(0, 1.6f, 0);
        clip.min.set(-0.24f, 0, -0.24f);
        clip.max.set(0.24f, 1.42f, 0.24f);
        assertTrue(clip.occludes(new Vector3f(0, 0.6f, 0.3f), 0));
        assertFalse(clip.occludes(new Vector3f(2.0f, 0.6f, 0.3f), 0));
        assertFalse(clip.occludes(new Vector3f(0, 1.5f, 0), 0));
        assertFalse(clip.occludes(new Vector3f(0, 1.8f, 0), 0));
        assertFalse(clip.occludes(new Vector3f(0, 0.6f, 0.3f), 2.0f));
    }
}
