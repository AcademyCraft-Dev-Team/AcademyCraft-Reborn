package org.academy.internal.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.joml.Quaternionf;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Entity state and confirmed events only; all geometry and materials are authored in VFXGraph. */
public final class IronSandVfxClient {
    private static final Map<Integer, ActiveEffect> RINGS = new HashMap<>();
    private static final Map<Integer, ActiveEffect> CLOUDS = new HashMap<>();
    private static final ArrayDeque<ActiveEffect> TRANSIENTS = new ArrayDeque<>();
    private static ClientLevel level;

    private IronSandVfxClient() {}

    public static void tick() {
        var current = Minecraft.getInstance().level;
        if (level != current) { clear(); level = current; }
        if (level == null) return;
        prune(RINGS, false);
        prune(CLOUDS, true);
        TRANSIENTS.removeIf(ActiveEffect::isExpired);
        for (var player : level.players()) {
            if (!player.isAlive() || player.isRemoved()) continue;
            var state = player.getData(AttachmentTypes.IRON_SAND_DATA.get());
            if (state.defense()) RINGS.computeIfAbsent(player.getId(), _ -> attached("defense", player, false));
            if (state.cloudRadius() > 0) CLOUDS.computeIfAbsent(player.getId(), _ -> attached("cloud", player, true));
        }
    }

    private static void prune(Map<Integer, ActiveEffect> effects, boolean cloud) {
        effects.entrySet().removeIf(entry -> {
            var entity = level.getEntity(entry.getKey());
            var state = entity == null ? null : entity.getData(AttachmentTypes.IRON_SAND_DATA.get());
            boolean remove = entity == null || !entity.isAlive() || entity.isRemoved()
                    || (cloud ? state.cloudRadius() <= 0 : !state.defense());
            if (remove) entry.getValue().stop();
            return remove;
        });
    }

    private static ActiveEffect attached(String name, Entity entity, boolean cloud) {
        var effect = spawn(name, entity.position());
        effect.follow(entity);
        effect.bindFrame((active, camera, partialTick) -> {
            if (entity.level() != Minecraft.getInstance().level || !entity.isAlive()) return false;
            float radius = cloud ? entity.getData(AttachmentTypes.IRON_SAND_DATA.get()).cloudRadius()
                    : "defense".equals(name) ? 1.2f : 1.4f;
            active.effect().setLiveParam("radius", Value.of(radius));
            active.setCullingSphere(active.position(), radius + 3);
            active.effect().setLiveParam("detail", Value.of(detail(entity)));
            return true;
        });
        return effect;
    }

    public static void whip(int id) {
        tick();
        var entity = level == null ? null : level.getEntity(id);
        if (entity == null) return;
        var effect = attached("whip", entity, false);
        effect.setRotation(new Quaternionf().rotationY((float) Math.toRadians(-entity.getYRot())));
        effect.bindFrame((active, camera, partialTick) -> {
            float radius = entity.getData(AttachmentTypes.IRON_SAND_DATA.get()).whipRange();
            active.effect().setLiveParam("radius", Value.of(radius));
            active.effect().setLiveParam("detail", Value.of(detail(entity)));
            active.setCullingSphere(active.position(), radius + 2);
            return entity.isAlive() && entity.level() == Minecraft.getInstance().level;
        });
        transientEffect(effect, 0.5f);
    }

    public static void defenseEvent(int id, boolean intercept, Vec3 vector) {
        tick();
        var entity = level == null ? null : level.getEntity(id);
        if (entity == null || !Double.isFinite(vector.lengthSqr())) return;
        if (intercept) {
            var effect = spawn("intercept", vector);
            effect.bindFrame((active, camera, partialTick) -> entity.level() == Minecraft.getInstance().level);
            effect.setCullingSphere(vector.toVector3f(), 3);
            transientEffect(effect, 0.24f);
        } else {
            var effect = attached("guard", entity, false);
            effect.setRotation(new Quaternionf().rotationY((float) Math.atan2(vector.x, vector.z)));
            effect.effect().setLiveParam("source_elevation", Value.of((float) Math.clamp(vector.y, -1, 1)));
            transientEffect(effect, 0.65f);
        }
    }

    private static ActiveEffect spawn(String name, Vec3 position) {
        var effect = VfxGraphManager.INSTANCE.spawn(AcademyCraft.academy("vfxgraph/iron_sand_" + name), position.toVector3f());
        effect.bindGameTime("time");
        effect.setRenderDistance(64);
        effect.effect().setLiveParam("seed", Value.of((float) (level == null ? 42 : level.getGameTime() % 100000)));
        return effect;
    }

    private static void transientEffect(ActiveEffect effect, float duration) {
        effect.setGameTimeLifetimeSeconds(duration);
        TRANSIENTS.removeIf(ActiveEffect::isExpired);
        while (TRANSIENTS.size() >= 24) TRANSIENTS.removeFirst().stop();
        TRANSIENTS.addLast(effect);
    }

    private static float detail(Entity entity) {
        var mc = Minecraft.getInstance();
        float quality = mc.options.particles().get() == ParticleStatus.MINIMAL ? 0.35f
                : mc.options.particles().get() == ParticleStatus.DECREASED ? 0.65f : 1;
        return mc.player != null && mc.player.distanceToSqr(entity) > 32 * 32 ? quality * 0.5f : quality;
    }

    public static void clear() {
        RINGS.values().forEach(ActiveEffect::stop);
        CLOUDS.values().forEach(ActiveEffect::stop);
        TRANSIENTS.forEach(ActiveEffect::stop);
        RINGS.clear(); CLOUDS.clear(); TRANSIENTS.clear(); level = null;
    }
}
