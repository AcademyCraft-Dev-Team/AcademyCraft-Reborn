package org.academy.api.common.ability.pakcet;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.data.AbilityData;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

@PacketTarget(ThreadType.CLIENT)
public final class SyncAbilityDataPacket extends Packet<ClientPacketListener, SyncAbilityDataPacket> {
    private static final StreamCodec<ByteBuf, SkillOccupationSnapshot> OCCUPATION_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SkillOccupationSnapshot::skillId,
            ByteBufCodecs.VAR_INT, SkillOccupationSnapshot::stackCount,
            ByteBufCodecs.VAR_INT, SkillOccupationSnapshot::remainingIterationPoints,
            ByteBufCodecs.FLOAT, SkillOccupationSnapshot::occupiedCp,
            SkillOccupationSnapshot::new
    );
    private static final StreamCodec<ByteBuf, List<SkillOccupationSnapshot>> OCCUPATION_LIST_CODEC =
            OCCUPATION_CODEC.apply(ByteBufCodecs.list());
    public static final StreamCodec<ByteBuf, SyncAbilityDataPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {
                ByteBufCodecs.FLOAT.encode(buf, packet.cpData.getMaxCP());
                ByteBufCodecs.FLOAT.encode(buf, packet.cpData.getAvailableCP());
                ByteBufCodecs.VAR_INT.encode(buf, packet.cpData.getLevel().ordinal());
                ByteBufCodecs.VAR_INT.encode(buf, packet.cpData.getStatus().ordinal());
                ByteBufCodecs.VAR_INT.encode(buf, packet.cpData.getStateTimer());
                ByteBufCodecs.VAR_INT.encode(buf, packet.cpData.getCurrSP());
                ByteBufCodecs.VAR_INT.encode(buf, packet.cpData.getMaxSP());
                ByteBufCodecs.FLOAT.encode(buf, packet.cpData.getCurrMP());
                ByteBufCodecs.FLOAT.encode(buf, packet.cpData.getMaxMP());
                ByteBufCodecs.FLOAT.encode(buf, packet.cpData.getAbilityExp());
                ByteBufCodecs.FLOAT.encode(buf, packet.calculationIntensity);
                OCCUPATION_LIST_CODEC.encode(buf, packet.skillOccupations);
            },
            buf -> create(
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    OCCUPATION_LIST_CODEC.decode(buf)
            )
    );
    private final AbilityData cpData;
    private final float calculationIntensity;
    private final List<SkillOccupationSnapshot> skillOccupations;

    public SyncAbilityDataPacket(AbilityData cpData) {
        this(cpData, 1.0f, List.<SkillOccupationSnapshot>of());
    }

    public SyncAbilityDataPacket(AbilityData cpData, float calculationIntensity,
                                 List<AbilityData.CpOccupationData> occupations) {
        this(cpData, calculationIntensity, summarizeOccupations(occupations));
    }

    private SyncAbilityDataPacket(AbilityData cpData, float calculationIntensity,
                                  Collection<SkillOccupationSnapshot> skillOccupations) {
        this.cpData = cpData;
        this.calculationIntensity = Float.isFinite(calculationIntensity)
                ? Math.max(0.0f, calculationIntensity)
                : 1.0f;
        this.skillOccupations = List.copyOf(skillOccupations);
    }

    private static SyncAbilityDataPacket create(float maxCP, float availableCP, int levelOrd,
                                                int statusOrd, int stateTimer, int currSP,
                                                int maxSP, float currMP, float maxMP,
                                                float abilityExp, float calculationIntensity,
                                                List<SkillOccupationSnapshot> skillOccupations) {
        var data = AbilityData.builder()
                .maxCP(maxCP)
                .availableCP(availableCP)
                .level(AbilityLevel.values()[levelOrd])
                .status(AbilityData.Status.values()[statusOrd])
                .stateTimer(stateTimer)
                .currSP(currSP)
                .maxSP(maxSP)
                .currMP(currMP)
                .maxMP(maxMP)
                .abilityExp(abilityExp)
                .build();
        return new SyncAbilityDataPacket(data, calculationIntensity, skillOccupations);
    }

    public static List<SkillOccupationSnapshot> summarizeOccupations(
            List<AbilityData.CpOccupationData> occupations
    ) {
        if (occupations.isEmpty()) return List.of();
        var summaries = new TreeMap<String, SkillOccupationSnapshot>();
        for (var occupation : occupations) {
            if (occupation.isPermanent() || occupation.getSkillId().isBlank()) {
                continue;
            }
            var snapshot = new SkillOccupationSnapshot(
                    occupation.getSkillId(),
                    1,
                    occupation.getIterationTicks(),
                    occupation.getAmount()
            );
            summaries.merge(occupation.getSkillId(), snapshot, SkillOccupationSnapshot::merge);
        }
        return List.copyOf(summaries.values());
    }

    public AbilityData getAbilityData() {
        return cpData;
    }

    public float getCalculationIntensity() {
        return calculationIntensity;
    }

    public List<SkillOccupationSnapshot> getSkillOccupations() {
        return skillOccupations;
    }

    @Override
    public PacketType<ClientPacketListener, SyncAbilityDataPacket> getPacketType() {
        return PacketTypes.SYNC_ABILITY_DATA.get();
    }

    public record SkillOccupationSnapshot(
            String skillId,
            int stackCount,
            int remainingIterationPoints,
            float occupiedCp
    ) {
        public SkillOccupationSnapshot {
            stackCount = Math.max(0, stackCount);
            remainingIterationPoints = Math.max(0, remainingIterationPoints);
            occupiedCp = Float.isFinite(occupiedCp) ? Math.max(0.0f, occupiedCp) : 0.0f;
        }

        private SkillOccupationSnapshot merge(SkillOccupationSnapshot other) {
            return new SkillOccupationSnapshot(
                    skillId,
                    stackCount + other.stackCount,
                    remainingIterationPoints,
                    occupiedCp + other.occupiedCp
            );
        }
    }
}
