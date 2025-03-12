package org.academy.api.server.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;

public class S2CRequestPacket extends ClientboundCustomPayloadPacket {
    public S2CRequestPacket(ResourceLocation key) {
        super(AcademyCraftNetworkResourceLocations.S2C_REQUEST, new FriendlyByteBuf(Unpooled.buffer()).writeResourceLocation(key));
    }
}