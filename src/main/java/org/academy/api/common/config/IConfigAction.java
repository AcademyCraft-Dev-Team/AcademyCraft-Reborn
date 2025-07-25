package org.academy.api.common.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.jetbrains.annotations.NotNull;

public interface IConfigAction<T> {
    @NotNull
    T deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson);

    @NotNull
    JsonElement serialize(@NotNull T configInstance, @NotNull Gson gson);

    @SuppressWarnings("unchecked")
    default JsonElement serializeRaw(@NotNull Object configInstance, @NotNull Gson gson) {
        if (getConfigClass().isInstance(configInstance)) {
            return serialize((T) configInstance, gson);
        }
        throw new IllegalArgumentException("Cannot serialize instance of " + configInstance.getClass().getName() +
                " with config actions for " + getConfigClass().getName());
    }

    @NotNull
    T getDefaultConfig();

    @NotNull
    Class<T> getConfigClass();
}