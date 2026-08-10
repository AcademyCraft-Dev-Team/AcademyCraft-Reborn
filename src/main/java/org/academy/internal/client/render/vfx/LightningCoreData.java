package org.academy.internal.client.render.vfx;

public record LightningCoreData(TubeMesh tube) implements LightningMeshData {
    @Override
    public TubeMesh tube() {
        return tube;
    }
}
