package org.academy.internal.client.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureTransactions;
import org.academy.internal.common.structure.physics.*;
import org.academy.internal.common.sublevel.*;

import java.util.List;
import java.util.Objects;

/** Real host mutations must replace native terrain, including cached air and capture removal. */
final class SubLevelTerrainMutationSmoke {
    private SubLevelTerrainMutationSmoke() {}

    static void run(MinecraftServer server) {
        for (var host : List.of(server.overworld(), Objects.requireNonNull(server.getLevel(Level.NETHER)),
                Objects.requireNonNull(server.getLevel(Level.END)))) verify(host);
        AcademyCraft.LOGGER.info("SUBLEVEL_TERRAIN_MUTATION_PASSED hosts=3 removal=true placement=true capture_removal=true");
    }

    private static void verify(ServerLevel host) {
        var runtime = SubLevelServer.runtime(host).orElseThrow();
        var world = runtime.universe().world();
        // The same coordinates in each host, on section boundaries and above generated terrain.
        var source = new BlockPos(1423, host.getMaxY() - 81, 1423);
        host.getChunkAt(source);
        require(host.getBlockState(source).isAir(), "fixture location occupied: " + source);
        var scan = new AABB(source);
        var rest = new PhysicsPose(new PhysicsVector(source.getX() + 0.5, source.getY() + 1.25,
                source.getZ() + 0.5), PhysicsQuaternion.IDENTITY);
        SubLevelId captured = null;
        try (var probe = world.createBody(BodyType.DYNAMIC, rest,
                List.of(new PhysicsBox(PhysicsVector.ZERO, PhysicsQuaternion.IDENTITY,
                        new PhysicsVector(0.25, 0.25, 0.25))), 1.0, 0.6, 0.0, true)) {
            host.setBlock(source, Blocks.STONE.defaultBlockState(), SubLevelCaptureService.TRANSACTION_FLAGS);
            runtime.probeTerrain(scan);
            step(world);
            require(probe.pose().position().y() >= source.getY() + 1.20, "new terrain did not support probe");

            host.setBlock(source, Blocks.AIR.defaultBlockState(), SubLevelCaptureService.TRANSACTION_FLAGS);
            runtime.probeTerrain(scan);
            reset(probe, rest);
            step(world);
            double afterRemoval = probe.pose().position().y();
            require(afterRemoval < source.getY() + 0.95, "deleted host block retained native collision: " + afterRemoval);

            // The preceding empty capture installed the retry delay; this edit must bypass it.
            host.setBlock(source, Blocks.STONE.defaultBlockState(), SubLevelCaptureService.TRANSACTION_FLAGS);
            runtime.probeTerrain(scan);
            reset(probe, rest);
            step(world);
            require(probe.pose().position().y() >= source.getY() + 1.20, "placement waited for empty-section retry");

            var result = StructureTransactions.capture(host, new StructureCaptureRequest(List.of(source), 1, 1,
                    StructureCaptureRequest.ALLOW, true));
            require(result.succeeded(), "public terrain capture failed: " + result);
            captured = new SubLevelId(result.structure().orElseThrow().id());
            require(host.getBlockState(source).isAir(), "public capture left the source block");
            require(runtime.shift(captured, new Vec3(8, 0, 0)), "could not move captured copy away from probe");
            runtime.probeTerrain(scan);
            reset(probe, rest);
            step(world);
            double afterCapture = probe.pose().position().y();
            require(afterCapture < source.getY() + 0.95, "captured source retained native collision: " + afterCapture);
            AcademyCraft.LOGGER.info("SUBLEVEL_TERRAIN_MUTATION_HOST host={} source={} removed_probe_y={} captured_probe_y={}",
                    host.dimension().identifier(), source, afterRemoval, afterCapture);
        } finally {
            if (captured != null) runtime.remove(captured);
            host.setBlock(source, Blocks.AIR.defaultBlockState(), SubLevelCaptureService.TRANSACTION_FLAGS);
            runtime.probeTerrain(scan);
        }
    }

    private static void reset(NativePhysicsBody probe, PhysicsPose pose) {
        probe.setPose(pose);
        probe.setVelocity(PhysicsVector.ZERO, PhysicsVector.ZERO);
        probe.wake();
    }

    private static void step(NativePhysicsWorld world) {
        for (int step = 0; step < 30; step++) world.step(1.0 / 60.0, 2);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("SubLevel terrain mutation: " + message);
    }
}
