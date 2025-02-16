package org.academy.api.common.network;

import net.minecraft.network.FriendlyByteBuf;

public interface FriendlyByteBufParser {
    void parse(FriendlyByteBuf friendlyByteBuf, Response response);
}