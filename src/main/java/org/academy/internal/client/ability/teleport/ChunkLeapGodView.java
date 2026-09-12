package org.academy.internal.client.ability.teleport;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;


/**
 * Client state for the 区块跃迁 god view: a detached overhead camera looking straight down at the
 * inspected location, rendering the real world.
 *
 * <p>This is deliberately not a picture of the map. The map raster is an abstraction produced by sampling
 * blocks; what a player needs before committing a swap is the actual terrain as the game draws it. With the
 * camera detached, the ordinary camera-centric renderer draws real chunks — the same path that renders the
 * player's own view.
 *
 * <p>State is a plain static holder so the camera mixin can consult it without a screen reference.
 */
public final class ChunkLeapGodView {
    /** Initial height above the surface; the player can raise or lower it while looking around. */
    private static final double DEFAULT_HEIGHT = 48.0;
    /** Limits on the camera height, so it cannot be buried in the ground or pushed out of sight. */
    private static final double MIN_HEIGHT = 6.0;
    private static final double MAX_HEIGHT = 320.0;
    /** Blocks the camera moves per WASD press, at the current height. */
    private static final double MOVE_STEP = 6.0;
    /** Multiplier applied per wheel notch. */
    private static final double HEIGHT_STEP = 1.2;
    /** Half-angle the camera leans from vertical, so terrain relief is visible rather than perfectly flat. */
    private static final float DEFAULT_PITCH = 78.0f;
    private static final float YAW = 0.0f;
    /** Extra far-clip distance, so the ground under the camera is not cut off. */
    private static final double FAR_EXTRA = 256.0;

    private static volatile boolean active;
    private static volatile double targetX;
    private static volatile double targetZ;
    /**
     * Surface height at the point the view was entered, held fixed while panning.
     *
     * <p>Kept separate from the camera altitude so that moving across terrain does not make the camera climb
     * and dip with it: the reference is only established on entry, so the camera stays at a constant absolute
     * height for the whole session unless the player changes it deliberately.
     */
    private static volatile double entrySurfaceY;
    /** Altitude above {@link #entrySurfaceY}; the wheel adjusts this. */
    private static volatile double altitude = DEFAULT_HEIGHT;
    private static volatile float pitch = DEFAULT_PITCH;

    private ChunkLeapGodView() {
    }

    /**
     * Whether the HUD was already hidden by the player before the god view took it over.
     *
     * <p>Recorded so leaving restores the player setting rather than always un-hiding: F1 toggles a flag the
     * player owns, and the god view only borrows it.
     */
    private static boolean hudWasHidden;

    /**
     * Enters the god view at a location, establishing its altitude reference.
     *
     * <p>Only this path sets the reference height and resets the zoom: it is what a fresh map click does. Panning
     * goes through {@link #refocus} instead, so moving never disturbs either.
     */
    public static void enter(double x, double y, double z) {
        targetX = x;
        targetZ = z;
        entrySurfaceY = y;
        altitude = DEFAULT_HEIGHT;
        pitch = DEFAULT_PITCH;
        active = true;
        // Hide the HUD for the duration, the same way F1 does, so the terrain is not covered by bars.
        var hud = Minecraft.getInstance().gui.hud;
        hudWasHidden = hud.isHidden();
        if (!hudWasHidden) hud.toggle();
    }

    /**
     * Moves the camera to a new column without changing its altitude.
     *
     * <p>This is the panning path. The y value the server reports for the new column is deliberately discarded:
     * using it would make the camera ride the terrain, which is disorienting and hides the very relief the view
     * exists to show.
     */
    public static void refocus(double x, double z) {
        if (!active) return;
        targetX = x;
        targetZ = z;
    }

    public static void leave() {
        active = false;
        // Give the HUD flag back exactly as it was found, including if the player had it hidden already.
        var hud = Minecraft.getInstance().gui.hud;
        if (hud.isHidden() != hudWasHidden) hud.toggle();
    }

    public static boolean isActive() {
        return active;
    }

    public static Vec3 cameraPosition() {
        return new Vec3(targetX, entrySurfaceY + altitude, targetZ);
    }

    public static float cameraPitch() {
        return pitch;
    }

    /** Horizontal focus, for issuing a stream request without going through the camera vector. */
    public static double focusX() {
        return targetX;
    }

    public static double focusZ() {
        return targetZ;
    }

    public static double altitude() {
        return altitude;
    }

    /**
     * Moves the camera focus across the world.
     *
     * <p>Movement is horizontal and relative to the screen, so W goes "up the map" regardless of how the
     * camera is oriented — that is what makes it usable while looking straight down.
     *
     * @param forward  positive to move north (decreasing Z)
     * @param strafe   positive to move east (increasing X)
     */
    public static void move(double forward, double strafe) {
        if (!active) return;
        // Screen-relative, and the signs follow from the camera orientation: yaw 0 faces +Z, so on screen
        // "up" is +Z and "right" is -X. Moving the other way is what made WASD feel inverted.
        targetX -= strafe * MOVE_STEP;
        targetZ += forward * MOVE_STEP;
    }

    /**
     * Raises or lowers the camera.
     *
     * <p>Height rather than field-of-view is the zoom control here: from directly overhead, changing the
     * distance is what changes how much ground is in view, and it keeps the terrain the same apparent size.
     *
     * @param up true to rise, false to descend
     */
    public static void adjustHeight(boolean up) {
        if (!active) return;
        var scaled = altitude * (up ? HEIGHT_STEP : 1 / HEIGHT_STEP);
        altitude = Math.clamp(scaled, MIN_HEIGHT, MAX_HEIGHT);
    }

    public static float cameraYaw() {
        return YAW;
    }

    public static double farExtra() {
        return FAR_EXTRA;
    }

    /** True when the local player exists and the camera should currently be overridden. */
    public static boolean shouldOverrideCamera() {
        return active && Minecraft.getInstance().level != null;
    }
}
