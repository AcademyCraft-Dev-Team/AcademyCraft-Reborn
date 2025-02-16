package org.academy.api.client.network.packet;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.common.network.AcademyCraftFriendlyByteBufFactories;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;

import java.util.List;

public class C2SResponsePacket extends ServerboundCustomPayloadPacket {
    public C2SResponsePacket(String identifier, ResourceLocation key, List<Object> value) {
        super(AcademyCraftNetworkResourceLocations.C2S_RESPONSE, create(identifier, key, value));
    }

    private static FriendlyByteBuf create(String identifier, ResourceLocation key, List<Object> value) {
        FriendlyByteBuf friendlyByteBuf = PacketByteBufs.create();
        friendlyByteBuf.writeUtf(identifier);
        friendlyByteBuf.writeResourceLocation(key);
        return AcademyCraftFriendlyByteBufFactories.FRIENDLY_BYTE_BUF_FACTORY_MAP.get(identifier).create(friendlyByteBuf, value);
    }
}
