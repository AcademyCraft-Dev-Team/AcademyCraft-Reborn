package org.academy.internal.common.ability.accelerator.skills;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.AcademyCraftServer;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.PacketType;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.EmptyPacket;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.util.MathUtil;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class DirStrike extends Skill {
    public DirStrike() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL3)
                .dependsOn(Skills.VECTOR_REFLECTION)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.CLIENT_CONFIG.getConfig(key);
        if (Client.CONFIG == null) {
            Client.CONFIG = new Client.Config();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(key, Client.CONFIG);
        }

        InputSystem.addKeyBinding(Client.KEY_NAME, Client.CONFIG.getKeyBinding(Client.KEY_NAME, new InputSystem.InputPair(
                InputSystem.InputType.KEYBOARD,
                new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_R)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(
                                Set.of(
                                        GLFW.GLFW_MOD_ALT
                                )
                        )
                )
        )), Client::onAction);
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftServer.SERVER_NETWORK_MANAGER.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME = SkillNames.DIR_STRIKE + "_use";
        public static Config CONFIG = new Config();

        public static void onAction() {
            if (Minecraft.getInstance().player == null) return;
            AcademyCraftClient.sendPacket(new C2SPacket(new ActionPacket()));
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public @NotNull DirStrike.Client.Config getDefault() {
                    return new Config();
                }

                @Override
                public @NotNull Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void onAction(ActionPacket packet) {
            var serverPlayer = packet.getPacketListener().getPlayer();

            var level = serverPlayer.serverLevel();
            var chunkCache = level.getChunkSource();
            var lookDir = serverPlayer.getLookAngle();
            var horizontalLookDir = new Vec3(lookDir.x, 0, lookDir.z).normalize();

            var playerPos = serverPlayer.blockPosition();

            var range = 5;
            var width = 5;
            var verticalRange = 2;

            var affectedBlocks = new ArrayList<BlockPos>();
            var processedPositions = new HashSet<BlockPos>();

            level.playSound(null, playerPos, SoundEvents.GENERIC_BIG_FALL, SoundSource.PLAYERS, 0.7f, 0.9f);

            for (var yOffset = -1; yOffset <= verticalRange - 1; ++yOffset) {
                var targetY = playerPos.getY() + yOffset;
                for (var i = 1; i <= range; ++i) {
                    for (var j = -width / 2; j <= width / 2; ++j) {
                        var forwardOffset = horizontalLookDir.scale(i);
                        var sideOffset = horizontalLookDir.yRot((float) Math.toRadians(90)).scale(j);

                        var groundPos = BlockPos.containing(playerPos.getX() + forwardOffset.x + sideOffset.x,
                                targetY,
                                playerPos.getZ() + forwardOffset.z + sideOffset.z);

                        if (processedPositions.add(groundPos)) {
                            affectedBlocks.add(groundPos);
                        }
                    }
                }
            }

            for (BlockPos pos : affectedBlocks) {
                BlockState blockState = level.getBlockState(pos);
                if (!blockState.isAir() && !blockState.hasBlockEntity() && blockState.getDestroySpeed(level, pos) >= 0 && blockState.getFluidState().isEmpty()) {
                    SoundType soundType = blockState.getSoundType(level, pos, null);
                    level.playSound(null, pos, soundType.getBreakSound(), SoundSource.BLOCKS, soundType.getVolume() * 0.8f, soundType.getPitch() * 0.9f);

                    FallingBlockEntity fallingBlock = new FallingBlockEntity(EntityType.FALLING_BLOCK, level);
                    fallingBlock.disableDrop();
                    fallingBlock.blockState = blockState;
                    fallingBlock.blocksBuilding = true;
                    fallingBlock.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    fallingBlock.setStartPos(pos);
                    fallingBlock.dropItem = false;
                    fallingBlock.setHurtsEntities(0.0f, 0);
                    fallingBlock.time = 1;

                    Vec3 blockCenter = Vec3.atCenterOf(pos);
                    Vec3 playerCenter = serverPlayer.position();
                    Vec3 outwardDir = blockCenter.subtract(playerCenter).normalize();

                    double yVel = MathUtil.RANDOM.nextDouble(0.2, 0.3);
                    double outwardVel = 0.1 + level.random.nextDouble();

                    Vec3 velocity = new Vec3(outwardDir.x * outwardVel, yVel, outwardDir.z * outwardVel);

                    fallingBlock.setDeltaMovement(velocity);

                    level.addFreshEntity(fallingBlock);
                    chunkCache.broadcast(fallingBlock, new ClientboundSetEntityMotionPacket(fallingBlock));

                    level.sendParticles(
                            new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, blockState),
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            40,
                            0.4, 0.4, 0.4,
                            0.2
                    );
                }
            }

            Vec3 basePos = serverPlayer.position();
            var attackArea = new AABB(basePos, basePos.add(horizontalLookDir.scale(5))).inflate(1.0);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, attackArea, e ->
                    e != serverPlayer && e.isAlive());

            for (LivingEntity target : targets) {
                target.hurt(level.damageSources().playerAttack(serverPlayer), 6.0f);
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActionPacket extends EmptyPacket<ServerGamePacketListenerImpl> {
        public ActionPacket(ServerGamePacketListenerImpl listener) {
            super(listener);
        }

        public ActionPacket() {
            super(null);
        }

        @Override
        public @NotNull PacketType<ServerGamePacketListenerImpl, ? extends Packet<ServerGamePacketListenerImpl>> getPacketType() {
            return PacketTypes.DIR_STRIKE.get();
        }
    }
}