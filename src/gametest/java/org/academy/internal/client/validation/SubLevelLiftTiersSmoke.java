package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureDynamics;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.movement.VectorStructureService;
import org.academy.internal.common.ability.accelerator.skills.lv3.KineticThrow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Opt-in client validation of the live kinetic-throw lift for the three design gradients.
 * A 9x9 isolated platform is captured with a 4/24/44 tick charge (tiers 0/1/2), then the
 * client keeps the actual skill session for 200 consecutive ticks within +/-0.1 blocks.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelLiftTiersSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.sublevelLiftTiersSmoke");
    private static final int[] HEIGHTS = {3, 5, 7};
    private static final int[] CHARGE_TICKS = {4, 24, 44};
    private static final double TOLERANCE = 0.1;
    private static final int HOVER_TICKS = 200;
    private static final int PLATFORM_RADIUS = 4;
    private static final int TIER_SPACING = 40;
    private static final int MAX_AGE = 2_000;

    private static volatile boolean clientReady;
    private static volatile boolean failed;
    private static volatile boolean complete;
    private static volatile boolean closing;
    private static boolean started;
    private static int lastTick = -1;
    private static int age;
    private static int tier;
    private static int phase;
    private static long chargeStart;
    private static int releaseAt;
    private static int hoverTicks;
    private static int hoverBest;
    private static int platformX, platformY, platformZ;
    private static Vec3 sourceCenter;
    private static StructureRef reference;
    private static ServerPlayer player;
    private static Vec3 previousPosition;
    private static Vec3 previousVelocity;
    private static float previousYaw;
    private static float previousPitch;
    private static boolean previousNoGravity;
    private static GameType previousMode;
    private static final List<Map<String, Object>> results = new ArrayList<>();

    private SubLevelLiftTiersSmoke() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverTick(ServerTickEvent.Post event) {
        if (!ENABLED || !clientReady || failed || complete) return;
        var server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty() || lastTick == server.getTickCount()) return;
        lastTick = server.getTickCount();
        try {
            if (!started) {
                begin(server);
                return;
            }
            age++;
            if (age > MAX_AGE) throw new IllegalStateException("lift tiers smoke deadline");
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            switch (phase) {
                case 1 -> tickCharging(server);
                case 2 -> tickCapturing(server);
                case 3 -> tickHovering(server);
                case 4 -> tickLaunching(server);
                default -> throw new IllegalStateException("unexpected phase " + phase);
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static void begin(MinecraftServer server) {
        player = server.getPlayerList().getPlayers().getFirst();
        previousPosition = player.position();
        previousVelocity = player.getDeltaMovement();
        previousYaw = player.getYRot();
        previousPitch = player.getXRot();
        previousNoGravity = player.isNoGravity();
        previousMode = player.gameMode.getGameModeForPlayer();
        enable(player);
        tier = 0;
        buildPlatform();
        phase = 1;
        started = true;
        AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_START origin={} tier=0", player.blockPosition());
    }

    private static void buildPlatform() {
        var level = player.level();
        int x = player.blockPosition().getX() + 40 + tier * TIER_SPACING;
        int z = player.blockPosition().getZ();
        int y = Math.min(level.getMaxY() - 24, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 30);
        platformX = x;
        platformY = y;
        platformZ = z;
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                for (int dy = -2; dy <= 10; dy++) {
                    level.setBlock(new BlockPos(x + dx, y + dy, z + dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
            for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                level.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        sourceCenter = new Vec3(x + 0.5, y + 0.5, z + 0.5);
        var feet = new Vec3(x + 0.5, y + 1, z - 8.5);
        place(feet, sourceCenter);
        var press = KineticThrow.PressPacket.INSTANCE;
        press.setPacketListener(player.connection);
        KineticThrow.Server.handlePress(press);
        chargeStart = player.level().getGameTime();
        AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_PLATFORM tier={} center={} platform={}", tier, sourceCenter,
                new BlockPos(x, y, z));
    }

    private static void tickCharging(MinecraftServer server) {
        if (player.level().getGameTime() - chargeStart < CHARGE_TICKS[tier]) return;
        var release = KineticThrow.ReleasePacket.INSTANCE;
        release.setPacketListener(player.connection);
        KineticThrow.Server.handleRelease(release);
        releaseAt = server.getTickCount();
        phase = 2;
    }

    private static void tickCapturing(MinecraftServer server) {
        var diagnostic = VectorStructureService.diagnostic(player).orElse(null);
        if (diagnostic == null) {
            if (server.getTickCount() - releaseAt > 80) throw new IllegalStateException("capture did not start");
            return;
        }
        if (diagnostic.stage() == VectorStructureService.Stage.CAPTURING) {
            if (server.getTickCount() - releaseAt > 200) throw new IllegalStateException("capture did not finish");
            return;
        }
        if (diagnostic.reference() == null) throw new IllegalStateException("captured structure has no reference");
        reference = diagnostic.reference();
        hoverTicks = 0;
        hoverBest = 0;
        phase = 3;
        AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_CAPTURED tier={} reference={} source={}", tier, reference, sourceCenter);
    }

    private static void tickHovering(MinecraftServer server) {
        var motion = StructureDynamics.motion(player.level(), reference).orElse(null);
        if (motion == null) {
            if (server.getTickCount() - releaseAt > 400) throw new IllegalStateException("lift body unavailable");
            return;
        }
        var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
        double rise = center.y - sourceCenter.y;
        if (Math.abs(rise - HEIGHTS[tier]) <= TOLERANCE) {
            hoverTicks++;
            hoverBest = Math.max(hoverBest, hoverTicks);
        } else {
            hoverTicks = 0;
        }
        if (hoverTicks >= HOVER_TICKS) {
            var result = new LinkedHashMap<String, Object>();
            result.put("tier", tier);
            result.put("height", HEIGHTS[tier]);
            result.put("rise", rise);
            result.put("hover_ticks", hoverTicks);
            result.put("hover_best", hoverBest);
            result.put("pass", true);
            results.add(result);
            AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_TIER_PASSED tier={} height={} rise={} hover={}",
                    tier, HEIGHTS[tier], rise, hoverTicks);
            player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                    player.getYRot() + 180.0F, player.getXRot());
            player.setYHeadRot(player.getYRot());
            if (!VectorStructureService.launch(player)) throw new IllegalStateException("tier launch refused");
            phase = 4;
            releaseAt = server.getTickCount();
        } else if (server.getTickCount() - releaseAt > 700) {
            throw new IllegalStateException("tier " + tier + " did not hold 200 ticks; best=" + hoverBest);
        }
    }

    private static void tickLaunching(MinecraftServer server) {
        if (server.getTickCount() - releaseAt < 20) return;
        tier++;
        if (tier > 2) {
            complete = true;
            finish();
            return;
        }
        buildPlatform();
        phase = 1;
    }

    private static void enable(ServerPlayer player) {
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
        player.setNoGravity(true);
    }

    private static void place(Vec3 feet, Vec3 lookAt) {
        var eye = feet.add(0, player.getEyeHeight(), 0);
        var delta = lookAt.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
        player.connection.teleport(feet.x, feet.y, feet.z, yaw, pitch);
        player.setYHeadRot(yaw);
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(true);
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || closing) return;
        var minecraft = Minecraft.getInstance();
        if (failed || complete) {
            closing = true;
            if (minecraft.player != null) minecraft.player.resetFallDistance();
            minecraft.stop();
            return;
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) return;
        if (!minecraft.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString()
                .startsWith("native-sublevel-direct-smoke")) {
            fail(new IllegalStateException("lift tiers smoke requires an isolated run directory"));
            return;
        }
        clientReady = true;
    }

    private static void finish() {
        AcademyCraft.LOGGER.info("SUBLEVEL_LIFT357_SMOKE_PASSED tiers={} results={}", results.size(), results);
        restore();
    }

    private static void fail(Throwable failure) {
        if (failed || complete) return;
        failed = true;
        AcademyCraft.LOGGER.error("SUBLEVEL_LIFT357_SMOKE_FAILED tier={} phase={} age={} best={} results={}",
                tier, phase, age, hoverBest, results, failure);
        restore();
    }

    private static void restore() {
        if (player == null) return;
        try {
            player.resetFallDistance();
            player.connection.teleport(previousPosition.x, previousPosition.y, previousPosition.z,
                    previousYaw, previousPitch);
            player.setDeltaMovement(previousVelocity);
            player.setNoGravity(previousNoGravity);
            player.setGameMode(previousMode);
        } catch (RuntimeException failure) {
            AcademyCraft.LOGGER.warn("Could not restore lift tiers smoke player", failure);
        }
    }

    public static boolean complete() {
        return complete;
    }
}
