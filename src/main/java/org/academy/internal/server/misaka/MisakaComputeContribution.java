package org.academy.internal.server.misaka;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.UUID;

public final class MisakaComputeContribution {
    public static final float CP_PER_MSK = 1.0f;

    private MisakaComputeContribution() {
    }

    public static float cpPerMsk(MinecraftServer server) {
        var academy = server.getAcademyCraftServer();
        if (academy == null) {
            return CP_PER_MSK;
        }
        return academy.getGenericConfig().misakaCpPerMsk;
    }

    public static float mskPerSecond(int perception) {
        if (perception <= 100) {
            return perception;
        }
        if (perception <= 110) {
            return 100f + 2f * (perception - 100);
        }
        return 120f + (280f / 90f) * (perception - 110);
    }

    /**
     * Privilege-player CP recovery from bound sisters, in CP per second.
     * Formula: {@code 0.75 * msk/s * misakaCpPerMsk} summed over qualifying sisters.
     */
    public static float privilegeRecoveryPerSecond(MinecraftServer server, UUID playerUuid) {
        var player = server.getPlayerList().getPlayer(playerUuid);
        if (player == null) {
            return 0f;
        }
        String name = player.getGameProfile().name();
        float sum = 0f;
        for (var record : MisakaSisterRoster.get(server).all()) {
            if (!record.awakened || record.networkNodePos == null || record.starving) {
                continue;
            }
            if (!FavorService.isPrivilegePlayer(record, name)) {
                continue;
            }
            sum += 0.75f * mskPerSecond(record.perception) * cpPerMsk(server);
        }
        return sum;
    }

    /** Notify online privilege candidates after Misaka contribution may have changed. */
    public static void refreshCpForRecord(MinecraftServer server, MisakaSisterRecord record) {
        var names = new HashSet<String>();
        if (record.lastInteractedBenevolentPlayerName != null
                && !record.lastInteractedBenevolentPlayerName.isEmpty()) {
            names.add(record.lastInteractedBenevolentPlayerName);
        }
        int maxFavor = record.favorByPlayerName.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (maxFavor > 0) {
            for (var entry : record.favorByPlayerName.entrySet()) {
                if (entry.getValue() == maxFavor) {
                    names.add(entry.getKey());
                }
            }
        }
        refreshCpForNames(server, names);
    }

    public static void refreshCpForNames(MinecraftServer server, Iterable<String> names) {
        var academy = server.getAcademyCraftServer();
        if (academy == null) {
            return;
        }
        var ability = academy.getAbilitySystemServer();
        for (String name : names) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            ServerPlayer player = findOnlineByName(server, name);
            if (player != null) {
                ability.refreshPlayerCommonSkillBonuses(player.getUUID());
            }
        }
    }

    private static @Nullable ServerPlayer findOnlineByName(MinecraftServer server, String name) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getGameProfile().name().equals(name)) {
                return player;
            }
        }
        return null;
    }
}
