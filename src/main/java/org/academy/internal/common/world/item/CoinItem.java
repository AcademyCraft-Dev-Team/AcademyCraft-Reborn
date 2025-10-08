package org.academy.internal.common.world.item;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;
import org.misaka.api.common.network.ThreadType;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;

public class CoinItem extends Item {
    public CoinItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var itemStack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            var initialVelocity = player.onGround() ?
                    player.getDeltaMovement().multiply(2.25, 0, 2.25)
                    :
                    player.getDeltaMovement().multiply(1.5, 0, 1.5);

            MisakaNetworkClient.sendPacket(new ThrowCoinPacket(
                        initialVelocity.add(0, 0.5, 0),
                        player.getYRot(), player.getXRot()));

            if (!player.getAbilities().instabuild) {
                itemStack.shrink(1);
            }
            player.playSound(SoundEvents.COIN.get(), 1.0F, 1.0F);
            player.getCooldowns().addCooldown(itemStack, 5);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @PacketTarget(ThreadType.SERVER)
    public static class ThrowCoinPacket extends Packet<ServerGamePacketListenerImpl, ThrowCoinPacket> {
        private static final StreamCodec<ByteBuf, Vec3> VEC3_STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Vec3::x,
                ByteBufCodecs.DOUBLE, Vec3::y,
                ByteBufCodecs.DOUBLE, Vec3::z,
                Vec3::new
        );

        public static final StreamCodec<ByteBuf, ThrowCoinPacket> CODEC = StreamCodec.composite(
                VEC3_STREAM_CODEC,
                ThrowCoinPacket::getInitialVelocity,
                ByteBufCodecs.FLOAT,
                ThrowCoinPacket::getYRot,
                ByteBufCodecs.FLOAT,
                ThrowCoinPacket::getXRot,
                ThrowCoinPacket::new
        );

        private final Vec3 initialVelocity;
        private final float yRot;
        private final float xRot;

        public ThrowCoinPacket(Vec3 initialVelocity, float yRot, float xRot) {
            this.initialVelocity = initialVelocity;
            this.yRot = yRot;
            this.xRot = xRot;
        }

        public Vec3 getInitialVelocity() {
            return initialVelocity;
        }

        public float getYRot() {
            return yRot;
        }

        public float getXRot() {
            return xRot;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ThrowCoinPacket> getPacketType() {
            return PacketTypes.THROW_COIN_WITH_VELOCITY.get();
        }
    }
}