package org.academy.api.common.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

public interface FriendlyByteBufFactory {
    FriendlyByteBuf create(FriendlyByteBuf friendlyByteBuf, List<?> value);
}