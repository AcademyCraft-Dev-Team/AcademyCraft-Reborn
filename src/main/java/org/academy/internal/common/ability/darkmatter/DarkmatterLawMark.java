package org.academy.internal.common.ability.darkmatter;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;

/** Short-lived server-side abnormal-law mark shared by Cut, Disassemble and Interference. */
public final class DarkmatterLawMark {
    private static final String EFFECT = "abnormal_law";

    private DarkmatterLawMark() {
    }

    public static void apply(ServerPlayer owner, LivingEntity target, float betaPower, int durationTicks) {
        if (owner == null || target == null || !(betaPower > 0.0f) || durationTicks <= 0) return;
        TimedSkillEffectRuntime.put(
                owner,
                target.getUUID(),
                Skills.DARKMATTER_CUT.get(),
                EFFECT,
                durationTicks,
                Math.max(0.0f, betaPower));
    }

    /** Returns the armor-bypassing detonation damage and consumes a matching, unexpired mark. */
    public static float detonate(ServerPlayer owner, LivingEntity target) {
        if (owner == null || target == null) return 0.0f;
        return TimedSkillEffectRuntime.consume(
                        owner.getUUID(),
                        target.getUUID(),
                        Skills.DARKMATTER_CUT.get(),
                        EFFECT,
                        target.level().getGameTime())
                .map(entry -> 1.0f + 0.8f * entry.value())
                .orElse(0.0f);
    }

    public static boolean isMarkedBy(ServerPlayer owner, LivingEntity target) {
        return owner != null && target != null && TimedSkillEffectRuntime.get(
                owner.getUUID(),
                target.getUUID(),
                Skills.DARKMATTER_CUT.get(),
                EFFECT,
                target.level().getGameTime()).isPresent();
    }
}
