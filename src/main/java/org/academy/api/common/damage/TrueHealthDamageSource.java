package org.academy.api.common.damage;

import net.minecraft.world.damagesource.DamageSource;

/** Selects VEC-grade authoritative health settlement without changing damage type or attribution. */
public final class TrueHealthDamageSource extends DamageSource {
    private TrueHealthDamageSource(DamageSource original) {
        super(original.typeHolder(), original.getDirectEntity(), original.getEntity());
    }

    public static DamageSource of(DamageSource original) {
        if (matches(original)) return original;
        return original instanceof SkillDamageSource skill
                ? new SkillSource(skill) : new TrueHealthDamageSource(original);
    }

    public static boolean matches(DamageSource source) {
        return source instanceof TrueHealthDamageSource || source instanceof SkillSource;
    }

    private static final class SkillSource extends SkillDamageSource {
        private final boolean markHostility;

        private SkillSource(SkillDamageSource original) {
            super(original.typeHolder(), original.getDirectEntity(), original.getEntity(),
                    original.getSkill(), original.electricalChargePoints());
            markHostility = original.canMarkHostility();
        }

        @Override
        public boolean canMarkHostility() {
            return markHostility;
        }
    }
}
