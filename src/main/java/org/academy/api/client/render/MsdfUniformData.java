package org.academy.api.client.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import net.minecraft.client.renderer.DynamicUniformStorage;
import org.joml.Vector4f;

import java.nio.ByteBuffer;

public record MsdfUniformData(
        float range,
        float thickness,
        float outlineThickness,
        Vector4f outlineColor
) implements DynamicUniformStorage.DynamicUniform {
    public static final int UBO_SIZE = new Std140SizeCalculator()
            .putFloat()
            .putFloat()
            .putFloat()
            .putVec4()
            .get();

    @Override
    public void write(ByteBuffer buffer) {
        Std140Builder.intoBuffer(buffer)
                .putFloat(range)
                .putFloat(thickness)
                .putFloat(outlineThickness)
                .putVec4(outlineColor);
    }
}