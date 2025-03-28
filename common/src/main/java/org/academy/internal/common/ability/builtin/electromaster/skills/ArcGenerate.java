package org.academy.internal.common.ability.builtin.electromaster.skills;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftClientConfig;
import org.academy.AcademyCraftServer;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.NetworkResourceLocations;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.academy.internal.common.world.entity.skill.Arc;
import org.academy.internal.server.world.level.storage.AcademyCraftWorldData;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ArcGenerate extends Skill {
    public static final Skill INSTANCE = new ArcGenerate();
    public static final String KEY_NAME = "arc_generate.generate";
    public static AcademyCraftClientConfig.InputPair KEY;
    public static final float BASE_DAMAGE = 2.0F;

    private ArcGenerate() {
        super("arc_generate", 1);
    }

    @Override
    public void initClient() {
        KEY = AcademyCraftClient.clientConfig.getKey(KEY_NAME,
                new AcademyCraftClientConfig.InputPair(AcademyCraftClientConfig.InputType.KEYBOARD, new InputSystem.InputEvent(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_G)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT))
                )));
        InputSystem.addKeyBinding(KEY_NAME, KEY, Client::handler);
    }

    @Override
    public void initServer(MinecraftServer server) {
        try {
            NetworkSystemServer.registerC2SPacketHandler(
                    NetworkResourceLocations.C2S_ARC_GENERATE_PACKET,
                    Server.class.getMethod("handle", ServerPlayer.class, ServerLevel.class),
                    objects -> Server.handle((ServerPlayer) objects[0], (ServerLevel) objects[1])
            );
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static final class Client {
        public static void handler() {
            if (ClientUtil.isScreenNull()) {
                NetworkSystemClient.sendPacket(new C2SPacket(NetworkResourceLocations.C2S_ARC_GENERATE_PACKET));
            }
        }
    }

    public static final class Server {
        public static void handle(final @NotNull ServerPlayer player, final @NotNull ServerLevel level) {
            AcademyCraftWorldData.Player data = AcademyCraftServer.academyCraftWorldData.getPlayers().get(player.getUUID());
            float currentComputingPower = data.getComputingPower();
            if (currentComputingPower > 10) {
                AbilitySystemServer.setPlayerComputingPower(player.getUUID(), currentComputingPower - 10);
            } else {
                return;
            }

            Arc arc = new Arc(level, player);
            level.addFreshEntity(arc);
            arc.playSound(AcademyCraftSoundEvents.ARC_WEAK);
            Vec3 lookVec = arc.getLookAngle();
            Vec3 start = arc.position();
            int steps = 10;

            Set<LivingEntity> detectedEntities = new HashSet<>();

            for (int i = 0; i < steps; i++) {
                Vec3 segmentStart = start.add(lookVec.scale(i));
                Vec3 segmentEnd = start.add(lookVec.scale(i + 1));

                AABB box = new AABB(segmentStart, segmentEnd);
                List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, box);

                detectedEntities.addAll(entities);
            }

            detectedEntities.forEach(entity -> {
                if (entity == player) {
                    return;
                }
                float damage = BASE_DAMAGE * AbilitySystemServer.getDamageMultiplier();
                entity.hurt(player.damageSources().playerAttack(player), damage);
            });
        }
    }
}