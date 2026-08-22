package org.academy.api.common.ability.darkmatter;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Persistent 0..1 structural integrity of native dark-matter equipment. */
public record DarkmatterIntegrity(float value, float decayRemainder) {
    public static final DarkmatterIntegrity FULL = new DarkmatterIntegrity(1.0f, 0.0f);
    public static final DarkmatterIntegrity EMPTY = new DarkmatterIntegrity(0.0f, 0.0f);
    private static final Codec<DarkmatterIntegrity> CURRENT_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.FLOAT.fieldOf("value").forGetter(DarkmatterIntegrity::value),
                    Codec.FLOAT.optionalFieldOf("decay_remainder", 0.0f)
                            .forGetter(DarkmatterIntegrity::decayRemainder)
            ).apply(instance, DarkmatterIntegrity::new));
    /** Accepts the former scalar component and writes the new remainder-aware structure. */
    public static final Codec<DarkmatterIntegrity> CODEC = Codec.either(CURRENT_CODEC, Codec.FLOAT)
            .xmap(value -> value.map(integrity -> integrity, DarkmatterIntegrity::new), Either::left);
    public static final StreamCodec<io.netty.buffer.ByteBuf, DarkmatterIntegrity> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, DarkmatterIntegrity::value,
                    ByteBufCodecs.FLOAT, DarkmatterIntegrity::decayRemainder,
                    DarkmatterIntegrity::new);

    public DarkmatterIntegrity(float value) {
        this(value, 0.0f);
    }

    public DarkmatterIntegrity {
        value = Float.isFinite(value) ? Math.clamp(value, 0.0f, 1.0f) : 0.0f;
        decayRemainder = Float.isFinite(decayRemainder)
                ? Math.clamp(decayRemainder, -0.001f, 0.001f) : 0.0f;
        if (value <= 0.0f || value >= 1.0f) decayRemainder = 0.0f;
    }

    public boolean operational() {
        return value > 1.0e-5f;
    }
}
