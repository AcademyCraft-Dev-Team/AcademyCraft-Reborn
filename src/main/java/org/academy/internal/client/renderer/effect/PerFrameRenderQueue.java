package org.academy.internal.client.renderer.effect;

import java.util.ArrayList;
import java.util.List;

final class PerFrameRenderQueue<T> {
    private final List<T> entries = new ArrayList<>();
    private Object level;

    void beginFrame(Object level) {
        entries.clear();
        this.level = level;
    }

    void add(T entry) {
        if (level != null) entries.add(entry);
    }

    List<T> consume(Object level) {
        if (this.level != level) {
            clear();
            return List.of();
        }
        var result = List.copyOf(entries);
        entries.clear();
        return result;
    }

    void clear() {
        entries.clear();
        level = null;
    }

    int size() {
        return entries.size();
    }
}
