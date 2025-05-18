package org.academy.api.common.ability;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.network.*;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.vanilla.EnvType;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

@PacketTarget(EnvType.CLIENT)
public class PlayerSyncPacket extends IPacket<ClientPacketListener> {
    public boolean levelChanged;
    public int level;
    public boolean maxComputingPowerChanged;
    public float maxComputingPower;
    public boolean currentComputingPowerChanged;
    public float currentComputingPower;
    public boolean abilityCategoryChanged;
    public String abilityCategory;
    public boolean skillsChanged;
    public HashSet<String> skills;

    @ReceiverConstructor
    public PlayerSyncPacket() {
    }

    @SenderConstructor
    public PlayerSyncPacket(boolean levelChanged,
                            int level,
                            boolean currentComputingPowerChanged,
                            float currentComputingPower,
                            boolean maxComputingPowerChanged,
                            float maxComputingPower,
                            boolean abilityCategoryChanged,
                            String abilityCategory,
                            boolean skillsChanged,
                            HashSet<String> skills) {
        this.levelChanged = levelChanged;
        this.level = level;
        this.currentComputingPowerChanged = currentComputingPowerChanged;
        this.currentComputingPower = currentComputingPower;
        this.maxComputingPowerChanged = maxComputingPowerChanged;
        this.maxComputingPower = maxComputingPower;
        this.abilityCategoryChanged = abilityCategoryChanged;
        this.abilityCategory = abilityCategory;
        this.skillsChanged = skillsChanged;
        this.skills = skills;
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        levelChanged = buf.readBoolean();
        if (levelChanged) {
            level = buf.readVarInt();
        }
        maxComputingPowerChanged = buf.readBoolean();
        if (maxComputingPowerChanged) {
            maxComputingPower = buf.readFloat();
        }
        currentComputingPowerChanged = buf.readBoolean();
        if (currentComputingPowerChanged) {
            currentComputingPower = buf.readFloat();
        }
        abilityCategoryChanged = buf.readBoolean();
        if (abilityCategoryChanged) {
            abilityCategory = buf.readUtf();
        }
        skillsChanged = buf.readBoolean();
        if (skillsChanged) {
            FriendlyByteBufDeserializer<HashSet<String>> setFriendlyByteBufDeserializer =
                    FriendlyByteBufDeserializers.getCollectionFriendlyByteBufDeserializer(String.class, HashSet::new);
            skills = setFriendlyByteBufDeserializer.deserialize(buf);
        }
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeBoolean(levelChanged);
        if (levelChanged) {
            buf.writeVarInt(level);
        }
        buf.writeBoolean(maxComputingPowerChanged);
        if (maxComputingPowerChanged) {
            buf.writeFloat(maxComputingPower);
        }
        buf.writeBoolean(currentComputingPowerChanged);
        if (currentComputingPowerChanged) {
            buf.writeFloat(currentComputingPower);
        }
        buf.writeBoolean(abilityCategoryChanged);
        if (abilityCategoryChanged) {
            buf.writeUtf(abilityCategory);
        }
        buf.writeBoolean(skillsChanged);
        if (skillsChanged) {
            FriendlyByteBufSerializer<HashSet<String>> setFriendlyByteBufSerializer =
                    FriendlyByteBufSerializers.getCollectionFriendlyByteBufSerializer(String.class);
            setFriendlyByteBufSerializer.serialize(buf, skills);
        }
    }
}