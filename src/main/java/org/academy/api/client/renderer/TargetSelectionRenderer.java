package org.academy.api.client.renderer;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.internal.client.render.vfx.TargetSelectionOverlay;

/** Reusable monochrome targeting markers for player skills, entity controllers and addon previews. */
public final class TargetSelectionRenderer {
    private TargetSelectionRenderer() { }

    public enum Palette {
        BLOODFLOW(0xEF3D4C), TELEPORT(0xA769FF);
        private final int rgb;
        Palette(int rgb) { this.rgb = rgb; }
        public float red() { return ((rgb >> 16) & 255) / 255.0f; }
        public float green() { return ((rgb >> 8) & 255) / 255.0f; }
        public float blue() { return (rgb & 255) / 255.0f; }
    }

    public enum State {
        PREVIEW(0.45f), LOCKED(0.85f), REGION(0.60f), SECONDARY(0.45f);
        private final float opacity;
        State(float opacity) { this.opacity = opacity; }
        public float opacity() { return opacity; }
    }

    /** Stable selection keys let a marker fade when its owner stops submitting it. */
    public static void renderEntity(LevelRenderEvent event, Object selectionKey, Entity target,
                                    Palette palette, State state) {
        if (target == null || target.isRemoved()) return;
        var bounds = TargetSelectionModel.interpolatedBounds(target.getBoundingBox(),
                target.position(), target.getPosition(event.getPartialTick()));
        var grounded = target.onGround() || target.level().getBlockCollisions(target,
                TargetSelectionModel.supportProbe(target.getBoundingBox())).iterator().hasNext();
        TargetSelectionOverlay.request(event, selectionKey, target.getUUID(), bounds, palette, state, false, grounded);
    }

    public static void renderDestination(LevelRenderEvent event, Object selectionKey, Vec3 center, Palette palette) {
        if (center == null) return;
        var bounds = new AABB(center.x - 0.5, center.y - 0.5, center.z - 0.5,
                center.x + 0.5, center.y + 0.5, center.z + 0.5);
        TargetSelectionOverlay.request(event, selectionKey, State.PREVIEW, bounds, palette, State.PREVIEW, false);
    }

    public static void renderRegion(LevelRenderEvent event, Object selectionKey, AABB bounds, Palette palette) {
        TargetSelectionOverlay.request(event, selectionKey, State.REGION, bounds, palette, State.REGION, true);
    }
}
