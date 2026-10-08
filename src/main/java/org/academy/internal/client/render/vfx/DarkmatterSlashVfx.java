package org.academy.internal.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.common.vfx.SkillVfxState;
import org.academy.internal.common.world.entity.skill.DarkmatterCutSlash;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.Map;

/** Compatibility entry for older slash packets/entities; all geometry now comes from the editable graph. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class DarkmatterSlashVfx {
    private static final Map<DarkmatterCutSlash, ActiveEffect> LEGACY = new IdentityHashMap<>();
    private DarkmatterSlashVfx() { }
    public static void register() { }

    public static ActiveEffect spawn(SkillVfxState.Slash state) {
        var level = Minecraft.getInstance().level;
        var effect = VfxGraphManager.INSTANCE.spawn(AcademyCraft.academy("vfxgraph/darkmatter_cut"), state.position().toVector3f());
        effect.setGameTimeLifetimeSeconds(state.lifetimeTicks() / 20f);
        effect.setRotation(new Quaternionf().rotateY((float) Math.toRadians(-state.yRot())).rotateX((float) Math.toRadians(state.xRot())));
        effect.setRenderDistance(96);
        effect.setCullingSphere(state.position().toVector3f(), state.scale() * 5 + 1);
        effect.bindFrame((active, camera, partialTick) -> {
            if (level == null || Minecraft.getInstance().level != level) return false;
            active.effect().setLiveParam("time", Value.of(active.gameAgeSeconds()));
            active.effect().setLiveParam("radius", Value.of(state.scale() * 4.4f));
            active.effect().setLiveParam("width", Value.of(.75f));
            active.effect().setLiveParam("height", Value.of(0f));
            active.effect().setLiveParam("reverse", Value.of(state.direction() < 0 ? 1f : 0));
            active.effect().setLiveParam("camera", Value.of(new Vector3f(camera.position()).sub(state.position().toVector3f())));
            return true;
        });
        return effect;
    }

    @SubscribeEvent public static void entityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof DarkmatterCutSlash slash) || !slash.level().isClientSide()
                || slash.isRemoved() || LEGACY.containsKey(slash)) return;
        LEGACY.put(slash, spawn(new SkillVfxState.Slash(slash.position(), slash.getXRot(), slash.getYRot(),
                slash.getScale(), slash.getSwingDirection(), slash.getDuration())));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        LEGACY.entrySet().removeIf(entry -> {
            if (!entry.getKey().isRemoved() && entry.getKey().level() == Minecraft.getInstance().level && !entry.getValue().isStopped()) return false;
            entry.getValue().stop();
            return true;
        });
    }
}
