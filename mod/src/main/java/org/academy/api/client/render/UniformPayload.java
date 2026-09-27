package org.academy.api.client.render;

import net.minecraft.client.renderer.DynamicGpuDataStorage;

public record UniformPayload<T extends DynamicGpuDataStorage.DynamicGpuData>(
        String name, Class<T> type, T data, int size
) {
}
