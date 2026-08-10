package org.academy.api.client.render.vfx.lightning;

import org.joml.Vector3f;

public final class LightningPoint {
    public Vector3f position;
    public Vector3f forwardAxis;
    public Vector3f rightAxis;
    public Vector3f upAxis;
    public boolean supportsNextGenerations;

    public LightningPoint() {
    }

    public LightningPoint(
            Vector3f position,
            Vector3f forwardAxis,
            Vector3f rightAxis,
            Vector3f upAxis,
            boolean supportsNextGenerations
    ) {
        this.position = position;
        this.forwardAxis = forwardAxis;
        this.rightAxis = rightAxis;
        this.upAxis = upAxis;
        this.supportsNextGenerations = supportsNextGenerations;
    }
}
