package org.academy.internal.client.render.vfx;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.api.client.resources.R;
import org.academy.internal.common.ability.electromaster.SkyStrikeProfile;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public final class SkyStrikeVfx implements Vfx {
    private static final int MAX_DETAILED_EFFECTS = 12;
    private static int activeDetailedEffects;

    private final Vec3 impact;
    private final SkyStrikeProfile profile;
    private final SkyStrikeGeometry geometry;
    private final List<ArcTube> arcTubes = new ArrayList<>();
    private final float flashIntensity;
    private final float feedbackAttenuation;
    private final boolean detailedSlot;
    private final SkyStrikeGeometry.Detail detail;
    private float ageTicks;
    private boolean alive = true;
    private boolean released;

    public SkyStrikeVfx(
            Vec3 impact,
            long seed,
            SkyStrikeProfile profile,
            SkyStrikeGeometry.Detail requestedDetail,
            float flashIntensity,
            float feedbackAttenuation
    ) {
        this.impact = impact;
        this.profile = profile;
        this.flashIntensity = clamp01(flashIntensity);
        this.feedbackAttenuation = clamp01(feedbackAttenuation);
        this.detail = claimDetail(requestedDetail);
        this.detailedSlot = this.detail != SkyStrikeGeometry.Detail.COLUMN_ONLY;
        this.geometry = SkyStrikeGeometry.build(profile, impact, seed, this.detail);
    }

    private static void pushVerticalBillboard(
            VfxSink sink,
            Identifier texture,
            Vec3 bottomCenter,
            Vec3 cameraPos,
            float width,
            float height,
            Vector4f color,
            boolean glow
    ) {
        var dx = cameraPos.x - bottomCenter.x;
        var dz = cameraPos.z - bottomCenter.z;
        var length = Mth.sqrt((float) (dx * dx + dz * dz));
        var rightX = length > 1.0E-6 ? dz / length : 1.0;
        var rightZ = length > 1.0E-6 ? -dx / length : 0.0;
        var halfWidth = width * 0.5;
        var p0 = new Vector3f(
                (float) (bottomCenter.x - rightX * halfWidth),
                (float) bottomCenter.y,
                (float) (bottomCenter.z - rightZ * halfWidth)
        );
        var p1 = new Vector3f(
                (float) (bottomCenter.x + rightX * halfWidth),
                (float) bottomCenter.y,
                (float) (bottomCenter.z + rightZ * halfWidth)
        );
        var p2 = new Vector3f(p1).add(0, height, 0);
        var p3 = new Vector3f(p0).add(0, height, 0);
        pushQuad(sink, texture, p0, p1, p2, p3, color, glow);
    }

    private static void pushQuad(
            VfxSink sink,
            Identifier texture,
            Vector3f p0,
            Vector3f p1,
            Vector3f p2,
            Vector3f p3,
            Vector4f color,
            boolean glow
    ) {
        if (glow) {
            sink.push(new SkyStrikeWorldGlowData(texture, p0, p1, p2, p3, color));
        } else {
            sink.push(new SkyStrikeWorldCoreData(texture, p0, p1, p2, p3, color));
        }
    }

    private static float smooth(float value) {
        return value * value * (3.0f - 2.0f * value);
    }

    private static synchronized SkyStrikeGeometry.Detail claimDetail(SkyStrikeGeometry.Detail requested) {
        if (requested == SkyStrikeGeometry.Detail.COLUMN_ONLY) return requested;
        if (activeDetailedEffects >= MAX_DETAILED_EFFECTS) return SkyStrikeGeometry.Detail.COLUMN_ONLY;
        activeDetailedEffects++;
        return requested;
    }

    static synchronized void clearConcurrency() {
        activeDetailedEffects = 0;
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Mth.clamp(value, 0.0f, 1.0f) : 0.0f;
    }

    @Override
    public void update(float dt, VfxFrameContext context) {
        if (!alive) return;
        if (Float.isFinite(dt) && dt > 0.0f) ageTicks += Math.min(dt, 4.0f);
        if (ageTicks >= profile.lifetimeTicks()) {
            alive = false;
            releaseSlot();
        }
    }

    @Override
    public void sample(VfxFrameContext context, VfxSink sink) {
        if (!alive) return;
        var pulse = pulse(ageTicks);
        var cameraPos = new Vec3(
                context.camera().pos().x,
                context.camera().pos().y,
                context.camera().pos().z
        );
        pushColumn(sink, cameraPos, pulse);
        pushImpactFlash(sink, cameraPos, flashCurve(ageTicks));
        if (detail != SkyStrikeGeometry.Detail.COLUMN_ONLY) {
            pushRing(sink);
            if (pulse > 0.015f) pushArcs(context, sink, pulse);
        }

        var screenAlpha = profile.flashAlpha()
                * flashCurve(ageTicks)
                * flashIntensity
                * feedbackAttenuation;
        if (screenAlpha > 0.001f) {
            sink.push(new SkyStrikeScreenFlashData(screenAlpha, profile.flashCap()));
        }
    }

    private void pushArcs(VfxFrameContext context, VfxSink sink, float pulse) {
        var paths = geometry.paths();
        while (arcTubes.size() < paths.size()) {
            arcTubes.add(new ArcTube());
        }
        while (arcTubes.size() > paths.size()) {
            arcTubes.removeLast();
        }
        for (var i = 0; i < paths.size(); i++) {
            var tube = arcTubes.get(i);
            tube.build(paths.get(i), ageTicks * 1.25f);
            if (tube.mesh().isEmpty()) continue;
            sink.push(new LightningCoreData(tube));
            sink.push(new LightningRenderData(tube));
        }
    }

    private void pushColumn(VfxSink sink, Vec3 cameraPos, float pulse) {
        var alpha = Mth.clamp(pulse, 0.0f, 1.0f);
        if (alpha <= 0.005f) return;
        var height = profile.columnHeight();
        pushVerticalBillboard(
                sink,
                R.textures.ability.electromaster.skill.sky_strike.effect.lightning_column,
                impact,
                cameraPos,
                profile.columnWidth(),
                height,
                new Vector4f(0.96f, 0.99f, 1.0f, alpha),
                false
        );
        pushVerticalBillboard(
                sink,
                R.textures.ability.electromaster.skill.sky_strike.effect.lightning_column,
                impact,
                cameraPos,
                profile.columnWidth() * 1.35f,
                height,
                new Vector4f(0.20f, 0.55f, 1.0f, alpha * 0.72f),
                true
        );
    }

    private void pushImpactFlash(VfxSink sink, Vec3 cameraPos, float curve) {
        if (curve <= 0.005f) return;
        var size = profile == SkyStrikeProfile.THUNDERCLAP ? 12.0f : 5.0f;
        var base = impact.add(0, -size * 0.08, 0);
        pushVerticalBillboard(
                sink,
                R.textures.ability.electromaster.skill.sky_strike.effect.impact_flash,
                base,
                cameraPos,
                size,
                size,
                new Vector4f(0.96f, 0.99f, 1.0f, curve * 0.92f),
                false
        );
        pushVerticalBillboard(
                sink,
                R.textures.ability.electromaster.skill.sky_strike.effect.impact_flash,
                base,
                cameraPos,
                size * 1.25f,
                size * 1.25f,
                new Vector4f(0.20f, 0.55f, 1.0f, curve * 0.56f),
                true
        );
    }

    private void pushRing(VfxSink sink) {
        if (ageTicks > profile.ringDurationTicks()) return;
        var progress = Mth.clamp(ageTicks / profile.ringDurationTicks(), 0.0f, 1.0f);
        var radius = profile.ringStartRadius()
                + (profile.ringEndRadius() - profile.ringStartRadius()) * smooth(progress);
        var alpha = (1.0f - progress) * 0.88f;
        var y = (float) impact.y + 0.08f;
        var x = (float) impact.x;
        var z = (float) impact.z;
        var p0 = new Vector3f(x - radius, y, z - radius);
        var p1 = new Vector3f(x + radius, y, z - radius);
        var p2 = new Vector3f(x + radius, y, z + radius);
        var p3 = new Vector3f(x - radius, y, z + radius);
        pushQuad(sink, R.textures.ability.electromaster.skill.sky_strike.effect.impact_shockwave_ring,
                p0, p1, p2, p3, new Vector4f(0.49f, 0.80f, 1.0f, alpha), false);
        pushQuad(sink, R.textures.ability.electromaster.skill.sky_strike.effect.impact_shockwave_ring,
                p0, p1, p2, p3, new Vector4f(0.20f, 0.55f, 1.0f, alpha * 0.72f), true);
    }

    private float pulse(float age) {
        if (profile.restrike()) {
            if (age <= 1.0f) return 1.0f;
            if (age < 2.8f) return 0.25f * (1.0f - (age - 1.0f) / 1.8f);
            if (age < 3.25f) return 0.75f;
            return 0.75f * Math.max(0.0f,
                    1.0f - (age - 3.25f) / (profile.lifetimeTicks() - 3.25f));
        }
        return Math.max(0.0f, 1.0f - age / profile.lifetimeTicks());
    }

    private float flashCurve(float age) {
        var initial = age < profile.flashDurationTicks()
                ? Mth.square(1.0f - age / profile.flashDurationTicks())
                : 0.0f;
        if (!profile.restrike()) return initial;
        var restrike = Math.max(0.0f, 1.0f - Math.abs(age - 3.0f) / 0.65f) * 0.45f;
        return Math.min(1.0f, initial + restrike);
    }

    private synchronized void releaseSlot() {
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
