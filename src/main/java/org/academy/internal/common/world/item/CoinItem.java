package org.academy.internal.common.world.item;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public class CoinItem extends Item {
    public static final String HEADS_TAG = "AcademyCoinHeads";
    private static boolean serverInitialized;

    public CoinItem(Properties properties) {
        super(properties);
    }

    /** Throwing a coin is a plain item action and must not require any skill or ability. */
    public static synchronized void initServer() {
        if (serverInitialized) return;
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
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

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void onThrowCoin(ThrowCoinPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var hand = findHeldCoin(player);
            if (hand == null) return;
            var stack = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(stack)) return;
            player.getCooldowns().addCooldown(stack, 5);
            if (!player.isCreative()) stack.shrink(1);

            var thrownCoin = new ThrownCoin(player.level(), player);
            thrownCoin.setHeads(player.getRandom().nextBoolean());
            thrownCoin.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
            var initialVelocity = player.onGround()
                    ? player.getDeltaMovement().multiply(2.25, 0, 2.25)
                    : player.getDeltaMovement().multiply(1.5, 0, 1.5);
            thrownCoin.setDeltaMovement(initialVelocity.add(0, 0.5, 0));
            thrownCoin.setYRot(player.getYRot());
            thrownCoin.setXRot(player.getXRot());
            thrownCoin.yRotO = player.getYRot();
            thrownCoin.xRotO = player.getXRot();
            player.level().addFreshEntity(thrownCoin);
            player.level().playSound(
                    null,
                    player,
                    SoundEvents.COIN.get(),
                    SoundSource.PLAYERS,
                    1.0f,
                    1.0f
            );
        }

        private static InteractionHand findHeldCoin(Player player) {
            for (var hand : InteractionHand.values()) {
                if (player.getItemInHand(hand).is(Items.COIN.get())) return hand;
            }
            return null;
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onCoinPickup(ItemEntityPickupEvent.Post event) {
            if (!(event.getPlayer() instanceof ServerPlayer player)) return;
            if (!event.getOriginalStack().is(Items.COIN.get())) return;
            var data = event.getItemEntity().getPersistentData();
            if (!data.contains(HEADS_TAG)) return;
            player.sendSystemMessage(Component.translatable(
                    data.getBooleanOr(HEADS_TAG, false)
                            ? "message.academy.coin.heads"
                            : "message.academy.coin.tails"
            ));
        }
    }
}
