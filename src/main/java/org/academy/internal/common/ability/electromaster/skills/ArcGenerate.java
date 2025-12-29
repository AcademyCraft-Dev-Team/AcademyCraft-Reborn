package org.academy.internal.common.ability.electromaster.skills;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.Branch;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.path.LinePath;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.entity.skill.ArcEffect;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ArcGenerate extends Skill {
    public static final String KEY_NAME_GENERATE = SkillNames.ARC_GENERATE + ".generate";
    public static final float BASE_DAMAGE = 2.0F;

    public ArcGenerate() {
        super(Builder.of(AbilityCategories.ELECTROMASTER.get()).level(AbilityLevel.LEVEL1));
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.ArcGenerateConfig.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(KEY_NAME_GENERATE, Client.CONFIG.getKeyBinding(KEY_NAME_GENERATE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_G)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT)))
                )
        ), Client::handler);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static ArcGenerateConfig CONFIG = new ArcGenerateConfig();

        public static void handler() {
            MisakaNetworkClient.sendPacket(GeneratePacket.INSTANCE);
        }

        public static class ArcGenerateConfig extends KeyBindingConfig {
            public static final class Action implements TypeHandler<ArcGenerateConfig> {
                public static final TypeHandler<ArcGenerateConfig> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public ArcGenerate.Client.ArcGenerateConfig getDefault() {
                    return new ArcGenerateConfig();
                }

                @Override
                public Class<ArcGenerateConfig> getTypeClass() {
                    return ArcGenerateConfig.class;
                }
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void handle(GeneratePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var level = player.level();
            var currentComputingPower = AbilitySystemServer.getPlayerAvailableCP(player.getUUID());
            if (currentComputingPower <= 10) return;

            var yawRad = (float) Math.toRadians(-player.getVisualRotationYInDegrees());
            var eyePos = player.getEyePosition();

            var playerOrientation = new Quaternionf().rotateY(yawRad);

            var look = new Vector3f(0, 0, 1).rotate(playerOrientation);
            var up = new Vector3f(0, 1, 0).rotate(playerOrientation);
            var right = new Vector3f(-1, 0, 0).rotate(playerOrientation);

            var handPos = eyePos
                    .add(new Vec3(right).scale(0.35))
                    .add(new Vec3(up).scale(-0.8))
                    .add(new Vec3(look).scale(0.35));

            var length = LevelUtil.getValidViewDistance(player, 10);
            var targetPos = eyePos.add(player.getLookAngle().scale(length));
            var trunkLength = (float) handPos.distanceTo(targetPos);

            var arc = new ArcEffect(level, 20);
            arc.setPos(handPos);

            List<Branch> branches = new ArrayList<>();
            var branchCount = 4 + MathUtil.RANDOM.nextInt(3);
            var maxAngleRad = Math.toRadians(10.0);

            for (var i = 0; i < branchCount; i++) {
                var progress = 0.2f + MathUtil.RANDOM.nextFloat() * 0.7f;
                var branchLength = trunkLength * (0.3f + MathUtil.RANDOM.nextFloat() * 0.2f);

                var phi = MathUtil.RANDOM.nextDouble() * maxAngleRad;

                var x = Mth.sin(phi);
                var y = Mth.sin(phi);
                var z = Mth.cos(phi);

                var localDir = new Vector3f(x, y, z).normalize().mul(branchLength);

                var childPath = new ArcPath(
                        new LinePath(new Vector3f(0, 0, 0), localDir),
                        List.of(
                                new JaggedModifier(1, 3, MathUtil.RANDOM.nextLong())
                        ),
                        2.0f,
                        List.of()
                );

                branches.add(new Branch(progress, childPath));
            }

            var rootPath = new ArcPath(
                    new LinePath(handPos.toVector3f(), targetPos.toVector3f()),
                    List.of(
                            new JaggedModifier(1, 4, MathUtil.RANDOM.nextLong())
                    ),
                    2,
                    branches
            );

            arc.setArcPath(rootPath);
            level.addFreshEntity(arc);
            arc.playSound(SoundEvents.ARC_WEAK.get());

            var radius = 0.25f;
            var src = player.damageSources().playerAttack(player);
            LevelUtil.attackEntitiesAlongPath(level, handPos, targetPos, radius, src, BASE_DAMAGE);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class GeneratePacket extends Packet<ServerGamePacketListenerImpl, GeneratePacket> {
        public static final GeneratePacket INSTANCE = new GeneratePacket();
        public static final StreamCodec<ByteBuf, GeneratePacket> CODEC = StreamCodec.unit(INSTANCE);

        private GeneratePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, GeneratePacket> getPacketType() {
            return PacketTypes.ARC_GENERATE_GENERATE.get();
        }
    }
}