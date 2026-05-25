package org.academy.api.common.arc.data;

import org.academy.api.common.util.UncheckedUtil;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PathData {
    private final List<PathFrame> frames;
    private final Map<PropertyType<?>, List<?>> properties = new HashMap<>();

    public PathData(List<PathFrame> frames) {
        this.frames = frames;
    }

    public List<PathFrame> getFrames() {
        return frames;
    }

    public <T> @Nullable List<T> getProperty(PropertyType<T> type) {
        return UncheckedUtil.uncheckedCast(properties.get(type));
    }

    public <T> void setProperty(PropertyType<T> type, List<T> values) {
        if (values.size() != frames.size()) {
            throw new IllegalArgumentException("Property list size must match frame list size.");
        }
        properties.put(type, values);
    }

    public boolean hasProperty(PropertyType<?> type) {
        return properties.containsKey(type);
    }
}