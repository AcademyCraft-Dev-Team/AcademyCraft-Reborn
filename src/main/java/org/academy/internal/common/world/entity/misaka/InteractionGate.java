package org.academy.internal.common.world.entity.misaka;

import net.minecraft.server.MinecraftServer;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.common.world.entity.misaka.perception.PerceptionService;
import org.academy.internal.server.misaka.MisakaComputeContribution;
import org.academy.internal.server.misaka.MisakaComputeIndex;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import org.jspecify.annotations.Nullable;

public final class InteractionGate {
    public enum Intent {
        LEASH,
        FEED_TOWER_AWAKEN,
        FEED_RECOVER,
        PET,
        PANEL,
        FEED_FOOD,
        FEED_PROMAX,
        STATE,
        PICKUP,
        /** First bind while unbound — DEFAULT+. */
        BIND_FIRST,
        /** Change or clear network node while already bound — privilege only. */
        MIGRATE
    }

    private InteractionGate() {
    }

    public static boolean allow(MisakaSisterRecord record, String name, Intent intent) {
        if (intent == Intent.LEASH) {
            return true;
        }
        if (intent == Intent.FEED_TOWER_AWAKEN) {
            return !record.awakened && !record.incapacitated;
        }
        if (intent == Intent.FEED_RECOVER) {
            // Caller already verified the sister is downed (roster and/or entity flag).
            if (!record.awakened) {
                return true;
            }
            return FavorService.relation(record, name).ordinal() >= MobRelation.DEFAULT.ordinal();
        }
        if (!record.awakened) {
            return false;
        }
        var relation = FavorService.relation(record, name);
        boolean atLeastDefault = relation.ordinal() >= MobRelation.DEFAULT.ordinal();
        return switch (intent) {
            // Read-only panel for any awakened relation (incl. INDIFFERENT/HOSTILE/DEADLY).
            case PANEL -> true;
            case PET, FEED_FOOD, BIND_FIRST -> atLeastDefault;
            case FEED_PROMAX -> atLeastDefault && !record.promaxUsed;
            case STATE, PICKUP, MIGRATE -> FavorService.isPrivilegePlayer(record, name);
            default -> false;
        };
    }

    public static void touchBenevolent(MisakaSisterRecord record, String name) {
        touchBenevolent(record, name, null);
    }

    public static void touchBenevolent(MisakaSisterRecord record, String name, @Nullable MinecraftServer server) {
        if (FavorService.relation(record, name) == MobRelation.BENEVOLENT) {
            boolean changed = !name.equals(record.lastInteractedBenevolentPlayerName);
            record.lastInteractedBenevolentPlayerName = name;
            if (server != null) {
                MisakaSisterRoster.get(server).setDirty();
                if (changed) {
                    MisakaComputeIndex.get(server).markDirty();
                }
            }
        }
    }

    /**
     * Privilege / CP side-effects for max-favor (BENEVOLENT) interactions.
     * Always deferred to the next server tick so index rebuild + CP sync cannot
     * abort {@code mobInteract} / wash action-bar feedback on the interact tick.
     * At favor &lt; max these calls are mostly no-ops; at favor == max they become heavy.
     */
    public static void scheduleAfterInteract(
            @Nullable MinecraftServer server,
            MisakaSisterRecord record,
            String name
    ) {
        if (server == null || record == null || name == null || name.isEmpty()) {
            return;
        }
        server.execute(() -> {
            touchBenevolent(record, name, server);
            PerceptionService.tryIntegrateIfEligible(server, record);
            MisakaComputeContribution.refreshCpForRecord(server, record);
        });
    }
}
