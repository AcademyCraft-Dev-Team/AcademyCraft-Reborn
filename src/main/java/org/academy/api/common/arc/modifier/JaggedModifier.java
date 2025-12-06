package org.academy.api.common.arc.modifier;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.arc.PathModifier;
import org.academy.api.common.arc.PathModifierType;
import org.academy.api.common.arc.data.PathData;
import org.academy.api.common.arc.data.PathFrame;
import org.academy.api.common.arc.data.PropertyType;
import org.academy.internal.common.arc.PathModifierTypes;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public record JaggedModifier(float jaggedness, int subdivisions, long seed) implements PathModifier {
    public static final StreamCodec<ByteBuf, JaggedModifier> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, JaggedModifier::jaggedness,
            ByteBufCodecs.INT, JaggedModifier::subdivisions,
            ByteBufCodecs.LONG, JaggedModifier::seed,
            JaggedModifier::new
    );

    @Override
    public PathData apply(PathData data, float time) {
        var originalFrames = data.getFrames();
        if (originalFrames.size() < 2 || jaggedness <= 1.0E-6f || subdivisions <= 0) {
            return data;
        }

        var random = new Random(seed + (int) time);
        List<Vector3f> finalPoints = new ArrayList<>();
        finalPoints.add(new Vector3f(originalFrames.getFirst().position()));

        for (var i = 0; i < originalFrames.size() - 1; i++) {
            var start = originalFrames.get(i).position();
            var end = originalFrames.get(i + 1).position();
            displaceSegment(start, end, subdivisions, jaggedness, random, finalPoints);
        }

        return rebuildPathData(finalPoints, data);
    }

    private void displaceSegment(Vector3fc p1, Vector3fc p2, int depth, float currentJaggedness, Random random, List<Vector3f> pointList) {
        if (depth <= 0) {
            pointList.add(new Vector3f(p2));
            return;
        }

        var midpoint = new Vector3f(p1).add(p2).mul(0.5f);
        var segmentDir = new Vector3f(p2).sub(p1);

        var randomVec = new Vector3f(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f).normalize();
        var perpendicular = new Vector3f(segmentDir).cross(randomVec);
        if (perpendicular.lengthSquared() < 1.0E-6f) {
            perpendicular.set(1, 0, 0);
        }
        perpendicular.normalize();

        var displacement = segmentDir.length() * currentJaggedness * (random.nextFloat() - 0.5f);
        midpoint.add(perpendicular.mul(displacement));

        var nextJaggedness = currentJaggedness * 0.5f;

        displaceSegment(p1, midpoint, depth - 1, nextJaggedness, random, pointList);
        displaceSegment(midpoint, p2, depth - 1, nextJaggedness, random, pointList);
    }

    private PathData rebuildPathData(List<Vector3f> points, PathData originalData) {
        if (points.size() < 2) {
            return new PathData(new ArrayList<>());
        }

        List<PathFrame> newFrames = new ArrayList<>(points.size());
        var globalUp = new Vector3f(0, 1, 0);

        for (var i = 0; i < points.size() - 1; i++) {
            var p1 = points.get(i);
            var p2 = points.get(i + 1);

            var tangent = new Vector3f(p2).sub(p1).normalize();
            var normal = new Vector3f(tangent).cross(globalUp);
            if (normal.lengthSquared() < 1.0E-6f) {
                normal.set(tangent).cross(new Vector3f(1, 0, 0));
            }
            normal.normalize();

            newFrames.add(new PathFrame(p1, tangent, normal));
        }

        var lastFrame = newFrames.getLast();
        newFrames.add(new PathFrame(points.getLast(), lastFrame.tangent(), lastFrame.normal()));

        var newData = new PathData(newFrames);
        transferProperties(newData, originalData, points.size());

        return newData;
    }

    private void transferProperties(PathData newData, PathData originalData, int newSize) {
        if (originalData.hasProperty(PropertyType.THICKNESS)) {
            List<Float> newThickness = new ArrayList<>(newSize);
            for (var i = 0; i < newSize; i++) {
                newThickness.add(1.0f);
            }
            newData.setProperty(PropertyType.THICKNESS, newThickness);
        }

        if (originalData.hasProperty(PropertyType.COLOR)) {
            List<Vector3f> newColor = new ArrayList<>(newSize);
            var firstColor = originalData.getProperty(PropertyType.COLOR).getFirst();
            for (var i = 0; i < newSize; i++) {
                newColor.add(new Vector3f(firstColor));
            }
            newData.setProperty(PropertyType.COLOR, newColor);
        }
    }

    @Override
    public PathModifierType<? extends PathModifier> getType() {
        return PathModifierTypes.JAGGED.get();
    }
}