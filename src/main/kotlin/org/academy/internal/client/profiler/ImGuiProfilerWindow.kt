package org.academy.internal.client.profiler

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.resource.RenderTargetDescriptor
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import imgui.ImGui
import imgui.extension.implot.ImPlot
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImBoolean
import imgui.type.ImInt
import net.minecraft.client.Minecraft
import org.academy.Dev
import org.academy.api.client.gui.imgui.ImGuiUtilApi
import org.academy.api.client.render.Render
import org.academy.api.client.render.TextureBinding
import org.academy.api.common.profiler.AcademyProfiler
import org.academy.api.common.profiler.SampledNode
import org.academy.api.common.profiler.SamplerSnapshot
import org.academy.api.common.profiler.ZoneProfiler
import org.joml.Vector4f

/**
 * ImGui 性能分析窗口（开发构建）。
 *
 * 数据全部来自 [AcademyProfiler]（自包含采集层），本类只负责可视化。
 */
object ImGuiProfilerWindow {
    @Volatile
    var visible = false
        private set

    private const val TAB_SAMPLER = 0
    private const val TAB_ZONES = 1
    private const val TAB_FRAME = 2
    private var tab = TAB_SAMPLER

    private var samplerIntervalUs = 1000

    private var zoneThreadName: String? = null
    private var zonePath = ZoneProfiler.ROOT

    fun toggle() {
        visible = !visible
    }

    fun setVisible(value: Boolean) {
        visible = value
    }

    /** 渲染到主屏幕（由 HudManager 在帧末调用）。 */
    fun renderToMainScreen() {
        if (!visible || !Dev.HAS_IM_GUI) return
        val mc = Minecraft.getInstance()
        val main = mc.gameRenderer.mainRenderTarget()
        val pool = Render.Buffers.getResourcePool()
        val desc = RenderTargetDescriptor(
            main.width, main.height, true, Vector4f(0f), GpuFormat.RGBA8_UNORM
        )
        val target = pool.acquire(desc)
        try {
            val color = target.getColorTextureView() ?: return
            ImGuiUtilApi.render(target) { draw() }
            val mainColor = main.getColorTextureView() ?: return
            Render.runBlitPass(
                mainColor,
                Render.RenderPipelines.BLIT_SCREEN_PREMULTIPLIED_ALPHA,
                Render.Buffers.getInstance().fsQuadVBNDC,
                listOf(
                    TextureBinding(
                        "Sampler0", color,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
                    )
                ),
                mutableListOf(),
                false
            )
        } finally {
            pool.release(desc, target)
        }
    }

    private fun draw() {
        if (ImGui.begin("Academy Profiler")) {
            drawToolbar()
            ImGui.separator()
            if (ImGui.beginTabBar("ac_profiler_tabs")) {
                if (ImGui.beginTabItem("Sampler")) {
                    tab = TAB_SAMPLER
                    drawSamplerTab()
                    ImGui.endTabItem()
                }
                if (ImGui.beginTabItem("Zones")) {
                    tab = TAB_ZONES
                    drawZonesTab()
                    ImGui.endTabItem()
                }
                if (ImGui.beginTabItem("Frame Time")) {
                    tab = TAB_FRAME
                    drawFrameTab()
                    ImGui.endTabItem()
                }
                ImGui.endTabBar()
            }
        }
        ImGui.end()
    }

    private fun drawToolbar() {
        if (ImGui.button("Close")) visible = false
        ImGui.sameLine()
        if (ImGui.button("Reset Zones")) AcademyProfiler.resetZones()
        ImGui.sameLine()

        if (AcademyProfiler.isSampling()) {
            if (ImGui.button("Pause/Resume")) {
                if (AcademyProfiler.isSamplingPaused()) AcademyProfiler.resumeSampling()
                else AcademyProfiler.pauseSampling()
            }
            ImGui.sameLine()
            if (ImGui.button("Stop Sampling")) AcademyProfiler.stopSampling()
        } else {
            val interval = ImInt(samplerIntervalUs)
            ImGui.setNextItemWidth(90f)
            if (ImGui.inputInt("interval us", interval)) {
                samplerIntervalUs = interval.get().coerceIn(100, 1_000_000)
            }
            ImGui.sameLine()
            if (ImGui.button("Start Sampling")) {
                AcademyProfiler.startSampling(samplerIntervalUs.toLong())
            }
        }
        ImGui.sameLine()
        if (AcademyProfiler.isCapturingZones()) {
            if (ImGui.button("Stop Zones")) AcademyProfiler.stopZoneCapture()
        } else {
            if (ImGui.button("Start Zones")) AcademyProfiler.startZoneCapture()
        }
    }

    // ------------------------------------------------------------------
    // Sampler tab
    // ------------------------------------------------------------------

    private fun drawSamplerTab() {
        val snapshot = AcademyProfiler.snapshot()
        val sampler = snapshot.sampler

        for (ref in AcademyProfiler.samplerThreads()) {
            val enabled = ImBoolean(ref.enabled)
            if (ImGui.checkbox(ref.name, enabled)) {
                AcademyProfiler.setThreadEnabled(ref.id, enabled.get())
            }
        }
        ImGui.text(
            "Total samples: %d   Duration: %.1f s   Paused: %s".format(
                sampler.totalSamples, sampler.durationSeconds, snapshot.samplingPaused
            )
        )

        if (sampler.totalSamples > 0 && ImPlot.beginPlot("Sampler Pie", 0f, 200f)) {
            val top = topSampledMethods(sampler, 10)
            if (top.isNotEmpty()) {
                ImPlot.setupAxes("", "")
                ImPlot.setupAxesLimits(-1.5, 1.5, -1.5, 1.5)
                val labels = top.map { it.first }.toTypedArray()
                val values = top.map { it.second.toFloat() }.toFloatArray()
                ImPlot.plotPieChart(labels, values, 0.0, 0.0, 1.0)
            }
            ImPlot.endPlot()
        }

        ImGui.separator()
        if (ImGui.beginChild("sampler_calltree", 0f, 0f)) {
            for (view in sampler.threads.values) {
                if (ImGui.collapsingHeader("%s (%d samples)".format(view.name, view.samples))) {
                    ImGui.indent()
                    renderCallNode(view.root)
                    ImGui.unindent()
                }
            }
        }
        ImGui.endChild()
    }

    private fun topSampledMethods(sampler: SamplerSnapshot, n: Int): List<Pair<String, Double>> {
        val map = HashMap<String, Long>()
        for (view in sampler.threads.values) {
            collectSelf(view.root, map)
        }
        val total = sampler.totalSamples
        return map.entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key to (if (total > 0) it.value * 100.0 / total else 0.0) }
    }

    private fun collectSelf(node: SampledNode, map: MutableMap<String, Long>) {
        if (node.label != "<root>") {
            map.merge(node.label, node.selfSamples, Long::plus)
        }
        for (child in node.children) {
            collectSelf(child, map)
        }
    }

    private fun renderCallNode(node: SampledNode) {
        val label = "%s  [%.1f%% self / %.1f%% total]".format(node.label, node.selfPercent, node.samplesPercent)
        if (node.children.isEmpty()) {
            ImGui.text(label)
        } else if (ImGui.treeNodeEx(label, ImGuiTreeNodeFlags.SpanAvailWidth)) {
            for (child in node.children) {
                renderCallNode(child)
            }
            ImGui.treePop()
        }
    }

    // ------------------------------------------------------------------
    // Zones tab
    // ------------------------------------------------------------------

    private fun drawZonesTab() {
        if (!AcademyProfiler.isCapturingZones()) {
            ImGui.text("Zone capture is off. Click 'Start Zones' in the toolbar.")
            return
        }
        val snapshot = AcademyProfiler.snapshot()
        val threadNames = snapshot.zones.keys.sorted()
        if (threadNames.isEmpty()) {
            ImGui.text("No zone data captured yet...")
            return
        }

        if (zoneThreadName == null || zoneThreadName !in threadNames) {
            zoneThreadName = threadNames.first()
            zonePath = ZoneProfiler.ROOT
        }
        val current = zoneThreadName!!
        val selected = ImInt(threadNames.indexOf(current).coerceAtLeast(0))
        if (ImGui.combo("Thread", selected, threadNames.toTypedArray())) {
            zoneThreadName = threadNames[selected.get()]
            zonePath = ZoneProfiler.ROOT
        }

        val zones = snapshot.zones[current] ?: return

        if (zonePath != ZoneProfiler.ROOT) {
            if (ImGui.button("<- Up")) {
                zonePath = parentOf(zonePath)
            }
            ImGui.sameLine()
        }
        ImGui.text("Path: " + zonePath.replace(ZoneProfiler.PATH_SEPARATOR, '.'))

        val children = zones.childrenOf(zonePath)
        if (children.isNotEmpty() && ImPlot.beginPlot("Zone Pie", 0f, 200f)) {
            ImPlot.setupAxes("", "")
            ImPlot.setupAxesLimits(-1.5, 1.5, -1.5, 1.5)
            val top = children.take(10)
            val labels = top.map { it.name }.toTypedArray()
            val values = top.map { it.totalMs.toFloat() }.toFloatArray()
            ImPlot.plotPieChart(labels, values, 0.0, 0.0, 1.0)
            ImPlot.endPlot()
        }

        ImGui.separator()
        if (ImGui.beginTable("zones_table", 5, ImGuiTableFlags.RowBg or ImGuiTableFlags.SizingStretchProp)) {
            ImGui.tableSetupColumn("Name", ImGuiTableColumnFlags.WidthStretch)
            ImGui.tableSetupColumn("Count")
            ImGui.tableSetupColumn("Total ms")
            ImGui.tableSetupColumn("% of parent")
            ImGui.tableSetupColumn("Self ms")
            ImGui.tableHeadersRow()

            for (child in children) {
                ImGui.tableNextRow()
                ImGui.tableNextColumn()
                if (ImGui.selectable(child.name + "##" + child.path)) {
                    // single click selects
                }
                if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                    zonePath = child.path
                }
                ImGui.tableNextColumn()
                ImGui.text(child.count.toString())
                ImGui.tableNextColumn()
                ImGui.text("%.2f".format(child.totalMs))
                ImGui.tableNextColumn()
                ImGui.text("%.1f%%".format(zones.parentPercent(child)))
                ImGui.tableNextColumn()
                ImGui.text("%.2f".format(child.selfMs))
            }
            ImGui.endTable()
        }

        ImGui.text(
            "Thread total: %.2f ms   Path total: %.2f ms".format(
                zones.rootTotalNs / 1e6, (zones.sliceAt(zonePath)?.totalNs ?: 0L) / 1e6
            )
        )
    }

    private fun parentOf(path: String): String {
        if (path == ZoneProfiler.ROOT) return ZoneProfiler.ROOT
        val idx = path.lastIndexOf(ZoneProfiler.PATH_SEPARATOR)
        return if (idx < 0) ZoneProfiler.ROOT else path.substring(0, idx)
    }

    // ------------------------------------------------------------------
    // Frame Time tab
    // ------------------------------------------------------------------

    private fun drawFrameTab() {
        val frame = AcademyProfiler.snapshot().frame
        ImGui.text("FPS: %.1f".format(frame.fps))
        ImGui.text(
            "Frame avg %.2f ms | min %.2f | max %.2f | p99 %.2f | last %.2f".format(
                frame.avgMs, frame.minMs, frame.maxMs, frame.p99Ms, frame.lastFrameMs
            )
        )
        ImGui.text("Samples: %d / 720".format(frame.size))

        if (frame.size > 0 && ImPlot.beginPlot("Frame Time (ms)", 0f, 180f)) {
            ImPlot.setupAxes("frame", "ms")
            ImPlot.setupAxesLimits(0.0, frame.size.toDouble(), 0.0, 33.0)
            ImPlot.plotLine("ms", frame.frameTimesMs)
            ImPlot.endPlot()
        }

        if (frame.size > 0 && ImPlot.beginPlot("Heap (MB)", 0f, 120f)) {
            ImPlot.setupAxes("frame", "MB")
            ImPlot.plotLine("heap", frame.heapUsedMb)
            ImPlot.endPlot()
        }
    }
}
