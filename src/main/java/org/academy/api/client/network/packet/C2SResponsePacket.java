package org.academy.api.client.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.FriendlyByteBufFactories;

import java.util.List;

public class C2SResponsePacket extends ServerboundCustomPayloadPacket {
    public C2SResponsePacket(String identifier, ResourceLocation key, List<Object> value) {
        super(AcademyCraftNetworkResourceLocations.C2S_RESPONSE, create(identifier, key, value));
    }

    private static FriendlyByteBuf create(String identifier, ResourceLocation key, List<Object> value) {
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        friendlyByteBuf.writeUtf(identifier);
        friendlyByteBuf.writeResourceLocation(key);
        return FriendlyByteBufFactories.FRIENDLY_BYTE_BUF_FACTORY_MAP.get(identifier).create(friendlyByteBuf, value);
    }
}
