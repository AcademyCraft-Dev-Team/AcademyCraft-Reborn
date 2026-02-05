package org.academy.api.client.gui.msdf.core;

public class SDFTransformation extends Projection {
    public DistanceMapping distanceMapping;

    public SDFTransformation() {
        distanceMapping = new DistanceMapping();
    }

    public SDFTransformation(Projection projection, DistanceMapping distanceMapping) {
        super(projection.scale, projection.translate);
        this.distanceMapping = distanceMapping;
    }
}