package org.academy.internal.common.network.syncher;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.academy.AcademyCraft;
import org.academy.api.common.arc.ArcPath;

import java.util.List;

public final class EntityDataSerializers {
    public static final DeferredRegister<EntityDataSerializer<?>> ENTITY_DATA_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, AcademyCraft.MOD_ID);
    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<List<ArcPath>>> ARC_PATH =
            ENTITY_DATA_SERIALIZERS.register("arc_path", () -> EntityDataSerializer.forValueType(ArcPath.CODEC.apply(ByteBufCodecs.list())));

    private EntityDataSerializers() {
    }
}