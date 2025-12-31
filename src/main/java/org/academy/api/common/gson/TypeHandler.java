package org.academy.api.common.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;

public interface TypeHandler<T> {
    default TypeAdapter<T> getAdapter(Gson gson) {
        return gson.getAdapter(getTypeClass());
    }

    T getDefault();

    Class<T> getTypeClass();
}