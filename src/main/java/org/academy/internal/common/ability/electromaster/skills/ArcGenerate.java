package org.academy.internal.common.ability.electromaster.skills;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.Branch;
import org.academy.api.common.arc.modifier.DisplacementModifier;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.modifier.TaperModifier;
import org.academy.api.common.arc.path.LinePath;
import org.academy.api.common.arc.property.AttributeCurve;
import org.academy.api.common.arc.property.Knot;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.ArcEffect;
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
    public void initServer(MinecraftServer server) {
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
            var currentComputingPower = AbilitySystemServer.getPlayerComputingPower(player.getUUID());
            if (currentComputingPower <= 10) return;

            var lookVec = player.getLookAngle();
            var playerPos = player.position();
            var eyePos = player.getEyePosition();
            var rightVec = lookVec.cross(new Vec3(0, 1, 0)).normalize();
            var right = (player.getMainArm() == HumanoidArm.RIGHT);
            var handPos = playerPos.add(rightVec.scale(right ? 0.4 : -0.4f)).add(0, 1.2, 0).add(lookVec.scale(0.5));

            var length = LevelUtil.getValidViewDistance(player, 10);
            var targetPos = eyePos.add(lookVec.scale(length));
            var trunkLength = (float) handPos.distanceTo(targetPos);

            var arc = new ArcEffect(EntityTypes.ARC_EFFECT.get(), level);
            arc.setPos(handPos);

            List<Branch> branches = new ArrayList<>();
            int branchCount = 3 + MathUtil.RANDOM.nextInt(3);
            double maxAngleRad = Math.toRadians(20.0);

            for (int i = 0; i < branchCount; i++) {
                float progress = 0.2f + MathUtil.RANDOM.nextFloat() * 0.7f;
                float branchLength = trunkLength * (0.3f + MathUtil.RANDOM.nextFloat() * 0.4f);

                double phi = MathUtil.RANDOM.nextDouble() * maxAngleRad;
                double theta = MathUtil.RANDOM.nextDouble() * 2.0 * Math.PI;

                float x = (float) (Math.sin(phi) * Math.cos(theta));
                float y = (float) (Math.sin(phi) * Math.sin(theta));
                float z = (float) Math.cos(phi);

                Vector3f localDir = new Vector3f(x, y, z).normalize().mul(branchLength);

                ArcPath childPath = new ArcPath(
                        new LinePath(new Vector3f(0, 0, 0), localDir),
                        List.of(
                                new JaggedModifier(0.6f, 3, MathUtil.RANDOM.nextLong()),
                                new TaperModifier(
                                        new AttributeCurve(List.of(new Knot(0, 1.0f), new Knot(1, 0.1f))),
                                        0.5f
                                )
                        ),
                        2.0f,
                        List.of()
                );

                branches.add(new Branch(progress, childPath));
            }

            ArcPath rootPath = new ArcPath(
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
            var damage = BASE_DAMAGE * AbilitySystemServer.getDamageMultiplier();
            var src = player.damageSources().playerAttack(player);
            LevelUtil.attackEntitiesAlongPath(level, handPos, targetPos, radius, src, damage);
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