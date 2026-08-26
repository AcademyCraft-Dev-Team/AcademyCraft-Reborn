package org.academy.internal.client.render.vfx;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.joml.Vector3f;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class PlatinumExecutionVfxClient {
    public static final int DURATION_TICKS = 40;
    private static final Identifier EXECUTION_ASSET = AcademyCraft.academy("vfxgraph/platinum_execution");
    private static final Map<UUID, ExecutionEffect> ACTIVE = new LinkedHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private PlatinumExecutionVfxClient() {
    }

    public static void register() {
        // Graph 资产与通用电弧渲染器由 VfxGraphManager 统一管理。
    }

    public static void enqueue(UUID executionId, int entityId, double x, double y, double z,
                               float yRot, float width, float height, int durationTicks) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null || executionId == null) return;
        var target = level.getEntity(entityId);
        if (target != null) target.setInvisible(true);

        var previous = ACTIVE.remove(executionId);
        if (previous != null) {
            VfxGraphManager.INSTANCE.stop(previous.effect);
        }
        float safeWidth = Math.max(0.3f, width);
        float safeHeight = Math.max(0.5f, height);
        int safeDuration = Math.max(1, durationTicks);
        try {
            var effect = VfxGraphManager.INSTANCE.spawn(
                    EXECUTION_ASSET, new Vector3f((float) x, (float) y, (float) z));
            var state = new ExecutionEffect(
                    effect,
                    new Vec3(x, y, z),
                    yRot,
                    safeWidth,
                    safeHeight,
                    level.getGameTime(),
                    safeDuration);
            effect.bind("progress", () -> Value.of(state.progress()));
            effect.bind("width", () -> Value.of(state.width));
            effect.bind("height", () -> Value.of(state.height));
            effect.bind("yaw", () -> Value.of(state.yaw));
            ACTIVE.put(executionId, state);
        } catch (RuntimeException exception) {
            AcademyCraft.getLogger().warn("Unable to spawn platinum execution VFX graph", exception);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            for (var state : ACTIVE.values()) {
                VfxGraphManager.INSTANCE.stop(state.effect);
            }
            ACTIVE.clear();
            return;
        }
        var iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            var state = iterator.next().getValue();
            float progress = state.progress();
            if (!state.burstSpawned && progress >= 0.45f) {
                state.burstSpawned = true;
                spawnBurst(state);
            }
            if (progress >= 1f || state.effect.isStopped()) {
                VfxGraphManager.INSTANCE.stop(state.effect);
                iterator.remove();
            }
        }
    }

    private static void spawnBurst(ExecutionEffect state) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (int i = 0; i < 36; i++) {
            double x = state.position.x + (RANDOM.nextDouble() - 0.5) * state.width;
            double y = state.position.y + RANDOM.nextDouble() * state.height;
            double z = state.position.z + (RANDOM.nextDouble() - 0.5) * state.width;
            double vx = (RANDOM.nextDouble() - 0.5) * 0.22;
            double vy = 0.02 + RANDOM.nextDouble() * 0.18;
            double vz = (RANDOM.nextDouble() - 0.5) * 0.22;
            level.addParticle(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.PORTAL,
                    x, y, z, vx, vy, vz);
        }
    }

    private static final class ExecutionEffect {
        private final ActiveEffect effect;
        private final Vec3 position;
        private final float yaw;
        private final float width;
        private final float height;
        private final long startTick;
        private final int durationTicks;
        private boolean burstSpawned;

        private ExecutionEffect(ActiveEffect effect, Vec3 position, float yaw, float width, float height,
                                long startTick, int durationTicks) {
            this.effect = effect;
            this.position = position;
            this.yaw = yaw;
            this.width = width;
            this.height = height;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
        }

        private float progress() {
            var minecraft = Minecraft.getInstance();
            var level = minecraft.level;
            if (level == null) return 1f;
            float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            return Mth.clamp(
                    (float) ((level.getGameTime() + partialTick - startTick) / durationTicks),
                    0f,
                    1f);
        }
    }
}
