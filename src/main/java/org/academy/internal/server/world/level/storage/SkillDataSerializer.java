package org.academy.internal.server.world.level.storage;

import com.google.gson.*;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.registries.Registries;
import org.academy.internal.common.skilldata.CommonSkillData;
import org.academy.internal.common.skilldata.SkillData;

import java.lang.reflect.Type;

public class SkillDataSerializer<T extends SkillData> implements JsonSerializer<T>, JsonDeserializer<T> {
    @Override
    public JsonElement serialize(T data, Type typeOfSrc, JsonSerializationContext context) {
        var json = context.serialize(data).getAsJsonObject();
        json.addProperty("type", data.getType().toString());
        return json;
    }

    @Override
    public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        var jsonObject = json.getAsJsonObject();
        var typeId = CommonSkillData.ID;

        if (jsonObject.has("type")) {
            try {
                typeId = Identifier.parse(jsonObject.get("type").getAsString());
            } catch (Exception ignored) {
                AcademyCraft.LOGGER.error("Failed to parse skill data type from JSON: {}", jsonObject.get("type").getAsString());
            }
        }

        var dataTypeOptional = Registries.SKILL_DATA_TYPES.get(typeId);
        var dataType = dataTypeOptional.<SkillDataType<?>>map(Holder.Reference::value).orElse(null);

        if (dataType == null) {
            var defaultTypeOptional = Registries.SKILL_DATA_TYPES.get(CommonSkillData.ID);
            dataType = defaultTypeOptional.get().value();
        }

        return context.deserialize(json, dataType.clazz());
    }
}