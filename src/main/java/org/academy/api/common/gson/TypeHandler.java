package org.academy.api.common.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import org.jetbrains.annotations.NotNull;

public interface TypeHandler<T> {
    @NotNull
    default TypeAdapter<T> getAdapter(@NotNull Gson gson) {
        return gson.getAdapter(getTypeClass());
    }

    @NotNull
    T getDefault();

    @NotNull
    Class<T> getTypeClass();
}