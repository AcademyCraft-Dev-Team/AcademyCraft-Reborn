package org.academy.internal.client.app.misaka

import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.academy.api.client.app.App
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.*
import org.academy.api.client.resources.R
import org.academy.api.common.misaka.MisakaComputeDevices
import org.academy.api.common.util.L10n
import org.academy.internal.client.misaka.MisakaDeviceQuotaClient
import org.academy.internal.common.network.misaka.MisakaDeviceQuotaSyncPacket
import org.academy.internal.common.network.misaka.SetMisakaDeviceAllocationPacket
import org.misaka.MisakaNetworkClient
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Terminal app: allocate the player's Misaka compute budget across present devices.
 * Un-equipped last% is shown but not counted toward the sum.
 */
object MisakaDeviceQuotaApp : App {
    private const val ROW_HEIGHT = 34f
    private const val INPUT_WIDTH = 22f
    private const val INPUT_HEIGHT = 10f
    private const val PCT_WIDTH = 10f
    private const val SLIDER_HEIGHT = 6f
    private const val ROW_PLANE = 0x22182028
    private const val SLIDER_TRACK = 0xFF2A3038.toInt()
    private const val PRIMARY = 0xFFE8EEF5.toInt()

    override fun createContext(): WidgetContext = Context()

    override fun name(): String = L10n["app.academy.misaka_device_quota.name"]

    override fun icon(): Identifier = R.textures.gui.app.abilitysettings.ability_settings

    private class Context : WidgetContext {
        private val localPercents = linkedMapOf<Identifier, Int>()
        private val seekBars = linkedMapOf<Identifier, SeekBarWidget>()
        private val inputs = linkedMapOf<Identifier, TextBoxWidget>()
        private var suppress = false
        private var lastSyncRevision = -1

        private val sumLabel = LabelWidget("").apply {
            scale = 0.75f
            alpha = 0.85f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(12f)
        }
        private val budgetLabel = LabelWidget("").apply {
            scale = 0.68f
            alpha = 0.75f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        private val rowsHost = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = 2f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .heightMode(SizeMode.WRAP_CONTENT)
        }
        private val root = object : FrameLayoutWidget() {
            init {
                layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            }

            override fun tick() {
                val revision = MisakaDeviceQuotaClient.allRows().hashCode() xor
                    MisakaDeviceQuotaClient.remainingPercentSlots() xor
                    MisakaDeviceQuotaClient.playerBudgetMskPerSec().toBits()
                if (revision != lastSyncRevision) {
                    lastSyncRevision = revision
                    rebuildFromCache()
                }
                super.tick()
            }
        }

        init {
            val column = LinearLayoutWidget().apply {
                orientation = Orientation.VERTICAL
                spacing = 3f
                layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                    .padding(6f, 5f)
            }
            column.addChild("title", LabelWidget(L10n["app.academy.misaka_device_quota.title"]).apply {
                scale = 0.82f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(12f)
            })
            column.addChild("budget", budgetLabel)
            column.addChild("sum", sumLabel)
            column.addChild("rule", FillWidget(0x66FFFFFF).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(1f)
            })
            val scroll = ScrollPanelWidget(Orientation.VERTICAL).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(0f)
            }
            scroll.setContent(rowsHost)
            column.addChild("scroll", scroll)
            root.addChild("column", column)
            rebuildFromCache()
        }

        override fun get(): Widget = root

        private fun rebuildFromCache() {
            localPercents.clear()
            seekBars.clear()
            inputs.clear()
            rowsHost.clearChildren()

            val rows = MisakaDeviceQuotaClient.allRows()
            if (rows.isEmpty()) {
                rowsHost.addChild("empty", LabelWidget(L10n["app.academy.misaka_device_quota.empty"]).apply {
                    scale = 0.72f
                    alpha = 0.7f
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .height(14f)
                })
            } else {
                for (row in rows) {
                    val initial = if (row.present()) row.effectivePercent() else row.lastPercent()
                    localPercents[row.id()] = initial
                    rowsHost.addChild(row.id().toString(), deviceRow(row))
                }
            }
            refreshLabels()
        }

        private fun deviceRow(row: MisakaDeviceQuotaSyncPacket.DeviceQuotaRow): FrameLayoutWidget {
            val device = MisakaComputeDevices.get(row.id())
            val name = device?.displayName()?.string ?: row.id().toString()
            val frame = FrameLayoutWidget().apply {
                background = ColorDrawable(ROW_PLANE)
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(ROW_HEIGHT)
            }
            val column = LinearLayoutWidget().apply {
                orientation = Orientation.VERTICAL
                spacing = 2f
                layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                    .padding(4f, 3f)
            }
            frame.addChild("column", column)

            val heading = LinearLayoutWidget().apply {
                orientation = Orientation.HORIZONTAL
                spacing = 4f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(INPUT_HEIGHT)
            }
            column.addChild("heading", heading)
            heading.addChild("name", LabelWidget(name).apply {
                scale = 0.7f
                alpha = if (row.present()) 0.95f else 0.55f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .height(INPUT_HEIGHT)
                    .gravity(Gravity.CENTER_VERTICAL)
            })
            val input = TextBoxWidget(3).apply {
                setInputValidator { text -> text.isEmpty() || text.all { it.isDigit() } }
                text = (localPercents[row.id()] ?: 0).toString()
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .size(INPUT_WIDTH, INPUT_HEIGHT)
                    .gravity(Gravity.CENTER)
            }
            inputs[row.id()] = input
            heading.addChild("input", input)
            heading.addChild("pct", LabelWidget("%").apply {
                scale = 0.7f
                alpha = 0.8f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .size(PCT_WIDTH, INPUT_HEIGHT)
                    .gravity(Gravity.CENTER)
            })

            val seek = SeekBarWidget().apply {
                setMin(0f)
                setMax(100f)
                setProgress((localPercents[row.id()] ?: 0).toFloat())
                setKeyProgressIncrement(1)
                setBackgroundColor(SLIDER_TRACK)
                setProgressColor(PRIMARY)
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(SLIDER_HEIGHT)
            }
            seekBars[row.id()] = seek
            column.addChild("seek", seek)

            seek.setOnSeekBarChangeListener(object : SeekBarWidget.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBarWidget, progress: Float, fromUser: Boolean) {
                    if (suppress || !fromUser) return
                    setPercent(row.id(), progress.roundToInt(), presentOnly = row.present())
                    val clamped = localPercents[row.id()] ?: 0
                    if (seekBar.progress.roundToInt() != clamped) {
                        suppress = true
                        seekBar.setProgress(clamped.toFloat())
                        suppress = false
                    }
                    suppress = true
                    inputs[row.id()]?.text = clamped.toString()
                    suppress = false
                }

                override fun onStartTrackingTouch(seekBar: SeekBarWidget) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBarWidget) {
                    if (!suppress) commit()
                }
            })
            input.setOnFocusLost {
                if (suppress) return@setOnFocusLost
                val parsed = input.text.toIntOrNull() ?: (localPercents[row.id()] ?: 0)
                setPercent(row.id(), parsed, presentOnly = row.present())
                suppress = true
                seekBars[row.id()]?.setProgress((localPercents[row.id()] ?: 0).toFloat())
                inputs[row.id()]?.text = (localPercents[row.id()] ?: 0).toString()
                suppress = false
                commit()
            }

            if (!row.present() && row.lastPercent() > 0) {
                column.addChild("hint", LabelWidget(
                    Component.translatable(
                        "tooltip.academy.misaka_device_quota_unequipped",
                        row.lastPercent()
                    ).string
                ).apply {
                    scale = 0.58f
                    alpha = 0.6f
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .height(8f)
                })
            }
            return frame
        }

        private fun setPercent(id: Identifier, requested: Int, presentOnly: Boolean) {
            // Unequipped last% is editable for persistence but does not count toward the live sum.
            if (!presentOnly) {
                localPercents[id] = max(0, minOf(100, requested))
                refreshLabels()
                return
            }
            val others = presentSum() - (localPercents[id] ?: 0)
            localPercents[id] = max(0, minOf(100 - others, requested))
            refreshLabels()
        }

        private fun presentSum(): Int {
            var sum = 0
            for (row in MisakaDeviceQuotaClient.allRows()) {
                if (row.present()) {
                    sum += localPercents[row.id()] ?: 0
                }
            }
            return sum
        }

        private fun refreshLabels() {
            sumLabel.text = Component.translatable(
                "app.academy.misaka_device_quota.allocated",
                presentSum()
            ).string
            budgetLabel.text = Component.translatable(
                "app.academy.misaka_device_quota.budget",
                String.format("%.1f", MisakaDeviceQuotaClient.playerBudgetMskPerSec()),
                MisakaDeviceQuotaClient.remainingPercentSlots()
            ).string
        }

        private fun commit() {
            val payload = localPercents.map { (id, percent) ->
                SetMisakaDeviceAllocationPacket.DevicePercent(id, percent)
            }
            MisakaNetworkClient.send(SetMisakaDeviceAllocationPacket(payload))
        }
    }
}
