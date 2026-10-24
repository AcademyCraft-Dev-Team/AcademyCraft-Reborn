package org.academy.api.common.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import java.util.List;

/** Immutable display projection. No resource or permission authority crosses the network. */
public record AbilityActorSnapshot(int level, double availableCp, double capacity, boolean active, List<SkillStatus> skills) {
    public record SkillStatus(Identifier id, boolean enabled, int cooldown, boolean running) {
        public static final StreamCodec<ByteBuf, SkillStatus> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.map(Identifier::parse, Identifier::toString), SkillStatus::id,
                ByteBufCodecs.BOOL, SkillStatus::enabled, ByteBufCodecs.VAR_INT, SkillStatus::cooldown,
                ByteBufCodecs.BOOL, SkillStatus::running, SkillStatus::new);
    }
    public static final AbilityActorSnapshot EMPTY = new AbilityActorSnapshot(0, 0, 0, false, List.of());
    public static final StreamCodec<ByteBuf, AbilityActorSnapshot> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AbilityActorSnapshot::level, ByteBufCodecs.DOUBLE, AbilityActorSnapshot::availableCp,
            ByteBufCodecs.DOUBLE, AbilityActorSnapshot::capacity, ByteBufCodecs.BOOL, AbilityActorSnapshot::active,
            SkillStatus.CODEC.apply(ByteBufCodecs.list(64)), AbilityActorSnapshot::skills, AbilityActorSnapshot::new);
    public AbilityActorSnapshot { skills = List.copyOf(skills); }
}
