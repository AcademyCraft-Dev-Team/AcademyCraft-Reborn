package org.academy.api.client.render.vfxgraph.instance;

import java.nio.ByteBuffer;

import org.jspecify.annotations.Nullable;

public final class InstanceGeometryBuffer {
    private @Nullable ByteBuffer vertices;
    private int vertexCount;
    private @Nullable ByteBuffer instances;
    private int instanceCount;

    public void setFrame(@Nullable InstanceGeometryFrame frame) {
        if (frame == null) {
            vertices = null;
            vertexCount = 0;
            instances = null;
            instanceCount = 0;
            return;
        }
        vertices = frame.vertices();
        vertexCount = frame.vertexCount();
        instances = frame.instances();
        instanceCount = frame.instanceCount();
    }

    public @Nullable ByteBuffer vertices() {
        return vertices;
    }

    public int vertexCount() {
        return vertexCount;
    }

    public @Nullable ByteBuffer instances() {
        return instances;
    }

    public int instanceCount() {
        return instanceCount;
    }

    public boolean isEmpty() {
        return vertexCount == 0 || instanceCount == 0;
    }
}
