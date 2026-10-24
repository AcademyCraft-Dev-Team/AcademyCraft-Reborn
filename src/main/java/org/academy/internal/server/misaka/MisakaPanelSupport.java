package org.academy.internal.server.misaka;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.academy.AcademyCraft;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.internal.common.network.misaka.MisakaPanelDataPacket;
import org.academy.internal.common.world.entity.misaka.InteractionGate;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.entity.misaka.MobRelation;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.academy.internal.server.world.level.storage.WirelessNetworkData;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MisakaPanelSupport {
    private MisakaPanelSupport() {
    }

    public static Optional<MisakaSisterEntity> findLoadedEntity(Entity entity) {
        if (entity instanceof MisakaSisterEntity sister) {
            return Optional.of(sister);
        }
        return Optional.empty();
    }

    public static Optional<MisakaSisterEntity> findLoadedEntity(ServerLevel level, UUID entityUuid) {
        var entity = level.getEntity(entityUuid);
        return findLoadedEntity(entity);
    }

    public static Optional<MisakaSisterEntity> findLoadedEntityAnyDimension(
            net.minecraft.server.MinecraftServer server,
            UUID entityUuid
    ) {
        for (var level : server.getAllLevels()) {
            var found = findLoadedEntity(level, entityUuid);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    public static void sendPanel(ServerPlayer player, MisakaSisterEntity sister) {
        var record = sister.rosterRecord().orElse(null);
        if (record == null) {
            return;
        }
        try {
            MisakaNetworkServer.send(player, buildPanel(player, sister, record));
        } catch (RuntimeException ex) {
            AcademyCraft.LOGGER.error(
                    "Failed to open Misaka panel for {} on sister {}",
                    player.getGameProfile().name(),
                    record.misakaUuid,
                    ex
            );
        }
    }

    public static MisakaPanelDataPacket buildPanel(
            ServerPlayer player,
            MisakaSisterEntity sister,
            MisakaSisterRecord record
    ) {
        var level = (ServerLevel) player.level();
        var server = level.getServer();
        var overworld = server != null ? server.overworld() : level;
        String playerName = player.getGameProfile().name();
        var relation = FavorService.relation(record, playerName);
        // §16.2: favor <= 0 viewers only get public facts — no network name, topology or exact compute.
        boolean detailed = relation.ordinal() >= MobRelation.DEFAULT.ordinal();
        // Node configs live in overworld SavedData; position sample uses the sister entity.
        String nodeName = "";
        List<String> availableNodes = List.of();
        boolean reconstructionBlocked = false;
        if (detailed) {
            try {
                nodeName = WirelessNetworkData.displayName(overworld, record.networkNodePos, false);
                availableNodes = MisakaNAT.get().listAvailableNodes(overworld, sister.blockPosition());
                // hasReconstructionWork may rebuild the compute index (heavy at max favor /
                // privilege). Never let that abort panel open.
                if (record.networkNodePos != null && server != null) {
                    reconstructionBlocked = MisakaNAT.get().hasReconstructionWork(
                            server, record.networkNodePos, record.misakaUuid);
                }
            } catch (RuntimeException ex) {
                AcademyCraft.LOGGER.error(
                        "Misaka panel detail lookup failed for sister {}",
                        record.misakaUuid,
                        ex
                );
            }
        }
        boolean reconstructionWork = record.isReconstruction || record.perception >= 101;
        float msk = 0.0f;
        if (record.awakened && !record.starving && !record.incapacitated) {
            msk = MisakaComputeContribution.mskPerSecond(record.perception);
        }
        return new MisakaPanelDataPacket(
                sister.getUUID(),
                record.misakaUuid,
                record.serial,
                detailed ? record.perception : 0,
                detailed ? msk : 0.0f,
                record.personality.ordinal(),
                record.awakened,
                nodeName,
                availableNodes,
                relation.ordinal(),
                FavorService.isPrivilegePlayer(record, playerName),
                reconstructionWork,
                reconstructionBlocked,
                record.networkNodePos != null,
                record.incapacitated || sister.isIncapacitated()
        );
    }

    public static @Nullable MisakaSisterRecord resolveRecord(
            MisakaSisterEntity sister,
            UUID misakaUuid
    ) {
        if (sister.getMisakaUuid() != null && !sister.getMisakaUuid().equals(misakaUuid)) {
            return null;
        }
        return sister.rosterRecord().orElse(null);
    }

    /**
     * Same-dimension sister already loaded for this player. Gate and reach stay with the
     * caller so pickup (range first) and panel packets (allow then touch then range) keep
     * their existing order.
     */
    public static @Nullable SisterInteraction load(ServerPlayer player, UUID entityUuid) {
        if (player == null || entityUuid == null || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        var sister = findLoadedEntity(level, entityUuid).orElse(null);
        if (sister == null) {
            return null;
        }
        var record = resolveRecord(sister, sister.getMisakaUuid());
        if (record == null) {
            return null;
        }
        return new SisterInteraction(player, level, sister, record, player.getGameProfile().name());
    }

    public record SisterInteraction(
            ServerPlayer player,
            ServerLevel level,
            MisakaSisterEntity sister,
            MisakaSisterRecord record,
            String playerName
    ) {
        public boolean inRange(double maxRangeSqr) {
            return player.distanceToSqr(sister) <= maxRangeSqr;
        }

        public boolean allow(InteractionGate.Intent intent) {
            return InteractionGate.allow(record, playerName, intent);
        }

        public void touch() {
            InteractionGate.touchBenevolent(record, playerName, level.getServer());
        }
    }
}
