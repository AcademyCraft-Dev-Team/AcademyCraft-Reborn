package org.academy.internal.server.world.level.storage;

import com.google.gson.*;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.internal.common.skilldata.CommonSkillData;
import org.academy.internal.common.skilldata.SkillData;
import org.academy.internal.common.skilldata.CodecSkillData;
import org.academy.internal.common.skilldata.UnknownSkillData;
import org.academy.api.common.ability.data.SkillStateType;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class SkillDataSerializer<T extends SkillData> implements JsonSerializer<T>, JsonDeserializer<T> {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    private static final String LEGACY_TYPE_KEY = "_type";
    private static final String LEGACY_REFLECTION_FILTER_DATA_SUFFIX =
            ".accelerator.skills.ReflectionFilter$Data";

    private static final Map<Identifier, SkillStateType<?>> STATE_TYPES = new HashMap<>();
    private static final Map<Identifier, Class<? extends SkillData>> TYPE_MAP = new HashMap<>();

    static {
        TYPE_MAP.put(CommonSkillData.ID, CommonSkillData.class);
    }

    public static void registerType(Identifier id, Class<? extends SkillData> clazz) {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(clazz, "clazz");
        var previous = TYPE_MAP.putIfAbsent(id, clazz);
        if (previous != null && previous != clazz) {
            throw new IllegalStateException("Conflicting skill state type " + id + ": "
                    + previous.getName() + " and " + clazz.getName());
        }
    }

    public static void registerStateType(SkillStateType<?> type) {
        var previous = STATE_TYPES.get(type.id());
        if (previous != null && previous != type) throw new IllegalStateException("Conflicting skill state descriptor " + type.id());
        registerType(type.id(), CodecSkillData.class);
        STATE_TYPES.put(type.id(), type);
    }

    @Override
    public JsonElement serialize(T data, Type typeOfSrc, JsonSerializationContext context) {
        if (data instanceof UnknownSkillData unknown) return unknown.raw();
        var json = data instanceof CodecSkillData<?> typed ? typed.encode() : context.serialize(data).getAsJsonObject();
        json.addProperty("enabled", data.isEnabled());
        json.remove("exp");
        json.remove("maxExp");
        json.remove("level");
        json.addProperty("proficiency", data.getProficiency());
        json.addProperty("progressVersion", 1);
        json.addProperty("type", data.getType().toString());
        return json;
    }

    @Override
    public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        var jsonObject = json.getAsJsonObject();
        Class<? extends SkillData> targetClass = CommonSkillData.class;

        SkillData modern = null;
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
                var stateType = STATE_TYPES.get(typeId);
                if (stateType != null) {
                    try {
                        modern = CodecSkillData.decode(stateType, jsonObject);
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Preserving unsupported SkillData {}: {}", typeId, exception.getMessage());
                        return (T) new UnknownSkillData(jsonObject, typeId);
                    }
                } else if (registeredClass != null) {
                    targetClass = registeredClass;
                } else {
                    LOGGER.warn("Preserving missing SkillData type '{}'.", typeId);
                    return (T) new UnknownSkillData(jsonObject, typeId);
                }

            } catch (Exception e) {
                LOGGER.error("Preserving invalid SkillData type identifier: {}", typeStr);
                return (T) new UnknownSkillData(jsonObject, CommonSkillData.ID);
            }
        }
        var data = modern == null ? context.<T>deserialize(json, targetClass) : (T) modern;
        if (modern != null && jsonObject.has("enabled")) data.setEnabled(jsonObject.get("enabled").getAsBoolean());
        if (jsonObject.has("proficiency")) {
            data.setProficiency(jsonObject.get("proficiency").getAsFloat());
        } else {
            var exp = jsonObject.has("exp") ? jsonObject.get("exp").getAsFloat() : 0.0f;
            var maxExp = jsonObject.has("maxExp") ? jsonObject.get("maxExp").getAsInt() : 1000;
            var level = jsonObject.has("level") ? jsonObject.get("level").getAsInt() : 0;
            data.markLegacyProgress(exp, maxExp, level);
        }
        return data;
    }
}
