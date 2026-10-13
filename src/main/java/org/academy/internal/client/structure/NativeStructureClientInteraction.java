package org.academy.internal.client.structure;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.Connection;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.client.ClientHooks;
import org.academy.internal.common.network.NativeStructureUsePacket;
import org.academy.internal.common.network.NativeStructureUseResultPacket;
import org.academy.internal.common.network.NativeStructureRequestSequence;
import org.academy.internal.common.structure.space.NativeStructurePlacementPolicy;
import org.misaka.MisakaNetworkClient;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Objects;

/** Routes audited structure use without lending local coordinates to the host world's input code. */
public final class NativeStructureClientInteraction {
    private static final boolean SMOKE = Boolean.getBoolean("academy.nativeStructureMultiplayerSmoke")
            || Boolean.getBoolean("academy.nativeStructureProjectionSmoke")
            || Boolean.getBoolean("academy.nativeStructurePlacementSmoke")
            || Boolean.getBoolean("academy.nativeStructureChestSmoke");
    private static long lastUseTick = Long.MIN_VALUE;
    private static WeakReference<ClientLevel> lastLevel = new WeakReference<>(null);
    private static WeakReference<Connection> connection = new WeakReference<>(null);
    private static NativeStructureRequestSequence requests = new NativeStructureRequestSequence();
    private static @Nullable NativeStructureUseResultPacket lastAcknowledgement;

    private NativeStructureClientInteraction() {}

    public static boolean tryUse(Minecraft minecraft) {
        synchronizeConnection(minecraft);
        var player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gameMode == null || minecraft.gui.screen() != null
                || minecraft.getCameraEntity() != player || player.isSpectator() || !player.getAbilities().mayBuild
                || player.isHandsBusy() || player.isUsingItem() || minecraft.gameMode.isDestroying()) return false;
        var picked = NativeStructureRaycast.pick(player, player.blockInteractionRange(), 1.0F).orElse(null);
        if (picked == null) return false;
        // Vehicle control has a different vanilla movement path; do not lend it host useOn.
        if (player.isPassenger()) return true;
        var state = picked.projection().view().getBlockState(picked.structureHit().block().localPosition());
        var selection = NativeStructurePlacementPolicy.selectAction(player.getMainHandItem(),
                player.getOffhandItem(), player.isSecondaryUseActive(), state);
        long tick = minecraft.level.getGameTime();
        if (lastLevel.get() == minecraft.level && tick >= lastUseTick && tick - lastUseTick < 4) return true;
        lastLevel = new WeakReference<>(minecraft.level);
        lastUseTick = tick;
        for (var hand : InteractionHand.values()) {
            var inputEvent = ClientHooks.onClickInput(1, minecraft.options.keyUse, hand);
            if (inputEvent.isCanceled()) {
                if (inputEvent.shouldSwingHand()) player.swing(hand);
                return true;
            }
            var held = player.getItemInHand(hand);
            if (!held.isItemEnabled(minecraft.level.enabledFeatures())) return true;
            // A client hook can change hands or sneak state. Do not send a stale hand choice.
            var current = NativeStructurePlacementPolicy.selectAction(player.getMainHandItem(),
                    player.getOffhandItem(), player.isSecondaryUseActive(), state);
            if (!Objects.equals(selection, current)) return true;
            if (selection != null) {
                if (hand != selection.hand()) continue;
                if (selection.action() == NativeStructurePlacementPolicy.Action.PLACE
                        && player.getCooldowns().isOnCooldown(held)) return true;
                // Hooks may have turned the camera. Re-pick before taking this observation.
                var latest = NativeStructureRaycast.pick(player, player.blockInteractionRange(), 1.0F).orElse(null);
                if (latest == null || !latest.structureHit().block().equals(picked.structureHit().block())
                        || latest.projection().view().getBlockState(latest.localPosition()) != state
                        || requests.highest() == Long.MAX_VALUE || !NativeStructureInputSync.send(minecraft)) return true;
                MisakaNetworkClient.send(new NativeStructureUsePacket(requests.next(), latest.structureHit(), hand));
                if (inputEvent.shouldSwingHand()) player.swing(hand);
                return true;
            }
            if (!held.isEmpty()) {
                // Preserve food/bow/item use while the nearer structure occludes host useOn.
                // gameMode owns cooldowns, prediction, hotbar synchronization and item-use hooks.
                var result = minecraft.gameMode.useItem(player, hand);
                if (result instanceof InteractionResult.Success success) {
                    if (success.swingSource() == InteractionResult.SwingSource.CLIENT && inputEvent.shouldSwingHand()) {
                        player.swing(hand);
                    }
                    minecraft.gameRenderer.itemInHandRenderer.itemUsed(hand);
                    return true;
                }
            }
        }
        // Even a denied placement/use must not pass through to the host block behind this cell.
        return true;
    }

    public static void accept(NativeStructureUseResultPacket packet) {
        var minecraft = Minecraft.getInstance();
        // A queued reply from a disconnected listener cannot acknowledge the new connection.
        if (minecraft.getConnection() == null || packet.getPacketListener() != minecraft.getConnection()) return;
        synchronizeConnection(minecraft);
        if (requests.acknowledge(packet.requestId())) {
            // Do not retain the packet listener (and its old level) in diagnostics across disconnects.
            lastAcknowledgement = new NativeStructureUseResultPacket(packet.requestId(), packet.status());
        }
    }

    private static void synchronizeConnection(Minecraft minecraft) {
        var listener = minecraft.getConnection();
        var current = listener == null ? null : listener.getConnection();
        if (connection.get() == current) return;
        connection = new WeakReference<>(current);
        requests = new NativeStructureRequestSequence();
        lastAcknowledgement = null;
        lastUseTick = Long.MIN_VALUE;
        lastLevel = new WeakReference<>(null);
    }

    /** Diagnostics only; callers cannot allocate or acknowledge requests through these methods. */
    public static long lastRequestId() {
        synchronizeConnection(Minecraft.getInstance());
        return SMOKE ? requests.highest() : 0;
    }

    public static @Nullable NativeStructureUseResultPacket lastAcknowledgement() {
        synchronizeConnection(Minecraft.getInstance());
        return SMOKE ? lastAcknowledgement : null;
    }
}
