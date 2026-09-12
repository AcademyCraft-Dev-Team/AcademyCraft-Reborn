package org.academy.internal.client.render.vfx;

import com.mojang.math.Axis;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.block.entity.EnergyLaserTowerBlockEntity;
import org.academy.internal.common.world.level.block.entity.OrbitSkyHooks;
import org.academy.internal.server.misaka.MisakaRelayOrbits;

/**
 * In-orbit sky satellite: drawn when the camera is near <em>below the satellite slot</em>,
 * not when standing at the laser tower. Tower BE is only a lightweight data source (no orbit entity).
 * During orbital strike the mark follows the synced strike aim so it stays on the power-beam tip.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class MisakaOrbitSkyClient {
    /** Horizontal range under the satellite where the sky model may appear. */
    private static final double UNDER_SLOT_RANGE = 96.0;
    private static final double UNDER_SLOT_RANGE_SQR = UNDER_SLOT_RANGE * UNDER_SLOT_RANGE;
    /** Wider gate while striking — player often stands at the impact, not under the old orbit ring. */
    private static final double UNDER_STRIKE_RANGE = 160.0;
    private static final double UNDER_STRIKE_RANGE_SQR = UNDER_STRIKE_RANGE * UNDER_STRIKE_RANGE;
    private static final float SCALE_MIN = 3.0f;
    private static final float SCALE_MAX = 12.0f;
    private static final int PRUNE_INTERVAL = 40;

    private static final Map<BlockPos, OrbitMark> ORBITS = new ConcurrentHashMap<>();
    private static final ItemStackRenderState CACHED_NORMAL = new ItemStackRenderState();
    private static final ItemStackRenderState CACHED_HYPER = new ItemStackRenderState();
    private static ClientLevel itemCacheLevel;
    private static boolean itemCacheReady;
    private static int pruneTicker;

    static {
        OrbitSkyHooks.setHooks(MisakaOrbitSkyClient::sync, MisakaOrbitSkyClient::remove);
    }

    private MisakaOrbitSkyClient() {
    }

    /** Called from laser-tower client sync / unload. Cheap map update only. */
    public static void sync(EnergyLaserTowerBlockEntity tower) {
        if (tower.getLevel() == null || !tower.getLevel().isClientSide() || !tower.isMain()) {
            return;
        }
        var key = tower.getBlockPos().immutable();
        if (tower.isOrbiting()) {
            float y = tower.getOrbitVisualY();
            if (!(y > 1.0f) && tower.getLevel() instanceof ClientLevel clientLevel) {
                y = (float) MisakaRelayOrbits.visualOrbitY(clientLevel, MisakaRelayOrbits.DEFAULT_ORBIT_HEIGHT);
            }
            ORBITS.put(key, snapshot(tower, key, y));
        } else {
            ORBITS.remove(key);
        }
    }

    private static OrbitMark snapshot(EnergyLaserTowerBlockEntity tower, BlockPos key, float orbitY) {
        if (tower.isStrikeAiming()) {
            var aim = tower.getStrikeAim();
            return new OrbitMark(
                    key,
                    tower.getOrbitAngleSeed(),
                    tower.isOrbitHyper(),
                    orbitY,
                    true,
                    (float) aim.x,
                    (float) aim.y,
                    (float) aim.z
            );
        }
        return new OrbitMark(key, tower.getOrbitAngleSeed(), tower.isOrbitHyper(), orbitY, false, 0f, 0f, 0f);
    }

    public static void remove(BlockPos mainPos) {
        if (mainPos != null) {
            ORBITS.remove(mainPos.immutable());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ORBITS.clear();
        itemCacheReady = false;
        itemCacheLevel = null;
        pruneTicker = 0;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ORBITS.isEmpty()) {
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level == null) {
            ORBITS.clear();
            return;
        }
        if (++pruneTicker < PRUNE_INTERVAL) {
            return;
        }
        pruneTicker = 0;
        Iterator<Map.Entry<BlockPos, OrbitMark>> it = ORBITS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (!(level.getBlockEntity(entry.getKey()) instanceof EnergyLaserTowerBlockEntity tower)
                    || !tower.isOrbiting()) {
                it.remove();
            } else {
                // Refresh snapshot from BE so seed/y/strike aim stay current without per-frame BE lookup in render.
                float y = tower.getOrbitVisualY();
                if (!(y > 1.0f)) {
                    y = (float) MisakaRelayOrbits.visualOrbitY(level, MisakaRelayOrbits.DEFAULT_ORBIT_HEIGHT);
                }
                entry.setValue(snapshot(tower, entry.getKey(), y));
            }
        }
    }

    @SubscribeEvent
    public static void onLevelRender(LevelRenderEvent event) {
        if (ORBITS.isEmpty()) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        // Prefer looking up under the sat; slight facing allowance for edge of FOV.
        boolean lookingUp = player.getXRot() < -12.0f;
        var camera = event.getCameraPosition();
        long time = level.getGameTime();
        float partial = event.getPartialTick();
        ensureItemCache(level);

        var poseStack = event.getPoseStack();
        var collector = event.getSubmitNodeCollector();
        for (var mark : ORBITS.values()) {
            var slot = mark.slot(time);
            double dx = camera.x - slot.x;
            double dz = camera.z - slot.z;
            double rangeSqr = mark.strikeAiming ? UNDER_STRIKE_RANGE_SQR : UNDER_SLOT_RANGE_SQR;
            // Gate on satellite ground projection — independent of laser-tower distance.
            if (dx * dx + dz * dz > rangeSqr) {
                continue;
            }
            if (!mark.strikeAiming && !lookingUp) {
                var toSlot = slot.subtract(camera);
                double distSq = toSlot.lengthSqr();
                if (distSq < 1.0e-6
                        || player.getViewVector(partial).dot(toSlot.normalize()) <= 0.25) {
                    continue;
                }
            }
            double dist = camera.distanceTo(slot);
            float scale = (float) Mth.clamp(Math.max(32.0, dist) * 0.028, SCALE_MIN, SCALE_MAX);
            var item = mark.hyper ? CACHED_HYPER : CACHED_NORMAL;
            if (item.isEmpty()) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(
                    slot.x - camera.x,
                    slot.y - camera.y,
                    slot.z - camera.z
            );
            poseStack.mulPose(Axis.YP.rotationDegrees((time + partial) * 4.0f));
            poseStack.scale(scale, scale, scale);
            item.submit(
                    poseStack,
                    collector,
                    LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    0
            );
            poseStack.popPose();
        }
    }

    private static void ensureItemCache(ClientLevel level) {
        if (itemCacheReady && itemCacheLevel == level) {
            return;
        }
        CACHED_NORMAL.clear();
        CACHED_HYPER.clear();
        var resolver = Minecraft.getInstance().getItemModelResolver();
        resolver.updateForTopItem(
                CACHED_NORMAL,
                new ItemStack(Items.NETWORK_RELAY_SATELLITE.get()),
                ItemDisplayContext.GROUND,
                level,
                null,
                0
        );
        resolver.updateForTopItem(
                CACHED_HYPER,
                new ItemStack(Items.HYPER_NETWORK_RELAY_SATELLITE.get()),
                ItemDisplayContext.GROUND,
                level,
                null,
                1
        );
        itemCacheLevel = level;
        itemCacheReady = true;
    }

    private record OrbitMark(
            BlockPos laserPos,
            int seed,
            boolean hyper,
            float orbitY,
            boolean strikeAiming,
            float aimX,
            float aimY,
            float aimZ
    ) {
        private Vec3 slot(long gameTime) {
            if (strikeAiming) {
                return new Vec3(aimX, aimY, aimZ);
            }
            return MisakaRelayOrbits.orbitSlotWorld(laserPos, orbitY, gameTime, seed);
        }
    }
}
