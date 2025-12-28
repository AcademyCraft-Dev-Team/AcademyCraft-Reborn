package org.academy.api.common.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;

public class GsonUtil {

    public static boolean isValidField(JsonObject jsonObject, Field[] fields) {
        if (jsonObject == null) return false;

        for (Field field : fields) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                continue;
            }

            String fieldName = getSerializedName(field);
            if (!jsonObject.has(fieldName)) {
                return false;
            }

            JsonElement element = jsonObject.get(fieldName);
            if (!isTypeCompatible(field.getType(), element)) {
                return false;
            }
        }
        return true;
    }

    private static String getSerializedName(Field field) {
        SerializedName annotation = field.getAnnotation(SerializedName.class);
        return (annotation != null) ? annotation.value() : field.getName();
    }

    private static boolean isTypeCompatible(Class<?> type, JsonElement element) {
        if (element == null || element.isJsonNull()) return true;

        if (Iterable.class.isAssignableFrom(type) || type.isArray()) {
            return element.isJsonArray();
        } else if (Map.class.isAssignableFrom(type) || !isPrimitiveOrWrapper(type)) {
            return element.isJsonObject();
        } else {
            return element.isJsonPrimitive();
        }
    }

    private static boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() ||
                type == String.class ||
                type == Integer.class ||
                type == Long.class ||
                type == Double.class ||
                type == Float.class ||
                type == Boolean.class;
    }
}