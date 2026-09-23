package org.academy.internal.common.ability.electromaster.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.input.MouseScrollEvent;
import org.academy.api.client.resources.R;
import org.academy.api.client.resources.sounds.LoopingPlayerSoundInstance;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SkillProficiencyProfile;
import org.academy.api.common.ability.electromaster.MagneticallyManipulable;
import org.academy.api.common.ability.electromaster.MagneticMovement;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.electromaster.skills.lv1.ArcGenerate;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.academy.internal.common.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MagnetManipulation extends Skill {
    static final double MOVE_RANGE = 48.0;
    static final double MOVE_SPEED_PER_TICK = 36.0 / 20.0;
    static final double TARGET_PULL_SPEED_PER_TICK = 2.3;
    static final double PLAYER_STOP_DISTANCE = 1.35;
    static final double TARGET_STOP_DISTANCE = 0.65;
    static final double TARGET_FRONT_DISTANCE = 2.5;
    static final double HOLD_DISTANCE_MIN = 1.2;
    static final double HOLD_DISTANCE_MAX = 6.0;
    /** One notch must exceed the pull stop tolerance, or the target would never leave its rest spot. */
    static final double HOLD_DISTANCE_STEP = 1.0;
    static final float MOVE_CP_COST = 10.0f;
    static final int MOVE_CP_INTERVAL_TICKS = 20;
    public MagnetManipulation() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .iterationTicks(10)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.ARC_GENERATE)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
        );
    }

    static Vec3 calculateMoveVelocity(Vec3 origin, Vec3 target, Vec3 fallbackDirection) {
        var direction = target.subtract(origin);
        if (direction.lengthSqr() <= 1.0e-6) direction = fallbackDirection;
        if (direction.lengthSqr() <= 1.0e-6) return Vec3.ZERO;
        return direction.normalize().scale(MOVE_SPEED_PER_TICK);
    }

    static Vec3 calculatePullVelocity(Vec3 current, Vec3 origin, Vec3 target,
                                      Vec3 fallback, double maxSpeed, double stopDistance) {
        return MagneticMovement.calculatePullVelocity(current, origin, target, fallback, maxSpeed, stopDistance);
    }

    /** One wheel notch moves the controlled target one block, clamped to the hold envelope. */
    static double resolveHoldDistance(double current, double scrollOffset) {
        if (!Double.isFinite(current) || !Double.isFinite(scrollOffset)) return TARGET_FRONT_DISTANCE;
        return Math.clamp(current + Math.clamp(scrollOffset, -1, 1) * HOLD_DISTANCE_STEP,
                HOLD_DISTANCE_MIN, HOLD_DISTANCE_MAX);
    }

    /**
     * Shared homing profile for ability-controlled falling blocks.
     */
    public static Vec3 calculateControlledBlockVelocity(
            Vec3 currentVelocity,
            Vec3 origin,
            Vec3 target,
            Vec3 fallbackDirection,
            double maxSpeed,
            double stopDistance
    ) {
        return calculatePullVelocity(
                currentVelocity,
                origin,
                target,
                fallbackDirection,
                maxSpeed,
                stopDistance
        );
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    public static boolean isIronRelatedPath(String path) { return org.academy.api.common.ability.electromaster.MagneticTargets.isIronRelatedPath(path); }
    public static boolean hasMagneticKeyword(String path) { return org.academy.api.common.ability.electromaster.MagneticTargets.hasMagneticKeyword(path); }
    public static boolean isMagneticTagPath(String path) { return org.academy.api.common.ability.electromaster.MagneticTargets.isMagneticTagPath(path); }
    public static boolean isMagnetic(BlockState state) { return org.academy.api.common.ability.electromaster.MagneticTargets.isMagnetic(state); }
    public static boolean isMagnetic(ItemStack stack) { return org.academy.api.common.ability.electromaster.MagneticTargets.isMagnetic(stack); }
    public static boolean isMagnetic(Entity entity) { return org.academy.api.common.ability.electromaster.MagneticTargets.isMagnetic(entity); }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        var oldSelf = Client.CONFIG.getKeyBinding(Client.KEY_NAME_START);
        if (oldSelf == null) oldSelf = Client.CONFIG.getKeyBinding(Client.OLD_KEY_NAME_MOVE_START);
        var oldSelfDefault = InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R, InputConstants.PRESS, 0);
        if (!Client.CONFIG.containsKeyBinding(Client.KEY_NAME_LEVITATION) && oldSelf != null && !oldSelf.equals(oldSelfDefault)) {
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_LEVITATION, InputSystem.withAction(oldSelf, InputConstants.PRESS));
        }
        for (var obsolete : List.of(Client.KEY_NAME_START, Client.KEY_NAME_STOP,
                Client.OLD_KEY_NAME_MOVE_START, Client.OLD_KEY_NAME_MOVE_STOP, Client.KEY_NAME_TARGET_STOP)) Client.CONFIG.removeKeyBinding(obsolete);
        InputSystem.addMaintainedKeyBinding(Client.KEY_NAME_TARGET_START,
                Client.CONFIG.getKeyBindingMigratingDefaults(Client.KEY_NAME_TARGET_START,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R, InputConstants.PRESS, 0),
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N, InputConstants.PRESS, 0),
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_X, InputConstants.PRESS, 0)),
                _ -> Client.onMoveStart(PullMode.TARGET_TO_PLAYER),
                _ -> Client.onMoveStop(PullMode.TARGET_TO_PLAYER),
                // The maintained-binding heartbeat fires every 20 client ticks; the server drops the
                // context after 40 silent ticks, so every callback must refresh it.
                _ -> { if (Client.activeMode != null) MisakaNetworkClient.send(MoveStartPacket.TARGET_TO_PLAYER); },
                () -> AbilitySystemClient.canUseSkill(this) && Minecraft.getInstance().player != null
                        && Minecraft.getInstance().player.isAlive() && Minecraft.getInstance().gui.screen() == null);
        InputSystem.addKeyBinding(Client.KEY_NAME_LEVITATION,
                Client.CONFIG.getKeyBinding(Client.KEY_NAME_LEVITATION,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R, InputConstants.PRESS, InputConstants.MOD_SHIFT)),
                _ -> { if (AbilitySystemClient.canUseSkill(this)) MisakaNetworkClient.send(MoveStartPacket.PLAYER_TO_TARGET); });
        org.academy.api.client.hud.ability.ToggleStatusHud.Companion.registerStateProvider(this,
                () -> Minecraft.getInstance().player != null && Minecraft.getInstance().player.getData(AttachmentTypes.MAGNETIC_LEVITATION_REQUESTED));
        org.academy.api.client.hud.ability.ToggleStatusHud.Companion.registerDetailProvider(this, () -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return "";
            if (player.getData(AttachmentTypes.MAGNETIC_LEVITATION_ACTIVE)) {
                return net.minecraft.network.chat.Component.translatable("hud.academy.magnetic_field.hovering").getString();
            }
            if (player.getData(AttachmentTypes.MAGNETIC_LEVITATION_DEGRADED)) {
                return net.minecraft.network.chat.Component.translatable("hud.academy.magnetic_field.losing_support").getString();
            }
            return net.minecraft.network.chat.Component.translatable("hud.academy.magnetic_field.no_support").getString();
        });
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    enum PullMode {
        PLAYER_TO_TARGET,
        TARGET_TO_PLAYER
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ELECTROMASTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.MAGNET_MANIPULATION.get(),
                        List.of(ArcGenerate.Client.SKILL_INFO),
                        R.textures.magnet_manipulation_icon,
                        64,
                        46
                )
        );
        public static final String KEY_NAME_LEVITATION = SkillNames.MAGNET_MANIPULATION + "_levitation";
        public static final String KEY_NAME_START = SkillNames.MAGNET_MANIPULATION + "_start";
        public static final String KEY_NAME_STOP = SkillNames.MAGNET_MANIPULATION + "_stop";
        public static final String KEY_NAME_TARGET_START = SkillNames.MAGNET_MANIPULATION + "_target_start";
        public static final String KEY_NAME_TARGET_STOP = SkillNames.MAGNET_MANIPULATION + "_target_stop";
        private static final String OLD_KEY_NAME_MOVE_START = SkillNames.MAGNET_MANIPULATION + "_move_start";
        private static final String OLD_KEY_NAME_MOVE_STOP = SkillNames.MAGNET_MANIPULATION + "_move_stop";
        public static Config CONFIG = new Config();
        private static SoundInstance loopSound;
        private static @Nullable PullMode activeMode;
        private static int activeTicks;

        public static void onMoveStart(PullMode mode) {
            if (!AbilitySystemClient.canUseSkill(Skills.MAGNET_MANIPULATION.get())) return;
            var player = Minecraft.getInstance().player;
            if (player == null || Minecraft.getInstance().gui.screen() != null) return;
            if (activeMode != null && activeMode != mode) {
                MisakaNetworkClient.send(MoveStopPacket.INSTANCE);
            }
            activeMode = mode;
            activeTicks = 0;
            stopLoopSound();
            loopSound = new LoopingPlayerSoundInstance(
                    player, SoundEvents.MAGNET_MOVE_LOOP.get(), 1.0f, 1.0f,
                    () -> activeMode != null && Minecraft.getInstance().player == player);
            Minecraft.getInstance().getSoundManager().play(loopSound);
            MisakaNetworkClient.send(mode == PullMode.TARGET_TO_PLAYER
                    ? MoveStartPacket.TARGET_TO_PLAYER
                    : MoveStartPacket.PLAYER_TO_TARGET);
        }

        public static void onMoveStop(PullMode mode) {
            if (activeMode != mode) return;
            activeMode = null;
            stopLoopSound();
            if (Minecraft.getInstance().player != null) MisakaNetworkClient.send(MoveStopPacket.INSTANCE);
        }

        private static void stopLoopSound() {
            if (loopSound == null) return;
            Minecraft.getInstance().getSoundManager().stop(loopSound);
            loopSound = null;
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public MagnetManipulation.Client.Config getDefault() {
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
        private static final Map<Player, MoveContext> ACTIVE_MOVEMENT = createContextMap();

        public static boolean isControllingSelfMovement(ServerPlayer player) {
            return org.academy.internal.common.ability.electromaster.field.MagneticFieldRuntime.isHovering(player);
        }

        @SubscribePacket
        public static void handleMoveStart(MoveStartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!Skills.MAGNET_MANIPULATION.get().isEnabled(player)) {
                return;
            }

            if (packet.getMode() == PullMode.PLAYER_TO_TARGET) {
                org.academy.internal.common.ability.electromaster.field.MagneticFieldRuntime.toggle(player);
                return;
            }
            var previous = ACTIVE_MOVEMENT.get(player);
            if (previous != null) {
                previous.heartbeat = player.level().getServer().getTickCount();
                return;
            }
            if (!org.academy.api.server.ability.SkillAvailability.requestExecution(player, Skills.MAGNET_MANIPULATION.get(), false)) return;

            var context = new MoveContext(player, packet.getMode());
            ACTIVE_MOVEMENT.put(player, context);
            setVisualState(player, true);
            AbilitySystemServer.registerContext(context);
        }

        @SubscribePacket
        public static void handleMoveStop(MoveStopPacket packet) {
            var context = ACTIVE_MOVEMENT.get(packet.getPacketListener().getPlayer());
            if (context != null) context.end();
        }

        @SubscribePacket
        public static void handleMoveDistance(MoveDistancePacket packet) {
            var context = ACTIVE_MOVEMENT.get(packet.getPacketListener().getPlayer());
            if (context != null) context.adjustHoldDistance(packet.getScrollOffset());
        }

        private static void setVisualState(ServerPlayer player, boolean active) {
            player.setData(AttachmentTypes.MAGNET_MANIPULATION_ACTIVE.get(), active);
            player.syncData(AttachmentTypes.MAGNET_MANIPULATION_ACTIVE.get());
        }
    }

    public static final class MoveContext extends ServerContext {
        private final ResourceKey<Level> dimension;
        private long heartbeat;
        private final PullMode mode;
        private @Nullable Entity controlledTarget;
        private boolean controlsFallingBlock;
        private int movingTicks;
        private double holdDistance = TARGET_FRONT_DISTANCE;
        private boolean ended;

        private MoveContext(ServerPlayer player, PullMode mode) {
            super(player);
            dimension = player.level().dimension();
            heartbeat = player.level().getServer().getTickCount();
            this.mode = mode;
        }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            if (ended) return;
            if (player.level().getServer().getTickCount() - heartbeat > 40) { end(); return; }
            var skill = Skills.MAGNET_MANIPULATION.get();
            if (player.hasDisconnected()
                    || !player.isAlive()
                    || player.isSpectator()
                    || player.isPassenger()
                    || !player.level().dimension().equals(dimension)
                    || !skill.isEnabled(player)
                    || !org.academy.api.server.ability.SkillAvailability.requestExecution(player, skill, true)) {
                end();
                return;
            }

            var system = AbilitySystemServer.getSystem(player);
            var uuid = player.getUUID();
            if (movingTicks % MOVE_CP_INTERVAL_TICKS == 0
                    && !system.tryTimedOccupation(uuid, skill.adjustProficiencyCost(player,
                    SkillProficiencyProfile.CostKind.CONTINUOUS, MOVE_CP_COST), skill, 10)) {
                end();
                return;
            }
            var moved = pullTargetToPlayer();
            if (!moved) {
                end();
                return;
            }
            if (movingTicks == 0) skill.reportTrigger(player);
            skill.reportActivity(player, true);
            movingTicks++;
        }

        private boolean pullTargetToPlayer() {
            if (controlledTarget == null || !controlledTarget.isAlive()) {
                releaseControlledTarget();
                var target = findCrosshairTarget();
                if (target == null) return false;
                controlledTarget = target.entity() != null
                        ? target.entity()
                        : createFallingBlockTarget(target.blockPos());
                if (controlledTarget == null) return false;
            }

            var look = player.getLookAngle();
            var destination = player.getEyePosition().add(look.scale(holdDistance));
            var targetOrigin = controlledTarget.getBoundingBox().getCenter();
            var range = Skills.MAGNET_MANIPULATION.get().scaledRange(player,
                    org.academy.api.common.ability.electromaster.MagneticFieldTuning.targetPullRange(skillMilestone()));
            if (controlledTarget.level() != level() || player.distanceToSqr(controlledTarget) > range * range
                    || !org.academy.internal.common.entitycontrol.EntityMotionGuard.canApplyMotionFrom(player, controlledTarget)) return false;
            var velocity = calculatePullVelocity(
                    controlledTarget.getDeltaMovement(),
                    targetOrigin,
                    destination,
                    look,
                    org.academy.api.common.ability.electromaster.MagneticFieldTuning.targetPullSpeed(skillMilestone()),
                    TARGET_STOP_DISTANCE
            );
            org.academy.internal.common.entitycontrol.EntityMotionGuard.runWithMotionSource(player,
                    () -> controlledTarget.setDeltaMovement(velocity));
            controlledTarget.hurtMarked = true;
            controlledTarget.resetFallDistance();
            if (controlledTarget instanceof ServerPlayer targetPlayer) {
                targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
            }
            return true;
        }

        private @Nullable FallingBlockEntity createFallingBlockTarget(@Nullable BlockPos pos) {
            if (org.academy.api.server.ability.AbilityEffectPolicy.blockDestruction(level()) == org.academy.api.server.ability.AbilityEffectPolicy.Decision.DENY
                    || pos == null || !level().hasChunkAt(pos) || !level().mayInteract(player, pos)
                    || !org.academy.api.server.ability.SkillTuning.allowBlockDestruction(player, Skills.MAGNET_MANIPULATION.get())) return null;
            var state = level().getBlockState(pos);
            if (!isMagnetic(state)
                    || state.hasBlockEntity()
                    || state.getDestroySpeed(level(), pos) < 0.0f) {
                return null;
            }
            var fallingBlock = FallingBlockEntity.fall(level(), pos, state);
            org.academy.api.server.ability.GravityControl.set(fallingBlock, Skills.MAGNET_MANIPULATION.get().getKey(), true);
            controlsFallingBlock = true;
            return fallingBlock;
        }

        private @Nullable MagneticTarget findCrosshairTarget() {
            var eye = player.getEyePosition();
            var look = player.getLookAngle();
            if (look.lengthSqr() <= 1.0e-6) return null;
            look = look.normalize();
            var range = Skills.MAGNET_MANIPULATION.get().scaledRange(player,
                    org.academy.api.common.ability.electromaster.MagneticFieldTuning.targetPullRange(skillMilestone()));
            var end = eye.add(look.scale(range));
            var blockHit = level().clip(new ClipContext(
                    eye,
                    end,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));
            var blockDistance = blockHit.getType() == HitResult.Type.MISS
                    ? range * range
                    : eye.distanceToSqr(blockHit.getLocation());
            var entityHit = ProjectileUtil.getEntityHitResult(
                    level(),
                    player,
                    eye,
                    end,
                    player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0),
                    entity -> entity != player && entity.isAlive() && !entity.isSpectator()
                            && (mode != PullMode.PLAYER_TO_TARGET || isMagnetic(entity))
                            && !PvpSetting.shouldPrevent(player, entity),
                    0.3f
            );
            if (entityHit != null && eye.distanceToSqr(entityHit.getLocation()) < blockDistance) {
                var entity = entityHit.getEntity();
                return isMagnetic(entity)
                        ? new MagneticTarget(entity, null, entity.getBoundingBox().getCenter(), Direction.UP)
                        : null;
            }
            if (blockHit.getType() == HitResult.Type.MISS) return null;
            var pos = blockHit.getBlockPos();
            return (mode == PullMode.PLAYER_TO_TARGET || isMagnetic(level().getBlockState(pos)))
                    ? new MagneticTarget(null, pos, blockHit.getLocation(), blockHit.getDirection())
                    : null;
        }

        private void releaseControlledTarget() {
            if (controlledTarget != null && controlsFallingBlock && controlledTarget.isAlive()) {
                org.academy.api.server.ability.GravityControl.set(controlledTarget, Skills.MAGNET_MANIPULATION.get().getKey(), false);
            }
            controlledTarget = null;
            controlsFallingBlock = false;
        }

        private int skillMilestone() {
            return Skills.MAGNET_MANIPULATION.get().getEffectiveProficiencyMilestone(player);
        }

        private void adjustHoldDistance(double scrollOffset) {
            holdDistance = resolveHoldDistance(holdDistance, scrollOffset);
        }

        private void end() {
            if (ended) return;
            ended = true;
            unregister();
        }

        @Override
        protected void onUnregistered() {
            ended = true;
            releaseControlledTarget();
            Server.ACTIVE_MOVEMENT.remove(player, this);
            Server.setVisualState(player, false);
        }
    }

    private record MagneticTarget(@Nullable Entity entity, @Nullable BlockPos blockPos,
                                  Vec3 location, Direction face) {
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            if (Client.activeMode == null) return;
            var minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            if (player == null || !player.isAlive() || minecraft.gui.screen() != null
                    || (++Client.activeTicks > 10
                    && !player.getData(AttachmentTypes.MAGNET_MANIPULATION_ACTIVE.get()))) {
                Client.onMoveStop(Client.activeMode);
            }
        }

        /** While a magnetic target is held, the wheel moves it closer or farther instead of switching slots. */
        @SubscribeEvent
        public static void onScroll(MouseScrollEvent event) {
            if (event.yOffset == 0) return;
            var minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            if (player == null || !player.isAlive() || minecraft.gui.screen() != null
                    || !player.getData(AttachmentTypes.MAGNET_MANIPULATION_ACTIVE)
                    || !AbilitySystemClient.canUseSkill(Skills.MAGNET_MANIPULATION.get())) return;
            // Same wheel convention as PneumaticGrasp: positive yOffset (wheel up) pushes farther,
            // negative (wheel down) draws the target closer.
            MisakaNetworkClient.send(new MoveDistancePacket(event.yOffset));
            event.setCanceled(true);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class MoveStartPacket extends Packet<ServerGamePacketListenerImpl, MoveStartPacket> {
        public static final MoveStartPacket PLAYER_TO_TARGET = new MoveStartPacket(PullMode.PLAYER_TO_TARGET);
        public static final MoveStartPacket TARGET_TO_PLAYER = new MoveStartPacket(PullMode.TARGET_TO_PLAYER);
        public static final StreamCodec<ByteBuf, MoveStartPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL,
                packet -> packet.mode == PullMode.TARGET_TO_PLAYER,
                pullTarget -> pullTarget ? TARGET_TO_PLAYER : PLAYER_TO_TARGET
        );
        private final PullMode mode;

        private MoveStartPacket(PullMode mode) {
            this.mode = mode;
        }

        private PullMode getMode() {
            return mode;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, MoveStartPacket> getPacketType() {
            return PacketTypes.MAGNET_MANIPULATION_MOVE_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class MoveDistancePacket extends Packet<ServerGamePacketListenerImpl, MoveDistancePacket> {
        public static final StreamCodec<ByteBuf, MoveDistancePacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, packet -> packet.scrollOffset, MoveDistancePacket::new);
        private final double scrollOffset;

        public MoveDistancePacket(double scrollOffset) {
            this.scrollOffset = scrollOffset;
        }

        public double getScrollOffset() {
            return scrollOffset;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, MoveDistancePacket> getPacketType() {
            return PacketTypes.MAGNET_MANIPULATION_MOVE_DISTANCE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class MoveStopPacket extends Packet<ServerGamePacketListenerImpl, MoveStopPacket> {
        public static final MoveStopPacket INSTANCE = new MoveStopPacket();
        public static final StreamCodec<ByteBuf, MoveStopPacket> CODEC = StreamCodec.unit(INSTANCE);

        private MoveStopPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, MoveStopPacket> getPacketType() {
            return PacketTypes.MAGNET_MANIPULATION_MOVE_STOP.get();
        }
    }
}
