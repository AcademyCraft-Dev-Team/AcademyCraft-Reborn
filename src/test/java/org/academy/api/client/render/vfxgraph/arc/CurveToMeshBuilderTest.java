package org.academy.api.client.render.vfxgraph.arc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CurveToMeshBuilderTest {

    @Test
    void buildSimpleArc() {
        var arc = new ArcCurve();
        CurveGenerator.generate(arc, 0, 0, 0, 0, 1, 0, 0.01f, 12,
                0.8f, 0.2f, 0.4f, 1f, 1.0f, 42L,
                0, 0, 0, 0.3f, 0.35f, 0.6f, 2.0f);

        var mesh = CurveToMeshBuilder.build(arc, 8, 0.8f, 0.2f, 0.4f, 1f, 0.6f);

        // 12 control points × 8 verts/ring = 96 vertices
        assertEquals(12 * 8, mesh.vertexCount());
        // (12-1) × 8 × 6 = 528 indices
        assertEquals(11 * 8 * 6, mesh.indexCount());
        assertEquals(mesh.vertexCount() * CurveToMeshBuilder.FLOATS_PER_VERTEX * 4, mesh.vertexBytes());
    }

    @Test
    void buildEmptyArcReturnsEmpty() {
        var arc = new ArcCurve();
        var mesh = CurveToMeshBuilder.build(arc, 8, 1, 1, 1, 1, 0.6f);
        assertEquals(0, mesh.vertexCount());
        assertEquals(0, mesh.indexCount());
    }

    @Test
    void buildSinglePointArcReturnsEmpty() {
        var arc = new ArcCurve();
        arc.addPoint(0, 0, 0, 0.01f, 0);
        var mesh = CurveToMeshBuilder.build(arc, 8, 1, 1, 1, 1, 0.6f);
        assertEquals(0, mesh.vertexCount());
    }

    @Test
    void vertexBufferContainsValidData() {
        var arc = new ArcCurve();
        arc.addPoint(0, 0, 0, 0.01f, 0);
        arc.addPoint(0, 1, 0, 0.01f, 0);
        arc.setColor(1, 0.5f, 0.2f, 1f);

        var mesh = CurveToMeshBuilder.build(arc, 4, 1, 0.5f, 0.2f, 1f, 0.6f);

        // 2 points × 4 verts/ring = 8 vertices
        assertEquals(8, mesh.vertexCount());

        // Read first vertex: position should be near origin
        var buf = mesh.vertexBuffer();
        float px = buf.getFloat(0);
        float py = buf.getFloat(4);
        float pz = buf.getFloat(8);
        // Normal should be near unit length
        float nx = buf.getFloat(12);
        float ny = buf.getFloat(16);
        float nz = buf.getFloat(20);
        float nlen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        assertEquals(1.0f, nlen, 0.01f, "Normal should be unit length");
    }

    @Test
    void buildWithBranching() {
        var arc = new ArcCurve();
        CurveGenerator.generate(arc, 0, 0, 0, 0, 1, 0, 0.01f, 12,
                0.8f, 0.2f, 0.4f, 1f, 1.0f, 42L,
                1, 2, 1.57f, 0.3f, 0.35f, 0.6f, 2.0f);

        // With branching, arc should have >12 points
        assertTrue(arc.size() > 12);

        var mesh = CurveToMeshBuilder.build(arc, 8, 0.8f, 0.2f, 0.4f, 1f, 0.6f);
        assertTrue(mesh.vertexCount() > 12 * 8, "Branching should produce more vertices");
        assertTrue(mesh.indexCount() > 0);
    }

    @Test
    void indexValuesAreValid() {
        var arc = new ArcCurve();
        arc.addPoint(0, 0, 0, 0.01f, 0);
        arc.addPoint(0, 1, 0, 0.01f, 0);

        var mesh = CurveToMeshBuilder.build(arc, 4, 1, 1, 1, 1, 0.6f);

        for (int i = 0; i < mesh.indexCount(); i++) {
            assertTrue(mesh.indices()[i] >= 0 && mesh.indices()[i] < mesh.vertexCount(),
                    "Index out of range: " + mesh.indices()[i]);
        }
    }

    /** 互不相连的分段（不同 segment）必须各自成 run，不得被缝成一根管（顶点连接问题回归）。 */
    @Test
    void disconnectedSegmentsAreBuiltAsSeparateRuns() {
        var arc = new ArcCurve();
        // run 0：y=0..1；run 1：起点 (10,0,0)（与 run 0 相距 10，若被缝合会出现跨越空间的管）
        arc.addPoint(0, 0, 0, 0.01f, 0, 0);
        arc.addPoint(0, 1, 0, 0.01f, 0, 0);
        arc.addPoint(10, 0, 0, 0.01f, 0, 1);
        arc.addPoint(10, 1, 0, 0.01f, 0, 1);

        var mesh = CurveToMeshBuilder.build(arc, 4, 1, 1, 1, 1, 0.6f);

        // 2 runs × 2 点 × 4 环 = 16 顶点；2 runs × 1 对 × 4 × 6 = 48 索引（而非 3 对 = 72）
        assertEquals(16, mesh.vertexCount());
        assertEquals(48, mesh.indexCount());

        // 前 24 个索引只引用 run0 顶点 [0,8)，后 24 个只引用 run1 顶点 [8,16)
        for (int i = 0; i < 24; i++) {
            assertTrue(mesh.indices()[i] >= 0 && mesh.indices()[i] < 8,
                    "run0 索引越界: " + mesh.indices()[i]);
        }
        for (int i = 24; i < 48; i++) {
            assertTrue(mesh.indices()[i] >= 8 && mesh.indices()[i] < 16,
                    "run1 索引越界: " + mesh.indices()[i]);
        }
    }

    /** CurveGenerator 的每根分支应获得独立 segment id → 建管时分成独立 run。 */
    @Test
    void generatorAssignsDistinctSegmentPerBranch() {
        var arc = new ArcCurve();
        CurveGenerator.generate(arc, 0, 0, 0, 0, 1, 0, 0.01f, 12,
                0.8f, 0.2f, 0.4f, 1f, 1.0f, 42L,
                1, 2, 1.57f, 0.3f, 0.35f, 0.6f, 2.0f);

        // 主弧 + 2 分支 = 3 个不同 segment
        var segments = new java.util.HashSet<Integer>();
        for (int i = 0; i < arc.size(); i++) {
            segments.add(arc.segment(i));
        }
        assertEquals(3, segments.size());

        var mesh = CurveToMeshBuilder.build(arc, 8, 0.8f, 0.2f, 0.4f, 1f, 0.6f);
        // 若被缝成一根管，会多出 2 对连接（主弧→分支1、分支1→分支2）的索引
        // 3 runs：主弧(11 对) + 2 分支(各 11 对) = 33 对 × 8 × 6 = 1584
        assertEquals(33 * 8 * 6, mesh.indexCount());
    }
}
