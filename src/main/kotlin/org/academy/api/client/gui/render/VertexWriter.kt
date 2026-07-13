package org.academy.api.client.gui.render

interface VertexWriter {
    fun beginVertex()
    fun putFloat(value: Float)
    fun putVec2f(x: Float, y: Float)
    fun putVec3f(x: Float, y: Float, z: Float)
    fun putVec4f(x: Float, y: Float, z: Float, w: Float)
    fun putColor(r: Int, g: Int, b: Int, a: Int)
    fun putColor(argb: Int)
}