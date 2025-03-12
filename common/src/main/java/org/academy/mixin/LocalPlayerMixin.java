package org.academy.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import org.academy.AbilitySystemClient;
import org.academy.api.client.network.AcademyCraftNetworkSystemClient;
import org.academy.api.client.network.packet.C2SRequestPacket;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.Response;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
    @Shadow
    @Final
    public ClientPacketListener connection;

    @Inject(method = "tick", at = @At("HEAD"))
    public void tick(CallbackInfo ci) {
        Response response = new Response();
        response.runnable = new Runnable() {
            @Override
            public void run() {
                float currentComputingPower = (float) response.dataList.get(0);
                float maxComputingPower = (float) response.dataList.get(1);
                float computingPowerRecoverySpeed = (float) response.dataList.get(2);
                AbilitySystemClient.setComputingPower(currentComputingPower);
                AbilitySystemClient.setMaximumComputingPower(maxComputingPower);
            }
        };
        AcademyCraftNetworkSystemClient.CLIENT_RESPONSE_MAP.put(AcademyCraftNetworkResourceLocations.S2C_SYNC_RESPONSE, response);
        this.connection.send(new C2SRequestPacket(AcademyCraftNetworkResourceLocations.C2S_SYNC_REQUEST));
    }
}
