package org.academy.api.server.ability;

import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.server.ability.HostileTargetRuntime;

/** Additional, actor-specific hostility for server-side skill and program target selection. */
public final class HostileTargets {
    public static final int MARK_DURATION_TICKS = 20 * 10;

    private HostileTargets() {}

    /** Marks a target after an intentional attack, refreshing its ten-second lifetime. */
    public static boolean mark(LivingEntity attacker, LivingEntity target) {
        return HostileTargetRuntime.mark(attacker, target);
    }

    /** Add to natural hostility, after the ability's own target admission checks. */
    public static boolean isMarked(LivingEntity attacker, LivingEntity target) {
        return HostileTargetRuntime.isMarked(attacker, target);
    }
}
