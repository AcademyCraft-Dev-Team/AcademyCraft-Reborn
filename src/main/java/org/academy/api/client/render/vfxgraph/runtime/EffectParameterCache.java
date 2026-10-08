package org.academy.api.client.render.vfxgraph.runtime;

import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.GraphEffect;
import org.joml.Vector3fc;

import java.util.HashMap;
import java.util.Map;

/** Changed-value bindings for parameters owned by an adapter, including graph hot reload. */
public final class EffectParameterCache {
    private final Map<String, Value> values = new HashMap<>();
    private GraphEffect graph;
    private long writes;

    public boolean begin(GraphEffect graph) {
        if (this.graph == graph) return false;
        this.graph = java.util.Objects.requireNonNull(graph);
        values.clear();
        return true;
    }
    public void invalidate() { values.clear(); }
    public boolean putFloat(String id, float value) {
        var old = values.get(id);
        if (old instanceof Value.FloatVal scalar && Float.floatToIntBits(scalar.value()) == Float.floatToIntBits(value)) return false;
        return put(id, Value.of(value));
    }
    public boolean putInt(String id, int value) {
        if (values.get(id) instanceof Value.IntVal scalar && scalar.value() == value) return false;
        return put(id, Value.of(value));
    }
    public boolean putVec3(String id, Vector3fc value) {
        if (values.get(id) instanceof Value.Vec3Val vector && vector.x() == value.x() && vector.y() == value.y() && vector.z() == value.z()) return false;
        return put(id, new Value.Vec3Val(value.x(), value.y(), value.z()));
    }
    private boolean put(String id, Value value) {
        if (graph == null) throw new IllegalStateException("Call begin before binding parameters");
        values.put(id, value); graph.setLiveParam(id, value); writes++;
        return true;
    }
    public long writeCount() { return writes; }
}
