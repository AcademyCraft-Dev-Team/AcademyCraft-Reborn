package org.academy.internal.server.misaka;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.internal.common.network.misaka.MisakaPanelDataPacket;
import org.academy.internal.common.world.entity.misaka.InteractionGate;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
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
        MisakaNetworkServer.send(player, buildPanel(player, sister, record));
    }

    public static MisakaPanelDataPacket buildPanel(
            ServerPlayer player,
            MisakaSisterEntity sister,
            MisakaSisterRecord record
    ) {
        var level = (ServerLevel) player.level();
        String playerName = player.getGameProfile().name();
        String nodeName = "";
        if (record.networkNodePos != null) {
            var config = WirelessNetworkData.get(level).getNodeConfig(record.networkNodePos);
            if (config != null) {
                nodeName = config.name;
            }
        }
        var availableNodes = MisakaNAT.get().listAvailableNodes(level, sister.blockPosition());
        boolean reconstructionWork = record.perception >= 101;
        boolean reconstructionBlocked = record.networkNodePos != null
                && MisakaNAT.get().hasReconstructionWork(level.getServer(), record.networkNodePos, record.misakaUuid);
        return new MisakaPanelDataPacket(
                sister.getUUID(),
                record.misakaUuid,
                record.serial,
                record.perception,
                MisakaComputeContribution.mskPerSecond(record.perception),
                record.personality.ordinal(),
                record.awakened,
                nodeName,
                availableNodes,
                FavorService.relation(record, playerName).ordinal(),
                FavorService.isPrivilegePlayer(record, playerName),
                reconstructionWork,
                reconstructionBlocked
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
}
