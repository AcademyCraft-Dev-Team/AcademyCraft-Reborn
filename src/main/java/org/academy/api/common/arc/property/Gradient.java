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
        if (this.knots.isEmpty()) {
            return new Vector3f(1.0f, 1.0f, 1.0f);
        }

        if (this.knots.size() == 1 || progress <= this.knots.getFirst().progress()) {
            return new Vector3f(this.knots.getFirst().color());
        }

        if (progress >= this.knots.getLast().progress()) {
            return new Vector3f(this.knots.getLast().color());
        }

        for (int i = 0; i < this.knots.size() - 1; i++) {
            ColorKnot current = this.knots.get(i);
            ColorKnot next = this.knots.get(i + 1);

            if (progress >= current.progress() && progress <= next.progress()) {
                float segmentProgress = (progress - current.progress()) / (next.progress() - current.progress());
                return current.color().lerp(next.color(), segmentProgress, new Vector3f());
            }
        }

        return new Vector3f(this.knots.getLast().color());
    }
}