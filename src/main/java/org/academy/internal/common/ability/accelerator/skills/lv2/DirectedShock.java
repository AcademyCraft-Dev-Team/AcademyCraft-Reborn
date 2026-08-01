package org.academy.internal.common.ability.accelerator.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
public class DirectedShock extends Skill {
    public DirectedShock() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL1)
                .cpCost(50)
                .iterationTicks(4)
                .maxStacks(4)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
        );
    }

    public float getDamage(int level) {
        if (level >= 1) return 8.0f;
        return 6.0f;
    }

    public float getConeAngle(int level) {
        if (level >= 2) return 90.0f;
        return 60.0f;
    }

    public float getConeRange(int level) {
        if (level >= 3) return 4.0f;
        return 3.0f;
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_PRESS, Client.CONFIG.getKeyBinding(Client.KEY_NAME_PRESS,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R, InputConstants.PRESS, InputConstants.MOD_SHIFT)
        ), ctx -> Client.onChargeStart());
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R, InputConstants.RELEASE, InputConstants.MOD_SHIFT)
        ), ctx -> Client.onUse());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(Skills.DIRECTED_SHOCK.get(), List.of(), R.textures.ability.accelerator.skill.directed_shock.icon, 16, 45)
        );
        public static final String KEY_NAME_PRESS = SkillNames.DIRECTED_SHOCK + "_press";
        public static final String KEY_NAME_USE = SkillNames.DIRECTED_SHOCK + "_use";
        public static Config CONFIG = new Config();
        private static long chargeStartTime;

        public static void onChargeStart() {
            chargeStartTime = System.nanoTime();
        }

        public static void onUse() {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            var elapsedMs = (System.nanoTime() - chargeStartTime) / 1_000_000f;
            var chargeRatio = Math.clamp(elapsedMs / 2000f, 0.1f, 1.0f);
            MisakaNetworkClient.send(new ActionPacket(chargeRatio));
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public DirectedShock.Client.Config getDefault() {
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
            Skills.DIRECTED_SHOCK.get().executeActive(player, (ctx, actualCost) -> {
                var skill = Skills.DIRECTED_SHOCK.get();
                var skillLevel = ctx.level();
                var damage = skill.getDamage(skillLevel);
                var coneAngle = skill.getConeAngle(skillLevel);
                var coneRange = skill.getConeRange(skillLevel);

                var eyePos = player.getEyePosition();
                var lookDir = player.getLookAngle();
                var coneDotThreshold = Math.cos(Math.toRadians(coneAngle));

                var searchBox = player.getBoundingBox().expandTowards(lookDir.scale(coneRange)).inflate(1);
                var targets = player.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                        e -> e != player && e.isAlive() && !e.isSpectator());

                for (var target : targets) {
                    var toTarget = target.position().subtract(eyePos);
                    var distance = toTarget.length();
                    if (distance > coneRange || distance < 0.1) continue;
                    var direction = toTarget.normalize();
                    if (lookDir.dot(direction) < coneDotThreshold) continue;

                    var src = player.damageSources().playerAttack(player);
                    target.hurtServer(player.level(), src, damage);
                    target.setDeltaMovement(direction.scale(2.0));
                    target.hurtMarked = true;
                }
            });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActionPacket extends Packet<ServerGamePacketListenerImpl, ActionPacket> {
        public static final StreamCodec<ByteBuf, ActionPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, ActionPacket::getChargeRatio,
                ActionPacket::new
        );
        private final float chargeRatio;

        @SuppressWarnings("unused")
        public ActionPacket(float chargeRatio) {
            this.chargeRatio = chargeRatio;
        }

        public float getChargeRatio() {
            return chargeRatio;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ActionPacket> getPacketType() {
            return PacketTypes.DIRECTED_SHOCK_ACTION.get();
        }
    }
}
