package org.academy.internal.common.ability.builtin.accelerator.skills;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static org.academy.internal.common.ability.builtin.accelerator.skills.VectorReflection.Client.KEY_NAME_TOGGLE;

public class VectorReflection extends Skill {
    public static final Skill INSTANCE = new VectorReflection();

    private VectorReflection() {
        super(SkillNames.VECTOR_REFLECTION, 2);
    }

    @Override
    public void initClient() {
        AcademyCraftClient.CLIENT_CONFIG.getSkillClientConfig(INSTANCE.name, Client.CONFIG);
        InputSystem.addKeyBinding(KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(KEY_NAME_TOGGLE,
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
    }

    @Override
    public void initServer(MinecraftServer server) {
        NetworkSystemServer.registerC2SPacketHandler(NetworkResourceLocations.C2S_TOGGLE_REFLECTION_PACKET,
                (listener, packet) -> Server.toggleReflection(listener.player.getUUID())
        );
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE = SkillNames.VECTOR_REFLECTION + ".toggle";
        public static final SkillClientConfig.SkillClientKeyBindingConfig CONFIG = new VectorReflectionClientConfig();

        public static void toggleReflection() {
            NetworkSystemClient.sendPacket(new C2SPacket(NetworkResourceLocations.C2S_TOGGLE_REFLECTION_PACKET));
        }

        public static final class VectorReflectionClientConfig extends SkillClientConfig.SkillClientKeyBindingConfig {
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

        public static boolean shouldReflection(Player player, DamageSource damageSource) {
            if (player instanceof LocalPlayer) {
                return false;
            }
            if (player.isCreative() || player.isSpectator()) {
                return false;
            }
            final UUID uuid = player.getUUID();
            if (ACTIVE_REFLECTION_MAP.containsKey(uuid)) {
                return ACTIVE_REFLECTION_MAP.get(uuid);
            }
            return !damageSource.is(DamageTypes.STARVE) && !damageSource.is(DamageTypes.DROWN);
        }

        @SuppressWarnings("resource")
        public static Pair<Boolean, Float> handleHurt(Player player, DamageSource damageSource, float amount) {
            if (shouldReflection(player, damageSource)) {
                float needComputingPower = amount * 10;
                final float computingPower = AbilitySystemServer.getPlayerComputingPower(player.getUUID());
                AcademyCraft.LOGGER.info("{}{}", damageSource.toString(), damageSource.type());
                AcademyCraft.LOGGER.info("{} {}", needComputingPower, computingPower);
                if (computingPower > 0) {
                    player.level().playSound(null, player, AcademyCraftSoundEvents.VECTOR_REFLECTION, SoundSource.BLOCKS, 1, 1);
                }
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
            return Pair.of(true, amount);
        }
    }
}