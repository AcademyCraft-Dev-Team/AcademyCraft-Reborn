package org.academy.internal.common.ability.accelerator.skills.lv3;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
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
import org.academy.api.common.structure.BlockStructure;
import org.academy.api.common.structure.BlockStructureApi;
import org.academy.api.common.structure.BlockStructureCaptureOptions;
import org.academy.api.common.structure.BlockStructureKineticHandle;
import org.academy.api.common.structure.BlockStructureKineticOptions;
import org.academy.api.common.structure.BlockStructureKinetics;
import org.academy.api.common.structure.BlockStructureSettlementPolicy;
import org.academy.api.common.structure.BlockStructureSnapshot;
import org.academy.api.server.team.TeamRelations;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv2.KineticEnergyApplied;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Captures terrain as a hovering structure and throws it along the caster's view vector. */
public final class KineticThrow extends Skill {
    public static final double TARGET_RANGE = 24.0;
    public static final int TICKS_PER_CHARGE_TIER = 20;
    public static final int HOVER_TICKS = 10 * 20;
    public static final double PLAYER_CLEARANCE = 1.0;
    public static final int MAXIMUM_FLIGHT_TICKS = 30 * 20;
    public static final double BASE_THROW_SPEED = 2.5;
    public static final float DAMAGE_PER_BLOCK = 5.0f;

    private static final int[] HORIZONTAL_RADII = {5, 7, 9};
    private static final int[] VERTICAL_RADII = {2, 4, 6};
    private static final int[] CYLINDER_RADII = {5, 7, 9};
    private static final int[] CYLINDER_HEIGHTS = {5, 7, 9};
    private static final int[] LIFT_HEIGHTS = {4, 5, 7};
    private static final int[] EXPLOSION_RADII = {5, 7, 9};
    private static final double HOVER_MAXIMUM_SPEED = 1.25;
    private static final int IMPACT_COOLDOWN_TICKS = 10;

    public KineticThrow() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .damage()
                .level(AbilityLevel.LEVEL3)
                .energyCost(20_000)
                .cpCost(30)
                .iterationTicks(5)
                .maxStacks(3)
                .dependsOn(Skills.KINETIC_ENERGY_APPLIED)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
                .devCondition(new DevCondition.DependencyCondition(
                        "Kinetic Energy Applied", "academy:kinetic_energy_applied"))
        );
    }

    public static int chargeTier(long chargeTicks) {
        return Mth.clamp((int) (Math.max(0L, chargeTicks) / TICKS_PER_CHARGE_TIER), 0, 2);
    }

    public static int horizontalRadius(int tier) {
        return HORIZONTAL_RADII[Mth.clamp(tier, 0, 2)];
    }

    public static int verticalRadius(int tier) {
        return VERTICAL_RADII[Mth.clamp(tier, 0, 2)];
    }

    public static int cylinderRadius(int tier) {
        return CYLINDER_RADII[Mth.clamp(tier, 0, 2)];
    }

    public static int cylinderHeight(int tier) {
        return CYLINDER_HEIGHTS[Mth.clamp(tier, 0, 2)];
    }

    public static int liftHeight(int tier) {
        return LIFT_HEIGHTS[Mth.clamp(tier, 0, 2)];
    }

    public static int explosionRadius(int tier) {
        return EXPLOSION_RADII[Mth.clamp(tier, 0, 2)];
    }

    public static float structureDamage(int blockCount) {
        return Math.max(0, blockCount) * DAMAGE_PER_BLOCK;
    }

    public static Vec3 throwDirection(Vec3 viewDirection) {
        return viewDirection != null && viewDirection.lengthSqr() > 1.0e-8
                ? viewDirection.normalize()
                : Vec3.ZERO;
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(
                Client.KEY_NAME_CHARGE,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_CHARGE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD,
                                InputConstants.KEY_H, InputConstants.PRESS, 0)),
                _ -> Client.startCharge()
        );
        InputSystem.addKeyBinding(
                Client.KEY_NAME_RELEASE,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_RELEASE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD,
                                InputConstants.KEY_H, InputConstants.RELEASE, 0)),
                _ -> Client.releaseCharge()
        );
        InputSystem.addKeyBinding(
                Client.KEY_NAME_THROW,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_THROW,
                        InputSystem.combo(InputSystem.InputType.MOUSE,
                                InputConstants.MOUSE_BUTTON_LEFT, InputConstants.PRESS, 0)),
                _ -> Client.throwLookedAtStructure()
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.KINETIC_THROW.get(),
                        List.of(KineticEnergyApplied.Client.SKILL_INFO),
                        R.textures.kinetic_throw_icon,
                        150,
                        100
                )
        );
        public static final String KEY_NAME_CHARGE = SkillNames.KINETIC_THROW + "_charge";
        public static final String KEY_NAME_RELEASE = SkillNames.KINETIC_THROW + "_release";
        public static final String KEY_NAME_THROW = SkillNames.KINETIC_THROW + "_throw";
        public static Config CONFIG = new Config();
        private static boolean charging;

        private Client() {
        }

        private static void startCharge() {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (charging || minecraft.gui.screen() != null
                    || !AbilitySystemClient.canUseSkill(Skills.KINETIC_THROW.get())) return;
            charging = true;
            MisakaNetworkClient.send(StartPacket.INSTANCE);
        }

        private static void releaseCharge() {
            if (!charging) return;
            charging = false;
            MisakaNetworkClient.send(ReleasePacket.INSTANCE);
        }

        private static void throwLookedAtStructure() {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            var player = minecraft.player;
            if (player == null || minecraft.gui.screen() != null) return;
            MisakaNetworkClient.send(ThrowPacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

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
        private static final Map<UUID, ChargeState> CHARGES = new HashMap<>();
        private static final Map<UUID, HeldStructure> HELD_STRUCTURES = new HashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handleStart(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!Skills.KINETIC_THROW.get().isEnabled(player)
                    || !(player.level() instanceof ServerLevel level)) return;
            var hit = lookedAtBlock(player);
            if (hit == null) {
                player.sendOverlayMessage(Component.translatable(
                        "message.academy.kinetic_throw.block_target_required"));
                return;
            }
            CHARGES.put(player.getUUID(), new ChargeState(
                    level.dimension(), hit.getBlockPos().immutable(), level.getGameTime()));
        }

        @SubscribePacket
        public static void handleRelease(ReleasePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var charge = CHARGES.remove(player.getUUID());
            if (charge == null || !charge.dimension.equals(player.level().dimension())
                    || !(player.level() instanceof ServerLevel level)
                    || !Skills.KINETIC_THROW.get().isEnabled(player)) return;
            var chargeTicks = Math.max(0L, level.getGameTime() - charge.startTick);
            if (Skills.KINETIC_THROW.get().hasProficiencyMilestone(player, 2)) {
                chargeTicks = Math.round(chargeTicks / 0.8);
            }
            captureAndLift(player, charge.seed, chargeTier(chargeTicks));
        }

        @SubscribePacket
        public static void handleThrow(ThrowPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var held = HELD_STRUCTURES.get(player.getUUID());
            if (held == null || held.thrown || !held.usableBy(player)
                    || player.level().getGameTime() > held.expiresAt) {
                return;
            }
            var inside = BlockStructureApi.isViewerInside(player, held.structure);
            if (!inside && BlockStructureApi.findLookedAt(
                    player, TARGET_RANGE, structure -> structure == held.structure).isEmpty()) {
                return;
            }
            launch(player, held);
        }

        private static void captureAndLift(ServerPlayer player, BlockPos seed, int tier) {
            var level = player.level();
            var policy = capturePolicy(player);
            var selection = BlockStructureApi.selectLowerEllipsoidWithUpperCylinder(
                    level,
                    seed,
                    horizontalRadius(tier),
                    verticalRadius(tier),
                    cylinderRadius(tier),
                    cylinderHeight(tier),
                    policy
            );
            if (!selection.succeeded()) {
                player.sendOverlayMessage(Component.translatable(
                        "message.academy.kinetic_throw.capture_rejected"));
                return;
            }
            var forward = Vec3.directionFromRotation(0.0f, player.getYRot());
            var playerSafeSelection = BlockStructureApi.cropToForwardHalfSpace(
                    selection.positions(), player.position(), forward, PLAYER_CLEARANCE);
            var positions = BlockStructureApi.cropImmediatelyBlocked(
                    level, playerSafeSelection, Direction.UP);
            if (positions.isEmpty() || !positions.contains(seed)) {
                player.sendOverlayMessage(Component.translatable(
                        "message.academy.kinetic_throw.capture_rejected"));
                return;
            }

            var skill = Skills.KINETIC_THROW.get();
            skill.executeActive(player, (_, _) -> {
                var captured = BlockStructureApi.capture(
                        level,
                        positions,
                        new BlockStructureCaptureOptions(
                                Math.min(positions.size(), BlockStructureSnapshot.MAX_BLOCKS),
                                0,
                                false,
                                true,
                                policy,
                                BlockStructureSettlementPolicy.fixedAbove(
                                        seed.getY(),
                                        BlockStructureSettlementPolicy.NATURAL_BLOCKS
                                )
                        )
                );
                var structure = captured.structure().orElse(null);
                if (structure == null) {
                    player.sendOverlayMessage(Component.translatable(
                            "message.academy.kinetic_throw.capture_rejected"));
                    return;
                }
                releaseHeld(player.getUUID());
                var target = structure.position().add(0.0, liftHeight(tier), 0.0);
                var handle = BlockStructureKinetics.holdAt(
                        structure,
                        player,
                        target,
                        new BlockStructureKineticOptions(
                                HOVER_TICKS,
                                HOVER_MAXIMUM_SPEED,
                                IMPACT_COOLDOWN_TICKS,
                                true,
                                true
                        ),
                        _ -> {
                        }
                );
                HELD_STRUCTURES.put(player.getUUID(), new HeldStructure(
                        structure,
                        level.dimension(),
                        tier,
                        level.getGameTime() + HOVER_TICKS,
                        handle
                ));
                playKineticCue(structure);
            });
        }

        private static void launch(ServerPlayer player, HeldStructure held) {
            var direction = throwDirection(player.getLookAngle());
            if (direction.lengthSqr() <= 1.0e-8) return;
            var speed = Skills.KINETIC_THROW.get().hasProficiencyMilestone(player, 3)
                    ? BASE_THROW_SPEED * 1.2
                    : BASE_THROW_SPEED;
            held.handle.close();
            held.thrown = true;
            held.expiresAt = player.level().getGameTime() + MAXIMUM_FLIGHT_TICKS;
            held.handle = BlockStructureKinetics.launch(
                    held.structure,
                    player,
                    direction.normalize().scale(speed),
                    new BlockStructureKineticOptions(
                            MAXIMUM_FLIGHT_TICKS,
                            speed,
                            IMPACT_COOLDOWN_TICKS,
                            true
                    ),
                    impact -> applyEntityImpact(player, impact),
                    impact -> {
                        HELD_STRUCTURES.remove(player.getUUID(), held);
                        impact.structure().setVelocity(Vec3.ZERO);
                        createImpactExplosion(player, impact.structure(), held.tier);
                        impact.structure().beginGravitySettlement();
                    }
            );
            playKineticCue(held.structure);
        }

        private static void playKineticCue(BlockStructure structure) {
            var entity = structure.asEntity();
            entity.level().playSound(
                    null,
                    entity,
                    org.academy.internal.common.sounds.SoundEvents.VECTOR_REFLECTION.get(),
                    SoundSource.PLAYERS,
                    1.0f,
                    1.0f
            );
        }

        private static void applyEntityImpact(
                ServerPlayer player,
                org.academy.api.common.structure.BlockStructureImpact impact
        ) {
            var target = impact.target();
            if (!canAffect(player, target) || target instanceof BlockStructure) return;
            var blockCount = impact.structure().snapshot().blockCount();
            var damage = structureDamage(blockCount);
            if (target instanceof LivingEntity living && damage > 0.0f) {
                if (living.hurtServer(
                        player.level(),
                        SkillDamageSource.ofDirect(
                                player,
                                Skills.KINETIC_THROW.get(),
                                impact.structure().asEntity()),
                        damage)) {
                    Skills.KINETIC_THROW.get().onHurt(player, living, damage);
                }
            }
            if (impact.movement().lengthSqr() <= 1.0e-8
                    || !EntityMotionGuard.canApplyMotionFrom(player, target)) return;
            var milestoneScale = Skills.KINETIC_THROW.get()
                    .hasProficiencyMilestone(player, 3) ? 1.25 : 1.0;
            var knockback = Math.min(4.0, 0.6 + Math.sqrt(blockCount) * 0.08)
                    * milestoneScale;
            var impulse = impact.movement().normalize().scale(knockback)
                    .add(0.0, 0.15, 0.0);
            EntityMotionGuard.runWithMotionSource(player, () -> {
                target.setDeltaMovement(target.getDeltaMovement().add(impulse));
                target.hurtMarked = true;
            });
        }

        private static void createImpactExplosion(
                ServerPlayer player,
                BlockStructure structure,
                int tier
        ) {
            if (!(structure.asEntity().level() instanceof ServerLevel level)) return;
            var center = structure.asEntity().getBoundingBox().getCenter();
            var radius = explosionRadius(tier);
            var damage = structureDamage(structure.snapshot().blockCount());
            var area = AABB.ofSize(center, radius * 2.0, radius * 2.0, radius * 2.0);
            for (var target : level.getEntitiesOfClass(
                    LivingEntity.class,
                    area,
                    target -> canAffect(player, target)
                            && target.getBoundingBox().getCenter().distanceToSqr(center)
                            <= radius * radius)) {
                if (target.hurtServer(
                        level,
                        SkillDamageSource.ofDirect(
                                player,
                                Skills.KINETIC_THROW.get(),
                                structure.asEntity()),
                        damage)) {
                    Skills.KINETIC_THROW.get().onHurt(player, target, damage);
                }
            }
            level.sendParticles(
                    ParticleTypes.EXPLOSION_EMITTER,
                    center.x, center.y, center.z,
                    1,
                    0.0, 0.0, 0.0,
                    0.0
            );
            level.sendParticles(
                    ParticleTypes.EXPLOSION,
                    center.x, center.y, center.z,
                    radius * 8,
                    radius * 0.45, radius * 0.45, radius * 0.45,
                    0.08
            );
            level.playSound(
                    null,
                    center.x, center.y, center.z,
                    SoundEvents.GENERIC_EXPLODE,
                    SoundSource.BLOCKS,
                    4.0f,
                    0.8f + level.getRandom().nextFloat() * 0.2f
            );
        }

        private static boolean canAffect(ServerPlayer player, Entity target) {
            return target != player
                    && target.isAlive()
                    && !target.isSpectator()
                    && !TeamRelations.areAllied(player, target)
                    && !PvpSetting.shouldPrevent(player, target);
        }

        private static BlockStructureCaptureOptions.CapturePolicy capturePolicy(
                ServerPlayer player
        ) {
            return (level, position, state, blockEntity) ->
                    level.mayInteract(player, position)
                            && blockEntity == null
                            && BlockStructureCaptureOptions.CapturePolicy.MOVABLE.canCapture(
                            level, position, state, null);
        }

        private static BlockHitResult lookedAtBlock(ServerPlayer player) {
            var start = player.getEyePosition();
            var end = start.add(player.getLookAngle().scale(TARGET_RANGE));
            var hit = player.level().clip(new ClipContext(
                    start,
                    end,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    player
            ));
            return hit.getType() == HitResult.Type.BLOCK
                    && start.distanceToSqr(hit.getLocation()) <= TARGET_RANGE * TARGET_RANGE
                    ? hit
                    : null;
        }

        private static void tick(ServerPlayer player) {
            var charge = CHARGES.get(player.getUUID());
            if (charge != null) {
                if (!charge.dimension.equals(player.level().dimension())
                        || !player.isAlive()
                        || player.hasDisconnected()
                        || !Skills.KINETIC_THROW.get().isEnabled(player)) {
                    CHARGES.remove(player.getUUID());
                } else {
                    Skills.KINETIC_THROW.get().reportActivity(player, true);
                }
            }
            var held = HELD_STRUCTURES.get(player.getUUID());
            if (held != null && (!held.usableBy(player)
                    || player.level().getGameTime() > held.expiresAt
                    || !held.handle.active())) {
                releaseHeld(player.getUUID());
            }
        }

        private static void clear(ServerPlayer player) {
            CHARGES.remove(player.getUUID());
            releaseHeld(player.getUUID());
        }

        private static void releaseHeld(UUID playerId) {
            var held = HELD_STRUCTURES.remove(playerId);
            if (held != null) held.handle.close();
        }

        private record ChargeState(
                ResourceKey<Level> dimension,
                BlockPos seed,
                long startTick
        ) {
        }

        private static final class HeldStructure {
            private final BlockStructure structure;
            private final ResourceKey<Level> dimension;
            private final int tier;
            private long expiresAt;
            private BlockStructureKineticHandle handle;
            private boolean thrown;

            private HeldStructure(
                    BlockStructure structure,
                    ResourceKey<Level> dimension,
                    int tier,
                    long expiresAt,
                    BlockStructureKineticHandle handle
            ) {
                this.structure = structure;
                this.dimension = dimension;
                this.tier = tier;
                this.expiresAt = expiresAt;
                this.handle = handle;
            }

            private boolean usableBy(ServerPlayer player) {
                return structure != null
                        && !structure.asEntity().isRemoved()
                        && dimension.equals(player.level().dimension())
                        && structure.asEntity().level() == player.level();
            }
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.tick(player);
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.clear(player);
        }

        @SubscribeEvent
        public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.clear(player);
        }

        @SubscribeEvent
        public static void onLivingDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.clear(player);
        }

        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            Server.CHARGES.clear();
            Server.HELD_STRUCTURES.clear();
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
            return PacketTypes.KINETIC_THROW_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ReleasePacket extends Packet<ServerGamePacketListenerImpl, ReleasePacket> {
        public static final ReleasePacket INSTANCE = new ReleasePacket();
        public static final StreamCodec<ByteBuf, ReleasePacket> CODEC = StreamCodec.unit(INSTANCE);

        private ReleasePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ReleasePacket> getPacketType() {
            return PacketTypes.KINETIC_THROW_RELEASE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ThrowPacket extends Packet<ServerGamePacketListenerImpl, ThrowPacket> {
        public static final ThrowPacket INSTANCE = new ThrowPacket();
        public static final StreamCodec<ByteBuf, ThrowPacket> CODEC = StreamCodec.unit(INSTANCE);

        private ThrowPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ThrowPacket> getPacketType() {
            return PacketTypes.KINETIC_THROW_THROW.get();
        }
    }
}
