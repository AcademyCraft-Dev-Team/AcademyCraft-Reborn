package org.academy.api.common.vfx;

import net.minecraft.world.phys.Vec3;

/** Complete visual descriptions independent of damage, CP and client entity tracking. */
public sealed interface SkillVfxState {
    Vec3 position();

    record Beam(Vec3 position, float xRot, float yRot, float length, float scale, float sideOffset,
                int chargeTicks, int delayTicks, int remainingTicks, int flags,
                float reflectionDistance, float returnLength, Vec3 returnDirection, float tickRate)
            implements SkillVfxState {
        public boolean fired() { return (flags & 1) != 0; }
        public boolean continuous() { return (flags & 2) != 0; }
        public boolean held() { return (flags & 4) != 0; }
        public boolean reflected() { return (flags & 8) != 0; }
    }

    record Plasma(Vec3 position, Vec3 chargeOrigin, Vec3 target, float progress,
                  float speed, int launchDelay, boolean launched, float tickRate, float chargeRate)
            implements SkillVfxState {
        /** Samples from the authoritative snapshot, including before launch and during its delay. */
        public Vec3 positionAt(float elapsedTicks) {
            if (!launched) return position;
            var delta = target.subtract(position);
            double distance = delta.length();
            double travel = Math.max(0, elapsedTicks * tickRate - launchDelay) * speed;
            return distance < 1.0e-6 ? target
                    : position.add(delta.scale(Math.min(1, travel / distance)));
        }
    }

    record Burst(Vec3 position, Vec3 direction, float radius, float intensity,
                 int lifetimeTicks, boolean plasmaImpact) implements SkillVfxState {}

    /** Hiding a subscription allows subsequent snapshots; termination does not. */
    record End(Vec3 position, boolean hidden) implements SkillVfxState {}
}
