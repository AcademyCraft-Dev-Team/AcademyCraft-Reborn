package org.academy.api.common.arc.data;

import org.joml.Vector3f;

public final class PropertyType<T> {
    public static final PropertyType<Float> THICKNESS = new PropertyType<>();
    public static final PropertyType<Vector3f> COLOR = new PropertyType<>();

    private PropertyType() {
    }
}