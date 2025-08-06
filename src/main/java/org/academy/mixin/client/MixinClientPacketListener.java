package org.academy.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import org.academy.AcademyCraftClient;
import org.academy.internal.common.world.item.ImagiphaseDowsingRodItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * For Network System
 */
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {
    @Unique
    ClientPacketListener academyCraft$instance;

    @Shadow
    public abstract Connection getConnection();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo info) {
        ImagiphaseDowsingRodItem.RENDER_TARGET_POSITIONS.clear();
        academyCraft$instance = (ClientPacketListener) (Object) this;
        AcademyCraftClient.connection = getConnection();
    }
}