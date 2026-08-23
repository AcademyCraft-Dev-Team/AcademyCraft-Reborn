package org.academy.api.common.ability.darkmatter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Server-validated physical parameters embedded in a shaped dark-matter block item. */
public record DarkmatterBlockProfile(float hardness, float explosionResistance, boolean gravity) {
    public static final float MIN_HARDNESS = 0.0f;
    public static final float MAX_HARDNESS = 50.0f;
    public static final float MIN_EXPLOSION_RESISTANCE = 0.0f;
    public static final float MAX_EXPLOSION_RESISTANCE = 1_200.0f;
    public static final DarkmatterBlockProfile DEFAULT = new DarkmatterBlockProfile(5.0f, 30.0f, false);

    public static final Codec<DarkmatterBlockProfile> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.FLOAT.fieldOf("hardness").forGetter(DarkmatterBlockProfile::hardness),
                    Codec.FLOAT.fieldOf("explosion_resistance")
                            .forGetter(DarkmatterBlockProfile::explosionResistance),
                    Codec.BOOL.fieldOf("gravity").forGetter(DarkmatterBlockProfile::gravity)
            ).apply(instance, DarkmatterBlockProfile::new));

    public static final StreamCodec<ByteBuf, DarkmatterBlockProfile> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, DarkmatterBlockProfile::hardness,
            ByteBufCodecs.FLOAT, DarkmatterBlockProfile::explosionResistance,
            ByteBufCodecs.BOOL, DarkmatterBlockProfile::gravity,
            DarkmatterBlockProfile::new);

    public DarkmatterBlockProfile {
        hardness = Math.clamp(Float.isFinite(hardness) ? hardness : DEFAULT.hardness,
                MIN_HARDNESS, MAX_HARDNESS);
        explosionResistance = Math.clamp(
                Float.isFinite(explosionResistance) ? explosionResistance
                        : DEFAULT.explosionResistance,
                MIN_EXPLOSION_RESISTANCE, MAX_EXPLOSION_RESISTANCE);
    }
}
