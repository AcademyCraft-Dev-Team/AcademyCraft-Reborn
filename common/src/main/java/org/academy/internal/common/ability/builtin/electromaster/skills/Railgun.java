package org.academy.internal.common.ability.builtin.electromaster.skills;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.AcademyCraftServer;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.config.IConfigAction;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.EmptyPacket;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.gui.screen.AbilityDeveloperScreen;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.ability.builtin.electromaster.Electromaster;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.academy.internal.common.world.entity.skill.RailgunRay;
import org.academy.internal.common.world.item.CoinItem;
import org.academy.internal.common.world.item.Items;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Railgun extends Skill {
    public static final Skill INSTANCE = new Railgun();

    private Railgun() {
        super(SkillNames.RAILGUN, 5, 15000, List.of(ArcGenerate.INSTANCE));
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftServer.NETWORK_SYSTEM_SERVER_INSTANCE.registerPacketListener(Server.class);
    }

    @Override
    public void initClient() {
        AcademyCraftConfig.registerConfigActions(INSTANCE.name, Client.RailgunConfig.Action.INSTANCE);
        Client.CLIENT_CONFIG = AcademyCraftClient.CLIENT_CONFIG.getConfig(INSTANCE.name);
        if (Client.CLIENT_CONFIG == null) {
            Client.CLIENT_CONFIG = new Client.RailgunConfig();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(INSTANCE.name, Client.CLIENT_CONFIG);
        }

        InputSystem.addKeyBinding(Client.KEY_NAME_SHOOT, Client.CLIENT_CONFIG.getKeyBinding(Client.KEY_NAME_SHOOT,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_X)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>())
                )
        ), Client::handleKey);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO =
                AbilityDeveloperScreen.registerSkillInfo(Electromaster.INSTANCE, INSTANCE, List.of(),
                        TextureResources.TEXTURE_RAILGUN_ICON, 200, 70.25f);
        public static final String KEY_NAME_SHOOT = SkillNames.RAILGUN + "_shoot";
        public static RailgunConfig CLIENT_CONFIG = new RailgunConfig();


        public static void handleKey() {
            NetworkSystemClient.sendPacket(new C2SPacket(new ShootPacket()));
        }

        public static class RailgunConfig {
            @SerializedName("keyBindings")
            private final Map<String, InputSystem.InputPair> keyBindings = new HashMap<>();

            public InputSystem.InputPair getKeyBinding(String name, InputSystem.InputPair defaultConfig) {
                if (!keyBindings.containsKey(name)) {
                    setKeyBinding(name, defaultConfig);
                }
                return keyBindings.get(name);
            }
            public void setKeyBinding(String name, InputSystem.InputPair keyBinding) {
                this.keyBindings.put(name, keyBinding);
            }

            public static final class Action implements IConfigAction<RailgunConfig> {
                public static final IConfigAction<RailgunConfig> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public @NotNull Railgun.Client.RailgunConfig deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson) {
                    return gson.fromJson(jsonElement, RailgunConfig.class);
                }

                @Override
                public @NotNull JsonElement serialize(@NotNull Railgun.Client.RailgunConfig configInstance, @NotNull Gson gson) {
                    return gson.toJsonTree(configInstance);
                }

                @Override
                public @NotNull Railgun.Client.RailgunConfig getDefaultConfig() {
                    return new RailgunConfig();
                }

                @Override
                public @NotNull Class<RailgunConfig> getConfigClass() {
                    return RailgunConfig.class;
                }
            }
        }
    }

    @SuppressWarnings("resource")
    public static final class Server {
        public static final Map<UUID, Integer> ACTIVE_COIN_IDS = new ConcurrentHashMap<>();

        @SubscribePacket
        public static void handleThrowCoinWithVelocity(CoinItem.ThrowCoinPacket packet) {
            ServerPlayer player = packet.packetListenerSupplier.get().getPlayer();
            Level level = player.level();

            boolean hasCoin = false;
            if (player.isCreative()) {
                hasCoin = true;
            } else {
                for (InteractionHand hand : InteractionHand.values()) {
                    if (player.getItemInHand(hand).is(Items.COIN)) {
                        player.getItemInHand(hand).shrink(1);
                        hasCoin = true;
                        break;
                    }
                }
            }

            if (!hasCoin) {
                return;
            }

            ThrownCoin thrownCoin = new ThrownCoin(level, player);
            thrownCoin.setPos(player.getX(), player.getEyeY() - 0.1D, player.getZ());

            thrownCoin.setDeltaMovement(packet.initialVelocity);
            thrownCoin.setRot(packet.yRot,packet.xRot);
            thrownCoin.yRotO = packet.yRot;
            thrownCoin.xRotO = packet.xRot;

            level.addFreshEntity(thrownCoin);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), AcademyCraftSoundEvents.COIN, player.getSoundSource(), 1.0F, 1.0F);
            ACTIVE_COIN_IDS.put(player.getUUID(), thrownCoin.getId());
        }

        @SubscribePacket
        public static void handleShoot(ShootPacket packet) {
            ServerPlayer player = packet.packetListenerSupplier.get().getPlayer();
            final UUID uuid = player.getUUID();
            final float computingPower = AbilitySystemServer.getPlayerComputingPower(uuid);
            if (computingPower < 100) {
                return;
            }
            EntityType<?> targetEntity = EntityTypes.THROWN_COIN_ENTITY_TYPE;

            Vec3 playerCenter = player.position().add(0, player.getBbHeight() / 2.0, 0);
            AABB detectionBox = new AABB(playerCenter.subtract(1.5, 1.5, 1.5),
                    playerCenter.add(1.5, 1.5, 1.5));

            List<Entity> entities = player.level().getEntities(player, detectionBox, entity -> entity.getType() == targetEntity);

            if (!entities.isEmpty()) {
                ACTIVE_COIN_IDS.remove(uuid);

                if (!player.isCreative()) {
                    AbilitySystemServer.setPlayerComputingPower(uuid, computingPower - 100);
                }

                ThrownCoin coin = (ThrownCoin) entities.get(0);
                RailgunRay railgunRay = new RailgunRay(EntityTypes.RAILGUN_RAY_ENTITY_TYPE, player.level());
                coin.kill();

                float length = 50;
                Vec3 lookDir = player.getLookAngle();
                Vec3 startPos = player.getEyePosition().add(lookDir.scale(0.5));

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
                railgunRay.playSound(AcademyCraftSoundEvents.RAILGUN);

            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ShootPacket extends EmptyPacket<ServerGamePacketListenerImpl> {
    }
}