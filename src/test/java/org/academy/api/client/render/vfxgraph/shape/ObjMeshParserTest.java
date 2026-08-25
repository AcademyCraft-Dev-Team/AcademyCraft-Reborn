package org.academy.api.client.render.vfxgraph.shape;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ObjMeshParserTest {
    @Test
    void parsesTriangle() {
        var triangles = ObjMeshParser.parse("""
                v 0 0 0
                v 1 0 0
                v 0 1 0
                f 1 2 3
                """);
        assertArrayEquals(new float[] { 0, 0, 0, 1, 0, 0, 0, 1, 0 }, triangles, 1e-6f);
    }

    @Test
    void triangulatesQuadWithFan() {
        var triangles = ObjMeshParser.parse("""
                v 0 0 0
                v 1 0 0
                v 1 1 0
                v 0 1 0
                f 1 2 3 4
                """);
        assertArrayEquals(new float[] {
                0, 0, 0, 1, 0, 0, 1, 1, 0,
                0, 0, 0, 1, 1, 0, 0, 1, 0
        }, triangles, 1e-6f);
    }

    @Test
    void handlesTextureCoordinateIndices() {
        var triangles = ObjMeshParser.parse("""
                v 0 0 0
                v 1 0 0
                v 0 1 0
                f 1/1 2/1 3/1
                """);
        assertArrayEquals(new float[] { 0, 0, 0, 1, 0, 0, 0, 1, 0 }, triangles, 1e-6f);
    }

    @Test
    void handlesNegativeIndicesAndComments() {
        var triangles = ObjMeshParser.parse("""
                # comment
                v 0 0 0
                v 1 0 0
                v 0 1 0
                f -3 -2 -1
                """);
        assertArrayEquals(new float[] { 0, 0, 0, 1, 0, 0, 0, 1, 0 }, triangles, 1e-6f);
    }

    @Test
    void rejectsOutOfRangeIndex() {
        assertThrows(IllegalArgumentException.class, () -> ObjMeshParser.parse("""
                v 0 0 0
                v 1 0 0
                v 0 1 0
                f 1 2 9
                """));
    }

    @Test
    void rejectsNoFaces() {
        assertThrows(IllegalArgumentException.class, () -> ObjMeshParser.parse("v 0 0 0\nv 1 0 0\n"));
    }
}
