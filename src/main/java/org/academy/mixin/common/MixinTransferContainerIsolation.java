package org.academy.mixin.common;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.academy.internal.common.structure.transaction.PayloadIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blocks existing menus and item capabilities as well as newly opened containers. */
@Mixin(BaseContainerBlockEntity.class)
public abstract class MixinTransferContainerIsolation implements Container {
    @Inject(method = {"canOpen", "stillValid"}, at = @At("HEAD"), cancellable = true)
    private void academy$locked(Player player, CallbackInfoReturnable<Boolean> result) {
        if (PayloadIsolation.denied((BlockEntity) (Object) this)) result.setReturnValue(false);
    }
    @Inject(method = {"getItem", "removeItemNoUpdate"}, at = @At("HEAD"), cancellable = true)
    private void academy$read(int slot, CallbackInfoReturnable<ItemStack> result) {
        if (PayloadIsolation.denied((BlockEntity) (Object) this)) result.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method = "removeItem", at = @At("HEAD"), cancellable = true)
    private void academy$take(int slot, int count, CallbackInfoReturnable<ItemStack> result) {
        if (PayloadIsolation.denied((BlockEntity) (Object) this)) result.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method = "setItem(ILnet/minecraft/world/item/ItemStack;Z)V", at = @At("HEAD"), cancellable = true)
    private void academy$write(int slot, ItemStack stack, boolean transaction, CallbackInfo callback) {
        if (PayloadIsolation.denied((BlockEntity) (Object) this)) callback.cancel();
    }
    @Inject(method = "clearContent", at = @At("HEAD"), cancellable = true)
    private void academy$clear(CallbackInfo callback) {
        if (PayloadIsolation.denied((BlockEntity) (Object) this)) callback.cancel();
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return !PayloadIsolation.denied((BlockEntity) (Object) this);
    }
}
