package org.academy.api.common.util;

import com.google.gson.JsonObject;

import java.lang.reflect.Field;

public class GsonUtil {
    public static boolean isValidField(JsonObject jsonObject, Field[] fields) {
        for (var field : fields) {
            var fieldName = field.getName();

            if (!jsonObject.has(fieldName)) {
                return false;
            }

            var element = jsonObject.get(fieldName);
            if (!element.isJsonObject()) {
                return false;
            }

            var nestedObject = element.getAsJsonObject();

            for (var nestedField : field.getType().getDeclaredFields()) {
                var nestedFieldName = nestedField.getName();

                if (!nestedObject.has(nestedFieldName)) {
                    return false;
                }
            }
        }
        return true;
    }
}