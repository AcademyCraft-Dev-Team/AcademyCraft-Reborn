package org.academy.api.client.gui.msdf.util;

import com.mojang.blaze3d.platform.NativeImage;
import org.academy.api.client.gui.msdf.core.FloatBitmapRef;

public final class MsdfTextureUtil {
    public static NativeImage convertToNativeImage(FloatBitmapRef bitmap) {
        var width = bitmap.width;
        var height = bitmap.height;
        var channels = bitmap.nChannels;
        var nativeImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);

        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var index = bitmap.getIndex(x, y);
                var r = clamp(bitmap.pixels[index]);
                var g = channels >= 2 ? clamp(bitmap.pixels[index + 1]) : r;
                var b = channels >= 3 ? clamp(bitmap.pixels[index + 2]) : r;
                var a = channels >= 4 ? clamp(bitmap.pixels[index + 3]) : 255;

                var argb = (a << 24) | (r << 16) | (g << 8) | b;
                nativeImage.setPixel(x, height - 1 - y, argb);
            }
        }
        return nativeImage;
    }

    private static int clamp(float val) {
        return Math.max(0, Math.min(255, (int) (val * 255.0f)));
    }
}