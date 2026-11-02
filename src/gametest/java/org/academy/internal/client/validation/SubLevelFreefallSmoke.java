package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureDynamics;
import org.academy.api.common.structure.BlockStructureApi;
import org.academy.api.common.structure.BlockStructureCaptureOptions;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.movement.VectorStructureService;
import org.academy.internal.common.ability.accelerator.skills.lv3.KineticThrow;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;

import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in isolated integrated-client regression for a lifted structure released without launch. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelFreefallSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.sublevelFreefallSmoke");
    private static volatile Path directory;
    private static volatile boolean finished;
    private static volatile boolean failed;
    private static volatile boolean screenshotRequested;
    private static volatile boolean screenshotSaved;
    private static volatile boolean aimActive;
    private static volatile float aimYaw;
    private static volatile float aimPitch;
    private static int phase;
    private static int age;
    private static int fixture;
    private static BlockPos base;
    private static StructureRef reference;
    private static boolean landed;
    private static boolean slept;
    private static boolean restored;
    private static double lastBottom;
    private static int screenshotFrames;
    private static double minimumBottom;
    private static double maximumUpwardVelocity;
    private static final StringBuilder RESULTS = new StringBuilder();

    private SubLevelFreefallSmoke() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverTick(ServerTickEvent.Post event) {
        if (!ENABLED || directory == null || finished || failed) return;
        var players = event.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        var player = players.getFirst();
        if (SubLevelServer.runtime(player.level()).isEmpty()) return;
        try {
            require(++age < 800, "phase timeout: " + phase);
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            switch (phase) {
                case 0 -> {
                    prepare(player);
                    next();
                }
                case 1 -> {
                    // Let the initial teleport and destination chunk tracking settle.
                    if (age < 20) return;
                    var probe = BlockStructureApi.selectLowerEllipsoidWithUpperCylinder(player.level(), base.offset(0, 3, -1),
                            5, 2, 5, 5, BlockStructureCaptureOptions.CapturePolicy.MOVABLE);
                    require(probe.succeeded(), "capture selection is unavailable before charge: " + probe);
                    var packet = KineticThrow.PressPacket.INSTANCE;
                    packet.setPacketListener(player.connection);
                    KineticThrow.Server.handlePress(packet);
                    next();
                }
                case 2 -> {
                    if (age < 4) return;
                    var packet = KineticThrow.ReleasePacket.INSTANCE;
                    packet.setPacketListener(player.connection);
                    KineticThrow.Server.handleRelease(packet);
                    next();
                }
                case 3 -> {
                    var diagnostic = VectorStructureService.diagnostic(player).orElseThrow();
                    require(!diagnostic.flying(), "capture became a projectile");
                    if (!diagnostic.lifted()) return;
                    reference = diagnostic.reference();
                    require(diagnostic.blockCount() == 9, "expected a nine-block source platform");
                    var motion = StructureDynamics.motion(player.level(), reference).orElseThrow();
                    var body = SubLevelServer.runtime(player.level()).orElseThrow()
                            .get(SubLevelStructureRefs.id(reference).orElseThrow()).orElseThrow();
                    require(Math.abs(body.worldBounds().minY - (base.getY() + 6)) < 0.1,
                            "skill did not lift the source three blocks");
                    if (motion.linearVelocity().length() > 0.01 || motion.angularVelocity().length() > 0.01) return;
                    VectorStructureService.terminate(player);
                    require(VectorStructureService.diagnostic(player).isEmpty(), "lift control was not released");
                    AcademyCraft.LOGGER.info("SUBLEVEL_FREEFALL_RELEASE fixture={} bounds={} motion={}",
                            fixture, body.worldBounds(), motion);
                    minimumBottom = Double.POSITIVE_INFINITY;
                    maximumUpwardVelocity = 0;
                    landed = false;
                    slept = false;
                    restored = false;
                    next();
                }
                case 4 -> {
                    require(VectorStructureService.diagnostic(player).isEmpty(), "unexpected skill control during freefall");
                    var runtime = SubLevelServer.runtime(player.level()).orElseThrow();
                    var body = runtime.get(SubLevelStructureRefs.id(reference).orElseThrow()).orElse(null);
                    if (body != null) {
                        var motion = StructureDynamics.motion(player.level(), reference).orElseThrow();
                        lastBottom = body.worldBounds().minY - (base.getY() + 1);
                        minimumBottom = Math.min(minimumBottom, lastBottom);
                        maximumUpwardVelocity = Math.max(maximumUpwardVelocity, motion.linearVelocity().y());
                        landed |= lastBottom < 0.03;
                        slept |= runtime.universe().motion(body.id()).orElseThrow().sleeping();
                        if (age % 20 == 0 || lastBottom < -0.02) {
                            AcademyCraft.LOGGER.info("SUBLEVEL_FREEFALL_STATE fixture={} tick={} bottom={} motion={} terrain={}",
                                    fixture, age, lastBottom, motion, runtime.terrainDiagnostics());
                        }
                        require(lastBottom >= -0.02, "structure penetrated floor: " + lastBottom + " motion=" + motion);
                        require(motion.linearVelocity().y() <= 0.02, "released structure rebounded: " + motion);
                    } else {
                        // Ordinary skill-created structures restore after resting. Their
                        // disappearance is valid only with all nine cells on the floor.
                        require(landed && slept, "structure disappeared before naturally settling");
                        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                            require(player.level().getBlockState(base.offset(x, 1, z)).is(Blocks.IRON_BLOCK),
                                    "restoration lost or misplaced a released cell");
                            require(player.level().getBlockState(base.offset(x, 0, z)).is(Blocks.STONE),
                                    "restoration damaged the supporting floor");
                        }
                        restored = true;
                    }
                    if (age < 200) return;
                    require(landed && Math.abs(lastBottom) < 0.02 && slept, "released structure did not settle");
                    String result = "fixture=" + (fixture == 0 ? "flat" : "pit")
                            + " cells=9 ticks=" + age + " minimum_bottom=" + minimumBottom
                            + " maximum_upward_velocity=" + maximumUpwardVelocity + " restored=" + restored;
                    RESULTS.append(result).append('\n');
                    AcademyCraft.LOGGER.info("SUBLEVEL_FREEFALL_CASE_PASSED {}", result);
                    if (++fixture < 2) { phase = 0; age = 0; }
                    else { screenshotRequested = true; next(); }
                }
                case 5 -> {
                    if (!screenshotSaved) return;
                    Files.writeString(directory.resolve("sublevel-freefall-result.txt"), "PASSED\n" + RESULTS);
                    AcademyCraft.LOGGER.info("SUBLEVEL_FREEFALL_SMOKE_PASSED {}", RESULTS);
                    finished = true;
                }
                default -> throw new IllegalStateException("Unexpected phase " + phase);
            }
        } catch (Throwable error) {
            failed = true;
            AcademyCraft.LOGGER.error("SUBLEVEL_FREEFALL_SMOKE_FAILED phase={} fixture={}", phase, fixture, error);
            try { Files.writeString(directory.resolve("sublevel-freefall-result.txt"), "FAILED\n" + error); }
            catch (Exception writeError) { AcademyCraft.LOGGER.error("Could not record freefall failure", writeError); }
        }
    }

    private static void prepare(ServerPlayer player) {
        require(StructureBackendRouter.nativeEnabled(player.level().getServer()), "requires the native backend");
        VectorStructureService.clear(player);
        var level = player.level();
        // Keep the fixture outside the template world's spawn-protection area.
        base = new BlockPos(256 + fixture * 32, Math.min(240, level.getMaxY() - 20), 256);
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            var floor = base.offset(x, 0, z);
            level.getChunkAt(floor);
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            for (int y = 1; y <= 12; y++) level.setBlock(floor.above(y), Blocks.AIR.defaultBlockState(), 3);
            if (fixture == 1 && Math.max(Math.abs(x), Math.abs(z)) == 2) {
                level.setBlock(floor.above(), Blocks.STONE.defaultBlockState(), 3);
            }
            if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                level.setBlock(floor.above(3), Blocks.IRON_BLOCK.defaultBlockState(), 3);
            }
        }
        var system = AbilitySystemServer.getSystem(player);
        var actor = player.getUUID();
        var skill = Skills.KINETIC_THROW.get();
        system.setPlayerAbilityCategory(actor, AbilityCategories.ACCELERATOR.get());
        system.setPlayerLevel(actor, 5);
        system.setPlayerBaseMaxCP(actor, 100_000);
        system.setPlayerAvailableCP(actor, 100_000);
        system.setPlayerStatus(actor, org.academy.api.common.data.AbilityData.Status.NORMAL);
        system.addPlayerSkill(player, skill.getKeyString());
        if (!skill.isEnabled(player)) system.toggleSkill(actor, skill.getKeyString());
        player.setGameMode(GameType.SURVIVAL);
        Vec3 position = Vec3.atLowerCornerOf(base).add(0.5, 2, -6);
        Vec3 delta = Vec3.atCenterOf(base.offset(0, 3, -1)).subtract(position.add(0, player.getEyeHeight(), 0));
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
        player.connection.teleport(position.x, position.y, position.z, yaw, pitch);
        player.setYHeadRot(yaw);
        aimYaw = yaw;
        aimPitch = pitch;
        aimActive = true;
        var eye = player.getEyePosition();
        var hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(24)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        require(level.getBlockState(hit.getBlockPos()).is(Blocks.IRON_BLOCK), "charge ray missed source platform: " + hit);
        require(level.mayInteract(player, hit.getBlockPos()), "source platform is protected");
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        var minecraft = Minecraft.getInstance();
        if (finished || failed) { minecraft.stop(); return; }
        if (minecraft.level == null || minecraft.player == null) return;
        if (directory == null) {
            var path = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
            require(path.getFileName().toString().startsWith("native-sublevel-freefall-"), "requires isolated fixture directory");
            minecraft.options.pauseOnLostFocus = false;
            // Like the H protocol fixture, isolate server-driven commands from
            // physical keyboard/mouse input while the client is running.
            org.lwjgl.glfw.GLFW.glfwIconifyWindow(minecraft.getWindow().handle());
            directory = path;
        }
        if (aimActive) {
            minecraft.player.setNoGravity(true);
            minecraft.player.setDeltaMovement(Vec3.ZERO);
            minecraft.player.setYRot(aimYaw);
            minecraft.player.setXRot(aimPitch);
            minecraft.player.connection.send(new ServerboundMovePlayerPacket.Rot(aimYaw, aimPitch,
                    minecraft.player.onGround(), minecraft.player.horizontalCollision));
        }
        if (screenshotRequested && !screenshotSaved) {
            if (screenshotFrames++ == 0) {
                aimPitch = 20;
                org.lwjgl.glfw.GLFW.glfwRestoreWindow(minecraft.getWindow().handle());
            }
            if (screenshotFrames < 10) return;
            screenshotRequested = false;
            Screenshot.grab(minecraft.gameDirectory, "sublevel-freefall.png", minecraft.gameRenderer.mainRenderTarget(), 1,
                    message -> screenshotSaved = Files.isRegularFile(directory.resolve("screenshots/sublevel-freefall.png")));
        }
    }

    private static void next() { phase++; age = 0; }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Freefall smoke: " + message);
    }
}
