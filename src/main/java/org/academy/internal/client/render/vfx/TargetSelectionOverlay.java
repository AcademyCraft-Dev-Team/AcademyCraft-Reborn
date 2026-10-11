package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.Render;
import org.academy.api.client.renderer.TargetSelectionModel;
import org.academy.api.client.renderer.TargetSelectionRenderer.Palette;
import org.academy.api.client.renderer.TargetSelectionRenderer.State;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Collects skill requests before drawing once, with short acquisition and release transitions. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class TargetSelectionOverlay {
    private static final double TRANSITION_SECONDS = 0.12;
    private static final Map<Object, Marker> MARKERS = new HashMap<>();
    private static ClientLevel markerLevel;
    private TargetSelectionOverlay() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level != markerLevel) { MARKERS.clear(); markerLevel = level; }
    }

    public static void request(LevelRenderEvent event, Object key, Object target, AABB bounds,
                               Palette palette, State state, boolean region) {
        request(event, key, target, bounds, palette, state, region, false);
    }

    public static void request(LevelRenderEvent event, Object key, Object target, AABB bounds,
                               Palette palette, State state, boolean region, boolean grounded) {
        var level = Minecraft.getInstance().level;
        if (level == null || key == null || bounds == null || palette == null || state == null) return;
        if (level != markerLevel) { MARKERS.clear(); markerLevel = level; }
        var now = animationTime();
        if (TargetSelectionModel.layout(bounds, region, now) == null) return;
        var marker = MARKERS.computeIfAbsent(key, _ -> new Marker());
        if (!Objects.equals(marker.target, target) || marker.palette != palette) marker.started = now;
        marker.target = target; marker.bounds = bounds; marker.palette = palette; marker.state = state;
        marker.region = region; marker.grounded = grounded; marker.lastSeen = now; marker.event = event;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(LevelRenderEvent event) {
        var level = Minecraft.getInstance().level;
        if (level != markerLevel || level == null) { MARKERS.clear(); markerLevel = level; return; }
        var now = animationTime();
        var iterator = MARKERS.values().iterator();
        while (iterator.hasNext()) {
            var marker = iterator.next();
            var elapsed = Math.max(0, now - marker.lastSeen);
            if (marker.event != event && elapsed >= TRANSITION_SECONDS) { iterator.remove(); continue; }
            var acquire = Math.clamp((now - marker.started) / TRANSITION_SECONDS, 0, 1);
            var release = marker.event == event ? 1 : Math.clamp(1 - elapsed / TRANSITION_SECONDS, 0, 1);
            var opacity = (float) (marker.state.opacity() * acquire * release);
            if (opacity <= 0) continue;
            draw(event, marker, now, opacity);
        }
    }

    private static void draw(LevelRenderEvent event, Marker marker, double now, float opacity) {
        var camera = event.getCameraPosition();
        var relative = marker.bounds.move(-camera.x, -camera.y, -camera.z);
        var layout = TargetSelectionModel.layout(relative, marker.region, now, marker.grounded);
        if (layout == null) return;
        var minimumWidth = Math.min(0.13, relative.getCenter().length() * 0.0016);
        var palette = marker.palette;
        event.submitPoseGeometry(Render.RenderTypes.TARGET_SELECTION, (pose, vertices) ->
                TargetSelectionModel.emit(layout, now, minimumWidth, (a, b, c, d, alpha) -> {
                    vertex(vertices, pose.pose(), a, palette, alpha * opacity);
                    vertex(vertices, pose.pose(), b, palette, alpha * opacity);
                    vertex(vertices, pose.pose(), c, palette, alpha * opacity);
                    vertex(vertices, pose.pose(), d, palette, alpha * opacity);
                }));
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, Vec3 point, Palette color, float alpha) {
        vertices.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .setColor(color.red(), color.green(), color.blue(), alpha);
    }

    // Server clock corrections must not rewind acquisition or release of a client-only marker.
    private static double animationTime() {
        return System.nanoTime() * 1.0e-9;
    }

    private static final class Marker {
        Object target;
        AABB bounds;
        Palette palette;
        State state;
        boolean region, grounded;
        double started, lastSeen;
        LevelRenderEvent event;
    }
}
