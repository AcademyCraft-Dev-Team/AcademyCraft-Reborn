package org.academy.api.client.gui.msdf.core;

public class ByteBitmap {
    public final byte[] pixels;
    public final int width;
    public final int height;

    public ByteBitmap(int width, int height) {
        this.width = width;
        this.height = height;
        pixels = new byte[width * height];
    }

    public ByteBitmapRef toRef() {
        return new ByteBitmapRef(pixels, width, height, width, YAxisOrientation.Y_UPWARD);
    }
}