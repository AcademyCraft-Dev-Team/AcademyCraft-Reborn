package org.academy.internal.common.ability.aeromanip.skills.lv1;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
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
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeContext;
import org.academy.internal.common.ability.aeromanip.AeromanipChargeTier;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipVfx;
import org.academy.internal.client.ability.aeromanip.AeromanipChargeHud;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.server.ability.AeromanipResourceManager;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Air-mobility stance, temporary hover, and temporary compressed-air foothold. */
public final class LaminarBuffer extends Skill {
    static final float TOGGLE_CP_OCCUPATION = 12.0f;
    static final float HOVER_CP_COST = 10.0f;
    static final float HOVER_AIR_COST = 32.0f;
    static final float PLATFORM_CP_COST = 18.0f;
    static final float PLATFORM_AIR_COST = 48.0f;
    private static final double ALLY_RADIUS = 16.0;
    private static final int HOVER_DURATION_TICKS = 60;
    private static final int MILESTONE_TWO_HOVER_DURATION_TICKS = 100;
    private static final int PLATFORM_DURATION_TICKS = 200;
    private static final int MILESTONE_THREE_PLATFORM_DURATION_TICKS = 300;
    private static final double AIR_HORIZONTAL_DRAG = 0.91;
    private static final double STANDARD_GROUND_HORIZONTAL_DRAG = 0.54600006;
    private static final double MAX_RETAINED_HORIZONTAL_SPEED = 1.5;
    private static final double GROUND_SPEED_TOLERANCE = 1.0e-3;

    public LaminarBuffer() {
        super(Builder.of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .iterationTicks(5)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.AIRFLOW_JET)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1)));
    }

    static Vec3 horizontalMovementInput(float strafe, float forward, float yawDegrees) {
        var input = new Vec3(strafe, 0.0, forward);
        var lengthSqr = input.lengthSqr();
        if (lengthSqr < 1.0e-7) return Vec3.ZERO;
        if (lengthSqr > 1.0) input = input.normalize();
        var yaw = yawDegrees * Mth.DEG_TO_RAD;
        var sin = Mth.sin(yaw);
        var cos = Mth.cos(yaw);
        return new Vec3(
                input.x * cos - input.z * sin,
                0.0,
                input.z * cos + input.x * sin
        );
    }

    static double groundEquivalentHorizontalSpeed(
            double movementSpeed,
            boolean sprinting,
            double inputAmount
    ) {
        if (!Double.isFinite(movementSpeed) || !Double.isFinite(inputAmount)) return 0.0;
        var amount = Math.clamp(inputAmount, 0.0, 1.0);
        var airborneAcceleration = (sprinting ? 0.026 : 0.02) * amount;
        var groundDisplacement = Math.max(0.0, movementSpeed) * amount
                / (1.0 - STANDARD_GROUND_HORIZONTAL_DRAG);
        return Math.max(0.0, groundDisplacement - airborneAcceleration);
    }

    static Vec3 bufferedAirVelocity(
            Vec3 velocity,
            Vec3 horizontalInput,
            double groundEquivalentSpeed
    ) {
        if (velocity == null || !Double.isFinite(velocity.x)
                || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z)) return Vec3.ZERO;
        var horizontalSpeed = Math.hypot(velocity.x, velocity.z);
        var fallSpeed = Math.max(-0.12, velocity.y);
        if (horizontalInput != null && Double.isFinite(horizontalInput.x)
                && Double.isFinite(horizontalInput.z)
                && horizontalInput.horizontalDistanceSqr() > 1.0e-7) {
            var direction = new Vec3(horizontalInput.x, 0.0, horizontalInput.z).normalize();
            var targetSpeed = Math.max(0.0, groundEquivalentSpeed);
            var resolvedSpeed = horizontalSpeed > targetSpeed + GROUND_SPEED_TOLERANCE
                    ? horizontalSpeed
                    : targetSpeed;
            return new Vec3(
                    direction.x * resolvedSpeed,
                    fallSpeed,
                    direction.z * resolvedSpeed
            );
        }
        var retainedScale = horizontalSpeed > 1.0e-5
                ? Math.min(1.0 / AIR_HORIZONTAL_DRAG,
                MAX_RETAINED_HORIZONTAL_SPEED / horizontalSpeed)
                : 1.0;
        return new Vec3(
                velocity.x * retainedScale,
                fallSpeed,
                velocity.z * retainedScale
        );
    }

    static Vec3 boostedJumpVelocity(Vec3 velocity) {
        if (velocity == null || !Double.isFinite(velocity.x)
                || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z)) return Vec3.ZERO;
        return new Vec3(velocity.x * 1.05, velocity.y + 0.12, velocity.z * 1.05);
    }

    static int hoverDuration(boolean milestoneTwo) {
        return milestoneTwo ? MILESTONE_TWO_HOVER_DURATION_TICKS : HOVER_DURATION_TICKS;
    }

    static int platformDuration(boolean milestoneThree) {
        return milestoneThree ? MILESTONE_THREE_PLATFORM_DURATION_TICKS : PLATFORM_DURATION_TICKS;
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        var defaultBinding = InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_V,
                InputSystem.ANY_ACTION,
                0);
        var configuredBinding = Client.CONFIG.getKeyBinding(Client.KEY_NAME_CAST, defaultBinding);
        if (configuredBinding.action() != InputSystem.ANY_ACTION) {
            configuredBinding = new InputSystem.KeyCombination(
                    configuredBinding.type(), configuredBinding.keys(), InputSystem.ANY_ACTION,
                    configuredBinding.modifiers(), configuredBinding.availableWhenScreen(),
                    configuredBinding.unbound());
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_CAST, configuredBinding);
            AcademyCraftClient.Config.INSTANCE.save();
        }
        InputSystem.addMaintainedKeyBinding(
                Client.KEY_NAME_CAST,
                configuredBinding,
                _ -> Client.start(),
                _ -> Client.stop());
        Client.SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.LAMINAR_BUFFER.get(),
                        List.of(AirflowJet.Client.SKILL_INFO),
                        R.textures.air_cushion_icon,
                        130,
                        40));
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_CAST = SkillNames.LAMINAR_BUFFER + "_cast";
        public static AbilitySystemClient.SkillInfo SKILL_INFO;
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void start() {
            if (AbilitySystemClient.canUseSkill(Skills.LAMINAR_BUFFER.get())) {
                AeromanipChargeHud.begin(Skills.LAMINAR_BUFFER.get());
                MisakaNetworkClient.send(StartPacket.INSTANCE);
            }
        }

        private static void stop() {
            AeromanipChargeHud.end(Skills.LAMINAR_BUFFER.get());
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
        private static final Map<ServerPlayer, ChargeContext> CHARGES = new WeakHashMap<>();
        private static final Map<ServerPlayer, HoverContext> HOVERS = new WeakHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handleStart(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.LAMINAR_BUFFER.get();
            if (CHARGES.containsKey(player) || !skill.isEnabled(player)) return;
            var hover = HOVERS.remove(player);
            if (hover != null) hover.stop();
            var context = new ChargeContext(player);
            CHARGES.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        @SubscribePacket
        public static void handleStop(StopPacket packet) {
            var context = CHARGES.get(packet.getPacketListener().getPlayer());
            if (context != null) context.release();
        }

        static boolean isBufferActive(ServerPlayer player) {
            var skillId = Skills.LAMINAR_BUFFER.get().getKeyString();
            return AbilitySystemServer.getSystem(player).getPlayerData(player.getUUID())
                    .getCpOccupations().stream()
                    .anyMatch(occupation -> occupation.isPermanent()
                            && skillId.equals(occupation.getSkillId()));
        }

        private static final class ChargeContext extends AeromanipChargeContext {
            private ChargeContext(ServerPlayer player) {
                super(player, Skills.LAMINAR_BUFFER.get());
            }

            @Override
            protected void onReleased(AeromanipChargeTier tier, long chargeTicks) {
                var skill = Skills.LAMINAR_BUFFER.get();
                switch (tier) {
                    case INSTANT -> toggleBuffer(player, skill);
                    case HALF -> skill.executeActiveWithResource(
                            player,
                            _ -> cpCost(player, HOVER_CP_COST),
                            _ -> HOVER_AIR_COST,
                            (_, _) -> beginHover(player, skill));
                    case FULL -> {
                        var platformPos = platformPosition(player);
                        if (!canPlacePlatform(player.level(), platformPos)) return;
                        skill.executeActiveWithResource(
                                player,
                                _ -> cpCost(player, PLATFORM_CP_COST),
                                _ -> PLATFORM_AIR_COST,
                                (_, _) -> placePlatform(player, skill, platformPos));
                    }
                }
            }

            @Override
            protected void onTierReached(AeromanipChargeTier tier) {
                player.level().playSound(null, player.blockPosition(), SoundEvents.AIRFLOW_JET.get(),
                        SoundSource.PLAYERS, 0.35f,
                        tier == AeromanipChargeTier.FULL ? 1.65f : 1.4f);
                AeromanipVfx.field(player.level(),
                        new Vec3(player.getX(), player.getBoundingBox().minY + 0.05, player.getZ()),
                        tier == AeromanipChargeTier.FULL ? 1.25 : 0.72);
            }

            @Override
            protected void onChargeEnded(boolean released) {
                CHARGES.remove(player, this);
            }
        }

        private static float cpCost(ServerPlayer player, float baseCost) {
            return baseCost * AeromanipConfig.cpMultiplier(player, SkillNames.LAMINAR_BUFFER);
        }

        private static void toggleBuffer(ServerPlayer player, LaminarBuffer skill) {
            var system = AbilitySystemServer.getSystem(player);
            if (isBufferActive(player)) {
                system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
                skill.reportTrigger(player);
                return;
            }
            if (system.ensurePermanentOccupation(
                    player.getUUID(), cpCost(player, TOGGLE_CP_OCCUPATION), skill)) {
                skill.reportTrigger(player);
                AeromanipVfx.burst(player.level(),
                        player.position().add(0.0, player.getBbHeight() * 0.45, 0.0), 0.75);
            }
        }

        private static void beginHover(ServerPlayer player, LaminarBuffer skill) {
            var previous = HOVERS.remove(player);
            if (previous != null) previous.stop();
            var baseDuration = hoverDuration(skill.hasProficiencyMilestone(player, 2));
            var duration = Math.max(1, Math.round(baseDuration
                    * AeromanipConfig.durationMultiplier(player, SkillNames.LAMINAR_BUFFER)));
            var hover = new HoverContext(player, skill, duration);
            HOVERS.put(player, hover);
            AbilitySystemServer.registerContext(hover);
        }

        private static BlockPos platformPosition(ServerPlayer player) {
            return BlockPos.containing(
                    player.getX(),
                    player.getBoundingBox().minY - 1.0,
                    player.getZ());
        }

        private static boolean canPlacePlatform(ServerLevel level, BlockPos pos) {
            return level.getBlockState(pos).isAir() && level.getFluidState(pos).isEmpty();
        }

        private static void placePlatform(ServerPlayer player, LaminarBuffer skill, BlockPos pos) {
            var level = player.level();
            if (!canPlacePlatform(level, pos)) return;
            level.setBlockAndUpdate(pos, Blocks.COMPRESSED_AIR_PLATFORM.get().defaultBlockState());
            var baseDuration = platformDuration(skill.hasProficiencyMilestone(player, 3));
            var duration = Math.max(1, Math.round(baseDuration
                    * AeromanipConfig.durationMultiplier(player, SkillNames.LAMINAR_BUFFER)));
            level.scheduleTick(pos, Blocks.COMPRESSED_AIR_PLATFORM.get(), duration);
            AeromanipVfx.field(level,
                    new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), 0.82);
            level.playSound(null, pos, SoundEvents.AIRFLOW_JET.get(),
                    SoundSource.PLAYERS, 0.65f, 0.75f);
        }

        private static final class HoverContext extends ServerContext {
            private final ServerLevel initialLevel;
            private final LaminarBuffer skill;
            private final AeromanipResourceManager.UsageLease usageLease;
            private final double targetY;
            private final int durationTicks;
            private int ticks;
            private boolean ended;

            private HoverContext(ServerPlayer player, LaminarBuffer skill, int durationTicks) {
                super(player);
                this.skill = skill;
                this.durationTicks = durationTicks;
                initialLevel = player.level();
                targetY = player.getY();
                usageLease = AbilitySystemServer.getSystem(player)
                        .getAeromanipResourceManager().beginUse(player);
            }

            private void stop() {
                if (ended) return;
                ended = true;
                unregister();
            }

            @SubscribeEvent
            public void onTick(ServerTickEvent.Pre event) {
                if (ended || ticks >= durationTicks || player.hasDisconnected() || !player.isAlive()
                        || player.level() != initialLevel || !skill.isEnabled(player)) {
                    stop();
                    return;
                }
                var velocity = player.getDeltaMovement();
                var correction = Math.clamp((targetY - player.getY()) * 0.35, -0.25, 0.25);
                player.setDeltaMovement(velocity.x * 0.92, correction, velocity.z * 0.92);
                player.hurtMarked = true;
                player.resetFallDistance();
                skill.reportActivity(player, true);
                if (ticks % 8 == 0) {
                    AeromanipVfx.field(initialLevel,
                            new Vec3(player.getX(), player.getBoundingBox().minY + 0.05, player.getZ()),
                            0.72);
                }
                ticks++;
            }

            @Override
            protected void onUnregistered() {
                ended = true;
                usageLease.close();
                HOVERS.remove(player, this);
            }
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onEntityTick(EntityTickEvent.Post event) {
            if (!(event.getEntity() instanceof LivingEntity living)
                    || !(living.level() instanceof ServerLevel level)
                    || living.isRemoved()) return;
            var skill = Skills.LAMINAR_BUFFER.get();
            if (living instanceof ServerPlayer player
                    && !skill.isEnabled(player)
                    && Server.isBufferActive(player)) {
                AbilitySystemServer.getSystem(player).releaseMaintenanceOccupation(
                        player.getUUID(), skill.getKeyString());
            }
            if (living.onGround() || living.isInWater()) return;
            var owner = findOwner(level, living, skill);
            if (owner == null) return;
            var current = living.getDeltaMovement();
            var movementInput = horizontalMovementInput(
                    living.xxa, living.zza, living.getYRot());
            var groundEquivalentSpeed = groundEquivalentHorizontalSpeed(
                    living.getSpeed(), living.isSprinting(), movementInput.horizontalDistance());
            var buffered = bufferedAirVelocity(
                    current, movementInput, groundEquivalentSpeed);
            if (!buffered.equals(current)) {
                living.setDeltaMovement(buffered);
                living.hurtMarked = true;
            }
            living.resetFallDistance();
        }

        @SubscribeEvent
        public static void onJump(LivingEvent.LivingJumpEvent event) {
            var living = event.getEntity();
            if (!(living.level() instanceof ServerLevel level)) return;
            var owner = findOwner(level, living, Skills.LAMINAR_BUFFER.get());
            if (owner == null) return;
            living.setDeltaMovement(boostedJumpVelocity(living.getDeltaMovement()));
            living.hurtMarked = true;
        }

        private static ServerPlayer findOwner(
                ServerLevel level,
                LivingEntity target,
                LaminarBuffer skill
        ) {
            for (var owner : level.players()) {
                if (!owner.isAlive() || !skill.isEnabled(owner) || !Server.isBufferActive(owner)) continue;
                if (target == owner) return owner;
                if (!skill.hasProficiencyMilestone(owner, 1)
                        || owner.distanceToSqr(target) > ALLY_RADIUS * ALLY_RADIUS) continue;
                if (owner.isAlliedTo(target)
                        || target instanceof TamableAnimal animal && animal.isOwnedBy(owner)) {
                    return owner;
                }
            }
            return null;
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StartPacket extends Packet<ServerGamePacketListenerImpl, StartPacket> {
        public static final StartPacket INSTANCE = new StartPacket();
        public static final StreamCodec<ByteBuf, StartPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StartPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StartPacket> getPacketType() {
            return PacketTypes.LAMINAR_BUFFER_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StopPacket extends Packet<ServerGamePacketListenerImpl, StopPacket> {
        public static final StopPacket INSTANCE = new StopPacket();
        public static final StreamCodec<ByteBuf, StopPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StopPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StopPacket> getPacketType() {
            return PacketTypes.LAMINAR_BUFFER_STOP.get();
        }
    }
}
