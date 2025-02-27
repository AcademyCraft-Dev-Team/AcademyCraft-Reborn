package org.academy.api.common.curriculum;

import java.util.ArrayList;
import java.util.List;

public abstract class Curriculum {
    public final String title;
    public final String identifier;
    public final List<Curriculum> curriculums = new ArrayList<>();

    protected Curriculum(String title, String identifier) {
        this.title = title;
        this.identifier = identifier;
    }
}