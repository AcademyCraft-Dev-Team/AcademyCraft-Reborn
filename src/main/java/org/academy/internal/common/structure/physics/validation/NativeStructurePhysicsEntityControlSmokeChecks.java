package org.academy.internal.common.structure.physics.validation;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePhysicsLease;
import org.academy.api.common.structure.StructurePhysicsRegion;
import org.academy.internal.common.structure.physics.NativeStructurePhysics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/** Real entities, world events and native ticks; embedded player channels are not TCP clients. */
final class NativeStructurePhysicsEntityControlSmokeChecks implements AutoCloseable {
    private enum Phase { LIFT, DEAD_STEP, FORCE, COAST, LAND, DONE }
    private static final double DT = 0.05;
    private static final double GRAVITY = 9.81;
    private final ServerLevel host;
    private final MinecraftServer server;
    private final StructurePhysicsRegion region;
    private final StructureHandle handle, companion;
    private final DoubleSupplier minY;
    private final double floorTop;
    private final Vec3 fixturePosition, localCom, initialCom, target;
    private final double mass;
    private final StringBuilder evidence;
    private final List<Entity> entities = new ArrayList<>();
    private final List<PlayerFixture> players = new ArrayList<>();
    private final StructurePhysicsControl.Anchor anchor;
    private StructurePhysicsLease lease, companionLease;
    private ArmorStand actor;
    private StructureMotionState before;
    private long beforeSteps;
    private Phase phase = Phase.LIFT;
    private int ticks, stableTicks;

    NativeStructurePhysicsEntityControlSmokeChecks(ServerLevel host, StructurePhysicsRegion region,
                                                   StructureHandle handle, StructureHandle companion,
                                                   DoubleSupplier minY, double floorTop, Vec3 fixturePosition,
                                                   ServerPlayer camera, StringBuilder evidence) {
        require(Boolean.getBoolean("academy.nativeStructurePhysicsSmoke"), "Entity control probe is opt-in only");
        this.host = host; server = host.getServer(); this.region = region;
        this.handle = handle; this.companion = companion; this.minY = minY;
        this.floorTop = floorTop; this.fixturePosition = fixturePosition; this.evidence = evidence;
        var motion = motion();
        localCom = motion.localCenterOfMass(); initialCom = worldCom(motion); target = initialCom.add(0, 2, 0);
        mass = motion.mass();
        anchor = new StructurePhysicsControl.Anchor(localCom, target, Vec3.ZERO, mass * 60, mass * 16, mass * 40);
        // A second member remains leased throughout every actor lifecycle mutation below.
        companionLease = NativeStructurePhysicsLeaseSmokeChecks.armAtCurrentCom(region, companion);
        try {
            verifyRejectedActorsAndUnboundLease();
            verifyNpcLifecycle();
            verifyPlayers();
            require(companionLease.state() == StructurePhysicsLease.State.ACTIVE && companionLease.control().isPresent(),
                    "Actor lifecycle events invalidated another member's unbound lease");
            if (camera != null) {
                var cameraLease = acquire(camera, zeroForce());
                cameraLease.close();
                evidence.append("entity_camera_registered_acquire_release=true\n");
            }
            companionLease.close();
            var companionMotion = region.motion(companion.reference()).orElseThrow();
            companionLease = region.acquireEntityControl(companion.reference(), npc(), UUID.randomUUID(),
                    new StructurePhysicsControl.Anchor(companionMotion.localCenterOfMass(), worldCom(companionMotion),
                            Vec3.ZERO, companionMotion.mass() * 60, companionMotion.mass() * 16, companionMotion.mass() * 40),
                    200).orElseThrow();
            actor = npc();
            lease = region.acquireEntityControl(handle.reference(), actor, UUID.randomUUID(), anchor, 8).orElseThrow();
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    boolean advance() {
        require(++ticks < 450, "Entity control probe timed out at " + phase);
        require(region.status() == StructurePhysicsRegion.Status.READY, "Actor invalidation paused its region");
        require(companionLease.state() == StructurePhysicsLease.State.ACTIVE && companionLease.control().isPresent(),
                "Actor invalidation affected another member's entity-bound lease");
        companionLease.renew(200);
        var motion = motion();
        require(Math.abs(motion.mass() - mass) < 1.0E-8 && minY.getAsDouble() >= floorTop - 0.15,
                "Entity control fixture changed geometry or crossed the platform");
        switch (phase) {
            case LIFT -> {
                require(lease.state() == StructurePhysicsLease.State.ACTIVE, "Live registered NPC lost its lease");
                if (ticks % 4 == 0) lease.renew(8);
                boolean converged = worldCom(motion).distanceTo(target.add(0, -GRAVITY / 60, 0)) < 0.06
                        && motion.linearVelocity().lengthSqr() < 0.0036;
                stableTicks = converged ? stableTicks + 1 : 0;
                if (stableTicks < 15) return false;
                require(ticks > 8 && worldCom(motion).y > initialCom.y + 1.5,
                        "Entity-bound PD did not lift through renewed native steps");
                // No lease write/read follows death before the next real solver tick.
                lease.setControl(force());
                actor.setHealth(0);
                before = motion; beforeSteps = steps();
                evidence.append("entity_npc_renewed_pd_lift=true\n");
                phase = Phase.DEAD_STEP;
            }
            case DEAD_STEP -> {
                require(steps() == beforeSteps + 1, "Dead controller changed the region's step schedule");
                assertVector(before.linearVelocity().add(0, -GRAVITY * DT, 0), motion.linearVelocity(),
                        "Dead actor's queued force reached the native solver");
                NativeStructurePhysicsLeaseSmokeChecks.assertInvalidated(lease);
                require(region.control(handle.reference()).isEmpty(), "Dead actor retained its command");
                actor.discard();
                actor = npc();
                lease = acquire(actor, force());
                before = motion; beforeSteps = steps();
                evidence.append("entity_dead_before_step_releases_gravity=true\nentity_invalidation_keeps_region_ready=true\n");
                phase = Phase.FORCE;
            }
            case FORCE -> {
                require(steps() == beforeSteps + 1, "Entity force missed its outer physics tick");
                assertVector(before.linearVelocity().add(2 * DT, 0, 0), motion.linearVelocity(),
                        "Entity-bound finite force did not integrate F*dt/m");
                require(lease.state() == StructurePhysicsLease.State.ACTIVE && lease.control().isEmpty(),
                        "Consuming the entity Force lost ownership or retained its command");
                before = motion; beforeSteps = steps();
                phase = Phase.COAST;
            }
            case COAST -> {
                require(steps() == beforeSteps + 1, "Entity Force coast missed its outer physics tick");
                assertVector(before.linearVelocity().add(0, -GRAVITY * DT, 0), motion.linearVelocity(),
                        "Consumed entity Force leaked into the following tick");
                actor.discard();
                NativeStructurePhysicsLeaseSmokeChecks.assertInvalidated(lease);
                evidence.append("entity_finite_force_integrated_once=true\nentity_empty_lease_removal_invalidated=true\n");
                stableTicks = 0; phase = Phase.LAND;
            }
            case LAND -> {
                boolean settled = Math.abs(minY.getAsDouble() - floorTop) < 0.12
                        && motion.linearVelocity().lengthSqr() < 0.0064 && motion.angularVelocity().lengthSqr() < 0.0064;
                stableTicks = settled ? stableTicks + 1 : 0;
                if (stableTicks < 15) return false;
                require(region.control(handle.reference()).isEmpty(), "Invalidated entity control reappeared while landing");
                evidence.append("entity_other_member_lease_preserved=true\nentity_window_pose_writes=0\nentity_window_velocity_writes=0\n");
                close(); phase = Phase.DONE;
                evidence.append("entity_fixtures_cleaned=true\n");
                return true;
            }
            case DONE -> { return true; }
        }
        return false;
    }

    private void verifyRejectedActorsAndUnboundLease() {
        var absent = EntityTypes.ARMOR_STAND.create(host, EntitySpawnReason.COMMAND);
        require(absent != null, "Cannot construct absent actor fixture");
        var foreign = EntityTypes.ARMOR_STAND.create(server.getLevel(Level.NETHER), EntitySpawnReason.COMMAND);
        require(foreign != null, "Cannot construct wrong-host fixture");
        var dead = npc(); dead.setHealth(0);
        rejectArgument(() -> region.acquireEntityControl(handle.reference(), absent, UUID.randomUUID(), zeroForce(), 8));
        rejectArgument(() -> region.acquireEntityControl(handle.reference(), foreign, UUID.randomUUID(), zeroForce(), 8));
        rejectArgument(() -> region.acquireEntityControl(handle.reference(), dead, UUID.randomUUID(), zeroForce(), 8));
        var unbound = region.acquireControl(handle.reference(), dead.getUUID(), zeroForce(), 8).orElseThrow();
        var command = unbound.control();
        dead.discard();
        require(unbound.state() == StructurePhysicsLease.State.ACTIVE && unbound.control().equals(command),
                "Entity removal revoked the existing actor-independent UUID lease");
        rejectArgument(() -> region.acquireEntityControl(handle.reference(), absent, UUID.randomUUID(), anchor, 8));
        require(unbound.state() == StructurePhysicsLease.State.ACTIVE && unbound.control().equals(command),
                "Rejected actor changed the existing owner or command");
        unbound.close();
        evidence.append("entity_absent_dead_wrong_host_rejected=true\nentity_invalid_acquire_preserves_slot=true\nentity_uuid_only_lease_unchanged=true\n");
    }

    private void verifyNpcLifecycle() {
        var first = npc();
        var old = acquire(first, zeroForce());
        var impostor = EntityTypes.ARMOR_STAND.create(host, EntitySpawnReason.COMMAND);
        require(impostor != null, "Cannot construct same-UUID actor fixture");
        impostor.setUUID(first.getUUID());
        rejectArgument(() -> region.acquireEntityControl(handle.reference(), impostor, old.controllerId(), zeroForce(), 8));
        require(old.state() == StructurePhysicsLease.State.ACTIVE, "Same-UUID impostor affected the registered actor");
        first.discard();
        NativeStructurePhysicsLeaseSmokeChecks.assertInvalidated(old);
        // Same-tick leave and return must not revive the old lease, even for the same instance.
        first.revive();
        require(host.addFreshEntity(first) && host.getEntity(first.getUUID()) == first, "Cannot re-add same-tick actor");
        NativeStructurePhysicsLeaseSmokeChecks.assertInvalidated(old);
        var returned = acquire(first, zeroForce());
        old.close();
        require(returned.state() == StructurePhysicsLease.State.ACTIVE, "Old close revoked returned actor's fresh lease");
        first.discard();
        NativeStructurePhysicsLeaseSmokeChecks.assertInvalidated(returned);
        entities.add(impostor); impostor.setNoGravity(true); impostor.setPos(fixturePosition);
        require(host.addFreshEntity(impostor), "Cannot register same-UUID replacement after original removal");
        var replacement = region.acquireEntityControl(handle.reference(), impostor, old.controllerId(), zeroForce(), 8).orElseThrow();
        old.close(); returned.close();
        require(replacement.state() == StructurePhysicsLease.State.ACTIVE && replacement.control().isPresent(),
                "Previous instance's lease affected the same-UUID replacement");
        replacement.close(); impostor.discard();

        var dying = npc();
        var deadWrite = acquire(dying, zeroForce());
        dying.setHealth(0);
        rejectState(() -> deadWrite.setControl(anchor));
        NativeStructurePhysicsLeaseSmokeChecks.assertInvalidated(deadWrite); dying.discard();
        dying = npc();
        var deadRenew = acquire(dying, zeroForce());
        dying.setHealth(0);
        rejectState(() -> deadRenew.renew(8));
        NativeStructurePhysicsLeaseSmokeChecks.assertInvalidated(deadRenew); dying.discard();
        evidence.append("entity_same_uuid_impostor_rejected=true\nentity_leave_immediate_invalidated=true\n"
                + "entity_same_tick_return_not_restored=true\nentity_same_uuid_replacement_isolated=true\n"
                + "entity_dead_set_rejected_immediately=true\nentity_dead_renew_rejected_immediately=true\n");
    }

    private void verifyPlayers() {
        var profile = new GameProfile(UUID.randomUUID(), "pd-absent");
        var absent = new ServerPlayer(server, host, profile, CommonListenerCookie.createInitial(profile, false).clientInformation());
        rejectArgument(() -> region.acquireEntityControl(handle.reference(), absent, UUID.randomUUID(), zeroForce(), 8));
        var fixture = player();
        var player = fixture.player();
        var bound = acquire(player, zeroForce());
        var destination = server.getLevel(Level.NETHER);
        require(destination != null, "Missing ordinary Nether for player transfer fixture");
        destination.getChunkAt(net.minecraft.core.BlockPos.containing(fixturePosition));
        require(player.teleport(new TeleportTransition(destination, fixturePosition, Vec3.ZERO, 0, 0,
                TeleportTransition.DO_NOTHING)) == player, "Player dimension transfer failed");
        NativeStructurePhysicsLeaseSmokeChecks.assertInvalidated(bound);
        var transferredPlayer = player;
        rejectArgument(() -> region.acquireEntityControl(handle.reference(), transferredPlayer, UUID.randomUUID(), zeroForce(), 8));
        require(player.teleport(new TeleportTransition(host, fixturePosition, Vec3.ZERO, 0, 0,
                TeleportTransition.DO_NOTHING)) == player, "Player return transfer failed");
        NativeStructurePhysicsLeaseSmokeChecks.assertInvalidated(bound);
        var returned = acquire(player, zeroForce());
        bound.close();
        require(returned.state() == StructurePhysicsLease.State.ACTIVE, "Old player lease revoked post-transfer acquisition");
        player.setHealth(0);
        rejectState(() -> returned.renew(8));
        NativeStructurePhysicsLeaseSmokeChecks.assertInvalidated(returned);
        cleanup(fixture);

        fixture = player(); player = fixture.player();
        var disconnected = acquire(player, zeroForce());
        player.disconnect();
        rejectState(() -> disconnected.setControl(zeroForce()));
        NativeStructurePhysicsLeaseSmokeChecks.assertInvalidated(disconnected);
        cleanup(fixture);

        fixture = player();
        var closedConnection = acquire(fixture.player(), zeroForce());
        fixture.connection().disconnect(Component.literal("Entity control fixture disconnect"));
        require(!fixture.player().connection.isAcceptingMessages(), "Embedded connection did not close");
        rejectState(() -> closedConnection.renew(8));
        NativeStructurePhysicsLeaseSmokeChecks.assertInvalidated(closedConnection);
        cleanup(fixture);

        fixture = player();
        var logout = acquire(fixture.player(), zeroForce());
        server.getPlayerList().remove(fixture.player());
        NativeStructurePhysicsLeaseSmokeChecks.assertInvalidated(logout);
        cleanup(fixture);
        evidence.append("entity_player_fixture=embedded_channel\nentity_player_actual_tcp_disconnect=false\n"
                + "entity_unregistered_player_rejected=true\nentity_registered_player_acquire=true\n"
                + "entity_player_dimension_roundtrip_invalidated=true\nentity_player_dead_renew_rejected=true\n"
                + "entity_player_disconnect_flag_set_rejected=true\nentity_player_closed_channel_renew_rejected=true\n"
                + "entity_player_logout_immediate_invalidated=true\n");
    }

    private ArmorStand npc() {
        var value = EntityTypes.ARMOR_STAND.create(host, EntitySpawnReason.COMMAND);
        require(value != null, "Cannot create NPC actor");
        entities.add(value); value.setNoGravity(true); value.setPos(fixturePosition);
        require(host.addFreshEntity(value) && host.getEntity(value.getUUID()) == value, "Cannot register NPC actor");
        return value;
    }

    private PlayerFixture player() {
        var profile = new GameProfile(UUID.randomUUID(), "pd-" + UUID.randomUUID().toString().substring(0, 8));
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(server, host, profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var fixture = new PlayerFixture(player, connection, new EmbeddedChannel(connection));
        players.add(fixture); server.getConnection().getConnections().add(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.markClientLoaded(); player.setGameMode(GameType.SPECTATOR);
        player.snapTo(fixturePosition.x, fixturePosition.y, fixturePosition.z, 0, 0); player.setNoGravity(true);
        require(player.level() == host && host.getEntity(player.getUUID()) == player
                        && server.getPlayerList().getPlayer(player.getUUID()) == player,
                "Embedded player was not registered in both actual indexes");
        return fixture;
    }

    private void cleanup(PlayerFixture fixture) {
        server.getConnection().getConnections().remove(fixture.connection());
        if (server.getPlayerList().getPlayer(fixture.player().getUUID()) == fixture.player()) server.getPlayerList().remove(fixture.player());
        fixture.channel().finishAndReleaseAll();
        require(server.getPlayerList().getPlayer(fixture.player().getUUID()) != fixture.player()
                        && fixture.player().level().getEntity(fixture.player().getUUID()) != fixture.player()
                        && !server.getConnection().getConnections().contains(fixture.connection()) && !fixture.channel().isOpen(),
                "Embedded player fixture retained a player index or open connection");
        players.remove(fixture);
    }

    @Override public void close() {
        if (lease != null) lease.close();
        if (companionLease != null) companionLease.close();
        while (!players.isEmpty()) cleanup(players.getFirst());
        for (var entity : entities) {
            if (!entity.isRemoved()) entity.discard();
            require(entity.isRemoved() && host.getEntity(entity.getUUID()) != entity,
                    "NPC fixture remained registered after cleanup");
        }
        entities.clear();
    }

    private record PlayerFixture(ServerPlayer player, Connection connection, EmbeddedChannel channel) {}
    private StructurePhysicsLease acquire(Entity entity, StructurePhysicsControl control) {
        return region.acquireEntityControl(handle.reference(), entity, UUID.randomUUID(), control, 200).orElseThrow();
    }
    private StructurePhysicsControl.Force zeroForce() { return new StructurePhysicsControl.Force(localCom, Vec3.ZERO); }
    private StructurePhysicsControl.Force force() { return new StructurePhysicsControl.Force(localCom, new Vec3(mass * 2, mass * GRAVITY, 0)); }
    private StructureMotionState motion() { return region.motion(handle.reference()).orElseThrow(); }
    private long steps() { return ((NativeStructurePhysics.Region) region).diagnostics().steps(); }
    private static Vec3 worldCom(StructureMotionState motion) { return motion.pose().localToWorldPoint(motion.localCenterOfMass()); }
    private static void rejectState(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalStateException expected) { rejected = true; }
        require(rejected, "Unavailable entity lease accepted mutation");
    }
    private static void rejectArgument(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "Unavailable entity was accepted as controller");
    }
    private static void assertVector(Vec3 expected, Vec3 actual, String message) {
        require(expected.distanceTo(actual) < 1.0E-6, message + ": expected=" + expected + ", actual=" + actual);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
