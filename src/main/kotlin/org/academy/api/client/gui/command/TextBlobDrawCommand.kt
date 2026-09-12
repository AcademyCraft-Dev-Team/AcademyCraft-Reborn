package org.academy.api.client.gui.command

import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.glyph.GlyphReadiness
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.render.VertexWriter
import org.academy.api.client.gui.text.TextBlob
import org.academy.api.client.gui.text.TextShaper
import org.academy.api.client.gui.text.TextShapingOptions
import org.academy.api.client.gui.text.subrun.SubRunContainer
import org.academy.api.client.render.Render

/**
 * 与设备无关的文本记录（对标 Skia 的 `TextBlob` + RecordedDraw）。
 *
 * 只保存文本与外观参数；真正的设备字形在 [expand] 时用 canvas CTM 生成。展开结果按
 * `(deviceOrigin, guiScale, deviceScale, readinessVersion)` 缓存并**拉取式**失效：MSDF 字形就绪后
 * [GlyphReadiness.version] 变化，下一帧自然重建，无需回调或控件失效。
 *
 * - 几何：局部空间，按 [contentScale]（布局缩放）烘焙，CTM 只施一次。
 * - 光栅分辨率：`contentScale × maxScale(CTM)`（Skia strike spec），随最终 CTM 变化。
 */
class TextBlobDrawCommand : DrawCommand(
    Render.RenderPipelines.BITMAP_TEXT,
    emptyList(),
    emptyList()
), ExpandableDrawCommand {
    var text: String = ""
        set(value) { if (field != value) { field = value; cache = null } }
    var fontSize: Float = 0f
        set(value) { if (field != value) { field = value; cache = null } }

    /** 影响 shaping 的样式参数（字号之外）；变化时使 blob 缓存失效。 */
    var shapingOptions: TextShapingOptions = TextShapingOptions.DEFAULT
        set(value) { if (field != value) { field = value; blob = null; blobShaping = null } }

    var thickness: Float = 0f
        set(value) { if (field != value) { field = value; cache = null } }
    var red: Float = 1f
        set(value) { if (field != value) { field = value; cache = null } }
    var green: Float = 1f
        set(value) { if (field != value) { field = value; cache = null } }
    var blue: Float = 1f
        set(value) { if (field != value) { field = value; cache = null } }
    var alpha: Float = 1f
        set(value) { if (field != value) { field = value; cache = null } }

    /** 内容缩放（自动适配 layoutScale）；控件自身缩放经 CTM，不在此处。 */
    var contentScale: Float = 1f
        set(value) { if (field != value) { field = value; cache = null } }
    var revealCodeUnits: Int = Int.MAX_VALUE
        set(value) { if (field != value) { field = value; cache = null } }

    /** AOSP horizontal fading edges (marquee): viewport in block-local units + per-frame strengths. */
    var fadeViewportLeft: Float = 0f
        set(value) { if (field != value) { field = value; cache = null } }
    var fadeViewportWidth: Float = 0f
        set(value) { if (field != value) { field = value; cache = null } }
    var fadeLength: Float = 0f
        set(value) { if (field != value) { field = value; cache = null } }
    var fadeLeftStrength: Float = 0f
        set(value) { if (field != value) { field = value; cache = null } }
    var fadeRightStrength: Float = 0f
        set(value) { if (field != value) { field = value; cache = null } }

    private var cache: List<DrawCommand>? = null
    private var cachedOriginX = Float.NaN
    private var cachedOriginY = Float.NaN
    private var cachedGuiScale = -1f
    private var cachedDeviceScale = -1f
    private var cachedVersion = -1L

    /** Shaping is device-independent, so the AWT-laid-out blob is cached by (text, size, shaping). */
    private var blob: TextBlob? = null
    private var blobText: String? = null
    private var blobFontSize = Float.NaN
    private var blobShaping: TextShapingOptions? = null

    override fun generateVertices(writer: VertexWriter, pose: PoseStack.Pose, alphaMul: Float) {
        // Always expanded into sub-commands before batching; never drawn directly.
    }

    override fun expand(pose: PoseStack.Pose, alphaMul: Float): List<DrawCommand> {
        val matrix = pose.pose()
        // device 原点（Skia deviceOrigin）用于亚像素相位/网格对齐。
        val originX = matrix.m30()
        val originY = matrix.m31()
        val guiScale = UiEnvironment.get().guiScale
        val version = GlyphReadiness.version
        val deviceScale = contentScale * Canvas.maxScale(matrix)

        val cached = cache
        if (cached != null &&
            originX == cachedOriginX && originY == cachedOriginY &&
            guiScale == cachedGuiScale && deviceScale == cachedDeviceScale &&
            version == cachedVersion
        ) {
            return cached
        }

        var shaped = blob
        if (shaped == null || blobText != text || blobFontSize != fontSize || blobShaping != shapingOptions) {
            shaped = TextShaper.shape(text, fontSize, shapingOptions)
            blob = shaped
            blobText = text
            blobFontSize = fontSize
            blobShaping = shapingOptions
        }

        val generated = SubRunContainer.make(
            shaped, fontSize, thickness, red, green, blue, alpha,
            contentScale, deviceScale, guiScale,
            originX, originY, revealCodeUnits,
            fadeViewportLeft, fadeViewportWidth, fadeLength, fadeLeftStrength, fadeRightStrength
        )
        cache = generated
        cachedOriginX = originX
        cachedOriginY = originY
        cachedGuiScale = guiScale
        cachedDeviceScale = deviceScale
        cachedVersion = version
        return generated
    }
}
