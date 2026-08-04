package org.academy.internal.common.attachment;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.academy.internal.client.renderer.entity.layers.quantum.QuantumData;
import org.academy.internal.common.ability.electromaster.skills.lv5.Railgun;

import java.util.function.Supplier;

import static org.academy.AcademyCraft.MODID;

public final class AttachmentTypes {
    public static final DeferredRegister<AttachmentType<?>> REGISTER = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);
    public static final Supplier<Boolean> DEFAULT_FALSE = () -> false;
    public static final Supplier<Boolean> DEFAULT_TRUE = () -> true;
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> HAS_DATA_TERMINAL = REGISTER.register("has_data_terminal",
            () -> AttachmentType
                    .builder(DEFAULT_FALSE)
                    .serialize(Codec.BOOL.fieldOf("has_data_terminal"))
                    .sync(ByteBufCodecs.BOOL)
                    .build()
    );
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> FRIENDLY_FIRE_ENABLED = REGISTER.register(
            "friendly_fire_enabled",
            () -> AttachmentType
                    .builder(DEFAULT_TRUE)
                    .serialize(Codec.BOOL.fieldOf("enabled"))
                    .copyOnDeath()
                    .sync(ByteBufCodecs.BOOL)
                    .build()
    );
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> DESTROY_BLOCKS_ENABLED = REGISTER.register(
            "destroy_blocks_enabled",
            () -> AttachmentType
                    .builder(DEFAULT_TRUE)
                    .serialize(Codec.BOOL.fieldOf("enabled"))
                    .copyOnDeath()
                    .sync(ByteBufCodecs.BOOL)
                    .build()
    );
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> ACTIVATED_STORM_WING = REGISTER.register("activated_storm_wing",
            () -> AttachmentType
                    .builder(DEFAULT_FALSE)
                    .sync(ByteBufCodecs.BOOL)
                    .build()
    );
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> ACTIVATED_BLACK_WING = REGISTER.register("activated_black_wing",
            () -> AttachmentType.builder(DEFAULT_FALSE).sync(ByteBufCodecs.BOOL).build()
    );
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> ACTIVATED_WHITE_WING = REGISTER.register("activated_white_wing",
            () -> AttachmentType.builder(DEFAULT_FALSE).sync(ByteBufCodecs.BOOL).build()
    );
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> ACTIVATED_PLATINUM_WING = REGISTER.register("activated_platinum_wing",
            () -> AttachmentType.builder(DEFAULT_FALSE).sync(ByteBufCodecs.BOOL).build()
    );
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> CROSSING_THE_ABYSS_ACTIVE = REGISTER.register("crossing_the_abyss_active",
            () -> AttachmentType.builder(DEFAULT_FALSE).sync(ByteBufCodecs.BOOL).build()
    );
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> MAGNET_MANIPULATION_ACTIVE = REGISTER.register(
            "magnet_manipulation_active",
            () -> AttachmentType.builder(DEFAULT_FALSE).sync(ByteBufCodecs.BOOL).build()
    );
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> KINETIC_BLOCK_BREAK_ENABLED = REGISTER.register(
            "kinetic_shockwave_enabled",
            () -> AttachmentType
                    .builder(DEFAULT_FALSE)
                    .serialize(Codec.BOOL.fieldOf("enabled"))
                    .sync(ByteBufCodecs.BOOL)
                    .build()
    );
    @Deprecated(forRemoval = false)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> KINETIC_SHOCKWAVE_ENABLED =
            KINETIC_BLOCK_BREAK_ENABLED;
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> KINETIC_IMPACT_LEVEL = REGISTER.register(
            "kinetic_impact_level",
            () -> AttachmentType
                    .builder(() -> 1)
                    .serialize(Codec.INT.fieldOf("level"))
                    .sync(ByteBufCodecs.VAR_INT)
                    .build()
    );
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> BIOELECTRIC_OPERATION_ACTIVE_TICKS = REGISTER.register(
            "bioelectric_operation_active_ticks",
            () -> AttachmentType
                    .builder(() -> 0)
                    .serialize(Codec.INT.fieldOf("ticks"))
                    .build()
    );
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> LIGHT_SHIELD_ACTIVE = REGISTER.register(
            "light_shield_active",
            () -> AttachmentType
                    .builder(DEFAULT_FALSE)
                    .sync(ByteBufCodecs.BOOL)
                    .build()
    );
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> DARKMATTER_SIX_WINGS = REGISTER.register("darkmatter_six_wings",
            () -> AttachmentType
                    .builder(DEFAULT_FALSE)
                    .sync(ByteBufCodecs.BOOL)
                    .build()
    );
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Railgun.Data>> RAILGUN_DATA = REGISTER.register("railgun_data",
            () -> AttachmentType
                    .builder(Railgun.Data::getDefault)
                    .sync(Railgun.Data.CODEC)
                    .build()
    );
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<QuantumData>> QUANTUM_DATA = REGISTER.register(
            "quantum_data",
            () -> AttachmentType
                    .builder(QuantumData::getDefault)
                    .sync(QuantumData.CODEC)
                    .build()
    );
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> SP_REDUCTION_RATE = REGISTER.register(
            "sp_reduction_rate",
            () -> AttachmentType
                    .builder(() -> 1.0f)
                    .sync(ByteBufCodecs.FLOAT)
                    .build()
    );

    //KineticEnergyApplied的投射物增伤
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> PROJECTILE_EXTRA_DAMAGE = REGISTER.register(
            "projectile_extra_damage",
            () -> AttachmentType
                    .builder(() -> 0.0f)
                    .sync(ByteBufCodecs.FLOAT)
                    .build()
    );
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> VECTOR_REFLECTED_PROJECTILE = REGISTER.register(
            "vector_reflected_projectile",
            () -> AttachmentType
                    .builder(DEFAULT_FALSE)
                    .sync(ByteBufCodecs.BOOL)
                    .build()
    );
}
