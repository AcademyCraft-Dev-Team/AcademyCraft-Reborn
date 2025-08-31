package org.academy.internal.common.attachment;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

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
                    .build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> ACTIVATED_STORM_WING = REGISTER.register("activated_storm_wing",
            () -> AttachmentType
                    .builder(DEFAULT_FALSE)
                    .serialize(Codec.BOOL.fieldOf("activated_storm_wing"))
                    .sync(ByteBufCodecs.BOOL)
                    .build());
}