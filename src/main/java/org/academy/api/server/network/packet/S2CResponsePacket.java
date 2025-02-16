package org.academy.api.server.network.packet;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.common.network.AcademyCraftFriendlyByteBufFactories;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;

import java.util.List;

public class S2CResponsePacket extends ClientboundCustomPayloadPacket {
    public S2CResponsePacket(String identifier, ResourceLocation key, List<?> value) {
        super(AcademyCraftNetworkResourceLocations.S2C_RESPONSE, create(identifier, key, value));
    }

    private static FriendlyByteBuf create(String identifier, ResourceLocation key, List<?> value) {
        FriendlyByteBuf friendlyByteBuf = PacketByteBufs.create();
        friendlyByteBuf.writeUtf(identifier);
        friendlyByteBuf.writeResourceLocation(key);
        return AcademyCraftFriendlyByteBufFactories.FRIENDLY_BYTE_BUF_FACTORY_MAP.get(identifier).create(friendlyByteBuf, value);
    }
}