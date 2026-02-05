package org.academy.api.client.gui.msdf.core;

import net.minecraft.util.Mth;

public class BitmapInterpolation {
    public static void interpolate(float[] output, FloatBitmapRef bitmap, Point2 pos) {
        var px = Arithmetic.clamp(pos.x, bitmap.width);
        var py = Arithmetic.clamp(pos.y, bitmap.height);
        px -= 0.5;
        py -= 0.5;
        var l = Mth.floor(px);
        var b = Mth.floor(py);
        var r = l + 1;
        var t = b + 1;
        var lr = px - l;
        var bt = py - b;

        l = Mth.clamp(l, 0, bitmap.width - 1);
        r = Mth.clamp(r, 0, bitmap.width - 1);
        b = Mth.clamp(b, 0, bitmap.height - 1);
        t = Mth.clamp(t, 0, bitmap.height - 1);

        for (var i = 0; i < bitmap.nChannels; ++i) {
            var lb = bitmap.pixels[bitmap.getIndex(l, b) + i];
            var rb = bitmap.pixels[bitmap.getIndex(r, b) + i];
            var lt = bitmap.pixels[bitmap.getIndex(l, t) + i];
            var rt = bitmap.pixels[bitmap.getIndex(r, t) + i];
            output[i] = (float) Arithmetic.mix(Arithmetic.mix(lb, rb, lr), Arithmetic.mix(lt, rt, lr), bt);
        }
    }
}