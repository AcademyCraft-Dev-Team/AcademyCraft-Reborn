package org.academy.internal.common.ability.builtin.electromaster.skills;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.AcademyCraftServer;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkManagerClient;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.config.IConfigAction;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.EmptyPacket;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.gui.screen.AbilityDeveloperScreen;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.ability.builtin.electromaster.Electromaster;
import org.academy.internal.common.sounds.AcademyCraftSoundEvents;
import org.academy.internal.common.world.entity.skill.Arc;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ArcGenerate extends Skill {
    public static final Skill INSTANCE = new ArcGenerate();
    public static final String KEY_NAME_GENERATE = SkillNames.ARC_GENERATE + ".generate";
    public static final float BASE_DAMAGE = 2.0F;

    private ArcGenerate() {
        super(SkillNames.ARC_GENERATE, 1);
    }

    @Override
    public void initClient() {
        AcademyCraftConfig.registerConfigActions(INSTANCE.name, Client.ArcGenerateConfig.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.CLIENT_CONFIG.getConfig(INSTANCE.name);
        if (Client.CONFIG == null) {
            Client.CONFIG = new Client.ArcGenerateConfig();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(INSTANCE.name, Client.CONFIG);
        }

        InputSystem.addKeyBinding(KEY_NAME_GENERATE, Client.CONFIG.getKeyBinding(KEY_NAME_GENERATE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_G)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT)))
                )
        ), Client::handler);
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftServer.SERVER_NETWORK_MANAGER.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO =
                AbilityDeveloperScreen.registerSkillInfo(Electromaster.INSTANCE, INSTANCE, List.of(Railgun.Client.SKILL_INFO),
                        TextureResources.TEXTURE_ARC_GENERATE_ICON, 20, 70.25f);
        public static ArcGenerateConfig CONFIG = new ArcGenerateConfig();

        public static void handler() {
            NetworkManagerClient.sendPacket(new C2SPacket(new GeneratePacket()));
        }

        public static class ArcGenerateConfig extends KeyBindingConfig {
            public static final class Action implements IConfigAction<ArcGenerateConfig> {
                public static final IConfigAction<ArcGenerateConfig> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public @NotNull ArcGenerate.Client.ArcGenerateConfig deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson) {
                    return gson.fromJson(jsonElement, ArcGenerateConfig.class);
                }

                @Override
                public @NotNull JsonElement serialize(@NotNull ArcGenerate.Client.ArcGenerateConfig configInstance, @NotNull Gson gson) {
                    return gson.toJsonTree(configInstance);
                }

                @Override
                public @NotNull ArcGenerate.Client.ArcGenerateConfig getDefaultConfig() {
                    return new ArcGenerateConfig();
                }

                @Override
                public @NotNull Class<ArcGenerateConfig> getConfigClass() {
                    return ArcGenerateConfig.class;
                }
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void handle(GeneratePacket packet) {
            ServerPlayer player = packet.packetListenerSupplier.get().getPlayer();
            ServerLevel level = player.serverLevel();
            float currentComputingPower = AbilitySystemServer.getPlayerComputingPower(player.getUUID());
            if (currentComputingPower <= 10) return;
            AbilitySystemServer.setPlayerComputingPower(player.getUUID(), currentComputingPower - 10);

            Vec3 lookVec = player.getLookAngle();
            Vec3 playerPos = player.position();
            Vec3 eyePos = player.getEyePosition();
            Vec3 rightVec = lookVec.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 handPos = playerPos.add(rightVec.scale(0.4)).add(0, 1.2, 0).add(lookVec.scale(0.5));
            Vec3 targetPos = eyePos.add(lookVec.scale(10));
            Arc arc = new Arc(level, handPos, targetPos);

            double length = LevelUtil.getValidViewDistance(arc, 10);
            arc.setLength((float) length);
            targetPos = eyePos.add(lookVec.scale(length));

            level.addFreshEntity(arc);
            arc.playSound(AcademyCraftSoundEvents.ARC_WEAK);

            float radius = 0.125f;
            float damage = BASE_DAMAGE * AbilitySystemServer.getDamageMultiplier();
            DamageSource src = player.damageSources().playerAttack(player);
            LevelUtil.attackEntitiesAlongPath(level, handPos, targetPos, radius, src, damage);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class GeneratePacket extends EmptyPacket<ServerGamePacketListenerImpl> {
    }
}