package org.academy.api.common.util;

import org.jspecify.annotations.Nullable;

@SuppressWarnings("unchecked")
public final class UncheckedUtil {
    @Nullable
    public static <T> Class<T> uncheckedCast(@Nullable Class<?> clazz) {
        return (Class<T>) clazz;
    }

    @Nullable
    public static <T> T uncheckedCast(@Nullable Object o) {
        return (T) o;
    }

    private UncheckedUtil() {
    }
}