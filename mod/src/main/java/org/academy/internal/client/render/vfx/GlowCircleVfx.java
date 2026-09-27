package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.device.GpuDevice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.render.vfx.*;
import org.academy.api.common.vfx.SkillVfxState;
import org.academy.internal.client.renderer.entity.GlowCircleRenderer;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.GlowCircle;

import java.util.List;

/**
 * Plays a detached GlowCircle mirror without registering it in ClientLevel.
 */
public final class GlowCircleVfx implements Vfx {
    private final GlowCircle mirror;
    private final ClientLevel level;
    private final long startedAt = System.nanoTime();
    private final long lifetimeNanos;

    private GlowCircleVfx(GlowCircle mirror, int lifetimeTicks) {
        this.mirror = mirror;
        level = (ClientLevel) mirror.level();
        lifetimeNanos = Math.max(1, lifetimeTicks) * 50_000_000L;
    }

    public static void register() {
        VfxRegistry.register(Data.class, VfxPhase.WORLD_TRANSLUCENT, new Renderer());
    }

    public static void spawn(ClientLevel level, SkillVfxState.DistortionRing state) {
        var mirror = new GlowCircle(EntityTypes.GLOW_CIRCLE.get(), level);
        mirror.applyVisualSnapshot(state);
        VfxManager.INSTANCE.spawn(new GlowCircleVfx(mirror, state.lifetimeTicks()));
    }

    @Override
    public void sample(VfxFrameContext context, VfxSink sink) {
        var position = mirror.position();
        if (!VfxVisibility.sphere(context.camera(), position, 4.0f)) return;
        float progress = Math.clamp((System.nanoTime() - startedAt) / (float) lifetimeNanos, 0.0f, 1.0f);
        sink.push(new Data(
                position,
                mirror.getXRot(),
                mirror.getYRot(),
                GlowCircleRenderer.radiusAt(progress),
                mirror.getEffectOwnerId()
        ));
    }

    @Override
    public boolean isAlive() {
        return Minecraft.getInstance().level == level && System.nanoTime() - startedAt < lifetimeNanos;
    }

    public record Data(Vec3 position, float xRot, float yRot, float radius, int ownerEntityId)
            implements VfxRenderData {
    }

    private static final class Renderer implements VfxRenderer<Data> {
        private final PoseStack poseStack = new PoseStack();

        @Override
        public void init(GpuDevice device) {
        }

        @Override
        public void render(VfxRenderContext context, List<? extends Data> data) {
            var camera = context.cameraPos();
            for (var ring : data) {
                if (!GlowCircleRenderer.isVisibleForCurrentCamera(ring.ownerEntityId())) continue;
                var position = ring.position();
                poseStack.pushPose();
                poseStack.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
                GlowCircleRenderer.renderRing(
                        poseStack, ring.radius(), ring.xRot(), ring.yRot());
                poseStack.popPose();
            }
        }
    }
}
