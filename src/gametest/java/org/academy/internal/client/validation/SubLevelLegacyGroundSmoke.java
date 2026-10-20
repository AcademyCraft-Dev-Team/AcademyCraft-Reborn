package org.academy.internal.client.validation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.movement.VectorStructureService;
import org.academy.internal.common.ability.accelerator.skills.lv3.KineticThrow;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.misaka.MisakaNetworkServer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reproduces the real saved legacy target at its original pose and terrain. */
final class SubLevelLegacyGroundSmoke {
    private static final UUID TARGET = UUID.fromString("f4628c1c-15e4-4f8a-9b23-22dea15cce2e");
    private static int phase;
    private static int ticks;
    private static StructureRef reference;
    private static StructurePose originalPose;
    private static int cellCount;
    private static Vec3 playerPosition;
    private static Vec3 playerVelocity;
    private static float playerYaw;
    private static float playerPitch;
    private static boolean playerNoGravity;
    private static GameType playerMode;
    private static List<StructurePhysicsRegionInfo> originalRegions;

    private SubLevelLegacyGroundSmoke() {}

    static boolean complete() { return phase == 2; }

    static void tick(MinecraftServer server) {
        if (complete()) return;
        var player = server.getPlayerList().getPlayers().getFirst();
        var level = player.level();
        var runtime = NativeStructureRuntime.backend(server);
        if (++ticks > 240) throw new IllegalStateException("Original-pose legacy adoption timed out");
        if (phase == 0) {
            reference = runtime.loadedReferences().stream().filter(ref -> ref.id().equals(TARGET)).findFirst().orElse(null);
            if (reference == null) {
                if (ticks < 80) return;
                AcademyCraft.LOGGER.info("CLIENT_LEGACY_GROUND_SKIPPED reason=original_saved_target_absent");
                phase = 2;
                return;
            }
            var handle = runtime.resolve(reference).orElse(null);
            if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) return;
            var origin = handle.pose().worldOrigin();
            var projection = level.getEntitiesOfClass(NativeStructureProjectionEntity.class,
                    new AABB(origin, origin).inflate(32)).stream()
                    .filter(entity -> !entity.isRemoved() && entity.viewState().reference().equals(reference))
                    .findFirst().orElse(null);
            if (projection == null || projection.worldCollisionBoxes().isEmpty()) return;
            originalPose = handle.pose();
            cellCount = runtime.presentedCellCount(reference);
            originalRegions = StructurePhysics.savedRegions(level);
            boolean regionsReady = runtime.savedPhysicsEntries(level).stream().allMatch(entry ->
                    StructurePhysics.attachedRegion(level, entry.reference())
                            .filter(region -> region.status() == StructurePhysicsRegion.Status.READY).isPresent());
            if (!regionsReady && ticks < 80) return;
            for (var entry : runtime.savedPhysicsEntries(level)) {
                var region = StructurePhysics.attachedRegion(level, entry.reference()).orElse(null);
                AcademyCraft.LOGGER.info("CLIENT_LEGACY_GROUND_REGION target={} member={} region={} bounds={}"
                                + " status={} diagnostic={}", reference, entry.reference(), entry.physicsAttachment().regionId(),
                        entry.physicsAttachment().bounds(), region == null ? "absent" : region.status(),
                        region == null ? "absent" : region.diagnostic());
            }
            if (!regionsReady) {
                // A failed checkpoint is deliberately non-resumable and requires an explicit
                // discard; it must not turn the unrelated ground/lift smoke into a failure.
                AcademyCraft.LOGGER.warn("CLIENT_LEGACY_GROUND_SKIPPED reason=unresumable_saved_region target={} ticks={}",
                        reference, ticks);
                phase = 2;
                return;
            }
            require(StructurePhysics.attachedRegion(level, reference).isEmpty(), "Original target was already attached");
            requireSinglePressHandler();
            playerPosition = player.position();
            playerVelocity = player.getDeltaMovement();
            playerYaw = player.getYRot();
            playerPitch = player.getXRot();
            playerNoGravity = player.isNoGravity();
            playerMode = player.gameMode.getGameModeForPlayer();
            enable(player);
            require(aimAtOriginalTarget(player, projection.worldCollisionBoxes()),
                    "Original legacy structure has no unobstructed local viewpoint");
            AcademyCraft.LOGGER.info("CLIENT_LEGACY_GROUND_START reference={} original_pose={} boxes={} eye={}",
                    reference, originalPose, projection.worldCollisionBoxes().size(), player.getEyePosition());
            var press = KineticThrow.PressPacket.INSTANCE;
            press.setPacketListener(player.connection);
            KineticThrow.Server.handlePress(press);
            var diagnostic = VectorStructureService.diagnostic(player).orElseThrow(
                    () -> new IllegalStateException("Original-pose legacy H was rejected"));
            require(reference.equals(diagnostic.reference()), "Original legacy H replaced target identity");
            require(handle.pose().equals(originalPose), "Adoption moved the original legacy target before physics");
            var region = StructurePhysics.attachedRegion(level, reference).orElseThrow();
            require(originalRegions.stream().anyMatch(saved -> saved.id().equals(region.id())),
                    "Original-pose adoption opened another region instead of reusing the reservation");
            require(StructurePhysics.savedRegions(level).size() == originalRegions.size(),
                    "Original-pose adoption changed the number of saved regions");
            phase = 1;
            ticks = 0;
        } else {
            var diagnostic = VectorStructureService.diagnostic(player).orElseThrow(
                    () -> new IllegalStateException("Original-pose legacy throw lost its pending session"));
            if (!diagnostic.flying()) return;
            var region = StructurePhysics.attachedRegion(level, reference).orElseThrow();
            var motion = region.motion(reference).orElseThrow();
            if (motion.pose().worldOrigin().distanceTo(originalPose.worldOrigin()) <= 0.05) return;
            require(runtime.presentedCellCount(reference) == cellCount, "Original legacy cells changed during adoption");
            AcademyCraft.LOGGER.info("CLIENT_LEGACY_GROUND_ADOPTION_PASSED reference={} region={}"
                            + " original_pose={} final_pose={} speed={} cells={} status={}",
                    reference, region.id(), originalPose, motion.pose(), motion.linearVelocity().length(), cellCount, region.status());
            VectorStructureService.clear(player);
            require(region.detach(reference), "Original legacy target could not detach after test");
            runtime.resolve(reference).orElseThrow().setPose(originalPose);
            player.connection.teleport(playerPosition.x, playerPosition.y, playerPosition.z, playerYaw, playerPitch);
            player.setYHeadRot(playerYaw);
            player.setDeltaMovement(playerVelocity);
            player.setNoGravity(playerNoGravity);
            player.setGameMode(playerMode);
            phase = 2;
        }
    }

    private static boolean aimAtOriginalTarget(ServerPlayer player, List<AABB> boxes) {
        var positions = new java.util.ArrayList<Vec3>();
        positions.add(player.getEyePosition());
        for (var box : boxes) {
            var center = box.getCenter();
            positions.add(center.add(4, 1, 0));
            positions.add(center.add(-4, 1, 0));
            positions.add(center.add(0, 1, 4));
            positions.add(center.add(0, 1, -4));
            positions.add(center.add(0, 4, 0));
        }
        for (var eye : positions) {
            var feet = eye.add(0, -player.getEyeHeight(), 0);
            if (!player.level().noCollision(player, player.getBoundingBox().move(feet.subtract(player.position())))) continue;
            for (var box : boxes) {
                var delta = box.getCenter().subtract(eye);
                if (delta.lengthSqr() < 0.01 || delta.lengthSqr() > 24 * 24) continue;
                var hit = StructureQueries.raycast(player.level(), eye, eye.add(delta.normalize().scale(24)));
                if (hit.isEmpty() || !hit.get().block().structure().equals(reference)) continue;
                var yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
                var pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
                player.connection.teleport(feet.x, feet.y, feet.z, yaw, pitch);
                player.setYHeadRot(yaw);
                player.setDeltaMovement(Vec3.ZERO);
                return true;
            }
        }
        return false;
    }

    private static void requireSinglePressHandler() {
        try {
            var field = MisakaNetworkServer.NETWORK_MANAGER.getClass().getDeclaredField("typedHandlers");
            field.setAccessible(true);
            var handlers = (Map<?, ?>) field.get(MisakaNetworkServer.NETWORK_MANAGER);
            var matching = (List<?>) handlers.get(KineticThrow.PressPacket.class);
            require(matching != null && matching.size() == 1, "Kinetic H packet has duplicate network registrations");
            AcademyCraft.LOGGER.info("CLIENT_KINETIC_PRESS_REGISTRATION handlers={}", matching.size());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect the real H packet registration", failure);
        }
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
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }
}
