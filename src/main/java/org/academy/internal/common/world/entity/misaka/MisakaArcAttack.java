package org.academy.internal.common.world.entity.misaka;

import net.minecraft.world.entity.LivingEntity;
import org.academy.api.server.ability.electromaster.ArcDischarge;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.electromaster.skills.lv1.ArcGenerate;

/** Sister adapter; the shared discharge API owns targeting, damage and effects. */
public final class MisakaArcAttack {
    private MisakaArcAttack() {}

    public static boolean tryFire(MisakaSisterEntity sister, LivingEntity target) {
        return ArcDischarge.fire(sister, target, Skills.ARC_GENERATE.get(),
                ArcGenerate.programDamage(1.0f, 1.0f));
    }
}
