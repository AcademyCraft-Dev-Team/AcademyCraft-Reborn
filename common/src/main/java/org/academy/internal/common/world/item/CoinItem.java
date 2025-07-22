package org.academy.internal.common.world.item;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.network.NetworkManagerClient;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.jetbrains.annotations.NotNull;

public class CoinItem extends Item {
    public CoinItem() {
        super(new Properties());
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand interactionHand) {
        ItemStack itemStack = player.getItemInHand(interactionHand);

        if (level.isClientSide) {
            Vec3 initialVelocity = player.onGround() ?
                    player.getDeltaMovement().multiply(2.25, 0, 2.25)
                    :
                    player.getDeltaMovement().multiply(1.5,0,1.5);

            NetworkManagerClient.sendPacket(new C2SPacket(new ThrowCoinPacket(
                    initialVelocity.add(0, 0.5, 0),
                    player.getYRot(), player.getXRot())));

            if (!player.getAbilities().instabuild) {
                itemStack.shrink(1);
            }
            player.playSound(AcademyCraftSoundEvents.COIN, 1.0F, 1.0F);
            player.getCooldowns().addCooldown(this, 5);
            return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
        }

        return InteractionResultHolder.pass(itemStack);
    }

    @PacketTarget(ThreadType.SERVER)
    public static class ThrowCoinPacket extends IPacket<ServerGamePacketListenerImpl> {
        public Vec3 initialVelocity;
        public float yRot;
        public float xRot;

        public ThrowCoinPacket() {
        }

        public ThrowCoinPacket(Vec3 initialVelocity, float yRot, float xRot) {
            this.initialVelocity = initialVelocity;
            this.yRot = yRot;
            this.xRot = xRot;
        }

        @Override
        public void read(@NotNull FriendlyByteBuf buf) {
            this.initialVelocity = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            this.yRot = buf.readFloat();
            this.xRot = buf.readFloat();
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
            buf.writeDouble(this.initialVelocity.x);
            buf.writeDouble(this.initialVelocity.y);
            buf.writeDouble(this.initialVelocity.z);
            buf.writeFloat(this.yRot);
            buf.writeFloat(this.xRot);
        }
    }
}