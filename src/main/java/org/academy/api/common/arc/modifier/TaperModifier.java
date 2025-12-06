package org.academy.api.common.arc.modifier;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.arc.PathModifier;
import org.academy.api.common.arc.PathModifierType;
import org.academy.api.common.arc.data.PathData;
import org.academy.api.common.arc.data.PathFrame;
import org.academy.api.common.arc.data.PropertyType;
import org.academy.api.common.arc.property.AttributeCurve;
import org.academy.internal.common.arc.PathModifierTypes;

import java.util.List;

public record TaperModifier(AttributeCurve curve, float globalScale) implements PathModifier {
    public static final StreamCodec<ByteBuf, TaperModifier> CODEC = StreamCodec.composite(
            AttributeCurve.CODEC, TaperModifier::curve,
            ByteBufCodecs.FLOAT, TaperModifier::globalScale,
            TaperModifier::new
    );

    @Override
    public PathData apply(PathData data, float time) {
        if (!data.hasProperty(PropertyType.THICKNESS)) {
            return data;
        }

        var thicknessTrack = data.getProperty(PropertyType.THICKNESS);
        var frameCount = data.getFrames().size();
        if (frameCount <= 1) {
            return data;
        }

        for (var i = 0; i < frameCount; ++i) {
            var progress = (float) i / (frameCount - 1);
            var taperScale = curve.evaluate(progress) * globalScale;
            thicknessTrack.set(i, thicknessTrack.get(i) * taperScale);
        }

        return data;
    }

    @Override
    public PathModifierType<? extends PathModifier> getType() {
        return PathModifierTypes.TAPER.get();
    }
}