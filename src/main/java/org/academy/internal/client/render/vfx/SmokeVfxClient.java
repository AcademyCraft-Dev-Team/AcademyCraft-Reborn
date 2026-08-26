package org.academy.internal.client.render.vfx;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.internal.common.world.entity.skill.Smoke;
import net.minecraft.resources.Identifier;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SmokeVfxClient {
    private static final Identifier SMOKE_ASSET = AcademyCraft.academy("vfxgraph/entity_smoke");

    private SmokeVfxClient() {
    }

    public static void register() {
        // Graph 资产和通用渲染器由 VfxGraphManager 统一注册。
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        var entity = event.getEntity();
        if (entity instanceof Smoke smoke) {
            try {
                var effect = VfxGraphManager.INSTANCE.spawnFollow(SMOKE_ASSET, smoke);
                effect.bind("smoke_size", () -> Value.of(smoke.size));
                effect.bind("smoke_alpha", () -> Value.of(smoke.getAlpha()));
                effect.bind("smoke_frame", () -> Value.of((float) smoke.frame));
            } catch (RuntimeException exception) {
                AcademyCraft.getLogger().warn("Unable to spawn smoke VFX graph", exception);
            }
        }
    }
}
