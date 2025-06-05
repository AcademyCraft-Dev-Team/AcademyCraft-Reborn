package org.academy.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.internal.common.world.item.ImagPhaseDosingRodItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {
    @Unique
    ClientPacketListener academyCraft$instance;
    @Shadow
    @Final
    private Connection connection;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo info) {
        ImagPhaseDosingRodItem.RENDER_TARGET_POSITIONS.clear();
        academyCraft$instance = (ClientPacketListener) (Object) this;
        NetworkSystemClient.connection = this.connection;
    }
}