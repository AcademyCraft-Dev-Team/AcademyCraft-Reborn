package org.academy.api.client.render.vfxgraph.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 回归：游戏暂停（dt=0 多帧）时火焰粒子应冻结而非消失/抖动（M21d spawnStart 修复 + 暂停冻结）。
 */
class FirePauseTest {
    @BeforeEach void setUp() { var m = VfxGraphManager.INSTANCE; m.init(); m.clearEffects(); m.invalidateAll(); }
    @AfterEach void tearDown() { VfxGraphManager.INSTANCE.close(); }

    @Test
    void fireSurvivesPauseFrames() {
        var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/demo_fire.json");
        var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        var assetId = Identifier.fromNamespaceAndPath("academy", "vfxgraph/demo_fire");
        VfxGraphManager.INSTANCE.registerAsset(assetId, json);
        var effect = VfxGraphManager.INSTANCE.spawn(assetId, new org.joml.Vector3f());
        // 播放 60 帧
        for (int i = 0; i < 60; i++) effect.tick(1f / 60f);
        int before = effect.effect().buffer().count();
        assertTrue(before > 0, "fire should have live particles before pause");
        float y0 = effect.effect().buffer().positionY(0);
        // 暂停 120 帧（dt=0）
        for (int i = 0; i < 120; i++) effect.tick(0f);
        int during = effect.effect().buffer().count();
        float y1 = effect.effect().buffer().positionY(0);
        // 断言：粒子数不减少、位置冻结
        assertTrue(during >= before, "particles should not die during pause: before=" + before + " during=" + during);
        assertTrue(Math.abs(y1 - y0) < 1e-4f, "position should freeze during pause");
    }
}
