package org.academy.api.client.gui.msdf.core;

public interface ContourCombiner<T> {
    void reset(Point2 p);

    EdgeSelector edgeSelector(int i);

    T distance();
}