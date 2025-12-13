package org.academy.mixin.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.academy.internal.client.renderer.entity.layers.quantum.QuantumRenderStateExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public class MixinLivingEntityRenderState implements QuantumRenderStateExtension {
    @Unique
    private boolean academy$quantumActive;
    @Unique
    private float academy$quantumIntensity;
    @Unique
    private int academy$quantumColor;
    @Unique
    private float academy$realWidth;
    @Unique
    private float academy$realHeight;

    @Override
    public boolean academy$isQuantumActive() {
        return academy$quantumActive;
    }

    @Override
    public float academy$getQuantumIntensity() {
        return academy$quantumIntensity;
    }

    @Override
    public int academy$getQuantumColor() {
        return academy$quantumColor;
    }

    @Override
    public void academy$setQuantumState(boolean active, float intensity, int color) {
        academy$quantumActive = active;
        academy$quantumIntensity = intensity;
        academy$quantumColor = color;
    }

    @Override
    public float academy$getRealWidth() {
        return academy$realWidth;
    }

    @Override
    public float academy$getRealHeight() {
        return academy$realHeight;
    }

    @Override
    public void academy$setRealSize(float width, float height) {
        academy$realWidth = width;
        academy$realHeight = height;
    }

}