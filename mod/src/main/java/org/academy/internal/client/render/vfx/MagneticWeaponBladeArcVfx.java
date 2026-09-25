package org.academy.internal.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.api.common.arc.ArcPath;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBlade;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBladeMotion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class MagneticWeaponBladeArcVfx implements Vfx {
    private static final long REMOVAL_FADE_NANOS = 150_000_000L;
    private static final double FULL_EFFECT_DISTANCE_SQR = 32.0 * 32.0;
    private static final double MAX_EFFECT_DISTANCE_SQR = 48.0 * 48.0;

    private final MagneticWeaponBlade blade;
    private final ArrayDeque<Vec3> history = new ArrayDeque<>();
    private final List<ArcTube> tubePool = new ArrayList<>();
    private int tubeCursor;
    private int lastEntityTick = Integer.MIN_VALUE;
    private int lastAttackTick;
    private int lastAttackSequence = Integer.MIN_VALUE;
    private int trailDecayTicks;
    private int impactTicks;
    private Vec3 impactCenter = Vec3.ZERO;
    private Vec3 impactHalfSize = new Vec3(0.4, 0.7, 0.4);
    private long removedAt;
    private boolean expired;

    public MagneticWeaponBladeArcVfx(MagneticWeaponBlade blade) {
        this.blade = blade;
    }

    private static Vec3 normalizedOr(Vec3 vector, Vec3 fallback) {
        return vector.lengthSqr() > 1.0E-8 ? vector.normalize() : fallback.normalize();
    }

    @Override
    public void sample(VfxFrameContext ctx, VfxSink sink) {
        updateRemovalState();
        if (expired) return;

        tubeCursor = 0;
        var camera = ctx.camera().pos();
        var cameraPosition = new Vec3(camera.x(), camera.y(), camera.z());
        var bladePosition = blade.isRemoved() || !blade.isAlive()
                ? history.isEmpty() ? blade.position() : history.getFirst()
                : blade.getPosition(ctx.partialTick());
        var distanceSqr = bladePosition.distanceToSqr(cameraPosition);
        if (distanceSqr > MAX_EFFECT_DISTANCE_SQR) {
            history.clear();
            lastEntityTick = blade.tickCount;
            lastAttackTick = blade.getAttackTick();
            lastAttackSequence = blade.getAttackSequence();
            return;
        }

        var status = Minecraft.getInstance().options.particles().get();
        var quality = Quality.from(status, distanceSqr <= FULL_EFFECT_DISTANCE_SQR);
        updateTickState(quality.maxHistory());

        var time = blade.tickCount * 0.5f;
        var seed = seed();
        if (blade.tickCount <= 4 && !blade.isRemoved()) {
            emitActivation(ctx, sink, quality, bladePosition, seed, time);
        }
        if (blade.getAttackTick() == 0 && !blade.isRemoved()) {
            emitIdleSpark(ctx, sink, quality, bladePosition, seed, time);
        }

        emitTrail(ctx, sink, quality, bladePosition, seed, time);
        emitImpact(ctx, sink, quality, seed, time);
    }

    private void updateTickState(int maxHistory) {
        if (blade.tickCount == lastEntityTick || blade.isRemoved()) return;
        lastEntityTick = blade.tickCount;

        if (impactTicks > 0) impactTicks--;
        var attackTick = blade.getAttackTick();
        var attackSequence = blade.getAttackSequence();
        if (attackTick > 0 && attackSequence != lastAttackSequence) {
            history.clear();
            trailDecayTicks = 0;
            lastAttackSequence = attackSequence;
        }

        if (attackTick >= MagneticWeaponBladeMotion.PREP_END_TICK + 1) {
            MagneticWeaponTrailBuilder.appendSample(history, blade.position(), maxHistory);
            trailDecayTicks = 3;
        } else if (attackTick == 0 && lastAttackTick > 0) {
            trailDecayTicks = 3;
        } else if (attackTick == 0 && trailDecayTicks > 0) {
            trailDecayTicks--;
            if (history.size() > 2) history.removeLast();
            if (trailDecayTicks == 0) history.clear();
        }

        if (MagneticWeaponBladeMotion.crossesImpact(lastAttackTick, attackTick)) {
            captureImpact();
            impactTicks = 3;
        }
        lastAttackTick = attackTick;
    }

    private void captureImpact() {
        var target = blade.level().getEntity(blade.getTargetId());
        if (target == null) {
            impactCenter = blade.position();
            impactHalfSize = new Vec3(0.4, 0.7, 0.4);
            return;
        }
        var box = target.getBoundingBox();
        impactCenter = box.getCenter();
        impactHalfSize = new Vec3(
                Math.max(0.3, box.getXsize() * 0.5),
                Math.max(0.4, box.getYsize() * 0.5),
                Math.max(0.3, box.getZsize() * 0.5)
        );
    }

    private void emitTrail(VfxFrameContext ctx, VfxSink sink, Quality quality,
                           Vec3 bladePosition, long seed, float time) {
        if (history.size() < 2) return;
        var points = new ArrayList<>(history);
        if (!blade.isRemoved()) points.set(0, bladePosition);
        while (points.size() > quality.maxHistory()) points.removeLast();
        emit(sink, MagneticWeaponTrailBuilder.trail(points, seed, 0.7f), time, quality.glow());

        if (quality.maxForks() <= 0 || points.size() < 3) return;
        var random = new Random(seed ^ 0x6A09E667F3BCC909L);
        for (var i = 0; i < quality.maxForks(); i++) {
            var index = 1 + random.nextInt(points.size() - 1);
            var start = points.get(index);
            var previous = points.get(Math.max(0, index - 1));
            var tangent = normalizedOr(previous.subtract(start), new Vec3(0.0, 1.0, 0.0));
            var randomDirection = normalizedOr(new Vec3(
                    random.nextDouble() - 0.5,
                    random.nextDouble() - 0.5,
                    random.nextDouble() - 0.5
            ), new Vec3(1.0, 0.0, 0.0));
            var perpendicular = normalizedOr(tangent.cross(randomDirection), new Vec3(1.0, 0.0, 0.0));
            var length = 0.2 + random.nextDouble() * 0.4;
            var end = start.add(perpendicular.scale(length)).add(tangent.scale(0.12));
            emit(sink, MagneticWeaponTrailBuilder.line(
                    start, end, seed + i * 31L + 7L, 0.32f
            ), time, quality.glow());
        }
    }

    private void emitActivation(VfxFrameContext ctx, VfxSink sink, Quality quality,
                                Vec3 bladePosition, long seed, float time) {
        var owner = blade.level().getEntity(blade.getOwnerId());
        if (!(owner instanceof Avatar avatar)) return;
        var forward = Vec3.directionFromRotation(0.0f, avatar.getYRot()).normalize();
        var right = new Vec3(-forward.z, 0.0, forward.x);
        var handSign = avatar.getMainArm() == HumanoidArm.RIGHT ? 1.0 : -1.0;
        var hand = avatar.position()
                .add(0.0, avatar.getBbHeight() * 0.65, 0.0)
                .add(right.scale(0.28 * handSign))
                .add(forward.scale(0.12));
        var count = quality.maxForks() > 0 ? 2 : 1;
        for (var i = 0; i < count; i++) {
            var end = bladePosition.add(0.0, (i - 0.5) * 0.18, 0.0);
            emit(sink, MagneticWeaponTrailBuilder.line(
                    hand, end, seed + 101L * i, 0.45f
            ), time, quality.glow());
        }
    }

    private void emitIdleSpark(VfxFrameContext ctx, VfxSink sink, Quality quality,
                               Vec3 bladePosition, long seed, float time) {
        var interval = 12 + Mth.positiveModulo(blade.getId(), 8);
        if (Mth.positiveModulo(blade.tickCount, interval) >= 2) return;
        var direction = Vec3.directionFromRotation(blade.getXRot(), blade.getYRot()).normalize();
        var side = normalizedOr(direction.cross(new Vec3(0.0, 1.0, 0.0)), new Vec3(1.0, 0.0, 0.0));
        var start = bladePosition.subtract(direction.scale(0.25)).add(side.scale(0.12));
        var end = bladePosition.add(direction.scale(0.35)).subtract(side.scale(0.15));
        emit(sink, MagneticWeaponTrailBuilder.line(start, end, seed, 0.28f), time, quality.glow());
    }

    private void emitImpact(VfxFrameContext ctx, VfxSink sink, Quality quality, long seed, float time) {
        if (impactTicks <= 0) return;
        var count = switch (quality.status()) {
            case ALL -> 6;
            case DECREASED -> 5;
            case MINIMAL -> 4;
        };
        var random = new Random(seed ^ 0xBB67AE8584CAA73BL);
        for (var i = 0; i < count; i++) {
            var end = impactCenter.add(
                    (random.nextDouble() * 2.0 - 1.0) * impactHalfSize.x,
                    (random.nextDouble() * 2.0 - 1.0) * impactHalfSize.y,
                    (random.nextDouble() * 2.0 - 1.0) * impactHalfSize.z
            );
            var start = impactCenter.add(
                    (random.nextDouble() - 0.5) * 0.2,
                    (random.nextDouble() - 0.5) * 0.2,
                    (random.nextDouble() - 0.5) * 0.2
            );
            emit(sink, MagneticWeaponTrailBuilder.line(
                    start, end, seed + 211L * i, 0.5f
            ), time, quality.glow());
        }
    }

    private void emit(VfxSink sink, ArcPath path, float time, boolean glow) {
        var tube = acquireTube();
        tube.build(path, time);
        if (tube.mesh().isEmpty()) return;
        sink.push(new LightningCoreData(tube));
        if (glow) sink.push(new LightningRenderData(tube));
    }

    private ArcTube acquireTube() {
        if (tubeCursor >= tubePool.size()) {
            tubePool.add(new ArcTube());
        }
        return tubePool.get(tubeCursor++);
    }

    private long seed() {
        return ((long) blade.getId() << 32)
                ^ (blade.getAttackSequence() * 0x9E3779B9L)
                ^ (blade.tickCount / 2L);
    }

    private void updateRemovalState() {
        if (!blade.isRemoved() && blade.isAlive()) return;
        if (removedAt == 0L) removedAt = System.nanoTime();
        expired = System.nanoTime() - removedAt >= REMOVAL_FADE_NANOS;
    }

    @Override
    public boolean isAlive() {
        return !expired;
    }

    private record Quality(ParticleStatus status, int maxHistory, int maxForks, boolean glow) {
        private static Quality from(ParticleStatus status, boolean fullDistance) {
            if (!fullDistance) {
                var history = status == ParticleStatus.MINIMAL
                        ? MagneticWeaponTrailBuilder.MINIMAL_HISTORY_SIZE
                        : MagneticWeaponTrailBuilder.FULL_HISTORY_SIZE;
                return new Quality(status, history, 0, false);
            }
            return switch (status) {
                case ALL -> new Quality(status, MagneticWeaponTrailBuilder.FULL_HISTORY_SIZE, 2, true);
                case DECREASED -> new Quality(status, MagneticWeaponTrailBuilder.FULL_HISTORY_SIZE, 1, true);
                case MINIMAL -> new Quality(status, MagneticWeaponTrailBuilder.MINIMAL_HISTORY_SIZE, 0, false);
            };
        }
    }
}
