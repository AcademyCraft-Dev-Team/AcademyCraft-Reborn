package org.academy.api.client.render;

import net.minecraft.client.renderer.DynamicUniformStorage;

public record UniformPayload<T extends DynamicUniformStorage.DynamicUniform>(
        String name, Class<T> type, T data, int size
) {
}