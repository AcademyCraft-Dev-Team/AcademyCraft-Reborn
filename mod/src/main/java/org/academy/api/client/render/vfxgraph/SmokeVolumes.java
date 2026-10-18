package org.academy.api.client.render.vfxgraph;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.common.vfx.SkillVfxState;

import java.util.IdentityHashMap;
import java.util.Map;

@EventBusSubscriber(Dist.CLIENT)
public final class SmokeVolumes {
    private static final Map<ActiveEffect, Playback> LOCAL = new IdentityHashMap<>();

    private SmokeVolumes() {
    }

    public static Identifier smokeAsset() {
        return Identifier.fromNamespaceAndPath("academy", "vfxgraph/entity_smoke");
    }

    public static void spawn(SkillVfxState.Smoke state) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var effect = VfxGraphManager.INSTANCE.spawn(smokeAsset(), state.position().toVector3f());
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
}
