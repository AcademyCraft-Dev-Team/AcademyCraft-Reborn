package org.academy.internal.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.EffectParameterCache;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.client.render.vfxgraph.shape.FeatherArrayGeometry;
import org.academy.api.client.render.vfxgraph.shape.BlockSurfaceOcclusionSampler;
import org.academy.api.client.render.vfxgraph.shape.SurfaceDetailSelector;
import org.academy.api.common.vfx.EffectScalarTransition;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.Map;

/** Three articulated pairs are graph-owned; the adapter supplies a neutral actor anchor and lifecycle. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class DarkmatterSixWingsVfxClient {
    private static final Map<LivingEntity, Wings> WINGS = new IdentityHashMap<>();
    private DarkmatterSixWingsVfxClient() { }

    /** Retained bootstrap entry: the legacy baked textured-wing renderer is no longer registered. */
    public static void register() { }
    public static int activeCount() { return WINGS.size(); }
    public static void strike(int actorId) {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(actorId) instanceof LivingEntity actor)) return;
        var wings = WINGS.get(actor);
        if (wings != null) wings.attackStarted = wings.effect.gameAgeSeconds();
    }

    /** Future non-player wing providers can reuse the same neutral actor anchor. */
    public static ActiveEffect attach(LivingEntity actor) {
        return WINGS.computeIfAbsent(actor, Wings::new).effect;
    }
    @SubscribeEvent public static void entityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Player player && player.level().isClientSide()
                && !player.isRemoved() && player.isAlive()
                && player.getData(AttachmentTypes.DARKMATTER_SIX_WINGS.get())) attach(player);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        WINGS.entrySet().removeIf(entry -> {
            var actor = entry.getKey();
            var wings = entry.getValue();
            if (actor.level() != Minecraft.getInstance().level || actor.isRemoved() || !actor.isAlive()
                    || wings.effect.isStopped()) {
                wings.effect.stop();
                return true;
            }
            return false;
        });
    }

    private static final class Wings {
        final LivingEntity actor;
        final ActiveEffect effect;
        final EffectScalarTransition opening = new EffectScalarTransition(0);
        final BlockSurfaceOcclusionSampler occlusion;
        final EffectParameterCache params = new EffectParameterCache();
        final SurfaceDetailSelector detail = new SurfaceDetailSelector();
        final Vector3f position = new Vector3f(), localCamera = new Vector3f();
        final Quaternionf rotation = new Quaternionf(), inverse = new Quaternionf();
        float attackStarted = -100;
        Wings(LivingEntity actor) {
            this.actor = actor;
            occlusion = new BlockSurfaceOcclusionSampler(actor, 6);
            effect = VfxGraphManager.INSTANCE.spawn(AcademyCraft.academy("vfxgraph/darkmatter_six_wings"),
                    actor.position().toVector3f());
            effect.setRenderDistance(128);
            effect.bindOcclusionSampler("occlusion", occlusion);
            effect.bindAnchor((transform, partialTick) -> {
                if (actor.level() != Minecraft.getInstance().level || actor.isRemoved() || !actor.isAlive()) return false;
                var point = actor.getPosition(partialTick);
                position.set((float) point.x, (float) point.y, (float) point.z);
                rotation.rotationY((float) Math.toRadians(-actor.yBodyRot));
                if (actor.isFallFlying()) rotation.rotateX((float) Math.toRadians(-actor.getXRot()));
                transform.translation(position).rotate(rotation);
                return true;
            });
            effect.bindFrame((active, camera, partialTick) -> {
                boolean enabled = !(actor instanceof Player player)
                        || player.getData(AttachmentTypes.DARKMATTER_SIX_WINGS.get());
                float time = active.gameAgeSeconds();
                var graph = active.effect();
                float transitionSeconds = Math.clamp(graph.floatParameter("transition_seconds", .30f), .05f, 2);
                float spread = opening.update(enabled ? 1 : 0, time, transitionSeconds);
                if (!enabled && spread <= 0) return false;
                occlusion.updateFrame(position, rotation, active.renderVisible() || time < .35f,
                        camera.position().distanceSquared(position) > 48 * 48 ? 12 : 4);
                inverse.set(rotation).conjugate().transform(localCamera.set(camera.position()).sub(position));
                params.begin(graph);
                params.putFloat("time", time);
                params.putFloat("spread", spread);
                params.putFloat("strength", spread);
                params.putFloat("grounded", actor.onGround() && !actor.isFallFlying() ? 1f : 0);
                params.putFloat("density", .65f);
                params.putFloat("flexibility", actor.isFallFlying() ? .20f : .12f);
                params.putFloat("attack", Math.max(0, (float) Math.sin(Math.clamp((time - attackStarted) / .32f, 0, 1) * Math.PI)));
                params.putVec3("camera", localCamera);
                params.putFloat("view_first_person", actor == Minecraft.getInstance().getCameraEntity()
                        && Minecraft.getInstance().options.getCameraType().isFirstPerson() ? 1f : 0);
                float distance = localCamera.length();
                params.putFloat("lod", Math.clamp(1 - (distance - 16) / 64, .2f, 1));
                float span = graph.floatParameter("span", 3.6f);
                float bounds = FeatherArrayGeometry.boundsRadius(span, graph.floatParameter("root_height", 1.25f), graph.floatParameter("root_depth", -.20f));
                int override = Math.round(graph.floatParameter("surface_detail_override", -1));
                params.putInt("surface_detail", override >= 0 ? Math.clamp(override, 0, 2)
                        : detail.update(Math.abs(span) * .5f, bounds, distance, camera.projection()));
                active.setCullingSphere(position, bounds);
                return true;
            });
        }
    }
}
