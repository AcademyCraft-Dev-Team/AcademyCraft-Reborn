package org.academy.api.client.render.vfx.lightning;

import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public final class LightningMeshBuilder implements TubeMeshView {
    private static final int NOT_INITIALIZED = -1;
    private static final float DEG_TO_RAD = Mth.PI / 180.0f;

    private int totalPointsCount = NOT_INITIALIZED;
    private int segmentResolution = NOT_INITIALIZED;
    private float[] positions;
    private float[] uvs;
    private int[] indices;
    private int vertexCount;
    private int indexCount;
    private float[] ringCos;
    private float[] ringSin;
    private long version;

    public void update(List<LightningBranch> lightningBranches, int segmentResolution, float segmentRadius) {
        update(lightningBranches, segmentResolution, segmentRadius, null);
    }

    public void update(
            List<LightningBranch> lightningBranches,
            int segmentResolution,
            float segmentRadius,
            float @Nullable [] pointRadii
    ) {
        int newTotalPointsCount = 0;
        for (LightningBranch lightningBranch : lightningBranches) {
            newTotalPointsCount += lightningBranch.lightningPoints.size();
        }

        if (needMeshReconstruction(newTotalPointsCount, segmentResolution)) {
            reconstructMesh(lightningBranches, newTotalPointsCount, segmentResolution);
        }

        int vertexIndex = 0;
        int pointIndex = 0;
        for (LightningBranch lightningBranch : lightningBranches) {
            float branchSegmentRadius = segmentRadius * lightningBranch.widthPercentage;
            for (LightningPoint lightningPoint : lightningBranch.lightningPoints) {
                float radius = pointRadii == null
                        ? branchSegmentRadius
                        : branchSegmentRadius * pointRadii[pointIndex];
                updatePointVertices(lightningPoint, radius, vertexIndex);
                vertexIndex += segmentResolution;
                pointIndex++;
            }
        }
    }

    public void clear() {
        totalPointsCount = NOT_INITIALIZED;
        segmentResolution = NOT_INITIALIZED;
        positions = null;
        uvs = null;
        indices = null;
        ringCos = null;
        ringSin = null;
        vertexCount = 0;
        indexCount = 0;
    }

    public boolean isEmpty() {
        return positions == null || vertexCount == 0;
    }

    public int vertexCount() {
        return vertexCount;
    }

    public int indexCount() {
        return indexCount;
    }

    public float[] positions() {
        return positions;
    }

    public float[] uvs() {
        return uvs;
    }

    public int[] indices() {
        return indices;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    @Override
    public long version() {
        return version;
    }

    @Override
    public void packIndices(ByteBuffer buffer) {
        buffer.clear();
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < indexCount; i++) {
            buffer.putInt(indices[i]);
        }
        buffer.flip();
    }

    private boolean needMeshReconstruction(int pointsCount, int segmentResolution) {
        return positions == null
                || totalPointsCount != pointsCount
                || this.segmentResolution != segmentResolution;
    }

    private void updatePointVertices(LightningPoint lightningPoint, float segmentRadius, int vertexIndex) {
        Vector3f right = lightningPoint.rightAxis;
        Vector3f up = lightningPoint.upAxis;
        for (int i = 0; i < segmentResolution; i++) {
            float offsetX = segmentRadius * ringCos[i];
            float offsetY = segmentRadius * ringSin[i];

            int base = (vertexIndex + i) * 3;
            positions[base] = lightningPoint.position.x + right.x * offsetX + up.x * offsetY;
            positions[base + 1] = lightningPoint.position.y + right.y * offsetX + up.y * offsetY;
            positions[base + 2] = lightningPoint.position.z + right.z * offsetX + up.z * offsetY;
        }
    }

    private void ensureRingTables() {
        if (ringCos != null && ringCos.length == segmentResolution) {
            return;
        }
        ringCos = new float[segmentResolution];
        ringSin = new float[segmentResolution];
        for (int i = 0; i < segmentResolution; i++) {
            float pointPercentage = (float) i / (segmentResolution - 1);
            float angle = DEG_TO_RAD * pointPercentage * 360f;
            ringCos[i] = Mth.cos(angle);
            ringSin[i] = Mth.sin(angle);
        }
    }

    private void reconstructMesh(List<LightningBranch> lightningBranches, int pointsCount, int segmentResolution) {
        totalPointsCount = pointsCount;
        this.segmentResolution = segmentResolution;
        ensureRingTables();

        int totalVerticesCount = 0;
        int totalTrianglesCount = 0;
        for (LightningBranch lightningBranch : lightningBranches) {
            int branchPointsCount = lightningBranch.lightningPoints.size();
            totalVerticesCount += segmentResolution * branchPointsCount;
            totalTrianglesCount += 2 * segmentResolution * (branchPointsCount - 1);
        }

        vertexCount = totalVerticesCount;
        indexCount = totalTrianglesCount * 3;
        positions = new float[totalVerticesCount * 3];
        uvs = new float[totalVerticesCount * 2];
        indices = new int[indexCount];

        int vertexOffset = 0;
        int trianglesOffset = 0;
        for (LightningBranch lightningBranch : lightningBranches) {
            createMeshData(lightningBranch, vertexOffset, trianglesOffset);
            int branchPointsCount = lightningBranch.lightningPoints.size();
            vertexOffset += branchPointsCount * segmentResolution;
            trianglesOffset += 3 * 2 * (branchPointsCount - 1) * segmentResolution;
        }
    }

    private void createMeshData(LightningBranch lightningBranch, int vertexOffset, int trianglesOffset) {
        List<LightningPoint> lightningPoints = lightningBranch.lightningPoints;
        int pointsCount = lightningPoints.size();
        int tri = trianglesOffset;

        for (int i = 0; i < pointsCount; i++) {
            for (int counter = 0; counter < segmentResolution; counter++) {
                int uvIndex = (vertexOffset + i * segmentResolution + counter) * 2;
                uvs[uvIndex] = lightningBranch.intensityPercentage;
                uvs[uvIndex + 1] = 0f;
            }

            if (i > 0) {
                tri = addTriangleIndices(tri, vertexOffset, i);
            }
        }
    }

    private int addTriangleIndices(int tri, int vertexIndexOffset, int pointIndex) {
        int previousSegmentFirstIndex = (pointIndex - 1) * segmentResolution;
        int previousSegmentLastIndex = previousSegmentFirstIndex + segmentResolution - 1;
        int currentSegmentFirstIndex = previousSegmentLastIndex + 1;
        int currentSegmentLastIndex = currentSegmentFirstIndex + segmentResolution - 1;

        for (int i = 0; i < segmentResolution; i++) {
            int previousSegmentFirst = previousSegmentFirstIndex + i;
            int previousSegmentSecond = previousSegmentFirst + 1;
            if (previousSegmentSecond > previousSegmentLastIndex) {
                previousSegmentSecond -= segmentResolution;
            }

            int currentSegmentFirst = currentSegmentFirstIndex + i;
            int currentSegmentSecond = currentSegmentFirst + 1;
            if (currentSegmentSecond > currentSegmentLastIndex) {
                currentSegmentSecond -= segmentResolution;
            }

            indices[tri++] = previousSegmentFirst + vertexIndexOffset;
            indices[tri++] = currentSegmentSecond + vertexIndexOffset;
            indices[tri++] = currentSegmentFirst + vertexIndexOffset;

            indices[tri++] = previousSegmentFirst + vertexIndexOffset;
            indices[tri++] = previousSegmentSecond + vertexIndexOffset;
            indices[tri++] = currentSegmentSecond + vertexIndexOffset;
        }

        return tri;
    }
}
