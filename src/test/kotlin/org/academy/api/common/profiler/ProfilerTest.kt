package org.academy.api.common.profiler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProfilerTest {

    @Test
    fun zoneProfilerAggregates() {
        AcademyProfiler.startZoneCapture()
        try {
            repeat(10) {
                AcademyProfiler.runZone("outer") {
                    AcademyProfiler.runZone("inner.a") { busyLoop(1_000_000) }
                    AcademyProfiler.runZone("inner.b") { busyLoop(500_000) }
                }
            }
            val snap = AcademyProfiler.snapshot()
            assertTrue(snap.zones.isNotEmpty(), "expected at least one zone session")
            val zs = snap.zones.values.first()
            val outer = zs.childrenOf(ZoneProfiler.ROOT).firstOrNull { it.name() == "outer" }
            assertNotNull(outer, "outer zone missing")
            assertEquals(10, outer!!.count())
            assertTrue(outer.totalNs() > 0)
            val children = zs.childrenOf(outer.path())
            assertTrue(children.any { it.name() == "inner.a" })
            assertTrue(children.any { it.name() == "inner.b" })
            val totalPercent = children.sumOf { zs.parentPercent(it) }
            assertTrue(totalPercent <= 101.0, "child percentages should sum to ~100: $totalPercent")
        } finally {
            AcademyProfiler.stopZoneCapture()
        }
    }

    @Test
    fun samplerCollectsSamples() {
        val target = Thread.currentThread()
        AcademyProfiler.registerThread(target)
        AcademyProfiler.startSampling(500)
        try {
            repeat(40) { busyLoop(8_000_000) }
        } finally {
            AcademyProfiler.stopSampling()
        }
        val snap = AcademyProfiler.snapshot()
        val sampler = snap.sampler
        assertNotNull(sampler, "sampler snapshot should exist after start/stop")
        assertTrue(sampler.totalSamples() > 0, "expected samples to be captured")
        val view = sampler.threads()[target.id]
        assertNotNull(view)
        assertTrue(view!!.root().samples() > 0)
    }

    @Test
    fun frameStatsRecords() {
        FrameStats.recordFrame(16_000_000L, 100_000_000L)
        FrameStats.recordFrame(17_000_000L, 110_000_000L)
        val snap = FrameStats.snapshot()
        assertEquals(2, snap.size())
        assertTrue(snap.avgMs() in 16.0..17.5, "avg ${snap.avgMs()}")
        assertTrue(snap.fps() > 0)
    }

    @Test
    fun dumpProducesText() {
        AcademyProfiler.startZoneCapture()
        try {
            AcademyProfiler.resetZones()
            AcademyProfiler.runZone("x") { busyLoop(100_000) }
            val snap = AcademyProfiler.snapshot()
            val zs = snap.zones.values.first()
            val text = ProfileDump.dumpZones(zs, 4)
            assertTrue(text.contains("x"), "dump should contain zone name")
            assertTrue(text.contains("ms"))
        } finally {
            AcademyProfiler.stopZoneCapture()
        }
    }

    private fun busyLoop(n: Int) {
        var x = 0L
        for (i in 0 until n) x += i
        if (x == Long.MIN_VALUE) println("never")
    }
}
