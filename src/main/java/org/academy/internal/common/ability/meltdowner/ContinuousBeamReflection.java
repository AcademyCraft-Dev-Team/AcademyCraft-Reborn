package org.academy.internal.common.ability.meltdowner;

import net.minecraft.server.level.ServerLevel;
import org.academy.internal.common.ability.accelerator.reflection.LinearAttackPayload;
import org.academy.internal.common.ability.accelerator.reflection.LinearReflectionResolver;
import org.academy.internal.common.ability.accelerator.reflection.LinearSegment;
import org.academy.internal.common.ability.accelerator.reflection.ResolvedLinearAttack;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;

import java.util.UUID;

public final class ContinuousBeamReflection {
    private ContinuousBeamReflection() {
    }

    public static ResolvedLinearAttack resolve(
            ServerLevel level,
            LinearSegment segment,
            LinearAttackPayload payload,
            ContinuousReflectionSession session,
            int currentTick,
            int damageInterval,
            boolean damagePulse
    ) {
        var leasedReflectorId = session.reflectorId();
        // Use the complete mode resolver first so Vector Reduction and both shield refractions
        // participate in continuous beams just like they do in one-shot linear attacks.
        var candidate = LinearReflectionResolver.findCandidate(level, segment, payload);
        // Preserve the paid Vector Reflection lease when its CP has fallen below the threshold
        // required to start a new reflection.
        if (candidate.isEmpty() && leasedReflectorId != null) {
            candidate = LinearReflectionResolver.findCandidate(
                    level,
                    segment,
                    payload,
                    player -> {
                        var active = VectorReflection.Server.isActive(player);
                        return isCandidateEligible(
                                leasedReflectorId,
                                player.getUUID(),
                                active,
                                !active && VectorReflection.Server.canMaintainLinearReflectionLease(player)
                        );
                    }
            );
        }
        if (candidate.isEmpty()) return ResolvedLinearAttack.unreflected(segment);

        var reflection = candidate.get();
        var reflectorId = reflection.reflector().getUUID();
        boolean active;
        if (session.isActiveFor(reflectorId)) {
            if (currentTick >= session.nextChargeTick()) {
                active = damagePulse
                        ? session.renewIfDue(
                        reflectorId,
                        currentTick,
                        damageInterval,
                        () -> LinearReflectionResolver.tryActivate(reflection)
                )
                        : session.activate(
                        reflectorId,
                        currentTick,
                        nextPulse(currentTick, damageInterval),
                        damageInterval,
                        () -> LinearReflectionResolver.tryActivate(reflection)
                );
            } else {
                active = true;
            }
        } else {
            active = session.activate(
                    reflectorId,
                    currentTick,
                    nextPulse(currentTick, damageInterval),
                    damageInterval,
                    () -> LinearReflectionResolver.tryActivate(reflection)
            );
        }
        return active
                ? LinearReflectionResolver.createReflected(segment, reflection)
                : ResolvedLinearAttack.unreflected(segment);
    }

    static boolean isCandidateEligible(
            UUID leasedReflectorId,
            UUID candidateId,
            boolean active,
            boolean leaseMaintainable
    ) {
        return active || (candidateId != null
                && candidateId.equals(leasedReflectorId)
                && leaseMaintainable);
    }

    static int nextPulse(int currentTick, int interval) {
        if (interval <= 0) return currentTick;
        var remainder = Math.floorMod(currentTick, interval);
        return remainder == 0 ? currentTick : currentTick + interval - remainder;
    }
}
