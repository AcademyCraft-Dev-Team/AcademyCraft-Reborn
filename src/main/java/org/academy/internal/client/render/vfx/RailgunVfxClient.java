package org.academy.internal.client.render.vfx;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxRegistry;
import org.academy.internal.common.world.entity.skill.RailgunRay;

import java.util.IdentityHashMap;
import java.util.Map;

@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class RailgunVfxClient {
    private static final Map<RailgunRay, RailgunShotVfx> SHOTS = new IdentityHashMap<>();

    private RailgunVfxClient() {
    }

    public static void register() {
        VfxRegistry.register(RailgunRingData.class, VfxPhase.WORLD_TRANSLUCENT, new TexturedQuadRenderer(false));
        VfxManager.INSTANCE.spawn(new RailgunVfx());
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() || !(event.getEntity() instanceof RailgunRay ray)) return;
        if (SHOTS.containsKey(ray)) return;
        var shot = new RailgunShotVfx(ray);
        SHOTS.put(ray, shot);
        VfxManager.INSTANCE.spawn(shot);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() || !(event.getEntity() instanceof RailgunRay ray)) return;
        var shot = SHOTS.remove(ray);
        if (shot != null) shot.stop();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        SHOTS.values().forEach(RailgunShotVfx::stop);
        SHOTS.clear();
    }
}
