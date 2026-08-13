package org.academy.desktop.platform

import com.mojang.blaze3d.preprocessor.GlslPreprocessor
import com.mojang.blaze3d.shaders.ShaderType
import net.minecraft.resources.Identifier

/**
 * Blaze3D [com.mojang.blaze3d.shaders.ShaderSource] that resolves GLSL from the
 * classpath (`assets/<ns>/shaders/<path><ext>`) and expands `#moj_import`
 * directives with the vanilla [GlslPreprocessor].
 *
 * Vanilla shaders are hidden from classpath resource lookup by FML's filtered
 * game-content loader, so those are read directly from the vanilla jar (located
 * via the code source of a vanilla class).
 */
object ClasspathShaderSource {
    fun read(id: Identifier, type: ShaderType): String? {
        val extension = if (type == ShaderType.VERTEX) ".vsh" else ".fsh"
        val raw = readResource(Identifier.fromNamespaceAndPath(id.namespace, "shaders/${id.path}$extension"))
            ?: return null

        val preprocessor = object : GlslPreprocessor() {
            override fun applyImport(isRelative: Boolean, path: String) = if (isRelative) {
                readResource(Identifier.fromNamespaceAndPath(id.namespace, "shaders/include/$path"))
            } else {
                readResource(Identifier.parse(path).withPrefix("shaders/include/"))
            }
        }
        return preprocessor.process(raw).joinToString("")
    }

    private fun readResource(location: Identifier): String? {
        readFromClasspath(location)?.let { return it }
        return readFromVanillaJar(location)
    }

    private fun readFromClasspath(location: Identifier): String? {
        return ClasspathShaderSource.javaClass
            .getResourceAsStream("/assets/${location.namespace}/${location.path}")
            ?.use { stream -> stream.bufferedReader().readText() }
    }

    private fun readFromVanillaJar(location: Identifier): String? {
        return try {
            val codeSource = net.minecraft.SharedConstants::class.java.protectionDomain.codeSource ?: return null
            val locationUrl = codeSource.location
            if (locationUrl.protocol != "file") return null
            val jarFile = java.io.File(locationUrl.toURI().path)
            if (!jarFile.isFile) return null
            java.util.zip.ZipFile(jarFile).use { zip ->
                val entry = zip.getEntry("assets/${location.namespace}/${location.path}") ?: return null
                zip.getInputStream(entry).use { stream -> stream.bufferedReader().readText() }
            }
        } catch (e: Exception) {
            null
        }
    }
}
