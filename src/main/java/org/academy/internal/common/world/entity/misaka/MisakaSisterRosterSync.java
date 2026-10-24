package org.academy.internal.common.world.entity.misaka;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraft;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.server.misaka.MisakaComputeContribution;
import org.academy.internal.server.misaka.MisakaComputeIndex;
import org.academy.internal.server.misaka.MisakaLoadedSisterIndex;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;

/**
 * Roster registration, synched-data mirroring, coverage/awake-window ticks, and bind/wander writebacks.
 * <p>
 * Roster SavedData is authoritative for identity and status. Living entities may lose
 * {@code misakaUuid} on reload or drift from roster fields; {@link #ensureRegistered} rebinds
 * and mirrors until both sides agree.
 */
public final class MisakaSisterRosterSync {
    private MisakaSisterRosterSync() {
    }

    /**
     * Ensure the living entity is bound to a roster row before AI / interact.
     * Prefer rebinding by persisted serial when {@code misakaUuid} was lost on reload —
     * otherwise a fresh non-incap record is created while the old incap row stays orphaned
     * (info shows incap=true, world sister keeps walking).
     */
    public static void ensureRegistered(MisakaSisterEntity sister) {
        if (sister.level().isClientSide() || sister.level().getServer() == null) {
            return;
        }
        var roster = MisakaSisterRoster.get(sister.level().getServer());

        if (sister.misakaUuid != null) {
            var existing = roster.get(sister.misakaUuid);
            if (existing.isPresent()) {
                attachToRecord(sister, existing.get(), "uuid");
                return;
            }
            AcademyCraft.LOGGER.warn(
                    "Misaka entity serial={} had roster uuid {} with no row; clearing uuid and recovering by serial",
                    sister.getSerial(),
                    sister.misakaUuid
            );
            sister.misakaUuid = null;
        }

        int serial = sister.getSerial();
        if (serial >= MisakaSisterRoster.SERIAL_MIN && serial <= MisakaSisterRoster.SERIAL_MAX) {
            var bySerial = roster.findBySerial(serial);
            if (bySerial.isPresent()) {
                attachToRecord(sister, bySerial.get(), "serial");
                return;
            }
        }

        // Entity exists in the world but no roster row: reclaim persisted serial when possible
        // instead of minting a new identity (which orphans the old serial in player memory).
        int day = MisakaDayTime.dayIndex(sister.level());
        var registered = serial >= MisakaSisterRoster.SERIAL_MIN && serial <= MisakaSisterRoster.SERIAL_MAX
                ? roster.tryAdoptPersistedSerial(serial, sister.getRandom(), day)
                : roster.tryRegisterRescued(sister.getRandom(), day);
        if (registered.isEmpty()) {
            AcademyCraft.LOGGER.error(
                    "Misaka entity with no roster row could not be adopted (serial={}); discarding",
                    serial
            );
            sister.discard();
            return;
        }
        AcademyCraft.LOGGER.info(
                "Misaka entity without roster row adopted as #{} ({})",
                registered.get().serial,
                registered.get().misakaUuid
        );
        bindToRecord(sister, registered.get());
        sister.pendingSerial = 0;
    }

    /**
     * Bind or refresh an entity against an authoritative roster row.
     * Handles uuid loss, serial drift, and status fields that only exist on one side.
     */
    private static void attachToRecord(MisakaSisterEntity sister, MisakaSisterRecord record, String via) {
        boolean uuidMismatch = !record.misakaUuid.equals(sister.misakaUuid);
        boolean serialMismatch = sister.getEntityData().get(MisakaSisterEntity.SERIAL) != record.serial;
        boolean statusMismatch = sister.getEntityData().get(MisakaSisterEntity.INCAPACITATED) != record.incapacitated
                || sister.getEntityData().get(MisakaSisterEntity.STARVING) != record.starving
                || sister.getEntityData().get(MisakaSisterEntity.AWAKENED) != record.awakened
                || sister.getEntityData().get(MisakaSisterEntity.PERCEPTION) != record.perception
                || sister.getEntityData().get(MisakaSisterEntity.PERSONALITY) != record.personality.ordinal()
                || sister.getEntityData().get(MisakaSisterEntity.WANDER_STYLE) != record.wanderStyle.ordinal();

        if (uuidMismatch) {
            AcademyCraft.LOGGER.info(
                    "Misaka rebound via {}: entity uuid {} / serial {} -> roster uuid {} / serial {}",
                    via,
                    sister.misakaUuid,
                    sister.getSerial(),
                    record.misakaUuid,
                    record.serial
            );
            bindToRecord(sister, record);
            return;
        }

        if (serialMismatch || statusMismatch || sister.needsInitialRosterSync()) {
            syncFromRecord(sister, record);
            sister.clearInitialRosterSync();
        }
        MisakaLoadedSisterIndex.put(sister);
    }

    public static void syncFromRoster(MisakaSisterEntity sister) {
        ensureRegistered(sister);
        sister.rosterRecord().ifPresent(record -> syncFromRecord(sister, record));
    }

    /**
     * Pull roster → entityData only when the roster marked this UUID dirty, or the entity
     * still needs its first bind mirror ({@link MisakaSisterEntity#needsInitialRosterSync()}).
     */
    public static void syncFromRosterIfDirty(MisakaSisterEntity sister) {
        ensureRegistered(sister);
        if (sister.misakaUuid == null || sister.level().getServer() == null) {
            return;
        }
        var roster = MisakaSisterRoster.get(sister.level().getServer());
        boolean dirty = roster.consumeEntitySyncDirty(sister.misakaUuid);
        if (!dirty && !sister.needsInitialRosterSync()) {
            return;
        }
        sister.clearInitialRosterSync();
        sister.rosterRecord().ifPresent(record -> syncFromRecord(sister, record));
    }

    public static void syncFromRecord(MisakaSisterEntity sister, MisakaSisterRecord record) {
        var data = sister.getEntityData();
        boolean wasIncap = data.get(MisakaSisterEntity.INCAPACITATED);
        data.set(MisakaSisterEntity.SERIAL, record.serial);
        data.set(MisakaSisterEntity.AWAKENED, record.awakened);
        data.set(MisakaSisterEntity.PERCEPTION, record.perception);
        data.set(MisakaSisterEntity.PERSONALITY, record.personality.ordinal());
        data.set(MisakaSisterEntity.STARVING, record.starving);
        data.set(MisakaSisterEntity.INCAPACITATED, record.incapacitated);
        data.set(MisakaSisterEntity.WANDER_STYLE, record.wanderStyle.ordinal());
        sister.pendingSerial = record.serial;
        sister.refreshNametag();
        if (!record.awakened) {
            sister.getFoodData().setFoodLevel(20);
            sister.getFoodData().setSaturation(5.0f);
        }
        sister.syncFoodLevel();
        if (record.incapacitated) {
            sister.applyIncapacitatedHold();
        } else if (wasIncap) {
            sister.clearIncapacitatedHold();
        }
        MisakaLoadedSisterIndex.put(sister);
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
        MisakaLoadedSisterIndex.put(sister);
        var chunk = sister.chunkPosition();
        var feet = sister.blockPosition();
        sister.rosterRecord().ifPresent(record -> {
            boolean sameChunk = chunk.equals(record.lastKnownChunk);
            boolean sameBlock = feet.equals(record.lastKnownBlockPos);
            boolean sameDim = sister.level().dimension().equals(record.lastKnownDimension);
            if (sameChunk && sameBlock && sameDim) {
                return;
            }
            record.lastKnownChunk = chunk;
            record.lastKnownBlockPos = feet.immutable();
            record.lastKnownDimension = sister.level().dimension();
            MisakaSisterRoster.get(sister.level().getServer()).setDirty();
            if (!sameChunk || !sameDim) {
                recheckNetworkCoverage(sister, record);
            }
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
        // LoS player scan is rare-payoff; probe every 10 ticks during the awaken window.
        if (sister.tickCount % 10 != 0) {
            return;
        }
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
        sister.setPersistenceRequired();
        sister.misakaUuid = record.misakaUuid;
        sister.pendingSerial = record.serial;
        sister.clearInitialRosterSync();
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

    /** Find a loaded entity bound to this roster uuid (any dimension). */
    public static java.util.Optional<MisakaSisterEntity> findLoadedSister(
            net.minecraft.server.MinecraftServer server,
            java.util.UUID misakaUuid
    ) {
        return java.util.Optional.ofNullable(
                org.academy.internal.server.misaka.MisakaNetworkCoverage.findLoadedSister(server, misakaUuid)
        );
    }
}
