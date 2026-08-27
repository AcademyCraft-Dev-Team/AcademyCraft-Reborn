package org.academy.internal.client.ability.mentalout;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.entitycontrol.PlayerControlFrame;
import org.academy.api.common.entitycontrol.PlayerMovementMode;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.PlayerControlSessionManager;
import org.academy.mixin.client.ClientInputAccessor;
import org.misaka.MisakaNetworkClient;

import java.util.UUID;

/**
 * Client-side capture/injection endpoint for an authorized player-control session.
 */
public final class PlayerControlClientState {
    private static UUID sessionId;
    private static UUID subjectUuid;
    private static int subjectEntityId = -1;
    private static long revision;
    private static PlayerControlSessionManager.Role role;
    private static PlayerControlFrame authorizedFrame = PlayerControlFrame.NEUTRAL;
    private static long authorizedSequence = -1L;
    private static long clientSequence;
    private static long lastSentGameTick = Long.MIN_VALUE;
    private static long lastAppliedSentGameTick = Long.MIN_VALUE;
    private static int previousDirectionMask;
    private static boolean previousJump;
    private static boolean previousSneak;
    private static boolean previousAttack;
    private static boolean previousUse;
    private static boolean previousOffhandAction;
    private static int previousHotbarMask;
    private static boolean pendingAttack;
    private static boolean pendingUse;
    private static boolean pendingJump;
    private static boolean pendingSneak;
    private static boolean pendingDirectionChange;
    private static int pendingDirectionMask;
    private static int pendingStruggleEdges;
    private static float virtualYaw;
    private static float virtualPitch;
    private static int struggle;
    private static float controllerCp;
    private static float controllerMaxCp;
    private static long targetViewSequence = -1L;
    private static PlayerControlSessionManager.TargetViewState targetViewState;

    private PlayerControlClientState() {
    }

    public static void begin(
            UUID requestedSession,
            long requestedRevision,
            PlayerControlSessionManager.Role requestedRole,
            int subjectEntityId,
            UUID requestedSubjectUuid
    ) {
        var minecraft = Minecraft.getInstance();
        if (requestedRevision < revision || minecraft.level == null || minecraft.player == null) {
            acknowledge(requestedSession, requestedRevision, false);
            return;
        }
        Entity target = null;
        if (requestedRole != PlayerControlSessionManager.Role.CONTROLLER) {
            if (!minecraft.player.getUUID().equals(requestedSubjectUuid)) {
                acknowledge(requestedSession, requestedRevision, false);
                return;
            }
        } else {
            target = minecraft.level.getEntity(subjectEntityId);
            if (target == null || target.isRemoved() || !target.getUUID().equals(requestedSubjectUuid)
                    || !MentalIntrusionClientState.isActive()) {
                acknowledge(requestedSession, requestedRevision, false);
                return;
            }
        }

        clearSessionKeys();
        sessionId = requestedSession;
        revision = requestedRevision;
        role = requestedRole;
        subjectUuid = requestedSubjectUuid;
        PlayerControlClientState.subjectEntityId = subjectEntityId;
        authorizedFrame = PlayerControlFrame.NEUTRAL;
        authorizedSequence = -1L;
        clientSequence = 0L;
        lastSentGameTick = Long.MIN_VALUE;
        lastAppliedSentGameTick = Long.MIN_VALUE;
        previousDirectionMask = 0;
        previousJump = false;
        previousSneak = false;
        previousAttack = false;
        previousUse = false;
        previousOffhandAction = false;
        previousHotbarMask = 0;
        pendingAttack = false;
        pendingUse = false;
        pendingJump = false;
        pendingSneak = false;
        pendingDirectionChange = false;
        pendingDirectionMask = 0;
        pendingStruggleEdges = 0;
        virtualYaw = target == null ? minecraft.player.getYRot() : target.getYRot();
        virtualPitch = target == null ? minecraft.player.getXRot() : target.getXRot();
        struggle = 0;
        targetViewSequence = -1L;
        targetViewState = null;
        acknowledge(requestedSession, requestedRevision, true);
    }

    public static void authorize(
            UUID requestedSession,
            long requestedRevision,
            long sequence,
            PlayerControlFrame frame
    ) {
        if (!matches(requestedSession, requestedRevision)
                || !isInputSubject()
                || sequence <= authorizedSequence) return;
        authorizedSequence = sequence;
        authorizedFrame = frame;
    }

    public static void status(
            UUID requestedSession,
            long requestedRevision,
            int requestedStruggle,
            float cp,
            float maxCp
    ) {
        if (!matches(requestedSession, requestedRevision)) return;
        struggle = Mth.clamp(requestedStruggle, 0, 100);
        controllerCp = Math.max(0.0f, cp);
        controllerMaxCp = Math.max(0.0f, maxCp);
    }

    public static void targetViewState(
            UUID requestedSession,
            long requestedRevision,
            long sequence,
            PlayerControlSessionManager.TargetViewState state
    ) {
        if (!matches(requestedSession, requestedRevision) || !isController()
                || sequence <= targetViewSequence) return;
        targetViewSequence = sequence;
        targetViewState = state;
    }

    public static void end(UUID requestedSession, long requestedRevision) {
        if (requestedRevision < revision) return;
        if (sessionId != null && requestedSession != null && !sessionId.equals(requestedSession)) return;
        revision = requestedRevision;
        clearSessionKeys();
        clearSession();
    }

    public static void tick() {
        if (sessionId == null) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            requestStop();
            clearSession();
            return;
        }
        if (role == PlayerControlSessionManager.Role.CONTROLLER
                && controlledViewEntity() == null) {
            // Lifecycle packets are best effort during death/removal. Never leave the local input
            // proxy latched if the controlled entity has already disappeared client-side.
            requestStop();
            clearSessionKeys();
            clearSession();
            return;
        }
        var gameTick = minecraft.level.getGameTime();
        if (role == PlayerControlSessionManager.Role.CONTROLLER) {
            captureController(minecraft, gameTick);
        } else {
            captureStruggleAndInject(minecraft, gameTick);
        }
    }

    public static boolean isActive() {
        return sessionId != null;
    }

    public static boolean isSubject() {
        return sessionId != null && role == PlayerControlSessionManager.Role.SUBJECT;
    }

    public static boolean isSelfControlled() {
        return sessionId != null && role == PlayerControlSessionManager.Role.SELF;
    }

    private static boolean isInputSubject() {
        return isSubject() || isSelfControlled();
    }

    public static boolean isController() {
        return sessionId != null && role == PlayerControlSessionManager.Role.CONTROLLER;
    }

    /**
     * Keeps vanilla creative-flight physics active while a path frame is driving the vertical
     * axis. Vanilla interprets repeated jump edges as a request to toggle flight and also clears
     * flight while touching the ground; both behaviours fight an authorized FLY frame.
     */
    public static boolean prepareAuthorizedFlight(LocalPlayer player) {
        if (player == null || player != Minecraft.getInstance().player
                || !isInputSubject() || authorizedSequence < 0L
                || authorizedFrame.mode() != PlayerMovementMode.FLY || !player.mayFly()) {
            return false;
        }
        if (!player.getAbilities().flying) {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
        return true;
    }

    public static boolean hasControllerView() {
        return isController();
    }

    public static float controllerViewYaw() {
        return virtualYaw;
    }

    public static float controllerViewPitch() {
        return virtualPitch;
    }

    /**
     * Receives the same sensitivity/inversion-adjusted deltas vanilla would pass to Entity.turn.
     */
    public static boolean captureViewTurn(double yawDelta, double pitchDelta) {
        if (isSelfControlled()) {
            if (yawDelta != 0.0 || pitchDelta != 0.0) requestStop();
            return false;
        }
        if (!isController()) return false;
        virtualYaw = Mth.wrapDegrees(virtualYaw + (float) yawDelta * 0.15f);
        virtualPitch = Mth.clamp(virtualPitch + (float) pitchDelta * 0.15f, -90.0f, 90.0f);
        return true;
    }

    public static boolean blocksWorldInteraction() {
        if (isSelfControlled()) {
            requestStop();
            return false;
        }
        return isActive();
    }

    public static int struggle() {
        return struggle;
    }

    public static float controllerCp() {
        return controllerCp;
    }

    public static float controllerMaxCp() {
        return controllerMaxCp;
    }

    public static PlayerControlSessionManager.TargetViewState targetViewState() {
        return isController() ? targetViewState : null;
    }

    public static Entity controlledViewEntity() {
        var minecraft = Minecraft.getInstance();
        if (!isController() || minecraft.level == null) return null;
        var entity = minecraft.level.getEntity(subjectEntityId);
        return entity != null && entity.isAlive() && !entity.isRemoved()
                && entity.getUUID().equals(subjectUuid) ? entity : null;
    }

    public static void clearLocal() {
        clearSessionKeys();
        clearSession();
        revision = 0L;
    }

    /**
     * Projects an authorized frame into the input object actually consumed by LocalPlayer physics.
     */
    public static void applyAuthorizedInput(LocalPlayer player) {
        if (sessionId == null || player != Minecraft.getInstance().player) return;
        var frame = isInputSubject() ? authorizedFrame : PlayerControlFrame.NEUTRAL;
        player.input.keyPresses = inputForFrame(frame);
        ((ClientInputAccessor) player.input).academy$setMoveVector(moveVectorForFrame(frame));
        if (!isInputSubject()) return;

        player.setYRot(frame.yaw());
        player.setXRot(frame.pitch());
        player.setYHeadRot(frame.yaw());
        if (authorizedSequence < 0L || player.level() == null) return;
        var gameTick = player.level().getGameTime();
        if (gameTick == lastAppliedSentGameTick) return;
        lastAppliedSentGameTick = gameTick;
        MisakaNetworkClient.send(new PlayerControlSessionManager.AppliedFramePacket(
                sessionId, revision, authorizedSequence
        ));
    }

    static Input inputForFrame(PlayerControlFrame frame) {
        return new Input(
                frame.forward() > 0.25f,
                frame.forward() < -0.25f,
                frame.strafe() > 0.25f,
                frame.strafe() < -0.25f,
                frame.jump(),
                frame.sneak(),
                frame.sprint()
        );
    }

    static Vec2 moveVectorForFrame(PlayerControlFrame frame) {
        var movement = new Vec2(frame.strafe(), frame.forward());
        return movement.lengthSquared() > 1.0f ? movement.normalized() : movement;
    }

    private static void captureController(Minecraft minecraft, long gameTick) {
        var options = minecraft.options;
        captureInventoryActions(options);
        var forward = axis(raw(options.keyUp), raw(options.keyDown));
        var strafe = axis(raw(options.keyLeft), raw(options.keyRight));
        var jump = raw(options.keyJump);
        var sneak = raw(options.keyShift);
        var sprint = raw(options.keySprint);
        var attackDown = raw(options.keyAttack);
        var useDown = raw(options.keyUse);
        pendingAttack |= attackDown && !previousAttack;
        pendingUse |= useDown && !previousUse;
        pendingJump |= jump && !previousJump;
        pendingSneak |= sneak && !previousSneak;
        previousAttack = attackDown;
        previousUse = useDown;
        previousJump = jump;
        previousSneak = sneak;

        var frame = new PlayerControlFrame(
                forward, strafe, virtualYaw, virtualPitch,
                jump || pendingJump, sneak || pendingSneak, sprint, pendingAttack, pendingUse,
                movementMode(minecraft.player)
        );
        if (gameTick == lastSentGameTick) return;
        lastSentGameTick = gameTick;
        MisakaNetworkClient.send(new PlayerControlSessionManager.IntentPacket(
                sessionId, revision, clientSequence++, frame
        ));
        pendingAttack = false;
        pendingUse = false;
        pendingJump = false;
        pendingSneak = false;
    }

    private static void captureInventoryActions(Options options) {
        var hotbarMask = 0;
        for (var slot = 0; slot < options.keyHotbarSlots.length; slot++) {
            if (raw(options.keyHotbarSlots[slot])) hotbarMask |= 1 << slot;
        }
        var offhandDown = raw(options.keySwapOffhand);
        if (AbilitySystemClient.getSkillProficiencyMilestone(
                Skills.MENTAL_TAKEOVER.get()) >= 3
                && ProficiencyPolicy.client().allowMentalTakeoverExtendedControls()) {
            var newlyPressed = hotbarMask & ~previousHotbarMask;
            if (newlyPressed != 0) {
                var slot = Integer.numberOfTrailingZeros(newlyPressed);
                MisakaNetworkClient.send(new PlayerControlSessionManager.InventoryActionPacket(
                        sessionId,
                        revision,
                        clientSequence++,
                        PlayerControlSessionManager.InventoryAction.SELECT_HOTBAR,
                        slot
                ));
            }
            if (offhandDown && !previousOffhandAction) {
                MisakaNetworkClient.send(new PlayerControlSessionManager.InventoryActionPacket(
                        sessionId,
                        revision,
                        clientSequence++,
                        PlayerControlSessionManager.InventoryAction.USE_OFFHAND,
                        0
                ));
            }
        }
        previousHotbarMask = hotbarMask;
        previousOffhandAction = offhandDown;
    }

    private static void captureStruggleAndInject(Minecraft minecraft, long gameTick) {
        var options = minecraft.options;
        var directionMask = directionMask(options);
        var jump = raw(options.keyJump);
        var sneak = raw(options.keyShift);
        var attack = raw(options.keyAttack);
        var use = raw(options.keyUse);
        var edgeMask = 0;
        if (jump && !previousJump) edgeMask |= 1;
        if (sneak && !previousSneak) edgeMask |= 2;
        if (attack && !previousAttack) edgeMask |= 4;
        if (use && !previousUse) edgeMask |= 8;
        if (directionMask != previousDirectionMask) {
            pendingDirectionChange = true;
            pendingDirectionMask = directionMask;
        }
        pendingStruggleEdges |= edgeMask;
        if (gameTick != lastSentGameTick && (pendingDirectionChange || pendingStruggleEdges != 0)) {
            lastSentGameTick = gameTick;
            MisakaNetworkClient.send(new PlayerControlSessionManager.StrugglePacket(
                    sessionId, revision, clientSequence++,
                    pendingDirectionChange ? pendingDirectionMask : directionMask,
                    pendingStruggleEdges
            ));
            pendingDirectionChange = false;
            pendingStruggleEdges = 0;
        }
        previousDirectionMask = directionMask;
        previousJump = jump;
        previousSneak = sneak;
        previousAttack = attack;
        previousUse = use;
    }

    private static PlayerMovementMode movementMode(LocalPlayer player) {
        if (player.isPassenger()) return PlayerMovementMode.MOUNT;
        if (player.isFallFlying()) return PlayerMovementMode.GLIDE;
        if (player.getAbilities().flying) return PlayerMovementMode.FLY;
        if (player.isInWater()) return PlayerMovementMode.SWIM;
        if (player.onClimbable()) return PlayerMovementMode.CLIMB;
        return PlayerMovementMode.WALK;
    }

    private static void clearSessionKeys() {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || player.input == null) return;
        player.input.keyPresses = Input.EMPTY;
        ((ClientInputAccessor) player.input).academy$setMoveVector(Vec2.ZERO);
    }

    private static int directionMask(Options options) {
        var result = 0;
        if (raw(options.keyUp)) result |= 1;
        if (raw(options.keyDown)) result |= 2;
        if (raw(options.keyLeft)) result |= 4;
        if (raw(options.keyRight)) result |= 8;
        return result;
    }

    private static boolean raw(KeyMapping mapping) {
        return InputSystem.isPhysicalDown(mapping);
    }

    private static float axis(boolean positive, boolean negative) {
        return (positive ? 1.0f : 0.0f) - (negative ? 1.0f : 0.0f);
    }

    private static boolean matches(UUID requestedSession, long requestedRevision) {
        return sessionId != null && sessionId.equals(requestedSession) && revision == requestedRevision;
    }

    private static void acknowledge(UUID requestedSession, long requestedRevision, boolean ready) {
        MisakaNetworkClient.send(new PlayerControlSessionManager.ReadyPacket(
                requestedSession, requestedRevision, ready
        ));
    }

    private static void requestStop() {
        if (sessionId != null) {
            MisakaNetworkClient.send(new PlayerControlSessionManager.StopRequestPacket(sessionId, revision));
        }
    }

    private static void clearSession() {
        sessionId = null;
        subjectUuid = null;
        subjectEntityId = -1;
        role = null;
        authorizedFrame = PlayerControlFrame.NEUTRAL;
        authorizedSequence = -1L;
        previousAttack = false;
        previousUse = false;
        pendingAttack = false;
        pendingUse = false;
        pendingJump = false;
        pendingSneak = false;
        pendingDirectionChange = false;
        pendingStruggleEdges = 0;
        targetViewSequence = -1L;
        targetViewState = null;
    }
}
