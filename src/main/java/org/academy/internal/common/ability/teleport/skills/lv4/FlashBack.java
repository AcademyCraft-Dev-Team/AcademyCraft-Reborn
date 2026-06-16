package org.academy.internal.common.ability.teleport.skills.lv4;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Pose;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;
import java.util.*;

public class FlashBack extends Skill {
    public FlashBack() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL4)
                .passive()
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.SELF_TELEPORT)
                .dependsOn(Skills.CLIP_THROUGH)
        );
    }

    @Override public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY, Client.CONFIG.getKeyBinding(Client.KEY,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_Q)), GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT)))))
        , Client::onToggle);
    }
    @Override public void initServer(MinecraftServerContext c) { MisakaNetworkServer.NETWORK_MANAGER.register(Server.class); }

    public static final class Client {
        public static final String KEY = SkillNames.FLASH_BACK + "_toggle";
        public static Config CONFIG = new Config();
        public static void onToggle() { MisakaNetworkClient.send(TogglePacket.INSTANCE); }
        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {} @Override public FlashBack.Client.Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        @SubscribePacket public static void handleToggle(TogglePacket p) { Skills.FLASH_BACK.get().toggle(p.getPacketListener().getPlayer()); }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer player)) return;
            var skill = Skills.FLASH_BACK.get();
            if (!skill.isEnabled(player) || !(player.level() instanceof ServerLevel)) return;
            var src = e.getSource(); if (src.getEntity() == null) return;
            var attacker = src.getEntity().position();
            var away = player.position().subtract(attacker).normalize().scale(3);
            var target = player.position().add(away);
            player.teleportTo(target.x, target.y, target.z);
            player.resetFallDistance();
            e.setCanceled(true);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);
        private TogglePacket() {} @Override public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() { return PacketTypes.FLASH_BACK_TOGGLE.get(); }
    }
}
