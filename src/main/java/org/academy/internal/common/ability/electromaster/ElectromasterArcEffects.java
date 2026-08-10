package org.academy.internal.common.ability.electromaster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.modifier.HelixModifier;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.modifier.TaperModifier;
import org.academy.api.common.arc.path.CirclePath;
import org.academy.api.common.arc.path.LinePath;
import org.academy.api.common.arc.property.AttributeCurve;
import org.academy.api.common.arc.property.Knot;
import org.academy.internal.common.ability.accelerator.reflection.LinearSegment;
import org.academy.internal.common.world.entity.skill.ArcEffect;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

public final class ElectromasterArcEffects {
    private static final AttributeCurve FULL_THICKNESS = new AttributeCurve(List.of(
            new Knot(0.0f, 1.0f),
            new Knot(0.82f, 1.0f),
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

    public static List<ArcPath> intertwinedBundle(Vec3 start, Vec3 end, int strands, float radius) {
        var paths = new ArrayList<ArcPath>(Math.max(1, strands));
        var length = Math.max(1.0, start.distanceTo(end));
        var turns = (float) Mth.clamp(length / 3.5, 2.0, 8.0);
        for (var i = 0; i < Math.max(1, strands); i++) {
            var phase = (float) (Mth.TWO_PI * i / Math.max(1, strands));
            paths.add(new ArcPath(
                    new LinePath(start.toVector3f(), end.toVector3f()),
                    List.of(
                            new HelixModifier(radius * (0.72f + (i & 1) * 0.28f),
                                    turns + i * 0.18f, phase),
                            new JaggedModifier(0.16f, 3, randomSeed()),
                            new TaperModifier(FULL_THICKNESS, 0.72f)
                    ),
                    2.4f,
                    List.of()
            ));
        }
        return paths;
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
}
