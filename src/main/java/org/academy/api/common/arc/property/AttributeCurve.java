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
        if (this.knots.isEmpty()) {
            return 1.0f;
        }

        if (this.knots.size() == 1 || progress <= this.knots.getFirst().progress()) {
            return this.knots.getFirst().value();
        }

        if (progress >= this.knots.getLast().progress()) {
            return this.knots.getLast().value();
        }

        for (int i = 0; i < this.knots.size() - 1; i++) {
            Knot current = this.knots.get(i);
            Knot next = this.knots.get(i + 1);

            if (progress >= current.progress() && progress <= next.progress()) {
                float segmentProgress = (progress - current.progress()) / (next.progress() - current.progress());
                return current.value() + (next.value() - current.value()) * segmentProgress;
            }
        }

        return this.knots.getLast().value();
    }
}