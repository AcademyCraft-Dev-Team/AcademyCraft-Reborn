package org.academy.api.common.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.academy.api.server.ability.AbilityCpAccount;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Authoritative actor state. Runtime sessions and entity references are deliberately not serialized. */
public final class AbilityActorState {
    public static final Codec<AbilityActorState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("version", 1).forGetter(s -> 1),
            AbilityActorProfile.CODEC.fieldOf("profile").forGetter(AbilityActorState::profile),
            AbilityCpAccount.CODEC.fieldOf("cp").forGetter(AbilityActorState::cp),
            Codec.unboundedMap(Identifier.CODEC, Codec.intRange(0, Integer.MAX_VALUE))
                    .optionalFieldOf("cooldowns", Map.of()).forGetter(s -> Map.copyOf(s.cooldowns)),
            Identifier.CODEC.listOf().optionalFieldOf("disabled", List.of()).forGetter(s -> List.copyOf(s.disabled)),
            Codec.unboundedMap(Identifier.CODEC, Codec.intRange(0, 3))
                    .optionalFieldOf("milestones", Map.of()).forGetter(s -> Map.copyOf(s.milestones)),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("global_cooldown", 0).forGetter(s -> s.globalCooldown)
    ).apply(i, AbilityActorState::new));

    private final AbilityActorProfile profile;
    private final AbilityCpAccount cp;
    private final Map<Identifier, Integer> cooldowns;
    private final Set<Identifier> disabled;
    private final Map<Identifier, Integer> milestones;
    private int globalCooldown;

    public AbilityActorState(AbilityActorProfile profile) {
        this(1, profile, new AbilityCpAccount(profile.capacity()), Map.of(), List.of(), Map.of(), 0);
    }
    private AbilityActorState(int version, AbilityActorProfile profile, AbilityCpAccount cp,
                              Map<Identifier, Integer> cooldowns, List<Identifier> disabled,
                              Map<Identifier, Integer> milestones, int globalCooldown) {
        if (version != 1) throw new IllegalArgumentException("Unsupported actor state version " + version);
        this.profile = profile;
        this.cp = cp;
        this.cooldowns = new HashMap<>(cooldowns);
        this.disabled = new HashSet<>(disabled);
        this.milestones = new HashMap<>(milestones);
        this.globalCooldown = globalCooldown;
        // No session survives a world reload, so no permanent reservation may survive it.
        cp.releasePermanent();
    }
    public AbilityActorProfile profile() { return profile; }
    public AbilityCpAccount cp() { return cp; }
    public boolean knows(Identifier skill) { return profile.skills().contains(skill); }
    public boolean enabled(Identifier skill) { return knows(skill) && !disabled.contains(skill); }
    public void setEnabled(Identifier skill, boolean enabled) {
        if (!knows(skill)) throw new IllegalArgumentException("Unknown actor skill " + skill);
        if (enabled) disabled.remove(skill); else disabled.add(skill);
    }
    public int milestone(Identifier skill) { return milestones.getOrDefault(skill, 0); }
    public void setMilestone(Identifier skill, int milestone) {
        if (!knows(skill) || milestone < 0 || milestone > 3) throw new IllegalArgumentException("milestone");
        milestones.put(skill, milestone);
    }
    public int cooldown(Identifier skill) { return cooldowns.getOrDefault(skill, 0); }
    public int globalCooldown() { return globalCooldown; }
    public void cast(Identifier skill, int cooldown) {
        cooldowns.put(skill, Math.max(0, cooldown));
        globalCooldown = 20;
    }
    public boolean tick(boolean recover) {
        boolean changed = globalCooldown > 0 || !cooldowns.isEmpty();
        if (globalCooldown > 0) globalCooldown--;
        cooldowns.replaceAll((id, ticks) -> Math.max(0, ticks - 1));
        cooldowns.values().removeIf(ticks -> ticks == 0);
        return (recover && cp.tick()) || changed;
    }
}
