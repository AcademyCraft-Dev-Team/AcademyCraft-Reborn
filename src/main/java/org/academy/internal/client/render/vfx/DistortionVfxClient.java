package org.academy.internal.client.render.vfx;

import java.util.ArrayList;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.joml.Vector3f;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class DistortionVfxClient {
    private static final Identifier RIPPLE_ASSET = AcademyCraft.academy("vfxgraph/distortion_ripple");
    private static final ArrayList<TimedEffect> ACTIVE = new ArrayList<>();

    private DistortionVfxClient() {
    }

    public static void register() {
        // Graph 资产与通用电弧渲染器由 VfxGraphManager 统一管理。
    }

    public static void trigger(float cx, float cy, float cz, float lifetime, float intensity,
                               float cr, float cg, float cb, float ca,
                               float er, float eg, float eb, float ea) {
        float safeLifetime = Float.isFinite(lifetime) ? Math.max(0.05f, lifetime) : 1f;
        float safeIntensity = Float.isFinite(intensity) ? Math.max(0f, intensity) : 1f;
        try {
            var effect = VfxGraphManager.INSTANCE.spawn(RIPPLE_ASSET, new Vector3f(cx, cy, cz));
            effect.bind("duration", () -> Value.of(safeLifetime));
            effect.bind("intensity", () -> Value.of(safeIntensity));
            effect.bind("core_color", () -> Value.color(cr, cg, cb, ca));
            effect.bind("edge_color", () -> Value.color(er, eg, eb, ea));
            ACTIVE.add(new TimedEffect(effect, Math.max(2, (int) Math.ceil(safeLifetime * 20f) + 2)));
        } catch (RuntimeException exception) {
            AcademyCraft.getLogger().warn("Unable to spawn distortion ripple VFX graph", exception);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        var iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            var timed = iterator.next();
            if (++timed.ageTicks >= timed.lifetimeTicks || timed.effect.isStopped()) {
                VfxGraphManager.INSTANCE.stop(timed.effect);
                iterator.remove();
            }
        }
    }

    private static final class TimedEffect {
        private final ActiveEffect effect;
        private final int lifetimeTicks;
        private int ageTicks;

        private TimedEffect(ActiveEffect effect, int lifetimeTicks) {
            this.effect = effect;
            this.lifetimeTicks = lifetimeTicks;
        }
    }
}
