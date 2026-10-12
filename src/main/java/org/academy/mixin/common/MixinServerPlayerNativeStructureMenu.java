package org.academy.mixin.common;

import net.minecraft.server.level.ServerPlayer;
import org.academy.internal.common.structure.space.NativeStructureChestMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

/** Keep the current menu stable for the whole vanilla click packet; close on the next server tick. */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerNativeStructureMenu {
    @Inject(method = {"closeContainer", "doCloseContainer"}, at = @At("HEAD"), cancellable = true)
    private void academy$deferNativeMenuClose(CallbackInfo callback) {
        var player = (ServerPlayer) (Object) this;
        if (player.containerMenu instanceof NativeStructureChestMenu menu && menu.deferCloseIfMutating()) {
            callback.cancel();
        }
    }

    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;",
            at = @At("HEAD"), cancellable = true)
    private void academy$refuseReplacingMutatingNativeMenu(CallbackInfoReturnable<OptionalInt> callback) {
        var player = (ServerPlayer) (Object) this;
        if (player.containerMenu instanceof NativeStructureChestMenu menu && menu.deferCloseIfMutating()) {
            callback.setReturnValue(OptionalInt.empty());
        }
    }

    @Inject(method = {"openHorseInventory", "openNautilusInventory"}, at = @At("HEAD"), cancellable = true)
    private void academy$refuseReplacingMutatingNativeMenuWithMount(CallbackInfo callback) {
        var player = (ServerPlayer) (Object) this;
        if (player.containerMenu instanceof NativeStructureChestMenu menu && menu.deferCloseIfMutating()) {
            callback.cancel();
        }
    }
}
