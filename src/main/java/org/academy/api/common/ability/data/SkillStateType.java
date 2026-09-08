package org.academy.api.common.ability.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import java.util.Objects;
import java.util.function.Supplier;

/** Codec for addon-owned state only; enabled/proficiency remain separate Academy fields. */
public record SkillStateType<T>(Identifier id, Codec<T> codec, Supplier<T> defaultValue,
                                int version, Migration migration) {
    public SkillStateType {
        Objects.requireNonNull(id);
        Objects.requireNonNull(codec);
        Objects.requireNonNull(defaultValue);
        Objects.requireNonNull(migration);
        if (version < 1) throw new IllegalArgumentException("State version must be positive");
    }
    public static <T> SkillStateType<T> of(Identifier id, Codec<T> codec, Supplier<T> defaults) {
        return new SkillStateType<>(id, codec, defaults, 1, (version, value) -> {
            if (version != 1) throw new IllegalArgumentException("Unsupported skill state version " + version);
            return value;
        });
    }
    @FunctionalInterface
    public interface Migration {
        /** Returns data in the current version without mutating the supplied raw value. */
        JsonElement migrate(int storedVersion, JsonElement value);
    }
}
