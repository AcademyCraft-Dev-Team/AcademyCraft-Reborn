package org.academy.internal.common.structure.physics.validation;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.data.AbilityData;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePhysicsLease;
import org.academy.api.common.structure.StructurePhysicsRegion;
import org.academy.api.common.structure.StructureQueries;
import org.academy.api.common.structure.StructureTraction;
import org.academy.api.common.structure.StructureTractionSession;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.aeromanip.config.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.skills.lv2.PneumaticGrasp;
import org.academy.internal.common.structure.physics.NativeStructurePhysics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/** Isolated real skill/NPC checks plus an observational window-input client stage. */
final class NativeStructurePhysicsGraspSmokeChecks implements AutoCloseable {
    record ManualState(String stage, String instruction, boolean waiting, boolean restoreBindings) {}
    record Inputs(UUID playerId, int gPress, int gRelease, int hRelease, int scrolls, int scrollSteps,
                  boolean customBinding, boolean ready) {}
    private static volatile ManualState manualState = new ManualState("NONE", "", false, false);
    private static volatile Inputs inputs = new Inputs(null, 0, 0, 0, 0, 0, false, false);
    private enum Phase { ITEM, HOLD, FARTHER_ITEM, SCROLL, RELEASE, OCCUPIED, LOS_ACQUIRE, LOS_BLOCKED, LOS_RESTORED, RANGE_ACQUIRE, RANGE_LOST,
        RESOURCE_EMPTY, NPC, SETTLE, TAP, TAP_SETTLE, CUSTOM_START, CUSTOM_HOLD, CUSTOM_SCROLL,
        CUSTOM_RELEASE, CUSTOM_FALL, FINAL_SETTLE, DONE }
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final double GRAVITY_STEP = 9.81 * 0.05;
    private final ServerLevel host;
    private final StructurePhysicsRegion region;
    private final StructureHandle handle;
    private final DoubleSupplier minY;
    private final double floorTop;
    private final ServerPlayer camera, player;
    private final Connection connection;
    private final EmbeddedChannel channel;
    private final StringBuilder evidence;
    private final Path directory;
    private final Vec3 cellCenter, fixtureEye;
    private Phase phase = Phase.ITEM;
    private int age, ticks, stable, manualTicks, loggedCustomPress = -1;
    private long manualStart, beforeSteps;
    private StructureMotionState before;
    private StructureMotionState lastMotion;
    private Vec3 initialCom, previousTarget;
    private float initialAir;
    private boolean sawCpOccupation, closed;
    private Integer originalAirCapacity;
    private StructurePhysicsLease competingLease;
    private StructureTractionSession npcSession;
    private ArmorStand npc;
    private ItemEntity item;
    private Vec3 itemStart;
    private BlockPos obstruction;
    private Inputs baseline;

    static ManualState manualState() { return manualState; }
    static void observeInputs(Inputs observed) { inputs = observed; }
    boolean manualWindow() { return manualStart != 0 && phase != Phase.DONE; }
    int manualTicks() { return manualTicks; }
    String diagnostic() { return "phase=" + phase + " age=" + age + " fixtureEye=" + fixtureEye + " lastMotion=" + lastMotion; }

    NativeStructurePhysicsGraspSmokeChecks(ServerLevel host, StructurePhysicsRegion region,
                                           StructureHandle handle, Vec3 cellCenter, DoubleSupplier minY,
                                           double floorTop, ServerPlayer camera, Path directory, StringBuilder evidence) {
        require(Boolean.getBoolean("academy.nativeStructurePhysicsSmoke")
                && Boolean.getBoolean("academy.nativeStructurePhysicsGraspSmoke"), "Grasp probe is opt-in only");
        this.host = host; this.region = region; this.handle = handle; this.cellCenter = cellCenter;
        this.minY = minY; this.floorTop = floorTop; this.camera = camera; this.directory = directory; this.evidence = evidence;
        // Every subcase targets the same platform. Re-basing the eye on each newly moved body
        // would repeatedly pull it farther across the world before the final free fall.
        fixtureEye = handle.pose().localToWorldPoint(cellCenter).add(0, 4, 4);
        var server = host.getServer();
        var profile = new GameProfile(UUID.randomUUID(), "grasp-" + UUID.randomUUID().toString().substring(0, 8));
        var cookie = CommonListenerCookie.createInitial(profile, false);
        player = new ServerPlayer(server, host, profile, cookie.clientInformation());
        connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
        server.getConnection().getConnections().add(connection);
        try {
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.connection.markClientLoaded(); player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
            configure(player); aim(player);
            var resolver = new org.academy.internal.common.ability.program.ServerProgramTargetResolver(player, 32, 32);
            var target = resolver.lookStructureTarget().orElseThrow();
            require(target.matches(handle) && resolver.structureMotion(target).orElseThrow().mass() == motion().mass(),
                    "Program query failed to resolve the independent whole physical body");
            require(resolver.structureMotion(new org.academy.api.common.ability.program.ProgramStructureReference(
                    target.reference(), UUID.randomUUID())).isEmpty(), "Program query accepted an old activation");
            evidence.append("program_whole_body_query=true\nprogram_stale_activation_rejected=true\n");
            initialCom = worldCom(motion()); initialAir = resources(player).getPlayerCurrMP(player.getUUID());
            itemStart = player.getEyePosition().add(player.getLookAngle().scale(1.5));
            spawnItem(itemStart);
            require(PneumaticGrasp.Server.start(player), "Real skill start was rejected");
            require(!PneumaticGrasp.Server.start(player), "Duplicate START created another real skill context");
            status("AUTO_HOLD", "Running the actual server skill path", false);
        } catch (RuntimeException | Error failure) {
            close(); throw failure;
        }
    }

    boolean advance() {
        if (manualWindow()) {
            manualTicks++;
            require(System.nanoTime() - manualStart < 300_000_000_000L,
                    "Actual client input did not complete within its separate 300-second window at " + phase
                            + " eye=" + camera.getEyePosition() + " look=" + camera.getLookAngle()
                            + " diagnostic=" + PneumaticGrasp.Server.diagnostic(camera) + " inputs=" + inputs);
        } else require(++ticks < 650, "Automatic grasp checks timed out at " + phase);
        age++;
        require(region.status() == StructurePhysicsRegion.Status.READY, "Grasp fixture region stopped: " + region.diagnostic());
        lastMotion = motion();
        switch (phase) {
            case ITEM -> {
                require(diagnostic(player).targetKind() == PneumaticGrasp.Server.TargetKind.ENTITY
                                && region.control(handle.reference()).isEmpty(),
                        "A farther native structure obscured the nearer ordinary item");
                if (age < 4) return false;
                require(item.position().distanceTo(itemStart) > 0.05, "Ordinary item lost its original movement path");
                PneumaticGrasp.Server.stop(player); PneumaticGrasp.Server.stop(player); assertStopped(player);
                item.discard(); item = null; aim(player);
                initialCom = worldCom(motion()); initialAir = resources(player).getPlayerCurrMP(player.getUUID());
                require(PneumaticGrasp.Server.start(player) && !PneumaticGrasp.Server.start(player),
                        "Repeat START did not preserve exactly one control context");
                evidence.append("grasp_nearer_ordinary_item_moved=true\ngrasp_duplicate_start_stop_idempotent=true\n");
                next(Phase.HOLD);
            }
            case HOLD -> {
                var diagnostic = diagnostic(player);
                require(handle.reference().equals(diagnostic.nativeRef()) && diagnostic.nativeControlActive(),
                        "Real skill did not acquire the independent structure");
                sawCpOccupation |= resources(player).getPlayerOccupiedCP(player.getUUID()) > 0;
                if (diagnostic.controlActiveTicks() < 20) return false;
                require(worldCom(motion()).distanceTo(initialCom) > 0.25, "Skill lease did not move the native body");
                require(sawCpOccupation && resources(player).getPlayerCurrMP(player.getUUID()) < initialAir,
                        "Real skill did not charge timed CP and compressed air");
                double expectedAir = 2 * Math.max(0, AeromanipConfig.skillFloat(player,
                        SkillNames.PNEUMATIC_GRASP, "compressedAirPerInterval", 2));
                require(Math.abs(initialAir - resources(player).getPlayerCurrMP(player.getUUID()) - expectedAir) < 0.01,
                        "Duplicate START changed the 10-tick skill air charge count");
                var point = handle.pose().localToWorldPoint(diagnostic.localAnchor());
                spawnFartherItem(point); next(Phase.FARTHER_ITEM);
            }
            case FARTHER_ITEM -> {
                var currentPoint = handle.pose().localToWorldPoint(diagnostic(player).localAnchor());
                require(host.noCollision(item) && item.position().distanceTo(itemStart) < 1.0E-6
                                && item.getBoundingBox().inflate(0.8).clip(player.getEyePosition(), currentPoint).isEmpty(),
                        "Farther-item fixture lost its collision-free rear position: item=" + item.position()
                                + " original=" + itemStart + " held_point=" + currentPoint);
                require(diagnostic(player).targetKind() == PneumaticGrasp.Server.TargetKind.STRUCTURE,
                        "A farther item displaced the nearer selected native structure");
                item.discard(); item = null;
                evidence.append("grasp_farther_item_host_collision_free=true\n");
                previousTarget = anchor().targetPosition();
                PneumaticGrasp.Server.adjustDistance(player, 1); next(Phase.SCROLL);
            }
            case SCROLL -> {
                require(Math.abs(diagnostic(player).holdDistance() - 3.5) < 1.0E-8
                                && anchor().targetPosition().distanceTo(previousTarget) > 0.8,
                        "Skill distance adjustment did not update the native anchor");
                if (age < 8) return false;
                if (!freeFallRoom()) {
                    require(age < 100, "Skill release fixture never cleared its platform"); return false;
                }
                PneumaticGrasp.Server.stop(player); assertStopped(player);
                before = motion(); beforeSteps = steps();
                evidence.append("grasp_server_real_skill_pd=true\ngrasp_server_cp_air_charged=true\ngrasp_server_distance_adjusted=true\n"
                        + "grasp_native_occludes_farther_item=true\ngrasp_duplicate_start_charge_count_preserved=true\n");
                next(Phase.RELEASE);
            }
            case RELEASE -> {
                assertGravity("Stopping the real skill retained a control force");
                evidence.append("grasp_server_release_gravity=true\n");
                var motion = motion();
                competingLease = region.acquireControl(handle.reference(), UUID.randomUUID(),
                        new StructurePhysicsControl.Anchor(motion.localCenterOfMass(), worldCom(motion), Vec3.ZERO,
                                motion.mass() * 60, motion.mass() * 16, motion.mass() * 40), 200).orElseThrow();
                aim(player); require(PneumaticGrasp.Server.start(player), "Competition skill start failed");
                next(Phase.OCCUPIED);
            }
            case OCCUPIED -> {
                require(PneumaticGrasp.Server.diagnostic(player).isEmpty()
                                && competingLease.state() == StructurePhysicsLease.State.ACTIVE && competingLease.control().isPresent(),
                        "Skill stole another owner's control or retained a rejected context");
                competingLease.close(); competingLease = null;
                evidence.append("grasp_server_competitor_preserved=true\n");
                aim(player); require(PneumaticGrasp.Server.start(player), "LOS fixture skill start failed"); next(Phase.LOS_ACQUIRE);
            }
            case LOS_ACQUIRE -> {
                require(diagnostic(player).nativeControlActive(), "LOS fixture never controlled the structure");
                var point = handle.pose().localToWorldPoint(diagnostic(player).localAnchor());
                obstruction = BlockPos.containing(player.getEyePosition().lerp(point, 0.5));
                require(host.getBlockState(obstruction).isAir() && host.getBlockEntity(obstruction) == null,
                        "LOS fixture refuses existing host content");
                require(host.setBlock(obstruction, Blocks.STONE.defaultBlockState(), FLAGS), "Cannot install LOS fixture");
                next(Phase.LOS_BLOCKED);
            }
            case LOS_BLOCKED -> {
                assertStopped(player); restoreObstruction();
                evidence.append("grasp_server_los_revokes=true\n");
                // Host edits occur in this LOWEST Post callback, after the native terrain snapshot.
                // Let one complete physics Post rebuild AIR before the next skill Pre acquisition.
                next(Phase.LOS_RESTORED);
            }
            case LOS_RESTORED -> {
                require(region.control(handle.reference()).isEmpty(), "Host restoration revived the LOS-revoked skill");
                aim(player); require(PneumaticGrasp.Server.start(player), "Range fixture skill start failed"); next(Phase.RANGE_ACQUIRE);
            }
            case RANGE_ACQUIRE -> {
                require(diagnostic(player).nativeControlActive(), "Range fixture never acquired control");
                player.snapTo(player.getX(), player.getY() + 40, player.getZ(), player.getYRot(), player.getXRot());
                next(Phase.RANGE_LOST);
            }
            case RANGE_LOST -> {
                assertStopped(player);
                evidence.append("grasp_server_range_revokes=true\n");
                aim(player); resources(player).setPlayerCurrMP(player.getUUID(), 0);
                require(PneumaticGrasp.Server.start(player), "Resource fixture skill start failed"); next(Phase.RESOURCE_EMPTY);
            }
            case RESOURCE_EMPTY -> {
                if (PneumaticGrasp.Server.diagnostic(player).isPresent()) {
                    require(age < 20, "Zero compressed air failed to stop the skill at its charge boundary"); return false;
                }
                assertStopped(player);
                evidence.append("grasp_server_insufficient_air_releases=true\n");
                npc = EntityTypes.ARMOR_STAND.create(host, EntitySpawnReason.COMMAND);
                require(npc != null, "Cannot create shared traction NPC");
                npc.setNoGravity(true); aim(npc);
                require(host.addFreshEntity(npc), "Cannot register shared traction NPC");
                var hit = StructureQueries.raycast(host, npc.getEyePosition(), npc.getEyePosition().add(npc.getLookAngle().scale(32))).orElseThrow();
                verifyNpcRejections(hit);
                var acquisition = StructureTraction.tryAcquire(npc, UUID.randomUUID(), hit,
                        new StructureTraction.Settings(32, 32, 120, 36, 1000, 3), (_, _) -> true);
                require(acquisition.status() == StructureTraction.Status.ACTIVE, "NPC shared acquisition failed: " + acquisition.status());
                npcSession = acquisition.session().orElseThrow(); initialCom = worldCom(motion());
                previousTarget = npcSession.worldPoint().add(0, 1.5, 0);
                require(npcSession.update(previousTarget, Vec3.ZERO) == StructureTraction.Status.ACTIVE, "NPC shared update failed");
                next(Phase.NPC);
            }
            case NPC -> {
                require(npcSession.update(previousTarget, Vec3.ZERO) == StructureTraction.Status.ACTIVE,
                        "NPC shared session failed before lease renewal");
                if (age < 25) return false;
                require(worldCom(motion()).distanceTo(initialCom) > 0.25, "NPC common API did not move the actual rigid body");
                npc.discard();
                require(npcSession.update(previousTarget, Vec3.ZERO) != StructureTraction.Status.ACTIVE
                                && region.control(handle.reference()).isEmpty(), "Dead NPC kept its shared controller");
                npcSession.close(); npcSession = null;
                evidence.append("grasp_npc_shared_api_native_motion=true\ngrasp_npc_removal_releases=true\n");
                next(Phase.SETTLE);
            }
            case SETTLE -> {
                if (!settled()) return false;
                if (camera == null) return finish();
                player.snapTo(fixtureEye.x + 6, fixtureEye.y - player.getEyeHeight(), fixtureEye.z, 90, 0);
                originalAirCapacity = AeromanipConfig.settings(camera).compressedAirCapacity;
                AeromanipConfig.settings(camera).compressedAirCapacity = 2048;
                evidence.append("grasp_client_fixture_air_capacity=2048\ngrasp_client_periodic_resource_refills=false\n");
                configure(camera); aim(camera); baseline = inputs; manualStart = System.nanoTime();
                next(Phase.TAP); status("DEFAULT_G_TAP", "Tap G once with the default press/release bindings", true);
            }
            case TAP -> {
                if (!readyInputs(false) || inputs.gPress() <= baseline.gPress() || inputs.gRelease() <= baseline.gRelease()) return false;
                next(Phase.TAP_SETTLE);
            }
            case TAP_SETTLE -> {
                if (age < 5) return false;
                assertStopped(camera);
                evidence.append("grasp_client_default_g_tap_observed=true\ngrasp_client_default_g_tap_no_leak=true\n"
                        + "grasp_client_default_g_tap_packet_arrival_inferred=false\n");
                aim(camera); // Prepare the next case after the initial window-focus mouse movement.
                baseline = inputs; next(Phase.CUSTOM_START);
                status("CUSTOM_G_START", "Test binding: tap G to start; scroll once; tap H to release (G press / H release)", true);
            }
            case CUSTOM_START -> {
                if (!readyInputs(true) || inputs.gPress() <= baseline.gPress()) return false;
                var current = PneumaticGrasp.Server.diagnostic(camera);
                if (loggedCustomPress != inputs.gPress()) {
                    loggedCustomPress = inputs.gPress();
                    AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_GRASP_ACTUAL_G press={} eye={} look={} diagnostic={}",
                            loggedCustomPress, camera.getEyePosition(), camera.getLookAngle(), current);
                }
                if (current.isEmpty() || !current.orElseThrow().nativeControlActive()) return false;
                require(handle.reference().equals(current.orElseThrow().nativeRef()),
                        "Actual G input acquired another structure: " + current);
                initialCom = worldCom(motion()); initialAir = resources(camera).getPlayerCurrMP(camera.getUUID());
                sawCpOccupation = false; next(Phase.CUSTOM_HOLD);
                status("CUSTOM_HOLD", "Keep the test control active while the native structure lifts", true);
            }
            case CUSTOM_HOLD -> {
                require(diagnostic(camera).nativeControlActive(), "Actual G input did not retain native control");
                sawCpOccupation |= resources(camera).getPlayerOccupiedCP(camera.getUUID()) > 0;
                if (diagnostic(camera).controlActiveTicks() < 20) return false;
                require(sawCpOccupation && resources(camera).getPlayerCurrMP(camera.getUUID()) < initialAir,
                        "Actual hold did not charge resources: cp=" + sawCpOccupation
                                + " air=" + resources(camera).getPlayerCurrMP(camera.getUUID()) + "/" + initialAir);
                // A valid hold may already be settled. Require motion after an actual target change.
                initialCom = worldCom(motion());
                previousTarget = anchor().targetPosition(); baseline = inputs; next(Phase.CUSTOM_SCROLL);
                status("CUSTOM_SCROLL", "Scroll one notch while G-started control remains active", true);
            }
            case CUSTOM_SCROLL -> {
                require(diagnostic(camera).nativeControlActive(), "Control ended before actual scroll");
                if (inputs.scrolls() <= baseline.scrolls()) return false;
                if (anchor().targetPosition().distanceTo(previousTarget) < 0.4) return false;
                if (worldCom(motion()).distanceTo(initialCom) <= 0.25) return false;
                if (!freeFallRoom()) return false;
                evidence.append("grasp_client_actual_scroll_events=").append(inputs.scrolls() - baseline.scrolls()).append('\n');
                baseline = inputs; next(Phase.CUSTOM_RELEASE);
                status("CUSTOM_H_RELEASE", "Tap H to release the test binding and observe free fall", true);
            }
            case CUSTOM_RELEASE -> {
                if (inputs.hRelease() <= baseline.hRelease()) return false;
                if (PneumaticGrasp.Server.diagnostic(camera).isPresent() || region.control(handle.reference()).isPresent()) return false;
                require(freeFallRoom(), "Actual release no longer has one tick of free-fall clearance: minY="
                        + minY.getAsDouble() + " floor=" + floorTop + " motion=" + motion());
                assertStopped(camera); before = motion(); beforeSteps = steps(); next(Phase.CUSTOM_FALL);
            }
            case CUSTOM_FALL -> {
                assertGravity("Actual H release retained a physics command");
                evidence.append("grasp_client_actual_window_input=true\ngrasp_client_probe_input_injection=false\n"
                        + "grasp_client_custom_binding=G_PRESS_H_RELEASE\ngrasp_client_default_g_continuous_hold_executed=false\n"
                        + "grasp_client_native_pd_motion=true\ngrasp_client_cp_air_charged=true\ngrasp_client_release_gravity=true\n");
                next(Phase.FINAL_SETTLE); status("RESTORE_BINDINGS", "Restoring original bindings and finishing the automatic checks", false);
                manualState = new ManualState("RESTORE_BINDINGS", "Restore original bindings", false, true);
            }
            case FINAL_SETTLE -> { if (settled()) return finish(); }
            case DONE -> { return true; }
        }
        return false;
    }

    private boolean readyInputs(boolean custom) {
        return inputs.ready() && camera.getUUID().equals(inputs.playerId()) && inputs.customBinding() == custom;
    }

    private void verifyNpcRejections(org.academy.api.common.structure.StructureHit hit) {
        var settings = new StructureTraction.Settings(32, 32, 120, 36, 1000, 3);
        require(StructureTraction.tryAcquire(npc, UUID.randomUUID(), hit, settings, (_, _) -> false).status()
                        == StructureTraction.Status.DENIED && region.control(handle.reference()).isEmpty(),
                "Denied NPC policy occupied the native control slot");
        require(StructureTraction.tryAcquire(npc, UUID.randomUUID(), hit,
                        new StructureTraction.Settings(32, 0.1, 120, 36, 1000, 3), (_, _) -> true).status()
                        == StructureTraction.Status.TOO_HEAVY && region.control(handle.reference()).isEmpty(),
                "NPC mass limit did not reject the real compound");
        var policyError = new IllegalStateException("isolated traction policy failure");
        boolean rejected = false;
        try { StructureTraction.tryAcquire(npc, UUID.randomUUID(), hit, settings, (_, _) -> { throw policyError; }); }
        catch (IllegalStateException failure) { require(failure == policyError, "Acquisition replaced the caller's policy failure"); rejected = true; }
        require(rejected && region.control(handle.reference()).isEmpty(), "Failed acquisition policy leaked a lease");
        var allowed = new boolean[]{true};
        var revoked = StructureTraction.tryAcquire(npc, UUID.randomUUID(), hit, settings, (_, _) -> allowed[0]).session().orElseThrow();
        allowed[0] = false;
        require(revoked.update(revoked.worldPoint(), Vec3.ZERO) == StructureTraction.Status.DENIED
                        && region.control(handle.reference()).isEmpty(), "Permission revocation retained an NPC lease");
        var throwsNow = new boolean[]{false};
        var exceptional = StructureTraction.tryAcquire(npc, UUID.randomUUID(), hit, settings, (_, _) -> {
            if (throwsNow[0]) throw policyError;
            return true;
        }).session().orElseThrow();
        throwsNow[0] = true; rejected = false;
        try { exceptional.update(exceptional.worldPoint(), Vec3.ZERO); }
        catch (IllegalStateException failure) { require(failure == policyError, "Update replaced the caller's policy failure"); rejected = true; }
        require(rejected && region.control(handle.reference()).isEmpty(), "Exceptional policy update leaked a lease");
        var successor = StructureTraction.tryAcquire(npc, UUID.randomUUID(), hit, settings, (_, _) -> true).session().orElseThrow();
        exceptional.close(); revoked.close();
        require(successor.status() == StructureTraction.Status.ACTIVE && region.control(handle.reference()).isPresent(),
                "Old policy-failed session closed its successor");
        var beyond = successor.worldPoint().add(region.bounds().getXsize() + 1, 0, 0);
        require(successor.update(beyond, Vec3.ZERO) == StructureTraction.Status.OUT_OF_BOUNDS
                        && region.control(handle.reference()).isEmpty(), "Out-of-region target was clamped or retained its lease");
        require(successor.update(successor.worldPoint(), Vec3.ZERO) == StructureTraction.Status.OUT_OF_BOUNDS,
                "Out-of-bounds terminal session automatically reacquired");
        successor.close();
        var callbackRemovesActor = new boolean[]{false};
        var removedByPolicy = StructureTraction.tryAcquire(npc, UUID.randomUUID(), hit, settings, (_, _) -> {
            if (callbackRemovesActor[0]) npc.discard();
            return true;
        }).session().orElseThrow();
        callbackRemovesActor[0] = true;
        require(removedByPolicy.update(removedByPolicy.worldPoint(), Vec3.ZERO) == StructureTraction.Status.NOT_AVAILABLE
                        && region.control(handle.reference()).isEmpty(), "Policy-side actor removal left a physics command");
        removedByPolicy.close();
        npc = EntityTypes.ARMOR_STAND.create(host, EntitySpawnReason.COMMAND);
        require(npc != null, "Cannot recreate NPC after policy-side removal");
        npc.setNoGravity(true); aim(npc); require(host.addFreshEntity(npc), "Cannot register replacement policy NPC");
        evidence.append("grasp_npc_permission_denied_and_revoked=true\ngrasp_npc_mass_budget_enforced=true\n"
                + "grasp_npc_policy_exception_preserved=true\ngrasp_npc_policy_failure_releases_exact_lease=true\n"
                + "grasp_npc_out_of_bounds_terminal=true\ngrasp_npc_policy_actor_removal_releases=true\n");
    }

    private void spawnItem(Vec3 point) {
        require(item == null, "Ordinary item fixture was not cleaned up");
        item = new ItemEntity(host, point.x, point.y, point.z, new ItemStack(Items.COBBLESTONE));
        item.setNoGravity(true); item.setPickUpDelay(32767); item.setDeltaMovement(Vec3.ZERO);
        require(host.addFreshEntity(item), "Could not register ordinary item fixture");
    }
    private void spawnFartherItem(Vec3 heldPoint) {
        require(item == null, "Farther item fixture was not cleaned up");
        var eye = player.getEyePosition();
        var direction = heldPoint.subtract(eye).normalize();
        var rayEnd = eye.add(direction.scale(16)); // Within even the unlevelled skill's range.
        // The view points downward: a large extension can put the item inside the host floor.
        // Choose the nearest genuinely empty rear position using the same inflated entity ray box.
        for (double gap = 1.5; gap <= 3; gap += 0.25) {
            var point = heldPoint.add(direction.scale(gap));
            var candidate = new ItemEntity(host, point.x, point.y, point.z, new ItemStack(Items.COBBLESTONE));
            candidate.setNoGravity(true); candidate.setPickUpDelay(32767); candidate.setDeltaMovement(Vec3.ZERO);
            var box = candidate.getBoundingBox().inflate(0.8);
            var hit = box.clip(eye, rayEnd);
            if (!host.noCollision(candidate) || hit.isEmpty()
                    || eye.distanceTo(hit.orElseThrow()) <= eye.distanceTo(heldPoint) + 0.3
                    || box.clip(eye, heldPoint).isPresent()) continue;
            item = candidate; itemStart = point;
            require(host.addFreshEntity(item), "Could not register collision-free farther item fixture");
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_GRASP_FARTHER_ITEM point={} held_point={} ray_hit={} host_collision=false",
                    point, heldPoint, hit.orElseThrow());
            return;
        }
        throw new IllegalStateException("No collision-free farther item position behind " + heldPoint + " from eye " + eye);
    }
    private void configure(ServerPlayer target) {
        var system = resources(target); var id = target.getUUID();
        system.setPlayerAbilityCategory(id, AbilityCategories.AEROMANIP.get()); system.setPlayerLevel(id, 5);
        system.setPlayerBaseMaxCP(id, 100_000); system.setPlayerAvailableCP(id, 100_000);
        target.setGameMode(GameType.SURVIVAL);
        system.setPlayerStatus(id, AbilityData.Status.NORMAL);
        for (var skill : new org.academy.api.common.ability.Skill[]{Skills.FLOW_SENSE.get(), Skills.PNEUMATIC_GRASP.get()}) {
            system.addPlayerSkill(target, skill.getKeyString());
            if (!skill.isEnabled(target)) system.toggleSkill(id, skill.getKeyString());
        }
        system.getAeromanipResourceManager().onPlayerLogin(target);
        system.setPlayerCurrMP(id, system.getAeromanipResourceManager().getCapacity(target));
        for (var sync : new net.minecraft.resources.Identifier[]{SyncTypes.ABILITY_CATEGORY, SyncTypes.SKILL_DATA, SyncTypes.CP_DATA})
            system.schedulePlayerSync(id, sync);
    }
    private void aim(net.minecraft.world.entity.Entity actor) {
        var point = handle.pose().localToWorldPoint(cellCenter);
        var eye = fixtureEye;
        var direction = point.subtract(eye).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
        actor.snapTo(eye.x, eye.y - actor.getEyeHeight(), eye.z, yaw, pitch);
        if (actor instanceof ServerPlayer target) {
            target.getAbilities().mayfly = true; target.getAbilities().flying = true; target.onUpdateAbilities();
            target.connection.teleport(actor.getX(), actor.getY(), actor.getZ(), yaw, pitch);
        }
        var rayHit = StructureQueries.raycast(host, actor.getEyePosition(),
                actor.getEyePosition().add(actor.getLookAngle().scale(32))).orElseThrow(() ->
                new IllegalStateException("Fixture aim misses the current real structure at " + phase));
        require(rayHit.block().structure().equals(handle.reference()), "Fixture aim selects another structure at " + phase);
    }
    private boolean settled() {
        var motion = motion();
        stable = Math.abs(minY.getAsDouble() - floorTop) < 0.15 && motion.linearVelocity().lengthSqr() < 0.01
                && motion.angularVelocity().lengthSqr() < 0.01 ? stable + 1 : 0;
        return stable >= 12;
    }
    private boolean freeFallRoom() {
        var motion = motion();
        // The fixture has a three-cell compound of diameter below four blocks.
        double sweep = Math.max(0, -motion.linearVelocity().y) * 0.05 + motion.angularVelocity().length() * 4 * 0.05;
        return minY.getAsDouble() > floorTop + Math.max(0.3, sweep + 0.1);
    }
    private void assertStopped(ServerPlayer target) {
        require(PneumaticGrasp.Server.diagnostic(target).isEmpty() && region.control(handle.reference()).isEmpty()
                        && !resources(target).getAeromanipResourceManager().isInUse(target),
                "Skill ended without releasing context, physics lease and compressed-air usage");
    }
    private void assertGravity(String message) {
        require(steps() == beforeSteps + 1, "Release check did not cover exactly one real native tick");
        var expected = before.linearVelocity().add(0, -GRAVITY_STEP, 0);
        require(expected.distanceTo(motion().linearVelocity()) < 1.0E-6,
                message + ": expected=" + expected + " actual=" + motion().linearVelocity());
    }
    private void restoreObstruction() {
        if (obstruction == null) return;
        require(host.getBlockState(obstruction).is(Blocks.STONE), "LOS fixture block changed unexpectedly");
        require(host.setBlock(obstruction, Blocks.AIR.defaultBlockState(), FLAGS), "Cannot restore LOS fixture AIR"); obstruction = null;
    }
    private void next(Phase value) {
        phase = value; age = 0; stable = 0;
        var current = motion();
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_GRASP_PHASE {} com={} linear={} angular={} min_y={}",
                phase, worldCom(current), current.linearVelocity(), current.angularVelocity(), minY.getAsDouble());
    }
    private void status(String stage, String instruction, boolean waiting) {
        manualState = new ManualState(stage, instruction, waiting, false);
        try { Files.writeString(directory.resolve("native-structure-grasp-status.txt"), "stage=" + stage + "\ninstruction=" + instruction + "\nmanual_timeout_seconds=300\n"); }
        catch (IOException failure) { throw new IllegalStateException("Cannot write isolated grasp status", failure); }
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_GRASP_STAGE {} {}", stage, instruction);
    }
    private boolean finish() {
        evidence.append("grasp_window_pose_writes=0\ngrasp_window_velocity_writes=0\ngrasp_manual_wait_ticks=").append(manualTicks).append('\n');
        close(); phase = Phase.DONE; return true;
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        manualState = new ManualState("RESTORE_BINDINGS", "Restore original bindings", false, true);
        PneumaticGrasp.Server.stop(player);
        if (camera != null) PneumaticGrasp.Server.stop(camera);
        if (originalAirCapacity != null) {
            AeromanipConfig.settings(camera).compressedAirCapacity = originalAirCapacity;
            resources(camera).getAeromanipResourceManager().onPlayerLogin(camera);
            originalAirCapacity = null;
        }
        if (competingLease != null) competingLease.close();
        if (npcSession != null) npcSession.close();
        if (npc != null && !npc.isRemoved()) npc.discard();
        if (item != null && !item.isRemoved()) item.discard();
        restoreObstruction();
        var server = host.getServer(); server.getConnection().getConnections().remove(connection);
        if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player);
        channel.finishAndReleaseAll();
        require(server.getPlayerList().getPlayer(player.getUUID()) != player && host.getEntity(player.getUUID()) != player
                        && !server.getConnection().getConnections().contains(connection) && !channel.isOpen(),
                "Skill fixture did not clean up its embedded player");
    }
    private StructurePhysicsControl.Anchor anchor() {
        var command = region.control(handle.reference()).orElseThrow();
        require(command instanceof StructurePhysicsControl.Anchor, "Skill did not use the shared PD anchor");
        return (StructurePhysicsControl.Anchor) command;
    }
    private PneumaticGrasp.Server.Diagnostic diagnostic(ServerPlayer target) {
        return PneumaticGrasp.Server.diagnostic(target).orElseThrow(() -> new IllegalStateException(
                "Skill context ended during fixture phase=" + phase + " age=" + age + " eye=" + target.getEyePosition()
                        + " item=" + (item == null ? "none" : item.position()) + " motion=" + motion()
                        + " command=" + region.control(handle.reference())));
    }
    private AbilitySystemServer resources(ServerPlayer target) { return AbilitySystemServer.getSystem(target); }
    private StructureMotionState motion() { return region.motion(handle.reference()).orElseThrow(); }
    private long steps() { return ((NativeStructurePhysics.Region) region).diagnostics().steps(); }
    private static Vec3 worldCom(StructureMotionState motion) { return motion.pose().localToWorldPoint(motion.localCenterOfMass()); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
