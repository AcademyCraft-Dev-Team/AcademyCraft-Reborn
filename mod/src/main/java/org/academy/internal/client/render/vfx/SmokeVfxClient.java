package org.academy.internal.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.common.vfx.SkillVfxState;
import org.academy.internal.common.world.entity.skill.Smoke;
import org.slf4j.Logger;

import java.util.IdentityHashMap;
import java.util.Map;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SmokeVfxClient {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    private static final Identifier SMOKE_ASSET = AcademyCraft.academy("vfxgraph/entity_smoke");

    private static final Map<ActiveEffect, Playback> LOCAL =
            new IdentityHashMap<>();

    public static void spawn(SkillVfxState.Smoke state) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var effect = VfxGraphManager.INSTANCE.spawn(SMOKE_ASSET, state.position().toVector3f());
        var playback = new Playback(level, state);
        effect.setCullingSphere(state.position().toVector3f(), state.size() * 2f);
        effect.bind("smoke_size", () -> Value.of(state.size()));
        effect.bind("smoke_alpha", () -> Value.of(state.alphaAt(playback.age
                + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false))));
        effect.bind("smoke_frame", () -> Value.of((float) state.frame()));
        LOCAL.put(effect, playback);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) return;
        var iterator = LOCAL.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var playback = entry.getValue();
            if (playback.level != minecraft.level || ++playback.age >= playback.state.visibleTicks()) {
                VfxGraphManager.INSTANCE.stop(entry.getKey());
                iterator.remove();
            }
        }
    }

    public static void clear() {
        LOCAL.keySet().forEach(VfxGraphManager.INSTANCE::stop);
        LOCAL.clear();
    }

    private static final class Playback {
        final ClientLevel level;
        final SkillVfxState.Smoke state;
        int age;

        Playback(ClientLevel level, SkillVfxState.Smoke state) {
            this.level = level;
            this.state = state;
        }
    }

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
                LOGGER.warn("Unable to spawn smoke VFX graph", exception);
            }
        }
    }
}
