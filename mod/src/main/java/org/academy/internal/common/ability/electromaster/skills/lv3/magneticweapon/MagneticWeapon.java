package org.academy.internal.common.ability.electromaster.skills.lv3.magneticweapon;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.graph.type.Value;
import org.academy.api.client.render.vfxgraph.render.GraphCamera;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.arc.ArcPath;
import org.academy.api.common.arc.PathModifier;
import org.academy.api.common.arc.modifier.ColorModifier;
import org.academy.api.common.arc.modifier.JaggedModifier;
import org.academy.api.common.arc.modifier.TaperModifier;
import org.academy.api.common.arc.path.LinePath;
import org.academy.api.common.arc.path.PolylinePath;
import org.academy.api.common.arc.property.AttributeCurve;
import org.academy.api.common.arc.property.ColorKnot;
import org.academy.api.common.arc.property.Gradient;
import org.academy.api.common.arc.property.Knot;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.hud.ability.ToggleStatusHud;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.BladeGeometry;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBlade;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBladeMotion;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBladeMotion.Phase;
import org.academy.internal.common.world.entity.skill.MagneticWeaponBladeMotion.Sweep;
import org.academy.internal.server.ability.AbilitySystemServer;
import org.academy.internal.server.ability.HostileTargets;
import org.academy.internal.server.team.TeamRelations;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;

public class MagneticWeapon extends Skill {
    private static final float RADIUS = 16.0f;
    private static final int ATTACK_COOLDOWN = 10;

    public MagneticWeapon() {
        super(Builder.of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(30)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.MAGNET_MANIPULATION));
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY, Client.CONFIG.getKeyBinding(Client.KEY,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G,
                                InputConstants.PRESS, 0)),
                _ -> Client.onToggle());
        ToggleStatusHud.Companion.registerStateProvider(Skills.MAGNETIC_WEAPON.get(), Client::isActive);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static boolean isSupportedWeapon(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.SWORDS);
    }

    @EventBusSubscriber(Dist.CLIENT)
    public static final class BladeEvents {
        private BladeEvents() {
        }

        @SubscribeEvent
        public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
            if (!event.getLevel().isClientSide()) return;
            if (event.getEntity() instanceof MagneticWeaponBlade blade) {
                MagneticWeapon.Client.MagneticWeaponBladeArcVfx.spawn(blade);
            }
        }
    }

    public static final class Client {

        public static final class MagneticWeaponBladeArcVfx {
            private static final double FULL_EFFECT_DISTANCE_SQR = 32.0 * 32.0;
            private static final double MAX_EFFECT_DISTANCE_SQR = 48.0 * 48.0;

            private final MagneticWeaponBlade blade;
            private final ArrayDeque<Vec3> history = new ArrayDeque<>();
            private final List<ArcPath> paths = new ArrayList<>();
            private Vec3 origin = Vec3.ZERO;
            private int lastEntityTick = Integer.MIN_VALUE;
            private boolean lastAttacking;
            private int trailDecayTicks;
            private int impactTicks;
            private Vec3 impactCenter = Vec3.ZERO;
            private Vec3 impactHalfSize = new Vec3(0.4, 0.7, 0.4);
            private float removedAt = Float.NaN;

            public static void spawn(MagneticWeaponBlade blade) {
                var adapter = new MagneticWeaponBladeArcVfx(blade);
                var effect = VfxGraphManager.INSTANCE.spawn(AcademyCraft.academy("vfxgraph/magnetic_weapon"),
                        blade.position().toVector3f());
                effect.bindArcs("paths", _ -> adapter.paths);
                effect.bindGameTime("time");
                effect.bindFrame(adapter::sample);
                effect.setRenderDistance(48);
            }

            private MagneticWeaponBladeArcVfx(MagneticWeaponBlade blade) {
                this.blade = blade;
            }

            private static Vec3 normalizedOr(Vec3 vector, Vec3 fallback) {
                return vector.lengthSqr() > 1.0E-8 ? vector.normalize() : fallback.normalize();
            }

            private boolean sample(ActiveEffect effect, GraphCamera camera, float partialTick) {
                paths.clear();
                if (blade.level() != Minecraft.getInstance().level) return false;
                boolean removed = blade.isRemoved() || !blade.isAlive();
                if (removed && !Float.isFinite(removedAt)) removedAt = effect.gameAgeSeconds();
                float fade = removed ? Math.max(0, 1 - (effect.gameAgeSeconds() - removedAt) / 0.15f) : 1;
                if (fade <= 0) return false;
                var cameraPosition = new Vec3(camera.position().x(), camera.position().y(), camera.position().z());
                var bladePosition = blade.isRemoved() || !blade.isAlive()
                        ? history.isEmpty() ? blade.position() : history.getFirst()
                        : blade.transform(partialTick).position();
                origin = bladePosition;
                effect.setPosition(origin.toVector3f());
                effect.setCullingSphere(origin.toVector3f(), 10);
                var distanceSqr = bladePosition.distanceToSqr(cameraPosition);
                if (distanceSqr > MAX_EFFECT_DISTANCE_SQR) {
                    history.clear();
                    lastEntityTick = blade.tickCount;
                    lastAttacking = blade.clientAttacking();
                    return true;
                }

                var status = Minecraft.getInstance().options.particles().get();
                var quality = Quality.from(status, distanceSqr <= FULL_EFFECT_DISTANCE_SQR);
                updateTickState(quality.maxHistory());
                effect.effect().setLiveParam("opacity", Value.of(fade * (quality.glow() ? 1f : 0.7f)));

                var seed = seed();
                var transform = blade.transform(partialTick);
                if (blade.tickCount <= 4 && !blade.isRemoved()) {
                    emitActivation(quality, bladePosition, seed);
                }
                if (!blade.clientAttacking() && !blade.isRemoved()) {
                    emitIdleSpark(bladePosition, seed,
                            Vec3.directionFromRotation(transform.pitch(), transform.yaw()));
                }

                emitTrail(quality, bladePosition, seed);
                emitImpact(quality, seed);
                return true;
            }

            private void updateTickState(int maxHistory) {
                if (blade.tickCount == lastEntityTick || blade.isRemoved()) return;
                lastEntityTick = blade.tickCount;

                if (impactTicks > 0) impactTicks--;
                var attacking = blade.clientAttacking();
                if (attacking && blade.popImpactCrossed()) {
                    captureImpact();
                    impactTicks = 3;
                }

                if (attacking) {
                    MagneticWeaponTrailBuilder.appendSample(history, blade.clientPosition(), maxHistory);
                    trailDecayTicks = 3;
                } else if (lastAttacking) {
                    trailDecayTicks = 3;
                } else if (trailDecayTicks > 0) {
                    trailDecayTicks--;
                    if (history.size() > 2) history.removeLast();
                    if (trailDecayTicks == 0) history.clear();
                }
                lastAttacking = attacking;
            }

            private void captureImpact() {
                var target = blade.level().getEntity(blade.getTargetId());
                if (target == null) {
                    impactCenter = blade.position();
                    impactHalfSize = new Vec3(0.4, 0.7, 0.4);
                    return;
                }
                var box = target.getBoundingBox();
                impactCenter = box.getCenter();
                impactHalfSize = new Vec3(
                        Math.max(0.3, box.getXsize() * 0.5),
                        Math.max(0.4, box.getYsize() * 0.5),
                        Math.max(0.3, box.getZsize() * 0.5)
                );
            }

            private void emitTrail(Quality quality,
                                   Vec3 bladePosition, long seed) {
                if (history.size() < 2) return;
                var points = new ArrayList<>(history);
                if (!blade.isRemoved()) points.set(0, bladePosition);
                while (points.size() > quality.maxHistory()) points.removeLast();
                emit(MagneticWeaponTrailBuilder.trail(points.stream().map(p -> p.subtract(origin)).toList(), seed, 0.7f));

                if (quality.maxForks() <= 0 || points.size() < 3) return;
                var random = new Random(seed ^ 0x6A09E667F3BCC909L);
                for (var i = 0; i < quality.maxForks(); i++) {
                    var index = 1 + random.nextInt(points.size() - 1);
                    var start = points.get(index);
                    var previous = points.get(Math.max(0, index - 1));
                    var tangent = normalizedOr(previous.subtract(start), new Vec3(0.0, 1.0, 0.0));
                    var randomDirection = normalizedOr(new Vec3(
                            random.nextDouble() - 0.5,
                            random.nextDouble() - 0.5,
                            random.nextDouble() - 0.5
                    ), new Vec3(1.0, 0.0, 0.0));
                    var perpendicular = normalizedOr(tangent.cross(randomDirection), new Vec3(1.0, 0.0, 0.0));
                    var length = 0.2 + random.nextDouble() * 0.4;
                    var end = start.add(perpendicular.scale(length)).add(tangent.scale(0.12));
                    emit(line(
                            start, end, seed + i * 31L + 7L, 0.32f
                    ));
                }
            }

            private void emitActivation(Quality quality,
                                        Vec3 bladePosition, long seed) {
                var owner = blade.level().getEntity(blade.getOwnerId());
                if (!(owner instanceof Avatar avatar)) return;
                var forward = Vec3.directionFromRotation(0.0f, avatar.getYRot()).normalize();
                var right = new Vec3(-forward.z, 0.0, forward.x);
                var handSign = avatar.getMainArm() == HumanoidArm.RIGHT ? 1.0 : -1.0;
                var hand = avatar.position()
                        .add(0.0, avatar.getBbHeight() * 0.65, 0.0)
                        .add(right.scale(0.28 * handSign))
                        .add(forward.scale(0.12));
                var count = quality.maxForks() > 0 ? 2 : 1;
                for (var i = 0; i < count; i++) {
                    var end = bladePosition.add(0.0, (i - 0.5) * 0.18, 0.0);
                    emit(line(
                            hand, end, seed + 101L * i, 0.45f
                    ));
                }
            }

            private void emitIdleSpark(Vec3 bladePosition, long seed, Vec3 direction) {
                var interval = 12 + Mth.positiveModulo(blade.getId(), 8);
                if (Mth.positiveModulo(blade.tickCount, interval) >= 2) return;
                var side = normalizedOr(direction.cross(new Vec3(0.0, 1.0, 0.0)), new Vec3(1.0, 0.0, 0.0));
                var start = bladePosition.subtract(direction.scale(0.25)).add(side.scale(0.12));
                var end = bladePosition.add(direction.scale(0.35)).subtract(side.scale(0.15));
                emit(line(start, end, seed, 0.28f));
            }

            private void emitImpact(Quality quality, long seed) {
                if (impactTicks <= 0) return;
                var count = switch (quality.status()) {
                    case ALL -> 6;
                    case DECREASED -> 5;
                    case MINIMAL -> 4;
                };
                var random = new Random(seed ^ 0xBB67AE8584CAA73BL);
                for (var i = 0; i < count; i++) {
                    var end = impactCenter.add(
                            (random.nextDouble() * 2.0 - 1.0) * impactHalfSize.x,
                            (random.nextDouble() * 2.0 - 1.0) * impactHalfSize.y,
                            (random.nextDouble() * 2.0 - 1.0) * impactHalfSize.z
                    );
                    var start = impactCenter.add(
                            (random.nextDouble() - 0.5) * 0.2,
                            (random.nextDouble() - 0.5) * 0.2,
                            (random.nextDouble() - 0.5) * 0.2
                    );
                    emit(line(
                            start, end, seed + 211L * i, 0.5f
                    ));
                }
            }

            private void emit(ArcPath path) {
                paths.add(path);
            }

            private ArcPath line(Vec3 start, Vec3 end, long seed, float thickness) {
                return MagneticWeaponTrailBuilder.line(start.subtract(origin), end.subtract(origin), seed, thickness);
            }

            private long seed() {
                return ((long) blade.getId() << 32)
                        ^ (blade.getAttackSequence() * 0x9E3779B9L)
                        ^ (blade.tickCount / 2L);
            }

            private record Quality(ParticleStatus status, int maxHistory, int maxForks, boolean glow) {
                private static Quality from(ParticleStatus status, boolean fullDistance) {
                    if (!fullDistance) {
                        var history = status == ParticleStatus.MINIMAL
                                ? MagneticWeaponTrailBuilder.MINIMAL_HISTORY_SIZE
                                : MagneticWeaponTrailBuilder.FULL_HISTORY_SIZE;
                        return new Quality(status, history, 0, false);
                    }
                    return switch (status) {
                        case ALL -> new Quality(status, MagneticWeaponTrailBuilder.FULL_HISTORY_SIZE, 2, true);
                        case DECREASED -> new Quality(status, MagneticWeaponTrailBuilder.FULL_HISTORY_SIZE, 1, true);
                        case MINIMAL -> new Quality(status, MagneticWeaponTrailBuilder.MINIMAL_HISTORY_SIZE, 0, false);
                    };
                }
            }
        }

        public static final class MagneticWeaponTrailBuilder {
            public static final int FULL_HISTORY_SIZE = 8;
            public static final int MINIMAL_HISTORY_SIZE = 6;
            public static final double DISCONTINUITY_DISTANCE_SQR = 16.0;

            private static final AttributeCurve TAPER = new AttributeCurve(List.of(
                    new Knot(0.0f, 1.0f),
                    new Knot(0.35f, 0.78f),
                    new Knot(0.72f, 0.28f),
                    new Knot(1.0f, 0.02f)
            ));
            private static final Gradient COLOR = new Gradient(List.of(
                    new ColorKnot(0.0f, new Vector3f(0.90f, 1.00f, 1.00f)),
                    new ColorKnot(0.52f, new Vector3f(0.18f, 0.75f, 1.00f)),
                    new ColorKnot(1.0f, new Vector3f(0.05f, 0.18f, 0.55f))
            ));

            private MagneticWeaponTrailBuilder() {
            }

            public static boolean appendSample(ArrayDeque<Vec3> history, Vec3 sample, int maxSize) {
                var discontinuity = !history.isEmpty()
                        && history.getFirst().distanceToSqr(sample) > DISCONTINUITY_DISTANCE_SQR;
                if (discontinuity) history.clear();
                if (history.isEmpty() || history.getFirst().distanceToSqr(sample) > 1.0E-4) {
                    history.addFirst(sample);
                }
                while (history.size() > Math.max(2, maxSize)) {
                    history.removeLast();
                }
                return discontinuity;
            }

            public static ArcPath trail(List<Vec3> points, long seed, float thickness) {
                List<Vector3fc> vertices = new ArrayList<>(points.size());
                for (var point : points) vertices.add(point.toVector3f());
                return new ArcPath(
                        new PolylinePath(vertices),
                        modifiers(seed, thickness),
                        2.2f,
                        List.of()
                );
            }

            public static ArcPath line(Vec3 start, Vec3 end, long seed, float thickness) {
                return new ArcPath(
                        new LinePath(start.toVector3f(), end.toVector3f()),
                        modifiers(seed, thickness),
                        2.2f,
                        List.of()
                );
            }

            public static List<PathModifier> modifiers(long seed, float thickness) {
                return List.of(
                        new JaggedModifier(0.12f, 2, seed),
                        new TaperModifier(TAPER, thickness),
                        new ColorModifier(COLOR)
                );
            }
        }

        public static final String KEY = SkillNames.MAGNETIC_WEAPON + "_toggle";
        public static Config CONFIG = new Config();

        private static void onToggle() {
            if (AbilitySystemClient.beginToggleRequest(Skills.MAGNETIC_WEAPON.get())) {
                MisakaNetworkClient.send(TogglePacket.INSTANCE);
            }
        }

        private static boolean isActive() {
            var player = Minecraft.getInstance().player;
            return player != null && player.getData(AttachmentTypes.MAGNETIC_WEAPON_DATA.get()).active();
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
        public static final class Routing {
            private static final double MAX_DETOUR_RATIO = 2.0;
            private static final int MAX_DETOURS = 6;
            private static final double CLEARANCE = 1.5;
            private static final double STEP = 0.4;
            private static final double OWNER_START_SKIP = 0.5;
            private static final double OWNER_CLEARANCE = 0.5;
            private static final double MIN_PROGRESS = 0.03;
            private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

            private Routing() {
            }

            public static List<Vec3> route(Level level, Vec3 start, Vec3 end, @Nullable Entity owner,
                                           Phase phase, Sweep sweep) {
                var straight = start.distanceTo(end);
                if (straight < 1.0E-4) return List.of(start, end);

                var avoiding = trace(level, start, end, owner, true, phase, sweep);
                if (avoiding != null && length(avoiding) <= straight * MAX_DETOUR_RATIO) return avoiding;

                var playerOnly = trace(level, start, end, owner, false, phase, sweep);
                return playerOnly != null ? playerOnly : List.of(start, end);
            }

            public static boolean hasRoute(Level level, Vec3 start, Vec3 end, @Nullable Entity owner,
                                           Phase phase, Sweep sweep) {
                var straight = start.distanceTo(end);
                if (straight < 1.0E-4) return true;
                var avoiding = trace(level, start, end, owner, true, phase, sweep);
                return avoiding != null && length(avoiding) <= straight * MAX_DETOUR_RATIO;
            }

            private static double length(List<Vec3> points) {
                var total = 0.0;
                for (var index = 1; index < points.size(); index++) {
                    total += points.get(index - 1).distanceTo(points.get(index));
                }
                return total;
            }

            private static @Nullable List<Vec3> trace(Level level, Vec3 start, Vec3 end, @Nullable Entity owner,
                                                      boolean blocks, Phase phase, Sweep sweep) {
                var waypoints = new ArrayList<Vec3>();
                waypoints.add(start);
                var current = start;
                for (var attempt = 0; attempt < MAX_DETOURS; attempt++) {
                    if (nearestHit(level, current, end, owner, blocks, phase, sweep) == null) {
                        waypoints.add(end);
                        return waypoints;
                    }
                    var detour = detour(level, current, end, owner, blocks, phase, sweep);
                    if (detour == null) return null;
                    waypoints.add(detour);
                    current = detour;
                }
                return null;
            }

            private static @Nullable Vec3 detour(Level level, Vec3 current, Vec3 end, @Nullable Entity owner,
                                                 boolean blocks, Phase phase, Sweep sweep) {
                var hit = nearestHit(level, current, end, owner, blocks, phase, sweep);
                if (hit == null) return null;

                var direction = end.subtract(current).normalize();
                var side = direction.cross(UP);
                side = side.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();

                var candidates = List.of(
                        hit.add(side.scale(CLEARANCE)),
                        hit.subtract(side.scale(CLEARANCE)),
                        hit.add(UP.scale(CLEARANCE)),
                        hit.add(side.scale(CLEARANCE * 0.7)).add(UP.scale(CLEARANCE * 0.7)),
                        hit.subtract(side.scale(CLEARANCE * 0.7)).add(UP.scale(CLEARANCE * 0.7)),
                        hit.subtract(UP.scale(CLEARANCE * 0.5))
                );

                Vec3 best = null;
                var bestCost = Double.MAX_VALUE;
                for (var candidate : candidates) {
                    var fraction = progress(candidate, current, end);
                    if (collides(level, candidate, direction, owner, blocks, phase, fraction, sweep)) continue;
                    if (fraction <= MIN_PROGRESS) continue;
                    if (nearestHit(level, current, candidate, owner, blocks, phase, sweep) != null) continue;

                    var cost = current.distanceTo(candidate) + candidate.distanceTo(end)
                            + Math.abs(candidate.y - hit.y) * 0.5;
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = candidate;
                    }
                }
                return best;
            }

            private static boolean collides(Level level, Vec3 point, Vec3 direction, @Nullable Entity owner,
                                            boolean blocks, Phase phase, double fraction, Sweep sweep) {
                var blade = bladeAt(point, direction, phase, fraction, sweep);
                if (blocks && intersectsBlocks(level, blade)) return true;
                return owner != null && intersects(blade, owner.getBoundingBox().inflate(OWNER_CLEARANCE));
            }

            private static Obb bladeAt(Vec3 point, Vec3 direction, Phase phase, double fraction, Sweep sweep) {
                var facing = MagneticWeaponBladeMotion.facingDirection(
                        direction.lengthSqr() < 1.0E-8 ? UP : direction.normalize(), phase, fraction, sweep);
                var axes = BladeGeometry.axes(
                        BladeGeometry.yawFromDirection(facing),
                        BladeGeometry.pitchFromDirection(facing),
                        MagneticWeaponBladeMotion.rollAt(phase, fraction, sweep)
                );
                return new Obb(point, axes[0], axes[1], axes[2]);
            }

            private static boolean intersectsBlocks(Level level, Obb blade) {
                for (var shape : level.getBlockCollisions(null, blade.bounds())) {
                    if (intersects(blade, shape.bounds())) return true;
                }
                return false;
            }

            private static boolean intersects(Obb blade, AABB box) {
                var offset = box.getCenter().subtract(blade.center());
                var extents = blade.extents();
                var axes = blade.axes();
                var worldHalf = new Vec3(box.getXsize() * 0.5, box.getYsize() * 0.5, box.getZsize() * 0.5);

                for (var axis : axes) {
                    if (separated(axes, extents, worldHalf, offset, axis)) return false;
                }
                for (var index = 0; index < 3; index++) {
                    if (separated(axes, extents, worldHalf, offset, worldAxis(index))) return false;
                }
                for (var bladeAxis : axes) {
                    for (var index = 0; index < 3; index++) {
                        var axis = bladeAxis.cross(worldAxis(index));
                        if (axis.lengthSqr() < 1.0E-9) continue;
                        if (separated(axes, extents, worldHalf, offset, axis.normalize())) return false;
                    }
                }
                return true;
            }

            private static boolean separated(
                    List<Vec3> axes, double[] extents, Vec3 worldHalf, Vec3 offset, Vec3 axis
            ) {
                var bladeRadius = 0.0;
                for (var index = 0; index < 3; index++) {
                    bladeRadius += extents[index] * Math.abs(axes.get(index).dot(axis));
                }
                var worldRadius = Math.abs(worldHalf.x * axis.x)
                        + Math.abs(worldHalf.y * axis.y)
                        + Math.abs(worldHalf.z * axis.z);
                return Math.abs(offset.dot(axis)) > bladeRadius + worldRadius;
            }

            private static Vec3 worldAxis(int index) {
                return switch (index) {
                    case 0 -> new Vec3(1.0, 0.0, 0.0);
                    case 1 -> new Vec3(0.0, 1.0, 0.0);
                    default -> new Vec3(0.0, 0.0, 1.0);
                };
            }

            private static double progress(Vec3 point, Vec3 start, Vec3 end) {
                var axis = end.subtract(start);
                var lengthSqr = axis.lengthSqr();
                if (lengthSqr < 1.0E-8) return 0.0;
                return point.subtract(start).dot(axis) / lengthSqr;
            }

            private static @Nullable Vec3 nearestHit(Level level, Vec3 from, Vec3 to, @Nullable Entity owner,
                                                     boolean blocks, Phase phase, Sweep sweep) {
                var length = from.distanceTo(to);
                if (length < 1.0E-6) return null;

                var direction = to.subtract(from).scale(1.0 / length);
                var steps = Math.max(1, Mth.ceil(length / STEP));
                var ownerBox = owner == null ? null : owner.getBoundingBox().inflate(OWNER_CLEARANCE);
                for (var index = 0; index <= steps; index++) {
                    var fraction = index / (double) steps;
                    var point = from.lerp(to, fraction);
                    var blade = bladeAt(point, direction, phase, fraction, sweep);
                    if (blocks && intersectsBlocks(level, blade)) return point;
                    if (ownerBox != null && fraction * length > OWNER_START_SKIP && intersects(blade, ownerBox)) return point;
                }
                return null;
            }

            private record Obb(Vec3 center, Vec3 axisX, Vec3 axisY, Vec3 axisZ) {
                private List<Vec3> axes() {
                    return List.of(axisX, axisY, axisZ);
                }

                private double[] extents() {
                    return new double[]{
                            BladeGeometry.HALF_EXTENTS.x,
                            BladeGeometry.HALF_EXTENTS.y,
                            BladeGeometry.HALF_EXTENTS.z
                    };
                }

                private AABB bounds() {
                    var x = Math.abs(axisX.x) * BladeGeometry.HALF_EXTENTS.x
                            + Math.abs(axisY.x) * BladeGeometry.HALF_EXTENTS.y
                            + Math.abs(axisZ.x) * BladeGeometry.HALF_EXTENTS.z;
                    var y = Math.abs(axisX.y) * BladeGeometry.HALF_EXTENTS.x
                            + Math.abs(axisY.y) * BladeGeometry.HALF_EXTENTS.y
                            + Math.abs(axisZ.y) * BladeGeometry.HALF_EXTENTS.z;
                    var z = Math.abs(axisX.z) * BladeGeometry.HALF_EXTENTS.x
                            + Math.abs(axisY.z) * BladeGeometry.HALF_EXTENTS.y
                            + Math.abs(axisZ.z) * BladeGeometry.HALF_EXTENTS.z;
                    return new AABB(center.x - x, center.y - y, center.z - z, center.x + x, center.y + y, center.z + z);
                }
            }
        }

        private static final Map<Player, Context> CONTEXT_MAP = createContextMap();

        private Server() {
        }

        public static float calculateDamage(float attackDamage, float playerMultiplier) {
            return Math.max(0.0f, attackDamage) * 0.6f * Math.max(0.0f, playerMultiplier);
        }

        public static boolean isActive(ServerPlayer player) {
            return CONTEXT_MAP.containsKey(player) && Skills.MAGNETIC_WEAPON.get().isEnabled(player);
        }

        public static void forceDisable(ServerPlayer player) {
            var context = CONTEXT_MAP.remove(player);
            if (context != null) context.end(false);
            var skill = Skills.MAGNETIC_WEAPON.get();
            var system = AbilitySystemServer.getSystem(player);
            var data = skill.getRuntimeData(player).orElse(null);
            if (data != null && data.isEnabled()) {
                system.toggleSkill(player.getUUID(), skill.getKeyString());
            }
            system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
            clearData(player);
        }

        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (isActive(player)) {
                forceDisable(player);
                return;
            }
            var weaponSlot = findFirstHotbarWeaponSlot(player);
            if (weaponSlot < 0) return;

            var skill = Skills.MAGNETIC_WEAPON.get();
            if (!skill.isEnabled(player)) skill.toggle(player);
            if (!skill.isEnabled(player)) return;
            var context = new Context(player, weaponSlot);
            CONTEXT_MAP.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        private static int findFirstHotbarWeaponSlot(ServerPlayer player) {
            for (var slot = 0; slot < 9; slot++) {
                if (isSupportedWeapon(player.getInventory().getItem(slot))) return slot;
            }
            return -1;
        }

        private static void clearData(ServerPlayer player) {
            player.setData(AttachmentTypes.MAGNETIC_WEAPON_DATA.get(), Data.DEFAULT);
            player.syncData(AttachmentTypes.MAGNETIC_WEAPON_DATA.get());
        }

        public static final class Context extends ServerContext {
            private static final int MAX_ROUTE_CANDIDATES = 3;
            private static final double CHAIN_CHARGE_DISTANCE = 3.5;

            private final MagneticWeaponBlade blade;
            private int weaponSlot;
            private int attackCooldown;
            private int attackSequence;
            private @Nullable PendingAttack pendingAttack;
            private ItemStack magnetizedWeapon = ItemStack.EMPTY;
            private boolean temporaryMagnetized;
            private boolean ended;

            private Context(ServerPlayer player, int weaponSlot) {
                super(player);
                this.weaponSlot = weaponSlot;
                bindWeapon(weaponSlot);
                blade = new MagneticWeaponBlade(EntityTypes.MAGNETIC_WEAPON_BLADE.get(), player.level());
                blade.configure(player, player.getInventory().getItem(weaponSlot));
                player.level().addFreshEntity(blade);
                syncData();
            }

            private boolean hasRoute(LivingEntity target) {
                return Routing.hasRoute(
                        player.level(), blade.position(), target.getBoundingBox().getCenter(), player,
                        MagneticWeaponBladeMotion.Phase.STRIKE, blade.sweep());
            }

            private static boolean isEnemy(ServerPlayer player, LivingEntity entity) {
                if (entity == player || !entity.isAlive() || TeamRelations.areAllied(player, entity)) return false;
                if (PvpSetting.shouldPrevent(player, entity)) return false;
                if (entity instanceof ServerPlayer target
                        && (target.isCreative() || target.isSpectator())) return false;
                return HostileTargets.isMarked(player, entity)
                        || player.getLastHurtByMob() == entity
                        || player.getLastHurtByPlayer() == entity
                        || entity instanceof Mob mob && mob.getTarget() == player;
            }

            private static void playArcSound(ServerLevel level, Vec3 position,
                                             float volume, float pitch) {
                level.playSound(
                        null,
                        position.x,
                        position.y,
                        position.z,
                        SoundEvents.ARC_WEAK.get(),
                        SoundSource.PLAYERS,
                        volume,
                        pitch
                );
            }

            @SubscribeEvent
            public void onTick(ServerTickEvent.Pre event) {
                var skill = Skills.MAGNETIC_WEAPON.get();
                if (!skill.isEnabled(player)
                        || !player.isAlive()
                        || player.hasDisconnected()
                        || blade.isRemoved()) {
                    end(true);
                    return;
                }

                var system = AbilitySystemServer.getSystem(player);
                if (!system.ensurePermanentOccupation(
                        player.getUUID(), skill.getMaintenanceCost(player), skill)) {
                    end(true);
                    return;
                }

                if (attackCooldown > 0) attackCooldown--;
                if (pendingAttack != null && player.level() instanceof ServerLevel level) {
                    advanceAttack(level, system);
                }

                if (pendingAttack == null) {
                    var nextWeaponSlot = Server.findFirstHotbarWeaponSlot(player);
                    if (nextWeaponSlot < 0) {
                        end(true);
                        return;
                    }
                    if (nextWeaponSlot != weaponSlot
                            || player.getInventory().getItem(nextWeaponSlot) != magnetizedWeapon) {
                        bindWeapon(nextWeaponSlot);
                    }
                    blade.setWeapon(player.getInventory().getItem(weaponSlot));
                }

                var stack = player.getInventory().getItem(weaponSlot);

                if (pendingAttack == null && attackCooldown <= 0
                        && player.level() instanceof ServerLevel level) {
                    var milestone = skill.getEffectiveProficiencyMilestone(player);
                    if (milestone >= 3 && player.tickCount % 20 == 0 && interceptProjectile(level, stack)) {
                        syncData();
                        return;
                    }
                    var target = selectTarget(level);
                    if (target != null) startAttack(target, stack);
                }
                syncData();
            }

            private @Nullable LivingEntity selectTarget(ServerLevel level) {
                var skill = Skills.MAGNETIC_WEAPON.get();
                var milestone = skill.getEffectiveProficiencyMilestone(player);
                var radius = skill.scaledRange(player, milestone >= 2 ? 20.0f : RADIUS);
                var candidates = level.getEntitiesOfClass(
                        LivingEntity.class,
                        player.getBoundingBox().inflate(radius),
                        entity -> isEnemy(player, entity)
                );
                return candidates.stream()
                        .filter(player::hasLineOfSight)
                        .min(Comparator.comparingDouble(player::distanceToSqr))
                        .or(() -> candidates.stream()
                                .sorted(Comparator.comparingDouble(player::distanceToSqr))
                                .limit(MAX_ROUTE_CANDIDATES)
                                .filter(this::hasRoute)
                                .findFirst())
                        .orElse(null);
            }

            private Vec3 chargePoint(LivingEntity target) {
                var center = target.getBoundingBox().getCenter();
                var away = MagneticWeaponBlade.idlePosition(player).subtract(center);
                return away.lengthSqr() < 1.0E-6
                        ? center
                        : center.add(away.normalize().scale(CHAIN_CHARGE_DISTANCE));
            }

            private void startAttack(LivingEntity target, ItemStack stack) {
                attackSequence++;
                blade.startAttack(target.getId(), attackSequence);
                pendingAttack = new PendingAttack(
                        target.getId(),
                        weaponSlot,
                        stack.copyWithCount(1),
                        new MagneticWeaponAttackTimeline(blade.plan())
                );
                attackCooldown = Skills.MAGNETIC_WEAPON.get().hasProficiencyMilestone(player, 2) ? 8 : ATTACK_COOLDOWN;
            }

            private boolean interceptProjectile(ServerLevel level, ItemStack stack) {
                var look = player.getLookAngle();
                var interceptRadius = Skills.MAGNETIC_WEAPON.get().scaledRange(player, 20.0f);
                var projectile = level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(interceptRadius),
                                shot -> shot.isAlive() && shot.getOwner() != player
                                        && shot.position().subtract(player.position()).dot(look) > 0.0
                                        && player.position().subtract(shot.position()).dot(shot.getDeltaMovement()) > 0.0)
                        .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
                if (projectile == null) return false;
                var source = projectile.getOwner();
                projectile.discard();
                if (source instanceof LivingEntity living && isEnemy(player, living)) startAttack(living, stack);
                return true;
            }

            private void advanceAttack(ServerLevel level, AbilitySystemServer system) {
                var pending = pendingAttack;
                if (pending == null) return;

                var elapsed = pending.timeline.advance();
                if (pending.timeline.isFinished()) {
                    var next = selectTarget(level);
                    if (next != null) {
                        startAttack(next, player.getInventory().getItem(weaponSlot));
                        return;
                    }
                    blade.finishAttack();
                    pendingAttack = null;
                    return;
                }

                blade.setAttackElapsed(elapsed);
                if (elapsed <= pending.timeline.impactElapsed()
                        && !matchesBoundWeapon(pending)) {
                    pending.timeline.cancel();
                }
                if (elapsed == 1) {
                    playArcSound(level, blade.position(), 0.35f, 1.3f);
                }
                if (elapsed == pending.timeline.impactElapsed()) {
                    resolveImpact(level, system, pending);
                    var next = selectTarget(level);
                    if (next != null) blade.beginCharge(chargePoint(next));
                }
            }

            private boolean matchesBoundWeapon(PendingAttack pending) {
                var current = player.getInventory().getItem(pending.weaponSlot);
                return isSupportedWeapon(current)
                        && ItemStack.isSameItemSameComponents(current, pending.weaponSnapshot);
            }

            private void resolveImpact(ServerLevel level, AbilitySystemServer system, PendingAttack pending) {
                if (!matchesBoundWeapon(pending)) pending.timeline.cancel();
                if (!pending.timeline.claimImpact()) return;
                var entity = level.getEntity(pending.targetId);
                if (!(entity instanceof LivingEntity target)
                        || target.level() != level
                        || !isEnemy(player, target)) {
                    return;
                }

                try (var _ = MagneticWeaponPlayerState.open(
                        player, pending.weaponSlot, -1.0f);
                     var _ = MagneticWeaponAttackContext.open(
                             player,
                             target,
                             system.getPlayerDamageMultiplier(player.getUUID())
                     )) {
                    player.attack(target);
                    var weaponAfterAttack = player.getInventory().getItem(pending.weaponSlot);
                    if (!weaponAfterAttack.isEmpty()) {
                        weaponAfterAttack.onEntitySwing(player, InteractionHand.MAIN_HAND);
                    }
                }
                playArcSound(level, target.getBoundingBox().getCenter(), 0.55f, 0.95f);
            }

            private void syncData() {
                var hideMainHand = weaponSlot >= 0
                        && player.getInventory().getSelectedSlot() == weaponSlot
                        && isSupportedWeapon(player.getInventory().getItem(weaponSlot));
                var data = new Data(true, weaponSlot, hideMainHand);
                if (data.equals(player.getData(AttachmentTypes.MAGNETIC_WEAPON_DATA.get()))) return;
                player.setData(AttachmentTypes.MAGNETIC_WEAPON_DATA.get(), data);
                player.syncData(AttachmentTypes.MAGNETIC_WEAPON_DATA.get());
            }

            private void end(boolean disableSkill) {
                if (ended) return;
                ended = true;
                Server.CONTEXT_MAP.remove(player, this);
                if (disableSkill && Skills.MAGNETIC_WEAPON.get().isEnabled(player)) {
                    Skills.MAGNETIC_WEAPON.get().toggle(player);
                }
                clearTemporaryMagnetization();
                blade.discard();
                Server.clearData(player);
                unregister();
            }

            private void bindWeapon(int slot) {
                clearTemporaryMagnetization();
                weaponSlot = slot;
                magnetizedWeapon = player.getInventory().getItem(slot);
                temporaryMagnetized = MagneticWeaponEnchantments.addTemporary(
                        player.registryAccess(), magnetizedWeapon);
                player.getInventory().setChanged();
            }

            private void clearTemporaryMagnetization() {
                if (temporaryMagnetized && !magnetizedWeapon.isEmpty()) {
                    MagneticWeaponEnchantments.removeTemporary(player.registryAccess(), magnetizedWeapon);
                    player.getInventory().setChanged();
                }
                magnetizedWeapon = ItemStack.EMPTY;
                temporaryMagnetized = false;
            }

            private record PendingAttack(int targetId, int weaponSlot, ItemStack weaponSnapshot,
                                         MagneticWeaponAttackTimeline timeline) {
            }
        }
    }

    public record Data(boolean active, int weaponSlot, boolean hideMainHand) {
        public static final Data DEFAULT = new Data(false, -1, false);
        public static final StreamCodec<ByteBuf, Data> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Data::active,
                ByteBufCodecs.INT, Data::weaponSlot,
                ByteBufCodecs.BOOL, Data::hideMainHand,
                Data::new
        );
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);

        private TogglePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.MAGNETIC_WEAPON_TOGGLE.get();
        }
    }
}
