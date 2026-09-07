package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import org.academy.api.client.render.vfx.*;
import org.academy.api.common.vfx.SkillVfxState;
import org.academy.internal.client.renderer.entity.KineticShockwaveRenderer;

/** Short local playback survives the original server tick and entity lifetime. */
public final class ShockwaveVfx implements Vfx {
    private final SkillVfxState.Burst burst;
    private final net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
    private final long startedAt = System.nanoTime();
    private final long lifetime;
    public ShockwaveVfx(SkillVfxState.Burst burst) {
        this.burst = burst;
        lifetime = Math.max(1, burst.lifetimeTicks()) * 50_000_000L;
    }
    public static void register() {
        VfxRegistry.register(Data.class, VfxPhase.WORLD_TRANSLUCENT, new Renderer());
    }
    @Override
    public void sample(VfxFrameContext ctx, VfxSink sink) {
        if (!VfxVisibility.sphere(ctx.camera(), burst.position(), burst.radius() + 2f)) return;
        float progress = Math.clamp((System.nanoTime() - startedAt) / (float) lifetime, 0f, 1f);
        sink.push(new Data(burst, progress));
    }
    @Override
    public boolean isAlive() {
        return net.minecraft.client.Minecraft.getInstance().level == level && System.nanoTime() - startedAt < lifetime;
    }

    public record Data(SkillVfxState.Burst burst, float progress) implements VfxRenderData {}
    private static final class Renderer implements VfxRenderer<Data> {
        private final PoseStack pose = new PoseStack();
        @Override public void init(GpuDevice device) {}
        @Override public void render(VfxRenderContext ctx, List<? extends Data> data) {
            for (var item : data) {
                var p = item.burst.position();
                var camera = ctx.cameraPos();
                var d = item.burst.direction();
                float yRot = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90f;
                float xRot = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
                pose.pushPose();
                pose.translate(p.x - camera.x, p.y - camera.y, p.z - camera.z);
                KineticShockwaveRenderer.renderRings(pose, item.burst.radius() * item.progress,
                        item.progress, item.burst.intensity(), xRot, yRot);
                pose.popPose();
            }
        }
    }
}
