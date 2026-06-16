package org.academy.internal.common.ability.teleport.skills.lv1;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.Smoke;
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

public class MatterWarp extends Skill {
    public MatterWarp() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL1)
                .cpCost(20)
                .iterationTicks(8)
                .maxStacks(1)
        );
    }

    public float getDamage(int level) {
        if (level >= 3) return 4.0f;
        return 2.0f;
    }

    public int getMaxDistance(int level) {
        if (level >= 2) return 16;
        return 8;
    }

    @Override
    public int getIterationTicks(int skillLevel) {
        if (skillLevel >= 1) return 4;
        return super.getIterationTicks(skillLevel);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_V)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT)))
                )
        ), Client::onUse);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_USE = SkillNames.MATTER_WARP + "_use";
        public static Config CONFIG = new Config();

        public static void onUse() {
            MisakaNetworkClient.sendPacket(UsePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public MatterWarp.Client.Config getDefault() {
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
        public static void handle(UsePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var level = player.level();
            Skills.MATTER_WARP.get().executeActive(player, (ctx, actualCost) -> {
                var maxDist = Skills.MATTER_WARP.get().getMaxDistance(ctx.level());
                var distance = LevelUtil.getValidViewDistance(player, maxDist);
                var targetPos = player.getEyePosition().add(player.getLookAngle().scale(distance));
                var damage = Skills.MATTER_WARP.get().getDamage(ctx.level());

                var mainHandItem = player.getMainHandItem();
                var hasMetal = mainHandItem.is(Items.COPPER_INGOT)
                        || mainHandItem.is(Items.GOLD_NUGGET)
                        || mainHandItem.is(Items.IRON_NUGGET);

                var useItem = !hasMetal && !mainHandItem.isEmpty();
                if (useItem) {
                    damage = Math.max(1.0f, damage / 4.0f);
                }

                var targets = level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(targetPos.add(-1, -1, -1), targetPos.add(1, 1, 1)));
                if (!targets.isEmpty()) {
                    var target = targets.getFirst();
                    if (target != player) {
                        target.hurtServer(level, level.damageSources().magic(), damage);
                    }
                }

                var smoke = new Smoke(EntityTypes.SMOKE.get(), level);
                smoke.setPos(targetPos);
                level.addFreshEntity(smoke);
            });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class UsePacket extends Packet<ServerGamePacketListenerImpl, UsePacket> {
        public static final UsePacket INSTANCE = new UsePacket();
        public static final StreamCodec<ByteBuf, UsePacket> CODEC = StreamCodec.unit(INSTANCE);

        private UsePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, UsePacket> getPacketType() {
            return PacketTypes.MATTER_WARP_USE.get();
        }
    }
}
