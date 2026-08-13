package org.academy.internal.common.ability.electromaster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.Branch;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.modifier.TaperModifier;
import org.academy.api.common.arc.path.CirclePath;
import org.academy.api.common.arc.path.LinePath;
import org.academy.api.common.arc.path.PolylinePath;
import org.academy.api.common.arc.property.AttributeCurve;
import org.academy.api.common.arc.property.Knot;
import org.academy.internal.common.ability.accelerator.reflection.LinearSegment;
import org.academy.internal.common.world.entity.skill.ArcEffect;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ElectromasterArcEffects {
    private static final AttributeCurve FULL_THICKNESS = new AttributeCurve(List.of(
            new Knot(0.0f, 1.0f),
            new Knot(0.82f, 1.0f),
            new Knot(1.0f, 0.05f)
    ));
    private static final AttributeCurve SPEAR_CORE_THICKNESS = new AttributeCurve(List.of(
            new Knot(0.0f, 0.45f),
            new Knot(0.08f, 1.0f),
            new Knot(0.78f, 1.0f),
            new Knot(1.0f, 0.03f)
    ));
    private static final AttributeCurve SPEAR_BRANCH_THICKNESS = new AttributeCurve(List.of(
            new Knot(0.0f, 0.72f),
            new Knot(0.48f, 1.0f),
            new Knot(1.0f, 0.02f)
    ));
    private static final AttributeCurve CHAIN_THICKNESS = new AttributeCurve(List.of(
            new Knot(0.0f, 0.12f),
            new Knot(0.12f, 1.0f),
            new Knot(0.84f, 0.82f),
            new Knot(1.0f, 0.05f)
    ));

    private ElectromasterArcEffects() {
    }

    /**
     * 一条锯齿电弧路径：LinePath + JaggedModifier，视觉等价于闪电。
     */
    public static ArcPath arc(Vec3 start, Vec3 end, long seed) {
        return new ArcPath(
                new LinePath(start.toVector3f(), end.toVector3f()),
                List.of(new JaggedModifier(0.18f, 4, seed)),
                2.0f,
                List.of()
        );
    }

    /**
     * Builds a long electric spear with a dense core and several jagged strands. The outer strands
     * expand from the caster's hand, weave around the shaft, then converge at the impact point.
     */
    public static List<ArcPath> spearBundle(Vec3 start, Vec3 end, int strands, float radius) {
        return spearBundle(start, end, strands, radius, randomSeed());
    }

    public static List<ArcPath> spearBundle(Vec3 start, Vec3 end, int strands, float radius, long seed) {
        if (!isFinite(start) || !isFinite(end) || start.distanceToSqr(end) < 1.0E-8) return List.of();

        var strandCount = Math.max(3, strands);
        var resolvedRadius = Math.max(0.04f, radius);
        var length = start.distanceTo(end);
        var direction = end.subtract(start).normalize();
        var reference = Math.abs(direction.y) < 0.92 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        var right = direction.cross(reference).normalize();
        var up = right.cross(direction).normalize();
        var vertexCount = Mth.clamp((int) Math.ceil(length / 2.4) + 1, 10, 26);
        var branches = createSpearBranches(length, seed);

        var paths = new ArrayList<ArcPath>(strandCount + 1);
        paths.add(new ArcPath(
                new LinePath(start.toVector3f(), end.toVector3f()),
                List.of(
                        new JaggedModifier(0.09f, 4, mixSeed(seed, 0)),
                        new TaperModifier(SPEAR_CORE_THICKNESS, 1.18f)
                ),
                0.72f,
                branches
        ));

        for (var strand = 0; strand < strandCount; strand++) {
            var random = new Random(mixSeed(seed, strand + 1));
            var angle = random.nextDouble() * Mth.TWO_PI;
            var angularStep = (random.nextDouble() - 0.5) * 1.2;
            var strandRadius = resolvedRadius * (0.62 + random.nextDouble() * 0.38);
            var vertices = new ArrayList<Vector3fc>(vertexCount);
            for (var vertex = 0; vertex < vertexCount; vertex++) {
                var progress = (double) vertex / (vertexCount - 1);
                var entry = smoothStep(Mth.clamp(progress / 0.12, 0.0, 1.0));
                var tip = smoothStep(Mth.clamp((1.0 - progress) / 0.30, 0.0, 1.0));
                var envelope = entry * tip;
                if (vertex > 0 && vertex < vertexCount - 1) {
                    angularStep = Mth.clamp(angularStep * 0.42 + random.nextGaussian() * 0.68,
                            -1.35, 1.35);
                    angle += angularStep;
                }
                var radialJitter = 0.48 + random.nextDouble() * 0.52;
                var offsetRadius = strandRadius * radialJitter * envelope;
                var offset = right.scale(Math.cos(angle) * offsetRadius)
                        .add(up.scale(Math.sin(angle) * offsetRadius));
                vertices.add(start.lerp(end, progress).add(offset).toVector3f());
            }
            paths.add(new ArcPath(
                    new PolylinePath(List.copyOf(vertices)),
                    List.of(
                            new JaggedModifier(0.11f, 2, mixSeed(seed, strand + 1)),
                            new TaperModifier(SPEAR_CORE_THICKNESS, 0.78f)
                    ),
                    0.62f,
                    List.of()
            ));
        }
        return List.copyOf(paths);
    }

    private static List<Branch> createSpearBranches(double spearLength, long seed) {
        var branchCount = Mth.clamp((int) Math.round(spearLength / 7.0), 1, 6);
        var lengthScale = Mth.clamp(spearLength / 6.0, 0.20, 1.0);
        var random = new Random(mixSeed(seed, 64));
        var branches = new ArrayList<Branch>(branchCount);
        for (var branch = 0; branch < branchCount; branch++) {
            var baseProgress = (branch + 1.0) / (branchCount + 1.0);
            var progress = (float) Mth.clamp(baseProgress + (random.nextDouble() - 0.5) * 0.10,
                    0.12, 0.90);
            var angle = random.nextDouble() * Mth.TWO_PI;
            var radialLength = (0.24 + random.nextDouble() * 0.48) * lengthScale;
            var forwardLength = (0.14 + random.nextDouble() * 0.42) * lengthScale;
            var localEnd = new Vector3f(
                    (float) (Math.cos(angle) * radialLength),
                    (float) (Math.sin(angle) * radialLength),
                    (float) forwardLength
            );
            var child = new ArcPath(
                    new LinePath(new Vector3f(), localEnd),
                    List.of(
                            new JaggedModifier(0.13f, 2, mixSeed(seed, 96 + branch)),
                            new TaperModifier(SPEAR_BRANCH_THICKNESS, 0.34f)
                    ),
                    0.46f,
                    List.of()
            );
            branches.add(new Branch(progress, child));
        }
        return List.copyOf(branches);
    }

    public static List<ArcPath> chainBundle(Vec3 start, Vec3 end, long seed) {
        if (!isFinite(start) || !isFinite(end) || start.distanceToSqr(end) < 1.0E-8) return List.of();
        var paths = new ArrayList<ArcPath>(3);
        for (var strand = 0; strand < 3; strand++) {
            paths.add(new ArcPath(
                    new LinePath(start.toVector3f(), end.toVector3f()),
                    List.of(
                            new JaggedModifier(0.20f + strand * 0.07f, 3, mixSeed(seed, strand)),
                            new TaperModifier(CHAIN_THICKNESS, 0.92f - strand * 0.18f)
                    ),
                    1.1f,
                    List.of()
            ));
        }
        return List.copyOf(paths);
    }

    public static void spawnChainArc(ServerLevel level, Vec3 start, Vec3 end) {
        spawnArc(level, chainBundle(start, end, randomSeed()), 8, start);
    }

    public static void spawnBeamCoils(ServerLevel level, LinearSegment segment) {
        if (segment == null || !segment.isFinite() || segment.length() < 1.0) return;
        var direction = segment.direction();
        var paths = new ArrayList<ArcPath>();
        for (var distance = 2.5; distance < segment.length(); distance += 3.5) {
            var center = segment.start().add(direction.scale(distance));
            paths.add(new ArcPath(
                    new CirclePath(center.toVector3f(), direction.toVector3f(), 0.34f),
                    List.of(
                            new JaggedModifier(0.10f, 2, randomSeed()),
                            new TaperModifier(FULL_THICKNESS, 0.42f)
                    ),
                    6.0f,
                    List.of()
            ));
        }
        spawnArc(level, paths, 8, segment.start());
    }

    public static void spawnShieldArcs(ServerLevel level, Vec3 center, long age) {
        var paths = new ArrayList<ArcPath>();
        for (var i = 0; i < 6; i++) {
            var angle0 = age * 0.17 + i * Mth.PI / 3.0;
            var angle1 = angle0 + 0.82;
            var y0 = 0.25 + (i % 3) * 0.62;
            var y1 = 0.25 + ((i + 1) % 3) * 0.62;
            var start = center.add(Mth.cos(angle0) * 0.78, y0, Mth.sin(angle0) * 0.78);
            var end = center.add(Mth.cos(angle1) * 0.78, y1, Mth.sin(angle1) * 0.78);
            paths.add(arc(start, end, randomSeed()));
        }
        spawnArc(level, paths, 5, center);
    }

    /**
     * Emits a short-lived electric ring on the shield face struck by a remote effect.
     */
    public static void spawnShieldInterceptRing(ServerLevel level, Vec3 center, Vec3 direction) {
        if (level == null || center == null || direction == null
                || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 1.0E-8) {
            return;
        }
        var normal = direction.normalize();
        var paths = new ArrayList<ArcPath>(2);
        paths.add(new ArcPath(
                new CirclePath(center.toVector3f(), normal.toVector3f(), 0.72f),
                List.of(
                        new JaggedModifier(0.13f, 3, randomSeed()),
                        new TaperModifier(FULL_THICKNESS, 0.86f)
                ),
                5.0f,
                List.of()
        ));
        paths.add(new ArcPath(
                new CirclePath(center.toVector3f(), normal.toVector3f(), 0.48f),
                List.of(
                        new JaggedModifier(0.10f, 2, randomSeed()),
                        new TaperModifier(FULL_THICKNESS, 0.58f)
                ),
                4.0f,
                List.of()
        ));
        spawnArc(level, paths, 6, center);
    }

    public static void spawnNovaRing(ServerLevel level, Vec3 center, double radius, long age) {
        var paths = new ArrayList<ArcPath>();
        var segments = 16;
        for (var i = 0; i < segments; i++) {
            var angle0 = i * Mth.TWO_PI / segments + age * 0.08;
            var angle1 = (i + 1) * Mth.TWO_PI / segments + age * 0.08;
            var start = center.add(Mth.cos(angle0) * radius, Mth.sin(angle0 * 3.0) * 0.16,
                    Mth.sin(angle0) * radius);
            var end = center.add(Mth.cos(angle1) * radius, Mth.sin(angle1 * 3.0) * 0.16,
                    Mth.sin(angle1) * radius);
            paths.add(arc(start, end, randomSeed()));
        }
        spawnArc(level, paths, 4, center);
    }

    public static void spawnSkyStrike(ServerLevel level, Vec3 impact) {
        spawnSkyStrike(level, impact, SkyStrikeProfile.LIGHTNING_STORM);
    }

    public static void spawnSkyStrike(ServerLevel level, Vec3 impact, SkyStrikeProfile profile) {
        var random = level.getRandom();
        var resolvedProfile = profile == null ? SkyStrikeProfile.LIGHTNING_STORM : profile;
        SkyStrikeVisualPacket.broadcast(level, impact, resolvedProfile);
        level.playSound(
                null,
                impact.x,
                impact.y,
                impact.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.WEATHER,
                resolvedProfile.thunderVolume(),
                0.8f + random.nextFloat() * 0.2f
        );
        level.playSound(
                null,
                impact.x,
                impact.y,
                impact.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.WEATHER,
                resolvedProfile.impactVolume(),
                0.9f + random.nextFloat() * 0.2f
        );
    }

    public static void spawnArc(ServerLevel level, List<ArcPath> paths, int lifetime, Vec3 origin) {
        if (paths.isEmpty()) return;
        var effect = new ArcEffect(level, lifetime);
        effect.setPos(origin);
        effect.setArcPaths(paths);
        level.addFreshEntity(effect);
    }

    private static long randomSeed() {
        return RandomSource.create().nextLong();
    }

    private static long mixSeed(long seed, int index) {
        var mixed = seed + 0x9E3779B97F4A7C15L * (index + 1L);
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    private static double smoothStep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static boolean isFinite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
