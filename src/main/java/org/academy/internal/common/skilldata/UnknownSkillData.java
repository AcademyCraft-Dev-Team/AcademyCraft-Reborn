package org.academy.internal.common.skilldata;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

/** Retains missing/future addon data verbatim until its implementation becomes available again. */
public final class UnknownSkillData extends CommonSkillData {
    private final JsonObject raw;
    private final Identifier type;
    public UnknownSkillData(JsonObject raw, Identifier type) {
        this.raw = raw.deepCopy();
        this.type = type;
    }
    @Override public Identifier getType() { return type; }
    @Override public boolean isEnabled() { return false; }
    public JsonObject raw() { return raw.deepCopy(); }
}
