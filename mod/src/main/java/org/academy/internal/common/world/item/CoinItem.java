package org.academy.internal.common.world.item;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public class CoinItem extends Item {
    public CoinItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var itemStack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            MisakaNetworkClient.send(ThrowCoinPacket.INSTANCE);
            player.getCooldowns().addCooldown(itemStack, 5);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @PacketTarget(ThreadType.SERVER)
    public static class ThrowCoinPacket extends Packet<ServerGamePacketListenerImpl, ThrowCoinPacket> {
        public static final ThrowCoinPacket INSTANCE = new ThrowCoinPacket();
        public static final StreamCodec<ByteBuf, ThrowCoinPacket> CODEC = StreamCodec.unit(INSTANCE);

        private ThrowCoinPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ThrowCoinPacket> getPacketType() {
            return PacketTypes.THROW_COIN_WITH_VELOCITY.get();
        }
    }
}
