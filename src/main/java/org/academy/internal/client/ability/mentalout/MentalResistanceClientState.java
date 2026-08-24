package org.academy.internal.client.ability.mentalout;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.academy.api.client.input.InputSystem;
import org.academy.internal.common.ability.mentalout.MentalResistanceManager;
import org.misaka.MisakaNetworkClient;

/** Captures physical break-free input only while the server reports an eligible mental effect. */
public final class MentalResistanceClientState {
    private static boolean active;
    private static boolean takeover;
    private static int points;
    private static int threshold = 1;
    private static int controllerLevel;
    private static long sequence;
    private static long lastSentGameTick = Long.MIN_VALUE;
    private static boolean previousForward;
    private static boolean previousBack;
    private static boolean previousLeft;
    private static boolean previousRight;
    private static boolean previousAttack;
    private static boolean previousUse;
    private static int pendingEdges;

    private MentalResistanceClientState() {
    }

    public static void update(
            boolean requestedActive,
            int requestedPoints,
            int requestedThreshold,
            int requestedControllerLevel,
            boolean requestedTakeover
    ) {
        var becomingActive = requestedActive && !active;
        active = requestedActive;
        points = Math.max(0, requestedPoints);
        threshold = Math.max(1, requestedThreshold);
        controllerLevel = Math.max(0, requestedControllerLevel);
        takeover = requestedTakeover;
        if (becomingActive) snapshotPhysicalInput();
        if (!active) {
            pendingEdges = 0;
            lastSentGameTick = Long.MIN_VALUE;
        }
    }

    public static void tick() {
        if (!active) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            clearLocal();
            return;
        }
        var options = minecraft.options;
        var forward = raw(options.keyUp);
        var back = raw(options.keyDown);
        var left = raw(options.keyLeft);
        var right = raw(options.keyRight);
        var attack = raw(options.keyAttack);
        var use = raw(options.keyUse);
        if (forward && !previousForward) pendingEdges |= 1;
        if (back && !previousBack) pendingEdges |= 1 << 1;
        if (left && !previousLeft) pendingEdges |= 1 << 2;
        if (right && !previousRight) pendingEdges |= 1 << 3;
        if (attack && !previousAttack) pendingEdges |= 1 << 4;
        if (use && !previousUse) pendingEdges |= 1 << 5;
        previousForward = forward;
        previousBack = back;
        previousLeft = left;
        previousRight = right;
        previousAttack = attack;
        previousUse = use;

        var gameTick = minecraft.level.getGameTime();
        if (pendingEdges == 0 || lastSentGameTick == gameTick) return;
        lastSentGameTick = gameTick;
        MisakaNetworkClient.send(new MentalResistanceManager.InputPacket(sequence++, pendingEdges));
        pendingEdges = 0;
    }

    public static boolean isActive() {
        return active;
    }

    public static float progress() {
        return Math.clamp(points / (float) Math.max(1, threshold), 0.0f, 1.0f);
    }

    public static int points() {
        return points;
    }

    public static int threshold() {
        return threshold;
    }

    public static int controllerLevel() {
        return controllerLevel;
    }

    public static boolean isTakeover() {
        return takeover;
    }

    public static void clearLocal() {
        active = false;
        takeover = false;
        points = 0;
        threshold = 1;
        controllerLevel = 0;
        pendingEdges = 0;
        lastSentGameTick = Long.MIN_VALUE;
        previousForward = false;
        previousBack = false;
        previousLeft = false;
        previousRight = false;
        previousAttack = false;
        previousUse = false;
    }

    private static void snapshotPhysicalInput() {
        var minecraft = Minecraft.getInstance();
        var options = minecraft.options;
        previousForward = raw(options.keyUp);
        previousBack = raw(options.keyDown);
        previousLeft = raw(options.keyLeft);
        previousRight = raw(options.keyRight);
        previousAttack = raw(options.keyAttack);
        previousUse = raw(options.keyUse);
        pendingEdges = 0;
    }

    private static boolean raw(KeyMapping mapping) {
        return InputSystem.isPhysicalDown(mapping);
    }
}
