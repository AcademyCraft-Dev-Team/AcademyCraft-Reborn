package org.academy.internal.common.structure.physics;

public enum BodyType {
    FIXED(0), DYNAMIC(1);

    final int code;

    BodyType(int code) {
        this.code = code;
    }
}
