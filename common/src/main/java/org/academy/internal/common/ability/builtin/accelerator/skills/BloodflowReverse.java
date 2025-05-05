package org.academy.internal.common.ability.builtin.accelerator.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import org.academy.AcademyCraftClient;
import org.academy.api.client.config.SkillClientConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.Packets;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.academy.internal.common.ability.builtin.accelerator.skills.BloodflowReverse.Client.KEY_NAME;

public class BloodflowReverse extends Skill {
    public static final Skill INSTANCE = new BloodflowReverse();

    private BloodflowReverse() {
        super(SkillNames.BLOODFLOW_REVERSE, 2);
    }

    @Override
    public void initClient() {
        AcademyCraftClient.CLIENT_CONFIG.getSkillClientConfig(INSTANCE.name, Client.CONFIG);
        InputSystem.addKeyBinding(KEY_NAME, Client.CONFIG.getKeyBinding(KEY_NAME,
                        new InputSystem.InputPair(
                                InputSystem.InputType.KEYBOARD,
                                new InputSystem.KeyInfo(
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
        NetworkSystemServer.registerC2SPacketHandler(Packets.C2S_REVERSE_BLOODFLOW,
                (listener, packet) -> Server.reverseBloodflow(listener.player)
        );
    }

    public static final class Client {
        public static final String KEY_NAME = "bloodflow_reverse";
        public static final BloodflowReverseClientConfig CONFIG = new BloodflowReverseClientConfig();

        public static void reverseBloodflow() {
            NetworkSystemClient.sendPacket(new C2SPacket(Packets.C2S_REVERSE_BLOODFLOW));
        }

        public static final class BloodflowReverseClientConfig extends SkillClientConfig.KeyBindingConfig {
            private BloodflowReverseClientConfig() {
            }
        }
    }

    public static final class Server {
        @SuppressWarnings("resource")
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
    }
}