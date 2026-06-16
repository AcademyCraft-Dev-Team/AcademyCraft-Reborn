package org.academy.internal.common.ability.electromaster.skills.lv2;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.renderer.effect.EMFieldEffectWrapper;
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

public class MagnetManipulation extends Skill {
    private static final float PULL_RANGE = 16.0f;
    private static final float PULL_FORCE = 0.8f;

    public MagnetManipulation() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL2)
                .cpCost(30)
                .iterationTicks(10)
                .maxStacks(1)
                .dependsOn(Skills.ARC_GENERATE)
        );
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(EMFieldEffectWrapper.INSTANCE);
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_PULL_SELF, Client.CONFIG.getKeyBinding(Client.KEY_NAME_PULL_SELF,
                        new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_X)), GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT)))))
                , Client::onPullSelf);
        InputSystem.addKeyBinding(Client.KEY_NAME_PULL_TARGET, Client.CONFIG.getKeyBinding(Client.KEY_NAME_PULL_TARGET,
                        new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_X)), GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT)))))
                , Client::onPullTarget);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_PULL_SELF = SkillNames.MAGNET_MANIPULATION + "_pull_self";
        public static final String KEY_NAME_PULL_TARGET = SkillNames.MAGNET_MANIPULATION + "_pull_target";
        public static Config CONFIG = new Config();

        public static void onPullSelf() {
            MisakaNetworkClient.send(new PullPacket(false));
            var p = net.minecraft.client.Minecraft.getInstance().player;
            if (p == null) return;
            EMFieldEffectWrapper.INSTANCE.ensureActive();
            for (var i = 0; i < 4; i++) {
                var angle = i * Math.PI / 2;
                var dx = Math.cos(angle) * 2;
                var dz = Math.sin(angle) * 2;
                EMFieldEffectWrapper.INSTANCE.addFieldLine(
                        p.position(), p.position().add(dx, 0, dz),
                        0.2f, 0.6f, 1.0f, 0.05f, 0.6f, 0.3f);
            }
        }

        public static void onPullTarget() {
            MisakaNetworkClient.send(new PullPacket(true));
            var p = net.minecraft.client.Minecraft.getInstance().player;
            if (p == null) return;
            var look = p.getLookAngle().scale(16);
            EMFieldEffectWrapper.INSTANCE.ensureActive();
            EMFieldEffectWrapper.INSTANCE.addFieldLine(
                    p.getEyePosition(), p.getEyePosition().add(look),
                    0.2f, 0.6f, 1.0f, 0.08f, 0.8f, 0.2f);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public MagnetManipulation.Client.Config getDefault() {
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
        public static void handlePull(PullPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.MAGNET_MANIPULATION.get().executeActive(player, (_, _) -> {
                var level = player.level();
                var eyePos = player.getEyePosition();
                var lookDir = player.getLookAngle();
                var targetPos = eyePos.add(lookDir.scale(PULL_RANGE));

                // Check for iron-bearing entities
                var box = new net.minecraft.world.phys.AABB(
                        eyePos.add(-0.5, -0.5, -0.5),
                        targetPos.add(0.5, 0.5, 0.5));
                var entities = level.getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != player && e.isAlive() && isIronArmored(e));

                if (!entities.isEmpty() && packet.pullTarget()) {
                    var target = entities.getFirst();
                    var pullVec = player.position().subtract(target.position()).normalize().scale(PULL_FORCE);
                    target.setDeltaMovement(target.getDeltaMovement().add(pullVec));
                    target.hurtMarked = true;
                } else {
                    // Check if looking at iron block, pull self towards it
                    var hitResult = level.clip(new net.minecraft.world.level.ClipContext(
                            eyePos, targetPos,
                            net.minecraft.world.level.ClipContext.Block.OUTLINE,
                            net.minecraft.world.level.ClipContext.Fluid.NONE, player));
                    if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                        var blockPos = hitResult.getBlockPos();
                        var block = level.getBlockState(blockPos);
                        if (isIronBlock(block)) {
                            var pullVec = new Vec3(blockPos.getX() + 0.5 - player.getX(),
                                    blockPos.getY() + 0.5 - player.getY(),
                                    blockPos.getZ() + 0.5 - player.getZ()).normalize().scale(PULL_FORCE * 1.2f);
                            player.setDeltaMovement(player.getDeltaMovement().add(pullVec));
                            player.hurtMarked = true;
                        }
                    }
                }
            });
        }

        private static boolean isIronBlock(net.minecraft.world.level.block.state.BlockState state) {
            var block = state.getBlock();
            return block == Blocks.IRON_BLOCK || block == Blocks.IRON_BARS
                    || block == Blocks.IRON_DOOR || block == Blocks.IRON_TRAPDOOR
                    || block == Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE
                    || block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL
                    || block == Blocks.DAMAGED_ANVIL || block == Blocks.HOPPER;
        }

        private static boolean isIronArmored(LivingEntity entity) {
            return entity.getType() == EntityTypes.IRON_GOLEM;
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class PullPacket extends Packet<ServerGamePacketListenerImpl, PullPacket> {
        private final boolean pullTarget;
        public static final StreamCodec<ByteBuf, PullPacket> CODEC =
                net.minecraft.network.codec.ByteBufCodecs.BOOL.map(PullPacket::new, PullPacket::pullTarget);

        public PullPacket(boolean pullTarget) {
            this.pullTarget = pullTarget;
        }

        public boolean pullTarget() {
            return pullTarget;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, PullPacket> getPacketType() {
            return PacketTypes.MAGNET_MANIPULATION_PULL.get();
        }
    }
}
