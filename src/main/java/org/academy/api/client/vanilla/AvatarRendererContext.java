package org.academy.api.client.vanilla;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public interface AvatarRendererContext {
    @Nullable Matrix4f takeModelRootMatrix();
}
