package org.academy.internal.client.ability.mentalout;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.academy.internal.common.ability.mentalout.PlayerControlSessionManager;

public interface ControlledItemInHandRendererBridge {
    void academy$submitControlledHands(
            AbstractClientPlayer player,
            PlayerControlSessionManager.TargetViewState state,
            float partialTick,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int packedLight
    );
}
