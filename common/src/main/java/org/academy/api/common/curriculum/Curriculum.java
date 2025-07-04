package org.academy.api.common.curriculum;

public abstract class Curriculum {
    public final String name;

    protected Curriculum(String newName) {
        this.name = newName;
    }
}