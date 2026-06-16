package org.academy.internal.common.ability.accelerator.skills.lv1;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.renderer.effect.VectorFieldEffectWrapper;
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

import java.util.LinkedHashSet;
import java.util.Set;

public class FlowControl extends Skill {
    public FlowControl() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL1)
                .cpCost(75)
                .iterationTicks(10)
                .maxStacks(1)
        );
    }

    @Override
    public float getCpCost(int skillLevel) {
        if (skillLevel >= 1) return 55;
        return super.getCpCost(skillLevel);
    }

    public float getRange(int level) {
        if (level >= 2) return 12.0f;
        return 8.0f;
    }

    public float getForce(int level) {
        if (level >= 3) return 3.0f;
        return 1.5f;
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(VectorFieldEffectWrapper.INSTANCE);
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                new InputSystem.InputPair(
                        InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_V)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>()
                        )
                )
        ), Client::onAction);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_USE = SkillNames.FLOW_CONTROL + "_use";
        public static Config CONFIG = new Config();

        public static void onAction() {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;
            var pull = mc.player.isShiftKeyDown();
            MisakaNetworkClient.send(new ActionPacket(pull));
            var p = mc.player;
            VectorFieldEffectWrapper.INSTANCE.trigger(
                    (float) p.getX(), (float) p.getY(), (float) p.getZ(),
                    8, 8, 0.5f, 0.0f, 0.8f, 1.0f, 3.0f);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public FlowControl.Client.Config getDefault() {
                    return new Config();
                }

                @Override
                public Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void handle(ActionPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var level = player.level();
            Skills.FLOW_CONTROL.get().executeActive(player, (ctx, actualCost) -> {
                var skill = Skills.FLOW_CONTROL.get();
                var pull = packet.isPull();
                var skillLevel = ctx.level();
                var range = skill.getRange(skillLevel);
                var effectiveRange = pull ? range / 2.0f : range;
                var force = skill.getForce(skillLevel);

                var lookDir = player.getLookAngle();
                var coneDotThreshold = Math.cos(Math.toRadians(30));

                var searchBox = player.getBoundingBox().inflate(effectiveRange);
                var targets = level.getEntities(player, searchBox,
                        e -> e.isAlive() && e != player && !e.isSpectator());

                for (var target : targets) {
                    var toTarget = target.position().subtract(player.position());
                    var distance = toTarget.length();
                    if (distance > effectiveRange || distance < 0.1) continue;

                    var direction = toTarget.normalize();
                    var dot = lookDir.dot(direction);
                    if (dot < coneDotThreshold) continue;

                    var velocity = direction.scale(force * (pull ? -1 : 1));
                    velocity = velocity.scale(1.0 - (distance / effectiveRange) * 0.5);
                    target.setDeltaMovement(velocity);
                    target.hurtMarked = true;
                }
            });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActionPacket extends Packet<ServerGamePacketListenerImpl, ActionPacket> {
        public static final StreamCodec<ByteBuf, ActionPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ActionPacket::isPull,
                ActionPacket::new
        );

        private final boolean pull;

        public ActionPacket(boolean pull) {
            this.pull = pull;
        }

        public boolean isPull() {
            return pull;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ActionPacket> getPacketType() {
            return PacketTypes.FLOW_CONTROL_ACTION.get();
        }
    }
}
