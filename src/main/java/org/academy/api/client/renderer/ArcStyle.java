package org.academy.api.client.renderer;

import org.joml.Vector3f;

public class ArcStyle {
    public Vector3f start = new Vector3f();
    public Vector3f end = new Vector3f(0, 1, 0);
    public int segments = 16;
    public float thickness = 0.1f;
    public long seed = 0;
    public float displacement = 5.0f;
    public float branchChance = 0.15f;
    public int maxBranchDepth = 2;
    public float branchLengthFactor = 0.6f;
    public float branchThicknessFactor = 0.6f;
    public float branchSegmentsFactor = 0.6f;
}