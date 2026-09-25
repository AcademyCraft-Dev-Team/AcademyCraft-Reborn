package org.academy.internal.client.hud

import net.minecraft.client.Minecraft
import net.minecraft.util.Mth

object HudLayout {
    const val MIN_SCALE = 0.5f
    const val MAX_SCALE = 2.0f

    enum class Anchor {
        TOP_LEFT,
        TOP_RIGHT,
        CENTER_LEFT,
        CENTER_RIGHT
    }

    enum class Region(
        val configKey: String,
        val nameKey: String,
        val nominalWidth: Float,
        val nominalHeight: Float,
        private val anchor: Anchor,
        private val defaultOffsetX: Float,
        private val defaultOffsetY: Float
    ) {
        TOGGLE_STATUS(
            "toggle_status",
            "hud.academy.layout.region.ability_status",
            140f,
            75f,
            Anchor.TOP_LEFT,
            8f,
            8f
        ),
        MENTAL_CONTROL(
            "mental_control",
            "hud.academy.layout.region.mental_control",
            142f,
            146f,
            Anchor.CENTER_LEFT,
            8f,
            0f
        ),
        CP(
            "cp",
            "hud.academy.layout.region.cp",
            240f,
            27f,
            Anchor.TOP_RIGHT,
            -4f,
            4f
        ),
        SKILL_WHEEL(
            "skill_wheel",
            "hud.academy.layout.region.skill_name",
            0f,
            128f,
            Anchor.CENTER_RIGHT,
            0f,
            0f
        );

        var translateX: Float
            get() = HudLayoutConfig.state(configKey).translateX
            set(value) {
                HudLayoutConfig.state(configKey).translateX = value
            }

        var translateY: Float
            get() = HudLayoutConfig.state(configKey).translateY
            set(value) {
                HudLayoutConfig.state(configKey).translateY = value
            }

        var scaleXY: Float
            get() = validScale(HudLayoutConfig.state(configKey).scaleXY)
            set(value) {
                HudLayoutConfig.state(configKey).scaleXY = validScale(value)
            }

        var hidden: Boolean
            get() = HudLayoutConfig.state(configKey).hidden
            set(value) {
                HudLayoutConfig.state(configKey).hidden = value
            }

        fun baseRect(minecraft: Minecraft): Rect {
            val live = hudLiveSizes[configKey]?.invoke()
            val logicalWidth = live?.width?.takeIf { it > 0f } ?: nominalWidth
            val logicalHeight = live?.height?.takeIf { it > 0f } ?: nominalHeight
            val width = logicalWidth * scaleXY
            val height = logicalHeight * scaleXY
            val screenWidth = minecraft.window.guiScaledWidth.toFloat()
            val screenHeight = minecraft.window.guiScaledHeight.toFloat()
            return Rect(anchorX(screenWidth, width), anchorY(screenHeight, height), width, height)
        }

        fun rect(minecraft: Minecraft): Rect {
            val base = baseRect(minecraft)
            val screenWidth = minecraft.window.guiScaledWidth.toFloat()
            val screenHeight = minecraft.window.guiScaledHeight.toFloat()
            return Rect(
                Mth.clamp(base.x + translateX, 0f, maxOf(0f, screenWidth - base.width)),
                Mth.clamp(base.y + translateY, 0f, maxOf(0f, screenHeight - base.height)),
                base.width,
                base.height
            )
        }

        fun setTranslate(x: Float, y: Float, minecraft: Minecraft) {
            val base = baseRect(minecraft)
            val screenWidth = minecraft.window.guiScaledWidth.toFloat()
            val screenHeight = minecraft.window.guiScaledHeight.toFloat()
            translateX = Mth.clamp(x, -base.x, maxOf(-base.x, screenWidth - base.width - base.x))
            translateY = Mth.clamp(y, -base.y, maxOf(-base.y, screenHeight - base.height - base.y))
        }

        fun toggleHidden() {
            hidden = !hidden
        }

        fun reset() {
            translateX = 0f
            translateY = 0f
            scaleXY = 1f
            hidden = false
        }

        private fun anchorX(screenWidth: Float, width: Float): Float = when (anchor) {
            Anchor.TOP_LEFT, Anchor.CENTER_LEFT -> defaultOffsetX
            Anchor.TOP_RIGHT, Anchor.CENTER_RIGHT -> screenWidth - width + defaultOffsetX
        }

        private fun anchorY(screenHeight: Float, height: Float): Float = when (anchor) {
            Anchor.TOP_LEFT, Anchor.TOP_RIGHT -> defaultOffsetY
            Anchor.CENTER_LEFT, Anchor.CENTER_RIGHT -> (screenHeight - height) / 2f + defaultOffsetY
        }
    }

    data class Rect(val x: Float, val y: Float, val width: Float, val height: Float) {
        fun contains(px: Double, py: Double): Boolean {
            return px >= x && px <= x + width && py >= y && py <= y + height
        }
    }

    @JvmStatic
    fun resetAll() {
        Region.entries.forEach { it.reset() }
    }

    /**
     * 注册某区域的实际内容尺寸 (逻辑像素, 未乘 [Region.scaleXY])。用于 WRAP_CONTENT 这类没有固定
     * nominal 尺寸的区域: 编辑器描边/拖拽与锚点计算会使用实时尺寸, 传 null 取消注册.
     */
    @JvmStatic
    fun registerLiveSize(configKey: String, provider: (() -> Rect)?) {
        if (provider == null) {
            hudLiveSizes.remove(configKey)
        } else {
            hudLiveSizes[configKey] = provider
        }
    }

    private fun validScale(scale: Float): Float {
        return if (scale.isFinite()) Mth.clamp(scale, MIN_SCALE, MAX_SCALE) else 1f
    }
}

/** 区域内实时内容尺寸 (逻辑像素) 的提供者, 见 [HudLayout.registerLiveSize]. */
private val hudLiveSizes: MutableMap<String, () -> HudLayout.Rect> = HashMap()
