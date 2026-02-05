package org.academy.api.client.gui.msdf.core;

public class FloatBitmap {
    public final float[] pixels;
    public final int width;
    public final int height;
    public final int nChannels;

    public FloatBitmap(int width, int height, int nChannels) {
        this.width = width;
        this.height = height;
        this.nChannels = nChannels;
        pixels = new float[nChannels * width * height];
    }

    public float[] getPixel(int x, int y) {
        var result = new float[nChannels];
        var index = nChannels * (width * y + x);
        System.arraycopy(pixels, index, result, 0, nChannels);
        return result;
    }

    public void setPixel(int x, int y, float[] values) {
        var index = nChannels * (width * y + x);
        System.arraycopy(values, 0, pixels, index, nChannels);
    }

    public FloatBitmapRef toRef() {
        return new FloatBitmapRef(pixels, width, height, nChannels, nChannels * width, YAxisOrientation.Y_UPWARD);
    }
}