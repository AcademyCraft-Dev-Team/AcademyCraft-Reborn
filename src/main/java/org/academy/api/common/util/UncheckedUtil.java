package org.academy.api.common.util;

public final class UncheckedUtil {
    @SuppressWarnings("unchecked")
    public static <T> Class<T> uncheckedCast(Class<?> clazz) {
        return (Class<T>) clazz;
    }

    @SuppressWarnings("unchecked")
    public static <T> T uncheckedCast(Object o) {
        return (T) o;
    }

    private UncheckedUtil() {
    }
}