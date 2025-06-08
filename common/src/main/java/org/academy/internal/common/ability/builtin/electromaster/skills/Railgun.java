package org.academy.internal.common.ability.builtin.electromaster.skills;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftClientConfig;
import org.academy.AcademyCraftServer;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.IClientConfigActions;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.client.vanilla.ClientTickEvent;
import org.academy.api.common.ability.Skill;
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
import org.academy.internal.common.core.particles.ParticleTypes;
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
        AcademyCraftClientConfig.registerConfigActions(INSTANCE.name, new Client.RailgunClientConfigData());
        Client.CLIENT_CONFIG = AcademyCraftClient.CLIENT_CONFIG.getConfig(
                INSTANCE.name,
                Client.RailgunClientConfigData.class
        );
        if (Client.CLIENT_CONFIG == null) {
            Client.CLIENT_CONFIG = new Client.RailgunClientConfigData();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(INSTANCE.name, Client.CLIENT_CONFIG);
        }

        InputSystem.addKeyBinding(Client.KEY_NAME_ACTION, Client.CLIENT_CONFIG.getKeyBinding(Client.KEY_NAME_ACTION,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_X)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>())
                )
        ), Client::handleKey);
        AcademyCraft.EVENT_BUS.register(Client.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO =
                AbilityDeveloperScreen.registerSkillInfo(Electromaster.INSTANCE, INSTANCE, List.of(),
                        TextureResources.TEXTURE_RAILGUN_ICON, 200, 70.25f);
        public static final String KEY_NAME_ACTION = SkillNames.RAILGUN + ".shoot_action";
        public static RailgunClientConfigData CLIENT_CONFIG = new RailgunClientConfigData();
        private static boolean anyPlayerCoinInAir = false;
        private static int trackedCoinEntityIdForHandEffect = -1;


        public static void handleKey() {
            NetworkSystemClient.sendPacket(new C2SPacket(new ShootPacket()));
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent event) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                anyPlayerCoinInAir = false;
                trackedCoinEntityIdForHandEffect = -1;
                return;
            }

            if (trackedCoinEntityIdForHandEffect != -1) {
                Entity coin = player.level().getEntity(trackedCoinEntityIdForHandEffect);
                if (coin == null || coin.isRemoved() || !(coin instanceof ThrownCoin) || coin.onGround()) {
                    anyPlayerCoinInAir = false;
                    trackedCoinEntityIdForHandEffect = -1;
                } else {
                    anyPlayerCoinInAir = true;
                }
            } else {
                anyPlayerCoinInAir = false;
            }


            if (anyPlayerCoinInAir && player.level().getGameTime() % 2 == 0) {
                float sideFactor = (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT) ? 0.4f : -0.4f;
                Vec3 handPos = player.position()
                        .add(player.getLookAngle().cross(new Vec3(0, 1, 0)).normalize().scale(sideFactor))
                        .add(0, player.getEyeHeight() * 0.8, 0)
                        .add(player.getLookAngle().scale(0.3));

                spawnHandParticles(player, handPos);
            }
        }

        private static void spawnHandParticles(LocalPlayer player, Vec3 handPos) {
            for (int i = 0; i < 2; ++i) {
                player.level().addParticle(ParticleTypes.ARC,
                        handPos.x + (player.level().random.nextDouble() - 0.5D) * 0.1D,
                        handPos.y + (player.level().random.nextDouble() - 0.5D) * 0.1D,
                        handPos.z + (player.level().random.nextDouble() - 0.5D) * 0.1D,
                        (player.level().random.nextDouble() - 0.5D) * 0.05D,
                        (player.level().random.nextDouble() - 0.5D) * 0.05D,
                        (player.level().random.nextDouble() - 0.5D) * 0.05D);
            }
        }

        public static void accessSetAnyPlayerCoinInAir(boolean value) {
            anyPlayerCoinInAir = value;
        }

        public static int accessGetTrackedCoinEntityIdForHandEffect() {
            return trackedCoinEntityIdForHandEffect;
        }

        public static void accessSetTrackedCoinEntityIdForHandEffect(int value) {
            trackedCoinEntityIdForHandEffect = value;
        }


        public static class RailgunClientConfigData implements IClientConfigActions<RailgunClientConfigData> {
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

            @Override
            public @NotNull RailgunClientConfigData deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson) {
                return gson.fromJson(jsonElement, RailgunClientConfigData.class);
            }

            @Override
            public @NotNull JsonElement serialize(@NotNull RailgunClientConfigData configInstance, @NotNull Gson gson) {
                return gson.toJsonTree(configInstance);
            }

            @Override
            public @NotNull RailgunClientConfigData getDefaultConfig() {
                return new RailgunClientConfigData();
            }

            @Override
            public @NotNull Class<RailgunClientConfigData> getConfigClass() {
                return RailgunClientConfigData.class;
            }
        }
    }

    @SuppressWarnings("resource")
    public static final class Server {
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
            thrownCoin.setYRot(packet.yRot);
            thrownCoin.setXRot(packet.xRot);
            thrownCoin.yRotO = packet.yRot;
            thrownCoin.xRotO = packet.xRot;

            level.addFreshEntity(thrownCoin);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), AcademyCraftSoundEvents.COIN, player.getSoundSource(), 1.0F, 1.0F);
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
                if (!AcademyCraft.DEBUG_MODE) {
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