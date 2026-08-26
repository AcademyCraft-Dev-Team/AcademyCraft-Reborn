package org.academy.api.client.render.vfxgraph.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        for (String name : new String[]{"demo_burst", "demo_fountain", "demo_ribbon", "minimal_burst", "demo_fire", "demo_arc",
                "surface_arc", "contact_arc", "spark", "demo_blender_arc", "plasma_cannon_charge", "plasma_cannon_focus",
                "plasma_cannon_projectile", "plasma_cannon_impact", "entity_smoke", "distortion_ripple",
                "platinum_execution"}) {
            var stream = getClass().getResourceAsStream("/assets/academy/vfxgraph/" + name + ".json");
            assertNotNull(stream, "sample asset " + name + " should be packaged");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var assetId = Identifier.fromNamespaceAndPath("academy", "vfxgraph/" + name);
            VfxGraphManager.INSTANCE.registerAsset(assetId, json);

            var effect = VfxGraphManager.INSTANCE.spawn(assetId, new org.joml.Vector3f(0f, 0f, 0f));
            assertNotNull(effect);
            assertTrue(effect.spec() != null);
            if (name.equals("entity_smoke")) {
                assertTrue(effect.spec().texture().toString()
                        .equals("academy:textures/ability/generic/effect/smokes.png"));
            }
            // 步进若干帧：确保曲线/渐变参数、多层 spawn、over-life 节点全链路可模拟
            for (int i = 0; i < 30; i++) {
                effect.tick(1f / 60f);
            }
        }
        assertTrue(VfxGraphManager.INSTANCE.effectCount() == 17);
    }
}
