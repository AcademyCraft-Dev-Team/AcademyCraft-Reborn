package org.academy.api.client.render.vfxgraph.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * M16-02 ParticleBuffer 性能门禁（headless）：SoA spawn/kill（swap-remove）压力。
 *
 * <p>断言宽松预算防 CI 抖动，跑分输出到日志供人工参考。</p>
 */
class ParticleBufferPerfTest {
    private static final long TEN_K_KILL_BUDGET_NS = 100_000_000L; // 100ms

    @Test
    void tenThousandSpawnAndKillChurn() {
        var buffer = new ParticleBuffer(64);
        for (int i = 0; i < 10000; i++) {
            buffer.spawn();
        }
        assertEquals(10000, buffer.count());
        assertEquals(16384, buffer.capacity()); // 64 → 128 → ... → 16384

        long start = System.nanoTime();
        // swap-remove 全量删除
        while (buffer.count() > 0) {
            buffer.kill(0);
        }
        long elapsed = System.nanoTime() - start;
        System.out.println("[perf] 10k spawn + swap-remove kill: " + elapsed / 1_000_000.0 + " ms");
        assertEquals(0, buffer.count());
        assertTrue(elapsed < TEN_K_KILL_BUDGET_NS, "10k kill exceeded budget: " + elapsed + " ns");
    }

    @Test
    void trailPushWritesHistory() {
        var buffer = new ParticleBuffer(16);
        int i = buffer.spawn();
        for (int k = 0; k < ParticleBuffer.TRAIL_LENGTH + 2; k++) {
            buffer.pushTrail(i, k, k, k);
        }
        assertEquals(ParticleBuffer.TRAIL_LENGTH, buffer.trailSize(i));
        // 最新样本在 index 0；超过 TRAIL_LENGTH 后最旧样本被挤出
        assertEquals(ParticleBuffer.TRAIL_LENGTH + 1, buffer.trailX(i, 0), 1e-5f);
        assertEquals(2f, buffer.trailX(i, ParticleBuffer.TRAIL_LENGTH - 1), 1e-5f);
    }
}
