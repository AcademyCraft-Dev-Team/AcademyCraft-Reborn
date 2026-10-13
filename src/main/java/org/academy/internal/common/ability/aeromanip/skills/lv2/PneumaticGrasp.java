package org.academy.internal.common.ability.aeromanip.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.ability.ClientContext;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.input.MouseScrollEvent;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.structure.BlockStructure;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructureQueries;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureTraction;
import org.academy.api.common.structure.StructureTractionSession;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.config.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.targeting.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.vfx.AeromanipVfx;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.network.PacketTypes;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public final class PneumaticGrasp extends Skill {
    private static final double MIN_CONTROL_DISTANCE = 2.0;
    private static final double DEFAULT_CONTROL_DISTANCE = 2.5;
    private static final double DISTANCE_STEP = 1.0;

    public PneumaticGrasp() {
        super(Builder.of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .iterationTicks(5)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.FLOW_SENSE)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2)));
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_START, Client.CONFIG.getKeyBinding(Client.KEY_NAME_START,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G, InputConstants.PRESS, 0)), _ -> Client.start());
        InputSystem.addKeyBinding(Client.KEY_NAME_STOP, Client.CONFIG.getKeyBinding(Client.KEY_NAME_STOP,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G, InputConstants.RELEASE, 0)), _ -> Client.stop());
        Client.SKILL_INFO = AbilitySystemClient.addSkillInfo(AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(Skills.PNEUMATIC_GRASP.get(), List.of(FlowSense.Client.SKILL_INFO),
                        R.textures.pneumatic_grasp_icon, 75, 72));
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_START = SkillNames.PNEUMATIC_GRASP + "_start";
        public static final String KEY_NAME_STOP = SkillNames.PNEUMATIC_GRASP + "_stop";
        public static AbilitySystemClient.SkillInfo SKILL_INFO;
        public static Config CONFIG = new Config();
        private static ControlContext currentContext;

        private static void start() {
            if (currentContext != null || !AbilitySystemClient.canUseSkill(Skills.PNEUMATIC_GRASP.get())) return;
            currentContext = new ControlContext();
            AbilitySystemClient.registerContext(currentContext);
            MisakaNetworkClient.send(StartPacket.INSTANCE);
        }

        private static void stop() {
            if (currentContext != null) currentContext.cleanup();
            MisakaNetworkClient.send(StopPacket.INSTANCE);
        }

        private static final class ControlContext extends ClientContext {
            @SubscribeEvent
            public void onScroll(MouseScrollEvent event) {
                var steps = Mth.sign(event.yOffset);
                if (steps == 0) return;
                MisakaNetworkClient.send(new AdjustDistancePacket(steps));
                event.setCanceled(true);
            }

            private void cleanup() {
                unregister();
                if (currentContext == this) currentContext = null;
            }
        }

        public static final class Config extends KeyBindingConfig {
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
        private static final Map<ServerPlayer, Context> ACTIVE = new WeakHashMap<>();

        @SubscribePacket
        public static void handle(StartPacket packet) {
            start(packet.getPacketListener().getPlayer());
        }

        /** Same server-owned entry point used by the skill packet; it grants no extra capability. */
        public static boolean start(ServerPlayer player) {
            requireThread(player);
            if (ACTIVE.containsKey(player) || !Skills.PNEUMATIC_GRASP.get().isEnabled(player)) return false;
            var context = new Context(player);
            ACTIVE.put(player, context);
            AbilitySystemServer.registerContext(context);
            Skills.PNEUMATIC_GRASP.get().reportTrigger(player);
            return true;
        }

        @SubscribePacket
        public static void handle(StopPacket packet) {
            stop(packet.getPacketListener().getPlayer());
        }

        public static void stop(ServerPlayer player) {
            requireThread(player);
            var context = ACTIVE.get(player);
            if (context != null) context.end();
        }

        @SubscribePacket
        public static void handle(AdjustDistancePacket packet) {
            adjustDistance(packet.getPacketListener().getPlayer(), packet.steps());
        }

        public static void adjustDistance(ServerPlayer player, int steps) {
            requireThread(player);
            var context = ACTIVE.get(player);
            if (context != null) context.adjustDistance(steps);
        }

        public enum TargetKind { NONE, ENTITY, STRUCTURE }

        /** A server-thread observation for integrations and validation; it cannot drive the skill. */
        public record Diagnostic(TargetKind targetKind, StructureRef nativeRef, Vec3 localAnchor,
                                 int controlActiveTicks, double holdDistance, boolean nativeControlActive) {}

        public static Optional<Diagnostic> diagnostic(ServerPlayer player) {
            requireThread(player);
            var context = ACTIVE.get(player);
            if (context == null || context.ended) return Optional.empty();
            var structure = context.structureControl;
            return Optional.of(new Diagnostic(structure != null ? TargetKind.STRUCTURE
                    : context.controlledTarget != null ? TargetKind.ENTITY : TargetKind.NONE,
                    structure == null ? null : structure.reference(), structure == null ? null : structure.localPoint(),
                    context.activeTicks, context.holdDistance,
                    structure != null && structure.status() == StructureTraction.Status.ACTIVE));
        }

        private static void requireThread(ServerPlayer player) {
            Objects.requireNonNull(player, "player");
            if (!player.level().getServer().isSameThread()) {
                throw new IllegalStateException("Pneumatic Grasp requires the owning server thread");
            }
        }

        private static final class Context extends ServerContext {
            private final ResourceKey<Level> dimension;
            private final AeromanipResourceManager.UsageLease usageLease;
            private final UUID controllerId = UUID.randomUUID();
            private Entity controlledTarget;
            private StructureTractionSession structureControl;
            private double controlledSpeedCap;
            private double holdDistance = DEFAULT_CONTROL_DISTANCE;
            private int activeTicks;
            private boolean ended;

            private Context(ServerPlayer player) {
                super(player);
                dimension = player.level().dimension();
                usageLease = AbilitySystemServer.getSystem(player)
                        .getAeromanipResourceManager().beginUse(player);
            }

            private void end() {
                if (!ended) {
                    ended = true;
                    unregister();
                }
            }

            @SubscribeEvent
            public void onTick(ServerTickEvent.Pre event) {
                if (event.getServer() != player.level().getServer()) return;
                try {
                    tick();
                } catch (RuntimeException | Error failure) {
                    // Unknown failures must remain visible, but must not leave a held structure owned.
                    try { end(); }
                    catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                    throw failure;
                }
            }

            private void tick() {
                var skill = Skills.PNEUMATIC_GRASP.get();
                if (ended || player.hasDisconnected() || !player.isAlive()
                        || !player.level().dimension().equals(dimension) || !skill.isEnabled(player)) {
                    end();
                    return;
                }
                skill.reportActivity(player, false);
                var eye = player.getEyePosition();
                var look = player.getLookAngle().normalize();
                if (look.lengthSqr() <= 1.0e-8) {
                    if (structureControl != null) end();
                    return;
                }
                var skillLevel = Math.clamp(skill.getLevel(player), 0, 2);
                var milestone = skill.getEffectiveProficiencyMilestone(player);
                var range = 16.0 + skillLevel * 4.0 + (milestone >= 1 ? 8.0 : 0.0);
                if (structureControl != null) {
                    moveStructure(eye, look, skillLevel, range);
                    return;
                }
                if (!isValidTarget(controlledTarget, skillLevel, range)) {
                    controlledTarget = null;
                    var selected = findTarget(eye, look, skillLevel, range);
                    if (selected != null && selected.structureHit() != null) {
                        var hit = selected.structureHit();
                        var acquired = StructureTraction.tryAcquire(player, controllerId, hit,
                                new StructureTraction.Settings(range, 32.0,
                                        milestone >= 2 ? 144.0 : 120.0, 36.0, 1000.0, 3),
                                (actor, handle) -> actor == player && !player.isSpectator()
                                        && player.getAbilities().mayBuild && player.level().mayInteract(player,
                                        BlockPos.containing(handle.pose().localToWorldPoint(hit.localPoint()))));
                        if (acquired.status() != StructureTraction.Status.ACTIVE) {
                            // Occupied or unavailable foreground structures still occlude later targets.
                            end();
                            return;
                        }
                        structureControl = acquired.session().orElseThrow();
                        moveStructure(eye, look, skillLevel, range);
                        return;
                    }
                    controlledTarget = selected == null ? null : selected.entity();
                    controlledSpeedCap = controlledTarget instanceof Projectile
                            ? Math.max(0.1, controlledTarget.getDeltaMovement().length())
                            : 0.0;
                }
                if (controlledTarget == null) return;
                if (!consumeControlTick(skill)) return;
                if (milestone >= 3 && controlledTarget instanceof LivingEntity living
                        && living.onGround() && activeTicks % 5 == 0
                        && !skill.executeContinuousWithResource(
                        player,
                        _ -> 5.0f,
                        _ -> 2.0f,
                        (_, _) -> { },
                        true)) {
                    end();
                    return;
                }
                EntityMotionGuard.runWithMotionSource(
                        player,
                        () -> moveTarget(controlledTarget, eye, look, skillLevel));
                skill.reportActivity(player, true);
            }

            private boolean consumeControlTick(PneumaticGrasp skill) {
                activeTicks++;
                if (activeTicks % 10 != 0 || skill.executeContinuousWithResource(
                        player, _ -> 10.0f,
                        _ -> Math.max(0.0f, AeromanipConfig.skillFloat(
                                player, SkillNames.PNEUMATIC_GRASP, "compressedAirPerInterval", 2.0f)),
                        (_, _) -> { }, true)) return true;
                end();
                return false;
            }

            private record Target(Entity entity, StructureHit structureHit) {}

            private Target findTarget(Vec3 eye, Vec3 look, int skillLevel, double range) {
                var end = eye.add(look.scale(range));
                // Vanilla clip may read chunks; targeting must not activate an unloaded host corridor.
                if (!rayLoaded(eye, end)) return null;
                var blockHit = player.level().clip(new ClipContext(
                        eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                var blockDistance = blockHit.getType() == HitResult.Type.MISS
                        ? range * range
                        : eye.distanceToSqr(blockHit.getLocation());
                var entityHit = entityHit(eye, end, skillLevel);
                double entityDistance = entityHit == null ? Double.POSITIVE_INFINITY
                        : eye.distanceToSqr(entityHit.getLocation());
                var structureHit = StructureQueries.raycast(player.level(), eye, end).orElse(null);
                double structureDistance = structureHit == null ? Double.POSITIVE_INFINITY
                        : eye.distanceToSqr(structureHit.worldPoint());
                if (structureHit != null && structureDistance <= entityDistance && structureDistance < blockDistance) {
                    return new Target(null, structureHit);
                }
                if (entityHit == null || entityDistance >= blockDistance || entityDistance >= structureDistance) return null;
                return new Target(entityHit.getEntity(), null);
            }

            private EntityHitResult entityHit(Vec3 eye, Vec3 end, int skillLevel) {
                return ProjectileUtil.getEntityHitResult(player.level(), player, eye, end,
                        new AABB(eye, end).inflate(1.25), entity -> isControllable(entity, skillLevel), 0.8f);
            }

            private boolean isValidTarget(Entity target, int skillLevel, double range) {
                return isControllable(target, skillLevel)
                        && target.level() == player.level()
                        && target.distanceToSqr(player) <= (range + 2.0) * (range + 2.0)
                        && rayLoaded(player.getEyePosition(), target.getEyePosition())
                        && player.hasLineOfSight(target)
                        && !structureOccludes(player.getEyePosition(), target.getBoundingBox().getCenter());
            }

            private boolean rayLoaded(Vec3 from, Vec3 to) {
                int minX = Mth.floor(Math.min(from.x, to.x)) >> 4;
                int maxX = Mth.floor(Math.max(from.x, to.x)) >> 4;
                int minZ = Mth.floor(Math.min(from.z, to.z)) >> 4;
                int maxZ = Mth.floor(Math.max(from.z, to.z)) >> 4;
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (!player.level().hasChunk(x, z)) return false;
                    }
                }
                return true;
            }

            private boolean structureOccludes(Vec3 eye, Vec3 point) {
                double distance = eye.distanceToSqr(point);
                if (distance < 1.0e-12 || distance > StructureQueries.MAX_RAY_LENGTH * StructureQueries.MAX_RAY_LENGTH) {
                    return distance >= 1.0e-12;
                }
                return StructureQueries.raycast(player.level(), eye, point)
                        .filter(hit -> eye.distanceToSqr(hit.worldPoint()) < distance).isPresent();
            }

            private void moveStructure(Vec3 eye, Vec3 look, int skillLevel, double range) {
                if (structureControl.status() != StructureTraction.Status.ACTIVE) {
                    end();
                    return;
                }
                var anchor = structureControl.worldPoint();
                double distance = eye.distanceToSqr(anchor);
                var obstruction = distance < 1.0e-12 ? null : entityHit(eye, anchor, skillLevel);
                if (distance > range * range || obstruction != null
                        && eye.distanceToSqr(obstruction.getLocation()) < distance - 1.0e-8) {
                    end();
                    return;
                }
                var destination = player.isShiftKeyDown()
                        ? anchor.add(anchor.subtract(eye).normalize().scale(4.0))
                        : eye.add(look.scale(holdDistance));
                if (structureControl.update(destination, Vec3.ZERO) != StructureTraction.Status.ACTIVE) {
                    end();
                    return;
                }
                var skill = Skills.PNEUMATIC_GRASP.get();
                // Commands only reach the later physics tick. Failed payment closes them before that step.
                if (!consumeControlTick(skill)) return;
                spawnStructureEffect(eye, structureControl.worldPoint(), structureControl.targetPosition());
                skill.reportActivity(player, true);
            }

            private void spawnStructureEffect(Vec3 eye, Vec3 anchor, Vec3 destination) {
                if (activeTicks % 6 != 0) return;
                var beam = anchor.subtract(eye);
                if (beam.lengthSqr() > 1.0e-8) AeromanipVfx.stream(player.level(), eye, beam, beam.length());
                AeromanipVfx.vortex(player.level(), anchor, 0.4);
                AeromanipVfx.burst(player.level(), destination, 0.32);
            }

            private boolean isControllable(Entity entity, int skillLevel) {
                if (entity == null || entity == player || !entity.isAlive() || entity.isSpectator()) return false;
                if (entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof Projectile) {
                    return true;
                }
                if (entity instanceof BlockStructure) {
                    return AeromanipTargeting.canAffectNegatively(player, entity);
                }
                if (!(entity instanceof LivingEntity living)
                        || AeromanipTargeting.isBoss(living)
                        || !AeromanipTargeting.canAffectNegatively(player, living)) {
                    return false;
                }
                return entity instanceof Enemy
                        || skillLevel >= 2 && !living.onGround()
                        || Skills.PNEUMATIC_GRASP.get().hasProficiencyMilestone(player, 3);
            }

            private void adjustDistance(int steps) {
                if (steps == 0) return;
                var skillLevel = Math.clamp(Skills.PNEUMATIC_GRASP.get().getLevel(player), 0, 2);
                var maxDistance = 12.0 + skillLevel * 2.0;
                holdDistance = AeromanipTargeting.adjustControlDistance(
                        holdDistance, steps, DISTANCE_STEP, MIN_CONTROL_DISTANCE, maxDistance);
            }

            private void moveTarget(Entity target, Vec3 eye, Vec3 look, int skillLevel) {
                var targetCenter = target.getBoundingBox().getCenter();
                var destination = player.isShiftKeyDown()
                        ? targetCenter.add(targetCenter.subtract(eye).normalize().scale(4.0))
                        : eye.add(look.scale(holdDistance));
                var delta = destination.subtract(targetCenter);
                if (delta.lengthSqr() <= 0.12 * 0.12) {
                    AeromanipTargeting.scaleVelocity(target, 0.3);
                    spawnEffect(target, eye, destination);
                    return;
                }
                var lightTarget = target instanceof ItemEntity
                        || target instanceof ExperienceOrb
                        || target instanceof Projectile;
                var structureTarget = target instanceof BlockStructure;
                var hostileLiving = target instanceof Enemy;
                var forceMultiplier = AeromanipTargeting.forceMultiplier(player, target);
                if (target instanceof LivingEntity living && living.onGround()
                        && Skills.PNEUMATIC_GRASP.get().hasProficiencyMilestone(player, 3)) {
                    forceMultiplier *= 0.5;
                }
                if (forceMultiplier <= 0.0) return;
                var response = (lightTarget ? 0.52
                        : structureTarget ? 0.22
                        : hostileLiving ? 0.28 + skillLevel * 0.04 : 0.3)
                        * forceMultiplier;
                if (!lightTarget && Skills.PNEUMATIC_GRASP.get().hasProficiencyMilestone(player, 2)) response *= 1.2;
                var projectileSpeed = target instanceof Projectile ? Math.max(0.1, controlledSpeedCap) : 1.35;
                var targetSpeed = (lightTarget ? projectileSpeed
                        : structureTarget ? 0.85
                        : hostileLiving ? 0.6 + skillLevel * 0.1 : 0.7)
                        * forceMultiplier;
                AeromanipTargeting.steerVelocity(target, delta, response, targetSpeed);
                target.resetFallDistance();
                spawnEffect(target, eye, destination);
            }

            private void spawnEffect(Entity target, Vec3 eye, Vec3 destination) {
                if (!(player.level() instanceof ServerLevel level) || activeTicks % 6 != 0) return;
                var targetCenter = target.getBoundingBox().getCenter();
                var beam = targetCenter.subtract(eye);
                if (beam.lengthSqr() > 1.0e-8) {
                    AeromanipVfx.stream(level, eye, beam, beam.length());
                }
                AeromanipVfx.vortex(level, targetCenter,
                        Math.max(0.4, target.getBbWidth() * 0.7));
                AeromanipVfx.burst(level, destination, 0.32);
            }

            @Override
            protected void onUnregistered() {
                controlledTarget = null;
                controlledSpeedCap = 0.0;
                try {
                    if (structureControl != null) {
                        var released = structureControl;
                        structureControl = null;
                        released.close();
                    }
                } finally {
                    try { usageLease.close(); }
                    finally { ACTIVE.remove(player, this); }
                }
            }
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
            return PacketTypes.PNEUMATIC_GRASP_START.get();
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
            return PacketTypes.PNEUMATIC_GRASP_STOP.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class AdjustDistancePacket extends Packet<ServerGamePacketListenerImpl, AdjustDistancePacket> {
        public static final StreamCodec<ByteBuf, AdjustDistancePacket> CODEC = ByteBufCodecs.VAR_INT.map(
                AdjustDistancePacket::new, AdjustDistancePacket::steps);
        private final int steps;

        public AdjustDistancePacket(int steps) {
            this.steps = steps;
        }

        public int steps() {
            return steps;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, AdjustDistancePacket> getPacketType() {
            return PacketTypes.PNEUMATIC_GRASP_ADJUST_DISTANCE.get();
        }
    }
}
