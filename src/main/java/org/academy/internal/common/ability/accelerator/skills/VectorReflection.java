package org.academy.internal.common.ability.accelerator.skills;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
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
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.PacketType;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.GlowCircle;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class VectorReflection extends Skill {
    public VectorReflection() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL2)
                .dependsOn(Skills.VECTOR_REFLECTION)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                        new InputSystem.InputPair(
                                InputSystem.InputType.KEYBOARD,
                                new InputSystem.KeyInfo(
                                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_R)),
                                        GLFW.GLFW_PRESS,
                                        new LinkedHashSet<>()
                                )
                        )
                ), Client::onToggle
        );
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftServer.NETWORK_MANAGER.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE = SkillNames.VECTOR_REFLECTION + "_toggle";
        public static Config CONFIG = new Config();

        public static void onToggle() {
            AcademyCraftClient.sendPacket(new C2SPacket(TogglePacket.INSTANCE));
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public @NotNull VectorReflection.Client.Config getDefault() {
                    return new Config();
                }

                @Override
                public @NotNull Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }

    public static final class Server {
        public static final Map<UUID, Boolean> ACTIVE_REFLECTION_MAP = new LinkedHashMap<>();

        @SubscribePacket
        public static void toggleReflection(TogglePacket packet) {
            var uuid = packet.getPacketListener().getPlayer().getUUID();
            ACTIVE_REFLECTION_MAP.compute(uuid, (key, value) -> value == null || !value);
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
        public static Pair<Boolean, Float> onPlayerHurt(Player player, DamageSource source, float originalDamage) {
            if (player.invulnerableTime > 10) {
                return Pair.of(false, 0f);
            }

            if (!shouldReflection(player, source)) {
                return Pair.of(true, originalDamage);
            }

            float requiredPower = originalDamage * 10f;
            float currentPower = AbilitySystemServer.getPlayerComputingPower(player.getUUID());

            if (currentPower > 0) {
                player.level().playSound(null, player, SoundEvents.VECTOR_REFLECTION.get(), SoundSource.BLOCKS, 1, 1);
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

            GlowCircle glowCircle = new GlowCircle(EntityTypes.GLOW_CIRCLE.get(), player.level());
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
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);

        private TogglePacket() {
        }

        @Override
        public @NotNull PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.VECTOR_REFLECTION_TOGGLE.get();
        }
    }
}