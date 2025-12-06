package org.academy.api.common.arc.property;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record Gradient(List<ColorKnot> knots) {
    public static final StreamCodec<ByteBuf, Gradient> CODEC = ColorKnot.CODEC.apply(ByteBufCodecs.list())
            .map(Gradient::new, Gradient::knots);

    public Gradient {
        knots = new ArrayList<>(knots);
        knots.sort(Comparator.comparingDouble(ColorKnot::progress));
    }

    public Vector3f evaluate(float progress) {
        if (knots.isEmpty()) {
            return new Vector3f(1.0f, 1.0f, 1.0f);
        }

        if (knots.size() == 1 || progress <= knots.getFirst().progress()) {
            return new Vector3f(knots.getFirst().color());
        }

        if (progress >= knots.getLast().progress()) {
            return new Vector3f(knots.getLast().color());
        }

        for (var i = 0; i < knots.size() - 1; i++) {
            var current = knots.get(i);
            var next = knots.get(i + 1);

            if (progress >= current.progress() && progress <= next.progress()) {
                var segmentProgress = (progress - current.progress()) / (next.progress() - current.progress());
                return current.color().lerp(next.color(), segmentProgress, new Vector3f());
            }
        }

        return new Vector3f(knots.getLast().color());
    }
}