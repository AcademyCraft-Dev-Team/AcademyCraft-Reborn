package org.academy.internal.client.renderer.effect;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.academy.AcademyCraft;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.Render;
import org.academy.api.client.renderer.LineBoxRenderer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class PlatinumExecutionEffect {
    public static final int DURATION_TICKS = 40;
    private static final Map<UUID, DeathState> DEATHS = new ConcurrentHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private PlatinumExecutionEffect() {
    }

    public static void enqueue(UUID executionId, int entityId, double x, double y, double z,
                               float yRot, float width, float height, int durationTicks) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || executionId == null) return;
        var target = minecraft.level.getEntity(entityId);
        if (target != null) target.setInvisible(true);
        DEATHS.put(executionId, new DeathState(
                new Vec3(x, y, z),
                yRot,
                Math.max(0.3f, width),
                Math.max(0.5f, height),
                minecraft.level.getGameTime(),
                Math.max(1, durationTicks)
        ));
    }

    @SubscribeEvent
    public static void onLevelRender(LevelRenderEvent event) {
        if (DEATHS.isEmpty()) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            DEATHS.clear();
            return;
        }
        var renderType = Render.RenderTypes.MINE_DETECT_LINES;
        var camera = minecraft.gameRenderer.mainCamera().position();
        var currentTick = minecraft.level.getGameTime() + event.getPartialTick();

        for (var iterator = DEATHS.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            var state = entry.getValue();
            var progress = Mth.clamp((currentTick - state.startTick) / state.durationTicks, 0.0f, 1.0f);
            if (progress >= 1.0f) {
                DEATHS.remove(entry.getKey(), state);
                continue;
            }
            if (!state.burstSpawned && progress >= 0.45f) {
                state.burstSpawned = true;
                spawnBurst(state);
            }

            var collapseProgress = Mth.clamp(progress / 0.72f, 0.0f, 1.0f);
            var inverse = 1.0f - collapseProgress;
            var collapse = 81.0f * (1.0f - inverse * inverse * inverse);
            var flash = progress < 0.08f ? 1.0f - progress / 0.08f : 0.0f;
            var alpha = progress > 0.70f
                    ? Mth.clamp(1.0f - (progress - 0.70f) / 0.30f, 0.0f, 1.0f)
                    : 1.0f;
            var red = Mth.clamp(0.80f + flash * 0.20f, 0.0f, 1.0f);
            var green = Mth.clamp(0.85f + flash * 0.15f, 0.0f, 1.0f);
            var halfWidth = Math.max(0.15, state.width * 0.5);
            var localBounds = new AABB(
                    -halfWidth, 0.0, -halfWidth,
                    halfWidth, state.height, halfWidth
            );

            var matrices = event.getMatrixStack();
            matrices.pushPose();
            matrices.translate(
                    (float) (state.position.x - camera.x),
                    (float) (state.position.y - camera.y),
                    (float) (state.position.z - camera.z)
            );
            matrices.mulPose(Axis.YP.rotationDegrees(180.0f - state.yRot));
            matrices.mulPose(Axis.XP.rotationDegrees(collapse));
            event.submitCustomGeometry(renderType, (snapshot, consumer) ->
                    LineBoxRenderer.renderWireframeBox(
                            snapshot, consumer, localBounds, red, green, 1.0f, alpha
                    ));
            matrices.popPose();
        }
    }

    private static void spawnBurst(DeathState state) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var random = RANDOM;
        for (var i = 0; i < 36; i++) {
            var x = state.position.x + (random.nextDouble() - 0.5) * state.width;
            var y = state.position.y + random.nextDouble() * state.height;
            var z = state.position.z + (random.nextDouble() - 0.5) * state.width;
            var vx = (random.nextDouble() - 0.5) * 0.22;
            var vy = 0.02 + random.nextDouble() * 0.18;
            var vz = (random.nextDouble() - 0.5) * 0.22;
            level.addParticle(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.PORTAL,
                    x, y, z, vx, vy, vz);
        }
    }

    private static final class DeathState {
        private final Vec3 position;
        private final float yRot;
        private final float width;
        private final float height;
        private final long startTick;
        private final int durationTicks;
        private boolean burstSpawned;

        private DeathState(Vec3 position, float yRot, float width, float height,
                           long startTick, int durationTicks) {
            this.position = position;
            this.yRot = yRot;
            this.width = width;
            this.height = height;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }
    }
}
