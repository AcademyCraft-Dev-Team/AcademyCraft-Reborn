package org.academy.api.client.render.vfx.lightning;

import java.util.ArrayList;
import java.util.List;

public final class LightningBranch {
    public int creationGeneration;
    public int spawnPointIndex;
    public float intensityPercentage;
    public float widthPercentage;
    public List<LightningPoint> lightningPoints;

    public LightningBranch() {
        this.lightningPoints = new ArrayList<>();
    }

    public LightningBranch(
            int creationGeneration,
            int spawnPointIndex,
            float intensityPercentage,
            float widthPercentage,
            List<LightningPoint> lightningPoints
    ) {
        this.creationGeneration = creationGeneration;
        this.spawnPointIndex = spawnPointIndex;
        this.intensityPercentage = intensityPercentage;
        this.widthPercentage = widthPercentage;
        this.lightningPoints = lightningPoints;
    }
}
