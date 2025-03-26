package org.academy.api.server.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.FriendlyByteBufFactories;

import java.util.List;

public class S2CResponsePacket extends ClientboundCustomPayloadPacket {
    public S2CResponsePacket(String identifier, ResourceLocation key, List<?> value) {
        super(AcademyCraftNetworkResourceLocations.S2C_RESPONSE, create(identifier, key, value));
    }

    private static FriendlyByteBuf create(String identifier, ResourceLocation key, List<?> value) {
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        friendlyByteBuf.writeUtf(identifier);
        friendlyByteBuf.writeResourceLocation(key);
        return FriendlyByteBufFactories.FRIENDLY_BYTE_BUF_FACTORY_MAP.get(identifier).create(friendlyByteBuf, value);
    }
}