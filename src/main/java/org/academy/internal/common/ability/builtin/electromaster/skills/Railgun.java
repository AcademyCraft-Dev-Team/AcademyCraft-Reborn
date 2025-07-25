package org.academy.internal.common.ability.builtin.electromaster.skills;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.AcademyCraftServer;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.ability.ClientContext;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkManagerClient;
import org.academy.api.client.render.EffectRenderEvent;
import org.academy.api.client.renderer.ArcFactory;
import org.academy.api.client.renderer.ArcStyle;
import org.academy.api.client.renderer.ArcStyles;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.config.IConfigAction;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.EmptyPacket;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.internal.client.gui.screen.AbilityDeveloperScreen;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.ability.builtin.electromaster.Electromaster;
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

import java.util.*;

public class Railgun extends Skill {
    public static final Skill INSTANCE = new Railgun();
    public static final long MAX_CHARGE_TIME_NANO = 2_000_000_000L;

    private Railgun() {
        super(SkillNames.RAILGUN, 5, 15000, List.of(ArcGenerate.INSTANCE));
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftServer.SERVER_NETWORK_MANAGER.registerPacketListener(Server.class);
    }

    @Override
    public void initClient() {
        AcademyCraftConfig.registerConfigActions(INSTANCE.name, Client.Config.Action.INSTANCE);
        Client.CLIENT_CONFIG = AcademyCraftClient.CLIENT_CONFIG.getConfig(INSTANCE.name);
        if (Client.CLIENT_CONFIG == null) {
            Client.CLIENT_CONFIG = new Client.Config();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(INSTANCE.name, Client.CLIENT_CONFIG);
        }

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
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 rightVec = lookVec.cross(new Vec3(0, 1, 0)).normalize();
        return eyePos.add(rightVec.scale(0.4)).add(lookVec.scale(0.5)).add(0, -0.2, 0);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO =
                AbilityDeveloperScreen.registerSkillInfo(Electromaster.INSTANCE, INSTANCE, List.of(),
                        TextureResources.RAILGUN_ICON, 200, 70.25f);
        public static final String KEY_NAME_SHOOT = SkillNames.RAILGUN + "_shoot";
        public static Config CLIENT_CONFIG = new Config();
        public static Context currentContext;

        public static void onAction() {
            if (Minecraft.getInstance().screen != null) return;
            if (currentContext != null) {
                currentContext.release();
            } else {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player == null) return;
                boolean hasCoin = player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.COIN) || player.getItemInHand(InteractionHand.OFF_HAND).is(Items.COIN);
                if (hasCoin) {
                    currentContext = new Context(player);
                    AbilitySystemClient.registerContext(currentContext);
                    NetworkManagerClient.sendPacket(new C2SPacket(new StartChargePacket()));
                }
            }
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements IConfigAction<Config> {
                public static final IConfigAction<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public @NotNull Railgun.Client.Config deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson) {
                    return gson.fromJson(jsonElement, Config.class);
                }

                @Override
                public @NotNull JsonElement serialize(@NotNull Railgun.Client.Config configInstance, @NotNull Gson gson) {
                    return gson.toJsonTree(configInstance);
                }

                @Override
                public @NotNull Railgun.Client.Config getDefaultConfig() {
                    return new Config();
                }

                @Override
                public @NotNull Class<Config> getConfigClass() {
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
                    this.maxLifetime = lifetime;
                }
            }

            public Context(LocalPlayer player) {
                this.player = player;
            }

            public void release() {
                NetworkManagerClient.sendPacket(new C2SPacket(new ShootPacket()));
                cleanup();
            }

            private void cleanup() {
                AbilitySystemClient.unregisterContext(this);
                if (Client.currentContext == this) {
                    Client.currentContext = null;
                }
            }

            @SubscribePacket
            public void onConfirmCharge(ConfirmChargePacket packet) {
                this.coinEntityId = packet.coinEntityId;
                this.chargeStartTime = System.nanoTime();
                this.active = true;
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
                float startOffsetX = (float) (Math.random() * 2 - 1) * 0.05f;
                float startOffsetY = (float) (Math.random() * 2 - 1) * 0.05f;
                float startOffsetZ = (float) (Math.random() * 2 - 1) * 0.05f; // Small random Z offset for start
                Vector3f start = new Vector3f(startOffsetX, startOffsetY, startOffsetZ);

                ArcStyle style = ArcStyles.classic();
                style.start = new Vector3f((float) (Math.random() - 0.5), (float) (Math.random() - 0.5), (float) (Math.random() - 0.5)).normalize().mul(0.4f);
                style.end = start;
                style.seed = player.level().random.nextLong();
                style.thickness = 0.05f;
                style.segments = 8;
                style.branchChance = 0.0f;

                ArcFactory.ArcRenderData data = ArcFactory.Generator.generate(style);
                activeArcs.add(new ArcEffect(data, 8));
            }

            @SubscribeEvent
            public void onEffectRender(EffectRenderEvent event) {
                if (!active || event.livingEntity != this.player) return;

                event.poseStack.pushPose();
                event.poseStack.translate(-0.35f, 0.45f, -0.2f);
                event.poseStack.last().pose().rotateY((float) Math.toRadians(player.yBodyRot));

                for (ArcEffect effect : activeArcs) {
                    ArcFactory.render(event.poseStack, effect.data, 1.0f, 1.0f, 1.0f, 1.0f);
                }

                event.poseStack.popPose();
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
            ServerPlayer player = packet.getPacketListener().getPlayer();
            Level level = player.level();
            ThrownCoin thrownCoin = new ThrownCoin(level, player);
            thrownCoin.setPos(player.getX(), player.getEyeY() - 0.1D, player.getZ());
            thrownCoin.setDeltaMovement(packet.initialVelocity);
            thrownCoin.setRot(packet.yRot, packet.xRot);
            thrownCoin.yRotO = packet.yRot;
            thrownCoin.xRotO = packet.xRot;
            level.addFreshEntity(thrownCoin);
        }

        public static void fire(ServerPlayer player, Entity coin) {
            final UUID uuid = player.getUUID();
            final float computingPower = AbilitySystemServer.getPlayerComputingPower(uuid);
            if (computingPower < 100) {
                return;
            }

            if (!player.isCreative()) {
                AbilitySystemServer.setPlayerComputingPower(uuid, computingPower - 100);
            }
            coin.discard();

            RailgunRay railgunRay = new RailgunRay(EntityTypes.RAILGUN_RAY.get(), player.level());
            float length = 50;
            Vec3 lookDir = player.getLookAngle();
            Vec3 startPos = coin.position();

            Vec3 endPos = startPos.add(lookDir.scale(length));
            railgunRay.setPos(startPos);
            railgunRay.setYRot(player.getYRot());
            railgunRay.setXRot(player.getXRot());
            player.level().addFreshEntity(railgunRay);

            Pair<Boolean, Double> result = LevelUtil.destroyBlocksAlongPath(railgunRay.level(), startPos, endPos, 0.025f, 10, false, true, true, false);
            if (result.getKey()) {
                double d = result.getValue();
                length = (float) d;
                endPos = startPos.add(lookDir.scale(length));
            }
            LevelUtil.attackEntitiesAlongPath(railgunRay.level(), startPos, endPos, 0.125f, new DamageSource(railgunRay.level().damageSources().damageTypes.getHolderOrThrow(DamageTypes.MOB_ATTACK), railgunRay), 150);
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

            @SubscribeEvent
            public void onTick(ServerTickEvent.Pre event) {
                ticksExisted++;
                Entity coin = player.level().getEntity(coinEntityId);

                if (player.isRemoved() || coin == null || coin.isRemoved()) {
                    cleanup();
                    return;
                }

                if (coin.getY() < getHandPosition(player).y() || ticksExisted > 40) {
                    fire();
                }
            }

            public void fire() {
                Entity coin = player.level().getEntity(coinEntityId);
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
                Entity coin = player.level().getEntity(coinEntityId);
                if (coin != null) {
                    coin.discard();
                }
                player.connection.send(new S2CPacket(new ChargeEndPacket()));
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StartChargePacket extends EmptyPacket<ServerGamePacketListenerImpl> {
        public StartChargePacket(ServerGamePacketListenerImpl listener) {
            super(listener);
        }

        public StartChargePacket() {
            super(null);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ShootPacket extends EmptyPacket<ServerGamePacketListenerImpl> {
        public ShootPacket(ServerGamePacketListenerImpl listener) {
            super(listener);
        }

        public ShootPacket() {
            super(null);
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class ConfirmChargePacket extends IPacket<ClientPacketListener> {
        public int coinEntityId;

        public ConfirmChargePacket(ClientPacketListener listener) {
            super(listener);
        }

        public ConfirmChargePacket(int coinEntityId) {
            super(null);
            this.coinEntityId = coinEntityId;
        }

        @Override
        public void read(@NotNull FriendlyByteBuf buf) {
            this.coinEntityId = buf.readVarInt();
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
            buf.writeVarInt(this.coinEntityId);
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class ChargeEndPacket extends EmptyPacket<ClientPacketListener> {
        public ChargeEndPacket(ClientPacketListener listener) {
            super(listener);
        }

        public ChargeEndPacket() {
            super(null);
        }
    }
}