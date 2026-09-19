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
import org.academy.internal.common.world.entity.projectile.DarkmatterFeatherProjectile;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.Map;

/** The same velocity-aligned material blade for pursuit modifiers and interference/six-wing feathers. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class DarkmatterFeatherVfxClient {
    private static final Map<DarkmatterFeatherProjectile, ActiveEffect> BLADES = new IdentityHashMap<>();
    private DarkmatterFeatherVfxClient() { }

    @SubscribeEvent public static void entityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof DarkmatterFeatherProjectile feather) || !feather.level().isClientSide()
                || feather.isRemoved() || BLADES.containsKey(feather)) return;
        var effect = VfxGraphManager.INSTANCE.spawn(AcademyCraft.academy("vfxgraph/darkmatter_feather"), feather.position().toVector3f());
        effect.setRenderDistance(96);
        effect.bindFrame((active, camera, partialTick) -> {
            if (feather.isRemoved() || feather.level() != Minecraft.getInstance().level) return false;
            var position = feather.getPosition(partialTick).toVector3f();
            var velocity = feather.getDeltaMovement().toVector3f();
            if (velocity.lengthSquared() < 0.0001f) velocity.set(feather.getLookAngle().toVector3f());
            var rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), velocity.normalize());
            active.setPosition(position);
            active.setRotation(rotation);
            active.setCullingSphere(position, 1.2f);
            active.effect().setLiveParam("time", Value.of(active.gameAgeSeconds()));
            active.effect().setLiveParam("camera", Value.of(new Quaternionf(rotation).conjugate()
                    .transform(new Vector3f(camera.position()).sub(position))));
            return true;
        });
        BLADES.put(feather, effect);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        BLADES.entrySet().removeIf(entry -> {
            if (!entry.getKey().isRemoved() && entry.getKey().level() == Minecraft.getInstance().level && !entry.getValue().isStopped()) return false;
            entry.getValue().stop(); return true;
        });
    }
}
