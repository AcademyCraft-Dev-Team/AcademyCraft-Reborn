package org.academy.internal.client.structure;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import org.academy.internal.common.network.NativeStructureUsePacket;
import org.misaka.MisakaNetworkClient;

import java.lang.ref.WeakReference;

/** Routes the explicit empty-hand adapters; ordinary input keeps vanilla handling. */
public final class NativeStructureClientInteraction {
    private static long lastUseTick = Long.MIN_VALUE;
    private static WeakReference<ClientLevel> lastLevel = new WeakReference<>(null);

    private NativeStructureClientInteraction() {}

    public static boolean tryUse(Minecraft minecraft) {
        var player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gui.screen() != null
                || minecraft.getCameraEntity() != player || player.isSpectator() || !player.getAbilities().mayBuild
                || !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) return false;
        var picked = NativeStructureRaycast.pick(player, player.blockInteractionRange(), 1.0F).orElse(null);
        if (picked == null) return false;
        var state = picked.projection().view().getBlockState(picked.structureHit().block().localPosition());
        if (!state.is(Blocks.LEVER) && !state.is(Blocks.CHEST)) return false;
        long tick = minecraft.level.getGameTime();
        if (lastLevel.get() == minecraft.level && tick >= lastUseTick && tick - lastUseTick < 4) return true;
        lastLevel = new WeakReference<>(minecraft.level);
        lastUseTick = tick;
        MisakaNetworkClient.send(new NativeStructureUsePacket(picked.structureHit()));
        player.swing(InteractionHand.MAIN_HAND);
        return true;
    }
}
