package org.academy.internal.common.ability.builtin.accelerator.skills;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.api.client.config.SkillClientConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.NetworkResourceLocations;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.network.NetworkSystemServer;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class VectorManipulation extends Skill {
    public static final VectorManipulation INSTANCE = new VectorManipulation();
    public static final String KEY_NAME_REFLECTION = "vec_manipulation.reflection";
    public static final String KEY_NAME_REVERSE_BLOODFLOW = "vec_manipulation.reverse_bloodflow";

    public VectorManipulation() {
        super("vec_manipulation", 1);
    }

    @Override
    public void initClient() {
        AcademyCraftClient.CLIENT_CONFIG.getSkillClientConfig(INSTANCE.name, Client.CONFIG);
        InputSystem.addKeyBinding(KEY_NAME_REFLECTION, Client.CONFIG.getKeyBinding(KEY_NAME_REFLECTION,
                        new InputSystem.InputPair(
                                InputSystem.InputType.KEYBOARD,
                                new InputSystem.InputEvent(
                                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_R)),
                                        GLFW.GLFW_RELEASE,
                                        new LinkedHashSet<>()
                                )
                        )
                ), Client::toggleReflection
        );
        InputSystem.addKeyBinding(KEY_NAME_REVERSE_BLOODFLOW, Client.CONFIG.getKeyBinding(KEY_NAME_REVERSE_BLOODFLOW,
                        new InputSystem.InputPair(
                                InputSystem.InputType.KEYBOARD,
                                new InputSystem.InputEvent(
                                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_R)),
                                        GLFW.GLFW_RELEASE,
                                        new LinkedHashSet<>(
                                                GLFW.GLFW_MOD_ALT
                                        )
                                )
                        )
                ), Client::reverseBloodflow
        );
    }

    @Override
    public void initServer(MinecraftServer server) {
        NetworkSystemServer.registerC2SPacketHandler(NetworkResourceLocations.C2S_TOGGLE_REFLECTION_PACKET,
                (listener, packet) ->
                        Server.toggleReflection(listener.player.getUUID())
        );
        NetworkSystemServer.registerC2SPacketHandler(NetworkResourceLocations.C2S_REVERSE_BLOODFLOW,
                (listener, packet) ->
                        Server.reverseBloodflow(listener.player)
        );
    }

    public static final class Client {
        public static final SkillClientConfig.SkillClientKeyBindingConfig CONFIG = new VectorManipulationClientConfig();

        public static final class VectorManipulationClientConfig extends SkillClientConfig.SkillClientKeyBindingConfig {
        }

        public static void reverseBloodflow() {
            NetworkSystemClient.sendPacket(new C2SPacket(NetworkResourceLocations.C2S_REVERSE_BLOODFLOW));
        }

        public static void toggleReflection() {
            NetworkSystemClient.sendPacket(new C2SPacket(NetworkResourceLocations.C2S_TOGGLE_REFLECTION_PACKET));
        }
    }

    public static final class Server {
        public static final Map<UUID, Boolean> ACTIVE_REFLECTION_MAP = new LinkedHashMap<>();

        public static void toggleReflection(UUID uuid) {
            if (ACTIVE_REFLECTION_MAP.containsKey(uuid)) {
                ACTIVE_REFLECTION_MAP.put(uuid, !ACTIVE_REFLECTION_MAP.get(uuid));
            } else {
                ACTIVE_REFLECTION_MAP.put(uuid, true);
            }
        }

        public static void reverseBloodflow(ServerPlayer player) {
            HitResult hitResult = player.pick(1, 1, false);
            List<LivingEntity> entityList = player.level().getEntitiesOfClass(LivingEntity.class,
                    new AABB(new BlockPos((int) hitResult.getLocation().x, (int) hitResult.getLocation().y, (int) hitResult.getLocation().z))
            );
            if (!entityList.isEmpty()) {
                LivingEntity livingEntity = entityList.get(0);
                if (livingEntity != player) {
                    livingEntity.hurt(new DamageSource(player.damageSources().damageTypes.getHolderOrThrow(DamageTypes.MAGIC)), livingEntity.getHealth());

                }
            }
        }

        public static Pair<Boolean, Float> handleHurt(Player player, DamageSource damageSource, float amount) {
            if (player instanceof LocalPlayer) {
                throw new RuntimeException("WTF? What are you doing? Don't invoke hurt in client!");
            }
            if (damageSource.is(DamageTypes.STARVE) || damageSource.is(DamageTypes.DROWN)) {
                return Pair.of(true, amount);
            }
            float computingPower = AbilitySystemServer.getPlayerComputingPower(player.getUUID());
            float needComputingPower = amount * 10;
            AcademyCraft.LOGGER.info(damageSource.toString() + damageSource.type());
            AcademyCraft.LOGGER.info(needComputingPower + " " + computingPower);
            if (computingPower > needComputingPower) {
                AbilitySystemServer.setPlayerComputingPower(player.getUUID(), computingPower - needComputingPower);
                Entity source = damageSource.getEntity();
                Entity directEntity = damageSource.getDirectEntity();
                if (source != player) {
                    if (damageSource.is(DamageTypes.ARROW) || damageSource.is(DamageTypes.THROWN)) {
                        if (directEntity != null) {
                            directEntity.setDeltaMovement(directEntity.getDeltaMovement().scale(10));
                            return Pair.of(false, 0f);
                        }
                    } else {
                        if (source != null) {
                            source.hurt(damageSource, amount);
                            return Pair.of(false, 0f);
                        }
                    }
                }
                return Pair.of(false, 0f);
            } else {
                AbilitySystemServer.setPlayerComputingPower(player.getUUID(), 0);
                return Pair.of(true, amount - (computingPower / 10));
            }
        }
    }
}