package org.academy.internal.common.skilldata;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.data.SkillStateType;
import java.util.Objects;

/** Framework-owned envelope keeps arbitrary addon state out of the activation/proficiency fields. */
public final class CodecSkillData<T> extends SkillData {
    private final transient SkillStateType<T> type;
    private T value;

    public CodecSkillData(SkillStateType<T> type) {
        this(type, Objects.requireNonNull(type.defaultValue().get()));
    }
    private CodecSkillData(SkillStateType<T> type, T value) {
        this.type = type;
        value(value);
    }
    @Override public Identifier getType() { return type.id(); }
    public SkillStateType<T> stateType() { return type; }
    public T value() { return value; }
    public void value(T value) {
        Objects.requireNonNull(value);
        type.codec().encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        this.value = value;
    }
    public JsonObject encode() {
        var result = new JsonObject();
        result.addProperty("stateVersion", type.version());
        result.add("state", type.codec().encodeStart(JsonOps.INSTANCE, value).getOrThrow());
        return result;
    }
    public static <T> CodecSkillData<T> decode(SkillStateType<T> type, JsonObject raw) {
        var version = raw.has("stateVersion") ? raw.get("stateVersion").getAsInt() : 1;
        if (version > type.version() || version < 1) throw new IllegalArgumentException("Unsupported state version " + version);
        var value = Objects.requireNonNull(raw.get("state"), "Missing addon state").deepCopy();
        if (version != type.version()) value = type.migration().migrate(version, value);
        return new CodecSkillData<>(type, type.codec().parse(JsonOps.INSTANCE, value).getOrThrow());
    }
}
