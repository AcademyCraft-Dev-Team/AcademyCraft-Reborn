package org.academy.internal.client.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.academy.internal.common.structure.transaction.SubLevelCaptureTarget;
import org.academy.internal.common.sublevel.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Default public capture path in all three vanilla hosts, with persistent restart fixtures. */
final class SubLevelMultiHostSmoke {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private SubLevelMultiHostSmoke() {}

    static void run(MinecraftServer server) {
        var hosts = hosts(server);
        var shared = SubLevelServer.runtime(hosts.getFirst()).orElseThrow().storage();
        var plots = new HashSet<PlotPos>();
        var fixture = new ArrayList<String>();
        int claimsBefore = legacyClaims(server);
        verifyRollbackIsolation(server, hosts.getFirst());
        for (var host : hosts) {
            var runtime = SubLevelServer.runtime(host).orElseThrow();
            require(runtime.storage() == shared, "hosts use independent plot allocators");
            for (var other : hosts) if (other != host) {
                require(runtime.universe().world() != SubLevelServer.runtime(other).orElseThrow().universe().world(),
                        "hosts share a native collision universe");
            }
            var denied = new BlockPos(1232, host.getMaxY() - 32, 1232);
            host.getChunkAt(denied);
            host.setBlock(denied, Blocks.IRON_BLOCK.defaultBlockState(), FLAGS);
            int allocated = shared.allocated();
            var rejected = StructureTransactions.capture(host, new StructureCaptureRequest(List.of(denied), 1, 1,
                    (level, position) -> false, true));
            require(rejected.status() == StructureCaptureResult.Status.PERMISSION_DENIED,
                    "permission rejection did not use public capture path: " + rejected);
            require(host.getBlockState(denied).is(Blocks.IRON_BLOCK) && shared.allocated() == allocated,
                    "permission rejection changed source or plot ownership");
            host.setBlock(denied, Blocks.AIR.defaultBlockState(), FLAGS);
            for (int index = 0; index < 2; index++) {
                var source = denied.offset(index * 8, 0, 8);
                host.getChunkAt(source);
                // Persist a small floor so the restart fixture does not fall into the End void.
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                    var floor = source.offset(x, -1, z);
                    host.getChunkAt(floor);
                    host.setBlock(floor, Blocks.STONE.defaultBlockState(), FLAGS);
                }
                host.setBlock(source, Blocks.DIAMOND_BLOCK.defaultBlockState(), FLAGS);
                var captured = StructureTransactions.capture(host, new StructureCaptureRequest(List.of(source), 1, 1,
                        StructureCaptureRequest.ALLOW, true));
                require(captured.succeeded(), "default public capture failed: " + captured);
                var reference = captured.structure().orElseThrow();
                require(SubLevelStructureRefs.isSubLevel(reference), "default capture selected the legacy four-slot backend");
                var id = new SubLevelId(reference.id());
                var body = runtime.get(id).orElseThrow();
                require(host.getBlockState(source).isAir(), "successful capture retained source");
                require(plots.add(body.plot()), "two hosts reused the same live plot");
                require(SubLevelServer.runtime(server, id).orElseThrow() == runtime, "identity lookup selected a foreign host");
                verifyControl(server, host, reference);
                fixture.add(host.dimension().identifier() + " " + id.value() + " " + body.plot().x() + " " + body.plot().z());
                runtime.flushStorage();
                runtime.persist();
            }
        }
        require(fixture.size() == 6 && legacyClaims(server) == claimsBefore,
                "new captures consumed or removed legacy slot ownership");
        try {
            Files.write(fixturePath(server), fixture);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot persist multihost smoke identity fixture", failure);
        }
        SubLevelCaptureRecoverySmoke.prepare(server);
        AcademyCraft.LOGGER.info("SUBLEVEL_MULTIHOST_PASSED hosts=3 captures=6 shared_allocator=true"
                + " isolated_worlds=true permission=true foreign_host_rejected=true legacy_claims={}", claimsBefore);
    }

    static void verifyRestored(MinecraftServer server) {
        SubLevelCaptureRecoverySmoke.verifyRestored(server);
        List<String> lines;
        try {
            lines = Files.readAllLines(fixturePath(server));
        } catch (IOException failure) {
            throw new IllegalStateException("Missing multihost restart fixture", failure);
        }
        require(lines.size() == 6, "incomplete restart fixture");
        var plots = new HashSet<PlotPos>();
        for (var line : lines) {
            var fields = line.split(" ");
            require(fields.length == 4, "invalid restart fixture entry");
            var host = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(fields[0])));
            require(host != null, "restored host missing");
            var runtime = SubLevelServer.runtime(host).orElseThrow();
            var id = new SubLevelId(UUID.fromString(fields[1]));
            var body = runtime.get(id).orElseThrow();
            require(body.plot().equals(new PlotPos(Integer.parseInt(fields[2]), Integer.parseInt(fields[3]))),
                    "restart changed plot ownership");
            require(plots.add(body.plot()), "restored host plot overlap");
            require(runtime.storage().blockState(body.plot(), BlockPos.ZERO).is(Blocks.DIAMOND_BLOCK),
                    "restored plot content changed");
            var reference = new SubLevelStructureHandle(body, runtime.storage(), host.dimension().identifier()).reference();
            verifyControl(server, host, reference);
        }
        AcademyCraft.LOGGER.info("SUBLEVEL_MULTIHOST_RESTART_PASSED hosts=3 restored=6 control=true contents=true");
    }

    private static void verifyControl(MinecraftServer server, ServerLevel host, StructureRef reference) {
        var runtime = SubLevelServer.runtime(host).orElseThrow();
        var id = new SubLevelId(reference.id());
        var handle = StructureSpaces.resolve(server, reference).orElseThrow();
        var center = handle.pose().localToWorldPoint(new Vec3(0.5, 0.5, 0.5));
        var actor = new ArmorStand(host, center.x + 3, center.y, center.z);
        require(host.addFreshEntity(actor), "could not register host controller");
        var token = UUID.randomUUID();
        try {
            require(StructureDirectControl.acquire(host, reference, actor, token) == StructureDirectControl.Status.ACTIVE,
                    "host direct lease refused");
            require(StructureDirectControl.submit(host, reference, actor, token,
                    new StructurePhysicsControl.Anchor(new Vec3(0.5, 0.5, 0.5), center.add(1, 0, 0), Vec3.ZERO, 100, 20, 200)),
                    "host bounded anchor refused");
            SubLevelDirectControl.tick(server);
            require(StructureDynamics.motion(host, reference).orElseThrow().linearVelocity().x > 0,
                    "host native body did not receive force");
            for (var other : hosts(server)) if (other != host) {
                require(StructureDynamics.motion(other, reference).isEmpty(), "foreign host observed native motion");
                var forged = new StructureRef(reference.id(), other.dimension().identifier(), reference.backend(), reference.generation());
                require(StructureSpaces.resolve(server, forged).isEmpty(), "forged host resolved a structure");
            }
        } finally {
            StructureDirectControl.release(reference, token);
            runtime.universe().setVelocity(id, PhysicsVector.ZERO, PhysicsVector.ZERO);
            actor.discard();
        }
    }

    /** Exercises rollback after body creation without poisoning the shared storage error state. */
    private static void verifyRollbackIsolation(MinecraftServer server, ServerLevel host) {
        var runtime = SubLevelServer.runtime(host).orElseThrow();
        var source = new BlockPos(1224, host.getMaxY() - 32, 1232);
        host.getChunkAt(source);
        host.setBlock(source, Blocks.EMERALD_BLOCK.defaultBlockState(), FLAGS);
        var target = new SubLevelCaptureTarget(server);
        var reference = target.allocate(host, source, BlockPos.ZERO, Blocks.EMERALD_BLOCK.defaultBlockState(),
                List.of(source), UUID.randomUUID()).orElseThrow();
        var id = new SubLevelId(reference.id());
        var activation = SubLevelActivationRegistry.activation(id);
        var token = UUID.randomUUID();
        var actor = new ArmorStand(host, source.getX() + 3, source.getY(), source.getZ());
        require(host.addFreshEntity(actor), "could not register rollback controller");
        try {
            require(!target.finalizeTarget(reference, source, List.of(source)), "empty staging plot finalized");
            require(runtime.universe().motion(id).isEmpty() && host.getBlockState(source).is(Blocks.EMERALD_BLOCK),
                    "failed finalize created a body or changed the source");
            require(target.writeCell(reference, BlockPos.ZERO, Blocks.EMERALD_BLOCK.defaultBlockState())
                    == StructureWriteResult.APPLIED, "could not copy rollback fixture");
            require(target.finalizeTarget(reference, source, List.of(source)), "rollback fixture did not activate");
            require(StructureDirectControl.acquire(host, reference, actor, token) == StructureDirectControl.Status.ACTIVE,
                    "rollback fixture lease refused");
            // This is the coordinator's failure branch after finalization; the source is still intact.
            target.releasePhysics(host, reference);
            require(runtime.get(id).orElseThrow().state() == SubLevelState.ALLOCATED,
                    "rollback copy remained published");
            require(runtime.universe().motion(id).isEmpty() && runtime.collisionIndex().worldBounds(id).isEmpty()
                    && runtime.localCollisionBoxes(id).isEmpty() && runtime.view(id).isEmpty(),
                    "rollback retained a body, collision or view");
            require(SubLevelActivationRegistry.activation(id).equals(activation)
                    && target.resolve(reference, false).isPresent(), "rollback lost transaction identity");
            require(StructureSpaces.resolve(server, reference).isEmpty()
                    && !StructureDirectControl.occupied(reference, activation.runtimeId()),
                    "rollback retained public access or a control lease");
            require(host.getBlockState(source).is(Blocks.EMERALD_BLOCK)
                    && target.readCell(reference, BlockPos.ZERO).orElseThrow().is(Blocks.EMERALD_BLOCK),
                    "rollback isolation discarded source or retained copy");
        } finally {
            StructureDirectControl.release(reference, token);
            actor.discard();
            target.releasePhysics(host, reference);
            require(target.releaseEmpty(reference), "rollback plot could not be reclaimed");
            host.setBlock(source, Blocks.AIR.defaultBlockState(), FLAGS);
        }
        AcademyCraft.LOGGER.info("SUBLEVEL_CAPTURE_ROLLBACK_PASSED empty_finalize_rejected=true source_preserved=true"
                + " body_removed=true collision_removed=true view_removed=true lease_revoked=true identity_preserved=true");
    }

    private static List<ServerLevel> hosts(MinecraftServer server) {
        return List.of(server.overworld(), java.util.Objects.requireNonNull(server.getLevel(Level.NETHER)),
                java.util.Objects.requireNonNull(server.getLevel(Level.END)));
    }

    private static int legacyClaims(MinecraftServer server) {
        var root = server.getWorldPath(LevelResource.ROOT).resolve("data/academy/native_structures");
        int result = 0;
        for (int slot = 0; slot < 4; slot++) if (Files.exists(root.resolve("owner_" + slot + ".txt"))) result++;
        return result;
    }

    private static Path fixturePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data/academy/sublevel_multihost_smoke.txt");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("SubLevel multihost: " + message);
    }
}
