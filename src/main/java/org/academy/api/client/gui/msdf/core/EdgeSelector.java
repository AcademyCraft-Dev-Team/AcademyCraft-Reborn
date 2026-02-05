package org.academy.api.client.gui.msdf.core;

public interface EdgeSelector {
    void reset(Point2 p);

    void addEdge(EdgeSegment prevEdge, EdgeSegment edge, EdgeSegment nextEdge);

    void merge(EdgeSelector other);
}