package org.academy.api.common.damage;

/** A law-mark detonation retains its skill/category while selecting authoritative health damage. */
public final class LawDetonationDamageSource extends SkillDamageSource {
    public LawDetonationDamageSource(SkillDamageSource original) {
        super(original.typeHolder(), original.getDirectEntity(), original.getEntity(), original.getSkill());
    }
}
