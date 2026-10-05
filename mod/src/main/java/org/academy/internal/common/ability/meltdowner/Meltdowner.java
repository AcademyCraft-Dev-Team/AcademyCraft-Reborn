package org.academy.internal.common.ability.meltdowner;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.academy.AcademyCraft;
import org.academy.api.client.render.vfxgraph.instance.EffectInstanceGeometrySource;
import org.academy.api.client.render.vfxgraph.instance.InstanceGeometryFrame;
import org.academy.api.client.render.vfxgraph.render.GraphCamera;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.VertexUtil;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.vfx.SkillVfxState;
import org.academy.internal.client.render.vfxgraph.SkillVfxStates;
import org.academy.internal.client.renderer.entity.ReflectedBeamVisualGeometry;
import org.academy.internal.common.ability.development.AbilityDevelopmentProfiles;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Meltdowner extends AbilityCategory {
    private static final int RADIATION_TICKS = 200;

    public void onDamageCompleted(LivingEntity target, DamageContainer hit, float healthDamage) {
        if (!(hit.getSource() instanceof SkillDamageSource source)
                || source.getSkill().getCategory() != this
                || !(healthDamage > 0.0f)) return;
        applyRadiation(target);
    }

    public void applyRadiation(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel) || !target.isAlive()) return;
        for (var effect : List.of(MobEffects.NAUSEA, MobEffects.SLOWNESS,
                MobEffects.MINING_FATIGUE, MobEffects.WEAKNESS)) {
            target.addEffect(new MobEffectInstance(effect, RADIATION_TICKS, 0));
        }
    }

    public Meltdowner() {
        super(0.1F, AbilityDevelopmentProfiles.MELTDOWNER);
    }

    @Override
    public Optional<ResourceKey<DamageType>> getDefaultDamageType() {
        return Optional.of(DamageTypes.MELT_DAMAGE);
    }

    @Override
    public Identifier getIcon() {
        return R.textures.ability.meltdowner.icon;
    }

    @Override
    public String getDisplayName() {
        return "Meltdowner";
    }

    public static final class Client {
        private Client() {
        }

        private static final Map<Long, BeamReplica> BEAM_REPLICAS = new HashMap<>();
        private static final Map<Long, Long> BEAM_RECEIVED_AT = new HashMap<>();

        public static void registerBeamSession() {
            SkillVfxStates.registerSession(new SkillVfxStates.Session() {
                @Override
                public void accept(long id, SkillVfxState state, long tick) {
                    if (state instanceof SkillVfxState.Beam beam) acceptBeam(id, beam, tick);
                    else if (state instanceof SkillVfxState.End end) endBeam(id, end, tick);
                }

                @Override
                public void tick(long tick) {
                    var iterator = BEAM_REPLICAS.entrySet().iterator();
                    while (iterator.hasNext()) {
                        var entry = iterator.next();
                        var replica = entry.getValue();
                        if (tick - BEAM_RECEIVED_AT.getOrDefault(entry.getKey(), tick) > 100) {
                            replica.release();
                        } else if (replica.apply(tick)) {
                            continue;
                        }
                        BEAM_RECEIVED_AT.remove(entry.getKey());
                        iterator.remove();
                    }
                }

                @Override
                public void reset() {
                    BEAM_REPLICAS.values().forEach(BeamReplica::release);
                    BEAM_REPLICAS.clear();
                    BEAM_RECEIVED_AT.clear();
                }
            });
        }

        private static void acceptBeam(long id, SkillVfxState.Beam state, long tick) {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            var existing = BEAM_REPLICAS.get(id);
            if (existing == null) {
                var mirror = new HighSpeedElectronBeam(EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get(), level);
                var replica = new BeamReplica(mirror, state, tick);
                BEAM_REPLICAS.put(id, replica);
                BEAM_RECEIVED_AT.put(id, tick);
                replica.apply(tick);
                replica.vfx.start();
                return;
            }
            existing.state = state;
            BEAM_RECEIVED_AT.put(id, tick);
            existing.apply(tick);
        }

        private static void endBeam(long id, SkillVfxState.End end, long tick) {
            var replica = BEAM_REPLICAS.get(id);
            if (replica == null) return;
            BEAM_REPLICAS.remove(id);
            BEAM_RECEIVED_AT.remove(id);
            if (!end.hidden() && replica.entity instanceof HighSpeedElectronBeam beam
                    && beam.hasFired() && !beam.isContinuous()) {
                BEAM_REPLICAS.put(id, replica);
                BEAM_RECEIVED_AT.put(id, tick);
                replica.endingAt = tick + Math.max(3, beam.currentRayLifeTicks);
                return;
            }
            replica.release();
        }

        private static final class BeamReplica {
            final HighSpeedElectronBeam entity;
            final ElectronBeamGraphVfx vfx;
            SkillVfxState.Beam state;
            long receivedAt;
            long endingAt = Long.MAX_VALUE;

            BeamReplica(HighSpeedElectronBeam entity, SkillVfxState.Beam state, long now) {
                this.entity = entity;
                this.vfx = new ElectronBeamGraphVfx(entity);
                this.state = state;
                receivedAt = now;
            }

            boolean apply(long now) {
                if (endingAt != Long.MAX_VALUE && now >= endingAt) {
                    release();
                    return false;
                }
                entity.applyVisualSnapshot(state, Math.min(25f, (float) (now - receivedAt)));
                return true;
            }

            void release() {
                vfx.expire();
            }
        }

        public static final class ElectronBeamGraphVfx {
            private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
            private static final double DIRECTION_EPSILON_SQUARED = 1.0e-12;
            private static final float SHAFT_RADIUS_SCALE = 0.25f;
            private static final float BALL_RADIUS_SCALE = 0.185f;
            private static final float RETURN_WIDTH_SCALE = 0.9f;
            private static final float RETURN_BALL_SCALE = 0.8f;
            private static final float CULLING_MARGIN = 14.0f;
            private static final float MINIMUM_FAR_PLANE = 384.0f;
            private static final float RAY_DECAY_TICKS = HighSpeedElectronBeam.MAX_RAY_LIFE_TICKS - 1.0f;
            private static final float TICKS_PER_SECOND = 20.0f;
            private static final Identifier SHAFT_ASSET = AcademyCraft.academy("vfxgraph/electron_beam");
            private static final Identifier MUZZLE_ASSET = AcademyCraft.academy("vfxgraph/electron_beam_muzzle");

            private final HighSpeedElectronBeam beam;
            private final BeamInstanceGeometry shaft;
            private final BeamInstanceGeometry muzzle;
            private @Nullable ActiveEffect shaftEffect;
            private @Nullable ActiveEffect muzzleEffect;
            private float peakAge = -1.0f;
            private @Nullable Vec3 attachedOrigin;
            private float attachedXRot;
            private float attachedYRot;
            private boolean expired;

            public ElectronBeamGraphVfx(HighSpeedElectronBeam beam) {
                this.beam = beam;
                this.shaft = new BeamInstanceGeometry(BeamInstanceGeometry.Kind.SHAFT);
                this.muzzle = new BeamInstanceGeometry(BeamInstanceGeometry.Kind.BALL);
            }

            public void start() {
                if (shaftEffect != null || expired) return;
                shaftEffect = spawnSegment(SHAFT_ASSET, shaft);
                muzzleEffect = spawnSegment(MUZZLE_ASSET, muzzle);
            }

            private ActiveEffect spawnSegment(Identifier asset, BeamInstanceGeometry geometry) {
                var effect = VfxGraphManager.INSTANCE.spawn(asset, new Vector3f());
                effect.bindInstanceGeometry(geometry);
                effect.bindFrame(this::sample);
                effect.setRenderDistance(256f);
                return effect;
            }

            public void expire() {
                expired = true;
                shaft.reset();
                muzzle.reset();
                if (shaftEffect != null) shaftEffect.stop();
                if (muzzleEffect != null) muzzleEffect.stop();
            }

            public static float shaftRadius(float progress, boolean isCharging, float widthScale) {
                return (isCharging ? 0.0f : progress) * SHAFT_RADIUS_SCALE * widthScale;
            }

            public static float ballRadius(float progress) {
                return progress * BALL_RADIUS_SCALE;
            }

            public static float beamProgress(float ageTicks, float chargeTicks, float peakAge, boolean continuous) {
                if (continuous) return 1.0f;
                var charge = Math.max(1.0f, chargeTicks);
                if (peakAge < 0.0f) {
                    return Mth.clamp(ageTicks / charge, 0.0f, 1.0f);
                }
                var peak = Mth.clamp(peakAge / charge, 0.0f, 1.0f);
                return Mth.clamp(peak - (ageTicks - peakAge) / RAY_DECAY_TICKS, 0.0f, 1.0f);
            }

            public static Quaternionf beamRotation(float xRot, float yRot) {
                return new Quaternionf()
                        .rotateY((90.0f - yRot) * Mth.DEG_TO_RAD)
                        .rotateZ((90.0f + xRot) * Mth.DEG_TO_RAD);
            }

            private boolean sample(ActiveEffect effect, GraphCamera camera, float partialTick) {
                if (expired) return false;
                if (beam.isRemoved() || !beam.isAlive()) {
                    expire();
                    return false;
                }

                var age = effect.gameAgeSeconds() * TICKS_PER_SECOND;
                var chargeTicks = Math.max(1.0f, beam.getAttackDelayTicks());
                if (peakAge < 0.0f && beam.hasFired()) peakAge = age;
                var isCharging = !beam.isContinuous() && !beam.hasFired();
                var progress = beamProgress(age, chargeTicks, peakAge, beam.isContinuous());

                var attachment = beam.getAttachment();
                if (attachment != null && !beam.hasFired()) {
                    var owner = beam.level().getEntity(attachment.ownerEntityId());
                    if (owner != null) {
                        attachedOrigin = attachment.origin(owner, partialTick);
                        attachedXRot = attachment.xRot(owner, partialTick);
                        attachedYRot = attachment.yRot(owner, partialTick);
                    }
                }
                Vec3 origin;
                float xRot;
                float yRot;
                if (attachedOrigin != null) {
                    origin = attachedOrigin;
                    xRot = attachedXRot;
                    yRot = attachedYRot;
                } else {
                    origin = beam.position();
                    xRot = beam.getXRot();
                    yRot = beam.getYRot();
                }
                var logicalDirection = Vec3.directionFromRotation(xRot, yRot);
                var visualStart = origin;
                var visualSideOffset = beam.getVisualSideOffset();
                if (Float.isFinite(visualSideOffset) && Math.abs(visualSideOffset) > 1.0e-4f) {
                    var horizontalForward = Vec3.directionFromRotation(0.0f, yRot);
                    var right = horizontalForward.cross(WORLD_UP);
                    if (right.lengthSqr() > DIRECTION_EPSILON_SQUARED) {
                        visualStart = origin.add(right.normalize().scale(visualSideOffset));
                    }
                }

                var originalLength = ReflectedBeamVisualGeometry.safeLength(beam.getBeamLength());
                var widthScale = Float.isFinite(beam.getBeamScale())
                        ? Math.max(0.0f, beam.getBeamScale())
                        : 0.0f;
                var visualEnd = origin.add(logicalDirection.scale(originalLength));
                var width = shaftRadius(progress, isCharging, widthScale);
                var radius = ballRadius(progress);
                var farthestEnd = visualEnd;
                var farthestLength = originalLength;

                shaft.reset();
                muzzle.reset();
                if (!beam.isReflectionActive()) {
                    poseSegment(shaft, visualStart, visualEnd, width);
                    poseBall(muzzle, visualStart, visualEnd, radius);
                } else {
                    var reflectedLength = Mth.clamp(beam.getReflectionDistance(), 0.0f, originalLength);
                    var reflectionPoint = origin.add(logicalDirection.scale(reflectedLength));
                    var returnEnd = ReflectedBeamVisualGeometry.directionalEnd(
                            reflectionPoint,
                            beam.getReflectionReturnDirection(),
                            beam.getReflectionReturnLength()
                    );
                    poseSegment(shaft, visualStart, reflectionPoint, width);
                    poseBall(muzzle, visualStart, reflectionPoint, radius);
                    poseSegment(shaft, reflectionPoint, returnEnd,
                            progress * SHAFT_RADIUS_SCALE * widthScale * RETURN_WIDTH_SCALE);
                    poseBall(muzzle, reflectionPoint, returnEnd, radius * RETURN_BALL_SCALE);
                    farthestEnd = returnEnd;
                    farthestLength = Math.max(originalLength,
                            (float) reflectionPoint.distanceTo(returnEnd));
                }

                var margin = CULLING_MARGIN * Math.max(1.0f, widthScale);
                setBounds(shaftEffect, visualStart, farthestEnd, margin);
                setBounds(muzzleEffect, visualStart, farthestEnd, margin);
                var farPlane = Math.max(MINIMUM_FAR_PLANE, farthestLength + 256.0f);
                if (shaftEffect != null) shaftEffect.setMinimumFarPlane(farPlane);
                if (muzzleEffect != null) muzzleEffect.setMinimumFarPlane(farPlane);
                return true;
            }

            private static void poseSegment(BeamInstanceGeometry geometry, Vec3 start, Vec3 end, float width) {
                var length = end.subtract(start).length();
                geometry.add(rotationOf(start, end), start, width,
                        Double.isFinite(length) ? (float) Math.max(0.0, length) : 0.0f, width);
            }

            private static void poseBall(BeamInstanceGeometry geometry, Vec3 start, Vec3 end, float radius) {
                geometry.add(rotationOf(start, end), start, radius, radius, radius);
            }

            private static Quaternionf rotationOf(Vec3 start, Vec3 end) {
                var direction = end.subtract(start);
                if (!(direction.lengthSqr() > DIRECTION_EPSILON_SQUARED)) {
                    return beamRotation(0.0f, 0.0f);
                }
                var horizontal = Mth.sqrt((float) (direction.x * direction.x + direction.z * direction.z));
                return beamRotation(
                        (float) (Mth.atan2(-direction.y, horizontal) * Mth.RAD_TO_DEG),
                        (float) (Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG)
                );
            }

            private static void setBounds(@Nullable ActiveEffect effect, Vec3 first, Vec3 second, float margin) {
                if (effect == null) return;
                var center = first.add(second).scale(0.5);
                var radius = (float) first.distanceTo(second) * 0.5f + margin;
                effect.setCullingSphere(center.toVector3f(), radius);
            }

            public static final class BeamInstanceGeometry implements EffectInstanceGeometrySource {
                private static final int VERTEX_STRIDE = 5 * Float.BYTES;
                private static final int INSTANCE_STRIDE = 16 * Float.BYTES;
                private static final int MAX_INSTANCES = 2;
                private static final ByteBuffer SHAFT_VERTICES = buildShaftVertices();
                private static final ByteBuffer BALL_VERTICES = buildBallVertices();
                private static final Matrix4f MATRIX = new Matrix4f();
                private static final float[] SCRATCH = new float[16];

                public static final int SHAFT_VERTEX_COUNT = SHAFT_VERTICES.capacity() / VERTEX_STRIDE;
                public static final int BALL_VERTEX_COUNT = BALL_VERTICES.capacity() / VERTEX_STRIDE;

                public enum Kind {
                    SHAFT,
                    BALL
                }

                private final Kind kind;
                private final ByteBuffer instances = BufferUtils
                        .createByteBuffer(MAX_INSTANCES * INSTANCE_STRIDE)
                        .order(ByteOrder.nativeOrder());
                private int instanceCount;

                public BeamInstanceGeometry(Kind kind) {
                    this.kind = kind;
                }

                public void reset() {
                    instanceCount = 0;
                }

                public void add(Quaternionf rotation, Vec3 position, float x, float y, float z) {
                    if (instanceCount >= MAX_INSTANCES) return;
                    MATRIX.translation((float) position.x, (float) position.y, (float) position.z)
                            .rotate(rotation)
                            .scale(x, y, z)
                            .get(SCRATCH, 0);
                    instances.limit(instances.capacity());
                    instances.position(instanceCount * INSTANCE_STRIDE);
                    for (var value : SCRATCH) instances.putFloat(value);
                    instanceCount++;
                }

                @Override
                public @Nullable InstanceGeometryFrame sample(float timeSeconds) {
                    if (instanceCount == 0) return null;
                    var vertices = kind == Kind.SHAFT ? SHAFT_VERTICES : BALL_VERTICES;
                    var vertexCount = kind == Kind.SHAFT ? SHAFT_VERTEX_COUNT : BALL_VERTEX_COUNT;
                    instances.position(0);
                    instances.limit(instanceCount * INSTANCE_STRIDE);
                    return new InstanceGeometryFrame(vertices, vertexCount, instances, instanceCount);
                }

                private static ByteBuffer buildShaftVertices() {
                    var source = VertexUtil.Cylinder.getCylinderVertexBuffer(0.0f, 1.0f, 0.5f, 16, false);
                    var buffer = BufferUtils.createByteBuffer(source.length * VERTEX_STRIDE)
                            .order(ByteOrder.nativeOrder());
                    for (var vertex : source) {
                        buffer.putFloat(vertex[0]).putFloat(vertex[1]).putFloat(vertex[2])
                                .putFloat(vertex[3]).putFloat(vertex[4]);
                    }
                    return buffer.flip();
                }

                private static ByteBuffer buildBallVertices() {
                    var source = VertexUtil.Ball.getIcosphereVertexBuffer(1.0f, 2, true);
                    var buffer = BufferUtils.createByteBuffer(source.length * VERTEX_STRIDE)
                            .order(ByteOrder.nativeOrder());
                    for (var vertex : source) {
                        buffer.putFloat(vertex[0]).putFloat(vertex[1]).putFloat(vertex[2])
                                .putFloat(0.5f).putFloat(0.5f);
                    }
                    return buffer.flip();
                }
            }
        }
    }
}
