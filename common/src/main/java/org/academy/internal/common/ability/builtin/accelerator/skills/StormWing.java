package org.academy.internal.common.ability.builtin.accelerator.skills;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraftClient;
import org.academy.api.client.config.SkillClientConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.NetworkResourceLocations;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.internal.client.renderer.effect.StormWingEffectRenderer;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.world.entity.player.PlayerSyncSkillData;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.academy.internal.common.ability.builtin.accelerator.skills.VectorReflection.Client.KEY_NAME_TOGGLE;

public class StormWing extends Skill {
    public static final Skill INSTANCE = new StormWing();
    public static final String TAG_KEY = "activated_storm_wing";

    private StormWing() {
        super(SkillNames.STORM_WING, 4);
    }

    @Override
    public void initClient() {
        RendererManager.EFFECT_RENDERER_MAP.add(StormWingEffectRenderer.INSTANCE);
        AcademyCraftClient.CLIENT_CONFIG.getSkillClientConfig(INSTANCE.name, Client.CONFIG);
        InputSystem.addKeyBinding(Client.KEY_NAME, VectorReflection.Client.CONFIG.getKeyBinding(KEY_NAME_TOGGLE,
                new InputSystem.InputPair(
                        InputSystem.InputType.KEYBOARD,
                        new InputSystem.InputEvent(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_B)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>()
                        )
                )
        ), Client::toggle);
    }

    @Override
    public void initServer(MinecraftServer server) {
        NetworkSystemServer.registerC2SPacketHandler(NetworkResourceLocations.C2S_STORM_WING_TOGGLE, (listener, packet) -> Server.handleToggle(listener.player));
    }

    public static final class Client {
        public static final String KEY_NAME = INSTANCE.name + "_toggle";
        public static final StormWingClientConfig CONFIG = new StormWingClientConfig();

        public static void toggle() {
            NetworkSystemClient.sendPacket(new C2SPacket(NetworkResourceLocations.C2S_STORM_WING_TOGGLE));
        }

        public static final class StormWingClientConfig extends SkillClientConfig.SkillClientKeyBindingConfig {
            private StormWingClientConfig() {
            }
        }
    }

    public static final class Server {
        public static void handleToggle(ServerPlayer player) {
            SynchedEntityData synchedEntityData = player.getEntityData();
            CompoundTag compoundTag = synchedEntityData.get(PlayerSyncSkillData.SKILL_DATA);
            CompoundTag newTag = new CompoundTag();
            newTag.putBoolean(TAG_KEY, !compoundTag.getBoolean(TAG_KEY));
            synchedEntityData.set(PlayerSyncSkillData.SKILL_DATA, newTag);
        }
    }
}