package org.academy.api.client.gui.msdf.core;

public class ByteBitmapRef {
    public byte[] pixels;
    public int width;
    public int height;
    public int rowStride;
    public YAxisOrientation yOrientation;
    private int offset;

    public ByteBitmapRef(byte[] pixels, int width, int height, int rowStride, YAxisOrientation yOrientation) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
        this.rowStride = rowStride;
        this.yOrientation = yOrientation;
        offset = 0;
    }

    public int getIndex(int x, int y) {
        return offset + rowStride * y + x;
    }

    public void reorient(YAxisOrientation newYAxisOrientation) {
        if (yOrientation != newYAxisOrientation) {
            offset += rowStride * (height - 1);
            rowStride = -rowStride;
            yOrientation = newYAxisOrientation;
        }
    }
}