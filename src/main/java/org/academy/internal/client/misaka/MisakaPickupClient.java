package org.academy.internal.client.misaka;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.academy.api.client.input.InputSystem;
import org.academy.internal.common.network.misaka.TogglePickUpMisakaPacket;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.misaka.MisakaNetworkClient;

public final class MisakaPickupClient {
    public static final String KEY_NAME_PICKUP = "misaka_pickup";

    private MisakaPickupClient() {
    }

    public static void init() {
        var defaultCombo = InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_B,
                InputConstants.PRESS,
                InputSystem.ANY_MODIFIER
        );
        InputSystem.addConfiguredKeyBinding(KEY_NAME_PICKUP, defaultCombo, context -> tryTogglePickup());
    }

    private static void tryTogglePickup() {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null || minecraft.gui.screen() != null) {
            return;
        }
        var box = player.getBoundingBox().inflate(MisakaSisterEntity.PICKUP_RANGE);
        MisakaSisterEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (var sister : level.getEntitiesOfClass(MisakaSisterEntity.class, box)) {
            if (!sister.isAwakened()) {
                continue;
            }
            double distance = sister.distanceToSqr(player);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = sister;
            }
        }
        if (nearest != null) {
            MisakaNetworkClient.send(new TogglePickUpMisakaPacket(nearest.getUUID()));
        }
    }
}
