package org.academy.api.common.arc.property;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record AttributeCurve(List<Knot> knots) {
    public static final StreamCodec<ByteBuf, AttributeCurve> CODEC = Knot.CODEC.apply(ByteBufCodecs.list())
            .map(AttributeCurve::new, AttributeCurve::knots);

    public AttributeCurve {
        knots = new ArrayList<>(knots);
        knots.sort(Comparator.comparingDouble(Knot::progress));
    }

    public float evaluate(float progress) {
        if (knots.isEmpty()) {
            return 1.0f;
        }

        if (knots.size() == 1 || progress <= knots.getFirst().progress()) {
            return knots.getFirst().value();
        }

        if (progress >= knots.getLast().progress()) {
            return knots.getLast().value();
        }

        for (var i = 0; i < knots.size() - 1; i++) {
            var current = knots.get(i);
            var next = knots.get(i + 1);

            if (progress >= current.progress() && progress <= next.progress()) {
                var segmentProgress = (progress - current.progress()) / (next.progress() - current.progress());
                return current.value() + (next.value() - current.value()) * segmentProgress;
            }
        }

        return knots.getLast().value();
    }
}