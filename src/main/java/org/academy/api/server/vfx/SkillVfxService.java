package org.academy.api.server.vfx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.vfx.SkillVfxState;
import org.academy.internal.server.vfx.SkillVfxRuntime;

/** Server-owned visual feedback API, usable by players, programs and non-player actors. */
public final class SkillVfxService {
    private SkillVfxService() {}

    public static void smoke(ServerLevel level, Vec3 position, float size, int lifetimeTicks) {
        if (!Double.isFinite(position.lengthSqr()) || !Float.isFinite(size)) return;
        var random = level.getRandom();
        SkillVfxRuntime.emit(level, new SkillVfxState.Smoke(position, Math.clamp(size, 0.01f, 16f),
                0.5f + random.nextFloat() * 0.2f, random.nextInt(4), Math.clamp(lifetimeTicks, 1, 80)));
    }

    public static void slash(ServerLevel level, Vec3 position, float xRot, float yRot,
                             float scale, int direction, int lifetimeTicks) {
        if (!Double.isFinite(position.lengthSqr()) || !Float.isFinite(xRot)
                || !Float.isFinite(yRot) || !Float.isFinite(scale)) return;
        SkillVfxRuntime.emit(level, new SkillVfxState.Slash(position, xRot, yRot, Math.clamp(scale, 0.1f, 32f),
                direction < 0 ? -1 : 1, Math.clamp(lifetimeTicks, 1, 200)));
    }

    public static void shockwave(ServerLevel level, Vec3 position, Vec3 direction, float radius, float intensity) {
        if (!Double.isFinite(position.lengthSqr()) || !Double.isFinite(direction.lengthSqr())
                || !Float.isFinite(radius) || !Float.isFinite(intensity)) return;
        intensity = Math.clamp(intensity, 1f, 6f);
        int life = Math.round(4f + 3f * (intensity - 1f) / 5f);
        SkillVfxRuntime.emit(level, new SkillVfxState.Burst(position,
                direction.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : direction.normalize(),
                Math.clamp(radius, 0.5f, 24f), intensity, life, false));
    }

    public static void plasmaImpact(ServerLevel level, Vec3 position, float radius) {
        if (!Double.isFinite(position.lengthSqr()) || !Float.isFinite(radius)) return;
        SkillVfxRuntime.emit(level, new SkillVfxState.Burst(position, Vec3.ZERO,
                Math.clamp(radius, 1f, 128f), 1f, 60, true));
    }
}
