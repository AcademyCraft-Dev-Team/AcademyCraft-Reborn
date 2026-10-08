package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.render.vfxgraph.sim.SimContext;

import java.util.Arrays;
import java.util.HashMap;

/** Stable shape ownership across swap removal, capacity growth, and editor simulation reset. */
final class ShapeParticleSlots {
    private final int[] indices;
    private final float[] seeds;
    private final HashMap<Float, Integer> owned = new HashMap<>();

    ShapeParticleSlots(int count) {
        indices = new int[count];
        Arrays.fill(indices, -1);
        seeds = new float[count];
    }
    int index(int ordinal) { return indices[ordinal]; }

    void update(ParticleBuffer buffer, SimContext context) {
        boolean unchanged = true;
        for (int ordinal = 0; ordinal < indices.length; ordinal++) {
            int index = indices[ordinal];
            if (index < 0 || index >= buffer.count() || buffer.seed(index) != seeds[ordinal]) {
                unchanged = false;
                break;
            }
        }
        // Maintained shapes usually own the same slots for their complete lifetime. Rebuild the
        // lookup only after swap removal/reset, avoiding a boxed Float lookup per feather/frame.
        if (unchanged) return;
        Arrays.fill(indices, -1);
        for (int i = 0; i < buffer.count(); i++) {
            var ordinal = owned.get(buffer.seed(i));
            if (ordinal != null) indices[ordinal] = i;
        }
        owned.entrySet().removeIf(entry -> indices[entry.getValue()] < 0);
        int start = buffer.count();
        for (int i = 0; i < indices.length; i++) {
            if (indices[i] >= 0) continue;
            indices[i] = buffer.spawn();
            owned.put(buffer.seed(indices[i]), i);
        }
        for (int ordinal = 0; ordinal < indices.length; ordinal++) seeds[ordinal] = buffer.seed(indices[ordinal]);
        context.emitBatch(start, buffer.count());
    }
}
