package org.academy.internal.server.world.level.storage;

import com.google.gson.*;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.internal.common.skilldata.CommonSkillData;
import org.academy.internal.common.skilldata.SkillData;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class SkillDataSerializer<T extends SkillData> implements JsonSerializer<T>, JsonDeserializer<T> {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    private static final String LEGACY_TYPE_KEY = "_type";
    private static final String LEGACY_REFLECTION_FILTER_DATA_SUFFIX =
            ".accelerator.skills.ReflectionFilter$Data";

    private static final Map<Identifier, Class<? extends SkillData>> TYPE_MAP = new HashMap<>();

    static {
        TYPE_MAP.put(CommonSkillData.ID, CommonSkillData.class);
    }

    public static void registerType(Identifier id, Class<? extends SkillData> clazz) {
        TYPE_MAP.put(id, clazz);
    }

    @Override
    public JsonElement serialize(T data, Type typeOfSrc, JsonSerializationContext context) {
        var json = context.serialize(data).getAsJsonObject();
        json.addProperty("type", data.getType().toString());
        return json;
    }

    @Override
    public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        var jsonObject = json.getAsJsonObject();
        Class<? extends SkillData> targetClass = CommonSkillData.class;

        String typeStr = null;
        if (jsonObject.has("type")) {
            typeStr = jsonObject.get("type").getAsString();
        } else if (jsonObject.has(LEGACY_TYPE_KEY)) {
            var legacyType = jsonObject.get(LEGACY_TYPE_KEY).getAsString();
            if (legacyType.endsWith(LEGACY_REFLECTION_FILTER_DATA_SUFFIX)) {
                typeStr = AcademyCraft.academy("reflection_filter").toString();
            }
        }

        if (typeStr != null) {
            try {
                var typeId = Identifier.parse(typeStr);
                var registeredClass = TYPE_MAP.get(typeId);
                if (registeredClass != null) {
                    targetClass = registeredClass;
                } else {
                    LOGGER.warn("Unknown SkillData type '{}', falling back to CommonSkillData.", typeId);
                }

            } catch (Exception e) {
                LOGGER.error("Failed to parse SkillData type identifier: {}", typeStr);
            }
        }
        return context.deserialize(json, targetClass);
    }
}
