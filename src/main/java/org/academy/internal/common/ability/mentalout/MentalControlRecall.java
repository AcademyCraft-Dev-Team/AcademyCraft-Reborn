package org.academy.internal.common.ability.mentalout;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MentalControlRecall {
    public static final double RECALL_RADIUS = 64.0;
    private static final double RECALL_RADIUS_SQR = RECALL_RADIUS * RECALL_RADIUS;
    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final Map<UUID, Set<UUID>> SUPPRESSED_UNTIL_EXIT = new HashMap<>();
    private static int ticker;

    private MentalControlRecall() {
    }

    public static void suppressUntilExit(ServerPlayer controller, Mob subject) {
        if (controller == null || subject == null) return;
        SUPPRESSED_UNTIL_EXIT.computeIfAbsent(controller.getUUID(), _ -> new HashSet<>())
                .add(subject.getUUID());
    }

    public static void allow(ServerPlayer controller, Mob subject) {
        if (controller == null || subject == null) return;
        var suppressed = SUPPRESSED_UNTIL_EXIT.get(controller.getUUID());
        if (suppressed == null) return;
        suppressed.remove(subject.getUUID());
        if (suppressed.isEmpty()) SUPPRESSED_UNTIL_EXIT.remove(controller.getUUID());
    }

    public static void releaseController(UUID controllerId) {
        if (controllerId != null) SUPPRESSED_UNTIL_EXIT.remove(controllerId);
    }

    public static void tick(MinecraftServer server) {
        ticker++;
        if (ticker < SCAN_INTERVAL_TICKS) return;
        ticker = 0;
        for (var player : server.getPlayerList().getPlayers()) scan(player);
    }

    public static void clear() {
        SUPPRESSED_UNTIL_EXIT.clear();
        ticker = 0;
    }

    private static void scan(ServerPlayer player) {
        if (!player.isAlive() || player.hasDisconnected()
                || !Skills.MENTAL_INTERVENTION.get().isEnabled(player)) return;
        updateSuppression(player);
        var suppressed = SUPPRESSED_UNTIL_EXIT.getOrDefault(player.getUUID(), Set.of());
        var context = MentaloutControlContext.get(player);
        var origin = player.position();
        var candidates = player.level().getEntitiesOfClass(
                        Mob.class,
                        new AABB(origin, origin).inflate(RECALL_RADIUS),
                        mob -> mob.isAlive()
                                && !mob.isRemoved()
                                && mob.distanceToSqr(player) <= RECALL_RADIUS_SQR
                                && MentalControlMemory.wasControlledBy(mob, player.getUUID())
                                && !suppressed.contains(mob.getUUID())
                                && (context == null || !context.contains(mob.getUUID()))
                                && !MentalControlRuntime.isProtectedTarget(mob)
                ).stream()
                .sorted(Comparator.comparingDouble((Mob mob) -> mob.distanceToSqr(player))
                        .thenComparing(Mob::getUUID))
                .toList();
        for (var candidate : candidates) MentaloutControlContext.recallTarget(player, candidate);
    }

    private static void updateSuppression(ServerPlayer player) {
        var suppressed = SUPPRESSED_UNTIL_EXIT.get(player.getUUID());
        if (suppressed == null) return;
        suppressed.removeIf(subjectId -> {
            var entity = player.level().getEntity(subjectId);
            return !(entity instanceof Mob mob)
                    || !mob.isAlive()
                    || mob.isRemoved()
                    || mob.distanceToSqr(player) > RECALL_RADIUS_SQR;
        });
        if (suppressed.isEmpty()) SUPPRESSED_UNTIL_EXIT.remove(player.getUUID());
    }
}
