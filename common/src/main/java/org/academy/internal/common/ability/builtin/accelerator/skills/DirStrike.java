package org.academy.internal.common.ability.builtin.accelerator.skills;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkManagerClient;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.config.IConfigAction;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.EmptyPacket;
import org.academy.api.common.util.MathUtil;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.client.gui.screen.AbilityDeveloperScreen;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.ability.builtin.accelerator.Accelerator;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class DirStrike extends Skill {
    public static final Skill INSTANCE = new DirStrike();

    private DirStrike() {
        super(SkillNames.DIR_STRIKE, 3, List.of(VectorReflection.INSTANCE));
    }

    @Override
    public void initClient() {
        AcademyCraftConfig.registerConfigActions(INSTANCE.name, Client.DirStrikeClientConfig.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.CLIENT_CONFIG.getConfig(INSTANCE.name);
        if (Client.CONFIG == null) {
            Client.CONFIG = new Client.DirStrikeClientConfig();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(INSTANCE.name, Client.CONFIG);
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
        AcademyCraftServer.NETWORK_SYSTEM_SERVER_INSTANCE.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO =
                AbilityDeveloperScreen.registerSkillInfo(Accelerator.INSTANCE, INSTANCE, List.of(VectorReflection.Client.SKILL_INFO),
                        TextureResources.TEXTURE_DIR_STRIKE_ICON, 100, 110);
        public static final String KEY_NAME = SkillNames.DIR_STRIKE + "_use";
        public static DirStrikeClientConfig CONFIG = new DirStrikeClientConfig();

        public static void onAction() {
            if (Minecraft.getInstance().player == null) return;
            NetworkManagerClient.sendPacket(new C2SPacket(new ActionPacket()));
        }

        public static class DirStrikeClientConfig {
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

            public static final class Action implements IConfigAction<DirStrikeClientConfig> {
                public static final IConfigAction<DirStrikeClientConfig> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public @NotNull DirStrike.Client.DirStrikeClientConfig deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson) {
                    return gson.fromJson(jsonElement, DirStrikeClientConfig.class);
                }

                @Override
                public @NotNull JsonElement serialize(@NotNull DirStrike.Client.DirStrikeClientConfig configInstance, @NotNull Gson gson) {
                    return gson.toJsonTree(configInstance);
                }

                @Override
                public @NotNull DirStrike.Client.DirStrikeClientConfig getDefaultConfig() {
                    return new DirStrikeClientConfig();
                }

                @Override
                public @NotNull Class<DirStrikeClientConfig> getConfigClass() {
                    return DirStrikeClientConfig.class;
                }
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void onAction(ActionPacket packet) {
            ServerPlayer serverPlayer = packet.packetListenerSupplier.get().getPlayer();

            ServerLevel level = serverPlayer.serverLevel();
            ServerChunkCache chunkCache = level.getChunkSource();
            Vec3 lookDir = serverPlayer.getLookAngle();
            Vec3 horizontalLookDir = new Vec3(lookDir.x, 0, lookDir.z).normalize();

            BlockPos playerPos = serverPlayer.blockPosition();

            int range = 5;
            int width = 5;
            int verticalRange = 2;

            List<BlockPos> affectedBlocks = new ArrayList<>();
            Set<BlockPos> processedPositions = new HashSet<>();

            level.playSound(null, playerPos, SoundEvents.GENERIC_BIG_FALL, SoundSource.PLAYERS, 0.7f, 0.9f);

            for (int yOffset = -1; yOffset <= verticalRange - 1; ++yOffset) {
                int targetY = playerPos.getY() + yOffset;
                for (int i = 1; i <= range; ++i) {
                    for (int j = -width / 2; j <= width / 2; ++j) {
                        Vec3 forwardOffset = horizontalLookDir.scale(i);
                        Vec3 sideOffset = horizontalLookDir.yRot((float) Math.toRadians(90)).scale(j);

                        BlockPos groundPos = BlockPos.containing(playerPos.getX() + forwardOffset.x + sideOffset.x,
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
                    SoundType soundType = blockState.getSoundType();
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
    }
}