package org.academy.api.server.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.jetbrains.annotations.NotNull;

public interface IServerConfigActions<T> {
    @NotNull
    T deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson);

    @NotNull
    JsonElement serialize(@NotNull T configInstance, @NotNull Gson gson);

    @NotNull
    T getDefaultConfig();

    @NotNull
    Class<T> getConfigClass();
}