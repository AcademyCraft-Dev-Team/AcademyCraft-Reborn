package org.academy.api.common.arc.data;

import org.joml.Vector3f;
import org.joml.Vector3fc;

public record PathFrame(Vector3fc position, Vector3f tangent, Vector3f normal) {
}