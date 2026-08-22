package org.academy.internal.common.world.item;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static org.academy.AcademyCraft.MODID;
import org.academy.api.common.ability.darkmatter.DarkmatterIntegrity;
import org.academy.api.common.ability.darkmatter.DarkmatterShapingProfile;

public final class ItemDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY =
            DATA_COMPONENTS.registerComponentType("energy", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DarkmatterIntegrity>>
            DARKMATTER_INTEGRITY = DATA_COMPONENTS.registerComponentType(
            "darkmatter_integrity", builder -> builder
                    .persistent(DarkmatterIntegrity.CODEC)
                    .networkSynchronized(DarkmatterIntegrity.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DarkmatterShapingProfile>>
            DARKMATTER_SHAPING_PROFILE = DATA_COMPONENTS.registerComponentType(
            "darkmatter_shaping_profile", builder -> builder
                    .persistent(DarkmatterShapingProfile.CODEC)
                    .networkSynchronized(DarkmatterShapingProfile.STREAM_CODEC));

    private ItemDataComponents() {
    }
}
