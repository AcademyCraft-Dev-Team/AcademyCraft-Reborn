package org.academy.internal.common.ability.electromaster.skills;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.client.sync.ClientSyncManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.sync.DataSyncManager;
import org.academy.api.server.sync.ServerSyncManager;
import org.academy.internal.client.renderer.effect.RailgunEffectRenderer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.sync.DataTypes;
import org.academy.internal.common.sync.SyncKeys;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.RailgunRay;
import org.academy.internal.common.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.misaka.MisakaNetworkClient.sendPacket;

public final class Railgun extends Skill {
    public static final int CHARGE_TIME = 20;

    public Railgun() {
        super(Builder.of(AbilityCategories.ELECTROMASTER.get()).level(AbilityLevel.LEVEL5));
    }

    @Override
    public void initServer(MinecraftServer server) {
        MisakaNetworkServer.NETWORK_MANAGER.registerPacketListener(Server.class);
        var key = SyncKeys.RAILGUN_CHARGING.get();
        Server.chargingSyncManager = new DataSyncManager<>(key, DataTypes.BOOL.get(), server.getPlayerList());
        ServerSyncManager.register(key, Server.chargingSyncManager);
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(RailgunEffectRenderer.INSTANCE);
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CLIENT_CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_START, Client.CLIENT_CONFIG.getKeyBinding(Client.KEY_NAME_START,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_X)),
                                GLFW.GLFW_PRESS,
                                new LinkedHashSet<>())
                )
        ), Client::start);

        ClientSyncManager.register(SyncKeys.RAILGUN_CHARGING.get(), Client::setCharging);
    }

    public static final class Client {
        public static final String KEY_NAME_START = SkillNames.RAILGUN + "_strat";
        public static Config CLIENT_CONFIG = new Config();
        private static boolean charging = false;

        public static boolean isCharging() {
            return charging;
        }

        public static void setCharging(boolean charging) {
            Client.charging = charging;
        }

        public static void start() {
            if (Minecraft.getInstance().screen == null) {
                var player = Minecraft.getInstance().player;
                if (player == null) return;
                if (player.isHolding(Items.COIN.get())) {
                    sendPacket(StartPacket.INSTANCE);
                }
            }
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Railgun.Client.Config getDefault() {
                    return new Config();
                }

                @Override
                public Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }

    public static final class Server {
        @Nullable
        private static DataSyncManager<Boolean> chargingSyncManager;
        public static final Map<Player, Context> CONTEXT_MAP = createContextMap();

        @SubscribePacket
        public static void onStartCharge(StartPacket packet) {
            if (chargingSyncManager == null) return;

            var player = packet.getPacketListener().getPlayer();
            if (CONTEXT_MAP.containsKey(player)) return;
            var uuid = player.getUUID();
            var off = player.getItemInHand(InteractionHand.OFF_HAND);
            var main = player.getItemInHand(InteractionHand.MAIN_HAND);
            var offEmpty = off.isEmpty();
            var mainEmpty = main.isEmpty();

            if (offEmpty && mainEmpty) return;

            if (!player.isCreative()) (offEmpty ? main : off).shrink(1);
            chargingSyncManager.set(uuid, true);

            var context = new Context(player, offEmpty ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            CONTEXT_MAP.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        public static class Context extends ServerContext {
            private final InteractionHand hand;
            private int ticks = 0;
            private float yCurveOld;

            public Context(ServerPlayer player, InteractionHand hand) {
                super(player);
                this.hand = hand;
            }

            @Override
            public void unregister() {
                super.unregister();
                CONTEXT_MAP.remove(player);
                player.removeData(AttachmentTypes.RAILGUN_DATA);
                if (chargingSyncManager != null) chargingSyncManager.set(player.getUUID(), false);
            }

            @SubscribeEvent
            public void onTick(ServerTickEvent.Post event) {
                ticks++;
                var yCurve = Math.max(0, -4.0f * ticks * (ticks - CHARGE_TIME) / (CHARGE_TIME * CHARGE_TIME));
                player.setData(
                        AttachmentTypes.RAILGUN_DATA,
                        new Data(
                                (player.getMainArm() == HumanoidArm.RIGHT) ==
                                        (hand == InteractionHand.MAIN_HAND), yCurve, yCurveOld
                        )
                );
                yCurveOld = yCurve;
                if (ticks > CHARGE_TIME) {
                    unregister();
                }
            }

            public void end() {
            }

            public void fire() {
                final var uuid = player.getUUID();
                final var computingPower = AbilitySystemServer.getPlayerComputingPower(uuid);

                if (!player.isCreative()) {
                    AbilitySystemServer.setPlayerComputingPower(uuid, computingPower - 100);
                }

                var railgunRay = new RailgunRay(EntityTypes.RAILGUN_RAY.get(), player.level());
                float length = 50;
                var lookDir = player.getLookAngle();
                var startPos = player.position().add(0, 1.5, 0);

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
        }
    }

    public record Data(boolean rightHand, float yOffset, float yOffsetOld) {
        private static final Data DEFAULT = new Data(true, 0, 0);
        public static final StreamCodec<ByteBuf, Data> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Data::rightHand,
                ByteBufCodecs.FLOAT, Data::yOffset,
                ByteBufCodecs.FLOAT, Data::yOffsetOld,
                Data::new
        );

        public static Data getDefault() {
            return DEFAULT;
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StartPacket extends Packet<ServerGamePacketListenerImpl, StartPacket> {
        public static final StartPacket INSTANCE = new StartPacket();
        public static final StreamCodec<ByteBuf, StartPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StartPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StartPacket> getPacketType() {
            return PacketTypes.RAILGUN_START_CHARGE.get();
        }
    }
}