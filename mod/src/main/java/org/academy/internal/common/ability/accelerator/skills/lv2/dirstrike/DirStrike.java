package org.academy.internal.common.ability.accelerator.skills.lv2.dirstrike;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.model.quad.BakedNormals;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.compatibility.IrisIntegration;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.vfxgraph.block.BlockGeometryFrame;
import org.academy.api.client.render.vfxgraph.block.EffectBlockGeometrySource;
import org.academy.api.client.render.vfxgraph.runtime.ActiveEffect;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv1.VectorBlast;
import org.academy.internal.common.ability.effect.TimedSkillEffectRuntime;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;
import org.academy.internal.server.team.TeamRelations;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class DirStrike extends Skill {
    public static final int EFFECT_RADIUS = 12;
    public static final int ATTACK_RADIUS = 12;
    private static final int AIRBORNE_RADIUS_BONUS = 6;
    private static final int EFFECT_MIN_Y_OFFSET = -3;
    private static final int EFFECT_MAX_Y_OFFSET = 5;
    private static final float BASE_DAMAGE = 12.0f;
    private static final double DIVE_SPEED = 2.5;
    private static final double GROUND_SECTOR_COS = Mth.cos((45.0) * Mth.DEG_TO_RAD);

    public DirStrike() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .damage()
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .cpCost(15)
                .iterationTicks(5)
                .maxStacks(2)
                .dependsOn(Skills.VECTOR_BLAST)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
                .devCondition(new DevCondition.DependencyCondition("Vector Blast", "academy:vector_blast"))
        );
    }

    public static boolean isInsideAttackRadius(double xOffset, double zOffset) {
        return xOffset * xOffset + zOffset * zOffset <= ATTACK_RADIUS * ATTACK_RADIUS;
    }

    public static float getDamage(float abilityPower, float damageMultiplier) {
        return BASE_DAMAGE * Math.max(0.0f, abilityPower) * Math.max(0.0f, damageMultiplier);
    }

    @Override
    public void initClient() {
        DirStrikeVisualPacket.initClient();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_PRESS,
                Client.CONFIG.getKeyBinding(Client.KEY_NAME_PRESS,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                                InputConstants.PRESS, 0)),
                ctx -> Client.onAction(true));
        InputSystem.addKeyBinding(Client.KEY_NAME_RELEASE,
                Client.CONFIG.getKeyBinding(Client.KEY_NAME_RELEASE,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                                InputConstants.RELEASE, 0)),
                ctx -> Client.onAction(false));
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ACCELERATOR.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.DIR_STRIKE.get(),
                        List.of(VectorBlast.Client.SKILL_INFO),
                        R.textures.dir_strike_icon,
                        100,
                        110
                )
        );
        public static final String KEY_NAME_PRESS = SkillNames.DIR_STRIKE + "_press";
        public static final String KEY_NAME_RELEASE = SkillNames.DIR_STRIKE + "_release";
        public static Config CONFIG = new Config();

        private Client() {
        }

        public static final class DirStrikeGroundEffect implements EffectBlockGeometrySource {
            private static final int MIN_Y_OFFSET = -3;
            private static final int MAX_Y_OFFSET = 5;
            private static final int MAX_GROUND_BLOCKS = 128;
            private static final int MAX_AIRBORNE_BLOCKS = 1024;
            private static final int MAX_ACTIVE_TIMELINES = 4;
            private static final int MAX_ACTIVE_BLOCKS = 2048;
            private static final int BASE_DURATION = 18;
            private static final int GROUND_HOLD_TICKS = 20;
            private static final int AIRBORNE_HOLD_TICKS = 60;
            private static final float BASE_PEAK = 0.38f;
            private static final float RISE_TICKS = 6.0f;
            private static final float DEG_TO_RAD = (float) (Mth.PI / 180.0);
            private static final double GROUND_SECTOR_COS = Mth.cos((45.0) * Mth.DEG_TO_RAD);
            private static final int INITIAL_VERTICES = 65536;
            private static final int VERTEX_STRIDE = (3 + 2 + 4) * Float.BYTES + 2 * Short.BYTES + 4 * Byte.BYTES;
            private static final List<Timeline> ACTIVE = new ArrayList<>();

            private ByteBuffer vertexData = BufferUtils.createByteBuffer(INITIAL_VERTICES * VERTEX_STRIDE);

            public static void spawn(
                    Vec3 center,
                    BlockPos origin,
                    int radius,
                    boolean airborne,
                    float lookX,
                    float lookZ,
                    long seed
            ) {
                var minecraft = Minecraft.getInstance();
                var level = minecraft.level;
                if (level == null || radius <= 0) return;

                var modelRenderer = new ModelBlockRenderer(
                        minecraft.options.ambientOcclusion().get(), false, minecraft.getBlockColors());
                var modelSet = minecraft.getModelManager().getBlockStateModelSet();
                var cutoutLeaves = minecraft.options.cutoutLeaves().get();
                var samples = sampleSurface(
                        level,
                        center,
                        origin,
                        Math.min(radius, 32),
                        airborne,
                        normalizedLook(lookX, lookZ),
                        seed,
                        modelRenderer,
                        modelSet,
                        cutoutLeaves
                );
                if (samples.isEmpty()) return;

                var endTick = samples.stream().mapToDouble(BlockSample::timelineEndTick).max().orElse(1.0);
                var timeline = new Timeline(
                        level,
                        center,
                        level.getGameTime(),
                        samples,
                        endTick,
                        samplesHaveLayer(samples, ChunkSectionLayer.SOLID),
                        samplesHaveLayer(samples, ChunkSectionLayer.CUTOUT),
                        samplesHaveLayer(samples, ChunkSectionLayer.TRANSLUCENT)
                );
                while (!ACTIVE.isEmpty() && (ACTIVE.size() >= MAX_ACTIVE_TIMELINES
                        || activeBlockCount() + samples.size() > MAX_ACTIVE_BLOCKS)) {
                    ACTIVE.removeFirst();
                }
                ACTIVE.add(timeline);
                spawnDebris(level, samples, seed);
            }

            @Override
            public @Nullable BlockGeometryFrame sample(float timeSeconds) {
                var minecraft = Minecraft.getInstance();
                var level = minecraft.level;
                if (level == null) {
                    clear();
                    return null;
                }

                var now = level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                ACTIVE.removeIf(timeline -> timeline.level != level
                        || now - timeline.startTick > timeline.endTick + 2.0);
                if (ACTIVE.isEmpty()) return null;
                var timelines = List.copyOf(ACTIVE);

                var requiredVertices = countLayerVertices(timelines, now);
                if (requiredVertices == 0) return null;
                ensureCapacity(requiredVertices);
                vertexData.clear();
                var vertices = new ByteBuffer[ChunkSectionLayer.values().length];
                var counts = new int[ChunkSectionLayer.values().length];
                var transformNormals = IrisIntegration.isShaderPackInUse();
                for (var layer : ChunkSectionLayer.values()) {
                    var start = vertexData.position();
                    var vertexCount = renderLayer(vertexData, timelines, now, layer, transformNormals);
                    if (vertexCount == 0) continue;
                    var end = vertexData.position();
                    var layerVertices = vertexData.duplicate();
                    layerVertices.position(start).limit(end);
                    vertices[layer.ordinal()] = layerVertices.slice().order(vertexData.order());
                    counts[layer.ordinal()] = vertexCount;
                }
                return new BlockGeometryFrame(vertices, counts);
            }

            private void ensureCapacity(int requiredVertices) {
                var requiredBytes = (long) requiredVertices * VERTEX_STRIDE;
                if (vertexData.capacity() >= requiredBytes) return;
                var newCapacity = Math.max(requiredVertices,
                        (int) (vertexData.capacity() / VERTEX_STRIDE) * 2);
                vertexData = BufferUtils.createByteBuffer(Math.toIntExact((long) newCapacity * VERTEX_STRIDE));
            }

            private static int countLayerVertices(List<Timeline> timelines, double now) {
                var count = 0;
                for (var timeline : timelines) {
                    var age = now - timeline.startTick;
                    for (var sample : timeline.samples) {
                        var mesh = sample.mesh;
                        if (mesh.isEmpty()) continue;
                        var activeTick = (float) (age - sample.delay);
                        if (activeTick < 0.0f || activeTick > sample.localEndTick()) continue;
                        var amount = motion(activeTick, sample.duration, sample.holdTicks);
                        if (amount <= 0.001f) continue;
                        count += mesh.vertexCount();
                    }
                }
                return count;
            }

            static float motion(float activeTick, int duration, int holdTicks) {
                var riseTicks = Math.clamp(duration * 0.45f, 2.0f, RISE_TICKS);
                var fallTicks = Math.max(6.0f, duration - riseTicks);
                if (activeTick <= riseTicks) {
                    var rise = Mth.clamp(activeTick / riseTicks, 0.0f, 1.0f);
                    var inverse = 1.0f - rise;
                    return 1.0f - inverse * inverse * inverse;
                }
                if (activeTick <= riseTicks + holdTicks) return 1.0f;
                var fall = Mth.clamp((activeTick - riseTicks - holdTicks) / fallTicks, 0.0f, 1.0f);
                return 1.0f - fall * fall;
            }

            private static int renderLayer(ByteBuffer vertexData, List<Timeline> timelines, double now,
                                           ChunkSectionLayer layer, boolean transformNormals) {
                var poseStack = new PoseStack();
                var rotation = new Quaternionf();
                var transformedPosition = new Vector3f();
                var vertexCount = 0;

                for (var timeline : timelines) {
                    if (!timeline.hasLayer(layer)) continue;
                    var age = now - timeline.startTick;
                    for (var sample : timeline.samples) {
                        var mesh = sample.mesh;
                        var vertices = mesh.vertices(layer);
                        if (vertices.isEmpty()) continue;
                        var activeTick = (float) (age - sample.delay);
                        if (activeTick < 0.0f || activeTick > sample.localEndTick()) continue;
                        var amount = motion(activeTick, sample.duration, sample.holdTicks);
                        if (amount <= 0.001f) continue;

                        poseStack.pushPose();
                        poseStack.translate(
                                sample.position.getX() + 0.5,
                                sample.position.getY(),
                                sample.position.getZ() + 0.5
                        );
                        poseStack.translate(
                                0.0,
                                amount * sample.peakHeight * 1.12f + 0.03f + amount * sample.cornerLift,
                                0.0
                        );
                        poseStack.rotate(rotation.rotationY(sample.yRotRadians));
                        poseStack.translate(0.0, 0.0, amount * 0.1f);
                        poseStack.rotate(rotation.rotationX(-amount * 12.0f * DEG_TO_RAD));
                        poseStack.rotate(rotation.rotationZ(amount * 4.2f * DEG_TO_RAD));
                        poseStack.rotate(rotation.rotationX(amount * sample.cornerPitchRadians));
                        poseStack.rotate(rotation.rotationZ(amount * sample.cornerRollRadians));
                        poseStack.scale(0.98f, 0.98f, 0.98f);
                        poseStack.translate(-0.5, 0.0, -0.5);
                        var count = putVertices(
                                poseStack.last(), vertexData, vertices, transformedPosition, transformNormals);
                        vertexCount += count;
                        poseStack.popPose();
                    }
                }
                return vertexCount;
            }

            private static int putVertices(PoseStack.Pose pose, ByteBuffer target,
                                           List<CachedVertex> vertices, Vector3f position,
                                           boolean transformNormals) {
                var positionMatrix = pose.pose();
                var normal = transformNormals ? new Vector3f() : null;
                var count = 0;
                for (var vertex : vertices) {
                    positionMatrix.transformPosition(vertex.x, vertex.y, vertex.z, position);
                    var r = (float) ARGB.red(vertex.color) / 255.0f;
                    var g = (float) ARGB.green(vertex.color) / 255.0f;
                    var b = (float) ARGB.blue(vertex.color) / 255.0f;
                    var a = (float) ARGB.alpha(vertex.color) / 255.0f;
                    target.putFloat(position.x);
                    target.putFloat(position.y);
                    target.putFloat(position.z);
                    target.putFloat(vertex.u).putFloat(vertex.v);
                    target.putFloat(r).putFloat(g).putFloat(b).putFloat(a);
                    target.putShort(packedBlockCoordinate(vertex.light));
                    target.putShort(packedSkyCoordinate(vertex.light));
                    if (normal == null) {
                        target.putInt(0);
                    } else {
                        pose.transformNormal(vertex.normalX, vertex.normalY, vertex.normalZ, normal).normalize();
                        target.put(packNormal(normal.x));
                        target.put(packNormal(normal.y));
                        target.put(packNormal(normal.z));
                        target.put((byte) 0);
                    }
                    count++;
                }
                return count;
            }

            private static short packedBlockCoordinate(int packedLight) {
                return (short) (packedLight & 0xFFFF);
            }

            private static short packedSkyCoordinate(int packedLight) {
                return (short) (packedLight >>> 16 & 0xFFFF);
            }

            private static byte packNormal(float component) {
                return (byte) Math.round(Math.clamp(component, -1.0f, 1.0f) * 127.0f);
            }

            private static List<BlockSample> sampleSurface(
                    ClientLevel level,
                    Vec3 center,
                    BlockPos origin,
                    int radius,
                    boolean airborne,
                    Vec3 look,
                    long seed,
                    ModelBlockRenderer modelRenderer,
                    BlockStateModelSet modelSet,
                    boolean cutoutLeaves
            ) {
                var candidates = new ArrayList<BlockPos>();
                for (var xOffset = -radius; xOffset <= radius; xOffset++) {
                    for (var zOffset = -radius; zOffset <= radius; zOffset++) {
                        var distanceSqr = xOffset * xOffset + zOffset * zOffset;
                        if (distanceSqr > radius * radius
                                || !airborne && !insideGroundSector(xOffset + 0.5, zOffset + 0.5, look)) {
                            continue;
                        }
                        var surface = findSurface(level, origin, xOffset, zOffset);
                        if (surface != null) candidates.add(surface);
                    }
                }

                var limit = airborne ? MAX_AIRBORNE_BLOCKS : MAX_GROUND_BLOCKS;
                if (candidates.size() > limit) {
                    candidates.sort(Comparator.comparingLong(pos -> priority(seed, pos)));
                    candidates.subList(limit, candidates.size()).clear();
                }
                candidates.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(center)));

                var samples = new ArrayList<BlockSample>(candidates.size());
                for (var pos : candidates) {
                    var state = level.getBlockState(pos);
                    if (state.getRenderShape() != RenderShape.MODEL) continue;
                    var mesh = bakeMesh(
                            level, pos, state, modelRenderer, modelSet, cutoutLeaves);
                    if (mesh.isEmpty()) continue;

                    var random = RandomSource.create(seed ^ pos.asLong());
                    var distance = Mth.sqrt((float) (pos.distToCenterSqr(center)));
                    var delay = Math.max(0, Mth.floor(distance * 1.1f) - 1) + random.nextInt(2);
                    var duration = BASE_DURATION + random.nextInt(3);
                    var peak = BASE_PEAK + random.nextFloat() * 0.2f
                            + Math.max(0.0f, 1.0f - distance / Math.max(1, radius)) * 0.08f
                            - (airborne ? 0.2f : 0.0f);
                    var outward = Vec3.atCenterOf(pos).subtract(center);
                    if (outward.lengthSqr() <= 1.0E-4) outward = new Vec3(0.0, 0.0, 1.0);
                    var modelSeed = state.getSeed(pos);
                    samples.add(new BlockSample(
                            pos.immutable(),
                            state,
                            mesh,
                            delay,
                            duration,
                            airborne ? AIRBORNE_HOLD_TICKS : GROUND_HOLD_TICKS,
                            Math.max(0.05f, peak),
                            (float) Mth.atan2(outward.x, outward.z),
                            (((modelSeed >> 1) & 1L) == 0L ? 4.0f : -4.0f) * DEG_TO_RAD,
                            (((modelSeed >> 2) & 1L) == 0L ? 3.4f : -3.4f) * DEG_TO_RAD,
                            0.015f + ((modelSeed >> 3) & 3L) * 0.005f
                    ));
                }
                return List.copyOf(samples);
            }

            private static CachedMesh bakeMesh(ClientLevel level, BlockPos pos, BlockState state,
                                               ModelBlockRenderer modelRenderer, BlockStateModelSet modelSet,
                                               boolean cutoutLeaves) {
                var renderState = new MovingBlockRenderState();
                renderState.randomSeedPos = pos;
                renderState.blockPos = pos;
                renderState.blockState = state;
                renderState.biome = level.getBiome(pos);
                renderState.cardinalLighting = level.cardinalLighting();
                renderState.lightEngine = level.getLightEngine();

                var vertices = new EnumMap<ChunkSectionLayer, List<CachedVertex>>(ChunkSectionLayer.class);
                for (var layer : ChunkSectionLayer.values()) vertices.put(layer, new ArrayList<>());
                var forcedLayer = ModelBlockRenderer.forceOpaque(cutoutLeaves, state)
                        ? ChunkSectionLayer.SOLID
                        : null;
                BlockQuadOutput output = (x, y, z, quad, instance) -> appendQuad(
                        vertices.get(forcedLayer == null ? quad.materialInfo().layer() : forcedLayer),
                        x, y, z, quad, instance);
                modelRenderer.tesselateBlock(
                        output,
                        0.0f,
                        0.0f,
                        0.0f,
                        renderState,
                        pos,
                        state,
                        modelSet.get(state),
                        state.getSeed(pos)
                );
                return new CachedMesh(
                        List.copyOf(vertices.get(ChunkSectionLayer.SOLID)),
                        List.copyOf(vertices.get(ChunkSectionLayer.CUTOUT)),
                        List.copyOf(vertices.get(ChunkSectionLayer.TRANSLUCENT))
                );
            }

            private static void appendQuad(List<CachedVertex> output, float x, float y, float z,
                                           BakedQuad quad, QuadInstance instance) {
                var faceNormal = quad.direction().getUnitVec3f();
                var lightEmission = quad.materialInfo().lightEmission();
                for (var vertexIndex = 0; vertexIndex < BakedQuad.VERTEX_COUNT; vertexIndex++) {
                    var position = quad.position(vertexIndex);
                    var packedUv = quad.packedUV(vertexIndex);
                    var packedNormal = quad.bakedNormals().normal(vertexIndex);
                    var normalX = faceNormal.x();
                    var normalY = faceNormal.y();
                    var normalZ = faceNormal.z();
                    if (!BakedNormals.isUnspecified(packedNormal)) {
                        normalX = BakedNormals.unpackX(packedNormal);
                        normalY = BakedNormals.unpackY(packedNormal);
                        normalZ = BakedNormals.unpackZ(packedNormal);
                    }
                    output.add(new CachedVertex(
                            position.x() + x,
                            position.y() + y,
                            position.z() + z,
                            ARGB.multiply(instance.getColor(vertexIndex), quad.bakedColors().color(vertexIndex)),
                            UVPair.unpackU(packedUv),
                            UVPair.unpackV(packedUv),
                            instance.overlayCoords(),
                            LightCoordsUtil.lightCoordsWithEmission(
                                    instance.getLightCoords(vertexIndex), lightEmission),
                            normalX,
                            normalY,
                            normalZ
                    ));
                }
            }

            private static @Nullable BlockPos findSurface(ClientLevel level, BlockPos origin,
                                                int xOffset, int zOffset) {
                for (var yOffset = MAX_Y_OFFSET; yOffset >= MIN_Y_OFFSET; yOffset--) {
                    var pos = origin.offset(xOffset, yOffset, zOffset);
                    if (!level.isLoaded(pos)) continue;
                    var state = level.getBlockState(pos);
                    if (!renderableGround(level, pos, state)) continue;
                    var above = pos.above();
                    var aboveState = level.getBlockState(above);
                    if (!aboveState.isAir() && !aboveState.getCollisionShape(level, above).isEmpty()) continue;
                    return pos.immutable();
                }
                return null;
            }

            private static boolean renderableGround(ClientLevel level, BlockPos pos, BlockState state) {
                return !state.isAir()
                        && !state.hasBlockEntity()
                        && state.getRenderShape() == RenderShape.MODEL
                        && state.getDestroySpeed(level, pos) >= 0.0f
                        && state.getFluidState().isEmpty();
            }

            private static boolean insideGroundSector(double xOffset, double zOffset, Vec3 look) {
                var distanceSqr = xOffset * xOffset + zOffset * zOffset;
                if (distanceSqr <= 1.0E-8) return true;
                var inverseDistance = 1.0 / Mth.sqrt((float) distanceSqr);
                return (xOffset * look.x + zOffset * look.z) * inverseDistance >= GROUND_SECTOR_COS;
            }

            private static Vec3 normalizedLook(float lookX, float lookZ) {
                if (!Float.isFinite(lookX) || !Float.isFinite(lookZ)) return new Vec3(0.0, 0.0, 1.0);
                var look = new Vec3(lookX, 0.0, lookZ);
                return look.lengthSqr() <= 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
            }

            private static boolean samplesHaveLayer(List<BlockSample> samples, ChunkSectionLayer layer) {
                for (var sample : samples) {
                    if (!sample.mesh.vertices(layer).isEmpty()) return true;
                }
                return false;
            }

            private static long priority(long seed, BlockPos pos) {
                var value = seed ^ pos.asLong() * 0x9E3779B97F4A7C15L;
                value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
                value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
                return value ^ value >>> 31;
            }

            private static int activeBlockCount() {
                var count = 0;
                for (var timeline : ACTIVE) count += timeline.samples.size();
                return count;
            }

            private static void spawnDebris(ClientLevel level, List<BlockSample> samples, long seed) {
                var random = RandomSource.create(seed ^ 0xD1A57A1B10C5L);
                var stride = Math.max(2, samples.size() / 48);
                for (var index = 0; index < samples.size(); index += stride) {
                    var sample = samples.get(index);
                    var pos = sample.position;
                    level.addParticle(
                            new BlockParticleOption(ParticleTypes.BLOCK, sample.state),
                            pos.getX() + 0.5,
                            pos.getY() + 0.9,
                            pos.getZ() + 0.5,
                            (random.nextDouble() - 0.5) * 0.08,
                            0.03 + random.nextDouble() * 0.05,
                            (random.nextDouble() - 0.5) * 0.08
                    );
                }
            }

            private static void clear() {
                ACTIVE.clear();
            }

            private record Timeline(
                    ClientLevel level,
                    Vec3 center,
                    long startTick,
                    List<BlockSample> samples,
                    double endTick,
                    boolean solid,
                    boolean cutout,
                    boolean translucent
            ) {
                private boolean hasLayer(ChunkSectionLayer layer) {
                    return switch (layer) {
                        case SOLID -> solid;
                        case CUTOUT -> cutout;
                        case TRANSLUCENT -> translucent;
                    };
                }
            }

            private record BlockSample(
                    BlockPos position,
                    BlockState state,
                    CachedMesh mesh,
                    int delay,
                    int duration,
                    int holdTicks,
                    float peakHeight,
                    float yRotRadians,
                    float cornerPitchRadians,
                    float cornerRollRadians,
                    float cornerLift
            ) {
                private float localEndTick() {
                    return duration + holdTicks + 2.0f;
                }

                private float timelineEndTick() {
                    return delay + localEndTick();
                }
            }

            private record CachedMesh(
                    List<CachedVertex> solid,
                    List<CachedVertex> cutout,
                    List<CachedVertex> translucent
            ) {
                private List<CachedVertex> vertices(ChunkSectionLayer layer) {
                    return switch (layer) {
                        case SOLID -> solid;
                        case CUTOUT -> cutout;
                        case TRANSLUCENT -> translucent;
                    };
                }

                private int vertexCount() {
                    return solid.size() + cutout.size() + translucent.size();
                }

                private boolean isEmpty() {
                    return solid.isEmpty() && cutout.isEmpty() && translucent.isEmpty();
                }
            }

            private record CachedVertex(
                    float x,
                    float y,
                    float z,
                    int color,
                    float u,
                    float v,
                    int overlay,
                    int light,
                    float normalX,
                    float normalY,
                    float normalZ
            ) {
            }
        }

        public static final class DirStrikeGroundVfxClient {
            private static final Identifier ASSET = Identifier.fromNamespaceAndPath(
                    AcademyCraft.MOD_ID, "vfxgraph/dir_strike_ground");
            private static @Nullable ActiveEffect effect;

            private DirStrikeGroundVfxClient() {
            }

            public static void register() {
                if (effect != null) return;
                var spawned = VfxGraphManager.INSTANCE.spawn(ASSET, new Vector3f());
                spawned.setAlwaysVisible(true);
                spawned.bindBlockGeometry(new DirStrikeGroundEffect());
                effect = spawned;
            }
        }

        public static void onAction(boolean pressed) {
            MisakaNetworkClient.send(new ActionPacket(pressed));
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
        private static final Set<UUID> DIVING_PLAYERS = ConcurrentHashMap.newKeySet();

        private Server() {
        }

        @SubscribePacket
        public static void onAction(ActionPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!packet.pressed()) {
                if (DIVING_PLAYERS.remove(player.getUUID())) {
                    EntityMotionGuard.runWithMotionSource(
                            player,
                            () -> player.setDeltaMovement(Vec3.ZERO)
                    );
                    player.syncVelocity = true;
                    player.fallDistance = 0.0;
                }
                return;
            }
            if (!Skills.DIR_STRIKE.get().isEnabled(player)) return;
            if (!player.onGround()) {
                DIVING_PLAYERS.add(player.getUUID());
                dive(player);
                return;
            }
            executeStrike(player, false);
        }

        private static void tick(ServerPlayer player) {
            if (!DIVING_PLAYERS.contains(player.getUUID())) return;
            if (!player.isAlive() || player.hasDisconnected()
                    || !Skills.DIR_STRIKE.get().isEnabled(player)) {
                DIVING_PLAYERS.remove(player.getUUID());
                return;
            }
            if (player.onGround() || player.verticalCollision) {
                DIVING_PLAYERS.remove(player.getUUID());
                EntityMotionGuard.runWithMotionSource(
                        player,
                        () -> player.setDeltaMovement(Vec3.ZERO)
                );
                player.syncVelocity = true;
                player.fallDistance = 0.0;
                executeStrike(player, true);
                return;
            }
            Skills.DIR_STRIKE.get().reportActivity(player, true);
            dive(player);
        }

        private static void dive(ServerPlayer player) {
            EntityMotionGuard.runWithMotionSource(
                    player,
                    () -> player.setDeltaMovement(0.0, -DIVE_SPEED, 0.0)
            );
            player.syncVelocity = true;
            player.fallDistance = 0.0;
        }

        private static void executeStrike(ServerPlayer player, boolean airborne) {
            var skill = Skills.DIR_STRIKE.get();
            skill.executeActive(player, (ctx, actualCost) -> {
                var level = player.level();
                var playerPos = player.blockPosition();
                var baseRadius = skill.hasProficiencyMilestone(player, 2) ? 14 : ATTACK_RADIUS;
                var radius = airborne ? baseRadius + AIRBORNE_RADIUS_BONUS : baseRadius;
                var targetRadius = skill.scaledRange(player, radius);
                var look = horizontalLook(player);
                level.playSound(null, playerPos, SoundEvents.DIR_STRIKE.get(),
                        SoundSource.PLAYERS, 1.0f, 1.0f);
                DirStrikeVisualPacket.broadcast(
                        level, player.position(), playerPos, radius, airborne, look);

                var minY = playerPos.getY() + EFFECT_MIN_Y_OFFSET;
                var maxY = playerPos.getY() + EFFECT_MAX_Y_OFFSET + 1;
                var center = player.position();
                var area = new AABB(
                        center.x - targetRadius, minY, center.z - targetRadius,
                        center.x + targetRadius, maxY, center.z + targetRadius
                );
                var damage = getDamage(
                        ctx.system().getPlayerAbilityPowerMultiplier(player.getUUID()),
                        ctx.system().getPlayerDamageMultiplier(player.getUUID())
                );
                var source = SkillDamageSource.of(
                        player,
                        skill,
                        DamageTypes.VEC
                );
                var targets = level.getEntitiesOfClass(LivingEntity.class, area,
                        target -> target != player
                                && target.isAlive()
                                && !target.isSpectator()
                                && !TeamRelations.areAllied(player, target)
                                && !PvpSetting.shouldPrevent(player, target)
                                && target.getY() >= minY
                                && target.getY() <= maxY
                                && isInsideStrikeArea(
                                target.getX() - center.x,
                                target.getZ() - center.z,
                                targetRadius,
                                airborne,
                                look
                        ));
                for (var target : targets) {
                    SkillDamageUtil.applyVerifiedTrueHealth(target, source, damage);
                    var velocity = target.getDeltaMovement();
                    var centerTarget = skill.hasProficiencyMilestone(player, 3)
                            && target.distanceToSqr(center) <= 16.0;
                    target.setDeltaMovement(velocity.x, centerTarget ? -0.35 : 0.5, velocity.z);
                    target.syncVelocity = true;
                    if (centerTarget) {
                        TimedSkillEffectRuntime.schedule(player, 6, () -> {
                            if (!target.isAlive() || target.level() != level) return;
                            SkillDamageUtil.applyVerifiedTrueHealth(
                                    target, source, damage * 0.4f);
                        });
                    }
                }
            });
        }

        private static boolean isInsideStrikeArea(double xOffset, double zOffset, double radius,
                                                  boolean airborne, Vec3 look) {
            var distanceSquared = xOffset * xOffset + zOffset * zOffset;
            if (distanceSquared > radius * radius) return false;
            if (airborne || distanceSquared <= 1.0e-8) return true;
            var inverseDistance = 1.0 / Mth.sqrt((float) (distanceSquared));
            return (xOffset * look.x + zOffset * look.z) * inverseDistance >= GROUND_SECTOR_COS;
        }

        private static Vec3 horizontalLook(ServerPlayer player) {
            var look = player.getLookAngle();
            var horizontal = new Vec3(look.x, 0.0, look.z);
            return horizontal.lengthSqr() <= 1.0e-8
                    ? new Vec3(0.0, 0.0, 1.0)
                    : horizontal.normalize();
        }

    }

    @EventBusSubscriber
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.tick(player);
        }

        @SubscribeEvent
        public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            Server.DIVING_PLAYERS.remove(event.getEntity().getUUID());
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActionPacket extends Packet<ServerGamePacketListenerImpl, ActionPacket> {
        public static final StreamCodec<ByteBuf, ActionPacket> CODEC = ByteBufCodecs.BOOL
                .map(ActionPacket::new, ActionPacket::pressed);
        private final boolean pressed;

        public ActionPacket(boolean pressed) {
            this.pressed = pressed;
        }

        public boolean pressed() {
            return pressed;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ActionPacket> getPacketType() {
            return PacketTypes.DIR_STRIKE.get();
        }
    }
}
