package org.academy.internal.common.world.entity.misaka;

import org.academy.api.server.ability.AbilityCastResult;
import org.academy.api.server.ability.AbilityCastRequest;
import org.academy.api.server.ability.EntityAbilityService;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.ability.Skills;

/** Sister adapter; the shared discharge API owns targeting, damage and effects. */
public final class MisakaArcAttack {
    private MisakaArcAttack() {}

    public static boolean tryFire(MisakaSisterEntity sister, LivingEntity target) {
        return EntityAbilityService.tryCast(sister,
                AbilityCastRequest.autonomous(Skills.ARC_GENERATE.get().getKey(), target))
                == AbilityCastResult.ACCEPTED;
    }
}
