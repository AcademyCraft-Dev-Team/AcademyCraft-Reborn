package org.academy.api.common.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import java.util.List;

/** Serializable non-player template; skill IDs stay identical to the player skill registry. */
public record AbilityActorProfile(Identifier id, Identifier category, int level, double capacity,
                                  double abilityPower, double damageMultiplier, List<Identifier> skills) {
    public static final Codec<AbilityActorProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(AbilityActorProfile::id),
            Identifier.CODEC.fieldOf("category").forGetter(AbilityActorProfile::category),
            Codec.intRange(0, 6).fieldOf("level").forGetter(AbilityActorProfile::level),
            Codec.doubleRange(0, 1000000).fieldOf("capacity").forGetter(AbilityActorProfile::capacity),
            Codec.doubleRange(0, 1000).fieldOf("power").forGetter(AbilityActorProfile::abilityPower),
            Codec.doubleRange(0, 1000).fieldOf("damage").forGetter(AbilityActorProfile::damageMultiplier),
            Identifier.CODEC.listOf().fieldOf("skills").forGetter(AbilityActorProfile::skills)
    ).apply(i, AbilityActorProfile::new));
    public AbilityActorProfile {
        skills = List.copyOf(skills);
        if (id == null || category == null || level < 0 || level > 6
                || !Double.isFinite(capacity) || capacity < 0 || capacity > 1000000
                || !Double.isFinite(abilityPower) || abilityPower < 0 || abilityPower > 1000
                || !Double.isFinite(damageMultiplier) || damageMultiplier < 0 || damageMultiplier > 1000) {
            throw new IllegalArgumentException("Invalid ability actor profile");
        }
    }
}
