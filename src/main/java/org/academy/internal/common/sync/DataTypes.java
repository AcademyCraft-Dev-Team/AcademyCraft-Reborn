package org.academy.internal.common.sync;

import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;
import org.academy.api.common.registries.Registries;
import org.academy.api.common.sync.DataType;

public final class DataTypes {
    public static final DeferredRegister<DataType<?>> SYNC_DATA_TYPES =
            DeferredRegister.create(Registries.Keys.DATA_TYPES, AcademyCraft.MOD_ID);
    public static final DeferredHolder<DataType<?>, DataType<Boolean>> BOOL =
            SYNC_DATA_TYPES.register("bool", () -> new DataType<>(ByteBufCodecs.BOOL));

    private DataTypes() {
    }
}