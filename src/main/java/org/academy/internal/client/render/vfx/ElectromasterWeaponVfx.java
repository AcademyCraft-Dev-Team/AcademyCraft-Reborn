package org.academy.internal.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.player.Player;
import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.api.client.resources.R;
import org.academy.internal.common.ability.electromaster.skills.lv3.magneticweapon.MagneticWeapon;
import org.academy.internal.common.ability.electromaster.skills.lv4.IronSandArsenal;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

import static org.academy.AcademyCraft.academy;

public final class ElectromasterWeaponVfx implements Vfx {
    public static final ContextKey<MagneticWeapon.Data> MAGNETIC_CONTEXT =
            new ContextKey<>(academy("magnetic_weapon"));
    public static final ContextKey<IronSandArsenal.Data> IRON_SAND_CONTEXT =
            new ContextKey<>(academy("iron_sand_operation"));
    public static final ContextKey<Integer> ENTITY_ID_CONTEXT =
            new ContextKey<>(academy("wing_entity_id"));
    public static final int SWEEP_DURATION_TICKS = 10;
    private static final int IRON_SAND_PARTICLES = 24;
    private static final Vector2f UV0 = new Vector2f(0.0f, 0.0f);
    private static final Vector2f UV1 = new Vector2f(1.0f, 0.0f);
    private static final Vector2f UV2 = new Vector2f(1.0f, 1.0f);
    private static final Vector2f UV3 = new Vector2f(0.0f, 1.0f);

    static final SweepAnimationTimeline<SweepMarker> IRON_SAND_SWEEPS =
            new SweepAnimationTimeline<>();
    private static ClientLevel animationLevel;

    public static void enqueueIronSandSweep(int entityId) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.getEntity(entityId) == null) return;
        if (animationLevel != minecraft.level) {
            clearSweeps();
            animationLevel = minecraft.level;
        }
        IRON_SAND_SWEEPS.enqueue(
                entityId,
                minecraft.level.getGameTime(),
                SweepMarker.INSTANCE
        );
    }

    public static void clientTick() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clearSweeps();
            return;
        }
        if (animationLevel != minecraft.level) {
            clearSweeps();
            animationLevel = minecraft.level;
            return;
        }
        IRON_SAND_SWEEPS.prune(
                minecraft.level.getGameTime(),
                SWEEP_DURATION_TICKS,
                entityId -> minecraft.level.getEntity(entityId) != null
        );
    }

    public static void clearSweeps() {
        IRON_SAND_SWEEPS.clear();
        animationLevel = null;
    }

    @Override
    public void sample(VfxFrameContext ctx, VfxSink sink) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        // World-space positions work in both camera modes and do not depend on avatar render order.
        for (var player : level.players()) {
            var ironSand = player.getData(AttachmentTypes.IRON_SAND_DATA.get());
            if (!ironSand.active()) continue;
            var position = player.getPosition(ctx.partialTick());
            if (position.distanceToSqr(ctx.camera().pos().x, ctx.camera().pos().y, ctx.camera().pos().z) > 160 * 160) continue;
            var effectTime = player.tickCount + ctx.partialTick();
            var currentTick = (double) level.getGameTime() + ctx.partialTick();
            var yaw = Mth.rotLerp(ctx.partialTick(), player.yRotO, player.getYRot());
            var root = new Matrix4f().translation((float) position.x, (float) position.y, (float) position.z)
                    .rotateY(-yaw * Mth.DEG_TO_RAD).scale(1, -1, -1);
            renderIronSand(sink, root, ironSand, effectTime, currentTick,
                    IRON_SAND_SWEEPS.entries(player.getId()));
        }
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    private static void renderIronSand(
            VfxSink sink,
            Matrix4f root,
            IronSandArsenal.Data state,
            float effectTime,
            double currentTick,
            List<SweepAnimationTimeline.Entry<SweepMarker>> animations
    ) {
        for (var i = 0; state.defense() && i < 12; i++) {
            var angle = effectTime * 0.075f + i * (float) (Mth.TWO_PI / 12.0);
            var radius = 0.72f + (i % 3) * 0.13f;
            var y = -0.25f - (i % 4) * 0.43f;
            var local = new Matrix4f()
                    .translate(Mth.cos(angle) * radius, y, Mth.sin(angle) * radius)
                    .rotateY(-effectTime * 5.0f * Mth.DEG_TO_RAD + i * 31.0f * Mth.DEG_TO_RAD)
                    .scale(0.22f);
            pushQuad(sink, root, local, 0.72f);
        }

        if (state.cloudRadius() > 0) renderCloud(sink, root, effectTime, state.cloudRadius());

        for (var entry : animations) {
            var progress = SweepAnimationTimeline.progress(entry, currentTick, SWEEP_DURATION_TICKS);
            if (progress < 0.0f || progress >= 1.0f) continue;
            renderThirdPersonSweep(sink, root, progress, state.whipRange());
        }
    }

    private static void renderThirdPersonSweep(VfxSink sink, Matrix4f root, float progress, float range) {
        var eased = progress * progress * (3.0f - 2.0f * progress);
        var sweepAngle = -60.0f + eased * 120.0f;
        var segments = 24;
        for (var i = 0; i < segments; i++) {
            var radialProgress = i / (float) (segments - 1);
            var radius = 0.8f + radialProgress * (range - 0.8f);
            var trailingAngle = (1.0f - radialProgress) * 24.0f;
            var angle = (sweepAngle - trailingAngle) * Mth.DEG_TO_RAD;
            var local = new Matrix4f()
                    .translate(Mth.sin(angle) * radius,
                            -1.0f + Mth.sin(progress * Mth.PI) * 0.16f + Mth.sin(i * 0.72f) * 0.06f,
                            -Mth.cos(angle) * radius)
                    .rotateY(angle + 90.0f * Mth.DEG_TO_RAD)
                    .rotateZ((-18.0f + radialProgress * 36.0f) * Mth.DEG_TO_RAD);
            var scale = 0.24f + radialProgress * 0.34f;
            local.scale(scale, scale, scale);
            pushQuad(sink, root, local, (0.9f - radialProgress * 0.18f) * (1.0f - progress * 0.22f));
        }
    }

    private static void renderCloud(VfxSink sink, Matrix4f root, float time, float radius) {
        // Several revolving chains make the occupied volume readable while leaving sight gaps.
        for (var band = 0; band < 6; band++) {
            var ringRadius = radius * (0.28f + band * 0.14f);
            for (var segment = 0; segment < 24; segment++) {
                var angle = segment * Mth.TWO_PI / 24 + time * (band % 2 == 0 ? 0.055f : -0.07f);
                var height = -1.0f + Mth.sin(angle * 2 + band) * ringRadius * 0.32f;
                var local = new Matrix4f().translate(Mth.sin(angle) * ringRadius, height,
                                Mth.cos(angle) * ringRadius)
                        .rotateY(angle).rotateZ(time * 0.3f + segment)
                        .scale(0.2f + band * 0.045f);
                pushQuad(sink, root, local, 0.52f);
            }
        }
    }

    private static void pushQuad(VfxSink sink, Matrix4f root, Matrix4f local, float alpha) {
        var world = new Matrix4f(root).mul(local);
        var p0 = new Vector3f(-1.0f, -1.0f, 0.0f).mulPosition(world);
        var p1 = new Vector3f(1.0f, -1.0f, 0.0f).mulPosition(world);
        var p2 = new Vector3f(1.0f, 1.0f, 0.0f).mulPosition(world);
        var p3 = new Vector3f(-1.0f, 1.0f, 0.0f).mulPosition(world);
        sink.push(new ElectromasterWeaponData(
                R.textures.iron_sand_arsenal_effect,
                p0, p1, p2, p3,
                UV0, UV1, UV2, UV3,
                new Vector4f(0.78f, 0.82f, 0.88f, alpha)
        ));
    }

    enum SweepMarker {
        INSTANCE
    }
}
