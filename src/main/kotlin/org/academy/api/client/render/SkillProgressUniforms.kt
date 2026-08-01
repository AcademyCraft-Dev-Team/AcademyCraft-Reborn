package org.academy.api.client.render

import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import net.minecraft.client.renderer.DynamicUniformStorage.DynamicUniform
import java.nio.ByteBuffer

class SkillProgressUniforms(val progress: Float) : DynamicUniform {
    override fun write(buffer: ByteBuffer) {
        Std140Builder.intoBuffer(buffer).putFloat(progress)
    }

    companion object {
        val UBO_SIZE: Int = Std140SizeCalculator().putFloat().get()
    }
}
