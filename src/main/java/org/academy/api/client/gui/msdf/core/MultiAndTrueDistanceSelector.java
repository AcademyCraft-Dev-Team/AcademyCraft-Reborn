package org.academy.api.client.gui.msdf.core;

public class MultiAndTrueDistanceSelector extends MultiDistanceSelector {
    @Override
    public MultiAndTrueDistance distance() {
        var multi = super.distance();
        var mtd = new MultiAndTrueDistance();
        mtd.r = multi.r;
        mtd.g = multi.g;
        mtd.b = multi.b;
        mtd.a = trueDistance().distance;
        return mtd;
    }
}