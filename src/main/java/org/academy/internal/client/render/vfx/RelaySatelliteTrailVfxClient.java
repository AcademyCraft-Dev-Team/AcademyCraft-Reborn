package org.academy.internal.client.render.vfx;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.internal.common.world.entity.misaka.RelaySatelliteEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 中继卫星起飞 / 坠毁尾迹。
 *
 * <p>绑定约定（与 {@code RelaySatelliteRenderer} 对齐）：
 * <ul>
 *   <li>渲染把模型上抬 {@link #RENDER_Y_LIFT}，geo 机腹/支脚在模型 Y=0 → 世界喷嘴 ≈ 实体原点 + lift</li>
 *   <li>起飞时模型保持 Y-up 直立，推力方向固定为世界 -Y，禁止沿横向速度倾斜（否则火/模脱节）</li>
 *   <li>坠毁时模型 +Y 跟随速度，喷嘴在反速度方向</li>
 *   <li>持续特效在 {@link LevelRenderEvent} 用 partialTick 插值位置，避免 tick 坐标与渲染脱节</li>
 * </ul>
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class RelaySatelliteTrailVfxClient {
    private static final Identifier FIRE = AcademyCraft.academy("vfxgraph/demo_fire");
    private static final Identifier ENGINE_SMOKE = AcademyCraft.academy("vfxgraph/entity_smoke");
    private static final Identifier MIST_STREAM = AcademyCraft.academy("vfxgraph/aeromanip_mist_stream");
    private static final Identifier MIST_BURST = AcademyCraft.academy("vfxgraph/aeromanip_mist_burst");
    private static final Identifier MIST_RING = AcademyCraft.academy("vfxgraph/aeromanip_mist_ring");
    private static final Identifier MIST_VORTEX = AcademyCraft.academy("vfxgraph/aeromanip_mist_vortex");
    private static final Identifier MIST_FIELD = AcademyCraft.academy("vfxgraph/aeromanip_mist_field");

    /** 与 {@code RelaySatelliteRenderer} 的 {@code poseStack.translate(0, 0.15, 0)} 一致。 */
    private static final float RENDER_Y_LIFT = 0.15f;
    /**
     * 喷嘴略低于机腹支脚，避免火心吃进模型；单位：相对实体原点的世界 Y（起飞直立时）。
     */
    private static final float LAUNCH_NOZZLE_Y = RENDER_Y_LIFT - 0.04f;
    /** 坠毁时从 AABB 中心沿反速度退到机腹的距离。 */
    private static final float CRASH_NOZZLE_FROM_CENTER = 0.28f;

    /** 沿排气轴相对喷嘴的位移（芯焰 → 羽流 → 烟 → 蒸汽）。 */
    private static final float CORE_ALONG = 0.06f;
    private static final float PLUME_ALONG = 0.38f;
    private static final float SMOKE_ALONG = 0.72f;
    private static final float STREAM_ALONG = 0.55f;
    private static final float PUFF_ALONG = 0.32f;

    /**
     * 服务端起飞点为 {@code cabinPos.Y + 2}（垫块顶约 +0.5），卫星脚点在半空。
     * 垫面冲击必须钉在垫块碰撞顶面，不能用实体 Y。
     */
    private static final float PAD_SURFACE_EPS = 0.04f;
    /** 离台冲击：短促闪一下（秒）。 */
    private static final float PAD_BLAST_LIFE = 0.38f;
    /** 垫面洗雾：卫星离垫面超过该高度后停止补喷。 */
    private static final float PAD_WASH_MAX_HEIGHT = 2.5f;
    /** 垫面洗雾硬截止（tick），避免长程起飞进度把洗雾拖到十几秒。 */
    private static final int PAD_WASH_MAX_TICKS = 14;
    private static final float PAD_WASH_LIFE = 0.32f;

    private static final Map<RelaySatelliteEntity, TrailBundle> EFFECTS = new IdentityHashMap<>();

    private RelaySatelliteTrailVfxClient() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof RelaySatelliteEntity satellite) {
            ensureTrail(satellite);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            stopAll();
            return;
        }

        Iterator<Map.Entry<RelaySatelliteEntity, TrailBundle>> iterator = EFFECTS.entrySet().iterator();
        ArrayList<RelaySatelliteEntity> reattach = null;
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var satellite = entry.getKey();
            var bundle = entry.getValue();
            var mode = trailMode(satellite);
            if (mode == TrailMode.NONE || satellite.isRemoved() || bundle.isStopped()) {
                bundle.stop();
                iterator.remove();
                continue;
            }
            if (bundle.mode != mode) {
                bundle.stop();
                iterator.remove();
                // Mode flipped mid-life (e.g. launch→crash); re-attach without a full entity sweep.
                if (reattach == null) {
                    reattach = new ArrayList<>(2);
                }
                reattach.add(satellite);
                continue;
            }
            tickBundle(satellite, bundle, 1.0f);
        }
        if (reattach != null) {
            for (var satellite : reattach) {
                ensureTrail(satellite);
            }
        }
    }

    /**
     * 实体已按 partialTick 提交后再同步持续特效锚点，保证与模型同帧。
     */
    @SubscribeEvent
    public static void onLevelRender(LevelRenderEvent event) {
        if (EFFECTS.isEmpty()) {
            return;
        }
        float partialTick = event.getPartialTick();
        for (var entry : EFFECTS.entrySet()) {
            var satellite = entry.getKey();
            var bundle = entry.getValue();
            if (satellite.isRemoved() || trailMode(satellite) != bundle.mode) {
                continue;
            }
            syncContinuousAnchors(satellite, bundle, partialTick);
        }
    }

    private static void ensureTrail(RelaySatelliteEntity satellite) {
        var mode = trailMode(satellite);
        if (mode == TrailMode.NONE || EFFECTS.containsKey(satellite)) {
            return;
        }
        try {
            var bundle = mode == TrailMode.LAUNCH
                    ? spawnLaunch(satellite)
                    : spawnCrash(satellite);
            // Put first so a later timed-mist failure cannot orphan continuous fire without a map entry.
            EFFECTS.put(satellite, bundle);
        } catch (RuntimeException exception) {
            AcademyCraft.getLogger().warn("Unable to spawn relay satellite trail VFX ({})", mode, exception);
        }
    }

    private static TrailBundle spawnLaunch(RelaySatelliteEntity satellite) {
        var anchors = resolveAnchors(satellite, TrailMode.LAUNCH, 1.0f);
        // demo_fire 内置粒子尺寸约 0.12–0.35；起飞需要明显大于方块的推力柱。
        var core = spawnOriented(FIRE, along(anchors.nozzle, anchors.exhaust, CORE_ALONG), anchors.rotation, 2.4f);
        var plume = spawnOriented(FIRE, along(anchors.nozzle, anchors.exhaust, PLUME_ALONG), anchors.rotation, 3.2f);
        var smoke = spawnOriented(ENGINE_SMOKE, along(anchors.nozzle, anchors.exhaust, SMOKE_ALONG), anchors.rotation, 1.4f);
        bindSmoke(smoke, satellite, 1.6f, 0.7f);
        var padSurface = resolvePadSurface(satellite);
        var bundle = new TrailBundle(TrailMode.LAUNCH, core, plume, smoke, padSurface);
        try {
            firePadBlast(padSurface);
            pulseExhaust(satellite, bundle, 0f, anchors);
        } catch (RuntimeException exception) {
            AcademyCraft.getLogger().warn("Unable to spawn relay satellite launch pulse VFX", exception);
        }
        return bundle;
    }

    private static TrailBundle spawnCrash(RelaySatelliteEntity satellite) {
        var anchors = resolveAnchors(satellite, TrailMode.CRASH, 1.0f);
        var core = spawnOriented(FIRE, along(anchors.nozzle, anchors.exhaust, CORE_ALONG), anchors.rotation, 2.2f);
        var plume = spawnOriented(FIRE, along(anchors.nozzle, anchors.exhaust, PLUME_ALONG), anchors.rotation, 3.0f);
        var smoke = spawnOriented(ENGINE_SMOKE, along(anchors.nozzle, anchors.exhaust, SMOKE_ALONG), anchors.rotation, 1.6f);
        bindSmoke(smoke, satellite, 2.0f, 0.65f);
        var bundle = new TrailBundle(TrailMode.CRASH, core, plume, smoke, null);
        pulseCrashSteam(anchors);
        return bundle;
    }

    private static void firePadBlast(Vector3f padSurface) {
        // 钉在垫块碰撞顶面；寿命短促，卫星一离台就散。
        var deck = new Vector3f(padSurface.x, padSurface.y + PAD_SURFACE_EPS, padSurface.z);
        var up = new Quaternionf();
        spawnTimed(MIST_RING, deck, up, 1.2f, PAD_BLAST_LIFE);
        spawnTimed(MIST_BURST, deck, up, 1.0f, PAD_BLAST_LIFE * 0.9f);
        spawnTimed(MIST_VORTEX, deck, up, 0.85f, PAD_BLAST_LIFE);
        spawnTimed(MIST_FIELD, deck, up, 0.9f, PAD_BLAST_LIFE * 1.05f);
    }

    /**
     * 从卫星当前位置向下找第一块有碰撞的方块顶面（发射台 SHAPE 高 0.5）。
     * 回退：与服务端 {@code cabinPos.Y + 2} 对齐 → 垫顶约 {@code satY - 1.5}。
     */
    private static Vector3f resolvePadSurface(RelaySatelliteEntity satellite) {
        Level level = satellite.level();
        double x = satellite.getX();
        double y = satellite.getY();
        double z = satellite.getZ();
        var cursor = BlockPos.containing(x, y, z).mutable();
        int minY = level.getMinY();
        for (int i = 0; i < 12 && cursor.getY() >= minY; i++) {
            var state = level.getBlockState(cursor);
            var shape = state.getCollisionShape(level, cursor);
            if (!shape.isEmpty()) {
                float top = cursor.getY() + (float) shape.max(Direction.Axis.Y);
                return new Vector3f((float) x, top, (float) z);
            }
            cursor.move(Direction.DOWN);
        }
        return new Vector3f((float) x, (float) (y - 1.5), (float) z);
    }

    private static void tickBundle(RelaySatelliteEntity satellite, TrailBundle bundle, float partialTick) {
        var anchors = resolveAnchors(satellite, bundle.mode, partialTick);
        syncContinuousAnchors(satellite, bundle, anchors, partialTick);

        if (bundle.mode == TrailMode.LAUNCH) {
            float t = Mth.clamp(satellite.getLaunchProgress(partialTick), 0f, 1f);
            if (shouldWashPad(satellite, bundle, partialTick) && (bundle.ageTicks % 4) == 0) {
                var wash = new Vector3f(
                        bundle.padOrigin.x,
                        bundle.padOrigin.y + PAD_SURFACE_EPS,
                        bundle.padOrigin.z
                );
                float fade = 1f - (bundle.ageTicks / (float) PAD_WASH_MAX_TICKS);
                spawnTimed(MIST_BURST, wash, new Quaternionf(), 0.75f * fade, PAD_WASH_LIFE);
                spawnTimed(MIST_RING, wash, new Quaternionf(), 0.65f * fade, PAD_WASH_LIFE);
            }
            int interval = t < 0.18f ? 3 : (t < 0.55f ? 4 : 6);
            bundle.steamPulseTicks++;
            if (bundle.steamPulseTicks >= interval) {
                bundle.steamPulseTicks = 0;
                pulseExhaust(satellite, bundle, t, anchors);
            }
        } else {
            bundle.steamPulseTicks++;
            if (bundle.steamPulseTicks >= 4) {
                bundle.steamPulseTicks = 0;
                pulseCrashSteam(anchors);
            }
        }
        bundle.ageTicks++;
    }

    private static boolean shouldWashPad(RelaySatelliteEntity satellite, TrailBundle bundle, float partialTick) {
        if (bundle.padOrigin == null || bundle.ageTicks >= PAD_WASH_MAX_TICKS) {
            return false;
        }
        float height = (float) (satellite.getPosition(partialTick).y - bundle.padOrigin.y);
        return height < PAD_WASH_MAX_HEIGHT;
    }

    private static void syncContinuousAnchors(RelaySatelliteEntity satellite, TrailBundle bundle, float partialTick) {
        syncContinuousAnchors(satellite, bundle, resolveAnchors(satellite, bundle.mode, partialTick), partialTick);
    }

    private static void syncContinuousAnchors(
            RelaySatelliteEntity satellite,
            TrailBundle bundle,
            Anchors anchors,
            float partialTick
    ) {
        float t = bundle.mode == TrailMode.LAUNCH
                ? Mth.clamp(satellite.getLaunchProgress(partialTick), 0f, 1f)
                : 0.5f;
        float coreScale = bundle.mode == TrailMode.LAUNCH ? Mth.lerp(t, 2.6f, 1.1f) : 2.2f;
        float plumeScale = bundle.mode == TrailMode.LAUNCH ? Mth.lerp(t, 3.4f, 1.4f) : 3.0f;
        float smokeSize = bundle.mode == TrailMode.LAUNCH ? Mth.lerp(t, 1.9f, 1.15f) : 2.2f;
        float smokeAlpha = bundle.mode == TrailMode.LAUNCH ? Mth.lerp(t, 0.78f, 0.28f) : 0.6f;

        // 起飞时羽流/烟只沿 -Y 拉长，不随横向速度歪斜偏移。
        float plumeAlong = bundle.mode == TrailMode.LAUNCH ? Mth.lerp(t, PLUME_ALONG, PLUME_ALONG + 0.35f) : PLUME_ALONG;
        float smokeAlong = bundle.mode == TrailMode.LAUNCH ? Mth.lerp(t, SMOKE_ALONG, SMOKE_ALONG + 0.55f) : SMOKE_ALONG;

        bundle.coreFire.setScale(coreScale);
        bundle.coreFire.setRotation(anchors.rotation);
        bundle.coreFire.setPosition(along(anchors.nozzle, anchors.exhaust, CORE_ALONG));

        bundle.plumeFire.setScale(plumeScale);
        bundle.plumeFire.setRotation(anchors.rotation);
        bundle.plumeFire.setPosition(along(anchors.nozzle, anchors.exhaust, plumeAlong));

        bundle.engineSmoke.setRotation(anchors.rotation);
        bundle.engineSmoke.setPosition(along(anchors.nozzle, anchors.exhaust, smokeAlong));
        bindSmoke(bundle.engineSmoke, satellite, smokeSize, smokeAlpha);
    }

    private static void pulseExhaust(RelaySatelliteEntity satellite, TrailBundle bundle, float t, Anchors anchors) {
        float streamScale = Mth.lerp(t, 1.05f, 0.42f);
        float puffScale = Mth.lerp(t, 0.8f, 0.32f);
        float streamAlong = Mth.lerp(t, STREAM_ALONG, STREAM_ALONG + 0.45f);
        spawnTimed(MIST_STREAM, along(anchors.nozzle, anchors.exhaust, streamAlong), anchors.rotation, streamScale, 1.05f);
        if (t < 0.65f) {
            spawnTimed(MIST_BURST, along(anchors.nozzle, anchors.exhaust, PUFF_ALONG), anchors.rotation, puffScale, 0.9f);
        }
        if (t < 0.12f) {
            spawnTimed(MIST_RING, along(anchors.nozzle, anchors.exhaust, 0.2f), anchors.rotation, 0.55f, 0.7f);
        }
    }

    private static void pulseCrashSteam(Anchors anchors) {
        spawnTimed(MIST_STREAM, along(anchors.nozzle, anchors.exhaust, STREAM_ALONG + 0.35f), anchors.rotation, 1.0f, 1.0f);
        spawnTimed(MIST_BURST, along(anchors.nozzle, anchors.exhaust, PUFF_ALONG), anchors.rotation, 0.75f, 0.85f);
    }

    /**
     * 计算喷嘴世界坐标与排气轴向。起飞强制 -Y；坠毁沿 -velocity。
     */
    private static Anchors resolveAnchors(RelaySatelliteEntity satellite, TrailMode mode, float partialTick) {
        Vec3 pos = satellite.getPosition(partialTick);
        if (mode == TrailMode.LAUNCH) {
            var exhaust = new Vec3(0.0, -1.0, 0.0);
            var nozzle = new Vector3f(
                    (float) pos.x,
                    (float) (pos.y + LAUNCH_NOZZLE_Y),
                    (float) pos.z
            );
            return new Anchors(nozzle, exhaust, orientedAlong(exhaust));
        }

        Vec3 motion = satellite.getDeltaMovement();
        Vec3 exhaust = motion.lengthSqr() > 1.0e-8
                ? motion.normalize().scale(-1.0)
                : new Vec3(0.0, 1.0, 0.0);
        // 实体 AABB 中心 → 沿排气退到机腹喷嘴。
        double centerY = pos.y + satellite.getBbHeight() * 0.5;
        var nozzle = new Vector3f(
                (float) (pos.x + exhaust.x * CRASH_NOZZLE_FROM_CENTER),
                (float) (centerY + exhaust.y * CRASH_NOZZLE_FROM_CENTER),
                (float) (pos.z + exhaust.z * CRASH_NOZZLE_FROM_CENTER)
        );
        return new Anchors(nozzle, exhaust, orientedAlong(exhaust));
    }

    private static Vector3f along(Vector3f nozzle, Vec3 exhaust, float distance) {
        return new Vector3f(
                nozzle.x + (float) exhaust.x * distance,
                nozzle.y + (float) exhaust.y * distance,
                nozzle.z + (float) exhaust.z * distance
        );
    }

    private static void bindSmoke(ActiveEffect smoke, RelaySatelliteEntity satellite, float size, float alpha) {
        smoke.bind("smoke_size", () -> Value.of(size));
        smoke.bind("smoke_alpha", () -> Value.of(alpha));
        smoke.bind("smoke_frame", () -> Value.of((float) (satellite.tickCount & 3)));
    }

    private static ActiveEffect spawnOriented(Identifier asset, Vector3f pos, Quaternionf rotation, float scale) {
        var effect = VfxGraphManager.INSTANCE.spawn(asset, pos);
        effect.setScale(scale);
        effect.setAlwaysVisible(true);
        effect.setRotation(rotation);
        return effect;
    }

    private static void spawnTimed(Identifier asset, Vector3f pos, Quaternionf rotation,
            float scale, float lifetimeSeconds) {
        var effect = VfxGraphManager.INSTANCE.spawn(asset, pos);
        effect.setScale(scale);
        effect.setAlwaysVisible(true);
        effect.setRotation(rotation);
        effect.setLifetimeSeconds(lifetimeSeconds);
    }

    private static TrailMode trailMode(RelaySatelliteEntity satellite) {
        if (satellite.isCrashing()) {
            return TrailMode.CRASH;
        }
        if (satellite.isLaunching()) {
            return TrailMode.LAUNCH;
        }
        return TrailMode.NONE;
    }

    private static Quaternionf orientedAlong(Vec3 direction) {
        var forward = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
        if (forward.lengthSquared() < 1.0e-6f) {
            forward.set(0f, -1f, 0f);
        }
        forward.normalize();
        return new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), forward);
    }

    private static void stopAll() {
        for (var bundle : EFFECTS.values()) {
            bundle.stop();
        }
        EFFECTS.clear();
    }

    private enum TrailMode {
        NONE,
        LAUNCH,
        CRASH
    }

    private record Anchors(Vector3f nozzle, Vec3 exhaust, Quaternionf rotation) {
    }

    private static final class TrailBundle {
        private final TrailMode mode;
        private final ActiveEffect coreFire;
        private final ActiveEffect plumeFire;
        private final ActiveEffect engineSmoke;
        private final Vector3f padOrigin;
        private int steamPulseTicks;
        private int ageTicks;

        private TrailBundle(TrailMode mode, ActiveEffect coreFire, ActiveEffect plumeFire,
                ActiveEffect engineSmoke, Vector3f padOrigin) {
            this.mode = mode;
            this.coreFire = coreFire;
            this.plumeFire = plumeFire;
            this.engineSmoke = engineSmoke;
            this.padOrigin = padOrigin;
        }

        private boolean isStopped() {
            return coreFire.isStopped() || plumeFire.isStopped() || engineSmoke.isStopped();
        }

        private void stop() {
            coreFire.stop();
            plumeFire.stop();
            engineSmoke.stop();
        }
    }
}
