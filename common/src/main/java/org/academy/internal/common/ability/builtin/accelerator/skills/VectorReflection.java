package org.academy.internal.common.ability.builtin.accelerator.skills;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.academy.AcademyCraftClient;
import org.academy.api.client.config.SkillClientConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.Packets;
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
        NetworkSystemServer.registerC2SPacketHandler(Packets.C2S_TOGGLE_REFLECTION,
                (listener, packet) -> Server.toggleReflection(listener.player.getUUID())
        );
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE = SkillNames.VECTOR_REFLECTION + ".toggle";
        public static final VectorReflectionClientConfig CONFIG = new VectorReflectionClientConfig();

        public static void toggleReflection() {
            NetworkSystemClient.sendPacket(new C2SPacket(Packets.C2S_TOGGLE_REFLECTION));
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

        private static void applyReflection(Player player, DamageSource source, float reflectedDamage) {
            Entity sourceEntity = source.getEntity();
            Entity directEntity = source.getDirectEntity();

            if (source.is(DamageTypes.ARROW) || source.is(DamageTypes.THROWN)) {
                if (directEntity != null) {
                    directEntity.setDeltaMovement(directEntity.getDeltaMovement().scale(10));
                }
                return;
            }

            if (sourceEntity != null && sourceEntity != player) {
                sourceEntity.hurt(source, reflectedDamage);
            }
        }
    }
}