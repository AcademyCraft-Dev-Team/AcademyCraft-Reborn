package org.academy.api.client.network.packet;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;

public class C2SRequestPacket extends ServerboundCustomPayloadPacket {
    public C2SRequestPacket(ResourceLocation key) {
        super(AcademyCraftNetworkResourceLocations.C2S_REQUEST, PacketByteBufs.create().writeResourceLocation(key));
    }
}