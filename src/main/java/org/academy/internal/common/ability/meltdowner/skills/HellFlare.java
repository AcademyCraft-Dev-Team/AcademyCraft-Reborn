package org.academy.internal.common.ability.meltdowner.skills;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
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
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.skilldata.HellFlareData;
import org.academy.internal.common.world.entity.skill.HellFlareRay;
import org.jspecify.annotations.Nullable;
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

public class HellFlare extends Skill {

    public HellFlare() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL4)
                .iterationTicks(1)
                .maintenanceCost(0)
                .withCustomData(HellFlareData.ID, HellFlareData.class, player -> new HellFlareData())
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
        private static final Map<Player, Context> CONTEXT_MAP = createContextMap();
        private static final double MAX_RANGE = 32.0;
        private static final int PHASE_P1_END = 120;
        private static final int PHASE_P2_END = 240;

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.HELL_FLARE.get();
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
            if (context == null) return;
            context.end();
        }

        private static @Nullable HellFlareData getData(ServerPlayer player) {
            return Skills.HELL_FLARE.get().<HellFlareData>getRuntimeData(player).orElse(null);
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
            if (target == null || !target.isAlive() || target.isSpectator()) return false;

            var eyePos = player.getEyePosition();
            var targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
            if (eyePos.distanceToSqr(targetCenter) > MAX_RANGE * MAX_RANGE) return false;

            var hitResult = player.level().clip(new ClipContext(
                    eyePos,
                    targetCenter,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));
            return hitResult.getType() == HitResult.Type.MISS;
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

        public static final class Context extends ServerContext {
            private final HellFlareRay ray;
            private int lastTargetId = -1;
            private boolean ended = false;

            private Context(ServerPlayer player) {
                super(player);
                ray = new HellFlareRay(level(), player);
                level().addFreshEntity(ray);

                var data = getData(player);
                if (data == null) return;
                data.setLockTicks(0);
                data.setPhase(1);
                ray.setPhase(1);
            }

            @SubscribeEvent
            public void onTick(ServerTickEvent.Pre event) {
                var skill = Skills.HELL_FLARE.get();
                if (!skill.isEnabled(player)) {
                    end();
                    return;
                }

                if (!player.isAlive() || player.hasDisconnected() || ray.isRemoved()) {
                    skill.toggle(player);
                    end();
                    return;
                }

                var data = getData(player);
                if (data == null) {
                    skill.toggle(player);
                    end();
                    return;
                }

                updateTargeting(player, ray);
                updatePhaseByTarget(data);
                ray.setPhase(data.getPhase());
            }

            private void updatePhaseByTarget(HellFlareData data) {
                var currentTargetId = ray.getTargetId();

                if (currentTargetId == -1) {
                    lastTargetId = -1;
                    data.setLockTicks(0);
                    data.setPhase(1);
                    return;
                }

                if (currentTargetId != lastTargetId) {
                    lastTargetId = currentTargetId;
                    data.setLockTicks(0);
                    data.setPhase(1);
                    return;
                }

                var lockTicks = data.getLockTicks() + 1;
                data.setLockTicks(lockTicks);

                if (lockTicks < PHASE_P1_END) {
                    data.setPhase(1);
                    return;
                }

                if (lockTicks < PHASE_P2_END) {
                    data.setPhase(2);
                    return;
                }

                data.setPhase(3);
            }

            private void end() {
                if (ended) return;
                ended = true;
                CONTEXT_MAP.remove(player);

                var data = getData(player);
                if (data != null) {
                    data.setLockTicks(0);
                    data.setPhase(1);
                }

                if (!ray.isRemoved()) {
                    ray.discard();
                }
                unregister();
            }
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
