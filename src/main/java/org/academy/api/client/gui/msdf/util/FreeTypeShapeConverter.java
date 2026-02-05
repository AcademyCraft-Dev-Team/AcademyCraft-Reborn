package org.academy.api.client.gui.msdf.util;

import org.academy.api.client.gui.msdf.core.*;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Outline;
import org.lwjgl.util.freetype.FT_Outline_Funcs;
import org.lwjgl.util.freetype.FT_Vector;
import org.lwjgl.util.freetype.FreeType;

import static org.lwjgl.system.MemoryUtil.NULL;

public final class FreeTypeShapeConverter {
    private FreeTypeShapeConverter() {
    }

    public static Shape loadShapeFromFtOutline(FT_Outline outline, MemoryStack stack) {
        var shape = new Shape();
        final @Nullable Contour[] current = {null};
        final var start = new Point2[]{new Point2()};

        var funcs = FT_Outline_Funcs.calloc(stack);
        funcs.move_to((to, _) -> {
            current[0] = shape.addContour();
            start[0] = new Point2(FT_Vector.nx(to), FT_Vector.ny(to));
            return 0;
        });
        funcs.line_to((to, _) -> {
            var p0 = current[0].edges.isEmpty() ? start[0] : current[0].edges.getLast().get().point(1);
            current[0].addEdge(new EdgeHolder(p0, new Point2(FT_Vector.nx(to), FT_Vector.ny(to)), EdgeColor.WHITE));
            return 0;
        });
        funcs.conic_to((control, to, _) -> {
            var p0 = current[0].edges.isEmpty() ? start[0] : current[0].edges.getLast().get().point(1);
            current[0].addEdge(new EdgeHolder(p0, new Point2(FT_Vector.nx(control), FT_Vector.ny(control)), new Point2(FT_Vector.nx(to), FT_Vector.ny(to)), EdgeColor.WHITE));
            return 0;
        });
        funcs.cubic_to((c1, c2, to, _) -> {
            var p0 = current[0].edges.isEmpty() ? start[0] : current[0].edges.getLast().get().point(1);
            current[0].addEdge(new EdgeHolder(p0, new Point2(FT_Vector.nx(c1), FT_Vector.ny(c1)), new Point2(FT_Vector.nx(c2), FT_Vector.ny(c2)), new Point2(FT_Vector.nx(to), FT_Vector.ny(to)), EdgeColor.WHITE));
            return 0;
        });

        FreeType.FT_Outline_Decompose(outline, funcs, NULL);
        return shape;
    }
}