package org.academy.api.client.gui.msdf.util

import org.academy.api.client.gui.msdf.core.Contour
import org.academy.api.client.gui.msdf.core.EdgeSegment
import org.academy.api.client.gui.msdf.core.Point
import org.academy.api.client.gui.msdf.core.Shape
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.freetype.FT_Outline
import org.lwjgl.util.freetype.FT_Outline_Funcs
import org.lwjgl.util.freetype.FT_Vector
import org.lwjgl.util.freetype.FreeType

object FreeTypeShapeConverter {
    fun loadShapeFromFtOutline(outline: FT_Outline, stack: MemoryStack): Shape {
        val contours = mutableListOf<Contour>()
        var currentEdges = mutableListOf<EdgeSegment>()
        var startPoint: Point? = null

        val func = FT_Outline_Funcs.calloc(stack)
        func.move_to { to, _ ->
            if (currentEdges.isNotEmpty()) contours.add(Contour(currentEdges.toList()))

            currentEdges = mutableListOf()
            startPoint = Point(FT_Vector.nx(to).toDouble(), FT_Vector.ny(to).toDouble())
            0
        }
        func.line_to { to, _ ->
            val p0 = if (currentEdges.isEmpty()) startPoint ?: error("move_to must be called before line_to")
            else currentEdges.last().endPoint

            val p1 = Point(FT_Vector.nx(to).toDouble(), FT_Vector.ny(to).toDouble())
            currentEdges.add(EdgeSegment.create(p0, p1))
            0
        }
        func.conic_to { control, to, _ ->
            val p0 = if (currentEdges.isEmpty()) startPoint ?: error("move_to must be called before conic_to")
            else currentEdges.last().endPoint

            val p1 = Point(FT_Vector.nx(control).toDouble(), FT_Vector.ny(control).toDouble())
            val p2 = Point(FT_Vector.nx(to).toDouble(), FT_Vector.ny(to).toDouble())
            currentEdges.add(EdgeSegment.create(p0, p1, p2))
            0
        }
        func.cubic_to { c1, c2, to, _ ->
            val p0 = if (currentEdges.isEmpty()) startPoint ?: error("move_to must be called before cubic_to")
            else currentEdges.last().endPoint

            val p1 = Point(FT_Vector.nx(c1).toDouble(), FT_Vector.ny(c1).toDouble())
            val p2 = Point(FT_Vector.nx(c2).toDouble(), FT_Vector.ny(c2).toDouble())
            val p3 = Point(FT_Vector.nx(to).toDouble(), FT_Vector.ny(to).toDouble())
            currentEdges.add(EdgeSegment.create(p0, p1, p2, p3))
            0
        }

        FreeType.FT_Outline_Decompose(outline, func, MemoryUtil.NULL)

        if (currentEdges.isNotEmpty()) contours.add(Contour(currentEdges.toList()))

        return Shape(contours)
    }
}