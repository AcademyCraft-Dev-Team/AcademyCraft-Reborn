package org.academy.internal.client.validation;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.academy.internal.common.sublevel.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Public Pneumatic Grasp entry, exclusivity, bounded force, and terminal-release probes. */
final class SubLevelTractionSmoke {
    private static final StructureTraction.Settings SETTINGS = new StructureTraction.Settings(64, 100, 100, 20, 200, 3);

    private SubLevelTractionSmoke() {}

    static void run(MinecraftServer server) {
        var runtime = SubLevelServer.runtime(server).orElseThrow();
        var host = runtime.host();
        var player = server.getPlayerList().getPlayers().getFirst();
        var source = BlockPos.containing(player.getEyePosition().add(0, 28, -10));
        host.getChunkAt(source);
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        host.setBlock(source, Blocks.IRON_BLOCK.defaultBlockState(), flags);
        var captured = SubLevelCaptureService.capture(host, runtime, List.of(source));
        var body = runtime.get(captured.id()).orElseThrow();
        var oldYaw = player.getYRot();
        var oldPitch = player.getXRot();
        var controller = UUID.randomUUID();
        try {
            player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(source));
            var hit = StructureQueries.raycast(host, player.getEyePosition(),
                    player.getEyePosition().add(player.getLookAngle().scale(64))).orElseThrow();
            check(hit.block().structure().id().equals(captured.id().value()), "fixture ray selected another structure");
            var reference = hit.block().structure();
            check(StructureTraction.tryAcquire(player, controller, hit, SETTINGS, (actor, handle) -> false).status()
                    == StructureTraction.Status.DENIED, "permission denial did not propagate");
            check(StructureTraction.tryAcquire(player, controller, hit,
                    new StructureTraction.Settings(64, 0.1, 100, 20, 200, 3), (actor, handle) -> true).status()
                    == StructureTraction.Status.TOO_HEAVY, "whole-body mass budget bypassed");
            var unregistered = new ArmorStand(host, player.getX(), player.getY(), player.getZ());
            check(StructureTraction.tryAcquire(unregistered, controller, hit, SETTINGS, (actor, handle) -> true).status()
                    == StructureTraction.Status.NOT_AVAILABLE, "unregistered actor acquired control");
            unregistered.discard();

            var first = StructureTraction.tryAcquire(player, controller, hit, SETTINGS, (actor, handle) -> true)
                    .session().orElseThrow();
            check(first.update(hit.worldPoint().add(1, 0, 0), Vec3.ZERO) == StructureTraction.Status.ACTIVE,
                    "public traction update was refused");
            SubLevelTraction.tick(server);
            SubLevelDirectControl.tick(server);
            check(runtime.universe().motion(captured.id()).orElseThrow().linearVelocity().x() > 0,
                    "traction did not apply a real native force");
            first.close();
            runtime.universe().setVelocity(captured.id(), PhysicsVector.ZERO, PhysicsVector.ZERO);
            SubLevelTraction.tick(server);
            SubLevelDirectControl.tick(server);
            check(runtime.universe().motion(captured.id()).orElseThrow().linearVelocity().equals(PhysicsVector.ZERO),
                    "released traction retained a queued force");
            check(first.update(hit.worldPoint(), Vec3.ZERO) == StructureTraction.Status.CLOSED,
                    "closed session reacquired control");

            var previous = StructureTraction.tryAcquire(player, controller, hit, SETTINGS, (actor, handle) -> true)
                    .session().orElseThrow();
            var successor = StructureTraction.tryAcquire(player, controller, hit, SETTINGS, (actor, handle) -> true)
                    .session().orElseThrow();
            previous.close();
            check(successor.status() == StructureTraction.Status.ACTIVE, "stale close revoked successor");
            var foreign = new ArmorStand(host, player.getX(), player.getY(), player.getZ());
            check(host.addFreshEntity(foreign), "could not register foreign controller");
            try {
                check(StructureDirectControl.acquire(host, reference, foreign, UUID.randomUUID())
                        == StructureDirectControl.Status.OCCUPIED, "foreign direct controller stole traction lease");
            } finally {
                foreign.discard();
            }
            successor.close();

            var permitted = new AtomicBoolean(true);
            var revocable = StructureTraction.tryAcquire(player, controller, hit, SETTINGS,
                    (actor, handle) -> permitted.get()).session().orElseThrow();
            permitted.set(false);
            check(revocable.update(hit.worldPoint(), Vec3.ZERO) == StructureTraction.Status.DENIED,
                    "permission was not rechecked on update");
            check(!StructureDirectControl.occupied(reference, hit.runtimeId()), "revocation retained exclusive lease");
            var bounded = StructureTraction.tryAcquire(player, controller, hit, SETTINGS, (actor, handle) -> true)
                    .session().orElseThrow();
            check(bounded.update(new Vec3(1_000_100, source.getY(), source.getZ()), Vec3.ZERO)
                    == StructureTraction.Status.OUT_OF_BOUNDS, "native target domain was bypassed");
            check(!StructureDirectControl.occupied(reference, hit.runtimeId()), "invalid target retained lease");
            AcademyCraft.LOGGER.info("SUBLEVEL_TRACTION_MATRIX_PASSED public_acquire=true native_force=true mass=true"
                    + " permission=true exclusive=true stale_close=true release=true bounds=true");
        } finally {
            player.setYRot(oldYaw);
            player.setXRot(oldPitch);
            SubLevelTraction.clear(server);
            runtime.remove(body.id());
            runtime.persist();
        }
    }

    /** Run after the other server probes; client snapshots should disappear after packet delivery. */
    static void shutdown(MinecraftServer server) {
        var runtime = SubLevelServer.runtime(server).orElseThrow();
        var host = runtime.host();
        var bounds = runtime.subLevels().stream().map(SubLevel::worldBounds).toList();
        SubLevelServer.shutdown(server);
        check(SubLevelServer.runtime(server).isEmpty(), "disabled backend remained accessible");
        for (var box : bounds) {
            check(SubLevelCollisionRuntime.index(host).map(index -> index.query(box).isEmpty()).orElse(true),
                    "disabled backend retained host collision");
        }
        AcademyCraft.LOGGER.info("SUBLEVEL_SHUTDOWN_SERVER_PASSED runtime_absent=true collisions_cleared=true");
    }

    static boolean clientSnapshotsCleared() {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        return level != null && org.academy.internal.client.sublevel.SubLevelClientState.snapshots(level).isEmpty();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("SubLevel traction: " + message);
    }
}
