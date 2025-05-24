package org.academy.internal.common.ability.builtin.accelerator.skills;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
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
import org.academy.api.common.vanilla.ThreadType;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.internal.client.gui.screen.AbilityDeveloperScreen;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.ability.builtin.accelerator.Accelerator;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        AcademyCraftClientConfig.registerConfigActions(INSTANCE.name, new Client.BloodflowReverseClientConfigData());
        Client.CONFIG = AcademyCraftClient.CLIENT_CONFIG.getConfig(
                INSTANCE.name,
                Client.BloodflowReverseClientConfigData.class
        );
        if (Client.CONFIG == null) {
            Client.CONFIG = new Client.BloodflowReverseClientConfigData();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(INSTANCE.name, Client.CONFIG);
        }

        InputSystem.addKeyBinding(Client.KEY_NAME_ACTION, Client.CONFIG.getKeyBinding(Client.KEY_NAME_ACTION,
                        new InputSystem.InputPair(
                                InputSystem.InputType.KEYBOARD,
                                new InputSystem.KeyInfo(
                                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_R)),
                                        GLFW.GLFW_RELEASE,
                                        new LinkedHashSet<>(
                                                Set.of(GLFW.GLFW_MOD_ALT)
                                        )
                                )
                        )
                ), Client::reverseBloodflow
        );
    }

    @Override
    public void initServer(MinecraftServer server) {
        NetworkSystemServer.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static final AbilityDeveloperScreen.SkillInfo SKILL_INFO =
                AbilityDeveloperScreen.registerSkillInfo(Accelerator.INSTANCE, INSTANCE, List.of(VectorReflection.Client.SKILL_INFO),
                        TextureResources.TEXTURE_BLOODFLOW_REVERSE_ICON, 90, 50);
        public static final String KEY_NAME_ACTION = SkillNames.BLOODFLOW_REVERSE + "_action";
        public static BloodflowReverseClientConfigData CONFIG = new BloodflowReverseClientConfigData();

        public static void reverseBloodflow() {
            NetworkSystemClient.sendPacket(new C2SPacket(new ReverseBloodflowPacket()));
        }

        public static class BloodflowReverseClientConfigData implements IClientConfigActions<BloodflowReverseClientConfigData> {
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
            public @NotNull BloodflowReverseClientConfigData deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson) {
                return gson.fromJson(jsonElement, BloodflowReverseClientConfigData.class);
            }

            @Override
            public @NotNull JsonElement serialize(@NotNull BloodflowReverseClientConfigData configInstance, @NotNull Gson gson) {
                return gson.toJsonTree(configInstance);
            }

            @Override
            public @NotNull BloodflowReverseClientConfigData getDefaultConfig() {
                return new BloodflowReverseClientConfigData();
            }

            @Override
            public @NotNull Class<BloodflowReverseClientConfigData> getConfigClass() {
                return BloodflowReverseClientConfigData.class;
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