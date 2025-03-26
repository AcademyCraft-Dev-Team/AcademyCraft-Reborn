package org.academy.api.common.curriculum;

public abstract class Curriculum {
    public final String title;
    public final String identifier;

    protected Curriculum(String title, String identifier) {
        this.title = title;
        this.identifier = identifier;
    }
}