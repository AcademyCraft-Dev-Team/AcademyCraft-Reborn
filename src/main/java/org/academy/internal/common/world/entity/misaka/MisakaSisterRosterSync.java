package org.academy.internal.common.world.entity.misaka;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.server.misaka.MisakaComputeContribution;
import org.academy.internal.server.misaka.MisakaComputeIndex;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;

/**
 * Roster registration, synched-data mirroring, coverage/awake-window ticks, and bind/wander writebacks.
 * Entity tick / interact call into here; behavior must stay identical to the former inlined methods.
 */
public final class MisakaSisterRosterSync {
    private MisakaSisterRosterSync() {
    }

    public static void ensureRegistered(MisakaSisterEntity sister) {
        if (sister.misakaUuid != null || sister.level().getServer() == null) {
            return;
        }
        int day = MisakaDayTime.dayIndex(sister.level());
        var registered = MisakaSisterRoster.get(sister.level().getServer()).tryRegisterRescued(sister.getRandom(), day);
        if (registered.isEmpty()) {
            // Serial pool exhausted — forbid creating more sisters.
            sister.discard();
            return;
        }
        var record = registered.get();
        sister.misakaUuid = record.misakaUuid;
        syncFromRecord(sister, record);
    }

    public static void syncFromRoster(MisakaSisterEntity sister) {
        sister.rosterRecord().ifPresent(record -> syncFromRecord(sister, record));
    }

    public static void syncFromRecord(MisakaSisterEntity sister, MisakaSisterRecord record) {
        var data = sister.getEntityData();
        data.set(MisakaSisterEntity.SERIAL, record.serial);
        data.set(MisakaSisterEntity.AWAKENED, record.awakened);
        data.set(MisakaSisterEntity.PERCEPTION, record.perception);
        data.set(MisakaSisterEntity.PERSONALITY, record.personality.ordinal());
        data.set(MisakaSisterEntity.STARVING, record.starving);
        data.set(MisakaSisterEntity.WANDER_STYLE, record.wanderStyle.ordinal());
        if (!record.awakened) {
            sister.getFoodData().setFoodLevel(20);
            sister.getFoodData().setSaturation(5.0f);
        }
        sister.syncFoodLevel();
    }

    public static void syncStarvingToRoster(MisakaSisterEntity sister) {
        if (sister.misakaUuid == null || sister.level().getServer() == null) {
            return;
        }
        boolean starvingNow = sister.getFoodData().getFoodLevel() == 0;
        sister.getEntityData().set(MisakaSisterEntity.STARVING, starvingNow);
        var server = sister.level().getServer();
        var roster = MisakaSisterRoster.get(server);
        var existing = roster.get(sister.misakaUuid);
        if (existing.isEmpty() || existing.get().starving == starvingNow) {
            return;
        }
        roster.modify(sister.misakaUuid, record -> record.starving = starvingNow);
        existing = roster.get(sister.misakaUuid);
        existing.ifPresent(record -> MisakaComputeContribution.refreshCpForRecord(server, record));
    }

    public static void updateLastKnownChunk(MisakaSisterEntity sister) {
        if (sister.misakaUuid == null || sister.level().getServer() == null) {
            return;
        }
        var chunk = sister.chunkPosition();
        sister.rosterRecord().ifPresent(record -> {
            if (chunk.equals(record.lastKnownChunk)) {
                return;
            }
            record.lastKnownChunk = chunk;
            record.lastKnownDimension = sister.level().dimension();
            MisakaSisterRoster.get(sister.level().getServer()).setDirty();
            recheckNetworkCoverage(sister, record);
        });
    }

    public static void recheckNetworkCoverage(MisakaSisterEntity sister) {
        sister.rosterRecord().ifPresent(record -> recheckNetworkCoverage(sister, record));
    }

    public static void recheckNetworkCoverage(MisakaSisterEntity sister, MisakaSisterRecord record) {
        var server = sister.level().getServer();
        if (server == null) {
            return;
        }
        if (!record.awakened || record.networkNodePos == null) {
            return;
        }
        // Topology resolve uses overworld wireless data; coverage sample uses this entity's level.
        var overworld = server.overworld();
        var networkId = MisakaNAT.get().resolveNetworkId(overworld, record.networkNodePos);
        boolean nowIn = MisakaNAT.get().canUseMisakaService(
                (ServerLevel) sister.level(),
                networkId,
                sister.blockPosition()
        );
        Boolean was = MisakaComputeIndex.get(server).lastCoverageContributing(record.misakaUuid);
        if (was == null) {
            MisakaComputeIndex.get(server).seedCoverageContributing(record.misakaUuid, nowIn);
            return;
        }
        if (was == nowIn) {
            return;
        }
        MisakaComputeIndex.get(server).adjustCoverageContribution(server, record, was, nowIn);
    }

    public static void tickAwakeWindowSpotting(MisakaSisterEntity sister) {
        sister.rosterRecord().ifPresent(record -> {
            if (!record.awakened || sister.level().getGameTime() > record.awakeWindowEndGameTime) {
                return;
            }
            var server = sister.level().getServer();
            for (ServerPlayer player : ((ServerLevel) sister.level()).getPlayers(
                    player -> player.hasLineOfSight(sister) && sister.distanceToSqr(player) <= 16.0 * 16.0)) {
                String name = player.getGameProfile().name();
                if (!record.awakeSpottedNames.add(name)) {
                    continue;
                }
                var component = FavorService
                        .resolveLanComponent((ServerLevel) sister.level(), record, MisakaSisterRoster.get(server).all());
                for (var member : component) {
                    member.awakeSpottedNames.add(name);
                }
                FavorService.modifyFavorLan(server, record, name, 1);
                MisakaSisterRoster.get(server).setDirty();
            }
        });
    }

    public static void bindToRecord(MisakaSisterEntity sister, MisakaSisterRecord record) {
        sister.misakaUuid = record.misakaUuid;
        syncFromRecord(sister, record);
    }

    public static void setWanderStyle(MisakaSisterEntity sister, WanderStyle style) {
        sister.rosterRecord().ifPresent(record -> {
            record.wanderStyle = style;
            sister.getEntityData().set(MisakaSisterEntity.WANDER_STYLE, style.ordinal());
            style.apply(sister);
            MisakaSisterRoster.get(sister.level().getServer()).setDirty();
        });
    }
}
