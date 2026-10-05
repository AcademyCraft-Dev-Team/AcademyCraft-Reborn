package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.academy.AcademyCraft;
import org.academy.internal.common.network.SpawnVfxGraphPacket;

import java.util.Map;

public final class VectorRedirectGraphEffects {
    private static final float LIFETIME_SECONDS = 0.35f;
    private static final float ARC_WIDTH_SCALE = 0.0057f;
    private static final float JAGGEDNESS_SPREAD_SCALE = 1.5f;

    private VectorRedirectGraphEffects() {
    }

    public static void broadcast(VectorRedirectPlan plan, double renderedLength) {
        if (plan.damageOnly()
                || plan.attack().tier().usesFallbackVisuals()
                || !plan.attack().tier().permitsSyntheticReturnVisual()
                || plan.attack().executionPolicy().visualStyle() == VectorVisualStyle.NONE
                || plan.attack().executionPolicy().visualStyle() == VectorVisualStyle.PROJECTILE
                || !(renderedLength > 1.0E-6)
                || !Double.isFinite(renderedLength)) {
            return;
        }
        var length = (float) Math.min(renderedLength, VectorExecutionPolicy.HARD_MAXIMUM_RANGE);
        var radius = (float) plan.attack().radius();
        var direction = plan.redirectedDirection();
        var mirrorPoint = plan.mirrorPoint();
        var seed = (float) plan.attack().fingerprint();
        ServerLevel level = plan.redirector().level();
        if (plan.attack().executionPolicy().visualStyle() == VectorVisualStyle.ARC) {
            var clampedRadius = Mth.clamp(radius, 0.02f, 4.0f);
            var jaggedness = plan.kind() == VectorRedirectKind.REFRACTION ? 0.18f : 0.1f;
            SpawnVfxGraphPacket.broadcast(level,
                    AcademyCraft.academy("vfxgraph/vector_redirect_arc"),
                    mirrorPoint,
                    direction,
                    -1,
                    1.0f,
                    LIFETIME_SECONDS,
                    Map.of(
                            "length", length,
                            "width", ARC_WIDTH_SCALE * clampedRadius,
                            "spread", jaggedness * JAGGEDNESS_SPREAD_SCALE,
                            "seed", seed
                    ));
            return;
        }
        var kindScale = plan.kind() == VectorRedirectKind.REFRACTION ? 0.65f : 1.0f;
        var widthScale = Math.max(0.15f, radius * 2.0f) * kindScale;
        SpawnVfxGraphPacket.broadcast(level,
                AcademyCraft.academy("vfxgraph/vector_redirect_beam"),
                mirrorPoint,
                direction,
                -1,
                1.0f,
                LIFETIME_SECONDS,
                Map.of(
                        "length", length,
                        "width_scale", widthScale,
                        "seed", seed
                ));
        SpawnVfxGraphPacket.broadcast(level,
                AcademyCraft.academy("vfxgraph/vector_redirect_muzzle"),
                mirrorPoint,
                direction,
                -1,
                1.0f,
                LIFETIME_SECONDS,
                Map.of(
                        "width_scale", kindScale,
                        "seed", seed
                ));
    }
}
