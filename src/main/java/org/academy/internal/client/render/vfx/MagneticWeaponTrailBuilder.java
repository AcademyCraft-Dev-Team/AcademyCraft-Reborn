package org.academy.internal.client.render.vfx;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.PathModifier;
import org.academy.api.common.arc.modifier.ColorModifier;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.modifier.TaperModifier;
import org.academy.api.common.arc.path.LinePath;
import org.academy.api.common.arc.path.PolylinePath;
import org.academy.api.common.arc.property.AttributeCurve;
import org.academy.api.common.arc.property.ColorKnot;
import org.academy.api.common.arc.property.Gradient;
import org.academy.api.common.arc.property.Knot;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class MagneticWeaponTrailBuilder {
    public static final int FULL_HISTORY_SIZE = 8;
    public static final int MINIMAL_HISTORY_SIZE = 6;
    public static final double DISCONTINUITY_DISTANCE_SQR = 16.0;

    private static final AttributeCurve TAPER = new AttributeCurve(List.of(
            new Knot(0.0f, 1.0f),
            new Knot(0.35f, 0.78f),
            new Knot(0.72f, 0.28f),
            new Knot(1.0f, 0.02f)
    ));
    private static final Gradient COLOR = new Gradient(List.of(
            new ColorKnot(0.0f, new Vector3f(0.90f, 1.00f, 1.00f)),
            new ColorKnot(0.52f, new Vector3f(0.18f, 0.75f, 1.00f)),
            new ColorKnot(1.0f, new Vector3f(0.05f, 0.18f, 0.55f))
    ));

    private MagneticWeaponTrailBuilder() {
    }

    public static boolean appendSample(ArrayDeque<Vec3> history, Vec3 sample, int maxSize) {
        var discontinuity = !history.isEmpty()
                && history.getFirst().distanceToSqr(sample) > DISCONTINUITY_DISTANCE_SQR;
        if (discontinuity) history.clear();
        if (history.isEmpty() || history.getFirst().distanceToSqr(sample) > 1.0E-4) {
            history.addFirst(sample);
        }
        while (history.size() > Math.max(2, maxSize)) {
            history.removeLast();
        }
        return discontinuity;
    }

    public static ArcPath trail(List<Vec3> points, long seed, float thickness) {
        List<Vector3fc> vertices = new ArrayList<>(points.size());
        for (var point : points) vertices.add(point.toVector3f());
        return new ArcPath(
                new PolylinePath(vertices),
                modifiers(seed, thickness),
                2.2f,
                List.of()
        );
    }

    public static ArcPath line(Vec3 start, Vec3 end, long seed, float thickness) {
        return new ArcPath(
                new LinePath(start.toVector3f(), end.toVector3f()),
                modifiers(seed, thickness),
                2.2f,
                List.of()
        );
    }

    static List<PathModifier> modifiers(long seed, float thickness) {
        return List.of(
                new JaggedModifier(0.12f, 2, seed),
                new TaperModifier(TAPER, thickness),
                new ColorModifier(COLOR)
        );
    }
}
