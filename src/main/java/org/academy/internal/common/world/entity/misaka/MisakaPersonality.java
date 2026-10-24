package org.academy.internal.common.world.entity.misaka;

import com.mojang.serialization.Codec;
import net.minecraft.util.RandomSource;

import java.util.Locale;

public enum MisakaPersonality {
    TIMID,
    LIVELY,
    BRAVE,
    COLD;

    public static final Codec<MisakaPersonality> CODEC = Codec.STRING.xmap(
            MisakaPersonality::byName,
            MisakaPersonality::getSerializedName
    );

    public static MisakaPersonality random(RandomSource random) {
        return values()[random.nextInt(values().length)];
    }

    public static MisakaPersonality fromOrdinal(int ordinal) {
        var values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return TIMID;
        }
        return values[ordinal];
    }

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static MisakaPersonality byName(String value) {
        for (var personality : values()) {
            if (personality.getSerializedName().equals(value)) {
                return personality;
            }
        }
        throw new IllegalArgumentException("Unknown Misaka personality: " + value);
    }
}
