package org.academy.internal.common.ability.electromaster.skills.lv4;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
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

import java.util.LinkedHashSet;
import java.util.Set;

public class LightningStorm extends Skill {
    private static final int STRIKE_COUNT = 21;
    private static final float RADIUS = 8.0f;
    private static final float DAMAGE = 8.0f;

    public LightningStorm() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL4)
                .cpCost(80)
                .iterationTicks(30)
                .maxStacks(1)
                .dependsOn(Skills.LIGHTNING_NOVA)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                        new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_L)), GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT)))))
                , Client::onUse);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_USE = SkillNames.LIGHTNING_STORM + "_use";
        public static Config CONFIG = new Config();

        public static void onUse() {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            var hitResult = mc.player.pick(50, 1.0f, false);
            var targetPos = hitResult.getLocation();
            MisakaNetworkClient.send(new ActivatePacket(targetPos));
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public LightningStorm.Client.Config getDefault() {
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
        public static void handle(ActivatePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.LIGHTNING_STORM.get().executeActive(player, (_, _) -> AbilitySystemServer.registerContext(new Context(player, packet.getTargetPos())));
        }
    }

    public static final class Context extends ServerContext {
        private final net.minecraft.world.phys.Vec3 center;
        private int strikesLeft = STRIKE_COUNT;
        private int cooldown;
        private boolean ended;

        private Context(ServerPlayer player, net.minecraft.world.phys.Vec3 center) {
            super(player);
            this.center = center;
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            if (player.hasDisconnected() || !player.isAlive() || strikesLeft <= 0) {
                end();
                return;
            }

            cooldown--;
            if (cooldown > 0) return;
            cooldown = 3;

            strikesLeft--;
            var r = (float) Math.sqrt(Math.random()) * RADIUS;
            var theta = Math.random() * 2 * Math.PI;
            var strikeX = center.x + r * Math.cos(theta);
            var strikeZ = center.z + r * Math.sin(theta);

            if (level() instanceof ServerLevel serverLevel) {
                var strikePos = new BlockPos((int) strikeX, (int) center.y, (int) strikeZ);
                var topPos = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, strikePos);
                var entity = new LightningBolt(
                        EntityTypes.LIGHTNING_BOLT, serverLevel);
                entity.setPos(topPos.getX(), topPos.getY(), topPos.getZ());
                serverLevel.addFreshEntity(entity);

                var box = new net.minecraft.world.phys.AABB(strikePos).inflate(3);
                var targets = serverLevel.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive());
                for (var target : targets) {
                    target.hurtServer(serverLevel, player.damageSources().lightningBolt(), DAMAGE);
                }
            }
        }

        private void end() {
            if (ended) return;
            ended = true;
            unregister();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActivatePacket extends Packet<ServerGamePacketListenerImpl, ActivatePacket> {
        private static final StreamCodec<ByteBuf, net.minecraft.world.phys.Vec3> VEC3_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, net.minecraft.world.phys.Vec3::x,
                ByteBufCodecs.DOUBLE, net.minecraft.world.phys.Vec3::y,
                ByteBufCodecs.DOUBLE, net.minecraft.world.phys.Vec3::z,
                net.minecraft.world.phys.Vec3::new);
        public static final StreamCodec<ByteBuf, ActivatePacket> CODEC = VEC3_CODEC.map(ActivatePacket::new, ActivatePacket::getTargetPos);
        private final net.minecraft.world.phys.Vec3 targetPos;

        public ActivatePacket(net.minecraft.world.phys.Vec3 targetPos) {
            this.targetPos = targetPos;
        }

        public net.minecraft.world.phys.Vec3 getTargetPos() {
            return targetPos;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ActivatePacket> getPacketType() {
            return PacketTypes.LIGHTNING_STORM_ACTIVATE.get();
        }
    }
}
