package org.academy.internal.client.structure;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.client.ClientHooks;
import org.academy.internal.common.network.NativeStructureUsePacket;
import org.academy.internal.common.structure.space.NativeStructurePlacementPolicy;
import org.misaka.MisakaNetworkClient;

import java.lang.ref.WeakReference;
import java.util.Objects;

/** Routes audited structure use without lending local coordinates to the host world's input code. */
public final class NativeStructureClientInteraction {
    private static long lastUseTick = Long.MIN_VALUE;
    private static WeakReference<ClientLevel> lastLevel = new WeakReference<>(null);

    private NativeStructureClientInteraction() {}

    public static boolean tryUse(Minecraft minecraft) {
        var player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gameMode == null || minecraft.gui.screen() != null
                || minecraft.getCameraEntity() != player || player.isSpectator() || !player.getAbilities().mayBuild
                || player.isHandsBusy() || player.isUsingItem() || minecraft.gameMode.isDestroying()) return false;
        var picked = NativeStructureRaycast.pick(player, player.blockInteractionRange(), 1.0F).orElse(null);
        if (picked == null) return false;
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
                // A hotbar key can run after gameMode.tick's ordinary synchronization this tick.
                // Both packets use the same connection, so the server observes the selected slot first.
                player.connection.send(new ServerboundSetCarriedItemPacket(player.getInventory().getSelectedSlot()));
                MisakaNetworkClient.send(new NativeStructureUsePacket(picked.structureHit(), hand));
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
}
