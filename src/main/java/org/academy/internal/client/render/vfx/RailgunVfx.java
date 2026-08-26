package org.academy.internal.client.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.api.client.resources.R;
import org.academy.internal.common.ability.electromaster.skills.lv4.Railgun;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Map;

import static org.academy.internal.common.ability.electromaster.skills.lv4.Railgun.CHARGE_TIME;
import static org.academy.internal.common.ability.electromaster.skills.lv4.Railgun.RELEASE_VISUAL_TICKS;

public final class RailgunVfx implements Vfx {
    private static final int RING_SEGMENTS = 28;
    private static final Vector2f UV0 = new Vector2f(0.0f, 0.0f);
    private static final Vector2f UV1 = new Vector2f(0.0f, 1.0f);
    private static final Vector2f UV2 = new Vector2f(1.0f, 1.0f);
    private static final Vector2f UV3 = new Vector2f(1.0f, 0.0f);

    @Override
    public void sample(VfxFrameContext ctx, VfxSink sink) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) return;
        var roots = WingAvatarRegistry.entries();

        for (var entry : roots.entrySet()) {
            var entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Player player)) continue;
            if (minecraft.options.getCameraType().isFirstPerson() && player == minecraft.player) continue;
            var data = player.getExistingDataOrNull(AttachmentTypes.RAILGUN_DATA);
            if (data == null) continue;

            var ticks = data.ticks() + ctx.partialTick();
            var strength = visualStrength(data, ticks);
            if (strength <= 0.0f) continue;

            var age = player.tickCount + ctx.partialTick();
            var root = new Matrix4f(entry.getValue());
            var camera = ctx.camera().pos();
            root.translate(camera.x, camera.y, camera.z);

            var handX = data.rightHand() ? -0.32f : 0.32f;
            var local = new Matrix4f().translate(handX, 0.55f, -0.16f);
            submitHandRings(sink, root, local, age, strength);

            if (data.coinReturnHint()) {
                var mainHandX = data.mainHandRight() ? -0.32f : 0.32f;
                var hintLocal = new Matrix4f().translate(mainHandX, 0.55f, -0.16f);
                submitCoinReturnHint(sink, root, hintLocal, age);
            }
        }

        if (minecraft.options.getCameraType().isFirstPerson() && minecraft.player != null) {
            submitFirstPerson(ctx, sink, minecraft.player);
        }
    }

    private static void submitFirstPerson(VfxFrameContext ctx, VfxSink sink, Player player) {
        var data = player.getExistingDataOrNull(AttachmentTypes.RAILGUN_DATA);
        if (data == null) return;
        var ticks = data.ticks() + ctx.partialTick();
        var strength = visualStrength(data, ticks);
        if (strength <= 0.0f) return;

        var camera = ctx.camera();
        var root = new Matrix4f()
                .translation(camera.pos())
                .rotate(camera.orientation());
        var age = player.tickCount + ctx.partialTick();
        var handX = data.rightHand() ? 0.34f : -0.34f;
        var local = new Matrix4f().translate(handX, -0.20f, -0.30f);
        submitHandRings(sink, root, local, age, strength);

        if (data.coinReturnHint()) {
            var mainHandX = data.mainHandRight() ? 0.34f : -0.34f;
            var hintLocal = new Matrix4f().translate(mainHandX, -0.20f, -0.30f);
            submitCoinReturnHint(sink, root, hintLocal, age);
        }
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    private static float visualStrength(Railgun.Data data, float ticks) {
        if (data.released()) {
            return Mth.clamp(1.0f - ticks / RELEASE_VISUAL_TICKS, 0.0f, 1.0f);
        }
        if (CHARGE_TIME <= 0) return 1.0f;
        return Mth.clamp(ticks / CHARGE_TIME, 0.15f, 1.0f);
    }

    private static void submitHandRings(VfxSink sink, Matrix4f root, Matrix4f local,
                                        float time, float strength) {
        renderRingPlane(sink, root, local, time, 0, 0.30f, 0.026f, strength);
        var second = new Matrix4f(local)
                .rotateX(66.0f * Mth.DEG_TO_RAD)
                .rotateZ(time * 5.0f * Mth.DEG_TO_RAD);
        renderRingPlane(sink, root, second, time, 1, 0.25f, 0.022f, strength * 0.88f);
        var third = new Matrix4f(local)
                .rotateY(72.0f * Mth.DEG_TO_RAD)
                .rotateZ(-time * 6.5f * Mth.DEG_TO_RAD);
        renderRingPlane(sink, root, third, time, 2, 0.21f, 0.019f, strength * 0.76f);
    }

    private static void submitCoinReturnHint(VfxSink sink, Matrix4f root, Matrix4f local, float time) {
        var pulse = 0.45f + 0.55f * Math.abs(Mth.sin(time * 0.85f));
        var hint = new Matrix4f(local).rotateZ(-time * 9.0f * Mth.DEG_TO_RAD);
        renderRingPlane(sink, root, hint, time, 4, 0.36f, 0.034f, pulse);
    }

    private static void renderRingPlane(VfxSink sink, Matrix4f root, Matrix4f local,
                                        float time, int ringIndex, float radius,
                                        float thickness, float alpha) {
        var world = new Matrix4f(root).mul(local);
        var phase = time * (0.18f + ringIndex * 0.035f) + ringIndex * 2.1f;
        for (var segment = 0; segment < RING_SEGMENTS; segment++) {
            if ((segment + ringIndex * 3) % 11 == 0) continue;
            var angle0 = Mth.TWO_PI * segment / RING_SEGMENTS + phase;
            var angle1 = Mth.TWO_PI * (segment + 1) / RING_SEGMENTS + phase;
            var noise0 = Mth.sin(segment * 2.71f + time * 0.83f + ringIndex * 4.2f) * 0.018f;
            var noise1 = Mth.sin((segment + 1) * 2.71f + time * 0.83f + ringIndex * 4.2f) * 0.018f;
            var radius0 = radius + noise0;
            var radius1 = radius + noise1;
            var halfWidth0 = thickness * (0.75f + 0.25f * Mth.sin(time + segment));
            var halfWidth1 = thickness * (0.75f + 0.25f * Mth.sin(time + segment + 1));
            var a0 = ringVertex(world, angle0, radius0 - halfWidth0, 0.0f);
            var a1 = ringVertex(world, angle0, radius0 + halfWidth0, 1.0f);
            var b1 = ringVertex(world, angle1, radius1 + halfWidth1, 1.0f);
            var b0 = ringVertex(world, angle1, radius1 - halfWidth1, 0.0f);
            sink.push(new RailgunRingData(
                    R.textures.ability.electromaster.skill.arc_generate.effect.line_segment,
                    a0, a1, b1, b0,
                    UV0, UV1, UV2, UV3,
                    new Vector4f(0.82f, 0.93f, 1.0f, alpha)
            ));
        }
    }

    private static Vector3f ringVertex(Matrix4f world, float angle, float radius, float u) {
        var x = Mth.cos(angle) * radius;
        var y = Mth.sin(angle) * radius;
        var z = Mth.sin(angle * 3.0f) * 0.014f;
        return new Vector3f(x, y, z).mulPosition(world);
    }

}
