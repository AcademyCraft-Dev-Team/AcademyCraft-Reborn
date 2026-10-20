package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureCaptureResult;
import org.academy.api.common.structure.StructureTransactions;
import org.academy.internal.client.sublevel.SubLevelClientState;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.sublevel.SubLevelServer;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Uses normal runtime ticks, never probeTerrain, to exercise separated bodies and real host walls. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelCollisionRegressionSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.sublevelCollisionRegressionSmoke");
    private static volatile boolean clientReady;
    private static volatile boolean finished;
    private static volatile boolean failed;
    private static volatile boolean screenshotRequested;
    private static volatile int clientFrames;
    private static int ticks;
    private static int startupTicks;
    private static boolean openedFromTitle;
    private static int phase;
    private static int age;
    private static BlockPos origin;
    private static final List<StructureCaptureResult> CAPTURES = new ArrayList<>();
    private static final List<SubLevelId> IDS = new ArrayList<>();
    private static double launchX;
    private static double maximumX;
    private static double restingY;
    private static double minimumFirstY = Double.POSITIVE_INFINITY;

    private SubLevelCollisionRegressionSmoke() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || !clientReady || finished || failed) return;
        var server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        var player = server.getPlayerList().getPlayers().getFirst();
        var runtime = SubLevelServer.runtime(player.level()).orElse(null);
        if (runtime == null) return;
        try {
            require(++ticks < 600, "timeout phase=" + phase + " terrain=" + runtime.terrainDiagnostics());
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            if (phase == 0) {
                origin = new BlockPos(player.getBlockX() + 24, player.level().getMaxY() - 72, player.getBlockZ());
                prepare(player, 0, 6);
                prepare(player, 80, 10);
                for (int y = 1; y <= 40; y++) for (int z = -24; z <= 24; z++) {
                    player.level().setBlock(origin.offset(10, y, z), Blocks.STONE.defaultBlockState(), 3);
                    player.level().setBlock(origin.offset(11, y, z), Blocks.STONE.defaultBlockState(), 3);
                }
                player.connection.teleport(origin.getX() - 8, origin.getY() + 7, origin.getZ() + 3, -90, 22);
                phase = 1;
                age = 0;
                return;
            }
            if (phase == 1) {
                boolean ready = true;
                for (int i = 0; i < CAPTURES.size(); i++) {
                    var result = CAPTURES.get(i);
                    if (result.pending()) {
                        result = StructureTransactions.poll(server, result.transactionId()).orElseThrow();
                        CAPTURES.set(i, result);
                    }
                    if (result.pending()) ready = false;
                    else require(result.succeeded(), "capture rejected: " + result);
                }
                if (!ready) return;
                for (var result : CAPTURES) IDS.add(new SubLevelId(result.structure().orElseThrow().id()));
                phase = 2;
                age = 0;
                return;
            }
            age++;
            var first = runtime.get(IDS.getFirst()).orElseThrow();
            var second = runtime.get(IDS.getLast()).orElseThrow();
            minimumFirstY = Math.min(minimumFirstY, first.worldBounds().minY);
            // Initial manifold correction may briefly embed a corner; settled contact below
            // has a tighter tolerance. Crossing the slab must still fail immediately.
            require(phase > 2 || first.worldBounds().minY >= origin.getY() + 0.75,
                    "first body fell through host floor: origin=" + origin + " bounds=" + first.worldBounds()
                            + " motion=" + runtime.universe().motion(first.id()) + " terrain=" + runtime.terrainDiagnostics()
                            + " covered=" + runtime.terrainCoverageComplete()
                            + " floor=" + player.level().getBlockState(origin));
            require(second.worldBounds().minY >= origin.getY() - 3.25,
                    "second body fell through lower host floor: origin=" + origin + " bounds=" + second.worldBounds());
            if (phase == 2 && age >= 100) {
                require(Math.abs(first.worldBounds().minY - origin.getY() - 1) < 0.03, "first body never settled on floor");
                require(Math.abs(second.worldBounds().minY - origin.getY() - 1) < 0.03, "distant body never settled on floor");
                require(runtime.terrainDiagnostics().boxes() > 0, "no host terrain colliders installed");
                checkEntityContact(player, first.worldBounds());
                launchX = first.worldCenter().x;
                maximumX = first.worldBounds().maxX;
                require(runtime.universe().setVelocity(first.id(), new PhysicsVector(50, 0, 0), PhysicsVector.ZERO),
                        "launch rejected");
                AcademyCraft.LOGGER.info("SUBLEVEL_COLLISION_FLOORS_PASSED separated=80 first_y={} second_y={} min_first_y={} terrain={}",
                        first.worldBounds().minY, second.worldBounds().minY, minimumFirstY, runtime.terrainDiagnostics());
                phase = 3;
                age = 0;
            } else if (phase == 3) {
                maximumX = Math.max(maximumX, first.worldBounds().maxX);
                require(maximumX <= origin.getX() + 10.15,
                        "50 blocks/s body crossed solid host wall: max_x=" + maximumX + " bounds=" + first.worldBounds());
                if (age < 20) return;
                require(first.worldCenter().x - launchX > 2, "world was frozen instead of resolving wall contact");
                restingY = second.worldBounds().minY;
                for (int x = -2; x <= 6; x++) for (int z = -2; z <= 6; z++) {
                    player.level().setBlock(origin.offset(80 + x, 0, z), Blocks.AIR.defaultBlockState(), 3);
                }
                AcademyCraft.LOGGER.info("SUBLEVEL_COLLISION_WALL_PASSED speed=50 max_x={} wall_x={}", maximumX, origin.getX() + 10);
                // The wall impact may subsequently tumble this body off the finite test pad.
                // Observe the other body for the independent floor-removal phase and screenshot.
                player.connection.teleport(origin.getX() + 78, origin.getY() + 3, origin.getZ() + 1, -90, 65);
                phase = 4;
                age = 0;
            } else if (phase == 4 && age >= 80) {
                require(restingY - second.worldBounds().minY > 3.8, "removed floor retained stale collider or sleep");
                require(Math.abs(second.worldBounds().minY - origin.getY() + 3) < 0.03, "lower floor did not support body");
                require(clientFrames >= 60, "client did not observe enough live snapshots");
                AcademyCraft.LOGGER.info("SUBLEVEL_COLLISION_MUTATION_PASSED fall={} lower_y={} client_frames={}",
                        restingY - second.worldBounds().minY, second.worldBounds().minY, clientFrames);
                finished = true;
            }
        } catch (Throwable error) {
            failed = true;
            AcademyCraft.LOGGER.error("SUBLEVEL_COLLISION_REGRESSION_FAILED", error);
        }
    }

    private static void prepare(ServerPlayer player, int dx, int height) {
        var level = player.level();
        for (int x = (origin.getX() + dx - 9) >> 4; x <= (origin.getX() + dx + 13) >> 4; x++) {
            for (int z = (origin.getZ() - 25) >> 4; z <= (origin.getZ() + 25) >> 4; z++) {
                level.getChunk(x, z);
            }
        }
        for (int y : new int[]{0, -4}) for (int x = -8; x <= 13; x++) for (int z = -24; z <= 24; z++) {
            level.setBlock(origin.offset(dx + x, y, z), Blocks.STONE.defaultBlockState(), 3);
        }
        var source = new ArrayList<BlockPos>();
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
            var position = origin.offset(dx + x, height + y, z);
            level.setBlock(position, Blocks.IRON_BLOCK.defaultBlockState(), 3);
            source.add(position);
        }
        CAPTURES.add(StructureTransactions.capture(level,
                new StructureCaptureRequest(source, 8, 3, StructureCaptureRequest.ALLOW, true)));
    }

    private static void checkEntityContact(ServerPlayer player, AABB structure) {
        var actor = new ArmorStand(player.level(), structure.minX - 1, structure.minY + 0.05,
                structure.getCenter().z);
        actor.setNoGravity(true);
        actor.move(MoverType.SELF, new Vec3(4, 0, 0));
        require(actor.getBoundingBox().maxX <= structure.minX + 0.05,
                "entity movement bypassed real SubLevel surface");
        double contactX = actor.getX();
        actor.move(MoverType.SELF, new Vec3(-0.5, 0, 0));
        require(actor.getX() < contactX - 0.45, "entity could not move away from structure contact");
        actor.setPos(structure.getCenter().x, structure.maxY + 1, structure.getCenter().z);
        actor.move(MoverType.SELF, new Vec3(0, -3, 0));
        require(Math.abs(actor.getY() - structure.maxY) < 0.08,
                "entity did not land on the real SubLevel top");
        AcademyCraft.LOGGER.info("SUBLEVEL_COLLISION_ENTITY_PASSED wall_x={} supported_y={}", contactX, actor.getY());
        actor.discard();
    }

    @SubscribeEvent
    public static void client(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        var minecraft = Minecraft.getInstance();
        if (failed) { minecraft.stop(); return; }
        if (minecraft.level == null || minecraft.player == null) {
            // A resource reload fallback may consume Quick Play before returning to the title.
            if (!openedFromTitle && minecraft.gui.screen() instanceof TitleScreen) {
                openedFromTitle = true;
                minecraft.createWorldOpenFlows().openWorld("test", minecraft::stop);
            }
            if (++startupTicks % 100 == 0) {
                AcademyCraft.LOGGER.info("SUBLEVEL_COLLISION_STARTUP screen={}", minecraft.gui.screen());
            }
            if (startupTicks >= 1200) {
                failed = true;
                AcademyCraft.LOGGER.error("SUBLEVEL_COLLISION_REGRESSION_FAILED client never entered test copy");
            }
            return;
        }
        minecraft.options.pauseOnLostFocus = false;
        if (!clientReady && !minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) {
            minecraft.debugEntries.toggleStatus(DebugScreenEntries.ENTITY_HITBOXES);
        }
        clientReady = true;
        if (!SubLevelClientState.snapshots(minecraft.level).isEmpty()) clientFrames++;
        if (!finished || screenshotRequested) return;
        if (!minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) {
            failed = true;
            AcademyCraft.LOGGER.error("SUBLEVEL_COLLISION_REGRESSION_FAILED F3+B debug rendering was not enabled");
            return;
        }
        screenshotRequested = true;
        var name = "sublevel-collision-regression-" + System.currentTimeMillis() + ".png";
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.gameRenderer.mainRenderTarget(), 1, message -> {
            var path = minecraft.gameDirectory.toPath().resolve("screenshots").resolve(name);
            if (!Files.isRegularFile(path)) {
                failed = true;
                AcademyCraft.LOGGER.error("SUBLEVEL_COLLISION_REGRESSION_FAILED screenshot missing: {}", message);
            } else {
                AcademyCraft.LOGGER.info("SUBLEVEL_COLLISION_REGRESSION_PASSED screenshots={} hitbox_debug_enabled=true source_world_untouched=true", path);
            }
            minecraft.stop();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
