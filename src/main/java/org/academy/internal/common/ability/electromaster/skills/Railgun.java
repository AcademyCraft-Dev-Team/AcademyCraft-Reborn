package org.academy.internal.common.ability.electromaster.skills;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.ability.ClientContext;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.EffectRenderEvent;
import org.academy.api.client.renderer.ArcFactory;
import org.academy.api.client.renderer.ArcStyle;
import org.academy.api.client.renderer.ArcStyles;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.academy.internal.common.world.entity.skill.RailgunRay;
import org.academy.internal.common.world.item.CoinItem;
import org.academy.internal.common.world.item.Items;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;
import org.misaka.api.common.network.packet.S2CPacket;

import java.util.*;

public final class Railgun extends Skill {
    public static final long MAX_CHARGE_TIME_NANO = 2_000_000_000L;

    public Railgun() {
        super(Builder.of(AbilityCategories.ELECTROMASTER.get()).level(AbilityLevel.LEVEL5));
    }

    @Override
    public void initServer(MinecraftServer server) {
        MisakaNetworkServer.NETWORK_MANAGER.registerPacketListener(Server.class);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CLIENT_CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_SHOOT, Client.CLIENT_CONFIG.getKeyBinding(Client.KEY_NAME_SHOOT,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_X)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>())
                )
        ), Client::onAction);
    }

    public static Vec3 getHandPosition(Player player) {
        var eyePos = player.getEyePosition();
        var lookVec = player.getLookAngle();
        var rightVec = lookVec.cross(new Vec3(0, 1, 0)).normalize();
        return eyePos.add(rightVec.scale(0.4)).add(lookVec.scale(0.5)).add(0, -0.2, 0);
    }

    public static final class Client {
        public static final String KEY_NAME_SHOOT = SkillNames.RAILGUN + "_shoot";
        public static Config CLIENT_CONFIG = new Config();
        public static Context currentContext;

        public static void onAction() {
            if (Minecraft.getInstance().screen != null) return;
            if (currentContext != null) {
                currentContext.release();
            } else {
                var player = Minecraft.getInstance().player;
                if (player == null) return;
                var hasCoin = player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.COIN) || player.getItemInHand(InteractionHand.OFF_HAND).is(Items.COIN);
                if (hasCoin) {
                    currentContext = new Context(player);
                    AbilitySystemClient.registerContext(currentContext);
                    MisakaNetworkClient.sendPacket(StartChargePacket.INSTANCE);
                }
            }
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public @NotNull Railgun.Client.Config getDefault() {
                    return new Config();
                }

                @Override
                public @NotNull Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }

        public static final class Context implements ClientContext {
            private final LocalPlayer player;
            private long chargeStartTime;
            private boolean active = false;
            private int coinEntityId = -1;
            private final List<ArcEffect> activeArcs = new ArrayList<>();
            private float chargeRatio = 0.0f;

            private static class ArcEffect {
                final ArcFactory.ArcRenderData data;
                int lifetime;
                final int maxLifetime;

                ArcEffect(ArcFactory.ArcRenderData data, int lifetime) {
                    this.data = data;
                    this.lifetime = lifetime;
                    maxLifetime = lifetime;
                }
            }

            public Context(LocalPlayer player) {
                this.player = player;
            }

            public void release() {
                MisakaNetworkClient.sendPacket(ShootPacket.INSTANCE);
                cleanup();
            }

            private void cleanup() {
                AbilitySystemClient.unregisterContext(this);
                if (currentContext == this) {
                    currentContext = null;
                }
            }

            @SubscribePacket
            public void onConfirmCharge(ConfirmChargePacket packet) {
                coinEntityId = packet.getCoinEntityId();
                chargeStartTime = System.nanoTime();
                active = true;
            }

            @SubscribePacket
            public void onChargeEnd(ChargeEndPacket packet) {
                cleanup();
            }

            @SubscribeEvent
            public void onClientTick(ClientTickEvent.Post event) {
                if (!active) return;

                if (player.level().getEntity(coinEntityId) == null) {
                    cleanup();
                    return;
                }

                chargeRatio = (float) (System.nanoTime() - chargeStartTime) / MAX_CHARGE_TIME_NANO;
                chargeRatio = Math.min(1.0f, chargeRatio);

                activeArcs.removeIf(arc -> --arc.lifetime <= 0);
                var startOffsetX = (float) (Math.random() * 2 - 1) * 0.05f;
                var startOffsetY = (float) (Math.random() * 2 - 1) * 0.05f;
                var startOffsetZ = (float) (Math.random() * 2 - 1) * 0.05f; // Small random Z offset for start
                var start = new Vector3f(startOffsetX, startOffsetY, startOffsetZ);

                var style = ArcStyles.classic();
                style.start = new Vector3f((float) (Math.random() - 0.5), (float) (Math.random() - 0.5), (float) (Math.random() - 0.5)).normalize().mul(0.4f);
                style.end = start;
                style.seed = player.level().random.nextLong();
                style.thickness = 0.05f;
                style.segments = 8;
                style.branchChance = 0.0f;

                var data = ArcFactory.Generator.generate(style);
                activeArcs.add(new ArcEffect(data, 8));
            }

            @SubscribeEvent
            public void onEffectRender(EffectRenderEvent event) {
                if (!active) return;

                event.getPoseStack().pushPose();
                event.getPoseStack().translate(-0.35f, 0.45f, -0.2f);
                event.getPoseStack().last().pose().rotateY((float) Math.toRadians(player.yBodyRot));

                for (var effect : activeArcs) {
                    ArcFactory.render(event.getPoseStack(), event.getSubmitNodeCollector(), effect.data, 1.0f, 1.0f, 1.0f, 1.0f);
                }

                event.getPoseStack().popPose();
            }
        }
    }

    public static final class Server {
        public static final Map<UUID, Context> CONTEXT_MAP = new HashMap<>();

        @SubscribePacket
        public static void onStartCharge(StartChargePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (CONTEXT_MAP.containsKey(player.getUUID())) return;

            var hasCoin = false;
            if (player.isCreative()) {
                hasCoin = true;
            } else {
                for (var hand : InteractionHand.values()) {
                    if (player.getItemInHand(hand).is(Items.COIN)) {
                        player.getItemInHand(hand).shrink(1);
                        hasCoin = true;
                        break;
                    }
                }
            }
            if (!hasCoin) return;

            var level = player.level();
            var thrownCoin = new ThrownCoin(level, player);
            var handPos = getHandPosition(player);
            thrownCoin.setPos(handPos);
            thrownCoin.setDeltaMovement(0, 0.4, 0);
            level.addFreshEntity(thrownCoin);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.COIN, player.getSoundSource(), 1.0F, 1.0F);

            var context = new Context(player, thrownCoin.getId());
            CONTEXT_MAP.put(player.getUUID(), context);
            AbilitySystemServer.registerContext(context);

            player.connection.send(new S2CPacket(new ConfirmChargePacket(thrownCoin.getId())));
        }

        @SubscribePacket
        public static void onShoot(ShootPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var context = CONTEXT_MAP.get(player.getUUID());
            if (context != null) {
                context.fire();
            }
        }

        @SubscribePacket
        public static void onThrowCoin(CoinItem.ThrowCoinPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var level = player.level();
            var thrownCoin = new ThrownCoin(level, player);
            thrownCoin.setPos(player.getX(), player.getEyeY() - 0.1D, player.getZ());
            thrownCoin.setDeltaMovement(packet.getInitialVelocity());
            thrownCoin.setRot(packet.getYRot(), packet.getXRot());
            thrownCoin.yRotO = packet.getYRot();
            thrownCoin.xRotO = packet.getXRot();
            level.addFreshEntity(thrownCoin);
        }

        public static void fire(ServerPlayer player, Entity coin) {
            final var uuid = player.getUUID();
            final var computingPower = AbilitySystemServer.getPlayerComputingPower(uuid);
            if (computingPower < 100) {
                return;
            }

            if (!player.isCreative()) {
                AbilitySystemServer.setPlayerComputingPower(uuid, computingPower - 100);
            }
            coin.discard();

            var railgunRay = new RailgunRay(EntityTypes.RAILGUN_RAY.get(), player.level());
            float length = 50;
            var lookDir = player.getLookAngle();
            var startPos = coin.position();

            var endPos = startPos.add(lookDir.scale(length));
            railgunRay.setPos(startPos);
            railgunRay.setYRot(player.getYRot());
            railgunRay.setXRot(player.getXRot());
            player.level().addFreshEntity(railgunRay);

            var result = LevelUtil.destroyBlocksAlongPath(railgunRay.level(), startPos, endPos, 0.025f, 10, false, true, true, false);
            if (result.getKey()) {
                double d = result.getValue();
                length = (float) d;
                endPos = startPos.add(lookDir.scale(length));
            }
            LevelUtil.attackEntitiesAlongPath(railgunRay.level(), startPos, endPos, 0.125f, new DamageSource(railgunRay.level().damageSources().damageTypes.getOrThrow(DamageTypes.MOB_ATTACK), railgunRay), 150);
            railgunRay.playSound(SoundEvents.RAILGUN.get());
        }

        public static class Context implements ServerContext {
            private final ServerPlayer player;
            private final int coinEntityId;
            private int ticksExisted = 0;

            public Context(ServerPlayer player, int coinEntityId) {
                this.player = player;
                this.coinEntityId = coinEntityId;
            }

            @SuppressWarnings("resource")
            @SubscribeEvent
            public void onTick(ServerTickEvent.Pre event) {
                ticksExisted++;
                var coin = player.level().getEntity(coinEntityId);

                if (player.isRemoved() || coin == null || coin.isRemoved()) {
                    cleanup();
                    return;
                }

                if (coin.getY() < getHandPosition(player).y() || ticksExisted > 40) {
                    fire();
                }
            }

            public void fire() {
                var coin = player.level().getEntity(coinEntityId);
                if (coin == null) {
                    cleanup();
                    return;
                }

                Server.fire(player, coin);
                cleanup();
            }

            public void cleanup() {
                CONTEXT_MAP.remove(player.getUUID());
                AbilitySystemServer.unregisterContext(this);
                var coin = player.level().getEntity(coinEntityId);
                if (coin != null) {
                    coin.discard();
                }
                player.connection.send(new S2CPacket(ChargeEndPacket.INSTANCE));
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StartChargePacket extends Packet<ServerGamePacketListenerImpl, StartChargePacket> {
        public static final StartChargePacket INSTANCE = new StartChargePacket();
        public static final StreamCodec<ByteBuf, StartChargePacket> CODEC = StreamCodec.unit(INSTANCE);

        private StartChargePacket() {
        }



        @Override
        public PacketType<ServerGamePacketListenerImpl, StartChargePacket> getPacketType() {
            return PacketTypes.RAILGUN_START_CHARGE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ShootPacket extends Packet<ServerGamePacketListenerImpl, ShootPacket> {
        public static final ShootPacket INSTANCE = new ShootPacket();
        public static final StreamCodec<ByteBuf, ShootPacket> CODEC = StreamCodec.unit(INSTANCE);

        private ShootPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ShootPacket> getPacketType() {
            return PacketTypes.RAILGUN_SHOOT.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class ConfirmChargePacket extends Packet<ClientPacketListener, ConfirmChargePacket> {
        public static final StreamCodec<ByteBuf, ConfirmChargePacket> CODEC = ByteBufCodecs.VAR_INT.map(ConfirmChargePacket::new, ConfirmChargePacket::getCoinEntityId);

        private final int coinEntityId;

        public ConfirmChargePacket(int coinEntityId) {
            this.coinEntityId = coinEntityId;
        }

        public int getCoinEntityId() {
            return coinEntityId;
        }

        @Override
        public PacketType<ClientPacketListener, ConfirmChargePacket> getPacketType() {
            return PacketTypes.RAILGUN_CONFIRM_CHARGE.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class ChargeEndPacket extends Packet<ClientPacketListener, ChargeEndPacket> {
        public static final ChargeEndPacket INSTANCE = new ChargeEndPacket();
        public static final StreamCodec<ByteBuf, ChargeEndPacket> CODEC = StreamCodec.unit(INSTANCE);

        private ChargeEndPacket() {
        }

        @Override
        public PacketType<ClientPacketListener, ChargeEndPacket> getPacketType() {
            return PacketTypes.RAILGUN_CHARGE_END.get();
        }
    }
}