package org.academy.api.client.hud.ability

import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.Minecraft
import net.minecraft.locale.Language
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge
import org.academy.api.client.ability.AbilitySystemClient
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.UiContext
import org.academy.api.client.gui.widget.FillWidget
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.LabelWidget
import org.academy.api.client.gui.widget.LinearLayoutWidget
import org.academy.api.client.gui.widget.ProgressBarWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.api.client.gui.widget.WidgetContext
import org.academy.api.client.vanilla.ResizeDisplayEvent
import org.academy.internal.client.ability.mentalout.MentaloutRosterClientState
import org.academy.internal.client.ability.mentalout.MentaloutRosterClientState.Entry
import org.academy.internal.client.hud.HudLayout
import org.academy.internal.common.ability.AbilityCategories
import java.util.Locale

/** Compact left-side overview of the active Mentalout control roster. */
class ControlledTargetsHud private constructor() {
    private val context = Context()
    private val uiContext = UiContext()

    fun perform(mouseX: Double, mouseY: Double, deltaPartialTick: Float) {
        uiContext.perform(context.get(), mouseX, mouseY, deltaPartialTick)
    }

    fun render(target: RenderTarget) {
        uiContext.upload(target, false)
        context.get().invalidate()
    }

    @SubscribeEvent
    fun onTick(@Suppress("unused") event: ClientTickEvent.Post) {
        if (Minecraft.getInstance().player == null) {
            MentaloutRosterClientState.clearLocal()
        } else {
            MentaloutRosterClientState.tick()
        }
        context.get().tick()
    }

    @SubscribeEvent
    fun onLoggingOut(@Suppress("unused") event: ClientPlayerNetworkEvent.LoggingOut) {
        MentaloutRosterClientState.clearLocal()
    }

    @SubscribeEvent
    fun onResizeDisplay(@Suppress("unused") event: ResizeDisplayEvent) {
        context.get().requestLayout()
    }

    private class Context : WidgetContext {
        private val panel = FrameLayoutWidget()
        private val content = LinearLayoutWidget()
        private val root = object : FrameLayoutWidget() {
            override fun tick() {
                applyHudLayout()
                refresh()
                super.tick()
            }
        }
        private var cachedSignature: String? = null

        init {
            panel.visibility = Widget.Visibility.GONE
            panel.origin = 0f
            panel.layoutParams = FrameLayoutWidget.LayoutParams().size(PANEL_WIDTH, PANEL_HEIGHT)
            panel.addChild("background", FillWidget(PANEL_BACKGROUND).also {
                it.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            })

            content.orientation = Orientation.VERTICAL
            content.spacing = 1f
            content.layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .padding(5f, 4f, 5f, 4f)
            panel.addChild("content", content)
            root.addChild("mental_control", panel)
        }

        override fun get(): WidgetContainer = root

        private fun applyHudLayout() {
            val region = HudLayout.Region.MENTAL_CONTROL
            val rect = region.rect(Minecraft.getInstance())
            panel.translationX = rect.x()
            panel.translationY = rect.y()
            panel.scale = region.scale()
        }

        private fun refresh() {
            val state = MentaloutRosterClientState.snapshot()
            val isMentalout = Minecraft.getInstance().player != null &&
                    AbilitySystemClient.getCategory() === AbilityCategories.MENTALOUT.get()
            val visible = isMentalout && state.entries().isNotEmpty()
            val nearest = if (visible) {
                selectNearest(state.entries())
            } else {
                emptyList()
            }
            val signature = buildString {
                append(isMentalout).append('|').append(state.entries().size)
                append('|').append(state.stuporCp()).append('|').append(state.impressionCp())
                nearest.forEach { entry ->
                    append('|').append(entry.targetUuid()).append(':').append(entry.entityTypeId())
                    append(':').append(entry.displayName()).append(':').append(entry.health())
                    append(':').append(entry.maxHealth()).append(':').append(entry.distance())
                    append(':').append(entry.support()).append(':').append(entry.flags())
                    append(':').append(entry.misidentificationTicks())
                }
            }
            if (signature == cachedSignature) return
            cachedSignature = signature
            panel.visibility = if (visible) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
            content.clearChildren()
            if (!visible) return

            content.addChild("header", createHeader(state.entries().size))
            nearest.forEachIndexed { index, entry ->
                content.addChild("target_$index", createTargetRow(entry, index))
            }
            repeat(MAX_VISIBLE_TARGETS - nearest.size) { index ->
                content.addChild("spacer_$index", createSpacer())
            }
            content.addChild(
                "footer",
                createFooter(
                    (state.entries().size - MAX_VISIBLE_TARGETS).coerceAtLeast(0),
                    state.stuporCp(),
                    state.impressionCp()
                )
            )
        }

        private fun selectNearest(entries: List<Entry>): List<Entry> {
            val nearest = ArrayList<Entry>(MAX_VISIBLE_TARGETS)
            entries.forEach { entry ->
                var low = 0
                var high = nearest.size
                while (low < high) {
                    val middle = (low + high) ushr 1
                    if (NEAREST_COMPARATOR.compare(nearest[middle], entry) <= 0) {
                        low = middle + 1
                    } else {
                        high = middle
                    }
                }
                if (low < MAX_VISIBLE_TARGETS) {
                    nearest.add(low, entry)
                    if (nearest.size > MAX_VISIBLE_TARGETS) nearest.removeAt(nearest.lastIndex)
                }
            }
            return nearest
        }

        private fun createHeader(total: Int): Widget {
            val header = LinearLayoutWidget()
            header.orientation = Orientation.HORIZONTAL
            header.layoutParams = LinearLayoutWidget.LayoutParams().size(CONTENT_WIDTH, HEADER_HEIGHT)
            header.addChild(
                "title",
                label(
                    text("hud.academy.mental_control.title"),
                    8f,
                    CONTENT_WIDTH - 32f,
                    HEADER_HEIGHT,
                    TEXT_PRIMARY
                )
            )
            header.addChild(
                "count",
                label(total.toString(), 8f, 32f, HEADER_HEIGHT, ACCENT).also {
                    it.layoutParams.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                }
            )
            return header
        }

        private fun createTargetRow(entry: Entry, index: Int): Widget {
            val row = FrameLayoutWidget()
            row.layoutParams = LinearLayoutWidget.LayoutParams().size(CONTENT_WIDTH, TARGET_ROW_HEIGHT)
            row.addChild("background", FillWidget(if (index % 2 == 0) ROW_EVEN else ROW_ODD).also {
                it.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            })

            val body = LinearLayoutWidget()
            body.orientation = Orientation.VERTICAL
            body.spacing = 0f
            body.layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .padding(3f, 1f, 3f, 1f)
            row.addChild("body", body)

            val top = LinearLayoutWidget()
            top.orientation = Orientation.HORIZONTAL
            top.spacing = 2f
            top.layoutParams = LinearLayoutWidget.LayoutParams().size(INNER_WIDTH, 6.5f)
            top.addChild("name", label(entry.displayName(), 6.5f, 78f, 6.5f, TEXT_PRIMARY))
            top.addChild("distance", label(distance(entry.distance()), 6f, 30f, 6.5f, TEXT_SECONDARY))
            top.addChild(
                "support",
                label(support(entry.support()), 5.8f, 38f, 6.5f, supportColor(entry.support()))
            )
            body.addChild("top", top)

            val details = LinearLayoutWidget()
            details.orientation = Orientation.HORIZONTAL
            details.spacing = 2f
            details.layoutParams = LinearLayoutWidget.LayoutParams().size(INNER_WIDTH, 6f)
            details.addChild("type", label(entityType(entry.entityTypeId()), 5.5f, 67f, 6f, TEXT_MUTED))
            details.addChild("health", label(health(entry), 5.5f, 40f, 6f, healthColor(entry)))
            details.addChild("effects", label(effects(entry), 5.5f, 39f, 6f, ACCENT))
            body.addChild("details", details)

            val healthBar = ProgressBarWidget()
                .setMax(if (entry.maxHealth() > 0f) entry.maxHealth() else 1f)
                .setProgress(entry.health())
                .setBackgroundColor(HEALTH_BACKGROUND)
                .setProgressColor(healthColor(entry))
            healthBar.layoutParams = LinearLayoutWidget.LayoutParams().size(INNER_WIDTH, 2f)
            body.addChild("health_bar", healthBar)
            return row
        }

        private fun createSpacer(): Widget {
            return FillWidget(ROW_EMPTY).also {
                it.layoutParams = LinearLayoutWidget.LayoutParams().size(CONTENT_WIDTH, TARGET_ROW_HEIGHT)
            }
        }

        private fun createFooter(hidden: Int, stuporCp: Int, impressionCp: Int): Widget {
            val hiddenText = if (hidden > 0) "+$hidden | " else ""
            val value = text(
                "hud.academy.mental_control.cp",
                hiddenText,
                stuporCp,
                impressionCp
            )
            return label(value, 6f, CONTENT_WIDTH, FOOTER_HEIGHT, TEXT_SECONDARY)
        }
    }

    companion object {
        const val PANEL_WIDTH = 168f
        const val PANEL_HEIGHT = 184f
        private const val CONTENT_WIDTH = 158f
        private const val INNER_WIDTH = 150f
        private const val HEADER_HEIGHT = 12f
        private const val TARGET_ROW_HEIGHT = 18f
        private const val FOOTER_HEIGHT = 11f
        private const val MAX_VISIBLE_TARGETS = 8

        private val NEAREST_COMPARATOR = compareBy<Entry> { it.distance() }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName() }
            .thenBy { it.targetUuid().toString() }

        private const val PANEL_BACKGROUND = 0x98D5D9D8.toInt()
        private const val ROW_EVEN = 0x98F1F3F2.toInt()
        private const val ROW_ODD = 0x90E4E9E7.toInt()
        private const val ROW_EMPTY = 0x70EBEEED.toInt()
        private const val HEALTH_BACKGROUND = 0xA0BBC4C1.toInt()
        private const val TEXT_PRIMARY = 0xFF18201E.toInt()
        private const val TEXT_SECONDARY = 0xFF43514D.toInt()
        private const val TEXT_MUTED = 0xFF53635F.toInt()
        private const val ACCENT = 0xFF006D55.toInt()
        private const val WARNING = 0xFF9A6200.toInt()
        private const val DANGER = 0xFFB52F3B.toInt()

        private lateinit var INSTANCE: ControlledTargetsHud

        val instance: ControlledTargetsHud
            get() = INSTANCE

        fun initMain() {
            INSTANCE = ControlledTargetsHud()
            NeoForge.EVENT_BUS.register(INSTANCE)
        }

        private fun label(text: String, fontSize: Float, width: Float, height: Float, color: Int): LabelWidget {
            return LabelWidget(text).also {
                it.baseFontSize = fontSize
                it.layoutParams = LinearLayoutWidget.LayoutParams()
                    .size(width, height)
                    .gravity(Gravity.CENTER_VERTICAL)
                it.setRed(((color shr 16) and 0xFF) / 255f)
                it.setGreen(((color shr 8) and 0xFF) / 255f)
                it.setBlue((color and 0xFF) / 255f)
            }
        }

        private fun text(key: String, vararg values: Any): String {
            return Language.getInstance().getOrDefault(key).format(*values)
        }

        private fun support(value: Byte): String {
            val suffix = when (value) {
                MentaloutRosterClientState.SUPPORT_FULL -> "full"
                MentaloutRosterClientState.SUPPORT_BEST_EFFORT -> "best_effort"
                else -> "unsupported"
            }
            return text("hud.academy.mental_control.support.$suffix")
        }

        private fun supportColor(value: Byte): Int = when (value) {
            MentaloutRosterClientState.SUPPORT_FULL -> ACCENT
            MentaloutRosterClientState.SUPPORT_BEST_EFFORT -> WARNING
            else -> DANGER
        }

        private fun effects(entry: Entry): String {
            val values = ArrayList<String>(4)
            if (entry.hasFlag(MentaloutRosterClientState.FLAG_STUPOR)) {
                values.add(text("hud.academy.mental_control.effect.stupor"))
            }
            if (entry.hasFlag(MentaloutRosterClientState.FLAG_IMPRESSION)) {
                values.add(text("hud.academy.mental_control.effect.impression"))
            }
            if (entry.hasFlag(MentaloutRosterClientState.FLAG_MISIDENTIFICATION)) {
                values.add(text("hud.academy.mental_control.effect.misidentification"))
            }
            if (entry.hasFlag(MentaloutRosterClientState.FLAG_OVERRIDDEN)) {
                values.add(text("hud.academy.mental_control.effect.overridden"))
            }
            return values.joinToString(" ").ifEmpty { "-" }
        }

        private fun entityType(typeId: String): String {
            val parts = typeId.split(':', limit = 2)
            if (parts.size != 2) return typeId
            val fallback = parts[1].replace('_', ' ')
            return Language.getInstance().getOrDefault("entity.${parts[0]}.${parts[1]}", fallback)
        }

        private fun distance(distance: Float): String {
            if (!distance.isFinite() || distance == Float.MAX_VALUE) return "? m"
            return String.format(Locale.ROOT, "%.1f m", distance)
        }

        private fun health(entry: Entry): String {
            return String.format(Locale.ROOT, "%.0f/%.0f", entry.health(), entry.maxHealth())
        }

        private fun healthColor(entry: Entry): Int {
            val ratio = if (entry.maxHealth() > 0f) entry.health() / entry.maxHealth() else 0f
            return when {
                ratio > 0.55f -> ACCENT
                ratio > 0.25f -> WARNING
                else -> DANGER
            }
        }
    }
}
