package org.academy.api.common.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.lang.reflect.Field;

public class GsonUtil {
    public static boolean isValidField(JsonObject jsonObject, Field[] fields) {
        for (Field field : fields) {
            String fieldName = field.getName();

            if (!jsonObject.has(fieldName)) {
                return false;
            }

            JsonElement element = jsonObject.get(fieldName);
            if (!element.isJsonObject()) {
                return false;
            }

            JsonObject nestedObject = element.getAsJsonObject();

            for (Field nestedField : field.getType().getDeclaredFields()) {
                String nestedFieldName = nestedField.getName();

                if (!nestedObject.has(nestedFieldName)) {
                    return false;
                }
            }
        }
        return true;
    }
}