package org.academy.api.common.ability;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.academy.api.common.network.FBBDeserializers;
import org.academy.api.common.network.FBBSerializers;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.PacketType;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.network.PacketTypes;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

@PacketTarget(ThreadType.CLIENT)
public class PlayerSyncPacket extends Packet<ClientGamePacketListener> {
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

    public PlayerSyncPacket(ClientGamePacketListener listener) {
        super(listener);
    }

    public PlayerSyncPacket(boolean newLevelChanged,
                            int newLevel,
                            boolean newCurrentComputingPowerChanged,
                            float newCurrentComputingPower,
                            boolean newMaxComputingPowerChanged,
                            float newMaxComputingPower,
                            boolean newAbilityCategoryChanged,
                            String newAbilityCategory,
                            boolean newSkillsChanged,
                            HashSet<String> newSkills) {
        super(null);
        levelChanged = newLevelChanged;
        level = newLevel;
        currentComputingPowerChanged = newCurrentComputingPowerChanged;
        currentComputingPower = newCurrentComputingPower;
        maxComputingPowerChanged = newMaxComputingPowerChanged;
        maxComputingPower = newMaxComputingPower;
        abilityCategoryChanged = newAbilityCategoryChanged;
        abilityCategory = newAbilityCategory;
        skillsChanged = newSkillsChanged;
        skills = newSkills;
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
            var setFriendlyByteBufDeserializer =
                    FBBDeserializers.getCollectionFriendlyByteBufDeserializer(String.class, HashSet::new);
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
            var setFriendlyByteBufSerializer =
                    FBBSerializers.getCollectionFriendlyByteBufSerializer(String.class);
            setFriendlyByteBufSerializer.serialize(buf, skills);
        }
    }

    @Override
    public @NotNull PacketType<ClientGamePacketListener, ? extends Packet<ClientGamePacketListener>> getPacketType() {
        return PacketTypes.PLAYER_SYNC.get();
    }
}