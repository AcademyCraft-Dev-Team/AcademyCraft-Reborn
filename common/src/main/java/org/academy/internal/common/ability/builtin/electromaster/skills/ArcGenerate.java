package org.academy.internal.common.ability.builtin.electromaster.skills;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.api.client.config.ClientConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.ClassPacketHandler;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.PacketTarget;
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
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ArcGenerate extends Skill {
    public static final Skill INSTANCE = new ArcGenerate();
    public static final String KEY_NAME = SkillNames.ARC_GENERATE + ".generate";
    public static final float BASE_DAMAGE = 2.0F;

    static {
        NetworkSystem.registerPacketType(GeneratePacket.class);
    }

    private ArcGenerate() {
        super(SkillNames.ARC_GENERATE, 1);
    }

    @Override
    public void initClient() {
        AcademyCraftClient.CLIENT_CONFIG.getSkillClientConfig(INSTANCE.name, Client.CONFIG);
        InputSystem.addKeyBinding(KEY_NAME, Client.CONFIG.getKeyBinding(KEY_NAME,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_G)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT)))
                )
        ), Client::handler);
    }

    @Override
    public void initServer(MinecraftServer server) {
        NetworkSystem.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static final AbilityDeveloperScreen.SkillInfo SKILL_INFO =
                AbilityDeveloperScreen.registerSkillInfo(Electromaster.INSTANCE, INSTANCE, List.of(Railgun.Client.SKILL_INFO),
                        TextureResources.TEXTURE_ARC_GENERATE_ICON, 20, 70.25f);
        public static final ArcGenerateSKillConfig CONFIG = new ArcGenerateSKillConfig();

        public static void handler() {
            //   if (!ClientUtil.isScreenNull() || ClientUtil.lacksSkill(INSTANCE)) return;
            NetworkSystemClient.sendPacket(new C2SPacket(new GeneratePacket()));
        }

        public static final class ArcGenerateSKillConfig extends ClientConfig.KeyBindingConfig {
        }
    }

    public static final class Server {
        @ClassPacketHandler
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
            Vec3 handPos = playerPos.add(rightVec.scale(0.4)).add(0, 1.2, 0);
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