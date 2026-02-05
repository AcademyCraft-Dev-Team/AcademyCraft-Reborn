package org.academy.api.client.gui.msdf.core;

public class FloatBitmapRef {
    public float[] pixels;
    public int width;
    public int height;
    public int nChannels;
    public int rowStride;
    public YAxisOrientation yOrientation;
    private int offset;

    public FloatBitmapRef(float[] pixels, int width, int height, int nChannels, int rowStride, YAxisOrientation yOrientation) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
        this.nChannels = nChannels;
        this.rowStride = rowStride;
        this.yOrientation = yOrientation;
        offset = 0;
    }

    public int getIndex(int x, int y) {
        return offset + rowStride * y + nChannels * x;
    }

    public void reorient(YAxisOrientation newYAxisOrientation) {
        if (yOrientation != newYAxisOrientation) {
            offset += rowStride * (height - 1);
            rowStride = -rowStride;
            yOrientation = newYAxisOrientation;
        }
    }

    public FloatBitmapRef getSection(int xMin, int yMin, int xMax, int yMax) {
        var section = new FloatBitmapRef(pixels, xMax - xMin, yMax - yMin, nChannels, rowStride, yOrientation);
        section.offset = getIndex(xMin, yMin);
        return section;
    }
}