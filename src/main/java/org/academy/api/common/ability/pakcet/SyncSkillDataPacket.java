package org.academy.api.common.ability.pakcet;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.skilldata.SkillData;
import org.academy.internal.server.world.level.storage.SkillDataSerializer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.lang.reflect.Type;
import java.util.Map;

@PacketTarget(ThreadType.CLIENT)
public class SyncSkillDataPacket extends Packet<ClientPacketListener, SyncSkillDataPacket> {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(SkillData.class, new SkillDataSerializer<>())
            .enableComplexMapKeySerialization()
            .create();

    private static final Type MAP_TYPE = new TypeToken<Map<String, SkillData>>(){}.getType();

    public static final StreamCodec<ByteBuf, SyncSkillDataPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {
                var json = GSON.toJson(packet.skillDataMap, MAP_TYPE);
                ByteBufCodecs.STRING_UTF8.encode(buf, json);
            },
            (buf) -> {
                var json = ByteBufCodecs.STRING_UTF8.decode(buf);
                Map<String, SkillData> map = GSON.fromJson(json, MAP_TYPE);
                return new SyncSkillDataPacket(map);
            }
    );
    private final Map<String, SkillData> skillDataMap;

    public SyncSkillDataPacket(Map<String, SkillData> skillDataMap) {
        this.skillDataMap = skillDataMap;
    }

    public Map<String, SkillData> getSkillDataMap() {
        return skillDataMap;
    }

    @Override
    public PacketType<ClientPacketListener, SyncSkillDataPacket> getPacketType() {
        return PacketTypes.SYNC_SKILL_DATA.get();
    }
}
