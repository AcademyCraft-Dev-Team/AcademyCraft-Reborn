package org.academy.internal.client.renderer.entity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.SmokeVolumes;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.internal.client.renderer.entity.state.SmokeRenderState;
import org.academy.internal.common.world.entity.skill.Smoke;
import org.slf4j.Logger;

public class SmokeRenderer extends EntityRenderer<Smoke, SmokeRenderState> {
    public SmokeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public SmokeRenderState createRenderState() {
        return new SmokeRenderState();
    }

    @EventBusSubscriber(Dist.CLIENT)
    public static final class Events {
        private static final Logger LOGGER = AcademyCraft.getLogger();

        private Events() {
        }

        @SubscribeEvent
        public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
            if (!event.getLevel().isClientSide()) return;
            if (!(event.getEntity() instanceof Smoke smoke)) return;
            try {
                var effect = VfxGraphManager.INSTANCE.spawnFollow(SmokeVolumes.smokeAsset(), smoke);
                effect.bind("smoke_size", () -> Value.of(smoke.size));
                effect.bind("smoke_alpha", () -> Value.of(smoke.getAlpha()));
                effect.bind("smoke_frame", () -> Value.of((float) smoke.frame));
            } catch (RuntimeException exception) {
                LOGGER.warn("Unable to spawn smoke VFX graph", exception);
            }
        }
    }
}
