package org.academy.internal.client.render.vfx;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.renderer.OrientedCylinderBeam;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.internal.common.world.entity.misaka.OrbitalStrikeProxyEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_ADDITIVE;
import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_GLOW_ADDITIVE;
import static org.academy.api.client.render.Render.RenderTypes.POS_COLOR_QUADS_NO_DEPTH_WRITE;

/**
 * 轨道打击：落点 VFX + 天→地激光柱。
 *
 * <p>激光在 {@link LevelRenderEvent} 画（相机相对），避免代理体 AABB 被视锥裁掉。
 * 开火顶点高度由服务端 {@code STRIKE_APEX_HEIGHT} 限制（非满轨高），追踪距离保持在 256 量级。</p>
 * <p>节奏：接近薄雾 → 开火瞬间震环/爆发 → 持续芯焰+蒸汽脉冲。</p>
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class OrbitalStrikeProxyVfxClient {
    private static final Identifier FIRE = AcademyCraft.academy("vfxgraph/demo_fire");
    private static final Identifier MIST_FIELD = AcademyCraft.academy("vfxgraph/aeromanip_mist_field");
    private static final Identifier MIST_STREAM = AcademyCraft.academy("vfxgraph/aeromanip_mist_stream");
    private static final Identifier MIST_BURST = AcademyCraft.academy("vfxgraph/aeromanip_mist_burst");
    private static final Identifier MIST_RING = AcademyCraft.academy("vfxgraph/aeromanip_mist_ring");
    private static final Identifier SPARK = AcademyCraft.academy("vfxgraph/spark");
    private static final Identifier BURST = AcademyCraft.academy("vfxgraph/demo_burst");

    /** 与服务器 {@code FIRE_PULSE_INTERVAL} 对齐的蒸汽/碎屑脉冲。 */
    private static final int PULSE_INTERVAL = 5;
    private static final float FIRE_SCALE = 1.15f;
    private static final float FIRE_SCALE_HYPER = 1.55f;
    private static final float FIELD_SCALE = 0.85f;
    private static final float FIELD_SCALE_HYPER = 1.15f;
    private static final float STREAM_SCALE = 0.55f;
    private static final float STREAM_SCALE_HYPER = 0.75f;
    private static final float IDLE_SCALE = 0.40f;
    private static final float RING_SCALE = 1.10f;
    private static final float BURST_SCALE = 0.95f;
    private static final float SPARK_SCALE = 0.70f;
    private static final float ONE_SHOT_LIFE = 0.85f;

    private static final Map<OrbitalStrikeProxyEntity, Bundle> EFFECTS = new IdentityHashMap<>();

    private OrbitalStrikeProxyVfxClient() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof OrbitalStrikeProxyEntity proxy) {
            ensure(proxy);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            stopAll();
            return;
        }

        Iterator<Map.Entry<OrbitalStrikeProxyEntity, Bundle>> iterator = EFFECTS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var proxy = entry.getKey();
            var bundle = entry.getValue();
            if (proxy.isRemoved() || bundle.isDead()) {
                bundle.stop();
                iterator.remove();
                continue;
            }
            boolean firing = proxy.isFiring();
            if (bundle.firing != firing) {
                bundle.stop();
                iterator.remove();
                continue;
            }
            tickBundle(proxy, bundle);
        }

        for (var entity : level.entitiesForRendering()) {
            if (entity instanceof OrbitalStrikeProxyEntity proxy) {
                ensure(proxy);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelRender(LevelRenderEvent event) {
        if (EFFECTS.isEmpty()) {
            return;
        }
        var camera = event.getCameraPosition();
        float partial = event.getPartialTick();
        var poseStack = event.getPoseStack();
        var collector = event.getSubmitNodeCollector();

        // Iterate tracked proxies (not entitiesForRendering): the orbit AABB is often
        // outside the frustum while the player stares at the melting pad.
        for (var entry : EFFECTS.entrySet()) {
            var proxy = entry.getKey();
            if (proxy.isRemoved() || !proxy.isFiring()) {
                continue;
            }
            Vec3 startWorld = proxy.getPosition(partial).add(0.0, 0.15, 0.0);
            BlockPos impact = proxy.getImpact();
            Vec3 endWorld = new Vec3(impact.getX() + 0.5, impact.getY() + 0.08, impact.getZ() + 0.5);
            submitDownlinkBeam(
                    poseStack,
                    collector,
                    startWorld.subtract(camera),
                    endWorld.subtract(camera),
                    proxy.isHyper(),
                    proxy.tickCount + partial
            );
            var bundle = entry.getValue();
            if (bundle.firing) {
                updateFireAnchors(proxy, bundle, partial);
            }
        }
    }

    private static void submitDownlinkBeam(
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.SubmitNodeCollector collector,
            Vec3 start,
            Vec3 end,
            boolean hyper,
            float age
    ) {
        var delta = end.subtract(start);
        double length = delta.length();
        if (!(length > 0.5) || !Double.isFinite(length)) {
            return;
        }
        if (!OrientedCylinderBeam.preparePose(poseStack, start, delta, (float) length)) {
            return;
        }
        // Soft power pulse — sci-fi column, not a flat stick.
        float pulse = 0.88f + 0.12f * Mth.sin(age * 0.35f);
        float shell = (hyper ? 1.05f : 0.72f) * pulse;
        float mid = (hyper ? 0.42f : 0.28f) * pulse;
        float core = (hyper ? 0.16f : 0.11f) * pulse;
        float len = (float) length;

        OrientedCylinderBeam.submitLayer(
                collector, poseStack, len, shell,
                POS_COLOR_QUADS_NO_DEPTH_WRITE, 1.0f, 0.28f, 0.06f, 0.42f
        );
        OrientedCylinderBeam.submitLayer(
                collector, poseStack, len, mid,
                POS_COLOR_QUADS_ADDITIVE, 1.0f, 0.55f, 0.12f, 0.72f
        );
        OrientedCylinderBeam.submitLayer(
                collector, poseStack, len, core,
                POS_COLOR_QUADS_GLOW_ADDITIVE, 1.0f, 0.92f, 0.55f, 0.98f
        );
        OrientedCylinderBeam.submitLayer(
                collector, poseStack, len, core * 0.45f,
                POS_COLOR_QUADS_ADDITIVE, 1.0f, 1.0f, 0.95f, 1.0f
        );
        poseStack.popPose();
    }

    private static void ensure(OrbitalStrikeProxyEntity proxy) {
        if (EFFECTS.containsKey(proxy)) {
            return;
        }
        try {
            EFFECTS.put(proxy, spawnBundle(proxy));
        } catch (RuntimeException exception) {
            AcademyCraft.getLogger().warn("Unable to spawn orbital strike proxy VFX", exception);
        }
    }

    private static Bundle spawnBundle(OrbitalStrikeProxyEntity proxy) {
        boolean firing = proxy.isFiring();
        boolean hyper = proxy.isHyper();
        if (!firing) {
            var idle = spawn(MIST_FIELD, proxyPos(proxy), IDLE_SCALE, true);
            return new Bundle(false, hyper, null, null, null, idle, 0);
        }
        var impact = impactSurface(proxy.getImpact());
        // Opening beat — shock ring + pressure burst + ion spark (one-shot).
        spawnOneShot(MIST_RING, impact, RING_SCALE * (hyper ? 1.35f : 1.0f), ONE_SHOT_LIFE);
        spawnOneShot(MIST_BURST, impact, BURST_SCALE * (hyper ? 1.25f : 1.0f), 0.7f);
        spawnOneShot(BURST, new Vector3f(impact.x, impact.y + 0.4f, impact.z),
                0.55f * (hyper ? 1.3f : 1.0f), 0.65f);
        spawnOneShot(SPARK, impact, SPARK_SCALE * (hyper ? 1.2f : 1.0f), 0.55f);

        float fireScale = hyper ? FIRE_SCALE_HYPER : FIRE_SCALE;
        float fieldScale = hyper ? FIELD_SCALE_HYPER : FIELD_SCALE;
        var fire = spawn(FIRE, impact, fireScale, true);
        fire.setRotation(upRotation());
        var field = spawn(MIST_FIELD, impact, fieldScale, true);
        var stream = spawn(MIST_STREAM, new Vector3f(impact.x, impact.y + 0.45f, impact.z),
                hyper ? STREAM_SCALE_HYPER : STREAM_SCALE, true);
        return new Bundle(true, hyper, fire, field, stream, null, 0);
    }

    private static void tickBundle(OrbitalStrikeProxyEntity proxy, Bundle bundle) {
        if (!bundle.firing) {
            if (bundle.idle != null) {
                bundle.idle.setPosition(proxyPos(proxy));
                bundle.idle.setRotation(upRotation());
            }
            return;
        }
        updateFireAnchors(proxy, bundle, 0.0f);
        bundle.pulseTicker++;
        if (bundle.pulseTicker % PULSE_INTERVAL != 0) {
            return;
        }
        // Sustained column wash — mist_stream/burst are spawn_burst, must re-pulse.
        var impact = impactSurface(proxy.getImpact());
        float streamScale = bundle.hyper ? STREAM_SCALE_HYPER : STREAM_SCALE;
        float burstScale = BURST_SCALE * (bundle.hyper ? 1.15f : 0.85f);
        spawnOneShot(MIST_STREAM, new Vector3f(impact.x, impact.y + 0.55f, impact.z), streamScale, 0.45f);
        spawnOneShot(MIST_BURST, impact, burstScale, 0.4f);
        if (bundle.pulseTicker % (PULSE_INTERVAL * 2) == 0) {
            spawnOneShot(SPARK, new Vector3f(impact.x, impact.y + 0.2f, impact.z),
                    SPARK_SCALE * (bundle.hyper ? 1.1f : 0.85f), 0.35f);
        }
    }

    private static void updateFireAnchors(OrbitalStrikeProxyEntity proxy, Bundle bundle, float unusedPartial) {
        var impact = impactSurface(proxy.getImpact());
        if (bundle.fire != null) {
            bundle.fire.setPosition(impact);
            bundle.fire.setRotation(upRotation());
        }
        if (bundle.field != null) {
            bundle.field.setPosition(impact);
            bundle.field.setRotation(upRotation());
        }
        if (bundle.stream != null) {
            bundle.stream.setPosition(new Vector3f(impact.x, impact.y + 0.45f, impact.z));
            bundle.stream.setRotation(upRotation());
        }
    }

    private static ActiveEffect spawn(Identifier asset, Vector3f pos, float scale, boolean alwaysVisible) {
        var effect = VfxGraphManager.INSTANCE.spawn(asset, pos);
        effect.setScale(scale);
        effect.setAlwaysVisible(alwaysVisible);
        effect.setRotation(upRotation());
        return effect;
    }

    private static void spawnOneShot(Identifier asset, Vector3f pos, float scale, float lifeSeconds) {
        try {
            var effect = spawn(asset, pos, scale, true);
            effect.setLifetimeSeconds(lifeSeconds);
        } catch (RuntimeException exception) {
            AcademyCraft.getLogger().debug("Orbital strike one-shot VFX failed: {}", asset, exception);
        }
    }

    /** Impact pad surface — not mid-air above the target block. */
    private static Vector3f impactSurface(BlockPos impact) {
        return new Vector3f(impact.getX() + 0.5f, impact.getY() + 0.05f, impact.getZ() + 0.5f);
    }

    private static Vector3f proxyPos(OrbitalStrikeProxyEntity proxy) {
        return new Vector3f((float) proxy.getX(), (float) proxy.getY(), (float) proxy.getZ());
    }

    private static Quaternionf upRotation() {
        return new Quaternionf();
    }

    private static void stopAll() {
        for (var bundle : EFFECTS.values()) {
            bundle.stop();
        }
        EFFECTS.clear();
    }

    private static final class Bundle {
        private final boolean firing;
        private final boolean hyper;
        private final ActiveEffect fire;
        private final ActiveEffect field;
        private final ActiveEffect stream;
        private final ActiveEffect idle;
        private int pulseTicker;

        private Bundle(
                boolean firing,
                boolean hyper,
                ActiveEffect fire,
                ActiveEffect field,
                ActiveEffect stream,
                ActiveEffect idle,
                int pulseTicker
        ) {
            this.firing = firing;
            this.hyper = hyper;
            this.fire = fire;
            this.field = field;
            this.stream = stream;
            this.idle = idle;
            this.pulseTicker = pulseTicker;
        }

        private boolean isDead() {
            if (firing) {
                return fire != null && fire.isStopped();
            }
            return idle != null && idle.isStopped();
        }

        private void stop() {
            if (fire != null) {
                fire.stop();
            }
            if (field != null) {
                field.stop();
            }
            if (stream != null) {
                stream.stop();
            }
            if (idle != null) {
                idle.stop();
            }
        }
    }
}
