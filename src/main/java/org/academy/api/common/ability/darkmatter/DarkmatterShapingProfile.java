package org.academy.api.common.ability.darkmatter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable, server-validated blueprint embedded in every shaped dark-matter item. */
public record DarkmatterShapingProfile(
        int abilityLevel,
        int alphaPoints,
        int betaPoints,
        Map<String, Integer> modifiers
) {
    public static final DarkmatterShapingProfile DEFAULT = balanced(1, Map.of());

    public static final Codec<DarkmatterShapingProfile> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("ability_level").forGetter(DarkmatterShapingProfile::abilityLevel),
                    Codec.INT.fieldOf("alpha_points").forGetter(DarkmatterShapingProfile::alphaPoints),
                    Codec.INT.fieldOf("beta_points").forGetter(DarkmatterShapingProfile::betaPoints),
                    Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("modifiers")
                            .forGetter(DarkmatterShapingProfile::modifiers)
            ).apply(instance, DarkmatterShapingProfile::new));

    public static final StreamCodec<ByteBuf, DarkmatterShapingProfile> STREAM_CODEC = StreamCodec.of(
            (buffer, profile) -> {
                ByteBufCodecs.VAR_INT.encode(buffer, profile.abilityLevel());
                ByteBufCodecs.VAR_INT.encode(buffer, profile.alphaPoints());
                ByteBufCodecs.VAR_INT.encode(buffer, profile.betaPoints());
                ByteBufCodecs.map(LinkedHashMap::new,
                        ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT)
                        .encode(buffer, new LinkedHashMap<>(profile.modifiers()));
            },
            buffer -> new DarkmatterShapingProfile(
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.map(LinkedHashMap::new,
                            ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT).decode(buffer)
            )
    );

    public DarkmatterShapingProfile {
        abilityLevel = Math.clamp(abilityLevel, 1, 5);
        var total = 50 * abilityLevel;
        alphaPoints = Math.clamp(alphaPoints, 0, total);
        betaPoints = Math.clamp(betaPoints, 0, total);
        if (alphaPoints + betaPoints != total) betaPoints = total - alphaPoints;
        var copy = new LinkedHashMap<String, Integer>();
        if (modifiers != null) modifiers.forEach((id, level) -> {
            if (id != null && !id.isBlank() && level != null && level > 0) {
                copy.put(id, level);
            }
        });
        modifiers = Map.copyOf(copy);
    }

    public static DarkmatterShapingProfile balanced(int level, Map<String, Integer> modifiers) {
        var safeLevel = Math.clamp(level, 1, 5);
        var total = safeLevel * 50;
        return new DarkmatterShapingProfile(safeLevel, total / 2,
                total - total / 2, modifiers);
    }

    public float alphaPower() {
        return alphaPoints / 50.0f;
    }

    public float betaPower() {
        return betaPoints / 50.0f;
    }

    public int modifierLevel(String id) {
        return modifiers.getOrDefault(id, 0);
    }
}
