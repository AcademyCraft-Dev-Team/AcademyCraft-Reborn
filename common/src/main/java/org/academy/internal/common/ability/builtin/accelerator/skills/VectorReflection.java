package org.academy.internal.common.ability.builtin.accelerator.skills;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.AcademyCraftServer;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkManagerClient;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.config.IConfigAction;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.EmptyPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.gui.screen.AbilityDeveloperScreen;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.ability.builtin.accelerator.Accelerator;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.GlowCircle;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class VectorReflection extends Skill {
    public static final Skill INSTANCE = new VectorReflection();

    private VectorReflection() {
        super(SkillNames.VECTOR_REFLECTION, 2);
    }

    @Override
    public void initClient() {
        AcademyCraftConfig.registerConfigActions(INSTANCE.name, Client.VectorReflectionConfig.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.CLIENT_CONFIG.getConfig(INSTANCE.name);
        if (Client.CONFIG == null) {
            Client.CONFIG = new Client.VectorReflectionConfig();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(INSTANCE.name, Client.CONFIG);
        }

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                        new InputSystem.InputPair(
                                InputSystem.InputType.KEYBOARD,
                                new InputSystem.KeyInfo(
                                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_R)),
                                        GLFW.GLFW_PRESS,
                                        new LinkedHashSet<>()
                                )
                        )
                ), Client::toggleReflection
        );
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftServer.NETWORK_SYSTEM_SERVER_INSTANCE.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO =
                AbilityDeveloperScreen.registerSkillInfo(Accelerator.INSTANCE, INSTANCE, List.of(),
                        TextureResources.TEXTURE_VECTOR_REFLECTION_ICON, 20, 70.25f);
        public static final String KEY_NAME_TOGGLE = SkillNames.VECTOR_REFLECTION + "_toggle";
        public static VectorReflectionConfig CONFIG = new VectorReflectionConfig();

        public static void toggleReflection() {
            NetworkManagerClient.sendPacket(new C2SPacket(new TogglePacket()));
        }

        public static class VectorReflectionConfig {
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

            public static final class Action implements IConfigAction<VectorReflectionConfig> {
                public static final IConfigAction<VectorReflectionConfig> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public @NotNull VectorReflection.Client.VectorReflectionConfig deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson) {
                    return gson.fromJson(jsonElement, VectorReflectionConfig.class);
                }

                @Override
                public @NotNull JsonElement serialize(@NotNull VectorReflection.Client.VectorReflectionConfig configInstance, @NotNull Gson gson) {
                    return gson.toJsonTree(configInstance);
                }

                @Override
                public @NotNull VectorReflection.Client.VectorReflectionConfig getDefaultConfig() {
                    return new VectorReflectionConfig();
                }

                @Override
                public @NotNull Class<VectorReflectionConfig> getConfigClass() {
                    return VectorReflectionConfig.class;
                }
            }
        }
    }

    public static final class Server {
        public static final Map<UUID, Boolean> ACTIVE_REFLECTION_MAP = new LinkedHashMap<>();

        @SubscribePacket
        public static void toggleReflection(TogglePacket packet) {
            UUID uuid = packet.packetListenerSupplier.get().getPlayer().getUUID();
            if (ACTIVE_REFLECTION_MAP.containsKey(uuid)) {
                ACTIVE_REFLECTION_MAP.put(uuid, !ACTIVE_REFLECTION_MAP.get(uuid));
            } else {
                ACTIVE_REFLECTION_MAP.put(uuid, true);
            }
        }

        public static boolean shouldReflection(Player player, DamageSource damageSource) {
            if (player.isCreative() || player.isSpectator()) {
                return false;
            }
            final UUID uuid = player.getUUID();
            if (ACTIVE_REFLECTION_MAP.containsKey(uuid)) {
                return ACTIVE_REFLECTION_MAP.get(uuid);
            }
            return !damageSource.is(DamageTypes.STARVE) && !damageSource.is(DamageTypes.DROWN) && !damageSource.is(DamageTypes.GENERIC_KILL);
        }

        @SuppressWarnings("resource")
        public static Pair<Boolean, Float> handleHurt(Player player, DamageSource source, float originalDamage) {
            if (player.invulnerableTime > 10) {
                return Pair.of(false, 0f);
            }

            if (!shouldReflection(player, source)) {
                return Pair.of(true, originalDamage);
            }

            float requiredPower = originalDamage * 10f;
            float currentPower = AbilitySystemServer.getPlayerComputingPower(player.getUUID());

            if (currentPower > 0) {
                player.level().playSound(null, player, AcademyCraftSoundEvents.VECTOR_REFLECTION, SoundSource.BLOCKS, 1, 1);
            }

            if (currentPower >= requiredPower) {
                AbilitySystemServer.setPlayerComputingPower(player.getUUID(), currentPower - requiredPower);
                player.invulnerableTime = 20;

                applyReflection(player, source, originalDamage);

                return Pair.of(false, 0f);
            }

            float reflectedDamage = currentPower / 10f;
            float remainingDamage = Math.max(0f, originalDamage - reflectedDamage);
            AbilitySystemServer.setPlayerComputingPower(player.getUUID(), 0);

            applyReflection(player, source, reflectedDamage);

            return Pair.of(true, remainingDamage);
        }

        @SuppressWarnings("resource")
        private static void applyReflection(Player player, DamageSource source, float reflectedDamage) {
            Entity sourceEntity = source.getEntity();
            Entity directEntity = source.getDirectEntity();

            if (sourceEntity == null || sourceEntity == player) return;

            boolean isProjectile = directEntity instanceof Projectile;

            Vec3 vec3 = player.getLookAngle().normalize().scale(1);

            Vec3 spawnPos = isProjectile
                    ? directEntity.position()
                    : player.getPosition(1.0F).add(vec3.x, vec3.y + 1.5, vec3.z);

            GlowCircle glowCircle = new GlowCircle(EntityTypes.GLOW_CIRCLE_ENTITY_TYPE, player.level());
            glowCircle.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

            Vec3 dir = sourceEntity.position().subtract(player.getPosition(1)).normalize();

            float yaw = (float) (Math.toDegrees(Math.atan2(dir.z, dir.x))) - 90.0F;
            float pitch = (float) (-Math.toDegrees(Math.asin(dir.y)));

            glowCircle.setYRot(yaw);
            glowCircle.setXRot(pitch);

            player.level().addFreshEntity(glowCircle);

            if (isProjectile) {
                directEntity.setDeltaMovement(directEntity.getDeltaMovement().scale(10));
            } else {
                sourceEntity.hurt(source, reflectedDamage);
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends EmptyPacket<ServerGamePacketListenerImpl> {
    }
}