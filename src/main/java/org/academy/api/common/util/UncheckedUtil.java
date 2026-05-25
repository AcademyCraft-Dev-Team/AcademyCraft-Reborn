package org.academy.api.common.util;

import org.jspecify.annotations.Nullable;

@SuppressWarnings("unchecked")
public final class UncheckedUtil {
    public static <T> T uncheckedCast(Object o) {
        return (T) o;
    }

    @Nullable
    public static <T> T uncheckedCastNullable(@Nullable Object o) {
        return (T) o;
    }

    private UncheckedUtil() {
    }
}