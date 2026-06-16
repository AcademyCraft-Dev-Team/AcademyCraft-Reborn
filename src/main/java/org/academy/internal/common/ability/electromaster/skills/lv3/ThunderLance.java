package org.academy.internal.common.ability.electromaster.skills.lv3;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.path.LinePath;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.skill.ArcEffect;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;

public class ThunderLance extends Skill {
    private static final int CHARGE_TICKS = 20;
    private static final float BASE_DAMAGE = 8.0f;

    public ThunderLance() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL3)
                .cpCost(60)
                .iterationTicks(20)
                .maxStacks(2)
                .dependsOn(Skills.ARC_GENERATE)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_T)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_CONTROL)))
                )
        ), Client::onUse);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_USE = SkillNames.THUNDER_LANCE + "_use";
        public static Config CONFIG = new Config();

        public static void onUse() {
            MisakaNetworkClient.sendPacket(StartPacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {}
                @Override public ThunderLance.Client.Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        private static final Map<Player, Context> CONTEXT_MAP = createContextMap();

        @SubscribePacket
        public static void handle(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (CONTEXT_MAP.containsKey(player)) return;
            Skills.THUNDER_LANCE.get().executeActive(player, (ctx, actualCost) -> {
                var context = new Context(player);
                CONTEXT_MAP.put(player, context);
                AbilitySystemServer.registerContext(context);
            });
        }
    }

    public static final class Context extends ServerContext {
        private int ticks;
        private final ArcEffect chargeArc;
        private boolean ended;

        private Context(ServerPlayer player) {
            super(player);
            chargeArc = new ArcEffect(player.level(), CHARGE_TICKS + 10);
            chargeArc.setPos(player.getEyePosition());
            player.level().addFreshEntity(chargeArc);
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            ticks++;
            if (player.hasDisconnected() || !player.isAlive()) {
                end();
                return;
            }

            var eyePos = player.getEyePosition();
            var lookDir = player.getLookAngle();

            if (ticks <= CHARGE_TICKS) {
                var branchCount = 3 + MathUtil.RANDOM.nextInt(3);
                var arcs = new ArrayList<ArcPath>();
                for (var i = 0; i < branchCount; i++) {
                    var offset = eyePos.add(
                            MathUtil.RANDOM.nextDouble(-1, 1),
                            MathUtil.RANDOM.nextDouble(-1, 1),
                            MathUtil.RANDOM.nextDouble(-1, 1));
                    arcs.add(new ArcPath(new LinePath(offset.toVector3f(), eyePos.toVector3f()),
                            List.of(new JaggedModifier(1, 2, MathUtil.RANDOM.nextLong())), 1.0f, List.of()));
                }
                chargeArc.setArcPaths(arcs);
                chargeArc.setPos(eyePos);
            } else if (ticks == CHARGE_TICKS + 1) {
                var range = 20f;
                var targetPos = eyePos.add(lookDir.scale(range));
                var distance = LevelUtil.getValidViewDistance(player, range);
                targetPos = eyePos.add(lookDir.scale(distance));

                var fireArc = new ArcEffect(player.level(), 20);
                fireArc.setPos(eyePos);
                var rootPath = new ArcPath(new LinePath(eyePos.toVector3f(), targetPos.toVector3f()),
                        List.of(new JaggedModifier(1, 4, MathUtil.RANDOM.nextLong())), 3.0f, List.of());
                fireArc.setArcPath(rootPath);
                player.level().addFreshEntity(fireArc);

                var src = player.damageSources().playerAttack(player);
                LevelUtil.attackEntitiesAlongPath(player.level(), eyePos, targetPos, 0.5f, src, BASE_DAMAGE);
                end();
            }
        }

        private void end() {
            if (ended) return;
            ended = true;
            Server.CONTEXT_MAP.remove(player);
            if (!chargeArc.isRemoved()) chargeArc.discard();
            unregister();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StartPacket extends Packet<ServerGamePacketListenerImpl, StartPacket> {
        public static final StartPacket INSTANCE = new StartPacket();
        public static final StreamCodec<ByteBuf, StartPacket> CODEC = StreamCodec.unit(INSTANCE);
        private StartPacket() {}
        @Override public PacketType<ServerGamePacketListenerImpl, StartPacket> getPacketType() {
            return PacketTypes.THUNDER_LANCE_START.get();
        }
    }
}
