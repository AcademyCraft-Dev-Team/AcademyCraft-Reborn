package org.academy.internal.common.ability.builtin.electromaster.skills;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftClientConfig;
import org.academy.AcademyCraftServer;
import org.academy.api.client.config.IClientConfigActions;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.common.ability.Skill;
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

import java.util.*;

public class ArcGenerate extends Skill {
    public static final Skill INSTANCE = new ArcGenerate();
    public static final String KEY_NAME_ACTION = SkillNames.ARC_GENERATE + ".generate_action";
    public static final float BASE_DAMAGE = 2.0F;

    private ArcGenerate() {
        super(SkillNames.ARC_GENERATE, 1);
    }

    @Override
    public void initClient() {
        AcademyCraftClientConfig.registerConfigActions(INSTANCE.name, new Client.ArcGenerateClientConfigData());
        Client.CONFIG = AcademyCraftClient.CLIENT_CONFIG.getConfig(
                INSTANCE.name,
                Client.ArcGenerateClientConfigData.class
        );
        if (Client.CONFIG == null) {
            Client.CONFIG = new Client.ArcGenerateClientConfigData();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(INSTANCE.name, Client.CONFIG);
        }

        InputSystem.addKeyBinding(KEY_NAME_ACTION, Client.CONFIG.getKeyBinding(KEY_NAME_ACTION,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_G)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT)))
                )
        ), Client::handler);
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftServer.NETWORK_SYSTEM_SERVER_INSTANCE.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static final AbilityDeveloperScreen.SkillInfo SKILL_INFO =
                AbilityDeveloperScreen.registerSkillInfo(Electromaster.INSTANCE, INSTANCE, List.of(Railgun.Client.SKILL_INFO),
                        TextureResources.TEXTURE_ARC_GENERATE_ICON, 20, 70.25f);
        public static ArcGenerateClientConfigData CONFIG = new ArcGenerateClientConfigData();

        public static void handler() {
            NetworkSystemClient.sendPacket(new C2SPacket(new GeneratePacket()));
        }

        public static class ArcGenerateClientConfigData implements IClientConfigActions<ArcGenerateClientConfigData> {
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
            public @NotNull ArcGenerateClientConfigData deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson) {
                return gson.fromJson(jsonElement, ArcGenerateClientConfigData.class);
            }

            @Override
            public @NotNull JsonElement serialize(@NotNull ArcGenerateClientConfigData configInstance, @NotNull Gson gson) {
                return gson.toJsonTree(configInstance);
            }

            @Override
            public @NotNull ArcGenerateClientConfigData getDefaultConfig() {
                return new ArcGenerateClientConfigData();
            }

            @Override
            public @NotNull Class<ArcGenerateClientConfigData> getConfigClass() {
                return ArcGenerateClientConfigData.class;
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