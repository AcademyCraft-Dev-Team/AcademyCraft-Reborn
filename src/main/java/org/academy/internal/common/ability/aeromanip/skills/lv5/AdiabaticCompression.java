package org.academy.internal.common.ability.aeromanip.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.ProficiencyPolicy;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv5.BloodflowReverse;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.AeromanipVfx;
import org.academy.internal.common.ability.aeromanip.skills.lv4.VortexPull;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.server.ability.AeromanipResourceManager;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Maintained aimed compression field with exact movement suppression and unbounded damage stacks. */
public final class AdiabaticCompression extends Skill {
    static final double BASE_RADIUS = 6.0;
    static final double MILESTONE_TWO_RADIUS = 8.0;
    static final double MOVEMENT_FACTOR = 0.05;
    static final int DEBUFF_DURATION_TICKS = 60;
    static final int DAMAGE_INTERVAL_TICKS = 10;
    static final float BASE_DAMAGE_PER_STACK = 0.5f;
    private static final Identifier MOVEMENT_SLOW_ID =
            AcademyCraft.academy("adiabatic_compression_movement");
    private static final Identifier JUMP_SLOW_ID =
            AcademyCraft.academy("adiabatic_compression_jump");
    private static final Map<UUID, SlowState> SLOWED_TARGETS = new HashMap<>();
    private static int serverTicks;

    public AdiabaticCompression() {
        super(Builder.of(AbilityCategories.AEROMANIP.get())
                .damage()
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(12)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.VORTEX_PULL)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5)));
    }

    static double radiusForMilestone(int milestone) {
        return milestone >= 2 ? MILESTONE_TWO_RADIUS : BASE_RADIUS;
    }

    static double movementModifierAmount() {
        return MOVEMENT_FACTOR - 1.0;
    }

    static int nextStackCount(int current) {
        return current >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, current) + 1;
    }

    static float damageForStacks(int stacks, float damagePerStack, int milestone) {
        var milestoneMultiplier = milestone >= 3 ? 1.25 : 1.0;
        var damage = Math.max(0, stacks) * Math.max(0.0, damagePerStack) * milestoneMultiplier;
        return (float) Math.min(Float.MAX_VALUE, damage);
    }

    static boolean contains(Vec3 center, Vec3 target, double radius) {
        return center.distanceToSqr(target) <= radius * radius;
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        var binding = Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_CAST,
                InputSystem.combo(
                        InputSystem.InputType.KEYBOARD,
                        InputConstants.KEY_Y,
                        InputSystem.ANY_ACTION,
                        InputConstants.MOD_ALT));
        if (binding.action() != InputSystem.ANY_ACTION) {
            binding = new InputSystem.KeyCombination(
                    binding.type(), binding.keys(), InputSystem.ANY_ACTION, binding.modifiers(),
                    binding.availableWhenScreen(), binding.unbound());
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_CAST, binding);
            AcademyCraftClient.Config.INSTANCE.save();
        }
        InputSystem.addMaintainedKeyBinding(
                Client.KEY_NAME_CAST, binding, _ -> Client.start(), _ -> Client.stop());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO =
                AbilitySystemClient.addSkillInfo(
                        AbilityCategories.AEROMANIP.get(),
                        new AbilitySystemClient.SkillInfo(
                                Skills.ADIABATIC_COMPRESSION.get(),
                                List.of(VortexPull.Client.SKILL_INFO),
                                R.textures.adiabatic_compression_icon,
                                150,
                                168));
        public static final String KEY_NAME_CAST = SkillNames.ADIABATIC_COMPRESSION + "_cast";
        public static Config CONFIG = new Config();
        private static boolean maintaining;

        private Client() {
        }

        private static void start() {
            if (maintaining
                    || !AbilitySystemClient.canUseSkill(Skills.ADIABATIC_COMPRESSION.get())) return;
            maintaining = true;
            MisakaNetworkClient.send(StartPacket.INSTANCE);
        }

        private static void stop() {
            if (!maintaining) return;
            maintaining = false;
            MisakaNetworkClient.send(StopPacket.INSTANCE);
        }

        public static final class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final Action INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Config getDefault() {
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
        private static final Map<ServerPlayer, Context> ACTIVE = new WeakHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handle(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.ADIABATIC_COMPRESSION.get();
            if (ACTIVE.containsKey(player) || !skill.isEnabled(player)) return;
            var context = new Context(player);
            ACTIVE.put(player, context);
            AbilitySystemServer.registerContext(context);
            skill.reportTrigger(player);
        }

        @SubscribePacket
        public static void handle(StopPacket packet) {
            var context = ACTIVE.get(packet.getPacketListener().getPlayer());
            if (context != null) context.end();
        }

        private static final class Context extends ServerContext {
            private final ResourceKey<Level> dimension;
            private final AeromanipResourceManager.UsageLease usageLease;
            private final Map<UUID, DamageStack> damageStacks = new HashMap<>();
            private int activeTicks;
            private boolean ended;

            private Context(ServerPlayer player) {
                super(player);
                dimension = player.level().dimension();
                usageLease = AbilitySystemServer.getSystem(player)
                        .getAeromanipResourceManager().beginUse(player);
            }

            private void end() {
                if (ended) return;
                ended = true;
                unregister();
            }

            @SubscribeEvent
            public void onTick(ServerTickEvent.Pre event) {
                var skill = Skills.ADIABATIC_COMPRESSION.get();
                if (ended || player.hasDisconnected() || !player.isAlive()
                        || !player.level().dimension().equals(dimension) || !skill.isEnabled(player)) {
                    end();
                    return;
                }
                activeTicks++;
                var costInterval = Math.max(1, Math.round(AeromanipConfig.skillFloat(
                        player, SkillNames.ADIABATIC_COMPRESSION,
                        "compressedAirIntervalTicks", 10.0f)));
                if ((activeTicks - 1) % costInterval == 0 && !skill.executeContinuousWithResource(
                        player,
                        _ -> skill.getCpCost(player)
                                * AeromanipConfig.cpMultiplier(
                                player, SkillNames.ADIABATIC_COMPRESSION),
                        _ -> Math.max(0.0f, AeromanipConfig.skillFloat(
                                player, SkillNames.ADIABATIC_COMPRESSION,
                                "compressedAirPerInterval", 8.0f)),
                        (_, _) -> { },
                        true)) {
                    end();
                    return;
                }
                var milestone = skill.getEffectiveProficiencyMilestone(player);
                var center = resolveTargetPoint(player);
                affectArea(skill, center, milestone);
                skill.reportActivity(player, true);
            }

            private Vec3 resolveTargetPoint(ServerPlayer owner) {
                var eye = owner.getEyePosition();
                var look = owner.getLookAngle();
                var target = BloodflowReverse.findTarget(
                        owner,
                        eye,
                        look,
                        0.0
                );
                if (target != null) return target.getBoundingBox().getCenter();
                var distance = BloodflowReverse.targetRange(owner, 0.0);
                var end = eye.add(look.normalize().scale(distance));
                var hit = owner.level().clip(new ClipContext(
                        eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
                return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
            }

            private void affectArea(AdiabaticCompression skill, Vec3 center, int milestone) {
                if (!(player.level() instanceof ServerLevel level)) return;
                var radius = radiusForMilestone(milestone)
                        * AeromanipConfig.rangeMultiplier(
                        player, SkillNames.ADIABATIC_COMPRESSION);
                var box = new AABB(
                        center.subtract(radius, radius, radius),
                        center.add(radius, radius, radius));
                var targets = level.getEntitiesOfClass(
                        LivingEntity.class,
                        box,
                        target -> canAffect(target) && contains(
                                center, target.getBoundingBox().getCenter(), radius));
                var handled = 0;
                var cap = ProficiencyPolicy.server(player).maxBonusEntitiesPerTick();
                for (var target : targets) {
                    if (handled++ >= cap) break;
                    applySlow(target);
                    if (activeTicks % DAMAGE_INTERVAL_TICKS == 0) {
                        damage(skill, level, target, milestone);
                    }
                }
                damageStacks.entrySet().removeIf(
                        entry -> activeTicks - entry.getValue().lastSeenTick() > DEBUFF_DURATION_TICKS);
                spawnVisual(level, center, radius);
            }

            private boolean canAffect(LivingEntity target) {
                if (target == player || !target.isAlive() || target.isRemoved()) return false;
                if (target instanceof TamableAnimal animal && animal.isOwnedBy(player)) return false;
                return AeromanipTargeting.canAffectNegatively(player, target);
            }

            private void damage(
                    AdiabaticCompression skill,
                    ServerLevel level,
                    LivingEntity target,
                    int milestone
            ) {
                var previous = damageStacks.get(target.getUUID());
                var previousStacks = previous == null
                        || activeTicks - previous.lastSeenTick() > DEBUFF_DURATION_TICKS
                        ? 0
                        : previous.stacks();
                var stacks = nextStackCount(previousStacks);
                damageStacks.put(target.getUUID(), new DamageStack(stacks, activeTicks));
                var baseDamage = AeromanipConfig.skillFloat(
                        player, SkillNames.ADIABATIC_COMPRESSION,
                        "damagePerStack", BASE_DAMAGE_PER_STACK);
                var system = AbilitySystemServer.getSystem(player);
                var damage = damageForStacks(stacks, baseDamage, milestone)
                        * AeromanipConfig.damageMultiplier(
                        player, SkillNames.ADIABATIC_COMPRESSION)
                        * system.getPlayerAbilityPowerMultiplier(player.getUUID())
                        * system.getPlayerDamageMultiplier(player.getUUID());
                target.invulnerableTime = 0;
                target.hurtServer(
                        level,
                        SkillDamageSource.of(
                                player, skill, DamageTypes.ADIABATIC_COMPRESSION),
                        damage);
            }

            private void spawnVisual(ServerLevel level, Vec3 center, double radius) {
                if (activeTicks % 8 != 0) return;
                AeromanipVfx.field(level, center, Math.max(0.5, radius * 0.82));
                if (activeTicks % 16 == 0) {
                    AeromanipVfx.vortex(level, center, Math.max(0.45, radius * 0.58));
                }
            }

            @Override
            protected void onUnregistered() {
                usageLease.close();
                ACTIVE.remove(player, this);
            }
        }
    }

    private static void applySlow(LivingEntity target) {
        syncSlowModifier(target.getAttribute(Attributes.MOVEMENT_SPEED), MOVEMENT_SLOW_ID);
        syncSlowModifier(target.getAttribute(Attributes.JUMP_STRENGTH), JUMP_SLOW_ID);
        SLOWED_TARGETS.put(
                target.getUUID(),
                new SlowState(new WeakReference<>(target), serverTicks + DEBUFF_DURATION_TICKS));
    }

    private static void syncSlowModifier(AttributeInstance attribute, Identifier id) {
        if (attribute == null) return;
        var current = attribute.getModifier(id);
        var amount = movementModifierAmount();
        if (current != null
                && current.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                && Math.abs(current.amount() - amount) < 1.0e-9) {
            return;
        }
        attribute.addOrUpdateTransientModifier(new AttributeModifier(
                id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void removeSlow(LivingEntity target) {
        var movement = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) movement.removeModifier(MOVEMENT_SLOW_ID);
        var jump = target.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) jump.removeModifier(JUMP_SLOW_ID);
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            serverTicks++;
            var iterator = SLOWED_TARGETS.values().iterator();
            while (iterator.hasNext()) {
                var state = iterator.next();
                var target = state.target().get();
                if (target != null && !target.isRemoved() && serverTicks < state.expiresAt()) continue;
                if (target != null) removeSlow(target);
                iterator.remove();
            }
        }
    }

    private record DamageStack(int stacks, int lastSeenTick) {
    }

    private record SlowState(WeakReference<LivingEntity> target, int expiresAt) {
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StartPacket
            extends Packet<ServerGamePacketListenerImpl, StartPacket> {
        public static final StartPacket INSTANCE = new StartPacket();
        public static final StreamCodec<ByteBuf, StartPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StartPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StartPacket> getPacketType() {
            return PacketTypes.ADIABATIC_COMPRESSION_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StopPacket
            extends Packet<ServerGamePacketListenerImpl, StopPacket> {
        public static final StopPacket INSTANCE = new StopPacket();
        public static final StreamCodec<ByteBuf, StopPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StopPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StopPacket> getPacketType() {
            return PacketTypes.ADIABATIC_COMPRESSION_STOP.get();
        }
    }
}
