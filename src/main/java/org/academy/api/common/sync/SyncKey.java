package org.academy.api.common.sync;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.academy.api.common.registries.Registries;

public record SyncKey(Identifier id) {
    public static final StreamCodec<ByteBuf, SyncKey> CODEC =
            ByteBufCodecs.idMapper(Registries.SYNC_KEYS);
}