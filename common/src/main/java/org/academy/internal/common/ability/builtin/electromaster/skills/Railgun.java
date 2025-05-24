package org.academy.internal.common.ability.builtin.electromaster.skills;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftClientConfig;
import org.academy.api.client.config.IClientConfigActions;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.EmptyPacket;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.internal.client.gui.screen.AbilityDeveloperScreen;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.ability.builtin.electromaster.Electromaster;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.projectile.ThrownCoin;
import org.academy.internal.common.world.entity.skill.RailgunRay;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class Railgun extends Skill {
    public static final Skill INSTANCE = new Railgun();

    static {
        NetworkSystem.registerPacketType(ShootPacket.class);
    }

    private Railgun() {
        super(SkillNames.RAILGUN, 5, 15000, List.of(ArcGenerate.INSTANCE));
    }

    @Override
    public void initServer(MinecraftServer server) {
        NetworkSystemServer.registerPacketListener(Server.class);
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
    }

    public static final class Client {
        public static final AbilityDeveloperScreen.SkillInfo SKILL_INFO =
                AbilityDeveloperScreen.registerSkillInfo(Electromaster.INSTANCE, INSTANCE, List.of(),
                        TextureResources.TEXTURE_RAILGUN_ICON, 200, 70.25f);
        public static final String KEY_NAME_ACTION = SkillNames.RAILGUN + ".shoot_action";
        public static RailgunClientConfigData CLIENT_CONFIG = new RailgunClientConfigData();

        public static void handleKey() {
            NetworkSystemClient.sendPacket(new C2SPacket(new ShootPacket()));
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
        public static void handleShoot(ShootPacket packet) {
            ServerPlayer player = packet.packetListenerSupplier.get().getPlayer();
            final UUID uuid = player.getUUID();
            final float computingPower = AbilitySystemServer.getPlayerComputingPower(uuid);
            if (computingPower < 100) {
                return;
            }
            EntityType<?> targetEntity = EntityTypes.THROWN_COIN_ENTITY_TYPE;

            Vec3 lookVec = player.getLookAngle().scale(0.25);
            BlockPos checkPos = new BlockPos((int) (lookVec.x + player.getX()),
                    (int) (lookVec.y + player.getEyeY()),
                    (int) (lookVec.z + player.getZ()));

            Vec3 boxSize = new Vec3(2, 2, 2);
            AABB box = new AABB(checkPos)
                    .inflate(boxSize.x / 2, boxSize.y / 2, boxSize.z / 2);
            List<Entity> entities = player.level().getEntities(player, box, entity -> entity.getType() == targetEntity);
            if (!entities.isEmpty()) {
                if (!AcademyCraft.DEBUG_MODE) {
                    AbilitySystemServer.setPlayerComputingPower(uuid, computingPower - 100);
                }
                player.sendSystemMessage(Component.literal("Yes"));
                ThrownCoin coin = (ThrownCoin) entities.get(0);
                RailgunRay railgunRay = new RailgunRay(EntityTypes.RAILGUN_RAY_ENTITY_TYPE, player.level());
                coin.kill();

                float length = 50;
                Vec3 startPos = new Vec3(coin.getX(), coin.getY(), coin.getZ());
                Vec3 endPos = startPos.add(player.getLookAngle().scale(length));
                railgunRay.setPos(startPos);
                railgunRay.setYRot(player.getYRot());
                railgunRay.setXRot(player.getXRot());
                player.level().addFreshEntity(railgunRay);

                Pair<Boolean, Double> result = LevelUtil.destroyBlocksAlongPath(railgunRay.level(), startPos, endPos, 0.025f, 10, false, true, true, false);
                if (result.getKey()) {
                    double d = result.getValue();
                    length = (float) d;
                    endPos = startPos.add(player.getLookAngle().scale(length));
                }
                LevelUtil.attackEntitiesAlongPath(railgunRay.level(), startPos, endPos, 0.125f, new DamageSource(railgunRay.level().damageSources().damageTypes.getHolderOrThrow(DamageTypes.MOB_ATTACK), railgunRay), 150);
                railgunRay.playSound(AcademyCraftSoundEvents.RAILGUN);
            } else {
                player.sendSystemMessage(Component.literal("No"));
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ShootPacket extends EmptyPacket<ServerGamePacketListenerImpl> {
    }
}