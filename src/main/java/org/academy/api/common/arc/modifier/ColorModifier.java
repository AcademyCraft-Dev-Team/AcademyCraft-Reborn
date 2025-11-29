package org.academy.api.common.arc.modifier;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.arc.PathModifier;
import org.academy.api.common.arc.PathModifierType;
import org.academy.api.common.arc.data.PathData;
import org.academy.api.common.arc.data.PropertyType;
import org.academy.api.common.arc.property.Gradient;
import org.academy.internal.common.arc.PathModifierTypes;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public record ColorModifier(Gradient gradient) implements PathModifier {
    public static final StreamCodec<ByteBuf, ColorModifier> CODEC = StreamCodec.composite(
            Gradient.CODEC, ColorModifier::gradient,
            ColorModifier::new
    );

    @Override
    public PathData apply(PathData data, int tick) {
        int frameCount = data.getFrames().size();
        if (frameCount <= 1) {
            return data;
        }

        List<Vector3f> colorTrack = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            float progress = (float) i / (frameCount - 1);
            colorTrack.add(this.gradient.evaluate(progress));
        }
        data.setProperty(PropertyType.COLOR, colorTrack);
        return data;
    }

    @Override
    public PathModifierType<? extends PathModifier> getType() {
        return PathModifierTypes.COLOR.get();
    }
}