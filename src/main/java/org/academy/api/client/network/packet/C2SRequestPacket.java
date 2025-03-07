package org.academy.api.client.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.FriendlyByteBufFactories;

import java.util.List;

public class C2SRequestPacket extends ServerboundCustomPayloadPacket {
    // 提供最基本的数据写入
    public C2SRequestPacket(ResourceLocation key, Object... objects) {
        super(AcademyCraftNetworkResourceLocations.C2S_REQUEST, createByteBuf(key, objects));
    }

    // 你需要自己写入key
    public C2SRequestPacket(FriendlyByteBuf friendlyByteBuf) {
        super(AcademyCraftNetworkResourceLocations.C2S_REQUEST, friendlyByteBuf);
    }

    private static FriendlyByteBuf createByteBuf(ResourceLocation key, Object... objects) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeResourceLocation(key);
        for (int i = 0; i < objects.length; i += 2) {
            String identifier = (String) objects[i];
            Object value = objects[i + 1];
            FriendlyByteBufFactories.FRIENDLY_BYTE_BUF_FACTORY_MAP.get(identifier).create(buf, List.of(value));
        }
        return buf;
    }
}