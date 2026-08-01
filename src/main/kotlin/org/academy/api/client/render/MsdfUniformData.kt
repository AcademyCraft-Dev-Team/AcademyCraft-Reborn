package org.academy.api.client.render

import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import net.minecraft.client.renderer.DynamicUniformStorage.DynamicUniform
import org.joml.Vector4f
import java.nio.ByteBuffer

data class MsdfUniformData(
    val range: Float,
    val thickness: Float,
    val outlineThickness: Float,
    val outlineColor: Vector4f
) : DynamicUniform {
    override fun write(buffer: ByteBuffer) {
        Std140Builder.intoBuffer(buffer)
            .putFloat(range)
            .putFloat(thickness)
            .putFloat(outlineThickness)
            .putVec4(outlineColor)
    }

    companion object {
        val UBO_SIZE: Int = Std140SizeCalculator()
            .putFloat()
            .putFloat()
            .putFloat()
            .putVec4()
            .get()
    }
}
