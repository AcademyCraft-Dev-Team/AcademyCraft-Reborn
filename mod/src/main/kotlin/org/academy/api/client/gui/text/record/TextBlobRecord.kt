package org.academy.api.client.gui.text.record

import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.command.ExpandableDrawCommand
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.render.VertexWriter
import org.academy.api.client.gui.text.atlas.GlyphSignal
import org.academy.api.client.gui.text.model.TextShapingOptions
import org.academy.api.client.gui.text.shape.ShapingCache
import org.academy.api.client.render.Render

class TextBlobRecord : DrawCommand(
    Render.RenderPipelines.BITMAP_TEXT,
    emptyList(),
    emptyList()
), ExpandableDrawCommand {
    var text: String = ""
        set(value) {
            if (field != value) {
                field = value; cache = null
            }
        }
    var fontSize: Float = 0f
        set(value) {
            if (field != value) {
                field = value; cache = null
            }
        }

    var shapingOptions: TextShapingOptions = TextShapingOptions.DEFAULT
        set(value) {
            if (field != value) {
                field = value; cache = null
            }
        }

    var thickness: Float = 0f
        set(value) {
            if (field != value) {
                field = value; cache = null
            }
        }
    var red: Float = 1f
        set(value) {
            if (field != value) {
                field = value; cache = null
            }
        }
    var green: Float = 1f
        set(value) {
            if (field != value) {
                field = value; cache = null
            }
        }
    var blue: Float = 1f
        set(value) {
            if (field != value) {
                field = value; cache = null
            }
        }
    var alpha: Float = 1f
        set(value) {
            if (field != value) {
                field = value; cache = null
            }
        }

    var contentScale: Float = 1f
        set(value) {
            if (field != value) {
                field = value; cache = null
            }
        }
    var revealCodeUnits: Int = Int.MAX_VALUE
        set(value) {
            if (field != value) {
                field = value; cache = null
            }
        }

    var fadeViewportLeft: Float = 0f
        set(value) {
            if (field != value) {
                field = value; cache = null
            }
        }
    var fadeViewportWidth: Float = 0f
        set(value) {
            if (field != value) {
                field = value; cache = null
            }
        }
    var fadeLength: Float = 0f
        set(value) {
            if (field != value) {
                field = value; cache = null
            }
        }
    var fadeLeftStrength: Float = 0f
        set(value) {
            if (field != value) {
                field = value; cache = null
            }
        }
    var fadeRightStrength: Float = 0f
        set(value) {
            if (field != value) {
                field = value; cache = null
            }
        }

    private var cache: List<DrawCommand>? = null
    private var cachedGuiScale = -1f
    private var cachedDeviceScale = -1f
    private var cachedVersion = -1L

    override fun generateVertices(writer: VertexWriter, pose: PoseStack.Pose, alphaMul: Float) {
    }

    override fun expand(pose: PoseStack.Pose, alphaMul: Float): List<DrawCommand> {
        val matrix = pose.pose()
        val guiScale = UiEnvironment.get().guiScale
        val version = GlyphSignal.version
        val deviceScale = contentScale * Canvas.maxScale(matrix)

        val cached = cache
        if (cached != null &&
            guiScale == cachedGuiScale && deviceScale == cachedDeviceScale &&
            version == cachedVersion
        ) {
            return cached
        }

        val shaped = ShapingCache.blob(text, fontSize, shapingOptions)

        val generated = SubRunBuilder.make(
            shaped, fontSize, thickness, red, green, blue, alpha,
            contentScale, deviceScale, guiScale,
            revealCodeUnits,
            fadeViewportLeft, fadeViewportWidth, fadeLength, fadeLeftStrength, fadeRightStrength
        )
        cache = generated
        cachedGuiScale = guiScale
        cachedDeviceScale = deviceScale
        cachedVersion = version
        return generated
    }
}
