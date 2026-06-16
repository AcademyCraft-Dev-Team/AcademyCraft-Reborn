package org.academy.internal.common.ability.meltdowner.skills.lv1;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
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
import org.academy.internal.common.skilldata.TraceRingData;
import org.academy.internal.common.world.entity.skill.LightOrb;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;

public class TraceRing extends Skill {
    private static final int BASE_DURATION_TICKS = 640;
    private static final int EXTENDED_DURATION_TICKS = 1280;
    private static final int ORB_COUNT = 6;
    private static final float INNER_RADIUS = 2.0f;
    private static final float OUTER_RADIUS = 4.0f;
    private static final float DAMAGE = 2.0f;

    public TraceRing() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL1)
                .passive()
                .maintenanceCost(60)
                .iterationTicks(10)
                .withCustomData(TraceRingData.ID, TraceRingData.class, player -> new TraceRingData())
        );
    }

    @Override
    public int getIterationTicks(int skillLevel) {
        if (skillLevel >= 1) return 6;
        return super.getIterationTicks(skillLevel);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                new InputSystem.InputPair(
                        InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_C)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>()
                        )
                )
        ), Client::onToggle);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE = SkillNames.TRACE_RING + "_toggle";
        public static Config CONFIG = new Config();

        public static void onToggle() {
            MisakaNetworkClient.sendPacket(TogglePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public TraceRing.Client.Config getDefault() {
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
        private static final Map<Player, Context> CONTEXT_MAP = createContextMap();

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.TRACE_RING.get();
            skill.toggle(player);

            if (!skill.isEnabled(player)) {
                endContext(player);
                return;
            }
            if (CONTEXT_MAP.containsKey(player)) return;
            var context = new Context(player);
            CONTEXT_MAP.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        private static void endContext(ServerPlayer player) {
            var context = CONTEXT_MAP.get(player);
            if (context != null) context.end();
        }
    }

    public static final class Context extends ServerContext {
        private final List<LightOrb> innerOrbs = new ArrayList<>();
        private final List<LightOrb> outerOrbs = new ArrayList<>();
        private int durationTicks;
        private boolean ended = false;

        private Context(ServerPlayer player) {
            super(player);
            var level = level();
            var skill = Skills.TRACE_RING.get();
            var skillLevel = skill.getLevel(player);

            durationTicks = (skillLevel >= 2) ? EXTENDED_DURATION_TICKS : BASE_DURATION_TICKS;

            for (var i = 0; i < ORB_COUNT; i++) {
                var orb = new LightOrb(level, -1, 0.3f, null);
                level.addFreshEntity(orb);
                innerOrbs.add(orb);
            }

            if (skillLevel >= 3) {
                for (var i = 0; i < ORB_COUNT; i++) {
                    var orb = new LightOrb(level, -1, 0.25f, null);
                    level.addFreshEntity(orb);
                    outerOrbs.add(orb);
                }
            }
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            var skill = Skills.TRACE_RING.get();
            if (!skill.isEnabled(player) || !player.isAlive() || player.hasDisconnected()) {
                end();
                return;
            }

            durationTicks--;
            if (durationTicks <= 0) {
                skill.toggle(player);
                end();
                return;
            }

            var center = player.position().add(0, player.getBbHeight() / 2.0, 0);
            var time = (float) player.level().getGameTime();
            updateOrbit(center, time, INNER_RADIUS, innerOrbs, 0);

            if (!outerOrbs.isEmpty()) {
                updateOrbit(center, time, OUTER_RADIUS, outerOrbs, (float) Math.PI / ORB_COUNT);
            }

            if (player.level() instanceof ServerLevel serverLevel) {
                checkOrbCollisions(serverLevel, innerOrbs);
                checkOrbCollisions(serverLevel, outerOrbs);
            }
        }

        private void updateOrbit(Vec3 center, float time, float radius, List<LightOrb> orbs, float phaseOffset) {
            var speed = 0.05f;
            for (var i = 0; i < orbs.size(); i++) {
                var angle = time * speed + (float) (2 * Math.PI * i / orbs.size()) + phaseOffset;
                var x = center.x + radius * Math.cos(angle);
                var z = center.z + radius * Math.sin(angle);
                var y = center.y + 0.3 * Math.sin(angle * 0.5);
                orbs.get(i).setPos(x, y, z);
            }
        }

        private void checkOrbCollisions(ServerLevel serverLevel, List<LightOrb> orbs) {
            for (var orb : orbs) {
                var box = orb.getBoundingBox().inflate(0.5);
                var targets = serverLevel.getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != player && e.isAlive());
                for (var target : targets) {
                    target.hurtServer(serverLevel, player.damageSources().magic(), DAMAGE);
                }
            }
        }

        private void end() {
            if (ended) return;
            ended = true;
            Server.CONTEXT_MAP.remove(player);
            for (var orb : innerOrbs) {
                if (!orb.isRemoved()) orb.discard();
            }
            for (var orb : outerOrbs) {
                if (!orb.isRemoved()) orb.discard();
            }
            var skill = Skills.TRACE_RING.get();
            skill.<TraceRingData>getRuntimeData(player).ifPresent(TraceRingData::resetTicks);
            unregister();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);

        private TogglePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.TRACE_RING_TOGGLE.get();
        }
    }
}
