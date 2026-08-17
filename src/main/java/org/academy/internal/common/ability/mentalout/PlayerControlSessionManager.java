package org.academy.internal.common.ability.mentalout;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.entitycontrol.*;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.common.ability.SkillProficiencyProfile;
import org.academy.internal.client.ability.mentalout.PlayerControlClientState;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;
import org.slf4j.Logger;

import java.util.*;

public final class PlayerControlSessionManager {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    public static final int DIRECT_CONTROL_PRIORITY = 300;
    private static final int READY_TIMEOUT_TICKS = 20;
    private static final int NEUTRAL_AFTER_TICKS = 5;
    private static final int CLIENT_TIMEOUT_TICKS = 20;
    private static final int STRUGGLE_MAX = 100;
    private static final int STRUGGLE_DECAY_DELAY = 10;
    private static final StreamCodec<ByteBuf, Tag> ITEM_STACK_TAG_CODEC = ByteBufCodecs.tagCodec(
            () -> NbtAccounter.create(1024L * 1024L));
    private static final Map<UUID, Session> BY_CONTROLLER = new HashMap<>();
    private static final Map<UUID, Session> BY_SUBJECT = new HashMap<>();
    private static final Map<UUID, MobSession> MOB_BY_CONTROLLER = new HashMap<>();
    private static final Map<UUID, MobSession> MOB_BY_SUBJECT = new HashMap<>();
    private static final Map<UUID, Long> RESISTANCE_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> REVISIONS = new HashMap<>();
    private static final Map<UUID, Anchor> FREEZE_ANCHORS = new HashMap<>();
    private static final Map<UUID, EndReason> CLOSED_PATH_REASONS = new HashMap<>();
    private static boolean clientInitialized;
    private static boolean serverInitialized;

    private PlayerControlSessionManager() {
    }

    public static synchronized void initClient() {
        if (clientInitialized) return;
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    public static synchronized void initServer() {
        if (serverInitialized) return;
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static StartResult toggle(ServerPlayer controller) {
        var current = BY_CONTROLLER.get(controller.getUUID());
        if (current != null) {
            stop(current, EndReason.CONTROLLER_STOPPED, true);
            return StartResult.STOPPED;
        }
        var currentMob = MOB_BY_CONTROLLER.get(controller.getUUID());
        if (currentMob != null) {
            stop(currentMob, EndReason.CONTROLLER_STOPPED, true);
            return StartResult.STOPPED;
        }
        var skill = Skills.MENTAL_TAKEOVER.get();
        if (!MentaloutConfig.allowMentalTakeover(controller) || !skill.isEnabled(controller)) {
            return StartResult.UNAVAILABLE;
        }
        if (BY_SUBJECT.containsKey(controller.getUUID())
                || MOB_BY_SUBJECT.containsKey(controller.getUUID())) return StartResult.CONTROL_CYCLE;
        var target = MentalIntrusionManager.target(controller);
        var roster = MentaloutControlContext.get(controller);
        if (target == null || target == controller
                || roster == null || !roster.contains(target.getUUID())) {
            return StartResult.NOT_IN_ROSTER;
        }
        if (!(target instanceof ServerPlayer subject)) {
            return target instanceof Mob mob
                    ? startMob(controller, mob, roster)
                    : StartResult.INVALID_TARGET;
        }
        if (subject.isSpectator() || !subject.isAlive() || subject.hasDisconnected()
                || subject.level() != controller.level()
                || FriendlyFireSetting.shouldPrevent(controller, subject)) {
            return StartResult.INVALID_TARGET;
        }
        if (MentalControlRuntime.isProtectedTarget(subject)) {
            MentalControlRuntime.notifyProtectionBlocked(controller, subject);
            return StartResult.PROTECTED;
        }
        if (isResistant(subject)) return StartResult.RESISTANT;
        var subjectSession = BY_SUBJECT.get(subject.getUUID());
        if (subjectSession != null && subjectSession.kind == Kind.DIRECT
                || BY_CONTROLLER.containsKey(subject.getUUID())) {
            return StartResult.CONTROL_CYCLE;
        }
        if (subjectSession != null) stop(subjectSession, EndReason.LIFECYCLE, true, false);
        var system = AbilitySystemServer.getSystem(controller);
        if (!system.replacePermanentOccupation(
                controller.getUUID(), skill.adjustProficiencyCost(
                        controller, SkillProficiencyProfile.CostKind.MAINTENANCE,
                        MentaloutConfig.mentalTakeoverOccupation(controller)), skill)) {
            return StartResult.INSUFFICIENT_CP;
        }

        ControlHandle handle;
        try {
            handle = MentalControlApi.apply(ControlRequest.permanent(
                    controller,
                    subject,
                    skill.getKey(),
                    DIRECT_CONTROL_PRIORITY,
                    List.of(new ControlDirective.DirectControl())
            ));
        } catch (RuntimeException exception) {
            system.releaseMaintenanceOccupation(controller.getUUID(), skill.getKeyString());
            if (MentalControlRuntime.isProtectedTarget(subject)) {
                return StartResult.PROTECTED;
            }
            return StartResult.INVALID_TARGET;
        }

        var now = controller.level().getGameTime();
        var revision = nextSessionRevision(controller.getUUID(), subject.getUUID());
        var session = new Session(
                UUID.randomUUID(), revision, Kind.DIRECT, controller, subject, handle,
                now + READY_TIMEOUT_TICKS, now,
                new Anchor(controller.position(), controller.getYRot(), controller.getXRot()),
                subject.position()
        );
        BY_CONTROLLER.put(controller.getUUID(), session);
        BY_SUBJECT.put(subject.getUUID(), session);
        sendBegin(session, controller, Role.CONTROLLER);
        sendBegin(session, subject, Role.SUBJECT);
        return StartResult.STARTED;
    }

    private static StartResult startMob(
            ServerPlayer controller,
            Mob subject,
            MentaloutControlContext roster
    ) {
        if (!roster.contains(subject.getUUID()) || !subject.isAlive() || subject.isRemoved()
                || subject.level() != controller.level()
                || FriendlyFireSetting.shouldPrevent(controller, subject)) {
            return StartResult.INVALID_TARGET;
        }
        if (MentalControlRuntime.isProtectedTarget(subject)) {
            MentalControlRuntime.notifyProtectionBlocked(controller, subject);
            return StartResult.PROTECTED;
        }
        if (MOB_BY_SUBJECT.containsKey(subject.getUUID())) return StartResult.CONTROL_CYCLE;
        var skill = Skills.MENTAL_TAKEOVER.get();
        var system = AbilitySystemServer.getSystem(controller);
        if (!system.replacePermanentOccupation(
                controller.getUUID(), skill.adjustProficiencyCost(
                        controller, SkillProficiencyProfile.CostKind.MAINTENANCE,
                        MentaloutConfig.mentalTakeoverOccupation(controller)), skill)) {
            return StartResult.INSUFFICIENT_CP;
        }
        ControlHandle handle;
        try {
            handle = MentalControlApi.apply(ControlRequest.permanent(
                    controller,
                    subject,
                    skill.getKey(),
                    DIRECT_CONTROL_PRIORITY,
                    List.of(new ControlDirective.DirectControl())
            ));
        } catch (RuntimeException exception) {
            system.releaseMaintenanceOccupation(controller.getUUID(), skill.getKeyString());
            if (MentalControlRuntime.isProtectedTarget(subject)) return StartResult.PROTECTED;
            return StartResult.INVALID_TARGET;
        }
        var now = controller.level().getGameTime();
        var session = new MobSession(
                UUID.randomUUID(),
                nextSessionRevision(controller.getUUID(), subject.getUUID()),
                controller,
                subject,
                handle,
                now + READY_TIMEOUT_TICKS,
                now,
                new Anchor(controller.position(), controller.getYRot(), controller.getXRot())
        );
        MOB_BY_CONTROLLER.put(controller.getUUID(), session);
        MOB_BY_SUBJECT.put(subject.getUUID(), session);
        sendBegin(session, controller);
        return StartResult.STARTED;
    }

    public static PathSessionToken beginPath(ControlContext context, ServerPlayer subject) {
        if (isResistant(subject)) {
            throw new IllegalStateException("Player input control resistance is active");
        }
        var existing = BY_SUBJECT.get(subject.getUUID());
        if (existing != null) {
            if (existing.kind == Kind.DIRECT) {
                throw new IllegalStateException("Direct control already owns player input");
            }
            stop(existing, EndReason.LIFECYCLE, true, false);
        }
        var now = subject.level().getGameTime();
        var revision = nextSessionRevision(context.controller().getUUID(), subject.getUUID());
        var session = new Session(
                UUID.randomUUID(), revision, Kind.PATH, context.controller(), subject, null,
                now + READY_TIMEOUT_TICKS, now, null, subject.position()
        );
        BY_SUBJECT.put(subject.getUUID(), session);
        sendBegin(session, subject, context.controller() == subject ? Role.SELF : Role.SUBJECT);
        return new PathSessionToken(session.id, session.revision, subject.getUUID());
    }

    public static boolean isPathActive(PathSessionToken token) {
        var session = pathSession(token);
        return session != null && session.state == State.ACTIVE;
    }

    public static boolean isPathHandshakePending(PathSessionToken token) {
        var session = pathSession(token);
        return session != null && session.state == State.HANDSHAKE;
    }

    public static Optional<PlayerMovementMode> pathAppliedMode(PathSessionToken token) {
        var session = pathSession(token);
        if (session == null || session.acknowledgedFrames.isEmpty()) return Optional.empty();
        return Optional.of(session.acknowledgedFrames.getLast().frame.mode());
    }

    public static void submitPathFrame(PathSessionToken token, PlayerControlFrame frame) {
        var session = pathSession(token);
        if (session == null || session.state != State.ACTIVE) return;
        authorize(session, normalizePathFrame(frame), ++session.authorizedSequence);
    }

    public static void closePath(PathSessionToken token, boolean applyResistance) {
        var session = pathSession(token);
        if (session != null) stop(session, EndReason.LIFECYCLE, true, applyResistance);
        if (token != null) CLOSED_PATH_REASONS.remove(token.sessionId);
    }

    public static Optional<EndReason> consumePathEndReason(PathSessionToken token) {
        return token == null ? Optional.empty()
                : Optional.ofNullable(CLOSED_PATH_REASONS.remove(token.sessionId));
    }

    public static void tick(MinecraftServer server) {
        var now = server.overworld().getGameTime();
        RESISTANCE_UNTIL.clear();
        FREEZE_ANCHORS.entrySet().removeIf(entry -> {
            var player = server.getPlayerList().getPlayer(entry.getKey());
            return player == null || !MentalControlRuntime.isFrozen(player);
        });
        for (var session : List.copyOf(new HashSet<>(BY_SUBJECT.values()))) {
            if (!isRetained(session)) {
                if (MentalControlRuntime.isProtectedTarget(session.subject)) {
                    MentalControlRuntime.notifyProtectionBlocked(session.controller, session.subject);
                }
                stop(session, MentalControlRuntime.isProtectedTarget(session.subject)
                        ? EndReason.PROTECTED : EndReason.LIFECYCLE, true);
                continue;
            }
            if (session.state == State.HANDSHAKE) {
                if (now >= session.readyDeadline) stop(session, EndReason.CLIENT_TIMEOUT, true);
                continue;
            }
            if (shouldEndForMissingAppliedFrame(
                    session.kind == Kind.PATH, now, session.lastAppliedTick)) {
                stop(session, EndReason.CLIENT_TIMEOUT, true);
                continue;
            }
            if (session.kind == Kind.PATH) continue;
            Skills.MENTAL_TAKEOVER.get().reportActivity(session.controller, true);
            if (now - session.lastIntentTick >= NEUTRAL_AFTER_TICKS
                    && session.lastNeutralTick != now) {
                session.lastNeutralTick = now;
                authorize(session, PlayerControlFrame.NEUTRAL, ++session.authorizedSequence);
            }
            if (now - session.lastStruggleTick > STRUGGLE_DECAY_DELAY && session.struggle > 0) {
                session.struggle--;
            }
            if (session.struggle >= STRUGGLE_MAX) {
                stop(session, EndReason.STRUGGLE, true);
                continue;
            }
            if (now % 5L == 0L) sendStatus(session);
            if (now - session.lastViewSnapshotTick >= 2L) sendTargetViewState(session, now);
        }
        for (var session : List.copyOf(MOB_BY_SUBJECT.values())) {
            if (!isRetained(session)) {
                if (MentalControlRuntime.isProtectedTarget(session.subject)) {
                    MentalControlRuntime.notifyProtectionBlocked(session.controller, session.subject);
                }
                stop(session, MentalControlRuntime.isProtectedTarget(session.subject)
                        ? EndReason.PROTECTED : EndReason.LIFECYCLE, true);
                continue;
            }
            if (session.state == State.HANDSHAKE) {
                if (now >= session.readyDeadline) stop(session, EndReason.CLIENT_TIMEOUT, true);
                continue;
            }
            Skills.MENTAL_TAKEOVER.get().reportActivity(session.controller, true);
            if (now - session.lastIntentTick >= NEUTRAL_AFTER_TICKS) {
                session.frame = PlayerControlFrame.NEUTRAL;
            }
            if (now % 5L == 0L) sendStatus(session);
        }
    }

    public static void onControllerDamaged(ServerPlayer player, float healthDamage) {
        if (healthDamage <= 0.0f) return;
        var session = BY_CONTROLLER.get(player.getUUID());
        if (session != null && session.state == State.ACTIVE) {
            stop(session, EndReason.CONTROLLER_DAMAGED, true);
        }
        var mobSession = MOB_BY_CONTROLLER.get(player.getUUID());
        if (mobSession != null && mobSession.state == State.ACTIVE) {
            stop(mobSession, EndReason.CONTROLLER_DAMAGED, true);
        }
    }

    public static boolean isResistant(ServerPlayer subject) {
        return false;
    }

    public static boolean blocksUntrustedWorldAction(ServerPlayer player) {
        if (player == null) return false;
        var controlled = BY_SUBJECT.get(player.getUUID());
        if (controlled != null && controlled.state == State.ACTIVE) return true;
        var controlling = BY_CONTROLLER.get(player.getUUID());
        if (controlling != null && controlling.state == State.ACTIVE) return true;
        var controllingMob = MOB_BY_CONTROLLER.get(player.getUUID());
        return controllingMob != null && controllingMob.state == State.ACTIVE;
    }

    public static long resistanceUntil(ServerPlayer subject) {
        return 0L;
    }

    public static void grantResistance(ServerPlayer subject) {
        if (subject == null) return;
        RESISTANCE_UNTIL.remove(subject.getUUID());
        MentalControlRuntime.releasePlayerInputLeases(subject.level().getServer(), subject.getUUID());
    }

    public static void releaseEntity(UUID entityId) {
        var session = BY_CONTROLLER.get(entityId);
        if (session == null) session = BY_SUBJECT.get(entityId);
        if (session != null) stop(session, EndReason.LIFECYCLE, true);
        var mobSession = MOB_BY_CONTROLLER.get(entityId);
        if (mobSession == null) mobSession = MOB_BY_SUBJECT.get(entityId);
        if (mobSession != null) stop(mobSession, EndReason.LIFECYCLE, true);
        FREEZE_ANCHORS.remove(entityId);
    }

    public static void clear() {
        List.copyOf(new HashSet<>(BY_SUBJECT.values())).forEach(
                session -> stop(session, EndReason.LIFECYCLE, false));
        BY_CONTROLLER.clear();
        BY_SUBJECT.clear();
        List.copyOf(MOB_BY_SUBJECT.values()).forEach(
                session -> stop(session, EndReason.LIFECYCLE, false));
        MOB_BY_CONTROLLER.clear();
        MOB_BY_SUBJECT.clear();
        RESISTANCE_UNTIL.clear();
        REVISIONS.clear();
        FREEZE_ANCHORS.clear();
        CLOSED_PATH_REASONS.clear();
    }

    /**
     * Returns true when the packet was corrected and vanilla handling must be cancelled.
     */
    public static boolean validateMovePlayer(ServerPlayer player, ServerboundMovePlayerPacket packet) {
        var controlled = BY_SUBJECT.get(player.getUUID());
        if (controlled != null && controlled.state == State.ACTIVE) {
            return validateControlledMovement(controlled, packet);
        }
        var controlling = BY_CONTROLLER.get(player.getUUID());
        if (controlling != null && controlling.state != State.CLOSED) {
            correct(player, controlling.controllerAnchor, packet.getYRot(player.getYRot()),
                    packet.getXRot(player.getXRot()));
            return true;
        }
        var controllingMob = MOB_BY_CONTROLLER.get(player.getUUID());
        if (controllingMob != null && controllingMob.state != State.CLOSED) {
            correct(player, controllingMob.controllerAnchor,
                    packet.getYRot(player.getYRot()), packet.getXRot(player.getXRot()));
            return true;
        }
        if (!MentalControlRuntime.isFrozen(player)) {
            FREEZE_ANCHORS.remove(player.getUUID());
            return false;
        }
        var anchor = FREEZE_ANCHORS.computeIfAbsent(player.getUUID(), _ ->
                new Anchor(player.position(), player.getYRot(), player.getXRot()));
        correct(player, anchor, packet.getYRot(player.getYRot()), packet.getXRot(player.getXRot()));
        return true;
    }

    private static boolean validateControlledMovement(Session session, ServerboundMovePlayerPacket packet) {
        var player = session.subject;
        var yaw = packet.getYRot(player.getYRot());
        var pitch = packet.getXRot(player.getXRot());
        var next = player.position();
        var deltaY = 0.0;
        var positionValid = true;
        if (packet.hasPosition()) {
            next = new Vec3(packet.getX(player.getX()), packet.getY(player.getY()), packet.getZ(player.getZ()));
            var delta = next.subtract(session.lastGoodPosition);
            deltaY = delta.y;
            var horizontal = delta.x * delta.x + delta.z * delta.z;
            var maxHorizontal = player.isPassenger() ? 16.0 : 2.25;
            positionValid = Double.isFinite(next.x) && Double.isFinite(next.y) && Double.isFinite(next.z)
                    && horizontal <= maxHorizontal;
        }
        var verticalDelta = deltaY;
        var candidateFrames = session.acknowledgedFrames.isEmpty()
                ? List.of(new PlayerControlFrame(
                0.0f, 0.0f, player.getYRot(), player.getXRot(),
                false, false, false, false, false, PlayerMovementMode.WALK
        ))
                : session.acknowledgedFrames.stream().map(AcknowledgedFrame::frame).toList();
        var valid = positionValid && Float.isFinite(yaw) && Float.isFinite(pitch)
                && candidateFrames.stream().anyMatch(frame ->
                Math.abs(Mth.wrapDegrees(yaw - frame.yaw())) <= 50.0f
                        && Math.abs(pitch - frame.pitch()) <= 50.0f
                        && validVertical(frame, verticalDelta));
        if (valid) {
            session.lastGoodPosition = next;
            session.invalidMoves = 0;
            return false;
        }
        session.invalidMoves++;
        var frame = session.acknowledgedFrames.isEmpty()
                ? PlayerControlFrame.NEUTRAL : session.acknowledgedFrames.getLast().frame;
        correct(player, new Anchor(session.lastGoodPosition, frame.yaw(), frame.pitch()),
                frame.yaw(), frame.pitch());
        if (session.invalidMoves >= 3) stop(session, EndReason.ILLEGAL_MOVEMENT, true);
        return true;
    }

    static boolean validVertical(PlayerControlFrame frame, double deltaY) {
        if (!Double.isFinite(deltaY) || deltaY > 2.5) return false;
        if (deltaY <= 0.7) return true;
        return frame.jump() || switch (frame.mode()) {
            case CLIMB, SWIM, FLY, GLIDE, BOAT, MOUNT, RAIL -> true;
            default -> false;
        };
    }

    static boolean shouldEndForMissingAppliedFrame(
            boolean pathSession,
            long currentTick,
            long lastAppliedTick
    ) {
        return pathSession && currentTick - lastAppliedTick >= CLIENT_TIMEOUT_TICKS;
    }

    private static void correct(ServerPlayer player, Anchor anchor, float yaw, float pitch) {
        var safeYaw = Float.isFinite(yaw) ? yaw : anchor.yaw;
        var safePitch = Float.isFinite(pitch) ? pitch : anchor.pitch;
        EntityMotionGuard.runInternalCorrection(player, () -> {
            player.setDeltaMovement(Vec3.ZERO);
            player.connection.teleport(anchor.position.x, anchor.position.y, anchor.position.z,
                    safeYaw, safePitch);
        });
        player.hurtMarked = true;
    }

    private static boolean isRetained(Session session) {
        var controller = session.controller;
        var subject = session.subject;
        var maxDistance = Skills.MENTAL_INTRUSION.get().hasProficiencyMilestone(controller, 2)
                ? Math.max(128.0, MentaloutConfig.intrusionMaximumDistance(controller))
                : MentaloutConfig.intrusionMaximumDistance(controller);
        return controller.isAlive() && !controller.hasDisconnected()
                && subject.isAlive() && !subject.hasDisconnected() && !subject.isSpectator()
                && controller.level() == subject.level()
                && (session.kind == Kind.PATH || Skills.MENTAL_TAKEOVER.get().isEnabled(controller))
                && (session.handle == null || !session.handle.isClosed())
                && (controller == subject || !MentalControlRuntime.isProtectedTarget(subject))
                && (session.kind == Kind.PATH
                || controller.distanceToSqr(subject) <= maxDistance * maxDistance);
    }

    private static boolean isRetained(MobSession session) {
        var controller = session.controller;
        var subject = session.subject;
        var maxDistance = Skills.MENTAL_INTRUSION.get().hasProficiencyMilestone(controller, 2)
                ? Math.max(128.0, MentaloutConfig.intrusionMaximumDistance(controller))
                : MentaloutConfig.intrusionMaximumDistance(controller);
        return controller.isAlive() && !controller.hasDisconnected()
                && subject.isAlive() && !subject.isRemoved()
                && controller.level() == subject.level()
                && Skills.MENTAL_TAKEOVER.get().isEnabled(controller)
                && !session.handle.isClosed()
                && !MentalControlRuntime.isProtectedTarget(subject)
                && controller.distanceToSqr(subject) <= maxDistance * maxDistance;
    }

    private static void ready(ServerPlayer sender, ReadyPacket packet) {
        var session = findSession(sender);
        if (session == null) {
            ready(sender, packet, MOB_BY_CONTROLLER.get(sender.getUUID()));
            return;
        }
        if (!matches(session, packet.sessionId, packet.revision) || session.state != State.HANDSHAKE) return;
        if (!packet.ready) {
            stop(session, EndReason.CLIENT_REJECTED, true);
            return;
        }
        if (sender == session.controller) session.controllerReady = true;
        if (sender == session.subject) session.subjectReady = true;
        if ((session.kind == Kind.DIRECT && !session.controllerReady) || !session.subjectReady) return;
        session.state = State.ACTIVE;
        session.lastIntentTick = sender.level().getGameTime();
        session.lastAppliedTick = session.lastIntentTick;
        authorize(session, PlayerControlFrame.NEUTRAL, ++session.authorizedSequence);
        if (session.kind == Kind.DIRECT) {
            sendStatus(session);
            sendTargetViewState(session, session.lastIntentTick);
        }
    }

    private static void ready(ServerPlayer sender, ReadyPacket packet, MobSession session) {
        if (!matches(session, packet.sessionId, packet.revision)
                || session.state != State.HANDSHAKE || sender != session.controller) return;
        if (!packet.ready) {
            stop(session, EndReason.CLIENT_REJECTED, true);
            return;
        }
        session.state = State.ACTIVE;
        session.lastIntentTick = sender.level().getGameTime();
        sendStatus(session);
    }

    private static void intent(ServerPlayer sender, IntentPacket packet) {
        var session = BY_CONTROLLER.get(sender.getUUID());
        if (session == null) {
            intent(sender, packet, MOB_BY_CONTROLLER.get(sender.getUUID()));
            return;
        }
        if (!matches(session, packet.sessionId, packet.revision) || session.state != State.ACTIVE
                || packet.sequence <= session.lastControllerSequence) return;
        var now = sender.level().getGameTime();
        if (session.lastIntentAcceptedTick == now) return;
        session.lastControllerSequence = packet.sequence;
        session.lastIntentAcceptedTick = now;
        session.lastIntentTick = now;
        var frame = normalizeDirectFrame(session.subject, packet.frame);
        authorize(session, frame, ++session.authorizedSequence);
        if (frame.attack()) attack(session.subject, frame);
        if (frame.use()) useCurrentItem(session.subject, frame);
    }

    private static void intent(ServerPlayer sender, IntentPacket packet, MobSession session) {
        if (!matches(session, packet.sessionId, packet.revision)
                || session.state != State.ACTIVE
                || packet.sequence <= session.lastControllerSequence) return;
        var now = sender.level().getGameTime();
        if (session.lastIntentAcceptedTick == now) return;
        session.lastControllerSequence = packet.sequence;
        session.lastIntentAcceptedTick = now;
        session.lastIntentTick = now;
        session.frameSequence++;
        session.frame = normalizeMobFrame(packet.frame);
    }

    private static void inventoryAction(ServerPlayer sender, InventoryActionPacket packet) {
        var session = BY_CONTROLLER.get(sender.getUUID());
        if (!matches(session, packet.sessionId, packet.revision)
                || session.state != State.ACTIVE || session.kind != Kind.DIRECT
                || packet.sequence <= session.lastInventoryActionSequence
                || !Skills.MENTAL_TAKEOVER.get().hasProficiencyMilestone(sender, 3)
                || !ProficiencyPolicy.server(sender).allowMentalTakeoverExtendedControls()) {
            return;
        }
        session.lastInventoryActionSequence = packet.sequence;
        var now = sender.level().getGameTime();
        switch (packet.action) {
            case SELECT_HOTBAR -> {
                if (packet.value < 0 || packet.value > 8
                        || session.lastHotbarSwitchTick != Long.MIN_VALUE
                        && now - session.lastHotbarSwitchTick < 20L) {
                    return;
                }
                session.lastHotbarSwitchTick = now;
                var inventory = session.subject.getInventory();
                inventory.setSelectedSlot(packet.value);
                session.subject.connection.send(new ClientboundSetHeldSlotPacket(packet.value));
                session.subject.inventoryMenu.broadcastChanges();
                sendTargetViewState(session, now);
            }
            case USE_OFFHAND -> {
                if (session.lastOffhandUseTick == now) return;
                session.lastOffhandUseTick = now;
                useCurrentItem(session.subject, session.authorizedFrame, InteractionHand.OFF_HAND);
            }
        }
    }

    private static PlayerControlFrame normalizeMobFrame(PlayerControlFrame frame) {
        return new PlayerControlFrame(
                frame.forward(), frame.strafe(), Mth.wrapDegrees(frame.yaw()), frame.pitch(),
                frame.jump(), frame.sneak(), frame.sprint(), frame.attack(), frame.use(),
                PlayerMovementMode.WALK
        );
    }

    public static Optional<MobDirectInput> mobDirectInput(Mob subject) {
        if (subject == null) return Optional.empty();
        var session = MOB_BY_SUBJECT.get(subject.getUUID());
        if (session == null || session.state != State.ACTIVE) return Optional.empty();
        return Optional.of(new MobDirectInput(session.frameSequence, session.frame));
    }

    private static void applied(ServerPlayer sender, AppliedFramePacket packet) {
        var session = BY_SUBJECT.get(sender.getUUID());
        if (!matches(session, packet.sessionId, packet.revision) || session.state != State.ACTIVE
                || packet.sequence < session.lastAppliedSequence) return;
        var authorized = session.authorizedFrames.stream()
                .filter(frame -> frame.sequence == packet.sequence)
                .findFirst().orElse(null);
        if (authorized == null) return;
        var now = sender.level().getGameTime();
        session.lastAppliedTick = now;
        if (packet.sequence == session.lastAppliedSequence) return;
        session.lastAppliedSequence = packet.sequence;
        session.acknowledgedFrames.addLast(new AcknowledgedFrame(
                authorized.sequence, authorized.frame, now
        ));
        while (session.acknowledgedFrames.size() > 3) session.acknowledgedFrames.removeFirst();
    }

    private static void struggle(ServerPlayer sender, StrugglePacket packet) {
        var session = BY_SUBJECT.get(sender.getUUID());
        if (!matches(session, packet.sessionId, packet.revision) || session.state != State.ACTIVE
                || packet.sequence <= session.lastSubjectSequence) return;
        var now = sender.level().getGameTime();
        if (session.lastStruggleAcceptedTick == now) return;
        session.lastSubjectSequence = packet.sequence;
        session.lastStruggleAcceptedTick = now;
        var direction = packet.directionMask & 0xF;
        if (session.controller == session.subject && hasSelfOverrideInput(direction, packet.edgeMask)) {
            stop(session, EndReason.CONTROLLER_STOPPED, true, false);
            return;
        }
        var points = strugglePoints(session.lastDirectionMask, direction, packet.edgeMask);
        session.lastDirectionMask = direction;
        if (points > 0) {
            if (Skills.MENTAL_TAKEOVER.get().hasProficiencyMilestone(session.controller, 2)) {
                var scaled = points * 0.75f + session.struggleRemainder;
                points = (int) Math.floor(scaled);
                session.struggleRemainder = scaled - points;
            }
            session.struggle = Math.min(STRUGGLE_MAX, session.struggle + Math.min(2, points));
            session.lastStruggleTick = now;
            sendStatus(session);
        }
    }

    static int strugglePoints(int previousDirectionMask, int directionMask, int edgeMask) {
        var points = 0;
        var direction = directionMask & 0xF;
        if (direction != 0 && direction != (previousDirectionMask & 0xF)) points++;
        if ((edgeMask & 0x3) != 0) points++;
        return Math.min(2, points);
    }

    static boolean hasSelfOverrideInput(int directionMask, int edgeMask) {
        return (directionMask & 0xF) != 0 || (edgeMask & 0xF) != 0;
    }

    private static PlayerControlFrame normalizeDirectFrame(ServerPlayer subject, PlayerControlFrame frame) {
        PlayerMovementMode mode;
        if (subject.isPassenger()) {
            var id = BuiltInRegistries.ENTITY_TYPE.getKey(subject.getVehicle().getType()).getPath();
            if (id.equals("boat") || id.endsWith("_boat")) mode = PlayerMovementMode.BOAT;
            else if (id.contains("minecart")) mode = PlayerMovementMode.RAIL;
            else mode = PlayerMovementMode.MOUNT;
        } else if (subject.isFallFlying()) mode = PlayerMovementMode.GLIDE;
        else if (subject.getAbilities().flying) mode = PlayerMovementMode.FLY;
        else if (subject.isInWater()) mode = PlayerMovementMode.SWIM;
        else if (subject.onClimbable()) mode = PlayerMovementMode.CLIMB;
        else mode = frame.jump() ? PlayerMovementMode.JUMP : PlayerMovementMode.WALK;
        return new PlayerControlFrame(
                frame.forward(), frame.strafe(), Mth.wrapDegrees(frame.yaw()), frame.pitch(),
                frame.jump(), frame.sneak(), frame.sprint(), frame.attack(), frame.use(), mode
        );
    }

    /**
     * Path frames originate on the server, so their planned movement mode is authoritative.
     */
    static PlayerControlFrame normalizePathFrame(PlayerControlFrame frame) {
        return new PlayerControlFrame(
                frame.forward(), frame.strafe(), Mth.wrapDegrees(frame.yaw()), frame.pitch(),
                frame.jump(), frame.sneak(), frame.sprint(), frame.attack(), frame.use(), frame.mode()
        );
    }

    private static void authorize(Session session, PlayerControlFrame frame, long sequence) {
        session.authorizedFrame = frame;
        session.authorizedFrames.addLast(new AuthorizedFrame(sequence, frame));
        while (session.authorizedFrames.size() > 64) session.authorizedFrames.removeFirst();
        MisakaNetworkServer.send(session.subject, new AuthorizedFramePacket(
                session.id, session.revision, sequence, frame
        ));
    }

    private static void attack(ServerPlayer subject, PlayerControlFrame frame) {
        var target = raycastEntity(subject, subject.isCreative() ? 5.0 : 3.0, frame.yaw(), frame.pitch());
        if (target == null || MentalControlRuntime.attackDecision(subject, target) == AttackDecision.DENY) return;
        subject.attack(target);
    }

    private static void useCurrentItem(ServerPlayer subject, PlayerControlFrame frame) {
        useCurrentItem(subject, frame, InteractionHand.MAIN_HAND);
    }

    private static void useCurrentItem(
            ServerPlayer subject,
            PlayerControlFrame frame,
            InteractionHand hand
    ) {
        var range = subject.isCreative() ? 5.0 : 4.5;
        var entity = raycastEntity(subject, range, frame.yaw(), frame.pitch());
        if (entity != null) {
            subject.interactOn(
                    entity,
                    hand,
                    entity.getBoundingBox().getCenter().subtract(entity.position())
            );
            closeUnauthorizedContainer(subject);
            return;
        }
        var eye = subject.getEyePosition();
        var hit = subject.level().clip(new ClipContext(
                eye,
                eye.add(Vec3.directionFromRotation(frame.pitch(), frame.yaw()).scale(range)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.ANY,
                subject
        ));
        var level = (ServerLevel) subject.level();
        var stack = subject.getItemInHand(hand);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            subject.gameMode.useItemOn(subject, level, stack, hand, blockHit);
        } else {
            subject.gameMode.useItem(subject, level, stack, hand);
        }
        closeUnauthorizedContainer(subject);
    }

    private static void closeUnauthorizedContainer(ServerPlayer subject) {
        if (subject.containerMenu != subject.inventoryMenu) subject.closeContainer();
    }

    private static LivingEntity raycastEntity(
            ServerPlayer subject,
            double range,
            float yaw,
            float pitch
    ) {
        var eye = subject.getEyePosition();
        var end = eye.add(Vec3.directionFromRotation(pitch, yaw).scale(range));
        var block = subject.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, subject));
        var rayEnd = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(
                subject.level(), subject, eye, rayEnd, new AABB(eye, rayEnd).inflate(1.0),
                entity -> entity instanceof LivingEntity living && living != subject
                        && living.isAlive() && living.isPickable() && !living.isSpectator(),
                0.3f
        );
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static void stop(Session session, EndReason reason, boolean notify) {
        stop(session, reason, notify, true);
    }

    private static void stop(Session session, EndReason reason, boolean notify, boolean applyResistance) {
        if (session == null || session.state == State.CLOSED) return;
        var wasActive = session.state == State.ACTIVE;
        session.state = State.CLOSED;
        if (session.kind == Kind.PATH) CLOSED_PATH_REASONS.put(session.id, reason);
        if (session.kind == Kind.DIRECT) BY_CONTROLLER.remove(session.controller.getUUID(), session);
        BY_SUBJECT.remove(session.subject.getUUID(), session);
        if (session.handle != null) session.handle.close();
        if (session.kind == Kind.DIRECT) {
            AbilitySystemServer.getSystem(session.controller).releaseMaintenanceOccupation(
                    session.controller.getUUID(), Skills.MENTAL_TAKEOVER.get().getKeyString());
        }
        if (wasActive && applyResistance) grantResistance(session.subject);
        if (notify) {
            var end = new EndPacket(session.id, session.revision, reason);
            if (session.kind == Kind.DIRECT) MisakaNetworkServer.send(session.controller, end);
            MisakaNetworkServer.send(session.subject, end);
        }
        if (wasActive && session.kind == Kind.DIRECT) MentalIntrusionManager.stopAny(session.controller);
    }

    private static void stop(MobSession session, EndReason reason, boolean notify) {
        if (session == null || session.state == State.CLOSED) return;
        var wasActive = session.state == State.ACTIVE;
        session.state = State.CLOSED;
        MOB_BY_CONTROLLER.remove(session.controller.getUUID(), session);
        MOB_BY_SUBJECT.remove(session.subject.getUUID(), session);
        session.handle.close();
        AbilitySystemServer.getSystem(session.controller).releaseMaintenanceOccupation(
                session.controller.getUUID(), Skills.MENTAL_TAKEOVER.get().getKeyString());
        if (notify) {
            MisakaNetworkServer.send(session.controller,
                    new EndPacket(session.id, session.revision, reason));
        }
        if (wasActive) MentalIntrusionManager.stopAny(session.controller);
    }

    private static void sendBegin(Session session, ServerPlayer recipient, Role role) {
        MisakaNetworkServer.send(recipient, new BeginPacket(
                session.id, session.revision, role, session.subject.getId(), session.subject.getUUID()));
    }

    private static void sendBegin(MobSession session, ServerPlayer recipient) {
        MisakaNetworkServer.send(recipient, new BeginPacket(
                session.id,
                session.revision,
                Role.CONTROLLER,
                session.subject.getId(),
                session.subject.getUUID()
        ));
    }

    private static void sendStatus(Session session) {
        var system = AbilitySystemServer.getSystem(session.controller);
        var status = new StatusPacket(
                session.id, session.revision, session.struggle,
                system.getPlayerAvailableCP(session.controller.getUUID()),
                system.getPlayerMaxCP(session.controller.getUUID())
        );
        MisakaNetworkServer.send(session.controller, status);
        MisakaNetworkServer.send(session.subject, status);
    }

    private static void sendStatus(MobSession session) {
        var system = AbilitySystemServer.getSystem(session.controller);
        MisakaNetworkServer.send(session.controller, new StatusPacket(
                session.id,
                session.revision,
                0,
                system.getPlayerAvailableCP(session.controller.getUUID()),
                system.getPlayerMaxCP(session.controller.getUUID())
        ));
    }

    private static void sendTargetViewState(Session session, long now) {
        if (session.kind != Kind.DIRECT || session.state != State.ACTIVE) return;
        var subject = session.subject;
        var inventory = subject.getInventory();
        var hotbar = new ArrayList<ItemStack>(9);
        for (var slot = 0; slot < 9; slot++) hotbar.add(inventory.getItem(slot).copy());
        var state = new TargetViewState(
                hotbar,
                inventory.getSelectedSlot(),
                subject.getOffhandItem().copy(),
                subject.getHealth(),
                subject.getMaxHealth(),
                subject.getAbsorptionAmount(),
                subject.getArmorValue(),
                subject.getFoodData().getFoodLevel(),
                subject.getFoodData().getSaturationLevel(),
                subject.getAirSupply(),
                subject.getMaxAirSupply(),
                subject.experienceProgress,
                subject.experienceLevel,
                subject.getAttackStrengthScale(0.0f),
                subject.isUsingItem(),
                subject.getUsedItemHand(),
                subject.getUseItemRemainingTicks()
        );
        session.lastViewSnapshotTick = now;
        try {
            MisakaNetworkServer.send(session.controller, new TargetViewStatePacket(
                    session.id,
                    session.revision,
                    ++session.viewSnapshotSequence,
                    state,
                    subject.registryAccess()
            ));
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Failed to send controlled-player view snapshot from {} to {}",
                    subject.getGameProfile().name(),
                    session.controller.getGameProfile().name(),
                    exception
            );
            stop(session, EndReason.LIFECYCLE, true);
        }
    }

    private static Session findSession(ServerPlayer player) {
        var session = BY_CONTROLLER.get(player.getUUID());
        return session != null ? session : BY_SUBJECT.get(player.getUUID());
    }

    private static Session pathSession(PathSessionToken token) {
        if (token == null) return null;
        var session = BY_SUBJECT.get(token.subjectId);
        return session != null && session.kind == Kind.PATH
                && matches(session, token.sessionId, token.revision) ? session : null;
    }

    private static long nextSessionRevision(UUID controllerId, UUID subjectId) {
        var next = Math.max(
                REVISIONS.getOrDefault(controllerId, 0L),
                REVISIONS.getOrDefault(subjectId, 0L)
        ) + 1L;
        REVISIONS.put(controllerId, next);
        REVISIONS.put(subjectId, next);
        return next;
    }

    private static boolean matches(Session session, UUID id, long revision) {
        return session != null && session.id.equals(id) && session.revision == revision;
    }

    private static boolean matches(MobSession session, UUID id, long revision) {
        return session != null && session.id.equals(id) && session.revision == revision;
    }

    private static void feedback(ServerPlayer player, String key) {
        player.sendOverlayMessage(Component.translatable(key));
    }

    private static void writeFrame(ByteBuf buf, PlayerControlFrame frame) {
        buf.writeByte(Math.round(frame.forward() * 127.0f));
        buf.writeByte(Math.round(frame.strafe() * 127.0f));
        buf.writeShort(Math.round(Mth.wrapDegrees(frame.yaw()) * 100.0f));
        buf.writeShort(Math.round(frame.pitch() * 100.0f));
        var actions = 0;
        if (frame.jump()) actions |= 1;
        if (frame.sneak()) actions |= 2;
        if (frame.sprint()) actions |= 4;
        if (frame.attack()) actions |= 8;
        if (frame.use()) actions |= 16;
        buf.writeByte(actions);
        buf.writeByte(frame.mode().ordinal());
    }

    private static PlayerControlFrame readFrame(ByteBuf buf) {
        var forward = buf.readByte() / 127.0f;
        var strafe = buf.readByte() / 127.0f;
        var yaw = buf.readShort() / 100.0f;
        var pitch = buf.readShort() / 100.0f;
        var actions = buf.readUnsignedByte();
        var modes = PlayerMovementMode.values();
        var mode = modes[Mth.clamp(buf.readUnsignedByte(), 0, modes.length - 1)];
        return new PlayerControlFrame(
                forward, strafe, yaw, pitch,
                (actions & 1) != 0, (actions & 2) != 0, (actions & 4) != 0,
                (actions & 8) != 0, (actions & 16) != 0, mode
        );
    }

    private static void writeTargetViewState(ByteBuf buf, SerializedTargetViewState state) {
        for (var stack : state.hotbar) writeItemStackSnapshot(buf, stack);
        buf.writeByte(state.selectedSlot);
        writeItemStackSnapshot(buf, state.offhand);
        buf.writeFloat(state.health);
        buf.writeFloat(state.maxHealth);
        buf.writeFloat(state.absorption);
        buf.writeByte(state.armor);
        buf.writeByte(state.food);
        buf.writeFloat(state.saturation);
        ByteBufCodecs.VAR_INT.encode(buf, state.air);
        ByteBufCodecs.VAR_INT.encode(buf, state.maxAir);
        buf.writeFloat(state.experienceProgress);
        ByteBufCodecs.VAR_INT.encode(buf, state.experienceLevel);
        buf.writeFloat(state.attackStrength);
        buf.writeBoolean(state.usingItem);
        buf.writeByte(state.useHand.ordinal());
        ByteBufCodecs.VAR_INT.encode(buf, state.useRemainingTicks);
    }

    private static SerializedTargetViewState readTargetViewState(ByteBuf buf) {
        var hotbar = new ArrayList<SerializedItemStack>(9);
        for (var slot = 0; slot < 9; slot++) hotbar.add(readItemStackSnapshot(buf));
        var selectedSlot = buf.readUnsignedByte();
        var offhand = readItemStackSnapshot(buf);
        var health = buf.readFloat();
        var maxHealth = buf.readFloat();
        var absorption = buf.readFloat();
        var armor = buf.readUnsignedByte();
        var food = buf.readUnsignedByte();
        var saturation = buf.readFloat();
        var air = ByteBufCodecs.VAR_INT.decode(buf);
        var maxAir = ByteBufCodecs.VAR_INT.decode(buf);
        var experienceProgress = buf.readFloat();
        var experienceLevel = ByteBufCodecs.VAR_INT.decode(buf);
        var attackStrength = buf.readFloat();
        var usingItem = buf.readBoolean();
        var hands = InteractionHand.values();
        var useHand = hands[Mth.clamp(buf.readUnsignedByte(), 0, hands.length - 1)];
        var useRemainingTicks = ByteBufCodecs.VAR_INT.decode(buf);
        return new SerializedTargetViewState(
                hotbar, selectedSlot, offhand, health, maxHealth, absorption,
                armor, food, saturation, air, maxAir, experienceProgress,
                experienceLevel, attackStrength, usingItem, useHand, useRemainingTicks
        );
    }

    private static void writeItemStackSnapshot(ByteBuf buf, SerializedItemStack snapshot) {
        buf.writeBoolean(snapshot.tag != null);
        if (snapshot.tag != null) ITEM_STACK_TAG_CODEC.encode(buf, snapshot.tag);
    }

    private static SerializedItemStack readItemStackSnapshot(ByteBuf buf) {
        return buf.readBoolean()
                ? new SerializedItemStack(ITEM_STACK_TAG_CODEC.decode(buf))
                : SerializedItemStack.EMPTY;
    }

    private static SerializedItemStack serializeItemStack(
            ItemStack stack,
            HolderLookup.Provider registries
    ) {
        if (stack.isEmpty()) return SerializedItemStack.EMPTY;
        try {
            var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
            return new SerializedItemStack(ItemStack.CODEC.encodeStart(ops, stack)
                    .getOrThrow(IllegalArgumentException::new));
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to serialize controlled-player item {}", stack.getItem(), exception);
            return SerializedItemStack.EMPTY;
        }
    }

    private static ItemStack deserializeItemStack(
            SerializedItemStack snapshot,
            HolderLookup.Provider registries
    ) {
        if (snapshot.tag == null) return ItemStack.EMPTY;
        try {
            var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
            return ItemStack.CODEC.parse(ops, snapshot.tag)
                    .getOrThrow(IllegalArgumentException::new);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to deserialize controlled-player item snapshot", exception);
            return ItemStack.EMPTY;
        }
    }

    private static void writeUuid(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    public enum Role {
        CONTROLLER,
        SUBJECT,
        SELF
    }

    public enum StartResult {
        STARTED,
        STOPPED,
        NOT_IN_ROSTER,
        INVALID_TARGET,
        PROTECTED,
        RESISTANT,
        CONTROL_CYCLE,
        INSUFFICIENT_CP,
        UNAVAILABLE
    }

    public enum EndReason {
        CONTROLLER_STOPPED,
        CONTROLLER_DAMAGED,
        STRUGGLE,
        PROTECTED,
        CLIENT_TIMEOUT,
        CLIENT_REJECTED,
        ILLEGAL_MOVEMENT,
        LIFECYCLE
    }

    private enum State {
        HANDSHAKE,
        ACTIVE,
        CLOSED
    }

    private enum Kind {
        DIRECT,
        PATH
    }

    public record PathSessionToken(UUID sessionId, long revision, UUID subjectId) {
    }

    public record MobDirectInput(long sequence, PlayerControlFrame frame) {
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void toggle(TogglePacket packet) {
            if (!MentaloutRequestGuard.acceptSkillUse(
                    packet.getPacketListener(), MentaloutRequestGuard.SkillUse.MENTAL_TAKEOVER,
                    packet.sequence)) return;
            var player = packet.getPacketListener().getPlayer();
            switch (PlayerControlSessionManager.toggle(player)) {
                case NOT_IN_ROSTER -> feedback(player, "message.academy.mentalout.takeover.not_in_roster");
                case INVALID_TARGET -> feedback(player, "message.academy.mentalout.invalid_target");
                case PROTECTED -> {
                }
                case RESISTANT -> feedback(player, "message.academy.mentalout.control_resistance");
                case CONTROL_CYCLE -> feedback(player, "message.academy.mentalout.control_cycle");
                case INSUFFICIENT_CP -> feedback(player, "message.academy.mentalout.insufficient_cp");
                case UNAVAILABLE -> feedback(player, "message.academy.mentalout.skill_unavailable");
                default -> {
                }
            }
        }

        @SubscribePacket
        public static void ready(ReadyPacket packet) {
            PlayerControlSessionManager.ready(packet.getPacketListener().getPlayer(), packet);
        }

        @SubscribePacket
        public static void intent(IntentPacket packet) {
            PlayerControlSessionManager.intent(packet.getPacketListener().getPlayer(), packet);
        }

        @SubscribePacket
        public static void inventoryAction(InventoryActionPacket packet) {
            PlayerControlSessionManager.inventoryAction(
                    packet.getPacketListener().getPlayer(), packet);
        }

        @SubscribePacket
        public static void struggle(StrugglePacket packet) {
            PlayerControlSessionManager.struggle(packet.getPacketListener().getPlayer(), packet);
        }

        @SubscribePacket
        public static void applied(AppliedFramePacket packet) {
            PlayerControlSessionManager.applied(packet.getPacketListener().getPlayer(), packet);
        }

        @SubscribePacket
        public static void stop(StopRequestPacket packet) {
            var session = findSession(packet.getPacketListener().getPlayer());
            if (matches(session, packet.sessionId, packet.revision)) {
                PlayerControlSessionManager.stop(
                        session,
                        EndReason.CONTROLLER_STOPPED,
                        true,
                        session.kind != Kind.PATH || session.controller != session.subject
                );
                return;
            }
            var mobSession = MOB_BY_CONTROLLER.get(
                    packet.getPacketListener().getPlayer().getUUID());
            if (matches(mobSession, packet.sessionId, packet.revision)) {
                PlayerControlSessionManager.stop(mobSession, EndReason.CONTROLLER_STOPPED, true);
            }
        }
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void begin(BeginPacket packet) {
            PlayerControlClientState.begin(packet.sessionId, packet.revision, packet.role,
                    packet.subjectEntityId, packet.subjectUuid);
        }

        @SubscribePacket
        public static void authorize(AuthorizedFramePacket packet) {
            PlayerControlClientState.authorize(
                    packet.sessionId, packet.revision, packet.sequence, packet.frame);
        }

        @SubscribePacket
        public static void status(StatusPacket packet) {
            PlayerControlClientState.status(packet.sessionId, packet.revision,
                    packet.struggle, packet.cp, packet.maxCp);
        }

        @SubscribePacket
        public static void targetView(TargetViewStatePacket packet) {
            PlayerControlClientState.targetViewState(
                    packet.sessionId,
                    packet.revision,
                    packet.sequence,
                    packet.decodeState(packet.getPacketListener().registryAccess())
            );
        }

        @SubscribePacket
        public static void end(EndPacket packet) {
            PlayerControlClientState.end(packet.sessionId, packet.revision);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = ByteBufCodecs.LONG.map(
                TogglePacket::new, packet -> packet.sequence);
        private final long sequence;

        public TogglePacket(long sequence) {
            this.sequence = sequence;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.MENTAL_TAKEOVER_TOGGLE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ReadyPacket extends Packet<ServerGamePacketListenerImpl, ReadyPacket> {
        public static final StreamCodec<ByteBuf, ReadyPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.sessionId);
                    buf.writeLong(packet.revision);
                    buf.writeBoolean(packet.ready);
                },
                buf -> new ReadyPacket(readUuid(buf), buf.readLong(), buf.readBoolean()));
        private final UUID sessionId;
        private final long revision;
        private final boolean ready;

        public ReadyPacket(UUID sessionId, long revision, boolean ready) {
            this.sessionId = sessionId;
            this.revision = revision;
            this.ready = ready;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ReadyPacket> getPacketType() {
            return PacketTypes.MENTAL_TAKEOVER_READY.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class IntentPacket extends Packet<ServerGamePacketListenerImpl, IntentPacket> {
        public static final StreamCodec<ByteBuf, IntentPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.sessionId);
                    buf.writeLong(packet.revision);
                    buf.writeLong(packet.sequence);
                    writeFrame(buf, packet.frame);
                },
                buf -> new IntentPacket(readUuid(buf), buf.readLong(), buf.readLong(), readFrame(buf)));
        private final UUID sessionId;
        private final long revision;
        private final long sequence;
        private final PlayerControlFrame frame;

        public IntentPacket(UUID sessionId, long revision, long sequence, PlayerControlFrame frame) {
            this.sessionId = sessionId;
            this.revision = revision;
            this.sequence = sequence;
            this.frame = frame;
        }

        public UUID sessionId() {
            return sessionId;
        }

        public long revision() {
            return revision;
        }

        public long sequence() {
            return sequence;
        }

        public PlayerControlFrame frame() {
            return frame;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, IntentPacket> getPacketType() {
            return PacketTypes.MENTAL_TAKEOVER_INTENT.get();
        }
    }

    public enum InventoryAction {
        SELECT_HOTBAR,
        USE_OFFHAND
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class InventoryActionPacket extends Packet<ServerGamePacketListenerImpl, InventoryActionPacket> {
        public static final StreamCodec<ByteBuf, InventoryActionPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.sessionId);
                    buf.writeLong(packet.revision);
                    buf.writeLong(packet.sequence);
                    buf.writeByte(packet.action.ordinal());
                    buf.writeByte(packet.value);
                },
                buf -> new InventoryActionPacket(
                        readUuid(buf),
                        buf.readLong(),
                        buf.readLong(),
                        InventoryAction.values()[Math.clamp(
                                buf.readUnsignedByte(), 0, InventoryAction.values().length - 1)],
                        buf.readByte()
                ));
        private final UUID sessionId;
        private final long revision;
        private final long sequence;
        private final InventoryAction action;
        private final int value;

        public InventoryActionPacket(
                UUID sessionId,
                long revision,
                long sequence,
                InventoryAction action,
                int value
        ) {
            this.sessionId = sessionId;
            this.revision = revision;
            this.sequence = sequence;
            this.action = action;
            this.value = value;
        }

        public UUID sessionId() {
            return sessionId;
        }

        public long revision() {
            return revision;
        }

        public long sequence() {
            return sequence;
        }

        public InventoryAction action() {
            return action;
        }

        public int value() {
            return value;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, InventoryActionPacket> getPacketType() {
            return PacketTypes.MENTAL_TAKEOVER_INVENTORY_ACTION.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StrugglePacket extends Packet<ServerGamePacketListenerImpl, StrugglePacket> {
        public static final StreamCodec<ByteBuf, StrugglePacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.sessionId);
                    buf.writeLong(packet.revision);
                    buf.writeLong(packet.sequence);
                    buf.writeByte(packet.directionMask);
                    buf.writeByte(packet.edgeMask);
                },
                buf -> new StrugglePacket(readUuid(buf), buf.readLong(), buf.readLong(),
                        buf.readUnsignedByte(), buf.readUnsignedByte()));
        private final UUID sessionId;
        private final long revision;
        private final long sequence;
        private final int directionMask;
        private final int edgeMask;

        public StrugglePacket(UUID sessionId, long revision, long sequence, int directionMask, int edgeMask) {
            this.sessionId = sessionId;
            this.revision = revision;
            this.sequence = sequence;
            this.directionMask = directionMask;
            this.edgeMask = edgeMask;
        }

        public int directionMask() {
            return directionMask;
        }

        public int edgeMask() {
            return edgeMask;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StrugglePacket> getPacketType() {
            return PacketTypes.MENTAL_TAKEOVER_STRUGGLE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class AppliedFramePacket extends Packet<ServerGamePacketListenerImpl, AppliedFramePacket> {
        public static final StreamCodec<ByteBuf, AppliedFramePacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.sessionId);
                    buf.writeLong(packet.revision);
                    buf.writeLong(packet.sequence);
                },
                buf -> new AppliedFramePacket(readUuid(buf), buf.readLong(), buf.readLong()));
        private final UUID sessionId;
        private final long revision;
        private final long sequence;

        public AppliedFramePacket(UUID sessionId, long revision, long sequence) {
            this.sessionId = sessionId;
            this.revision = revision;
            this.sequence = sequence;
        }

        public UUID sessionId() {
            return sessionId;
        }

        public long revision() {
            return revision;
        }

        public long sequence() {
            return sequence;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, AppliedFramePacket> getPacketType() {
            return PacketTypes.MENTAL_TAKEOVER_APPLIED.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StopRequestPacket extends Packet<ServerGamePacketListenerImpl, StopRequestPacket> {
        public static final StreamCodec<ByteBuf, StopRequestPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.sessionId);
                    buf.writeLong(packet.revision);
                },
                buf -> new StopRequestPacket(readUuid(buf), buf.readLong()));
        private final UUID sessionId;
        private final long revision;

        public StopRequestPacket(UUID sessionId, long revision) {
            this.sessionId = sessionId;
            this.revision = revision;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StopRequestPacket> getPacketType() {
            return PacketTypes.MENTAL_TAKEOVER_STOP.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class BeginPacket extends Packet<ClientPacketListener, BeginPacket> {
        public static final StreamCodec<ByteBuf, BeginPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.sessionId);
                    buf.writeLong(packet.revision);
                    buf.writeByte(packet.role.ordinal());
                    ByteBufCodecs.VAR_INT.encode(buf, packet.subjectEntityId);
                    writeUuid(buf, packet.subjectUuid);
                },
                buf -> new BeginPacket(readUuid(buf), buf.readLong(),
                        Role.values()[Mth.clamp(buf.readUnsignedByte(), 0, Role.values().length - 1)],
                        ByteBufCodecs.VAR_INT.decode(buf), readUuid(buf)));
        private final UUID sessionId;
        private final long revision;
        private final Role role;
        private final int subjectEntityId;
        private final UUID subjectUuid;

        public BeginPacket(UUID sessionId, long revision, Role role, int subjectEntityId, UUID subjectUuid) {
            this.sessionId = sessionId;
            this.revision = revision;
            this.role = role;
            this.subjectEntityId = subjectEntityId;
            this.subjectUuid = subjectUuid;
        }

        public Role role() {
            return role;
        }

        @Override
        public PacketType<ClientPacketListener, BeginPacket> getPacketType() {
            return PacketTypes.MENTAL_TAKEOVER_BEGIN.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class AuthorizedFramePacket extends Packet<ClientPacketListener, AuthorizedFramePacket> {
        public static final StreamCodec<ByteBuf, AuthorizedFramePacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.sessionId);
                    buf.writeLong(packet.revision);
                    buf.writeLong(packet.sequence);
                    writeFrame(buf, packet.frame);
                },
                buf -> new AuthorizedFramePacket(
                        readUuid(buf), buf.readLong(), buf.readLong(), readFrame(buf)));
        private final UUID sessionId;
        private final long revision;
        private final long sequence;
        private final PlayerControlFrame frame;

        public AuthorizedFramePacket(UUID sessionId, long revision, long sequence, PlayerControlFrame frame) {
            this.sessionId = sessionId;
            this.revision = revision;
            this.sequence = sequence;
            this.frame = frame;
        }

        @Override
        public PacketType<ClientPacketListener, AuthorizedFramePacket> getPacketType() {
            return PacketTypes.MENTAL_TAKEOVER_FRAME.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class StatusPacket extends Packet<ClientPacketListener, StatusPacket> {
        public static final StreamCodec<ByteBuf, StatusPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.sessionId);
                    buf.writeLong(packet.revision);
                    buf.writeByte(packet.struggle);
                    buf.writeFloat(packet.cp);
                    buf.writeFloat(packet.maxCp);
                },
                buf -> new StatusPacket(readUuid(buf), buf.readLong(), buf.readUnsignedByte(),
                        buf.readFloat(), buf.readFloat()));
        private final UUID sessionId;
        private final long revision;
        private final int struggle;
        private final float cp;
        private final float maxCp;

        public StatusPacket(UUID sessionId, long revision, int struggle, float cp, float maxCp) {
            this.sessionId = sessionId;
            this.revision = revision;
            this.struggle = struggle;
            this.cp = cp;
            this.maxCp = maxCp;
        }

        @Override
        public PacketType<ClientPacketListener, StatusPacket> getPacketType() {
            return PacketTypes.MENTAL_TAKEOVER_STATUS.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class TargetViewStatePacket extends Packet<ClientPacketListener, TargetViewStatePacket> {
        public static final StreamCodec<ByteBuf, TargetViewStatePacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.sessionId);
                    buf.writeLong(packet.revision);
                    buf.writeLong(packet.sequence);
                    writeTargetViewState(buf, packet.state);
                },
                buf -> new TargetViewStatePacket(
                        readUuid(buf), buf.readLong(), buf.readLong(), readTargetViewState(buf)
                ));
        private final UUID sessionId;
        private final long revision;
        private final long sequence;
        private final SerializedTargetViewState state;

        public TargetViewStatePacket(
                UUID sessionId,
                long revision,
                long sequence,
                TargetViewState state,
                HolderLookup.Provider registries
        ) {
            this(sessionId, revision, sequence, SerializedTargetViewState.from(state, registries));
        }

        private TargetViewStatePacket(
                UUID sessionId,
                long revision,
                long sequence,
                SerializedTargetViewState state
        ) {
            this.sessionId = sessionId;
            this.revision = revision;
            this.sequence = sequence;
            this.state = state;
        }

        public UUID sessionId() {
            return sessionId;
        }

        public long revision() {
            return revision;
        }

        public long sequence() {
            return sequence;
        }

        public TargetViewState decodeState(HolderLookup.Provider registries) {
            return state.decode(registries);
        }

        @Override
        public PacketType<ClientPacketListener, TargetViewStatePacket> getPacketType() {
            return PacketTypes.MENTAL_TAKEOVER_TARGET_VIEW.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class EndPacket extends Packet<ClientPacketListener, EndPacket> {
        public static final StreamCodec<ByteBuf, EndPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    writeUuid(buf, packet.sessionId);
                    buf.writeLong(packet.revision);
                    buf.writeByte(packet.reason.ordinal());
                },
                buf -> new EndPacket(readUuid(buf), buf.readLong(),
                        EndReason.values()[Mth.clamp(buf.readUnsignedByte(), 0, EndReason.values().length - 1)]));
        private final UUID sessionId;
        private final long revision;
        private final EndReason reason;

        public EndPacket(UUID sessionId, long revision, EndReason reason) {
            this.sessionId = sessionId;
            this.revision = revision;
            this.reason = reason;
        }

        @Override
        public PacketType<ClientPacketListener, EndPacket> getPacketType() {
            return PacketTypes.MENTAL_TAKEOVER_END.get();
        }
    }

    private record Anchor(Vec3 position, float yaw, float pitch) {
    }

    private record AuthorizedFrame(long sequence, PlayerControlFrame frame) {
    }

    private record AcknowledgedFrame(long sequence, PlayerControlFrame frame, long appliedTick) {
    }

    private record SerializedItemStack(Tag tag) {
        private static final SerializedItemStack EMPTY = new SerializedItemStack(null);
    }

    private record SerializedTargetViewState(
            List<SerializedItemStack> hotbar,
            int selectedSlot,
            SerializedItemStack offhand,
            float health,
            float maxHealth,
            float absorption,
            int armor,
            int food,
            float saturation,
            int air,
            int maxAir,
            float experienceProgress,
            int experienceLevel,
            float attackStrength,
            boolean usingItem,
            InteractionHand useHand,
            int useRemainingTicks
    ) {
        private SerializedTargetViewState {
            if (hotbar.size() != 9) {
                throw new IllegalArgumentException("Serialized target hotbar must have nine slots");
            }
            hotbar = List.copyOf(hotbar);
        }

        private static SerializedTargetViewState from(
                TargetViewState state,
                HolderLookup.Provider registries
        ) {
            return new SerializedTargetViewState(
                    state.hotbar.stream()
                            .map(stack -> serializeItemStack(stack, registries))
                            .toList(),
                    state.selectedSlot,
                    serializeItemStack(state.offhand, registries),
                    state.health,
                    state.maxHealth,
                    state.absorption,
                    state.armor,
                    state.food,
                    state.saturation,
                    state.air,
                    state.maxAir,
                    state.experienceProgress,
                    state.experienceLevel,
                    state.attackStrength,
                    state.usingItem,
                    state.useHand,
                    state.useRemainingTicks
            );
        }

        private TargetViewState decode(HolderLookup.Provider registries) {
            return new TargetViewState(
                    hotbar.stream()
                            .map(stack -> deserializeItemStack(stack, registries))
                            .toList(),
                    selectedSlot,
                    deserializeItemStack(offhand, registries),
                    health,
                    maxHealth,
                    absorption,
                    armor,
                    food,
                    saturation,
                    air,
                    maxAir,
                    experienceProgress,
                    experienceLevel,
                    attackStrength,
                    usingItem,
                    useHand,
                    useRemainingTicks
            );
        }
    }

    public record TargetViewState(
            List<ItemStack> hotbar,
            int selectedSlot,
            ItemStack offhand,
            float health,
            float maxHealth,
            float absorption,
            int armor,
            int food,
            float saturation,
            int air,
            int maxAir,
            float experienceProgress,
            int experienceLevel,
            float attackStrength,
            boolean usingItem,
            InteractionHand useHand,
            int useRemainingTicks
    ) {
        public TargetViewState {
            if (hotbar.size() != 9) throw new IllegalArgumentException("Target hotbar must have nine slots");
            hotbar = hotbar.stream().map(ItemStack::copy).toList();
            selectedSlot = Mth.clamp(selectedSlot, 0, 8);
            offhand = offhand.copy();
            health = Math.max(0.0f, health);
            maxHealth = Math.max(1.0f, maxHealth);
            absorption = Math.max(0.0f, absorption);
            armor = Mth.clamp(armor, 0, 20);
            food = Mth.clamp(food, 0, 20);
            saturation = Math.max(0.0f, saturation);
            air = Math.max(0, air);
            maxAir = Math.max(1, maxAir);
            experienceProgress = Mth.clamp(experienceProgress, 0.0f, 1.0f);
            experienceLevel = Math.max(0, experienceLevel);
            attackStrength = Mth.clamp(attackStrength, 0.0f, 1.0f);
            useHand = Objects.requireNonNull(useHand, "useHand");
            useRemainingTicks = Math.max(0, useRemainingTicks);
        }

        public ItemStack selectedItem() {
            return hotbar.get(selectedSlot);
        }
    }

    private static final class Session {
        private final UUID id;
        private final long revision;
        private final Kind kind;
        private final ServerPlayer controller;
        private final ServerPlayer subject;
        private final ControlHandle handle;
        private final long readyDeadline;
        private final Anchor controllerAnchor;
        private final ArrayDeque<AuthorizedFrame> authorizedFrames = new ArrayDeque<>();
        private final ArrayDeque<AcknowledgedFrame> acknowledgedFrames = new ArrayDeque<>();
        private State state = State.HANDSHAKE;
        private boolean controllerReady;
        private boolean subjectReady;
        private long lastIntentTick;
        private long lastIntentAcceptedTick = Long.MIN_VALUE;
        private long lastNeutralTick = Long.MIN_VALUE;
        private long lastControllerSequence = -1L;
        private long lastInventoryActionSequence = -1L;
        private long lastHotbarSwitchTick = Long.MIN_VALUE;
        private long lastOffhandUseTick = Long.MIN_VALUE;
        private long lastSubjectSequence = -1L;
        private long lastStruggleAcceptedTick = Long.MIN_VALUE;
        private long lastStruggleTick;
        private long authorizedSequence;
        private long lastAppliedSequence = -1L;
        private long lastAppliedTick;
        private long lastViewSnapshotTick = Long.MIN_VALUE;
        private long viewSnapshotSequence;
        private int lastDirectionMask;
        private int struggle;
        private float struggleRemainder;
        private int invalidMoves;
        private Vec3 lastGoodPosition;
        private PlayerControlFrame authorizedFrame = PlayerControlFrame.NEUTRAL;

        private Session(
                UUID id,
                long revision,
                Kind kind,
                ServerPlayer controller,
                ServerPlayer subject,
                ControlHandle handle,
                long readyDeadline,
                long now,
                Anchor controllerAnchor,
                Vec3 lastGoodPosition
        ) {
            this.id = id;
            this.revision = revision;
            this.kind = kind;
            this.controller = controller;
            this.subject = subject;
            this.handle = handle;
            this.readyDeadline = readyDeadline;
            this.controllerAnchor = controllerAnchor;
            this.lastIntentTick = now;
            this.lastAppliedTick = now;
            this.lastStruggleTick = now;
            this.lastGoodPosition = lastGoodPosition;
        }
    }

    private static final class MobSession {
        private final UUID id;
        private final long revision;
        private final ServerPlayer controller;
        private final Mob subject;
        private final ControlHandle handle;
        private final long readyDeadline;
        private final Anchor controllerAnchor;
        private State state = State.HANDSHAKE;
        private long lastIntentTick;
        private long lastIntentAcceptedTick = Long.MIN_VALUE;
        private long lastControllerSequence = -1L;
        private long frameSequence;
        private PlayerControlFrame frame = PlayerControlFrame.NEUTRAL;

        private MobSession(
                UUID id,
                long revision,
                ServerPlayer controller,
                Mob subject,
                ControlHandle handle,
                long readyDeadline,
                long now,
                Anchor controllerAnchor
        ) {
            this.id = id;
            this.revision = revision;
            this.controller = controller;
            this.subject = subject;
            this.handle = handle;
            this.readyDeadline = readyDeadline;
            this.lastIntentTick = now;
            this.controllerAnchor = controllerAnchor;
        }
    }
}
