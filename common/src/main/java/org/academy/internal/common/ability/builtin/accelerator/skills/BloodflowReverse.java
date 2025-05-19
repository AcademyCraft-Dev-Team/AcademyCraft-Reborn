package org.academy.internal.common.ability.builtin.accelerator.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import org.academy.AcademyCraftClient;
import org.academy.api.client.config.ClientConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.EmptyPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.client.gui.screen.AbilityDeveloperScreen;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.ability.builtin.accelerator.Accelerator;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.academy.internal.common.ability.builtin.accelerator.skills.BloodflowReverse.Client.KEY_NAME;

public class BloodflowReverse extends Skill {
    public static final Skill INSTANCE = new BloodflowReverse();

    static {
        NetworkSystem.registerPacketType(ReverseBloodflowPacket.class);
    }

    private BloodflowReverse() {
        super(SkillNames.BLOODFLOW_REVERSE, 2, List.of(VectorReflection.INSTANCE));
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
        NetworkSystem.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static final AbilityDeveloperScreen.SkillInfo SKILL_INFO =
                AbilityDeveloperScreen.registerSkillInfo(Accelerator.INSTANCE, INSTANCE, List.of(VectorReflection.Client.SKILL_INFO),
                        TextureResources.TEXTURE_BLOODFLOW_REVERSE_ICON, 90, 50);
        public static final String KEY_NAME = "bloodflow_reverse";
        public static final BloodflowReverseClientConfig CONFIG = new BloodflowReverseClientConfig();

        public static void reverseBloodflow() {
            NetworkSystemClient.sendPacket(new C2SPacket(new ReverseBloodflowPacket()));
        }

        public static final class BloodflowReverseClientConfig extends ClientConfig.KeyBindingConfig {
            private BloodflowReverseClientConfig() {
            }
        }
    }

    public static final class Server {
        @SuppressWarnings("resource")
        @SubscribePacket
        public static void reverseBloodflow(ReverseBloodflowPacket packet) {
            ServerPlayer player = packet.packetListenerSupplier.get().getPlayer();
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

    @PacketTarget(ThreadType.SERVER)
    public static final class ReverseBloodflowPacket extends EmptyPacket<ServerGamePacketListenerImpl> {
    }
}