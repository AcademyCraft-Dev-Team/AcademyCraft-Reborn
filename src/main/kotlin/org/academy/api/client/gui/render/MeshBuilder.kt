package org.academy.api.client.gui.render

import com.mojang.blaze3d.IndexType
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.util.ARGB
import org.lwjgl.system.MemoryUtil

class MeshBuilder(
    private val buffer: ByteBufferBuilder,
    private val topology: PrimitiveTopology,
    private val format: VertexFormat
) : VertexWriter {
    private val vertexSize = format.vertexSize
    private var vertexPointer = -1L
    private var vertices = 0
    private var writeOffset = 0

    override fun beginVertex() {
        vertexPointer = buffer.reserve(vertexSize)
        vertices++
        writeOffset = 0
    }

    override fun putFloat(value: Float) {
        MemoryUtil.memPutFloat(vertexPointer + writeOffset, value)
        writeOffset += 4
    }

    override fun putVec2f(x: Float, y: Float) {
        MemoryUtil.memPutFloat(vertexPointer + writeOffset, x)
        MemoryUtil.memPutFloat(vertexPointer + writeOffset + 4, y)
        writeOffset += 8
    }

    override fun putVec3f(x: Float, y: Float, z: Float) {
        MemoryUtil.memPutFloat(vertexPointer + writeOffset, x)
        MemoryUtil.memPutFloat(vertexPointer + writeOffset + 4, y)
        MemoryUtil.memPutFloat(vertexPointer + writeOffset + 8, z)
        writeOffset += 12
    }

    override fun putVec4f(x: Float, y: Float, z: Float, w: Float) {
        MemoryUtil.memPutFloat(vertexPointer + writeOffset, x)
        MemoryUtil.memPutFloat(vertexPointer + writeOffset + 4, y)
        MemoryUtil.memPutFloat(vertexPointer + writeOffset + 8, z)
        MemoryUtil.memPutFloat(vertexPointer + writeOffset + 12, w)
        writeOffset += 16
    }

    override fun putColor(r: Int, g: Int, b: Int, a: Int) {
        MemoryUtil.memPutByte(vertexPointer + writeOffset, r.toByte())
        MemoryUtil.memPutByte(vertexPointer + writeOffset + 1, g.toByte())
        MemoryUtil.memPutByte(vertexPointer + writeOffset + 2, b.toByte())
        MemoryUtil.memPutByte(vertexPointer + writeOffset + 3, a.toByte())
        writeOffset += 4
    }

    override fun putColor(argb: Int) {
        val abgr = ARGB.toABGR(argb)
        MemoryUtil.memPutInt(vertexPointer + writeOffset, abgr)
        writeOffset += 4
    }

    fun build(): MeshData? {
        if (vertices == 0) return null
        val result = buffer.build() ?: return null
        val indices = topology.indexCount(vertices)
        val indexType = IndexType.least(vertices)
        return MeshData(result, MeshData.DrawState(format, vertices, indices, topology, indexType))
    }
}
