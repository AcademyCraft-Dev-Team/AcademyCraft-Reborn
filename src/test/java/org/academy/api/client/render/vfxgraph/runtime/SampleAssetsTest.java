package org.academy.api.client.render.vfxgraph.runtime;

import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证随 mod 打包的示例图资产可用（asset 注册路径，M15-02）。
 */
class SampleAssetsTest {
    @BeforeEach
    void setUp() {
        var manager = VfxGraphManager.INSTANCE;
        manager.init();
        manager.clearEffects();
        manager.invalidateAll();
    }

    @AfterEach
    void tearDown() {
        VfxGraphManager.INSTANCE.close();
    }

    @Test
    void packagedAssetsDecodeAndSpawn() {
        for (var name : new String[]{"demo_burst", "demo_fountain", "demo_ribbon", "skill_dirstrike", "minimal_burst", "demo_fire", "demo_arc",
                "surface_arc", "contact_arc", "spark", "demo_blender_arc"}) {
            var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/" + name + ".json");
            assertNotNull(stream, "sample asset " + name + " should be packaged");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var assetId = Identifier.fromNamespaceAndPath("academy", "vfxgraph/" + name);
            VfxGraphManager.INSTANCE.registerAsset(assetId, json);

            var effect = VfxGraphManager.INSTANCE.spawn(assetId, new Vector3f(0f, 0f, 0f));
            assertNotNull(effect);
            assertNotNull(effect.spec());
            // 步进若干帧：确保曲线/渐变参数、多层 spawn、over-life 节点全链路可模拟
            for (var i = 0; i < 30; i++) {
                effect.tick(1f / 60f);
            }
        }
        assertEquals(11, VfxGraphManager.INSTANCE.effectCount());
    }
}
