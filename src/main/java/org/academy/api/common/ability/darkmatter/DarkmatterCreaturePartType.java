package org.academy.api.common.ability.darkmatter;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Public descriptor for a body part that can participate in a dark-matter creature blueprint. */
public record DarkmatterCreaturePartType(
        Identifier id,
        BodySlot slot,
        int clientModelId,
        StatProcessor statProcessor
) {
    public DarkmatterCreaturePartType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(slot, "slot");
        statProcessor = statProcessor == null ? StatProcessor.NONE : statProcessor;
        if (clientModelId < 0) throw new IllegalArgumentException("clientModelId must be non-negative");
    }

    /** Stable client localization key; extension mods can provide the same key in their lang files. */
    public String translationKey() {
        return "darkmatter_creature.part." + id.getNamespace() + "." + id.getPath();
    }

    public String descriptionTranslationKey() {
        return translationKey() + ".desc";
    }

    public enum BodySlot {
        HEAD,
        TORSO,
        LIMBS,
        ADDITIONAL
    }

    @FunctionalInterface
    public interface StatProcessor {
        StatProcessor NONE = (_, _, _) -> { };

        void apply(MutableStats stats, float alphaPower, float betaPower);
    }

    /** Mutable construction-time values exposed to extension part processors. */
    public static final class MutableStats {
        public double maxHealth;
        public double attackDamage;
        public double armor;
        public double movementSpeed;
        public double followRange;

        public MutableStats(double maxHealth, double attackDamage, double armor,
                            double movementSpeed, double followRange) {
            this.maxHealth = maxHealth;
            this.attackDamage = attackDamage;
            this.armor = armor;
            this.movementSpeed = movementSpeed;
            this.followRange = followRange;
        }
    }
}
