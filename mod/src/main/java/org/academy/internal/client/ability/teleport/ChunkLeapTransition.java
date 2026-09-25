package org.academy.internal.client.ability.teleport;

import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionTransitionScreenEvent;
import org.academy.AcademyCraft;
import org.academy.internal.client.gui.screen.ChunkLeapLoadingScreen;

/**
 * Replaces the terrain-loading screen with a lightweight one during 区块跃迁 cross-dimension jumps.
 *
 * <p>A dimension transition always runs through {@code LevelLoadTracker}, and cancelling that machinery
 * would leave the server waiting for a load notification that never comes. So instead of suppressing the
 * transition, the factory below swaps the visible screen only while a chunk leap is in flight, and
 * leaves every other transition (portals, commands, respawns) on the vanilla screen.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class ChunkLeapTransition {
    /**
     * How long after a swap request a transition is still attributed to chunk leap.
     *
     * <p>The swap only starts once both regions finish preloading, so the window has to cover that
     * wait plus the transition itself. Kept short so an unrelated portal trip a moment later is not
     * mistaken for ours.
     */
    private static final long ATTRIBUTION_WINDOW_MS = 15_000L;

    private static volatile long leapRequestedAt = Long.MIN_VALUE;

    private ChunkLeapTransition() {
    }

    /**
     * Called when a map request is sent that may move the player across dimensions.
     */
    public static void markPossibleTransition() {
        leapRequestedAt = Util.getMillis();
    }

    /**
     * Clears attribution, e.g. when the operation finishes without changing dimension.
     */
    public static void clearPossibleTransition() {
        leapRequestedAt = Long.MIN_VALUE;
    }

    /**
     * True while a chunk leap transition is plausibly responsible for a dimension change.
     */
    static boolean isLeapTransition() {
        var requestedAt = leapRequestedAt;
        if (!withinWindow(requestedAt, Util.getMillis(), ATTRIBUTION_WINDOW_MS)) {
            // Expired (or never armed): stop attributing later transitions to us.
            leapRequestedAt = Long.MIN_VALUE;
            return false;
        }
        return true;
    }

    /**
     * Pure window test. Extracted so the expiry rule can be unit-tested without a real clock.
     *
     * @param requestedAt  timestamp the window started, or {@link Long#MIN_VALUE} when never armed
     * @param now          current time
     * @param windowMillis how long the window stays open
     */
    static boolean withinWindow(long requestedAt, long now, long windowMillis) {
        if (requestedAt == Long.MIN_VALUE) return false;
        if (now < requestedAt) return true;
        return now - requestedAt <= windowMillis;
    }

    @SubscribeEvent
    public static void onRegisterTransitionScreens(RegisterDimensionTransitionScreenEvent event) {
        // Registered for every dimension; the factory decides per transition whether to intercept.
        for (var dimension : new ResourceKey[]{Level.OVERWORLD, Level.NETHER, Level.END}) {
            @SuppressWarnings("unchecked")
            var key = (ResourceKey<Level>) dimension;
            event.registerIncomingEffect(key, ChunkLeapTransition::createScreen);
        }
    }

    private static LevelLoadingScreen createScreen(LevelLoadTracker tracker, LevelLoadingScreen.Reason reason) {
        if (ChunkLeapClientConfig.seamlessTransition() && isLeapTransition()) {
            // Consume the attribution: one leap should not mask a later unrelated transition.
            clearPossibleTransition();
            return new ChunkLeapLoadingScreen(tracker, reason);
        }
        return new LevelLoadingScreen(tracker, reason);
    }
}
