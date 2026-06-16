package org.academy.internal.common.ability.meltdowner.skills;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
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
import org.academy.api.client.renderer.RendererManager;
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
import org.academy.internal.client.renderer.effect.AuraEffectWrapper;
import org.academy.internal.client.renderer.effect.ParticleEffectWrapper;
import org.academy.internal.common.world.entity.skill.HellFlareRay;
import org.academy.internal.common.world.entity.skill.LightOrb;
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
                .cpCost(600)
                .iterationTicks(30)
                .maintenanceCost(10)
                .withCustomData(HellFlareData.ID, HellFlareData.class, player -> new HellFlareData())
        );
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(AuraEffectWrapper.INSTANCE);
        RendererManager.registerEffectRenderer(ParticleEffectWrapper.INSTANCE);
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.HellFlareConfig.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_TEST, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_TEST,
                new InputSystem.InputPair(
                        InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_N)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT))
                        )
                )
        ), Client::handleKey);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TEST = "academy_meltdowner_hellflare_test";
        public static HellFlareConfig CONFIG = new HellFlareConfig();

        public static void handleKey() {
            if (!org.academy.api.client.ability.AbilitySystemClient.isSkillLearned(Skills.HELL_FLARE.get()))
                return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
            var p = net.minecraft.client.Minecraft.getInstance().player;
            if (p == null) return;
            AuraEffectWrapper.INSTANCE.triggerSphere(
                    (float) p.getX(), (float) p.getY() + 2.5f, (float) p.getZ(),
                    1.0f, 1.0f, 0.4f, 0.1f, 0.6f, 0.5f, 0.1f, 0.0f, 0.0f, 8.0f);
            var emitter = ParticleEffectWrapper.INSTANCE.createEmitter(
                    (float) p.getX(), (float) p.getY() + 2.0f, (float) p.getZ());
            emitter.setColor(1.0f, 0.5f, 0.1f);
            emitter.setEmissionRate(0);
            emitter.burst(20);
            emitter.setLifetime(1.0f, 0.5f);
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
        private static final int DAMAGE_INTERVAL = 30;
        private static final float[] PHASE_DAMAGE = {2.0f, 6.0f, 12.0f};
        private static final int[] PHASE_COLORS = {0xff6600, 0xffaa00, 0xffffff};

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
            private final LightOrb chargeOrb;
            private int lastTargetId = -1;
            private int lastDamageTick = 0;
            private boolean ended = false;

            private Context(ServerPlayer player) {
                super(player);
                ray = new HellFlareRay(level(), player);
                level().addFreshEntity(ray);

                chargeOrb = new LightOrb(level(), -1, 1.5f, null);
                chargeOrb.setPos(player.getX(), player.getY() + 2.5, player.getZ());
                chargeOrb.setColor(1.0f, 0.4f, 0.0f);
                level().addFreshEntity(chargeOrb);

                var data = getData(player);
                if (data == null) return;
                data.setLockTicks(0);
                data.setPhase(1);
                data.setMeltStacks(0);
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

                chargeOrb.setPos(player.getX(), player.getY() + 2.5, player.getZ());
                var phase = data.getPhase();
                var colorHex = PHASE_COLORS[phase - 1];
                chargeOrb.setColor(
                        ((colorHex >> 16) & 0xff) / 255f,
                        ((colorHex >> 8) & 0xff) / 255f,
                        (colorHex & 0xff) / 255f
                );
                chargeOrb.setScale(1.5f + phase * 0.3f);

                lastDamageTick++;
                if (lastDamageTick < DAMAGE_INTERVAL) return;
                lastDamageTick = 0;

                var targetId = ray.getTargetId();
                if (targetId == -1) return;

                var entity = player.level().getEntity(targetId);
                if (!(entity instanceof LivingEntity target) || !target.isAlive()) return;

                var baseDamage = PHASE_DAMAGE[phase - 1];
                var meltStacks = data.getMeltStacks();
                var damageMultiplier = 1.0f + meltStacks * 0.1f;
                var finalDamage = baseDamage * damageMultiplier;

                target.hurtServer((ServerLevel) player.level(),
                        player.damageSources().indirectMagic(player, player), finalDamage);

                if (phase == 3) {
                    data.setMeltStacks(meltStacks + 1);
                }
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
                    data.reset();
                }

                if (!ray.isRemoved()) {
                    ray.discard();
                }
                if (!chargeOrb.isRemoved()) {
                    chargeOrb.discard();
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
