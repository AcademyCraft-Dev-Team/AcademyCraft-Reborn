package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureTerrainBreakPolicy;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Server-side owner of soft-terrain destruction during real structure motion. An explicit
 * authorized actor is required; structures do not receive destruction rights just for moving.
 * A grant expires, so a discarded or orphaned structure cannot keep breaking terrain.
 */
public final class SubLevelTerrainBreaker {
    private static final long GRANT_TICKS = 20L * 45L;
    private static final long SCAN_INTERVAL_TICKS = 2L;
    private static final Map<SubLevelId, Grant> GRANTS = new HashMap<>();
    private static final Map<SubLevelId, Long> NEXT_SCAN = new HashMap<>();
    private static final Map<SubLevelId, StructurePose> LAST_SCAN_POSE = new HashMap<>();
    private static final Map<ServerLevel, Boolean> HOSTS = new WeakHashMap<>();
    private static volatile Predicate<Entity> nonPlayerPolicy = entity -> false;

    private SubLevelTerrainBreaker() {
    }

    /** Extension point for non-player controllers; the default is an explicit denial. */
    public static void setNonPlayerPolicy(Predicate<Entity> policy) {
        nonPlayerPolicy = policy == null ? entity -> false : policy;
    }

    /** Records the actor that currently owns this structure's motion. */
    public static void remember(ServerLevel host, SubLevelId id, Entity actor) {
        if (host == null || id == null || actor == null || !(actor instanceof Player)) return;
        GRANTS.put(id, new Grant(actor.getUUID(), host.dimension(), host.getGameTime() + GRANT_TICKS));
        HOSTS.put(host, Boolean.TRUE);
    }

    public static void forget(SubLevelId id) {
        GRANTS.remove(id);
        NEXT_SCAN.remove(id);
        LAST_SCAN_POSE.remove(id);
    }

    public static void forgetHost(ServerLevel host) {
        HOSTS.remove(host);
        GRANTS.entrySet().removeIf(entry -> entry.getValue().dimension().equals(host.dimension()));
        // SubLevel ids are not dimension-qualified; only drop records that are already expired.
        long now = host.getGameTime();
        GRANTS.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    public static void clear() {
        GRANTS.clear();
        NEXT_SCAN.clear();
        LAST_SCAN_POSE.clear();
        HOSTS.clear();
    }

    /** Shared authorization gate used by scan/authorize/apply. */
    static boolean actorAllowed(ServerLevel host, Entity actor) {
        if (actor == null || actor.isRemoved() || actor.level() != host || !actor.isAlive()) return false;
        if (actor instanceof net.minecraft.server.level.ServerPlayer player) {
            return player.connection != null && DestroyBlocksSetting.canDestroyBlocks(player);
        }
        return actor instanceof Player player && DestroyBlocksSetting.isDestroyBlocksEnabled(player)
                && DestroyBlocksSetting.isServerBlockDestructionAllowed(host)
                || nonPlayerPolicy.test(actor);
    }

    /** Called once per moved structure, after the whole solver step has published its pose. */
    public static void sweep(ServerLevel host, SubLevel subLevel, StructurePose previous, StructurePose current,
                             List<AABB> localBoxes) {
        if (host == null || subLevel == null || previous == null || current == null || localBoxes.isEmpty()) return;
        if (previous.equals(current)) return;
        long now = host.getGameTime();
        long next = NEXT_SCAN.getOrDefault(subLevel.id(), 0L);
        if (now < next) return;
        NEXT_SCAN.put(subLevel.id(), now + SCAN_INTERVAL_TICKS);
        // Sweep from the last scanned pose, not only the previous tick, so a skipped scan
        // interval cannot hide a fast body's motion.
        var from = LAST_SCAN_POSE.put(subLevel.id(), current);
        if (from == null) from = previous;
        if (from.equals(current)) return;
        var actor = resolveActor(host, subLevel.id(), now);
        if (actor == null || !actorAllowed(host, actor)) return;
        var reference = SubLevelStructureRefs.of(subLevel, host.dimension().identifier(),
                SubLevelActivationRegistry.find(subLevel.id())
                        .map(SubLevelActivationRegistry.Activation::generation).orElse(0L));
        var limits = new StructureTerrainBreakPolicy.Limits(
                Math.max(1, Integer.getInteger("academy.sublevel.softTerrainCells", 4_096)),
                Math.max(1, Integer.getInteger("academy.sublevel.softTerrainTests", 16_384)),
                Math.max(1, Integer.getInteger("academy.sublevel.softTerrainDestroyed", 48)));
        try {
            var sweep = SubLevelEnhancementBootstrap.TERRAIN_BREAKING.scan(host, reference,
                    from, current, localBoxes, limits);
            if (sweep.candidates().isEmpty()) return;
            var plan = SubLevelEnhancementBootstrap.TERRAIN_BREAKING.authorize(host, actor, sweep,
                    !(actor instanceof net.minecraft.world.entity.player.Player player) || !player.hasInfiniteMaterials(),
                    limits);
            if (plan.permitted().isEmpty()) return;
            SubLevelEnhancementBootstrap.TERRAIN_BREAKING.apply(host, actor, plan, limits);
        } catch (RuntimeException failure) {
            org.academy.AcademyCraft.LOGGER.error("SubLevel terrain sweep failed for {}", subLevel.id(), failure);
        }
    }

    private static Entity resolveActor(ServerLevel host, SubLevelId id, long now) {
        var grant = GRANTS.get(id);
        if (grant == null) return null;
        if (grant.expiresAt() < now || !grant.dimension().equals(host.dimension())) {
            GRANTS.remove(id);
            return null;
        }
        var player = host.getServer().getPlayerList().getPlayer(grant.actor());
        if (player != null && player.level() == host) return player;
        return host.getEntity(grant.actor());
    }

    private record Grant(UUID actor, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                         long expiresAt) {
    }
}
