package org.academy.internal.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.EffectParameterCache;
import org.academy.api.client.render.vfxgraph.runtime.StableEffectAdmission;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.internal.common.world.entity.projectile.DarkmatterFeatherProjectile;
import org.academy.internal.common.world.entity.projectile.DarkmatterCreatureProjectile;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

/** The same velocity-aligned material blade for pursuit modifiers and interference/six-wing feathers. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class DarkmatterFeatherVfxClient {
    private static final int MAX_BLADES = 192;
    private static final Map<Entity, Blade> BLADES = new LinkedHashMap<>();
    private static final Set<Entity> CANDIDATES = new LinkedHashSet<>();
    private static final StableEffectAdmission<Entity> ADMISSION = new StableEffectAdmission<>(MAX_BLADES, 4, .85);
    private static final Vector3f VIEW_POSITION = new Vector3f();
    private DarkmatterFeatherVfxClient() { }

    @SubscribeEvent public static void entityTick(EntityTickEvent.Post event) {
        var feather = event.getEntity();
        if (!(feather instanceof DarkmatterFeatherProjectile || feather instanceof DarkmatterCreatureProjectile)
                || !feather.level().isClientSide() || feather.isRemoved()) return;
        CANDIDATES.add(feather);
    }

    private static final class Blade {
        final ActiveEffect effect;
        final EffectParameterCache params = new EffectParameterCache();
        final Vector3f position = new Vector3f(), velocity = new Vector3f(), cameraPosition = new Vector3f();
        final Quaternionf rotation = new Quaternionf(), inverse = new Quaternionf();
        Blade(Entity feather) {
            effect = VfxGraphManager.INSTANCE.spawn(AcademyCraft.academy(feather instanceof DarkmatterFeatherProjectile
                    ? "vfxgraph/darkmatter_feather" : "vfxgraph/darkmatter_creation_trail"), feather.position().toVector3f());
            effect.setRenderDistance(96);
            effect.bindSurfaceSampler("surface", (index, u, v, point, normal) -> {
                point.set((u - .5f) * .045f, -.15f - v * .30f, (index % 2 == 0 ? -1 : 1) * .018f);
                normal.set(0, -1, 0);
                return true;
            });
            effect.bindFrame((active, camera, partialTick) -> {
                if (feather.isRemoved() || feather.level() != Minecraft.getInstance().level) return false;
                var interpolated = feather.getPosition(partialTick);
                position.set((float) interpolated.x, (float) interpolated.y, (float) interpolated.z);
                var movement = feather.getDeltaMovement();
                velocity.set((float) movement.x, (float) movement.y, (float) movement.z);
                if (velocity.lengthSquared() < 0.0001f) {
                    var look = feather.getLookAngle(); velocity.set((float) look.x, (float) look.y, (float) look.z);
                }
                velocity.normalize();
                rotation.rotationTo(0, 1, 0, velocity.x, velocity.y, velocity.z);
                active.setPosition(position);
                active.setRotation(rotation);
                active.setCullingSphere(position, 1.2f);
                params.begin(active.effect());
                params.putFloat("time", active.gameAgeSeconds());
                params.putFloat("progress", active.gameAgeSeconds() % .25f / .25f);
                params.putFloat("strength", feather instanceof DarkmatterFeatherProjectile blade && blade.hasPassedTarget() ? .55f : 1f);
                VIEW_POSITION.set(camera.position());
                inverse.set(rotation).conjugate().transform(cameraPosition.set(camera.position()).sub(position));
                params.putVec3("camera", cameraPosition);
                return true;
            });
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        CANDIDATES.removeIf(entity -> {
            if (!entity.isRemoved() && entity.level() == mc.level) return false;
            ADMISSION.remove(entity); return true;
        });
        if (mc.level == null) { ADMISSION.clear(); CANDIDATES.clear(); }
        if (BLADES.isEmpty() && mc.getCameraEntity() != null) {
            var position = mc.getCameraEntity().position();
            VIEW_POSITION.set((float) position.x, (float) position.y, (float) position.z);
        }
        for (var entity : CANDIDATES) {
            double dx = entity.getX() - VIEW_POSITION.x, dy = entity.getY() - VIEW_POSITION.y, dz = entity.getZ() - VIEW_POSITION.z;
            double distance = dx * dx + dy * dy + dz * dz;
            boolean local = entity instanceof net.minecraft.world.entity.projectile.Projectile projectile && projectile.getOwner() == mc.player;
            boolean exit = entity instanceof DarkmatterFeatherProjectile feather && feather.hasPassedTarget();
            ADMISSION.offer(entity, local ? 3 : exit || distance < 64 ? 2 : 1, distance);
        }
        var selected = ADMISSION.reconcile(mc.level == null ? 0 : mc.level.getGameTime());
        BLADES.entrySet().removeIf(entry -> {
            if (selected.contains(entry.getKey()) && !entry.getValue().effect.isStopped()) return false;
            entry.getValue().effect.stop(); return true;
        });
        for (var entity : selected) BLADES.computeIfAbsent(entity, Blade::new);
    }
}
