package org.academy.internal.client.sublevel;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import org.academy.internal.common.network.SubLevelInteractPacket;

/** Client input bridge for exact SubLevel snapshots. */
public final class SubLevelClientInteraction {
    private static final double REACH_PADDING = 0.5;

    private SubLevelClientInteraction() {
    }

    public static boolean use(Minecraft minecraft) {
        var hit = hit(minecraft);
        if (hit == null) return false;
        var stack = minecraft.player.getMainHandItem();
        var action = stack.getItem() instanceof BlockItem
                ? SubLevelInteractPacket.Action.PLACE : SubLevelInteractPacket.Action.USE;
        SubLevelInteractPacket.send(new SubLevelInteractPacket(hit.id(), hit.localPosition(),
                hit.face(), hit.worldPoint(), InteractionHand.MAIN_HAND, action));
        return true;
    }

    public static boolean attack(Minecraft minecraft) {
        var hit = hit(minecraft);
        if (hit == null) return false;
        SubLevelInteractPacket.send(new SubLevelInteractPacket(hit.id(), hit.localPosition(),
                hit.face(), hit.worldPoint(), InteractionHand.MAIN_HAND,
                SubLevelInteractPacket.Action.BREAK));
        return true;
    }

    private static SubLevelClientState.Hit hit(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) return null;
        var from = minecraft.player.getEyePosition();
        var to = from.add(minecraft.player.getViewVector(1.0F)
                .scale(minecraft.player.blockInteractionRange() + REACH_PADDING));
        return SubLevelClientState.raycast(minecraft.level, from, to);
    }
}
