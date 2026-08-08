package org.academy.internal.client.renderer.vfx;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.PathModifier;
import org.academy.api.common.arc.modifier.ColorModifier;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.modifier.TaperModifier;
import org.academy.api.common.arc.path.LinePath;
import org.academy.api.common.arc.property.AttributeCurve;
import org.academy.api.common.arc.property.ColorKnot;
import org.academy.api.common.arc.property.Gradient;
import org.academy.api.common.arc.property.Knot;
import org.academy.internal.common.ability.electromaster.SkyStrikeProfile;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public record SkyStrikeGeometry(
        List<ArcPath> paths,
        int aerialArcCount,
        int inwardArcCount,
        int groundArcCount
) {
    private static final AttributeCurve TAPER = new AttributeCurve(List.of(
            new Knot(0.0f, 1.0f),
            new Knot(0.72f, 0.9f),
            new Knot(1.0f, 0.04f)
    ));
    private static final Gradient BLUE_WHITE = new Gradient(List.of(
            new ColorKnot(0.0f, new Vector3f(0.96f, 0.99f, 1.0f)),
            new ColorKnot(0.55f, new Vector3f(0.49f, 0.80f, 1.0f)),
            new ColorKnot(1.0f, new Vector3f(0.20f, 0.55f, 1.0f))
    ));

    public SkyStrikeGeometry {
        paths = List.copyOf(paths);
    }

    public static SkyStrikeGeometry build(
            SkyStrikeProfile profile,
            Vec3 impact,
            long seed,
            Detail detail
    ) {
        var random = new Random(seed);
        if (detail == Detail.COLUMN_ONLY) {
            return new SkyStrikeGeometry(List.of(), 0, 0, 0);
        }

        var aerialCount = profile.aerialArcCount();
        var inwardCount = profile.inwardArcCount();
        var groundCount = profile.groundArcCount();
        if (detail == Detail.REDUCED) {
            aerialCount = (aerialCount + 1) / 2;
            inwardCount = (inwardCount + 1) / 2;
            groundCount = 0;
        }

        var paths = new ArrayList<ArcPath>(aerialCount + inwardCount + groundCount);
        var thunderclap = profile == SkyStrikeProfile.THUNDERCLAP;
        var aerialMinLength = thunderclap ? 8.0 : 5.0;
        var aerialLengthRange = thunderclap ? 10.0 : 5.0;
        for (var i = 0; i < aerialCount; i++) {
            var height = profile.columnHeight() * (0.28 + random.nextDouble() * 0.62);
            var start = impact.add(
                    gaussian(random) * profile.columnWidth() * 0.08,
                    height,
                    gaussian(random) * profile.columnWidth() * 0.08
            );
            var angle = random.nextDouble() * Math.PI * 2.0;
            var length = aerialMinLength + random.nextDouble() * aerialLengthRange;
            var vertical = (random.nextDouble() - 0.35) * length * 0.5;
            var end = start.add(Math.cos(angle) * length, vertical, Math.sin(angle) * length);
            paths.add(arc(start, end, thunderclap ? 0.38f : 0.28f,
                    thunderclap ? 2.8f : 1.8f, random.nextLong()));
        }

        for (var i = 0; i < inwardCount; i++) {
            var angle = random.nextDouble() * Math.PI * 2.0;
            var radius = 0.8 + random.nextDouble() * 2.7;
            var start = impact.add(
                    Math.cos(angle) * radius,
                    (random.nextDouble() - 0.35) * 2.0,
                    Math.sin(angle) * radius
            );
            var end = impact.add(gaussian(random) * 0.18, 0.28 + random.nextDouble() * 0.4,
                    gaussian(random) * 0.18);
            paths.add(arc(start, end, 0.52f, 2.6f, random.nextLong()));
        }

        for (var i = 0; i < groundCount; i++) {
            var angle = Math.PI * 2.0 * i / Math.max(1, groundCount) + random.nextDouble() * 0.18;
            var minRadius = thunderclap ? 3.0 : 1.5;
            var maxRadius = thunderclap ? 9.0 : 4.5;
            var radius = minRadius + random.nextDouble() * (maxRadius - minRadius);
            var start = impact.add(0, 0.16, 0);
            var end = impact.add(Math.cos(angle) * radius, 0.10, Math.sin(angle) * radius);
            paths.add(arc(start, end, thunderclap ? 0.32f : 0.24f,
                    thunderclap ? 1.8f : 1.1f, random.nextLong()));
        }
        return new SkyStrikeGeometry(paths, aerialCount, inwardCount, groundCount);
    }

    private static ArcPath arc(Vec3 start, Vec3 end, float jaggedness, float thickness, long seed) {
        return new ArcPath(
                new LinePath(start.toVector3f(), end.toVector3f()),
                List.<PathModifier>of(
                        new JaggedModifier(jaggedness, 4, seed),
                        new TaperModifier(TAPER, thickness),
                        new ColorModifier(BLUE_WHITE)
                ),
                1.6f,
                List.of()
        );
    }

    private static double gaussian(Random random) {
        return random.nextGaussian();
    }

    public enum Detail {
        FULL,
        REDUCED,
        COLUMN_ONLY
    }
}
