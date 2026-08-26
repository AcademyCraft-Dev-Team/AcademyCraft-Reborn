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
        var newTotalPointsCount = 0;
        for (var lightningBranch : lightningBranches) {
            newTotalPointsCount += lightningBranch.lightningPoints.size();
        }

        if (needMeshReconstruction(newTotalPointsCount, segmentResolution)) {
            reconstructMesh(lightningBranches, newTotalPointsCount, segmentResolution);
        }

        var vertexIndex = 0;
        var pointIndex = 0;
        for (var lightningBranch : lightningBranches) {
            var branchSegmentRadius = segmentRadius * lightningBranch.widthPercentage;
            for (var lightningPoint : lightningBranch.lightningPoints) {
                var radius = pointRadii == null
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
        for (var i = 0; i < indexCount; i++) {
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
        var right = lightningPoint.rightAxis;
        var up = lightningPoint.upAxis;
        for (var i = 0; i < segmentResolution; i++) {
            var offsetX = segmentRadius * ringCos[i];
            var offsetY = segmentRadius * ringSin[i];

            var base = (vertexIndex + i) * 3;
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
        for (var i = 0; i < segmentResolution; i++) {
            var pointPercentage = (float) i / (segmentResolution - 1);
            var angle = DEG_TO_RAD * pointPercentage * 360f;
            ringCos[i] = Mth.cos(angle);
            ringSin[i] = Mth.sin(angle);
        }
    }

    private void reconstructMesh(List<LightningBranch> lightningBranches, int pointsCount, int segmentResolution) {
        totalPointsCount = pointsCount;
        this.segmentResolution = segmentResolution;
        ensureRingTables();

        var totalVerticesCount = 0;
        var totalTrianglesCount = 0;
        for (var lightningBranch : lightningBranches) {
            var branchPointsCount = lightningBranch.lightningPoints.size();
            totalVerticesCount += segmentResolution * branchPointsCount;
            totalTrianglesCount += 2 * segmentResolution * (branchPointsCount - 1);
        }

        vertexCount = totalVerticesCount;
        indexCount = totalTrianglesCount * 3;
        positions = new float[totalVerticesCount * 3];
        uvs = new float[totalVerticesCount * 2];
        indices = new int[indexCount];

        var vertexOffset = 0;
        var trianglesOffset = 0;
        for (var lightningBranch : lightningBranches) {
            createMeshData(lightningBranch, vertexOffset, trianglesOffset);
            var branchPointsCount = lightningBranch.lightningPoints.size();
            vertexOffset += branchPointsCount * segmentResolution;
            trianglesOffset += 3 * 2 * (branchPointsCount - 1) * segmentResolution;
        }
    }

    private void createMeshData(LightningBranch lightningBranch, int vertexOffset, int trianglesOffset) {
        var lightningPoints = lightningBranch.lightningPoints;
        var pointsCount = lightningPoints.size();
        var tri = trianglesOffset;

        for (var i = 0; i < pointsCount; i++) {
            for (var counter = 0; counter < segmentResolution; counter++) {
                var uvIndex = (vertexOffset + i * segmentResolution + counter) * 2;
                uvs[uvIndex] = lightningBranch.intensityPercentage;
                uvs[uvIndex + 1] = 0f;
            }

            if (i > 0) {
                tri = addTriangleIndices(tri, vertexOffset, i);
            }
        }
    }

    private int addTriangleIndices(int tri, int vertexIndexOffset, int pointIndex) {
        var previousSegmentFirstIndex = (pointIndex - 1) * segmentResolution;
        var previousSegmentLastIndex = previousSegmentFirstIndex + segmentResolution - 1;
        var currentSegmentFirstIndex = previousSegmentLastIndex + 1;
        var currentSegmentLastIndex = currentSegmentFirstIndex + segmentResolution - 1;

        for (var i = 0; i < segmentResolution; i++) {
            var previousSegmentFirst = previousSegmentFirstIndex + i;
            var previousSegmentSecond = previousSegmentFirst + 1;
            if (previousSegmentSecond > previousSegmentLastIndex) {
                previousSegmentSecond -= segmentResolution;
            }

            var currentSegmentFirst = currentSegmentFirstIndex + i;
            var currentSegmentSecond = currentSegmentFirst + 1;
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
