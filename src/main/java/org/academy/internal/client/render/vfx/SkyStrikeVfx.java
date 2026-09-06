package org.academy.internal.client.render.vfx;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.internal.common.ability.electromaster.SkyStrikeProfile;
import org.joml.Vector3f;

/** Owns the graph-authored world effect and bounded screen feedback on the same paused game clock. */
public final class SkyStrikeVfx implements Vfx {
    private static final int MAX_DETAILED_EFFECTS = 12;
    private static int activeDetailedEffects;
    private static final java.util.Set<SkyStrikeVfx> ACTIVE = new java.util.HashSet<>();

    private final SkyStrikeProfile profile;
    private final float flashIntensity;
    private final float feedbackAttenuation;
    private final boolean detailedSlot;
    private final ActiveEffect graph;
    private float ageTicks;
    private boolean alive = true;
    private boolean released;

    public SkyStrikeVfx(Vec3 impact, long seed, SkyStrikeProfile profile,
                        SkyStrikeGeometry.Detail requestedDetail, float flashIntensity, float feedbackAttenuation) {
        this.profile = profile;
        this.flashIntensity = clamp01(flashIntensity);
        this.feedbackAttenuation = clamp01(feedbackAttenuation);
        var detail = claimDetail(requestedDetail);
        detailedSlot = detail != SkyStrikeGeometry.Detail.COLUMN_ONLY;
        var asset = Identifier.fromNamespaceAndPath(AcademyCraft.MOD_ID,
                profile == SkyStrikeProfile.THUNDERCLAP
                        ? "vfxgraph/sky_strike_thunderclap" : "vfxgraph/sky_strike_storm");
        try {
            graph = VfxGraphManager.INSTANCE.spawn(asset,
                    new Vector3f((float) impact.x, (float) impact.y, (float) impact.z));
        } catch (RuntimeException failure) {
            releaseSlot();
            throw failure;
        }
        synchronized (SkyStrikeVfx.class) {
            ACTIVE.add(this);
        }
        // Explicit time also keeps both render passes and the feedback pulse on one clock.
        graph.bind("time", () -> Value.of(ageTicks / 20f));
        graph.bind("seed", () -> Value.of((float) ((seed ^ (seed >>> 32)) & 0xFFFFFFL)));
        graph.bind("detail", () -> Value.of(switch (detail) {
            case FULL -> 1f;
            case REDUCED -> 0.45f;
            case COLUMN_ONLY -> 0f;
        }));
        graph.bind("cloud_opacity", () -> Value.of(profile == SkyStrikeProfile.THUNDERCLAP ? 1f : 0.32f));
        // The cloud extends far above the impact point used by the normal small-effect culler.
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null) graph.bindSurface("ground", new SkyStrikeTerrain(level, impact));
        graph.setAlwaysVisible(true);
        graph.setMinimumFarPlane(256);
    }

    private static synchronized SkyStrikeGeometry.Detail claimDetail(SkyStrikeGeometry.Detail requested) {
        if (requested == SkyStrikeGeometry.Detail.COLUMN_ONLY) return requested;
        if (activeDetailedEffects >= MAX_DETAILED_EFFECTS) return SkyStrikeGeometry.Detail.COLUMN_ONLY;
        activeDetailedEffects++;
        return requested;
    }

    static synchronized void clearConcurrency() {
        for (var effect : java.util.List.copyOf(ACTIVE)) effect.finish();
        activeDetailedEffects = 0;
    }

    private void finish() {
        alive = false;
        graph.stop();
        synchronized (SkyStrikeVfx.class) {
            ACTIVE.remove(this);
            releaseSlot();
        }
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Mth.clamp(value, 0, 1) : 0;
    }

    @Override
    public void update(float dt, VfxFrameContext context) {
        if (!alive) return;
        if (Float.isFinite(dt) && dt > 0) ageTicks += Math.min(dt, 4);
        if (ageTicks >= profile.lifetimeTicks()) {
            finish();
        }
    }

    @Override
    public void sample(VfxFrameContext context, VfxSink sink) {
        if (!alive) return;
        float screenAlpha = profile.flashAlpha() * flashCurve(ageTicks)
                * flashIntensity * feedbackAttenuation;
        if (screenAlpha > 0.001f) sink.push(new SkyStrikeScreenFlashData(screenAlpha, profile.flashCap()));
    }

    private float flashCurve(float age) {
        float initial = age < profile.flashDurationTicks()
                ? Mth.square(1f - age / profile.flashDurationTicks()) : 0;
        if (!profile.restrike()) return initial;
        float restrike = Math.max(0, 1f - Math.abs(age - 3f) / 0.65f) * 0.45f;
        return Math.min(1, initial + restrike);
    }

    private void releaseSlot() {
        if (!detailedSlot || released) return;
        released = true;
        synchronized (SkyStrikeVfx.class) {
            activeDetailedEffects = Math.max(0, activeDetailedEffects - 1);
        }
    }

    @Override
    public boolean isAlive() {
        return alive;
    }
}
