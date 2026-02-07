package org.academy.internal.common.ability.meltdowner.skills;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.skill.HellFlareRay;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HellFlare extends Skill {

    public HellFlare() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL4)
                .iterationTicks(1)
                .maintenanceCost(0)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.HellFlareConfig.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_TEST, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_TEST,
                new InputSystem.InputPair(
                        InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_V)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>()
                        )
                )
        ), Client::handleKey);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.registerPacketListener(Server.class);
        NeoForge.EVENT_BUS.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TEST = "academy_meltdowner_hellflare_test";
        public static HellFlareConfig CONFIG = new HellFlareConfig();

        public static void handleKey() {
            MisakaNetworkClient.sendPacket(TogglePacket.INSTANCE);
        }

        public static class HellFlareConfig extends KeyBindingConfig {
            public static final class Action implements TypeHandler<HellFlareConfig> {
                public static final TypeHandler<HellFlareConfig> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public HellFlareConfig getDefault() {
                    return new HellFlareConfig();
                }

                @Override
                public Class<HellFlareConfig> getTypeClass() {
                    return HellFlareConfig.class;
                }
            }
        }
    }

    public static final class Server {
        private static final Map<UUID, HellFlareRay> activeRays = new ConcurrentHashMap<>();
        private static final Map<UUID, Integer> forcedPhases = new ConcurrentHashMap<>();
        private static final double MAX_RANGE = 32.0;
        private static final int PHASE_P1_END = 120;
        private static final int PHASE_P2_END = 240;

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var uuid = player.getUUID();
            var level = player.level();

            if (activeRays.containsKey(uuid)) {
                var ray = activeRays.remove(uuid);
                if (ray != null) {
                    ray.discard();
                }
                forcedPhases.remove(uuid);
                return;
            }

            var ray = new HellFlareRay(level, player);
            ray.setPhase(getAutoPhase(ray.tickCount));
            ray.clearServerControlledPhase();
            level.addFreshEntity(ray);
            activeRays.put(uuid, ray);
        }

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Pre event) {
            var iterator = activeRays.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                var ray = entry.getValue();
                var owner = ray.getOwner();
                if (!(owner instanceof ServerPlayer player) || !player.isAlive() || ray.isRemoved()) {
                    ray.discard();
                    iterator.remove();
                    forcedPhases.remove(entry.getKey());
                    continue;
                }

                applyPhaseControl(player, ray);
                updateTargeting(player, ray);
            }
        }

        public static void setPhaseControl(ServerPlayer player, int phase) {
            var clamped = Math.max(1, Math.min(3, phase));
            forcedPhases.put(player.getUUID(), clamped);
        }

        public static void clearPhaseControl(ServerPlayer player) {
            forcedPhases.remove(player.getUUID());
        }

        private static void applyPhaseControl(ServerPlayer player, HellFlareRay ray) {
            var forced = forcedPhases.get(player.getUUID());
            if (forced != null) {
                ray.setServerControlledPhase(forced);
                return;
            }
            ray.clearServerControlledPhase();
            ray.setPhase(getAutoPhase(ray.tickCount));
        }

        private static int getAutoPhase(int ticks) {
            if (ticks < PHASE_P1_END) {
                return 1;
            }
            if (ticks < PHASE_P2_END) {
                return 2;
            }
            return 3;
        }

        private static void updateTargeting(ServerPlayer player, HellFlareRay ray) {
            var currentTargetId = ray.getTargetId();
            LivingEntity currentTarget = null;

            if (currentTargetId != -1) {
                var entity = player.level().getEntity(currentTargetId);
                if (entity instanceof LivingEntity living) {
                    currentTarget = living;
                }
            }

            if (currentTarget != null) {
                if (isValidTarget(player, currentTarget)) {
                    updateRayToTarget(player, ray, currentTarget);
                    return;
                }
                ray.setTargetId(-1);
                ray.setBeamLength(0f);
            }

            searchNewTarget(player, ray);
        }

        private static boolean isValidTarget(ServerPlayer player, LivingEntity target) {
            if (target == null || !target.isAlive() || target.isSpectator()) {
                return false;
            }

            var eyePos = player.getEyePosition();
            var targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
            if (eyePos.distanceToSqr(targetCenter) > MAX_RANGE * MAX_RANGE) {
                return false;
            }

            var hitResult = player.level().clip(new net.minecraft.world.level.ClipContext(
                    eyePos,
                    targetCenter,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    player
            ));
            return hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
        }

        private static void searchNewTarget(ServerPlayer player, HellFlareRay ray) {
            var level = player.level();
            var searchBox = player.getBoundingBox().inflate(MAX_RANGE);

            var candidates = level.getEntitiesOfClass(LivingEntity.class, searchBox, e ->
                    e != player && e.isAlive() && !e.isSpectator()
            );

            var bestTarget = candidates.stream()
                    .filter(e -> isValidTarget(player, e))
                    .max(Comparator.comparingDouble(LivingEntity::getMaxHealth));

            if (bestTarget.isPresent()) {
                updateRayToTarget(player, ray, bestTarget.get());
                return;
            }
            ray.setTargetId(-1);
            ray.setBeamLength(0f);
        }

        private static void updateRayToTarget(ServerPlayer player, HellFlareRay ray, LivingEntity target) {
            var eyePos = player.getEyePosition();
            var targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
            var dist = (float) eyePos.distanceTo(targetCenter);

            ray.setTargetId(target.getId());
            ray.setBeamLength(dist);
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
            return PacketTypes.HELL_FLARE_ACTION.get();
        }
    }
}
