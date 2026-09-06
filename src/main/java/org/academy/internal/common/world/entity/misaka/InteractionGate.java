package org.academy.internal.common.world.entity.misaka;

import net.minecraft.server.MinecraftServer;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.server.misaka.MisakaComputeIndex;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import org.jspecify.annotations.Nullable;

public final class InteractionGate {
    public enum Intent {
        LEASH,
        FEED_TOWER_AWAKEN,
        PET,
        PANEL,
        FEED_FOOD,
        FEED_PROMAX,
        STATE,
        PICKUP
    }

    private InteractionGate() {
    }

    public static boolean allow(MisakaSisterRecord record, String name, Intent intent) {
        if (intent == Intent.LEASH) {
            return true;
        }
        if (intent == Intent.FEED_TOWER_AWAKEN) {
            return !record.awakened;
        }
        if (!record.awakened) {
            return false;
        }
        var relation = FavorService.relation(record, name);
        boolean atLeastDefault = relation.ordinal() >= MobRelation.DEFAULT.ordinal();
        return switch (intent) {
            case PET, PANEL, FEED_FOOD -> atLeastDefault;
            case FEED_PROMAX -> atLeastDefault && !record.promaxUsed;
            case STATE, PICKUP -> FavorService.isPrivilegePlayer(record, name);
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
}
