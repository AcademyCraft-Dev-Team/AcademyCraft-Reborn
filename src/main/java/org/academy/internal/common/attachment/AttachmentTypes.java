package org.academy.internal.common.attachment;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.academy.internal.client.renderer.entity.layers.quantum.QuantumData;
import org.academy.internal.common.ability.electromaster.skills.Railgun;

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
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> ACTIVATED_STORM_WING = REGISTER.register("activated_storm_wing",
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
}