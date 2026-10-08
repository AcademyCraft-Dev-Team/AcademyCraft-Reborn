package org.academy.internal.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import org.academy.AcademyCraft;
import org.academy.api.client.render.vfxgraph.GraphEffect;
import org.academy.api.client.render.post.DirectionalFieldRenderer;
import org.academy.api.client.render.post.DirectionalFieldStyle;
import org.academy.api.client.render.post.WorldSurfaceMasks;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.EffectEnvironmentSample;
import org.academy.api.client.render.vfxgraph.runtime.EffectParameterCache;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.common.vfx.DirectionalArea;
import org.academy.internal.common.network.DarkmatterVisualPacket;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-defined membership with a maintained graph for medium and scene-output style. */
public final class InterferenceFieldClient {
    private static final Map<Integer, Entry> FIELDS = new LinkedHashMap<>();
    private static @Nullable ClientLevel world;
    private static List<DirectionalArea> frame = List.of();
    private static List<DirectionalFieldRenderer.Field> styledFrame = List.of();
    private static boolean frameDirty = true;
    private InterferenceFieldClient() { }

    public static void accept(DarkmatterVisualPacket packet) {
        resetWorld();
        if (world == null || !world.dimension().identifier().equals(packet.dimension)) return;
        if (!packet.active) { remove(packet.entityId); return; }
        if (packet.area == null) return;
        var actor = world.getEntity(packet.entityId) instanceof LivingEntity living ? living : null;
        var entry = FIELDS.get(packet.entityId);
        if (entry != null && (entry.actor != actor || entry.effect.isStopped())) { remove(packet.entityId); entry = null; }
        if (entry == null) {
            try {
                entry = new Entry(actor, packet, world);
                FIELDS.put(packet.entityId, entry);
                frameDirty = true;
            } catch (RuntimeException failure) {
                AcademyCraft.getLogger().error("Unable to create interference graph", failure);
                return;
            }
        }
        entry.refresh(packet);
    }

    private static void remove(int id) { var previous = FIELDS.remove(id); if (previous != null) { previous.effect.stop(); frameDirty = true; } }

    private static void resetWorld() {
        var next = Minecraft.getInstance().level;
        if (world == next) return;
        FIELDS.values().forEach(entry -> entry.effect.stop());
        FIELDS.clear(); frame = List.of(); styledFrame = List.of(); world = next;
        frameDirty = true;
        WorldSurfaceMasks.reset();
        DirectionalFieldRenderer.close();
    }

    public static void beginFrame() {
        tick();
        for (var entry : FIELDS.values()) if (entry.updateStyle()) frameDirty = true;
        if (frameDirty) {
            frame = FIELDS.values().stream().map(entry -> entry.packet.area).toList();
            styledFrame = FIELDS.values().stream().map(entry -> new DirectionalFieldRenderer.Field(entry.packet.area, entry.style)).toList();
            frameDirty = false;
        }
        WorldSurfaceMasks.beginFrame(frame);
    }

    public static void tick() {
        resetWorld();
        var iterator = FIELDS.values().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!entry.valid()) { entry.effect.stop(); iterator.remove(); frameDirty = true; }
        }
    }

    public static List<DirectionalArea> areas() { return frame; }
    public static List<DirectionalFieldRenderer.Field> styledAreas() { return styledFrame; }
    public static int activeCount() { return FIELDS.size(); }
    public static void close() {
        FIELDS.values().forEach(entry -> entry.effect.stop());
        FIELDS.clear(); frame = List.of(); styledFrame = List.of();
        frameDirty = true;
        WorldSurfaceMasks.close(); DirectionalFieldRenderer.close();
    }

    private static final class Entry {
        final @Nullable LivingEntity actor;
        final ClientLevel level;
        final ActiveEffect effect;
        DarkmatterVisualPacket packet;
        long tick;
        final EffectParameterCache params = new EffectParameterCache();
        final EffectEnvironmentSample environment = new EffectEnvironmentSample();
        final Vector3f origin = new Vector3f(), eye = new Vector3f(), forward = new Vector3f(), sun = new Vector3f();
        final Quaternionf rotation = new Quaternionf(), inverse = new Quaternionf();
        final Matrix4f cameraInverse = new Matrix4f();
        boolean constantsDirty = true, styleDirty = true;
        DirectionalFieldStyle style;
        GraphEffect styleGraph;
        long styleTick = Long.MIN_VALUE;
        float surface, air, edge;
        int scatter;

        Entry(@Nullable LivingEntity actor, DarkmatterVisualPacket packet, ClientLevel level) {
            this.actor = actor; this.packet = packet; this.level = level;
            effect = VfxGraphManager.INSTANCE.spawn(AcademyCraft.academy("vfxgraph/darkmatter_interference"), packet.position.toVector3f());
            effect.setRenderDistance(128);
            effect.bindFrame((active, camera, partialTick) -> {
                if (!valid()) return false;
                active.setPosition(origin); active.setRotation(rotation);
                active.setCullingSphere(origin, (float) this.packet.area.radius() + 1);
                inverse.transform(eye.set(camera.position()).sub(origin));
                cameraInverse.set(camera.viewRotation()).invert().transformDirection(forward.set(0, 0, -1));
                inverse.transform(forward).normalize();
                environment.update(level, this.packet.position.x, this.packet.position.y, this.packet.position.z, this.packet.entityId);
                var graph = active.effect();
                boolean reloaded = params.begin(graph);
                if (reloaded || constantsDirty) {
                    params.putFloat("range", (float) this.packet.area.radius());
                    params.putFloat("width", this.packet.width); params.putFloat("height", this.packet.height);
                    params.putFloat("alpha", alpha()); params.putFloat("beta", beta()); params.putFloat("gamma", this.packet.gamma);
                    constantsDirty = false;
                }
                params.putFloat("time", active.gameAgeSeconds());
                params.putVec3("camera", eye); params.putVec3("camera_forward", forward);
                params.putFloat("view_first_person", Minecraft.getInstance().options.getCameraType().isFirstPerson() ? 1f : 0f);
                params.putFloat("light", environment.light());
                params.putVec3("sun_direction", inverse.transform(sun.set(environment.sunlight())));
                return true;
            });
        }

        void refresh(DarkmatterVisualPacket packet) {
            if (!this.packet.area.equals(packet.area) || this.packet.alpha != packet.alpha || this.packet.beta != packet.beta) { frameDirty = true; styleDirty = true; }
            constantsDirty |= this.packet.width != packet.width || this.packet.height != packet.height
                    || this.packet.area.radius() != packet.area.radius() || this.packet.alpha != packet.alpha || this.packet.beta != packet.beta || this.packet.gamma != packet.gamma;
            this.packet = packet; tick = level.getGameTime();
            origin.set((float) packet.position.x, (float) packet.position.y, (float) packet.position.z);
            var direction = packet.area.direction();
            rotation.rotationTo(0, 0, 1, (float) direction.x, (float) direction.y, (float) direction.z);
            inverse.set(rotation).conjugate();
        }
        boolean updateStyle() {
            var graph = effect.effect();
            long now = level.getGameTime();
            if (!styleDirty && styleGraph == graph && styleTick == now) return false;
            styleTick = now;
            float s = graph.floatParameter("field_surface_strength", .16f), a = graph.floatParameter("field_air_strength", .10f), e = graph.floatParameter("field_edge_width", .22f);
            int samples = Math.round(graph.floatParameter("field_scatter_count", 32));
            if (!styleDirty && styleGraph == graph && surface == s && air == a && edge == e && scatter == samples) return false;
            surface = s; air = a; edge = e; scatter = samples; styleGraph = graph; styleDirty = false;
            var next = new DirectionalFieldStyle(s, a, e, samples).phase(alpha(), beta());
            boolean changed = !next.equals(style); style = next; return changed;
        }
        float alpha() { return packet.alpha + packet.beta > 0 ? packet.alpha / (packet.alpha + packet.beta) : 0.5f; }
        float beta() { return packet.alpha + packet.beta > 0 ? packet.beta / (packet.alpha + packet.beta) : 0.5f; }
        boolean valid() {
            return world == level && !effect.isStopped() && level.getGameTime() - tick <= 12
                    && (actor == null || !actor.isRemoved() && actor.isAlive() && level.getEntity(actor.getId()) == actor);
        }
    }
}
