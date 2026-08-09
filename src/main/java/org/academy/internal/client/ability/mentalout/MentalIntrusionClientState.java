package org.academy.internal.client.ability.mentalout;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import org.academy.internal.common.ability.mentalout.MentalIntrusionManager;
import org.misaka.MisakaNetworkClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MentalIntrusionClientState {
    private static final Map<UUID, FilterState> FILTERS = new HashMap<>();
    private static final Map<UUID, Long> FILTER_REVISIONS = new HashMap<>();
    private static UUID sessionId;
    private static UUID targetUuid;
    private static int targetEntityId = -1;
    private static long revision;
    private static ClientLevel clientLevel;
    private static Entity originalCamera;
    private static CameraType originalCameraType;

    private MentalIntrusionClientState() {
    }

    public static void begin(UUID requestedSession, long requestedRevision, int entityId, UUID entityUuid) {
        var minecraft = Minecraft.getInstance();
        synchronizeLevel(minecraft);
        if (requestedRevision < revision || minecraft.level == null || minecraft.player == null) {
            acknowledge(requestedSession, requestedRevision, false);
            return;
        }
        var target = minecraft.level.getEntity(entityId);
        if (target == null || target.isRemoved() || !target.getUUID().equals(entityUuid)) {
            acknowledge(requestedSession, requestedRevision, false);
            return;
        }

        restoreCamera(false);
        revision = requestedRevision;
        sessionId = requestedSession;
        targetUuid = entityUuid;
        targetEntityId = entityId;
        originalCamera = minecraft.getCameraEntity();
        originalCameraType = minecraft.options.getCameraType();
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.setCameraEntity(target);
        clearInteractionTarget(minecraft);
        acknowledge(requestedSession, requestedRevision, true);
    }

    public static void end(UUID requestedSession, long requestedRevision) {
        if (requestedRevision < revision) return;
        if (sessionId != null && requestedSession != null && !sessionId.equals(requestedSession)) return;
        revision = requestedRevision;
        restoreCamera(false);
    }

    public static void applyFilter(UUID hiddenUuid, int entityId, boolean active, long filterRevision) {
        var minecraft = Minecraft.getInstance();
        synchronizeLevel(minecraft);
        if (minecraft.level == null || minecraft.player == null) return;
        if (filterRevision <= FILTER_REVISIONS.getOrDefault(hiddenUuid, Long.MIN_VALUE)) return;
        FILTER_REVISIONS.put(hiddenUuid, filterRevision);
        if (active) {
            FILTERS.put(hiddenUuid, new FilterState(entityId, filterRevision));
        } else {
            FILTERS.remove(hiddenUuid);
        }
        if (minecraft.crosshairPickEntity != null && isHidden(minecraft.crosshairPickEntity)) {
            clearInteractionTarget(minecraft);
        }
    }

    public static boolean isActive() {
        return sessionId != null;
    }

    public static boolean blocksWorldInteraction() {
        return isActive();
    }

    public static boolean isHidden(Entity entity) {
        return entity != null && FILTERS.containsKey(entity.getUUID());
    }

    public static boolean hasFilters() {
        return !FILTERS.isEmpty();
    }

    public static void tick() {
        var minecraft = Minecraft.getInstance();
        synchronizeLevel(minecraft);
        if (minecraft.level == null || minecraft.player == null) {
            clearLocal();
            return;
        }
        if (sessionId == null) {
            if (minecraft.crosshairPickEntity != null && isHidden(minecraft.crosshairPickEntity)) {
                clearInteractionTarget(minecraft);
            }
            return;
        }
        var target = minecraft.level.getEntity(targetEntityId);
        if (target == null || !target.isAlive() || target.isRemoved()
                || !target.getUUID().equals(targetUuid)) {
            var endedSession = sessionId;
            var endedRevision = revision;
            restoreCamera(false);
            MisakaNetworkClient.send(new MentalIntrusionManager.ClientStopPacket(endedSession, endedRevision));
            return;
        }
        if (minecraft.getCameraEntity() != target) minecraft.setCameraEntity(target);
        clearInteractionTarget(minecraft);
    }

    public static void clearLocal() {
        restoreCamera(false);
        FILTERS.clear();
        FILTER_REVISIONS.clear();
        revision = 0L;
        clientLevel = null;
    }

    private static void acknowledge(UUID requestedSession, long requestedRevision, boolean ready) {
        MisakaNetworkClient.send(new MentalIntrusionManager.ReadyPacket(
                requestedSession,
                requestedRevision,
                ready
        ));
    }

    private static void synchronizeLevel(Minecraft minecraft) {
        if (clientLevel == minecraft.level) return;
        clearLocal();
        clientLevel = minecraft.level;
    }

    private static void restoreCamera(boolean preserveRevision) {
        if (sessionId == null) return;
        var minecraft = Minecraft.getInstance();
        var fallback = minecraft.player;
        var restored = originalCamera != null && !originalCamera.isRemoved() ? originalCamera : fallback;
        if (restored != null) minecraft.setCameraEntity(restored);
        if (originalCameraType != null) minecraft.options.setCameraType(originalCameraType);
        sessionId = null;
        targetUuid = null;
        targetEntityId = -1;
        originalCamera = null;
        originalCameraType = null;
        if (!preserveRevision) clearInteractionTarget(minecraft);
    }

    private static void clearInteractionTarget(Minecraft minecraft) {
        minecraft.crosshairPickEntity = null;
        minecraft.hitResult = BlockHitResult.miss(
                minecraft.gameRenderer.mainCamera().position(),
                net.minecraft.core.Direction.UP,
                net.minecraft.core.BlockPos.containing(minecraft.gameRenderer.mainCamera().position())
        );
    }

    private record FilterState(int entityId, long revision) {
    }
}
