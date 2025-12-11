package org.academy.internal.client.renderer.entity.layers.quantum;

public interface QuantumRenderStateExtension {
    boolean academy$isQuantumActive();

    float academy$getQuantumIntensity();

    int academy$getQuantumColor();

    void academy$setQuantumState(boolean active, float intensity, int color);

    float academy$getRealWidth();

    float academy$getRealHeight();

    void academy$setRealSize(float width, float height);
}