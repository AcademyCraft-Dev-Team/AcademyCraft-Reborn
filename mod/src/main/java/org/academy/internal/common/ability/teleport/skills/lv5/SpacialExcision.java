package org.academy.internal.common.ability.teleport.skills.lv5;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import io.netty.buffer.ByteBuf;
import java.lang.Math;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Continuation;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientResourceLoadFinishedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.Render;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformBinding;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.event.SkillExecutionFinishEvent;
import org.academy.api.common.ability.event.SkillExecutionStartEvent;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.effect.SpacialExcisionMath;
import org.academy.internal.common.ability.teleport.effect.TeleportCompletedEvent;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;
import org.academy.internal.server.ability.AbilitySystemServer;
import org.academy.internal.server.team.TeamRelations;
import org.joml.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import static com.mojang.renderpearl.api.pipeline.RenderPipeline.builder;
import static org.academy.AcademyCraft.academy;
import org.academy.api.client.render.vfxgraph.render.VfxGlowHooks;
import org.academy.internal.client.render.vfxgraph.FrameContext;
import org.academy.internal.client.render.vfxgraph.FrameProjectionContext;
import org.academy.internal.client.render.vfxgraph.VfxContexts;




public final class SpacialExcision extends Skill {
    public static final int ACTIVATION_CP = 100;
    public static final int DURATION_TICKS = 600;
    private static final double OBSERVER_RANGE = 512.0;

    public SpacialExcision() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .cpCost(ACTIVATION_CP)
                .iterationTicks(20)
                .maxStacks(1)
                .dependsOn(Skills.AREA_TELEPORT_SELECT)
        );
    }

    @Override
    public void initClient() {
        Client.init();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY, Client.CONFIG.getKeyBinding(Client.KEY,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_X,
                                InputConstants.PRESS, InputConstants.MOD_ALT)),
                ctx -> Client.onUse());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY = SkillNames.SPACIAL_EXCISION + "_use";
        public static Config CONFIG = new Config();
        private static boolean initialized;

        private Client() {
        }

        private static void init() {
            if (initialized) return;
            initialized = true;
            MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
            NeoForge.EVENT_BUS.register(Client.class);
            Vfx.init();
            VfxContexts.register(Vfx::sampleFrame);
            VfxGlowHooks.registerGlowLines(new VfxGlowHooks.GlowLinePass() {
                @Override
                public boolean hasWork() {
                    return Vfx.hasGlowLines();
                }

                @Override
                public void render(
                        @Nullable GpuTextureView color, @Nullable GpuTextureView depth
                ) {
                    Vfx.renderGlowLines(color, depth);
                }
            });
        }

        public static void onUse() {
            MisakaNetworkClient.send(ActivatePacket.INSTANCE);
        }

        @SubscribePacket
        public static void handleSegment(SegmentPacket packet) {
            Vfx.addSegment(
                    packet.sessionId(), packet.startTick(), packet.createdTick(), packet.endTick(), packet.dimension(),
                    packet.start(), packet.end(), packet.preTeleportYaw(), packet.seed()
            );
        }

        @SubscribePacket
        public static void handleEnd(EndPacket packet) {
            if (packet.sessionId() == null) return;
            Vfx.endSession(packet.sessionId());
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            Vfx.releaseTransientResources();
        }

        @SubscribeEvent
        public static void onLevelUnload(LevelEvent.Unload event) {
            if (event.getLevel() instanceof ClientLevel) {
                Vfx.releaseTransientResources();
            }
        }

        @SubscribeEvent
        public static void onResourceLoadFinished(ClientResourceLoadFinishedEvent event) {
            if (!event.isInitial()) {
                Vfx.releaseTransientResources();
            }
        }

        public static final class RenderMath {
            private static final float SCREEN_DIRECTION_EPSILON = 1.0e-6f;
            private static final float FALLOFF_DISTANCE_FACTOR = 2.5f;
            static final float MAX_NEAR_FALLOFF_SHORT_SIDE_FRACTION = 0.04f;
            static final float MIN_NEAR_FALLOFF_WORLD_WIDTH = 0.05f;
            static final float AFFINE_SOURCE_W_MINIMUM = 1.0e-1f;
            static final float AFFINE_MAX_SHIFT_SHORT_SIDE = 1.8e-1f;
            static final float AFFINE_MAX_SUPPORT_SCALE = 4096.0f;
            static final int AFFINE_SUPPORT_SEARCH_STEPS = 24;
            private static final float MIN_FLOW_SPEED = 0.035f;
            private static final float MAX_FLOW_SPEED = 0.085f;
            private static final float MIN_BREATH_SPEED = 0.012f;
            private static final float MAX_BREATH_SPEED = 0.028f;
            private static final float MIN_BREATH_AMPLITUDE = 0.035f;
            private static final float MAX_BREATH_AMPLITUDE = 0.060f;

            private RenderMath() {
            }

            static WorldOrientationWeights worldOrientationWeights(Vector3fc tangent) {
                if (tangent == null || !tangent.isFinite()) return WorldOrientationWeights.NONE;
                var length = tangent.length();
                if (!Float.isFinite(length) || length <= SCREEN_DIRECTION_EPSILON) {
                    return WorldOrientationWeights.NONE;
                }
                var horizontal = (float) Math.hypot(tangent.x(), tangent.z()) / length;
                var vertical = Math.abs(tangent.y()) / length;
                if (!Float.isFinite(horizontal) || !Float.isFinite(vertical)) {
                    return WorldOrientationWeights.NONE;
                }
                return new WorldOrientationWeights(
                        Math.clamp(horizontal, 0.0f, 1.0f),
                        Math.clamp(vertical, 0.0f, 1.0f));
            }

            static Vector3f worldDisplacement(
                    Vector3fc tangent,
                    Vector3fc planeUp,
                    float horizontalDistance,
                    float verticalDistance,
                    float progress
            ) {
                if (tangent == null || planeUp == null || !tangent.isFinite() || !planeUp.isFinite()) {
                    return new Vector3f();
                }
                var tangentLength = tangent.length();
                var planeUpLength = planeUp.length();
                if (!Float.isFinite(tangentLength) || !Float.isFinite(planeUpLength)
                        || tangentLength <= SCREEN_DIRECTION_EPSILON
                        || planeUpLength <= SCREEN_DIRECTION_EPSILON) {
                    return new Vector3f();
                }
                var weights = worldOrientationWeights(tangent);
                var horizontal = finiteNonNegative(horizontalDistance) * weights.horizontal();
                var vertical = finiteNonNegative(verticalDistance) * weights.vertical();
                var amount = Float.isFinite(progress) ? Math.clamp(progress, 0.0f, 1.0f) : 0.0f;
                return new Vector3f(tangent).div(tangentLength).mul(horizontal)
                        .add(new Vector3f(planeUp).div(planeUpLength).mul(vertical))
                        .mul(amount);
            }

            static float adaptiveFalloffWidth(
                    float minimumWidth,
                    float horizontalDistance,
                    float verticalDistance
            ) {
                var minimum = finiteNonNegative(minimumWidth);
                var requested = finiteNonNegative(horizontalDistance)
                        + finiteNonNegative(verticalDistance);
                return Math.max(minimum, FALLOFF_DISTANCE_FACTOR * requested);
            }

            static float screenBoundedFalloffWidth(
                    float requestedWorldWidth,
                    float projectedPixelsPerWorldUnit,
                    int viewportWidth,
                    int viewportHeight
            ) {
                if (!Float.isFinite(requestedWorldWidth) || requestedWorldWidth <= 0.0f) {
                    return 0.0f;
                }
                if (viewportWidth < 2 || viewportHeight < 2
                        || !Float.isFinite(projectedPixelsPerWorldUnit)
                        || projectedPixelsPerWorldUnit <= SCREEN_DIRECTION_EPSILON) {
                    return requestedWorldWidth;
                }
                var maximumPixels = MAX_NEAR_FALLOFF_SHORT_SIDE_FRACTION
                        * Math.min(viewportWidth, viewportHeight);
                var projectedLimit = maximumPixels / projectedPixelsPerWorldUnit;
                if (!Float.isFinite(projectedLimit)) return requestedWorldWidth;
                return Math.clamp(projectedLimit, MIN_NEAR_FALLOFF_WORLD_WIDTH, requestedWorldWidth);
            }

            private static float finiteNonNegative(float value) {
                return Float.isFinite(value) ? Math.max(value, 0.0f) : 0.0f;
            }

            static @Nullable AffineCutMapping regularizedAffineCutMapping(
                    Vector3fc supportViewPosition,
                    Vector3fc displacementView,
                    Matrix4fc projection,
                    int viewportWidth,
                    int viewportHeight
            ) {
                if (supportViewPosition == null || displacementView == null || projection == null
                        || !supportViewPosition.isFinite() || !displacementView.isFinite()
                        || !projection.isFinite() || viewportWidth < 2 || viewportHeight < 2) {
                    return null;
                }
                var shortSide = Math.min(viewportWidth, viewportHeight);
                var maximumPixels = AFFINE_MAX_SHIFT_SHORT_SIDE * shortSide;
                if (!Float.isFinite(maximumPixels) || maximumPixels <= 0.0f) return null;

                var mapping = evaluateAffineOffsets(
                        supportViewPosition, displacementView, projection,
                        viewportWidth, viewportHeight, 1.0f);
                if (affineMappingSafe(mapping, maximumPixels)) return mapping;

                var lowerScale = 1.0f;
                var upperScale = 2.0f;
                AffineCutMapping upperMapping = null;
                while (upperScale <= AFFINE_MAX_SUPPORT_SCALE) {
                    upperMapping = evaluateAffineOffsets(
                            supportViewPosition, displacementView, projection,
                            viewportWidth, viewportHeight, upperScale);
                    if (affineMappingSafe(upperMapping, maximumPixels)) break;
                    lowerScale = upperScale;
                    upperScale *= 2.0f;
                }
                if (!affineMappingSafe(upperMapping, maximumPixels)) return null;

                for (var step = 0; step < AFFINE_SUPPORT_SEARCH_STEPS; step++) {
                    var middleScale = (lowerScale + upperScale) * 0.5f;
                    var middleMapping = evaluateAffineOffsets(
                            supportViewPosition, displacementView, projection,
                            viewportWidth, viewportHeight, middleScale);
                    if (affineMappingSafe(middleMapping, maximumPixels)) {
                        upperScale = middleScale;
                        upperMapping = middleMapping;
                    } else {
                        lowerScale = middleScale;
                    }
                }
                return upperMapping;
            }

            private static @Nullable AffineCutMapping evaluateAffineOffsets(
                    Vector3fc supportViewPosition,
                    Vector3fc displacementView,
                    Matrix4fc projection,
                    int viewportWidth,
                    int viewportHeight,
                    float supportScale
            ) {
                if (!Float.isFinite(supportScale) || supportScale < 1.0f) return null;
                var support = new Vector3f(supportViewPosition).mul(supportScale);
                var destinationUv = projectViewPointToUv(support, projection);
                var positiveUv = projectViewPointToUv(
                        new Vector3f(support).add(displacementView), projection);
                var negativeUv = projectViewPointToUv(
                        new Vector3f(support).sub(displacementView), projection);
                if (destinationUv == null || positiveUv == null || negativeUv == null) return null;

                var positiveOffset = positiveUv.sub(destinationUv, new Vector2f());
                var negativeOffset = negativeUv.sub(destinationUv, new Vector2f());
                if (!positiveOffset.isFinite() || !negativeOffset.isFinite()) return null;
                var positivePixels = Math.hypot(
                        positiveOffset.x * viewportWidth,
                        positiveOffset.y * viewportHeight);
                var negativePixels = Math.hypot(
                        negativeOffset.x * viewportWidth,
                        negativeOffset.y * viewportHeight);
                var maximumOffsetPixels = (float) Math.max(positivePixels, negativePixels);
                if (!Float.isFinite(maximumOffsetPixels)) return null;
                return new AffineCutMapping(
                        positiveOffset, negativeOffset, maximumOffsetPixels);
            }

            private static @Nullable Vector2f projectViewPointToUv(
                    Vector3fc viewPosition,
                    Matrix4fc projection
            ) {
                var clip = new Vector4f(viewPosition, 1.0f).mul(projection);
                if (!clip.isFinite() || clip.w < AFFINE_SOURCE_W_MINIMUM) return null;
                var inverseW = 1.0f / clip.w;
                var uv = new Vector2f(
                        Math.fma(clip.x * inverseW, 0.5f, 0.5f),
                        Math.fma(clip.y * inverseW, 0.5f, 0.5f));
                return uv.isFinite() ? uv : null;
            }

            private static boolean affineMappingSafe(
                    @Nullable AffineCutMapping mapping,
                    float maximumPixels
            ) {
                return mapping != null
                        && mapping.positiveUvOffset().isFinite()
                        && mapping.negativeUvOffset().isFinite()
                        && Float.isFinite(mapping.maximumOffsetPixels())
                        && mapping.maximumOffsetPixels() <= maximumPixels;
            }

            static List<Long> selectBackgroundIds(
                    List<BackgroundCandidate> candidates,
                    int maximum
            ) {
                if (maximum <= 0 || candidates.isEmpty()) return List.of();
                var selected = new ArrayList<BackgroundCandidate>(
                        Math.min(maximum, candidates.size()));
                for (var candidate : candidates) {
                    if (!validCandidate(candidate)) continue;
                    var insertion = 0;
                    while (insertion < selected.size()
                            && compareCandidates(selected.get(insertion), candidate) <= 0) {
                        insertion++;
                    }
                    if (insertion >= maximum) continue;
                    selected.add(insertion, candidate);
                    if (selected.size() > maximum) selected.removeLast();
                }
                var ids = new ArrayList<Long>(selected.size());
                for (var candidate : selected) ids.add(candidate.stableId());
                return List.copyOf(ids);
            }

            private static boolean validCandidate(BackgroundCandidate candidate) {
                return candidate != null
                        && Double.isFinite(candidate.projectedArea())
                        && candidate.projectedArea() > 0.0
                        && Double.isFinite(candidate.distanceSquared())
                        && candidate.distanceSquared() >= 0.0;
            }

            private static int compareCandidates(
                    BackgroundCandidate first, BackgroundCandidate second
            ) {
                var byArea = Double.compare(second.projectedArea(), first.projectedArea());
                if (byArea != 0) return byArea;
                var byDistance = Double.compare(first.distanceSquared(), second.distanceSquared());
                return byDistance != 0 ? byDistance
                        : Long.compare(first.stableId(), second.stableId());
            }

            static int sourceValidationSide(double signedDistance, int previousSide) {
                if (!Double.isFinite(signedDistance)) return previousSide < 0 ? -1 : 1;
                if (signedDistance > 1.0e-6) return 1;
                if (signedDistance < -1.0e-6) return -1;
                return previousSide < 0 ? -1 : 1;
            }

            static NearProxyPlan nearMaskProxyPlan(
                    float minimumClipW,
                    float maximumClipW,
                    float nearClipW
            ) {
                if (!Float.isFinite(minimumClipW) || !Float.isFinite(maximumClipW)
                        || !Float.isFinite(nearClipW) || nearClipW <= 0.0f
                        || minimumClipW > maximumClipW || maximumClipW <= 0.0f) {
                    return NearProxyPlan.NONE;
                }
                var clipsCameraFront = minimumClipW <= 0.0f;
                var sourceMinimumW = clipsCameraFront ? 0.0f : minimumClipW;
                var proxyTargetW = nearClipW + 0.01f;
                if (sourceMinimumW >= proxyTargetW) return NearProxyPlan.NONE;
                return new NearProxyPlan(
                        proxyTargetW - sourceMinimumW,
                        clipsCameraFront);
            }

            static double clippedProjectedArea(List<ClipPoint> input, float nearW) {
                if (input.size() < 3 || !Float.isFinite(nearW) || nearW <= 0.0f) return 0.0;
                var polygon = List.copyOf(input);
                for (var boundary = 0; boundary < 5 && polygon.size() >= 3; boundary++) {
                    polygon = clipPolygon(polygon, boundary, nearW);
                }
                if (polygon.size() < 3) return 0.0;

                var twiceArea = 0.0;
                for (var i = 0; i < polygon.size(); i++) {
                    var current = polygon.get(i);
                    var next = polygon.get((i + 1) % polygon.size());
                    if (!current.isFinite() || !next.isFinite()
                            || current.w() < nearW || next.w() < nearW) {
                        return 0.0;
                    }
                    var currentX = current.x() / current.w();
                    var currentY = current.y() / current.w();
                    var nextX = next.x() / next.w();
                    var nextY = next.y() / next.w();
                    if (!Double.isFinite(currentX) || !Double.isFinite(currentY)
                            || !Double.isFinite(nextX) || !Double.isFinite(nextY)) {
                        return 0.0;
                    }
                    twiceArea += currentX * nextY - nextX * currentY;
                }
                var area = Math.abs(twiceArea) * 0.5;
                return Double.isFinite(area) ? area : 0.0;
            }

            private static List<ClipPoint> clipPolygon(
                    List<ClipPoint> input, int boundary, float nearW
            ) {
                var output = new ArrayList<ClipPoint>(input.size() + 1);
                var previous = input.get(input.size() - 1);
                var previousDistance = clipDistance(previous, boundary, nearW);
                if (!Float.isFinite(previousDistance)) return List.of();
                var previousInside = previousDistance >= 0.0f;
                for (var current : input) {
                    var currentDistance = clipDistance(current, boundary, nearW);
                    if (!Float.isFinite(currentDistance)) return List.of();
                    var currentInside = currentDistance >= 0.0f;
                    if (currentInside != previousInside) {
                        var denominator = previousDistance - currentDistance;
                        if (!Float.isFinite(denominator) || Math.abs(denominator) <= 1.0e-8f) {
                            return List.of();
                        }
                        var t = previousDistance / denominator;
                        var intersection = previous.lerp(current, t);
                        if (boundary == 0) {
                            intersection = new ClipPoint(
                                    intersection.x(), intersection.y(), nearW);
                        }
                        if (!intersection.isFinite()) return List.of();
                        output.add(intersection);
                    }
                    if (currentInside) output.add(current);
                    previous = current;
                    previousDistance = currentDistance;
                    previousInside = currentInside;
                }
                return output;
            }

            private static float clipDistance(ClipPoint point, int boundary, float nearW) {
                if (!point.isFinite()) return Float.NaN;
                return switch (boundary) {
                    case 0 -> point.w() - nearW;
                    case 1 -> point.x() + point.w();
                    case 2 -> point.w() - point.x();
                    case 3 -> point.y() + point.w();
                    case 4 -> point.w() - point.y();
                    default -> Float.NaN;
                };
            }

            static FlowParams flowParams(long seed) {
                var directionHash = mix64(seed ^ 0x9E3779B97F4A7C15L);
                var speedHash = mix64(seed ^ 0xD1B54A32D192ED03L);
                var phaseHash = mix64(seed ^ 0x94D049BB133111EBL);
                var direction = (directionHash & 1L) == 0L ? -1.0f : 1.0f;
                var speedUnit = unitFloat(speedHash);
                var speed = direction * (MIN_FLOW_SPEED
                        + (MAX_FLOW_SPEED - MIN_FLOW_SPEED) * speedUnit);
                return new FlowParams(speed, unitFloat(phaseHash));
            }

            static BreathParams breathParams(long seed) {
                var speedHash = mix64(seed ^ 0x632BE59BD9B4E019L);
                var phaseHash = mix64(seed ^ 0x8CB92BA72F3D8DD7L);
                var amplitudeHash = mix64(seed ^ 0xA0F2EC75A1FE1575L);
                var speed = MIN_BREATH_SPEED
                        + (MAX_BREATH_SPEED - MIN_BREATH_SPEED) * unitFloat(speedHash);
                var amplitude = MIN_BREATH_AMPLITUDE
                        + (MAX_BREATH_AMPLITUDE - MIN_BREATH_AMPLITUDE) * unitFloat(amplitudeHash);
                return new BreathParams(speed, unitFloat(phaseHash), amplitude);
            }

            static float breathScale(BreathParams params, float gameTime) {
                if (params == null) return 1.0f;
                if (!Float.isFinite(gameTime)) return 1.0f;
                var angle = gameTime * params.speed() * (float) (2.0 * Math.PI)
                        + params.phase() * (float) (2.0 * Math.PI);
                var scale = 1.0f + params.amplitude() * (float) Math.sin(angle);
                return Float.isFinite(scale) ? scale : 1.0f;
            }

            private static long mix64(long value) {
                value ^= value >>> 30;
                value *= 0xBF58476D1CE4E5B9L;
                value ^= value >>> 27;
                value *= 0x94D049BB133111EBL;
                return value ^ value >>> 31;
            }

            private static float unitFloat(long value) {
                return (float) ((value >>> 40) * 0x1.0p-24);
            }

            record FlowParams(float speed, float phase) {
            }

            record BreathParams(float speed, float phase, float amplitude) {
            }

            record WorldOrientationWeights(float horizontal, float vertical) {
                private static final WorldOrientationWeights NONE =
                        new WorldOrientationWeights(0.0f, 0.0f);
            }

            record AffineCutMapping(
                    Vector2f positiveUvOffset,
                    Vector2f negativeUvOffset,
                    float maximumOffsetPixels
            ) {
            }

            record ClipPoint(float x, float y, float w) {
                private boolean isFinite() {
                    return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(w);
                }

                private ClipPoint lerp(ClipPoint other, float t) {
                    return new ClipPoint(
                            Math.fma(other.x - x, t, x),
                            Math.fma(other.y - y, t, y),
                            Math.fma(other.w - w, t, w));
                }
            }

            record NearProxyPlan(float offset, boolean clipOriginalToCameraFront) {
                private static final NearProxyPlan NONE = new NearProxyPlan(0.0f, false);
            }

            record BackgroundCandidate(long stableId, double projectedArea, double distanceSquared) {
            }
        }

        public static record CutCamera(
                Vector3f pos,
                Matrix4f projectionMatrix,
                Matrix4f viewRotationMatrix,
                @Nullable GpuBufferSlice projectionUniform
        ) {
        }

        public static final class CutPipelines {
            private static final VertexFormat MASK_FORMAT = VertexFormat.builder(0)
                    .addAttribute("Position", GpuFormat.RGB32_FLOAT)
                    .addAttribute("WorldDisplacement", GpuFormat.RGB32_FLOAT)
                    .addAttribute("Coverage", GpuFormat.R32_FLOAT)
                    .build();

            static final VertexFormat LINE_FORMAT = VertexFormat.builder(0)
                    .addAttribute("Position", GpuFormat.RGB32_FLOAT)
                    .addAttribute("Color", GpuFormat.RGBA32_FLOAT)
                    .addAttribute("Flow", GpuFormat.R32_FLOAT)
                    .addAttribute("MaterialUv", GpuFormat.RG32_FLOAT)
                    .build();

            public static final RenderPipeline SPATIAL_CUT_MATERIAL = builder()
                    .withLocation(academy("pipeline/spatial_cut_material"))
                    .withVertexShader(R.shaders.core.spatial_cut_line)
                    .withFragmentShader(R.shaders.core.spatial_cut_material)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayout.builder()
                            .withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER)
                            .withUniform("Sampler1", UniformType.COMBINED_IMAGE_SAMPLER)
                            .build())
                    .withBindGroupLayout(BindGroupLayout.builder()
                            .withUniform("SpatialCutFlow", UniformType.UNIFORM_BUFFER)
                            .build())
                    .withCull(false)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withVertexBinding(0, LINE_FORMAT)
                    .build();

            public static final RenderPipeline SPATIAL_CUT_GLOW = builder()
                    .withLocation(academy("pipeline/spatial_cut_glow"))
                    .withVertexShader(R.shaders.core.spatial_cut_line)
                    .withFragmentShader(R.shaders.core.spatial_cut_glow)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayout.builder()
                            .withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER)
                            .withUniform("Sampler1", UniformType.COMBINED_IMAGE_SAMPLER)
                            .build())
                    .withBindGroupLayout(BindGroupLayout.builder()
                            .withUniform("SpatialCutFlow", UniformType.UNIFORM_BUFFER)
                            .build())
                    .withCull(false)
                    .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withVertexBinding(0, LINE_FORMAT)
                    .build();

            public static final RenderPipeline SPATIAL_CUT_MASK = builder()
                    .withLocation(academy("pipeline/spatial_cut_mask"))
                    .withVertexShader(R.shaders.core.spatial_cut_mask)
                    .withFragmentShader(R.shaders.core.spatial_cut_mask)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withCull(false)
                    .withColorTargetState(new ColorTargetState(
                            Optional.of(BlendFunction.ADDITIVE),
                            GpuFormat.RGBA16_FLOAT,
                            ColorTargetState.WRITE_ALL))
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withVertexBinding(0, MASK_FORMAT)
                    .build();

            public static final RenderPipeline SPATIAL_CUT_DEPTH = builder()
                    .withLocation(academy("pipeline/spatial_cut_depth"))
                    .withVertexShader(R.shaders.core.spatial_cut_mask)
                    .withFragmentShader(R.shaders.core.spatial_cut_depth)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withCull(false)
                    .withColorTargetState(new ColorTargetState(
                            Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
                    .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withVertexBinding(0, MASK_FORMAT)
                    .build();

            public static final RenderPipeline SPATIAL_CUT_POST = builder()
                    .withLocation(academy("pipeline/spatial_cut_post"))
                    .withVertexShader(R.shaders.core.screen_blit)
                    .withFragmentShader(R.shaders.core.spatial_cut)
                    .withBindGroupLayout(BindGroupLayout.builder()
                            .withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER)
                            .withUniform("Sampler3", UniformType.COMBINED_IMAGE_SAMPLER)
                            .withUniform("Sampler4", UniformType.COMBINED_IMAGE_SAMPLER)
                            .withUniform("Sampler5", UniformType.COMBINED_IMAGE_SAMPLER)
                            .withUniform("Sampler6", UniformType.COMBINED_IMAGE_SAMPLER)
                            .withUniform("Sampler7", UniformType.COMBINED_IMAGE_SAMPLER)
                            .withUniform("Sampler8", UniformType.COMBINED_IMAGE_SAMPLER)
                            .withUniform("Sampler9", UniformType.COMBINED_IMAGE_SAMPLER)
                            .withUniform("Sampler10", UniformType.COMBINED_IMAGE_SAMPLER)
                            .withUniform("Sampler11", UniformType.COMBINED_IMAGE_SAMPLER)
                            .withUniform("Sampler12", UniformType.COMBINED_IMAGE_SAMPLER)
                            .withUniform("Sampler13", UniformType.COMBINED_IMAGE_SAMPLER)
                            .withUniform("SpatialCutPost", UniformType.UNIFORM_BUFFER)
                            .build())
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withCull(false)
                    .withColorTargetState(new ColorTargetState(
                            Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withVertexBinding(0, DefaultVertexFormat.POSITION)
                    .build();

            private CutPipelines() {
            }
        }

        public static final class Vfx {
            private static final int VERTEX_STRIDE = 7 * Float.BYTES;
            private static final int LINE_VERTEX_STRIDE = CutPipelines.LINE_FORMAT.getVertexSize();
            private static final float DISPLACEMENT_RIGID_HALF_WIDTH = 12.0f;
            private static final float MIN_DISPLACEMENT_FALLOFF_WIDTH = 6.0f;
            private static final float HORIZONTAL_SLIDE_DISTANCE_BLOCKS = 6.0f;
            private static final float VERTICAL_CONVERGENCE_DISTANCE_BLOCKS = 0.5f;
            static final int MAX_SOURCE_VALIDATION_CUTS = 5;
            private static final float PORTAL_MAX_HALF_WIDTH = 0.32f;
            private static final double MAX_RENDER_DISTANCE = 512.0;
            private static final double TOMBSTONE_TICKS = 1200.0;
            private static final int MAX_ENDED_SESSION_TOMBSTONES = 4096;
            private static final int INITIAL_VERTICES = 256;
            private static final int LINE_LONGITUDINAL_SECTIONS = 24;
            private static final int MASK_FALLOFF_STEPS = 6;
            private static final int MASK_CROSS_SECTIONS = MASK_FALLOFF_STEPS + 2;
            private static final int MASK_LONGITUDINAL_SECTIONS =
                    MASK_FALLOFF_STEPS * 2 + LINE_LONGITUDINAL_SECTIONS + 1;
            private static final int MASK_MAX_VERTICES_PER_SEGMENT =
                    (MASK_LONGITUDINAL_SECTIONS - 1)
                            * (MASK_CROSS_SECTIONS - 1) * 2 * 2 * 6;
            private static final int LINE_MAX_VERTICES_PER_SEGMENT =
                    LINE_LONGITUDINAL_SECTIONS * 2 * 6;
            private static final float NEAR_CLIP_MIN_W = 5.0e-2f;
            static final int LINE_CLIP_VERTEX_COMPONENTS = 8;
            static final int LINE_CLIP_MAX_VERTICES = 4;
            static final int LINE_CLIP_SCRATCH_COMPONENTS =
                    LINE_CLIP_VERTEX_COMPONENTS * LINE_CLIP_MAX_VERTICES;
            private static final int SOURCE_VALIDATION_IDLE_FRAME_LIMIT = 120;
            private static final int FLOW_UNIFORM_SIZE = new Std140SizeCalculator().putVec4().get();
            private static final int POST_UNIFORM_SIZE = postUniformSize();
            private static final Identifier END_SKY_TEXTURE =
                    Identifier.withDefaultNamespace("textures/environment/end_sky.png");
            private static final Identifier END_PORTAL_TEXTURE =
                    Identifier.withDefaultNamespace("textures/entity/end_portal/end_portal.png");
            private static final boolean DEBUG_POST_CLASSIFICATION =
                    Boolean.getBoolean("academy.spatialCut.debugClassification");
            private static final Map<UUID, Session> SESSIONS = new LinkedHashMap<>();
            private static final Map<UUID, Double> ENDED_SESSIONS =
                    new LinkedHashMap<>(16, 0.75f, true);
            private static final CutSimulation VFX = new CutSimulation();
            private static final CutRenderer RENDERER = new CutRenderer();
            private static final CutLineRenderer MATERIAL_RENDERER =
                    new CutLineRenderer(LineKind.MATERIAL);
            private static final CutLineRenderer GLOW_RENDERER =
                    new CutLineRenderer(LineKind.GLOW);

            private static final @Nullable RenderTarget[] sourceValidationDepthTargets =
                    new RenderTarget[MAX_SOURCE_VALIDATION_CUTS];
            private static final @Nullable RenderTarget[] sourceValidationMaskTargets =
                    new RenderTarget[MAX_SOURCE_VALIDATION_CUTS];
            private static final boolean[] sourceValidationReady =
                    new boolean[MAX_SOURCE_VALIDATION_CUTS];
            private static final int[] sourceValidationIdleFrames =
                    new int[MAX_SOURCE_VALIDATION_CUTS];
            private static final Vector4f[] sourceValidationPlanes = newVector4Array();
            private static final Vector4f[] sourceValidationOffsets = newVector4Array();
            private static final Map<Long, Integer> sourceValidationSides = new HashMap<>();
            private static final Matrix4f sourceValidationInverseProjection = new Matrix4f();
            private static @Nullable GpuBuffer postUniforms;

            private static Vector4f[] newVector4Array() {
                var values = new Vector4f[MAX_SOURCE_VALIDATION_CUTS];
                for (var i = 0; i < values.length; i++) values[i] = new Vector4f();
                return values;
            }

            private static int postUniformSize() {
                var calculator = new Std140SizeCalculator().putVec4();
                for (var i = 0; i < MAX_SOURCE_VALIDATION_CUTS * 2; i++) {
                    calculator.putVec4();
                }
                return calculator.putMat4f().get();
            }

            private Vfx() {
            }

            static double localSessionStartTick(
                    double clientNow,
                    long serverStartTick,
                    long firstCreatedTick
            ) {
                if (!Double.isFinite(clientNow) || firstCreatedTick < serverStartTick) {
                    return Double.NaN;
                }
                return clientNow - ((double) firstCreatedTick - (double) serverStartTick);
            }

            static double localSessionTick(
                    double localStartTick,
                    long serverStartTick,
                    long serverTick
            ) {
                if (!Double.isFinite(localStartTick) || serverTick < serverStartTick) {
                    return Double.NaN;
                }
                return localStartTick + ((double) serverTick - (double) serverStartTick);
            }

            public static boolean validTimeline(long sessionStartTick, long createdTick, long endTick) {
                if (createdTick < sessionStartTick || createdTick >= endTick) return false;
                try {
                    var duration = Math.subtractExact(endTick, sessionStartTick);
                    return duration > 0L && duration <= 600L;
                } catch (ArithmeticException overflow) {
                    return false;
                }
            }

            static void retainSourceValidationSides(
                    Map<Long, Integer> sides,
                    Set<Long> activeIds
            ) {
                if (activeIds.isEmpty()) {
                    sides.clear();
                    return;
                }
                sides.keySet().retainAll(activeIds);
            }

            static int lineGrowthVertexRequirement(int writtenVertices) {
                return growthVertexRequirement(writtenVertices, LINE_MAX_VERTICES_PER_SEGMENT, 64);
            }

            static int clipLineVertices(
                    double[] input,
                    int inputCount,
                    float minimumW,
                    double[] output
            ) {
                if (input == output
                        || inputCount < 3 || inputCount > LINE_CLIP_MAX_VERTICES
                        || input.length < inputCount * LINE_CLIP_VERTEX_COMPONENTS
                        || output.length < LINE_CLIP_SCRATCH_COMPONENTS
                        || !Float.isFinite(minimumW)) {
                    return 0;
                }
                for (var component = 0;
                     component < inputCount * LINE_CLIP_VERTEX_COMPONENTS;
                     component++) {
                    if (!Double.isFinite(input[component])) return 0;
                }

                var outputCount = 0;
                var previous = (inputCount - 1) * LINE_CLIP_VERTEX_COMPONENTS;
                var previousInside = input[previous + 7] >= minimumW;
                for (var vertex = 0; vertex < inputCount; vertex++) {
                    var current = vertex * LINE_CLIP_VERTEX_COMPONENTS;
                    var currentInside = input[current + 7] >= minimumW;
                    if (currentInside != previousInside) {
                        if (outputCount >= LINE_CLIP_MAX_VERTICES
                                || !interpolateLineClip(
                                input, previous, current, minimumW,
                                output, outputCount * LINE_CLIP_VERTEX_COMPONENTS)) {
                            return 0;
                        }
                        outputCount++;
                    }
                    if (currentInside) {
                        if (outputCount >= LINE_CLIP_MAX_VERTICES) return 0;
                        System.arraycopy(input, current, output,
                                outputCount * LINE_CLIP_VERTEX_COMPONENTS,
                                LINE_CLIP_VERTEX_COMPONENTS);
                        outputCount++;
                    }
                    previous = current;
                    previousInside = currentInside;
                }
                return outputCount >= 3 ? outputCount : 0;
            }

            private static boolean interpolateLineClip(
                    double[] input,
                    int from,
                    int to,
                    float minimumW,
                    double[] output,
                    int destination
            ) {
                var t = SpacialExcisionMath.frontClipInterpolation(
                        (float) input[from + 7], (float) input[to + 7], minimumW);
                if (!Float.isFinite(t)) return false;
                for (var component = 0; component < 3; component++) {
                    var value = input[from + component]
                            + (input[to + component] - input[from + component]) * t;
                    if (!Double.isFinite(value)) return false;
                    output[destination + component] = value;
                }
                for (var component = 3; component < 7; component++) {
                    var value = lerpLineAttribute(
                            (float) input[from + component], (float) input[to + component], t);
                    if (!Float.isFinite(value)) return false;
                    output[destination + component] = value;
                }
                output[destination + 7] = minimumW;
                return true;
            }

            private static float lerpLineAttribute(float from, float to, float t) {
                return from + (to - from) * t;
            }

            static int growthVertexRequirement(int writtenVertices, int additionalVertices, int paddingVertices) {
                var required = (long) writtenVertices + additionalVertices + paddingVertices;
                return writtenVertices < 0 || additionalVertices < 0 || paddingVertices < 0
                        || required > Integer.MAX_VALUE ? -1 : (int) required;
            }

            static int growthByteCapacity(int oldByteCapacity, int requiredVertices, int stride) {
                if (oldByteCapacity < 0 || requiredVertices < 0 || stride <= 0) return -1;
                var requiredBytes = (long) requiredVertices * stride;
                var doubledBytes = (long) oldByteCapacity * 2L;
                var capacity = Math.max(requiredBytes, doubledBytes);
                return capacity > Integer.MAX_VALUE ? -1 : (int) capacity;
            }

            static @Nullable ByteBuffer grow(ByteBuffer old, int requiredVertices, int stride) {
                var byteCapacity = growthByteCapacity(old.capacity(), requiredVertices, stride);
                if (byteCapacity < 0) return null;
                var replacement = BufferUtils.createByteBuffer(byteCapacity);
                old.flip();
                replacement.put(old);
                return replacement;
            }

            public static void init() {
                var device = RenderSystem.getDevice();
                RENDERER.init(device);
                MATERIAL_RENDERER.init(device);
                GLOW_RENDERER.init(device);
            }

            public static void sampleFrame(FrameContext ctx) {
                VFX.sample(new CutCamera(
                        ctx.cameraPos(), ctx.projectionMatrix(), ctx.viewRotationMatrix(),
                        ctx.projectionUniform()), ctx.gameTime());
            }

            public static boolean hasGlowLines() {
                return VFX.latestLineVertexCount > 0;
            }

            public static void renderGlowLines(
                    @Nullable GpuTextureView color,
                    @Nullable GpuTextureView depth
            ) {
                if (color == null || depth == null) return;
                var vertices = VFX.latestLineVertices;
                var camera = VFX.latestCamera;
                if (vertices == null || camera == null || VFX.latestLineVertexCount <= 0) return;
                GLOW_RENDERER.renderGlowInput(color, depth, camera, VFX.latestGameTime,
                        vertices.duplicate(), VFX.latestLineVertexCount);
            }

            public static void addSegment(
                    UUID sessionId,
                    long sessionStartTick,
                    long createdTick,
                    long endTick,
                    Identifier dimension,
                    Vec3 start,
                    Vec3 end,
                    float preTeleportYaw,
                    long seed
            ) {
                var level = Minecraft.getInstance().level;
                if (level == null) return;
                if (!validTimeline(sessionStartTick, createdTick, endTick)
                        || !SpacialExcisionMath.isFinite(start)
                        || !SpacialExcisionMath.isFinite(end)
                        || !Float.isFinite(preTeleportYaw)) {
                    return;
                }
                var basis = SpacialExcisionMath.planeBasis(start, end, preTeleportYaw).orElse(null);
                if (basis == null) return;
                var delta = end.subtract(start);
                var length = delta.length();
                if (!SpacialExcisionMath.isFinite(delta) || !Double.isFinite(length) || length <= 0.001) {
                    return;
                }
                var center = start.add(end).scale(0.5);
                var requestedFalloffWidth = CutSimulation.displacementFalloffWidth(basis);
                var clientNow = (double) level.getGameTime()
                        + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
                pruneEndedSessions(clientNow);
                if (ENDED_SESSIONS.containsKey(sessionId)) return;
                var session = SESSIONS.get(sessionId);
                if (session == null) {
                    var localStartTick = localSessionStartTick(
                            clientNow, sessionStartTick, createdTick);
                    if (!Double.isFinite(localStartTick)) return;
                    session = new Session(sessionStartTick, endTick, localStartTick);
                    SESSIONS.put(sessionId, session);
                }
                if (session.endTick != endTick || session.startTick != sessionStartTick) return;
                var localCreatedTick = localSessionTick(
                        session.localStartTick, sessionStartTick, createdTick);
                if (!Double.isFinite(localCreatedTick)) return;
                session.segments.add(new Segment(
                        localCreatedTick, dimension, start, end, delta, length, center,
                        requestedFalloffWidth, seed,
                        basis, RenderMath.flowParams(seed),
                        RenderMath.breathParams(seed)));
            }

            public static void endSession(UUID sessionId) {
                SESSIONS.remove(sessionId);
                var level = Minecraft.getInstance().level;
                var now = level == null ? 0.0 : (double) level.getGameTime();
                rememberEndedSession(sessionId, now + TOMBSTONE_TICKS);
                pruneEndedSessions(now);
            }

            public static void clear() {
                SESSIONS.clear();
                ENDED_SESSIONS.clear();
                VFX.clearLatestFrame();
                for (var slot = 0; slot < MAX_SOURCE_VALIDATION_CUTS; slot++) {
                    sourceValidationReady[slot] = false;
                    sourceValidationIdleFrames[slot] = 0;
                    sourceValidationPlanes[slot].zero();
                    sourceValidationOffsets[slot].zero();
                }
            }

            private static void pruneEndedSessions(double now) {
                ENDED_SESSIONS.entrySet().removeIf(entry -> now >= entry.getValue());
            }

            private static void rememberEndedSession(UUID sessionId, double expiryTick) {
                ENDED_SESSIONS.remove(sessionId);
                ENDED_SESSIONS.put(sessionId, expiryTick);
                while (ENDED_SESSIONS.size() > MAX_ENDED_SESSION_TOMBSTONES) {
                    var iterator = ENDED_SESSIONS.entrySet().iterator();
                    iterator.next();
                    iterator.remove();
                }
            }

            public static void releaseTransientResources() {
                clear();
                for (var i = 0; i < MAX_SOURCE_VALIDATION_CUTS; i++) {
                    destroySourceValidationSlot(i);
                }
                sourceValidationInverseProjection.identity();
                sourceValidationSides.clear();
            }

            private static void destroySourceValidationSlot(int slot) {
                var depthTarget = sourceValidationDepthTargets[slot];
                if (depthTarget != null) {
                    depthTarget.destroyBuffers();
                }
                var maskTarget = sourceValidationMaskTargets[slot];
                if (maskTarget != null) {
                    maskTarget.destroyBuffers();
                }
                sourceValidationDepthTargets[slot] = null;
                sourceValidationMaskTargets[slot] = null;
                sourceValidationReady[slot] = false;
                sourceValidationIdleFrames[slot] = 0;
                sourceValidationPlanes[slot].zero();
                sourceValidationOffsets[slot].zero();
            }

            private static void ageSourceValidationSlots() {
                for (var slot = 0; slot < MAX_SOURCE_VALIDATION_CUTS; slot++) {
                    if (sourceValidationReady[slot]) {
                        sourceValidationIdleFrames[slot] = 0;
                    } else if (sourceValidationDepthTargets[slot] != null
                            || sourceValidationMaskTargets[slot] != null) {
                        if (++sourceValidationIdleFrames[slot] >= SOURCE_VALIDATION_IDLE_FRAME_LIMIT) {
                            destroySourceValidationSlot(slot);
                        }
                    }
                }
            }

            public static void close() {
                releaseTransientResources();
                RENDERER.close();
                MATERIAL_RENDERER.close();
                GLOW_RENDERER.close();
            }

            public static void prepareSourceValidation() {
                for (var slot = 0; slot < MAX_SOURCE_VALIDATION_CUTS; slot++) {
                    sourceValidationReady[slot] = false;
                    sourceValidationPlanes[slot].zero();
                    sourceValidationOffsets[slot].zero();
                }
                sourceValidationInverseProjection.identity();
                var camera = VFX.latestCamera;
                var projection = FrameProjectionContext.currentCopy();
                if (camera == null || projection == null || !projection.isFinite()) {
                    ageSourceValidationSlots();
                    return;
                }
                sourceValidationInverseProjection.set(projection).invert();
                if (!sourceValidationInverseProjection.isFinite()) {
                    sourceValidationInverseProjection.identity();
                    ageSourceValidationSlots();
                    return;
                }
                var inverseTranspose = new Matrix4f(camera.viewRotationMatrix()).invert().transpose();
                if (!inverseTranspose.isFinite()) {
                    ageSourceValidationSlots();
                    return;
                }

                var cameraPosition = new Vec3(camera.pos());
                var candidates = new ArrayList<RenderMath.BackgroundCandidate>();
                var byId = new LinkedHashMap<Long, VisibleCut>();
                for (var visible : VFX.latestVisibleCuts) {
                    var segment = visible.segment();
                    var area = projectedArea(camera, visible);
                    var distanceSquared = segment.center().distanceToSqr(cameraPosition);
                    var stableId = segment.seed();
                    while (byId.containsKey(stableId)) stableId++;
                    byId.put(stableId, visible);
                    candidates.add(new RenderMath.BackgroundCandidate(
                            stableId, area, distanceSquared));
                }
                retainSourceValidationSides(sourceValidationSides, byId.keySet());

                var selectedIds = RenderMath.selectBackgroundIds(
                        candidates, MAX_SOURCE_VALIDATION_CUTS);
                var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
                var readySlot = 0;
                for (var selectedId : selectedIds) {
                    if (readySlot >= MAX_SOURCE_VALIDATION_CUTS) break;
                    var visible = byId.get(selectedId);
                    if (visible == null) continue;
                    var depthTarget = ensureSourceValidationDepthTarget(
                            readySlot, main.width, main.height);
                    var maskTarget = ensureSourceValidationMaskTarget(
                            readySlot, main.width, main.height);
                    var plane = sourceValidationPlane(
                            camera, visible, selectedId, inverseTranspose);
                    var mapping = affineCutMapping(
                            camera, projection, visible, visible.worldDisplacement(),
                            main.width, main.height);
                    if (plane == null || mapping == null
                            || RENDERER.renderSelectedGeometry(
                            readySlot, camera, visible, maskTarget, depthTarget) < 0.0f) {
                        continue;
                    }
                    sourceValidationPlanes[readySlot].set(plane);
                    sourceValidationOffsets[readySlot].set(
                            mapping.positiveUvOffset().x,
                            mapping.positiveUvOffset().y,
                            mapping.negativeUvOffset().x,
                            mapping.negativeUvOffset().y);
                    sourceValidationReady[readySlot] = true;
                    readySlot++;
                }
                ageSourceValidationSlots();
            }

            private static Vec3 requestedWorldDisplacement(
                    SpacialExcisionMath.PlaneBasis basis, float progress
            ) {
                return new Vec3(RenderMath.worldDisplacement(
                        new Vector3f(
                                (float) basis.tangent().x,
                                (float) basis.tangent().y,
                                (float) basis.tangent().z),
                        new Vector3f(
                                (float) basis.planeUp().x,
                                (float) basis.planeUp().y,
                                (float) basis.planeUp().z),
                        HORIZONTAL_SLIDE_DISTANCE_BLOCKS,
                        VERTICAL_CONVERGENCE_DISTANCE_BLOCKS,
                        progress));
            }

            private static RenderMath.@Nullable AffineCutMapping affineCutMapping(
                    CutCamera camera,
                    Matrix4f projection,
                    VisibleCut visible,
                    Vec3 worldDisplacement,
                    int viewportWidth,
                    int viewportHeight
            ) {
                var footprint = CutSimulation.footprintPoints(visible, camera);
                if (footprint.isEmpty() || !SpacialExcisionMath.isFinite(worldDisplacement)) {
                    return null;
                }
                var supportWorld = Vec3.ZERO;
                for (var point : footprint) supportWorld = supportWorld.add(point);
                supportWorld = supportWorld.scale(1.0 / footprint.size());

                var supportView = new Vector4f(
                        (float) (supportWorld.x - camera.pos().x),
                        (float) (supportWorld.y - camera.pos().y),
                        (float) (supportWorld.z - camera.pos().z),
                        1.0f).mul(camera.viewRotationMatrix());
                var displacementView = new Vector4f(
                        (float) worldDisplacement.x,
                        (float) worldDisplacement.y,
                        (float) worldDisplacement.z,
                        0.0f).mul(camera.viewRotationMatrix());
                if (!supportView.isFinite() || !displacementView.isFinite()) return null;
                return RenderMath.regularizedAffineCutMapping(
                        new Vector3f(supportView.x, supportView.y, supportView.z),
                        new Vector3f(displacementView.x, displacementView.y, displacementView.z),
                        projection, viewportWidth, viewportHeight);
            }

            private static double projectedArea(CutCamera camera, VisibleCut visible) {
                var clipPoints = new ArrayList<RenderMath.ClipPoint>(4);
                for (var point : CutSimulation.footprintPoints(visible, camera)) {
                    var clip = CutSimulation.projectClip(camera, point);
                    if (clip == null) return 0.0;
                    clipPoints.add(new RenderMath.ClipPoint(
                            clip.x, clip.y, clip.w));
                }
                return RenderMath.clippedProjectedArea(
                        clipPoints, NEAR_CLIP_MIN_W);
            }

            private static @Nullable Vector4f sourceValidationPlane(
                    CutCamera camera,
                    VisibleCut visible,
                    long stableId,
                    Matrix4f inverseTranspose
            ) {
                var segment = visible.segment();
                var basis = visible.basis();
                var cameraWorld = new Vec3(camera.pos());
                var planePoint = segment.start();
                var signedDistance = cameraWorld.subtract(planePoint).dot(basis.planeNormal());
                if (!Double.isFinite(signedDistance)) return null;
                var previousSide = sourceValidationSides.getOrDefault(
                        stableId, signedDistance >= 0.0 ? 1 : -1);
                var stableSide = RenderMath.sourceValidationSide(
                        signedDistance, previousSide);
                sourceValidationSides.put(stableId, stableSide);
                var side = (float) stableSide;
                var normal = new Vector3f(
                        (float) basis.planeNormal().x,
                        (float) basis.planeNormal().y,
                        (float) basis.planeNormal().z).mul(-side);
                var relativePoint = new Vector3f(
                        (float) (planePoint.x - camera.pos().x),
                        (float) (planePoint.y - camera.pos().y),
                        (float) (planePoint.z - camera.pos().z));
                var worldRelativePlane = new Vector4f(normal, -normal.dot(relativePoint));
                var viewPlane = inverseTranspose.transform(worldRelativePlane);
                var length = (float) Math.sqrt(
                        viewPlane.x * viewPlane.x
                                + viewPlane.y * viewPlane.y
                                + viewPlane.z * viewPlane.z);
                if (!Float.isFinite(length) || length < 1.0e-5f) return null;
                viewPlane.div(length);
                return viewPlane.isFinite() && viewPlane.w <= 1.0e-4f ? viewPlane : null;
            }

            private static RenderTarget ensureSourceValidationDepthTarget(
                    int slot, int width, int height
            ) {
                var target = sourceValidationDepthTargets[slot];
                if (target == null) {
                    target = new TextureTarget(null, width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
                    sourceValidationDepthTargets[slot] = target;
                } else if (target.width != width || target.height != height) {
                    target.resize(width, height);
                }
                return target;
            }

            private static RenderTarget ensureSourceValidationMaskTarget(
                    int slot, int width, int height
            ) {
                var target = sourceValidationMaskTargets[slot];
                if (target == null) {
                    target = new TextureTarget(null, width, height, GpuFormat.RGBA16_FLOAT, null);
                    sourceValidationMaskTargets[slot] = target;
                } else if (target.width != width || target.height != height) {
                    target.resize(width, height);
                }
                return target;
            }

            public static void renderPost() {
                var minecraft = Minecraft.getInstance();
                var camera = VFX.latestCamera;
                var mainTarget = minecraft.gameRenderer.mainRenderTarget();
                var main = mainTarget.getColorTextureView();
                var mainDepth = mainTarget.getDepthTextureView();
                var scene = PostEffect.MAIN_SCENE.getColorTextureView();
                if (sourceValidationReady[0] && camera != null && postUniforms != null
                        && main != null && mainDepth != null && scene != null) {
                    var validationDepth0 = sourceValidationDepthTargets[0] == null
                            ? null : sourceValidationDepthTargets[0].getDepthTextureView();
                    var validationMask0 = sourceValidationMaskTargets[0] == null
                            ? null : sourceValidationMaskTargets[0].getColorTextureView();
                    if (validationDepth0 != null && validationMask0 != null) {
                        var sceneSampler = RenderSystem.getSamplerCache()
                                .getClampToEdge(FilterMode.LINEAR);
                        var maskSampler = RenderSystem.getSamplerCache()
                                .getClampToEdge(FilterMode.LINEAR);
                        var depthSampler = RenderSystem.getSamplerCache()
                                .getClampToEdge(FilterMode.NEAREST);
                        writePostUniforms();
                        var textures = new ArrayList<TextureBinding>(3 + MAX_SOURCE_VALIDATION_CUTS * 2);
                        textures.add(new TextureBinding("Sampler0", scene, sceneSampler));
                        textures.add(new TextureBinding("Sampler3", mainDepth, depthSampler));
                        var validBindings = true;
                        for (var slot = 0; slot < MAX_SOURCE_VALIDATION_CUTS; slot++) {
                            var slotDepthTarget = sourceValidationDepthTargets[slot];
                            var slotMaskTarget = sourceValidationMaskTargets[slot];
                            var validationDepth = sourceValidationReady[slot] && slotDepthTarget != null
                                    ? slotDepthTarget.getDepthTextureView()
                                    : validationDepth0;
                            var validationMask = sourceValidationReady[slot] && slotMaskTarget != null
                                    ? slotMaskTarget.getColorTextureView()
                                    : validationMask0;
                            if (validationDepth == null || validationMask == null) {
                                validBindings = false;
                                break;
                            }
                            textures.add(new TextureBinding("Sampler" + (4 + slot),
                                    validationDepth, depthSampler));
                            textures.add(new TextureBinding("Sampler" + (9 + slot),
                                    validationMask, maskSampler));
                        }
                        if (!validBindings) {
                            VFX.renderVisibleLines();
                            return;
                        }
                        Render.runBlitPass(
                                main,
                                CutPipelines.SPATIAL_CUT_POST,
                                Render.Buffers.getInstance().getFSQuadVBNDC(),
                                textures,
                                List.of(
                                        new UniformBinding("DynamicTransforms",
                                                RenderSystem.getDynamicUniforms().writeTransform(
                                                        camera.viewRotationMatrix())),
                                        new UniformBinding("Projection", projectionFor(camera)),
                                        new UniformBinding("SpatialCutPost", postUniforms.slice())
                                ),
                                false
                        );
                    }
                }
                VFX.renderVisibleLines();
            }

            private static void writePostUniforms() {
                if (postUniforms == null) return;
                try (var stack = MemoryStack.stackPush()) {
                    var builder = Std140Builder.onStack(stack, POST_UNIFORM_SIZE);
                    var selectedCount = 0.0f;
                    for (var ready : sourceValidationReady) {
                        if (ready) selectedCount += 1.0f;
                    }
                    builder.putVec4(new Vector4f(
                            selectedCount,
                            RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1.0f : 0.0f,
                            DEBUG_POST_CLASSIFICATION ? 1.0f : 0.0f,
                            0.0f));
                    for (var plane : sourceValidationPlanes) builder.putVec4(plane);
                    for (var offset : sourceValidationOffsets) builder.putVec4(offset);
                    builder.putMat4f(sourceValidationInverseProjection);
                    RenderSystem.getDevice().createCommandEncoder()
                            .writeToBuffer(postUniforms.slice(), builder.get());
                }
            }

            private static GpuBufferSlice projectionFor(CutCamera camera) {
                var captured = camera.projectionUniform();
                return captured != null
                        ? captured
                        : Render.Buffers.getInstance().getProjectionUB(camera.projectionMatrix()).slice();
            }

            private static final class Session {
                private final long startTick;
                private final long endTick;
                private final double localStartTick;
                private final double localEndTick;
                private final List<Segment> segments = new ArrayList<>();

                private Session(long startTick, long endTick, double localStartTick) {
                    this.startTick = startTick;
                    this.endTick = endTick;
                    this.localStartTick = localStartTick;
                    this.localEndTick = localSessionTick(localStartTick, startTick, endTick);
                }
            }

            private record Segment(
                    double localCreatedTick,
                    Identifier dimension,
                    Vec3 start,
                    Vec3 end,
                    Vec3 delta,
                    double length,
                    Vec3 center,
                    float requestedFalloffWidth,
                    long seed,
                    SpacialExcisionMath.PlaneBasis basis,
                    RenderMath.FlowParams flowParams,
                    RenderMath.BreathParams breathParams
            ) {
            }

            private record VisibleCut(
                    Segment segment,
                    float progress,
                    float breathScale,
                    SpacialExcisionMath.PlaneBasis basis,
                    Vec3 worldDisplacement,
                    float falloffWidth,
                    Vec3 proxyOffset,
                    boolean clipOriginalToCameraFront
            ) {
            }

            private record NearProxy(Vec3 offset, boolean clipOriginalToCameraFront) {
                private static final NearProxy NONE = new NearProxy(Vec3.ZERO, false);
            }

            private static final class CutSimulation {
                private ByteBuffer lineVertexData = BufferUtils.createByteBuffer(
                        INITIAL_VERTICES * LINE_VERTEX_STRIDE);
                private final double[] lineClipScratchA = new double[LINE_CLIP_SCRATCH_COMPONENTS];
                private final double[] lineClipScratchB = new double[LINE_CLIP_SCRATCH_COMPONENTS];
                private @Nullable ByteBuffer latestLineVertices;
                private int latestLineVertexCount;
                private @Nullable CutCamera latestCamera;
                private float latestGameTime;
                private List<VisibleCut> latestVisibleCuts = List.of();

                private void clearLatestFrame() {
                    latestLineVertices = null;
                    latestLineVertexCount = 0;
                    latestCamera = null;
                    latestGameTime = 0.0f;
                    latestVisibleCuts = List.of();
                    sourceValidationSides.clear();
                }

                private void sample(CutCamera camera, float gameTime) {
                    var level = Minecraft.getInstance().level;
                    if (level == null) {
                        clearLatestFrame();
                        return;
                    }
                    var now = gameTime;
                    var dimension = level.dimension().identifier();
                    List<Session> snapshot;
                    pruneEndedSessions(now);
                    var iterator = SESSIONS.entrySet().iterator();
                    while (iterator.hasNext()) {
                        var entry = iterator.next();
                        if (now < entry.getValue().localEndTick) continue;
                        rememberEndedSession(entry.getKey(), now + TOMBSTONE_TICKS);
                        iterator.remove();
                    }
                    snapshot = List.copyOf(SESSIONS.values());
                    if (snapshot.isEmpty()) {
                        clearLatestFrame();
                        return;
                    }

                    lineVertexData.clear();
                    var lineData = lineVertexData;
                    var lineVertices = 0;
                    var mainTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
                    var visibleCuts = new ArrayList<VisibleCut>();
                    for (var session : snapshot) {
                        for (var segment : session.segments) {
                            if (!dimension.equals(segment.dimension)) continue;
                            var progress = SpacialExcisionMath.transitionProgress(
                                    now, segment.localCreatedTick, session.localEndTick);
                            if (progress <= 0.0f) continue;
                            var basis = segment.basis;
                            var falloffWidth = effectiveFalloffWidth(
                                    camera, segment, basis, mainTarget.width, mainTarget.height);
                            if (!isPotentiallyVisible(camera, segment, basis, falloffWidth)) continue;
                            var proxy = nearMaskProxy(camera, segment, basis, falloffWidth);
                            var visible = new VisibleCut(
                                    segment, progress,
                                    RenderMath.breathScale(segment.breathParams, now),
                                    basis, requestedWorldDisplacement(basis, progress),
                                    falloffWidth,
                                    proxy.offset(), proxy.clipOriginalToCameraFront());
                            visibleCuts.add(visible);
                            var lineRequired = LINE_MAX_VERTICES_PER_SEGMENT;
                            var lineGrowthRequirement = lineGrowthVertexRequirement(lineVertices);
                            if (lineGrowthRequirement < 0) {
                                clearLatestFrame();
                                return;
                            }
                            if (lineData.remaining() < (long) lineRequired * LINE_VERTEX_STRIDE) {
                                var grown = grow(lineData, lineGrowthRequirement, LINE_VERTEX_STRIDE);
                                if (grown == null) {
                                    clearLatestFrame();
                                    return;
                                }
                                lineData = grown;
                                lineVertexData = grown;
                            }
                            lineVertices += appendLine(lineData, visible, camera);
                        }
                    }
                    if (!visibleCuts.isEmpty()) {
                        latestCamera = camera;
                        latestGameTime = now;
                        latestVisibleCuts = List.copyOf(visibleCuts);
                    } else {
                        clearLatestFrame();
                    }
                    if (lineVertices > 0) {
                        lineData.flip();
                        latestLineVertices = lineData.asReadOnlyBuffer();
                        latestLineVertexCount = lineVertices;
                    } else {
                        latestLineVertices = null;
                        latestLineVertexCount = 0;
                    }
                }

                private void renderVisibleLines() {
                    var vertices = latestLineVertices;
                    var camera = latestCamera;
                    if (vertices == null || camera == null || latestLineVertexCount <= 0) return;
                    MATERIAL_RENDERER.renderAfterPost(
                            camera, latestGameTime, vertices.duplicate(), latestLineVertexCount);
                }

                private static int appendSegment(
                        ByteBuffer target,
                        VisibleCut visible,
                        CutCamera camera
                ) {
                    var segment = visible.segment();
                    var basis = visible.basis();
                    var length = segment.length;
                    if (!Double.isFinite(length) || length <= 0.001) return 0;

                    var falloffWidth = visible.falloffWidth();
                    var worldDisplacement = visible.worldDisplacement();
                    var count = 0;
                    for (var i = 0; i + 1 < MASK_LONGITUDINAL_SECTIONS; i++) {
                        var along = longitudinalDistanceAt(i, length, falloffWidth);
                        var nextAlong = longitudinalDistanceAt(i + 1, length, falloffWidth);
                        var coverage0 = longitudinalCoverage(along, length, falloffWidth);
                        var coverage1 = longitudinalCoverage(nextAlong, length, falloffWidth);
                        var start = segment.start.add(basis.tangent().scale(along));
                        var end = segment.start.add(basis.tangent().scale(nextAlong));
                        count += appendMaskHalf(
                                target, start, end, basis, false, worldDisplacement,
                                falloffWidth,
                                coverage0, coverage1, visible, camera);
                        count += appendMaskHalf(
                                target, start, end, basis, true, worldDisplacement,
                                falloffWidth,
                                coverage0, coverage1, visible, camera);
                    }
                    return count;
                }

                private static NearProxy nearMaskProxy(
                        CutCamera camera,
                        Segment segment,
                        SpacialExcisionMath.PlaneBasis basis,
                        float falloffWidth
                ) {
                    var minimumW = Float.POSITIVE_INFINITY;
                    var maximumW = Float.NEGATIVE_INFINITY;
                    for (var point : footprintPoints(segment, basis, Vec3.ZERO, falloffWidth)) {
                        var clip = projectClip(camera, point);
                        if (clip == null) return NearProxy.NONE;
                        minimumW = Math.min(minimumW, clip.w);
                        maximumW = Math.max(maximumW, clip.w);
                    }
                    var plan = RenderMath.nearMaskProxyPlan(
                            minimumW, maximumW, NEAR_CLIP_MIN_W);
                    if (plan.offset() <= 0.0f) return NearProxy.NONE;
                    var worldForward = new Vector3f(0.0f, 0.0f, -1.0f);
                    new Matrix4f(camera.viewRotationMatrix()).invert()
                            .transformDirection(worldForward);
                    var lengthSquared = worldForward.lengthSquared();
                    if (!Float.isFinite(lengthSquared) || lengthSquared < 1.0e-8f) {
                        return NearProxy.NONE;
                    }
                    worldForward.normalize().mul(plan.offset());
                    return new NearProxy(
                            new Vec3(worldForward), plan.clipOriginalToCameraFront());
                }

                private static List<Vec3> footprintPoints(VisibleCut visible, CutCamera camera) {
                    var original = footprintPoints(
                            visible.segment(), visible.basis(), Vec3.ZERO, visible.falloffWidth());
                    if (visible.clipOriginalToCameraFront()) {
                        original = clipWorldPolygonToFront(original, camera, 0.0f);
                    }
                    if (original.isEmpty()) return List.of();
                    return original.stream()
                            .map(point -> point.add(visible.proxyOffset()))
                            .toList();
                }

                private static List<Vec3> footprintPoints(
                        Segment segment,
                        SpacialExcisionMath.PlaneBasis basis,
                        Vec3 proxyOffset,
                        float falloffWidth
                ) {
                    var visibleStart = segment.start.subtract(
                                    basis.tangent().scale(falloffWidth))
                            .add(proxyOffset);
                    var visibleEnd = segment.end.add(
                                    basis.tangent().scale(falloffWidth))
                            .add(proxyOffset);
                    var outerHalfWidth = DISPLACEMENT_RIGID_HALF_WIDTH + falloffWidth;
                    var left = basis.planeUp().scale(-outerHalfWidth);
                    var right = basis.planeUp().scale(outerHalfWidth);
                    return List.of(
                            visibleStart.add(left),
                            visibleEnd.add(left),
                            visibleEnd.add(right),
                            visibleStart.add(right));
                }

                private static float displacementFalloffWidth(
                        SpacialExcisionMath.PlaneBasis basis
                ) {
                    var weights = RenderMath.worldOrientationWeights(
                            vector(basis.tangent()));
                    return RenderMath.adaptiveFalloffWidth(
                            MIN_DISPLACEMENT_FALLOFF_WIDTH,
                            HORIZONTAL_SLIDE_DISTANCE_BLOCKS * weights.horizontal(),
                            VERTICAL_CONVERGENCE_DISTANCE_BLOCKS * weights.vertical());
                }

                private static float effectiveFalloffWidth(
                        CutCamera camera,
                        Segment segment,
                        SpacialExcisionMath.PlaneBasis basis,
                        int viewportWidth,
                        int viewportHeight
                ) {
                    var requested = segment.requestedFalloffWidth;
                    if (viewportWidth < 2 || viewportHeight < 2) {
                        return requested;
                    }

                    var cameraPosition = new Vec3(camera.pos().x, camera.pos().y, camera.pos().z);
                    var delta = segment.delta;
                    var lengthSquared = segment.length * segment.length;
                    if (!Double.isFinite(lengthSquared) || lengthSquared <= 0.0) return requested;
                    var along = cameraPosition.subtract(segment.start).dot(delta) / lengthSquared;
                    along = Math.clamp(along, 0.0, 1.0);
                    var support = segment.start.add(delta.scale(along));

                    var tangent = vector(basis.tangent());
                    var planeUp = vector(basis.planeUp());
                    var tangentPixels = projectedPixelsPerWorldUnit(
                            camera, support, tangent, viewportWidth, viewportHeight);
                    var planeUpPixels = projectedPixelsPerWorldUnit(
                            camera, support, planeUp, viewportWidth, viewportHeight);
                    var projectedPixels = Float.isFinite(tangentPixels)
                            && Float.isFinite(planeUpPixels)
                            ? Math.max(tangentPixels, planeUpPixels)
                            : Float.NaN;
                    if (!Float.isFinite(projectedPixels)
                            || projectedPixels <= 1.0e-6f) {
                        var clip = projectClip(camera, support);
                        var depth = clip != null && Float.isFinite(clip.w)
                                ? Math.max(clip.w, NEAR_CLIP_MIN_W + 0.01f)
                                : NEAR_CLIP_MIN_W + 0.01f;
                        var projection = camera.projectionMatrix();
                        var focalPixels = 0.5f * Math.max(
                                Math.abs(projection.m00()) * viewportWidth,
                                Math.abs(projection.m11()) * viewportHeight);
                        projectedPixels = Float.isFinite(focalPixels)
                                && focalPixels > 1.0e-6f
                                ? focalPixels / depth
                                : Float.NaN;
                    }
                    return RenderMath.screenBoundedFalloffWidth(
                            requested, projectedPixels, viewportWidth, viewportHeight);
                }

                private static float projectedPixelsPerWorldUnit(
                        CutCamera camera,
                        Vec3 origin,
                        Vector3f direction,
                        int viewportWidth,
                        int viewportHeight
                ) {
                    var originClip = projectClip(camera, origin);
                    var displacedClip = projectClip(
                            camera,
                            origin.add(new Vec3(direction.x, direction.y, direction.z)));
                    if (originClip == null || displacedClip == null
                            || originClip.w < NEAR_CLIP_MIN_W
                            || displacedClip.w < NEAR_CLIP_MIN_W) {
                        return Float.NaN;
                    }
                    var originNdcX = originClip.x / originClip.w;
                    var originNdcY = originClip.y / originClip.w;
                    var displacedNdcX = displacedClip.x / displacedClip.w;
                    var displacedNdcY = displacedClip.y / displacedClip.w;
                    var pixels = (float) Math.hypot(
                            0.5f * viewportWidth * (displacedNdcX - originNdcX),
                            0.5f * viewportHeight * (displacedNdcY - originNdcY));
                    return Float.isFinite(pixels) ? pixels : Float.NaN;
                }

                private static List<Vec3> clipWorldPolygonToFront(
                        List<Vec3> input,
                        CutCamera camera,
                        float minimumW
                ) {
                    if (input.size() < 3 || !Float.isFinite(minimumW) || minimumW < 0.0f) {
                        return List.of();
                    }
                    var vertices = new ArrayList<WorldClipVertex>(input.size());
                    for (var point : input) {
                        var clip = projectClip(camera, point);
                        if (clip == null) return List.of();
                        vertices.add(new WorldClipVertex(point, clip.w));
                    }
                    var output = new ArrayList<WorldClipVertex>(vertices.size() + 1);
                    var previous = vertices.get(vertices.size() - 1);
                    var previousInside = previous.clipW() >= minimumW;
                    for (var current : vertices) {
                        var currentInside = current.clipW() >= minimumW;
                        if (currentInside != previousInside) {
                            var t = SpacialExcisionMath.frontClipInterpolation(
                                    previous.clipW(), current.clipW(), minimumW);
                            if (!Float.isFinite(t)) return List.of();
                            output.add(new WorldClipVertex(
                                    previous.point().lerp(current.point(), t), minimumW));
                        }
                        if (currentInside) output.add(current);
                        previous = current;
                        previousInside = currentInside;
                    }
                    return output.size() < 3 ? List.of()
                            : output.stream().map(WorldClipVertex::point).toList();
                }

                private static int appendMaskHalf(
                        ByteBuffer target,
                        Vec3 start,
                        Vec3 end,
                        SpacialExcisionMath.PlaneBasis basis,
                        boolean right,
                        Vec3 worldDisplacement,
                        float falloffWidth,
                        float longitudinalCoverage0,
                        float longitudinalCoverage1,
                        VisibleCut visible,
                        CutCamera camera
                ) {
                    var sign = right ? 1.0f : -1.0f;
                    var planeUp = basis.planeUp();
                    var crossDistance = crossDistanceAt(0, falloffWidth);
                    var offset = sign * crossDistance;
                    var previousStart = start.add(planeUp.scale(offset));
                    var previousEnd = end.add(planeUp.scale(offset));
                    var previousCoverage = sign * crossCoverage(crossDistance, falloffWidth);
                    var count = 0;
                    for (var i = 0; i + 1 < MASK_CROSS_SECTIONS; i++) {
                        var nextDistance = crossDistanceAt(i + 1, falloffWidth);
                        var nextOffset = sign * nextDistance;
                        var nextStart = start.add(planeUp.scale(nextOffset));
                        var nextEnd = end.add(planeUp.scale(nextOffset));
                        var nextCoverage = sign * crossCoverage(nextDistance, falloffWidth);
                        var coverage0 = previousCoverage * longitudinalCoverage0;
                        var coverage1 = nextCoverage * longitudinalCoverage0;
                        var nextCoverage0 = nextCoverage * longitudinalCoverage1;
                        var previousCoverage1 = previousCoverage * longitudinalCoverage1;
                        if (right) {
                            count += putMaskTriangle(
                                    target, previousStart, nextEnd, nextStart,
                                    worldDisplacement,
                                    coverage0, nextCoverage0, coverage1,
                                    visible, camera);
                            count += putMaskTriangle(
                                    target, previousStart, previousEnd, nextEnd,
                                    worldDisplacement,
                                    coverage0, previousCoverage1, nextCoverage0,
                                    visible, camera);
                        } else {
                            count += putMaskTriangle(
                                    target, previousStart, nextStart, nextEnd,
                                    worldDisplacement,
                                    coverage0, coverage1, nextCoverage0,
                                    visible, camera);
                            count += putMaskTriangle(
                                    target, previousStart, nextEnd, previousEnd,
                                    worldDisplacement,
                                    coverage0, nextCoverage0, previousCoverage1,
                                    visible, camera);
                        }
                        previousStart = nextStart;
                        previousEnd = nextEnd;
                        previousCoverage = nextCoverage;
                    }
                    return count;
                }

                private int appendLine(
                        ByteBuffer target,
                        VisibleCut visible,
                        CutCamera camera
                ) {
                    var segment = visible.segment();
                    var progress = visible.progress();
                    var basis = visible.basis();
                    var length = segment.length;
                    if (!Double.isFinite(length) || length <= 0.001) return 0;

                    var flowParams = segment.flowParams;
                    var center = segment.center;
                    var breathScale = visible.breathScale();
                    var count = 0;
                    for (var i = 0; i < LINE_LONGITUDINAL_SECTIONS; i++) {
                        var t0 = (double) i / LINE_LONGITUDINAL_SECTIONS;
                        var t1 = (double) (i + 1) / LINE_LONGITUDINAL_SECTIONS;
                        var start = segment.start.lerp(segment.end, t0);
                        var end = segment.start.lerp(segment.end, t1);
                        var width0 = PORTAL_MAX_HALF_WIDTH * centeredSpindleProfile(t0);
                        var width1 = PORTAL_MAX_HALF_WIDTH * centeredSpindleProfile(t1);
                        var left0 = scaleAround(
                                start.subtract(basis.planeUp().scale(width0)), center, breathScale);
                        var right0 = scaleAround(
                                start.add(basis.planeUp().scale(width0)), center, breathScale);
                        var left1 = scaleAround(
                                end.subtract(basis.planeUp().scale(width1)), center, breathScale);
                        var right1 = scaleAround(
                                end.add(basis.planeUp().scale(width1)), center, breathScale);
                        var flow0 = (float) t0 + flowParams.phase();
                        var flow1 = (float) t1 + flowParams.phase();
                        var materialU0 = flowParams.speed();
                        var materialU1 = flowParams.speed();

                        count += putLineTriangle(
                                target, left0, right0, right1,
                                flow0, flow0, flow1,
                                materialU0, materialU0, materialU1,
                                0.0f, 1.0f, 1.0f,
                                progress, progress, progress, visible, camera);
                        count += putLineTriangle(
                                target, left0, right1, left1,
                                flow0, flow1, flow1,
                                materialU0, materialU1, materialU1,
                                0.0f, 1.0f, 0.0f,
                                progress, progress, progress, visible, camera);
                    }
                    return count;
                }

                private static Vec3 scaleAround(Vec3 point, Vec3 center, float scale) {
                    if (!SpacialExcisionMath.isFinite(point)
                            || !SpacialExcisionMath.isFinite(center)
                            || !Float.isFinite(scale)) {
                        return point;
                    }
                    return center.add(point.subtract(center).scale(scale));
                }

                private static boolean isPotentiallyVisible(
                        CutCamera camera,
                        Segment segment,
                        SpacialExcisionMath.PlaneBasis basis,
                        float falloffWidth
                ) {
                    var delta = segment.delta;
                    var lengthSquared = segment.length * segment.length;
                    if (!Double.isFinite(lengthSquared) || lengthSquared <= 0.0) return false;

                    var cameraWorld = new Vec3(camera.pos().x, camera.pos().y, camera.pos().z);
                    var along = cameraWorld.subtract(segment.start).dot(delta) / lengthSquared;
                    along = Math.clamp(along, 0.0, 1.0);
                    var closest = segment.start.add(delta.scale(along));
                    if (cameraWorld.distanceToSqr(closest) > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
                        return false;
                    }

                    var visibleStart = segment.start.subtract(
                            basis.tangent().scale(falloffWidth));
                    var visibleEnd = segment.end.add(
                            basis.tangent().scale(falloffWidth));
                    var startLeft = visibleStart.subtract(
                            basis.planeUp().scale(DISPLACEMENT_RIGID_HALF_WIDTH + falloffWidth));
                    var startRight = visibleStart.add(
                            basis.planeUp().scale(DISPLACEMENT_RIGID_HALF_WIDTH + falloffWidth));
                    var endLeft = visibleEnd.subtract(
                            basis.planeUp().scale(DISPLACEMENT_RIGID_HALF_WIDTH + falloffWidth));
                    var endRight = visibleEnd.add(
                            basis.planeUp().scale(DISPLACEMENT_RIGID_HALF_WIDTH + falloffWidth));
                    var points = new Vec3[]{startLeft, startRight, endLeft, endRight};
                    var minX = Double.POSITIVE_INFINITY;
                    var maxX = Double.NEGATIVE_INFINITY;
                    var minY = Double.POSITIVE_INFINITY;
                    var maxY = Double.NEGATIVE_INFINITY;
                    var hasFrontPoint = false;
                    for (var point : points) {
                        var clip = project(camera, point);
                        if (clip == null) return true;
                        if (clip.w <= 0.0f) continue;
                        var x = clip.x / clip.w;
                        var y = clip.y / clip.w;
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                        hasFrontPoint = true;
                    }
                    return hasFrontPoint && maxX >= -1.0 && minX <= 1.0
                            && maxY >= -1.0 && minY <= 1.0;
                }

                private static float crossDistanceAt(int index, float falloffWidth) {
                    if (index <= 0) return 0.0f;
                    if (index == 1) return DISPLACEMENT_RIGID_HALF_WIDTH;
                    return DISPLACEMENT_RIGID_HALF_WIDTH
                            + falloffWidth * (index - 1) / MASK_FALLOFF_STEPS;
                }

                private static double longitudinalDistanceAt(
                        int index, double length, float falloffWidth
                ) {
                    if (index < MASK_FALLOFF_STEPS) {
                        return -falloffWidth * (MASK_FALLOFF_STEPS - index)
                                / MASK_FALLOFF_STEPS;
                    }
                    var coreIndex = index - MASK_FALLOFF_STEPS;
                    if (coreIndex <= LINE_LONGITUDINAL_SECTIONS) {
                        return length * coreIndex / LINE_LONGITUDINAL_SECTIONS;
                    }
                    return length + falloffWidth * (coreIndex - LINE_LONGITUDINAL_SECTIONS)
                            / MASK_FALLOFF_STEPS;
                }

                private static float crossCoverage(float crossDistance, float falloffWidth) {
                    if (!Float.isFinite(crossDistance)) return 0.0f;
                    var distance = Math.abs(crossDistance);
                    if (distance <= DISPLACEMENT_RIGID_HALF_WIDTH) return 1.0f;
                    if (distance >= DISPLACEMENT_RIGID_HALF_WIDTH + falloffWidth) return 0.0f;
                    return oneMinusSmootherstep(
                            (distance - DISPLACEMENT_RIGID_HALF_WIDTH)
                                    / falloffWidth);
                }

                private static float longitudinalCoverage(
                        double along,
                        double length,
                        float falloffWidth
                ) {
                    if (!Double.isFinite(along) || !Double.isFinite(length) || length <= 0.0) {
                        return 0.0f;
                    }
                    if (along >= 0.0 && along <= length) return 1.0f;
                    if (along < 0.0) {
                        return oneMinusSmootherstep((float) (-along / falloffWidth));
                    }
                    return oneMinusSmootherstep(
                            (float) ((along - length) / falloffWidth));
                }

                private static float oneMinusSmootherstep(float value) {
                    var t = clamp01(value);
                    return 1.0f - t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
                }

                private static float centeredSpindleProfile(double coordinate) {
                    if (!Double.isFinite(coordinate)) return 0.0f;
                    var t = Math.clamp(coordinate, 0.0, 1.0);
                    var centeredDistance = Math.abs(t * 2.0 - 1.0);
                    var profile = 1.0 - centeredDistance * centeredDistance;
                    return Double.isFinite(profile) ? (float) Math.max(0.0, profile) : 0.0f;
                }

                private static float clamp01(float value) {
                    return Float.isFinite(value) ? Math.clamp(value, 0.0f, 1.0f) : 0.0f;
                }

                private static float clampSigned(float value) {
                    return Float.isFinite(value) ? Math.clamp(value, -1.0f, 1.0f) : 0.0f;
                }

                private static float finiteOrZero(float value) {
                    return Float.isFinite(value) ? value : 0.0f;
                }

                private static Vec3 finiteVec(Vec3 value) {
                    return SpacialExcisionMath.isFinite(value) ? value : Vec3.ZERO;
                }

                private static Vector3f vector(Vec3 value) {
                    return new Vector3f((float) value.x, (float) value.y, (float) value.z);
                }

                private static int putMaskTriangle(
                        ByteBuffer target,
                        Vec3 a,
                        Vec3 b,
                        Vec3 c,
                        Vec3 worldDisplacement,
                        float coverageA,
                        float coverageB,
                        float coverageC,
                        VisibleCut visible,
                        CutCamera camera
                ) {
                    if (!visible.clipOriginalToCameraFront()
                            && visible.proxyOffset().equals(Vec3.ZERO)) {
                        var clipWA = projectLineClipW(camera, a.x, a.y, a.z);
                        var clipWB = projectLineClipW(camera, b.x, b.y, b.z);
                        var clipWC = projectLineClipW(camera, c.x, c.y, c.z);
                        if (clipWA >= NEAR_CLIP_MIN_W
                                && clipWB >= NEAR_CLIP_MIN_W
                                && clipWC >= NEAR_CLIP_MIN_W) {
                            var cameraPos = camera.pos();
                            putMaskVertex(target, a, worldDisplacement, coverageA, cameraPos);
                            putMaskVertex(target, b, worldDisplacement, coverageB, cameraPos);
                            putMaskVertex(target, c, worldDisplacement, coverageC, cameraPos);
                            return 3;
                        }
                    }
                    var first = maskClipVertex(a, worldDisplacement, coverageA, camera);
                    var second = maskClipVertex(b, worldDisplacement, coverageB, camera);
                    var third = maskClipVertex(c, worldDisplacement, coverageC, camera);
                    if (first == null || second == null || third == null) return 0;
                    var clipped = prepareMaskVertices(
                            List.of(first, second, third), visible, camera);
                    if (clipped.size() < 3) return 0;

                    var cameraPos = camera.pos();
                    var count = 0;
                    for (var i = 1; i + 1 < clipped.size(); i++) {
                        putMaskVertex(target, clipped.get(0), cameraPos);
                        putMaskVertex(target, clipped.get(i), cameraPos);
                        putMaskVertex(target, clipped.get(i + 1), cameraPos);
                        count += 3;
                    }
                    return count;
                }

                private static void putMaskVertex(
                        ByteBuffer target,
                        Vec3 point,
                        Vec3 worldDisplacement,
                        float coverage,
                        Vector3f camera
                ) {
                    target.putFloat((float) (point.x - camera.x));
                    target.putFloat((float) (point.y - camera.y));
                    target.putFloat((float) (point.z - camera.z));
                    target.putFloat((float) worldDisplacement.x);
                    target.putFloat((float) worldDisplacement.y);
                    target.putFloat((float) worldDisplacement.z);
                    target.putFloat(clampSigned(coverage));
                }

                private static @Nullable MaskClipVertex maskClipVertex(
                        Vec3 point,
                        Vec3 worldDisplacement,
                        float coverage,
                        CutCamera camera
                ) {
                    var clip = projectClip(camera, point);
                    return clip == null ? null : new MaskClipVertex(
                            point,
                            finiteVec(worldDisplacement),
                            clampSigned(coverage),
                            clip.w);
                }

                private static List<MaskClipVertex> prepareMaskVertices(
                        List<MaskClipVertex> input,
                        VisibleCut visible,
                        CutCamera camera
                ) {
                    var originalFront = visible.clipOriginalToCameraFront()
                            ? clipMaskToFront(input, 0.0f) : input;
                    if (originalFront.size() < 3) return List.of();
                    var moved = new ArrayList<MaskClipVertex>(originalFront.size());
                    for (var vertex : originalFront) {
                        var point = vertex.point().add(visible.proxyOffset());
                        var clip = projectClip(camera, point);
                        if (clip == null) return List.of();
                        moved.add(new MaskClipVertex(
                                point,
                                vertex.worldDisplacement(),
                                vertex.coverage(),
                                clip.w));
                    }
                    return clipMaskToFront(moved, NEAR_CLIP_MIN_W);
                }

                private static List<MaskClipVertex> clipMaskToFront(
                        List<MaskClipVertex> input,
                        float minimumW
                ) {
                    var output = new ArrayList<MaskClipVertex>(4);
                    var previous = input.get(input.size() - 1);
                    var previousInside = previous.clipW() >= minimumW;
                    for (var current : input) {
                        var currentInside = current.clipW() >= minimumW;
                        if (currentInside != previousInside) {
                            var intersection = interpolateMaskClip(previous, current, minimumW);
                            if (intersection == null) return List.of();
                            output.add(intersection);
                        }
                        if (currentInside) output.add(current);
                        previous = current;
                        previousInside = currentInside;
                    }
                    return output;
                }

                private static @Nullable MaskClipVertex interpolateMaskClip(
                        MaskClipVertex from,
                        MaskClipVertex to,
                        float minimumW
                ) {
                    var t = SpacialExcisionMath.frontClipInterpolation(
                            from.clipW(), to.clipW(), minimumW);
                    if (!Float.isFinite(t)) return null;
                    return new MaskClipVertex(
                            from.point().lerp(to.point(), t),
                            from.worldDisplacement().lerp(to.worldDisplacement(), t),
                            lerp(from.coverage(), to.coverage(), t),
                            minimumW);
                }

                private static void putMaskVertex(
                        ByteBuffer target,
                        MaskClipVertex vertex,
                        Vector3f camera
                ) {
                    target.putFloat((float) (vertex.point().x - camera.x));
                    target.putFloat((float) (vertex.point().y - camera.y));
                    target.putFloat((float) (vertex.point().z - camera.z));
                    target.putFloat((float) vertex.worldDisplacement().x);
                    target.putFloat((float) vertex.worldDisplacement().y);
                    target.putFloat((float) vertex.worldDisplacement().z);
                    target.putFloat(clampSigned(vertex.coverage()));
                }

                private int putLineTriangle(
                        ByteBuffer target,
                        Vec3 a,
                        Vec3 b,
                        Vec3 c,
                        float flowA,
                        float flowB,
                        float flowC,
                        float materialUA,
                        float materialUB,
                        float materialUC,
                        float materialVA,
                        float materialVB,
                        float materialVC,
                        float alphaA,
                        float alphaB,
                        float alphaC,
                        VisibleCut visible,
                        CutCamera camera
                ) {
                    if (!putLineClipVertex(
                            lineClipScratchA, 0, a, flowA, materialUA, materialVA,
                            alphaA, camera)
                            || !putLineClipVertex(
                            lineClipScratchA, 1, b, flowB, materialUB, materialVB,
                            alphaB, camera)
                            || !putLineClipVertex(
                            lineClipScratchA, 2, c, flowC, materialUC, materialVC,
                            alphaC, camera)) {
                        return 0;
                    }
                    var clippedCount = prepareLineVertices(3, visible, camera);
                    if (clippedCount < 3) return 0;
                    var clipped = visible.clipOriginalToCameraFront()
                            ? lineClipScratchA : lineClipScratchB;

                    var cameraPos = camera.pos();
                    var count = 0;
                    for (var i = 1; i + 1 < clippedCount; i++) {
                        putLineVertex(target, clipped, 0, cameraPos);
                        putLineVertex(target, clipped, i, cameraPos);
                        putLineVertex(target, clipped, i + 1, cameraPos);
                        count += 3;
                    }
                    return count;
                }

                private static boolean putLineClipVertex(
                        double[] target,
                        int vertex,
                        Vec3 point,
                        float flow,
                        float materialU,
                        float materialV,
                        float alpha,
                        CutCamera camera
                ) {
                    var clipW = projectLineClipW(camera, point.x, point.y, point.z);
                    if (!Float.isFinite(clipW)) return false;
                    var offset = vertex * LINE_CLIP_VERTEX_COMPONENTS;
                    target[offset] = point.x;
                    target[offset + 1] = point.y;
                    target[offset + 2] = point.z;
                    target[offset + 3] = finiteOrZero(flow);
                    target[offset + 4] = finiteOrZero(materialU);
                    target[offset + 5] = clamp01(materialV);
                    target[offset + 6] = clamp01(alpha);
                    target[offset + 7] = clipW;
                    return true;
                }

                private int prepareLineVertices(
                        int inputCount,
                        VisibleCut visible,
                        CutCamera camera
                ) {
                    var moved = lineClipScratchA;
                    var movedCount = inputCount;
                    if (visible.clipOriginalToCameraFront()) {
                        movedCount = clipLineVertices(
                                lineClipScratchA, inputCount, 0.0f, lineClipScratchB);
                        if (movedCount < 3) return 0;
                        moved = lineClipScratchB;
                    }
                    if (!moveAndProjectLineVertices(
                            moved, movedCount, visible.proxyOffset(), camera)) {
                        return 0;
                    }
                    var nearOutput = visible.clipOriginalToCameraFront()
                            ? lineClipScratchA : lineClipScratchB;
                    return clipLineVertices(moved, movedCount, NEAR_CLIP_MIN_W, nearOutput);
                }

                private static boolean moveAndProjectLineVertices(
                        double[] vertices,
                        int vertexCount,
                        Vec3 proxyOffset,
                        CutCamera camera
                ) {
                    for (var vertex = 0; vertex < vertexCount; vertex++) {
                        var offset = vertex * LINE_CLIP_VERTEX_COMPONENTS;
                        var x = vertices[offset] + proxyOffset.x;
                        var y = vertices[offset + 1] + proxyOffset.y;
                        var z = vertices[offset + 2] + proxyOffset.z;
                        var clipW = projectLineClipW(camera, x, y, z);
                        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                                || !Float.isFinite(clipW)) {
                            return false;
                        }
                        vertices[offset] = x;
                        vertices[offset + 1] = y;
                        vertices[offset + 2] = z;
                        vertices[offset + 7] = clipW;
                    }
                    return true;
                }

                private static float projectLineClipW(
                        CutCamera camera,
                        double worldX,
                        double worldY,
                        double worldZ
                ) {
                    var cameraPos = camera.pos();
                    var relativeX = (float) (worldX - cameraPos.x);
                    var relativeY = (float) (worldY - cameraPos.y);
                    var relativeZ = (float) (worldZ - cameraPos.z);
                    var view = camera.viewRotationMatrix();
                    var viewX = Math.fma(view.m00(), relativeX,
                            Math.fma(view.m10(), relativeY,
                                    Math.fma(view.m20(), relativeZ, view.m30())));
                    var viewY = Math.fma(view.m01(), relativeX,
                            Math.fma(view.m11(), relativeY,
                                    Math.fma(view.m21(), relativeZ, view.m31())));
                    var viewZ = Math.fma(view.m02(), relativeX,
                            Math.fma(view.m12(), relativeY,
                                    Math.fma(view.m22(), relativeZ, view.m32())));
                    var viewW = Math.fma(view.m03(), relativeX,
                            Math.fma(view.m13(), relativeY,
                                    Math.fma(view.m23(), relativeZ, view.m33())));
                    var projection = camera.projectionMatrix();
                    var clipX = Math.fma(projection.m00(), viewX,
                            Math.fma(projection.m10(), viewY,
                                    Math.fma(projection.m20(), viewZ, projection.m30() * viewW)));
                    var clipY = Math.fma(projection.m01(), viewX,
                            Math.fma(projection.m11(), viewY,
                                    Math.fma(projection.m21(), viewZ, projection.m31() * viewW)));
                    var clipZ = Math.fma(projection.m02(), viewX,
                            Math.fma(projection.m12(), viewY,
                                    Math.fma(projection.m22(), viewZ, projection.m32() * viewW)));
                    var clipW = Math.fma(projection.m03(), viewX,
                            Math.fma(projection.m13(), viewY,
                                    Math.fma(projection.m23(), viewZ, projection.m33() * viewW)));
                    return Float.isFinite(clipX) && Float.isFinite(clipY)
                            && Float.isFinite(clipZ) && Float.isFinite(clipW)
                            ? clipW : Float.NaN;
                }

                private static void putLineVertex(
                        ByteBuffer target,
                        double[] vertices,
                        int vertex,
                        Vector3f camera
                ) {
                    var offset = vertex * LINE_CLIP_VERTEX_COMPONENTS;
                    target.putFloat((float) (vertices[offset] - camera.x));
                    target.putFloat((float) (vertices[offset + 1] - camera.y));
                    target.putFloat((float) (vertices[offset + 2] - camera.z));
                    target.putFloat(1.0f);
                    target.putFloat(1.0f);
                    target.putFloat(1.0f);
                    target.putFloat(clamp01((float) vertices[offset + 6]));
                    target.putFloat(finiteOrZero((float) vertices[offset + 3]));
                    target.putFloat(finiteOrZero((float) vertices[offset + 4]));
                    target.putFloat(clamp01((float) vertices[offset + 5]));
                }

                private static float lerp(float from, float to, float t) {
                    return from + (to - from) * t;
                }

                private record MaskClipVertex(
                        Vec3 point,
                        Vec3 worldDisplacement,
                        float coverage,
                        float clipW
                ) {
                }

                private record WorldClipVertex(Vec3 point, float clipW) {
                }

                private static @Nullable Vector4f project(CutCamera camera, Vec3 point) {
                    var relative = projectClip(camera, point);
                    return relative != null && Math.abs(relative.w) > 1.0e-5f ? relative : null;
                }

                private static @Nullable Vector4f projectClip(CutCamera camera, Vec3 point) {
                    var relative = new Vector4f(
                            (float) (point.x - camera.pos().x),
                            (float) (point.y - camera.pos().y),
                            (float) (point.z - camera.pos().z),
                            1.0f
                    );
                    relative.mul(camera.viewRotationMatrix()).mul(camera.projectionMatrix());
                    return Float.isFinite(relative.x) && Float.isFinite(relative.y)
                            && Float.isFinite(relative.z) && Float.isFinite(relative.w)
                            ? relative : null;
                }
            }

            private static final class CutRenderer {
                private @Nullable GpuBuffer vertexBuffer;
                private final @Nullable ByteBuffer[] selectedVertexData =
                        new ByteBuffer[MAX_SOURCE_VALIDATION_CUTS];
                private int capacityVertices;

                public void init(GpuDevice device) {
                    vertexBuffer = device.createBuffer(
                            () -> "Spatial Cut Mask Vertices",
                            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                            (long) INITIAL_VERTICES * VERTEX_STRIDE
                    );
                    postUniforms = device.createBuffer(
                            () -> "Spatial Cut Post Uniforms",
                            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                            POST_UNIFORM_SIZE
                    );
                    for (var slot = 0; slot < MAX_SOURCE_VALIDATION_CUTS; slot++) {
                        selectedVertexData[slot] = BufferUtils.createByteBuffer(
                                MASK_MAX_VERTICES_PER_SEGMENT * VERTEX_STRIDE);
                    }
                    capacityVertices = INITIAL_VERTICES;
                }

                private float renderSelectedGeometry(
                        int slot,
                        CutCamera camera,
                        VisibleCut visible,
                        RenderTarget maskTarget,
                        RenderTarget depthTarget
                ) {
                    if (slot < 0 || slot >= MAX_SOURCE_VALIDATION_CUTS || vertexBuffer == null) {
                        return -1.0f;
                    }
                    var vertices = selectedVertexData[slot];
                    if (vertices == null) return -1.0f;
                    vertices.clear();
                    var vertexCount = CutSimulation.appendSegment(vertices, visible, camera);
                    if (vertexCount <= 0) return -1.0f;
                    vertices.flip();
                    if (vertexCount > capacityVertices) grow(vertexCount);
                    if (vertexBuffer == null) return -1.0f;
                    var totalBytes = (long) vertexCount * VERTEX_STRIDE;
                    RenderSystem.getDevice().createCommandEncoder()
                            .writeToBuffer(vertexBuffer.slice(0, totalBytes), vertices);

                    var maskColor = maskTarget.getColorTextureView();
                    var depthColor = depthTarget.getColorTextureView();
                    var targetDepth = depthTarget.getDepthTextureView();
                    var mainDepth = Minecraft.getInstance().gameRenderer.mainRenderTarget()
                            .getDepthTextureView();
                    if (maskColor == null || depthColor == null
                            || targetDepth == null || mainDepth == null) return -1.0f;
                    var device = RenderSystem.getDevice();
                    device.createCommandEncoder().clearColorTexture(
                            maskColor.texture(), new Vector4f(0));
                    device.createCommandEncoder().clearDepthTexture(targetDepth.texture(), 1.0);

                    var maskEncoder = device.createCommandEncoder();
                    try (var pass = maskEncoder.createRenderPass(
                            () -> "Spatial Cut Selected Mask", maskColor, Optional.empty(),
                            mainDepth, OptionalDouble.empty())) {
                        pass.setPipeline(RenderSystem.getCompiledPipeline(CutPipelines.SPATIAL_CUT_MASK));
                        pass.setUniform("Projection", projectionFor(camera));
                        pass.setUniform("DynamicTransforms",
                                RenderSystem.getDynamicUniforms().writeTransform(
                                        camera.viewRotationMatrix()));
                        pass.setVertexBuffer(0, vertexBuffer.slice(0, totalBytes));
                        var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES);
                        pass.setIndexBuffer(sequential.getBuffer(vertexCount), sequential.type());
                        pass.drawIndexed(vertexCount, 1, 0, 0, 0);
                    }

                    var encoder = device.createCommandEncoder();
                    try (var pass = encoder.createRenderPass(
                            () -> "Spatial Cut Selected Depth", depthColor, Optional.empty(),
                            targetDepth, OptionalDouble.empty())) {
                        pass.setPipeline(RenderSystem.getCompiledPipeline(CutPipelines.SPATIAL_CUT_DEPTH));
                        pass.setUniform("Projection", projectionFor(camera));
                        pass.setUniform("DynamicTransforms",
                                RenderSystem.getDynamicUniforms().writeTransform(
                                        camera.viewRotationMatrix()));
                        pass.setUniform("Sampler0", mainDepth,
                                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                        pass.setVertexBuffer(0, vertexBuffer.slice(0, totalBytes));
                        var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES);
                        pass.setIndexBuffer(sequential.getBuffer(vertexCount), sequential.type());
                        pass.drawIndexed(vertexCount, 1, 0, 0, 0);
                    }
                    return 0.0f;
                }

                private void grow(int requiredVertices) {
                    if (vertexBuffer == null) return;
                    var old = vertexBuffer;
                    capacityVertices = Math.max(requiredVertices, capacityVertices * 2);
                    vertexBuffer = RenderSystem.getDevice().createBuffer(
                            () -> "Spatial Cut Mask Vertices",
                            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                            (long) capacityVertices * VERTEX_STRIDE
                    );
                    old.close();
                }

                public void close() {
                    if (vertexBuffer != null) vertexBuffer.close();
                    if (postUniforms != null) postUniforms.close();
                    vertexBuffer = null;
                    postUniforms = null;
                    for (var slot = 0; slot < MAX_SOURCE_VALIDATION_CUTS; slot++) {
                        selectedVertexData[slot] = null;
                    }
                    capacityVertices = 0;
                }
            }

            private enum LineKind {
                MATERIAL,
                GLOW
            }

            private static final class CutLineRenderer {
                private final LineKind kind;
                private @Nullable GpuBuffer vertexBuffer;
                private @Nullable GpuBuffer flowUniforms;
                private @Nullable ByteBuffer vertexData;
                private int capacityVertices;

                private CutLineRenderer(LineKind kind) {
                    this.kind = kind;
                }

                private void init(GpuDevice device) {
                    vertexBuffer = device.createBuffer(
                            () -> "Spatial Cut " + kind + " Vertices",
                            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                            (long) INITIAL_VERTICES * LINE_VERTEX_STRIDE
                    );
                    flowUniforms = device.createBuffer(
                            () -> "Spatial Cut " + kind + " Flow Uniforms",
                            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                            FLOW_UNIFORM_SIZE
                    );
                    capacityVertices = INITIAL_VERTICES;
                }

                private void renderGlowInput(
                        GpuTextureView color,
                        GpuTextureView depth,
                        CutCamera camera,
                        float gameTime,
                        ByteBuffer vertices,
                        int vertexCount
                ) {
                    if (vertexBuffer == null || vertexCount <= 0) return;
                    if (vertexCount > capacityVertices) grow(vertexCount);
                    var totalBytes = (long) vertexCount * LINE_VERTEX_STRIDE;
                    if (vertexData == null || vertexData.capacity() < totalBytes) {
                        vertexData = BufferUtils.createByteBuffer(Math.toIntExact(totalBytes));
                    }
                    vertexData.clear();
                    var copy = vertices.duplicate();
                    var requiredLimit = copy.position() + Math.toIntExact(totalBytes);
                    if (requiredLimit > copy.limit()) return;
                    copy.limit(requiredLimit);
                    vertexData.put(copy);
                    vertexData.flip();
                    draw(
                            RenderSystem.getDevice(), color, depth, projectionFor(camera),
                            camera.viewRotationMatrix(), gameTime, vertexData, vertexCount);
                }

                private void renderAfterPost(
                        CutCamera camera,
                        float gameTime,
                        ByteBuffer vertices,
                        int vertexCount
                ) {
                    if (vertexBuffer == null || vertexCount <= 0) return;
                    var minecraft = Minecraft.getInstance();
                    var color = minecraft.gameRenderer.mainRenderTarget().getColorTextureView();
                    var depth = minecraft.gameRenderer.mainRenderTarget().getDepthTextureView();
                    if (color == null || depth == null) return;
                    if (vertexCount > capacityVertices) grow(vertexCount);
                    var totalBytes = (long) vertexCount * LINE_VERTEX_STRIDE;
                    if (vertexData == null || vertexData.capacity() < totalBytes) {
                        vertexData = BufferUtils.createByteBuffer(Math.toIntExact(totalBytes));
                    }
                    vertexData.clear();
                    var copy = vertices.duplicate();
                    var requiredLimit = copy.position() + Math.toIntExact(totalBytes);
                    if (requiredLimit > copy.limit()) return;
                    copy.limit(requiredLimit);
                    vertexData.put(copy);
                    vertexData.flip();
                    draw(
                            RenderSystem.getDevice(), color, depth, projectionFor(camera),
                            camera.viewRotationMatrix(), gameTime, vertexData, vertexCount);
                }

                private void draw(
                        GpuDevice device,
                        GpuTextureView color,
                        GpuTextureView depth,
                        GpuBufferSlice projection,
                        Matrix4f viewRotation,
                        float gameTime,
                        ByteBuffer vertices,
                        int totalVertices
                ) {
                    if (vertexBuffer == null || totalVertices <= 0) return;
                    var totalBytes = (long) totalVertices * LINE_VERTEX_STRIDE;
                    device.createCommandEncoder().writeToBuffer(
                            vertexBuffer.slice(0, totalBytes), vertices);
                    if (flowUniforms == null) return;
                    try (var stack = MemoryStack.stackPush()) {
                        var builder = Std140Builder.onStack(stack, FLOW_UNIFORM_SIZE);
                        var time = Float.isFinite(gameTime) ? gameTime : 0.0f;
                        builder.putVec4(new Vector4f(time, 0.0f, 0.16f, 0.0f));
                        device.createCommandEncoder().writeToBuffer(flowUniforms.slice(), builder.get());
                    }
                    var textureManager = Minecraft.getInstance().getTextureManager();
                    var endSky = textureManager.getTexture(END_SKY_TEXTURE);
                    var endPortal = textureManager.getTexture(END_PORTAL_TEXTURE);

                    var encoder = device.createCommandEncoder();
                    try (var pass = encoder.createRenderPass(
                            () -> "Spatial Cut " + kind,
                            color, Optional.empty(), depth, OptionalDouble.empty()
                    )) {
                        pass.setPipeline(RenderSystem.getCompiledPipeline(switch (kind) {
                            case MATERIAL -> CutPipelines.SPATIAL_CUT_MATERIAL;
                            case GLOW -> CutPipelines.SPATIAL_CUT_GLOW;
                        }));
                        pass.setUniform("Projection", projection);
                        pass.setUniform("DynamicTransforms",
                                RenderSystem.getDynamicUniforms().writeTransform(viewRotation));
                        pass.setUniform("SpatialCutFlow", flowUniforms.slice());
                        pass.setUniform("Sampler0", endSky.getTextureView(), endSky.getSampler());
                        pass.setUniform("Sampler1", endPortal.getTextureView(), endPortal.getSampler());
                        pass.setVertexBuffer(0, vertexBuffer.slice(0, totalBytes));
                        var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES);
                        pass.setIndexBuffer(sequential.getBuffer(totalVertices), sequential.type());
                        pass.drawIndexed(totalVertices, 1, 0, 0, 0);
                    }
                }

                private void grow(int requiredVertices) {
                    if (vertexBuffer == null) return;
                    var old = vertexBuffer;
                    capacityVertices = Math.max(requiredVertices, capacityVertices * 2);
                    vertexBuffer = RenderSystem.getDevice().createBuffer(
                            () -> "Spatial Cut " + kind + " Vertices",
                            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                            (long) capacityVertices * LINE_VERTEX_STRIDE
                    );
                    old.close();
                }

                private void close() {
                    if (vertexBuffer != null) vertexBuffer.close();
                    if (flowUniforms != null) flowUniforms.close();
                    vertexBuffer = null;
                    flowUniforms = null;
                    vertexData = null;
                    capacityVertices = 0;
                }
            }
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
        public static void handle(ActivatePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (ACTIVE.containsKey(player)) return;

            var skill = Skills.SPACIAL_EXCISION.get();
            skill.executeActive(player, (ctx, actualCost) -> {
                var server = player.level().getServer();
                var startTick = server.getTickCount();
                var context = new Context(player, UUID.randomUUID(), startTick,
                        startTick + DURATION_TICKS);
                ACTIVE.put(player, context);
                AbilitySystemServer.registerContext(context);
            });
        }

        private static void tick(MinecraftServer server) {
            var now = server.getTickCount();
            for (var context : List.copyOf(ACTIVE.values())) {
                context.checkLifetime(server, now);
                if (context.server == server && !context.ended) {
                    context.tickCombat(now);
                }
            }
        }

        private static void stop(MinecraftServer server) {
            for (var context : List.copyOf(ACTIVE.values())) {
                if (context.server == server) context.end();
            }
        }
    }

    @EventBusSubscriber
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onTick(ServerTickEvent.Pre event) {
            Server.tick(event.getServer());
        }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent event) {
            Server.stop(event.getServer());
        }
    }

    public static final class Context extends ServerContext {
        private final MinecraftServer server;
        private final UUID sessionId;
        private final long startTick;
        private final long endTick;
        private final ArrayList<RecordedSegment> segments = new ArrayList<>();
        private final Map<Skill.ActiveExecutionContext, List<TeleportCompletedEvent>> pending =
                new IdentityHashMap<>();
        private final Deque<Skill.ActiveExecutionContext> teleportExecutions = new ArrayDeque<>();
        private final Set<ServerPlayer> observers =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private final ArrayList<LivingEntity> queryCandidates = new ArrayList<>();
        private boolean ended;

        private Context(ServerPlayer player, UUID sessionId, long startTick, long endTick) {
            super(player);
            server = player.level().getServer();
            this.sessionId = sessionId;
            this.startTick = startTick;
            this.endTick = endTick;
            observers.add(player);
        }

        @SubscribeEvent
        public void onTeleportSkillStart(SkillExecutionStartEvent event) {
            if (ended || !isOwnerTeleportExecution(event.context())) return;
            teleportExecutions.push(event.context());
            pending.put(event.context(), new ArrayList<>());
        }

        @SubscribeEvent
        public void onTeleportCompleted(TeleportCompletedEvent event) {
            if (ended || teleportExecutions.isEmpty() || event.entity() != player) return;
            var recorded = pending.get(teleportExecutions.peek());
            if (recorded != null) recorded.add(event);
        }

        @SubscribeEvent
        public void onTeleportSkillFinish(SkillExecutionFinishEvent event) {
            if (!isOwnerTeleportExecution(event.context())) return;
            var recorded = pending.remove(event.context());
            removeTeleportExecutionByIdentity(event.context());
            if (!event.successful() || recorded == null) return;
            for (var teleport : recorded) recordSuccessfulTeleport(teleport);
        }

        private void removeTeleportExecutionByIdentity(Skill.ActiveExecutionContext target) {
            var iterator = teleportExecutions.iterator();
            while (iterator.hasNext()) {
                if (iterator.next() == target) {
                    iterator.remove();
                    return;
                }
            }
        }

        private void checkLifetime(MinecraftServer tickingServer, long now) {
            if (server != tickingServer) return;
            if (ended || player.hasDisconnected() || !player.isAlive() || now >= endTick) {
                end();
            }
        }

        private void tickCombat(long now) {
            if (ended) return;
            for (var segment : List.copyOf(segments)) {
                if (ended) break;
                querySegment(now, segment);
            }
        }

        private void querySegment(long now, RecordedSegment segment) {
            var dimension = ResourceKey.create(Registries.DIMENSION, segment.dimension());
            var level = server.getLevel(dimension);
            if (level == null) return;

            var searchBox = new AABB(segment.start(), segment.end())
                    .inflate(Field.ATTRACTION_HALF_EXTENT);
            queryCandidates.clear();
            level.getEntities().get(
                    EntityTypeTest.forClass(LivingEntity.class), searchBox, target -> {
                        if (EntitySelector.NO_SPECTATORS.test(target)) queryCandidates.add(target);
                        return Continuation.CONTINUE;
                    });
            var age = now - segment.createdTick();
            for (var target : queryCandidates) {
                if (!isEligibleCombatTarget(target)) continue;
                var bounds = target.getBoundingBox();
                var lastHitAge = segment.persistentLastHitAges()
                        .getOrDefault(target.getUUID(), -1L);
                var actions = Field.tickActions(
                        age, !segment.spawnStrikeClaims().contains(target.getUUID()),
                        Field.isPersistentDamageDue(age, lastHitAge),
                        bounds, segment.start(), segment.end());
                if (actions.spawnStrike()
                        && segment.spawnStrikeClaims().add(target.getUUID())) {
                    SkillDamageUtil.apply(
                            player, target, Skills.SPACIAL_EXCISION.get(), DamageTypes.SPACE_DAMAGE,
                            (float) Field.SPAWN_STRIKE_DAMAGE);
                }
                if (actions.persistentDamage() && target.isAlive() && !target.isRemoved()) {
                    var damaged = SkillDamageUtil.apply(
                            player, target, Skills.SPACIAL_EXCISION.get(), DamageTypes.SPACE_DAMAGE,
                            (float) Field.PERSISTENT_DAMAGE);
                    if (damaged) {
                        segment.persistentLastHitAges().put(target.getUUID(), age);
                    }
                }
                if (isAttractionTarget(target)) {
                    applyPull(target, bounds, segment);
                }
            }
        }

        private void applyPull(
                LivingEntity target, AABB bounds, RecordedSegment segment
        ) {
            var distance = Field.attractionDistance(
                    bounds, segment.start(), segment.end());
            if (!Double.isFinite(distance)) return;
            target.setDeltaMovement(Field.pulledVelocity(
                    target.getDeltaMovement(), bounds,
                    segment.start(), segment.end(), distance));
            target.syncVelocity = true;
            target.resetFallDistance();
            if (target instanceof ServerPlayer targetPlayer) {
                targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
            }
        }

        private boolean isEligibleCombatTarget(LivingEntity target) {
            return target != player
                    && target.isAlive()
                    && !target.isRemoved()
                    && !(target instanceof Player immune && DamageTypes.isImmunePlayer(immune))
                    && !PvpSetting.shouldPrevent(player, target);
        }

        private boolean isAttractionTarget(LivingEntity target) {
            return isEligibleCombatTarget(target) && !TeamRelations.areAllied(player, target);
        }

        private boolean isOwnerTeleportExecution(Skill.ActiveExecutionContext context) {
            if (context == null || context.player() != player || context.skill() == null) return false;
            var skill = context.skill();
            return skill != Skills.SPACIAL_EXCISION.get()
                    && skill.getCategory() == AbilityCategories.TELEPORT.get();
        }

        private void recordSuccessfulTeleport(TeleportCompletedEvent event) {
            if (ended || event.sourceLevel() == null || event.destinationLevel() == null
                    || !event.sourceLevel().dimension().equals(event.destinationLevel().dimension())
                    || !SpacialExcisionMath.isFinite(event.origin())
                    || !SpacialExcisionMath.isFinite(event.destination())
                    || SpacialExcisionMath.planeBasis(
                    event.origin(), event.destination(), event.preTeleportYaw()).isEmpty()) {
                return;
            }
            var now = server.getTickCount();
            if (now >= endTick) return;

            var segment = new RecordedSegment(
                    event.origin(), event.destination(), event.preTeleportYaw(),
                    event.sourceLevel().dimension().identifier(),
                    event.sourceLevel().getRandom().nextLong(), now, new HashSet<>(),
                    new HashMap<>()
            );
            segments.add(segment);
            var packet = new SegmentPacket(
                    sessionId, startTick, segment.createdTick(), endTick,
                    segment.dimension(), segment.start(), segment.end(),
                    segment.preTeleportYaw(), segment.seed()
            );
            broadcast(segment, event, packet);
        }

        private void broadcast(RecordedSegment segment, TeleportCompletedEvent event, SegmentPacket packet) {
            ServerLevel sourceLevel = event.sourceLevel();
            var delta = segment.end().subtract(segment.start());
            var lengthSquared = delta.lengthSqr();
            if (!Double.isFinite(lengthSquared) || lengthSquared <= 0.0) return;
            var rangeSquared = OBSERVER_RANGE * OBSERVER_RANGE;
            for (var observer : sourceLevel.players()) {
                var along = observer.position().subtract(segment.start()).dot(delta) / lengthSquared;
                along = Math.clamp(along, 0.0, 1.0);
                var closest = segment.start().add(delta.scale(along));
                if (observer.position().distanceToSqr(closest) > rangeSquared) continue;
                MisakaNetworkServer.send(observer, packet);
                observers.add(observer);
            }
        }

        private void end() {
            if (ended) return;
            ended = true;
            Server.ACTIVE.remove(player, this);
            unregister();
        }

        @Override
        protected void onUnregistered() {
            Server.ACTIVE.remove(player, this);
            pending.clear();
            teleportExecutions.clear();
            segments.clear();
            queryCandidates.clear();
            var packet = new EndPacket(sessionId);
            for (var observer : List.copyOf(observers)) {
                if (!observer.hasDisconnected()) MisakaNetworkServer.send(observer, packet);
            }
            observers.clear();
        }

        private record RecordedSegment(
                Vec3 start,
                Vec3 end,
                float preTeleportYaw,
                Identifier dimension,
                long seed,
                long createdTick,
                Set<UUID> spawnStrikeClaims,
                Map<UUID, Long> persistentLastHitAges
        ) {
        }
    }

    static final class Field {
        static final double DAMAGE_HALF_EXTENT = 1.5;
        static final double ATTRACTION_HALF_EXTENT = 3.5;
        static final int PULSE_INTERVAL_TICKS = 10;
        static final int SPAWN_STRIKE_WINDOW_TICKS = 20;
        static final double PERSISTENT_DAMAGE = 12.0;
        static final double SPAWN_STRIKE_DAMAGE = 40.0;
        static final double MAX_PULL_SPEED = 1.10;

        private static final double OUTER_TARGET_SPEED = 0.35;
        private static final double OUTER_RESPONSE = 0.20;
        private static final double INNER_RESPONSE = 0.78;
        private static final double MIN_DISTANCE_SQUARED = 1.0e-8;

        private Field() {
        }

        static boolean intersectsExpandedAabb(
                Vec3 start, Vec3 end, AABB bounds, double halfExtent
        ) {
            if (!isFinite(start) || !isFinite(end) || !isFinite(bounds)
                    || !Double.isFinite(halfExtent) || halfExtent < 0.0) {
                return false;
            }
            var minX = bounds.minX - halfExtent;
            var minY = bounds.minY - halfExtent;
            var minZ = bounds.minZ - halfExtent;
            var maxX = bounds.maxX + halfExtent;
            var maxY = bounds.maxY + halfExtent;
            var maxZ = bounds.maxZ + halfExtent;
            if (!Double.isFinite(minX) || !Double.isFinite(minY)
                    || !Double.isFinite(minZ) || !Double.isFinite(maxX)
                    || !Double.isFinite(maxY) || !Double.isFinite(maxZ)) return false;

            var directionX = end.x - start.x;
            var directionY = end.y - start.y;
            var directionZ = end.z - start.z;
            if (!Double.isFinite(directionX) || !Double.isFinite(directionY)
                    || !Double.isFinite(directionZ)) return false;

            var enter = 0.0;
            var exit = 1.0;
            if (Math.abs(directionX) <= MIN_DISTANCE_SQUARED) {
                if (start.x < minX || start.x > maxX) return false;
            } else {
                var first = (minX - start.x) / directionX;
                var second = (maxX - start.x) / directionX;
                if (!Double.isFinite(first) || !Double.isFinite(second)) return false;
                enter = Math.max(enter, Math.min(first, second));
                exit = Math.min(exit, Math.max(first, second));
                if (enter > exit) return false;
            }
            if (Math.abs(directionY) <= MIN_DISTANCE_SQUARED) {
                if (start.y < minY || start.y > maxY) return false;
            } else {
                var first = (minY - start.y) / directionY;
                var second = (maxY - start.y) / directionY;
                if (!Double.isFinite(first) || !Double.isFinite(second)) return false;
                enter = Math.max(enter, Math.min(first, second));
                exit = Math.min(exit, Math.max(first, second));
                if (enter > exit) return false;
            }
            if (Math.abs(directionZ) <= MIN_DISTANCE_SQUARED) {
                return start.z >= minZ && start.z <= maxZ;
            }
            var first = (minZ - start.z) / directionZ;
            var second = (maxZ - start.z) / directionZ;
            if (!Double.isFinite(first) || !Double.isFinite(second)) return false;
            enter = Math.max(enter, Math.min(first, second));
            exit = Math.min(exit, Math.max(first, second));
            return enter <= exit;
        }

        static boolean isSpawnStrikeAge(long age) {
            return age >= 0 && age < SPAWN_STRIKE_WINDOW_TICKS;
        }

        static boolean isPersistentDamageDue(long age, long lastHitAge) {
            if (age < 0) return false;
            if (lastHitAge < 0) return true;
            return age >= lastHitAge && age - lastHitAge >= PULSE_INTERVAL_TICKS;
        }

        static TickActions tickActions(
                long age,
                boolean spawnUnclaimed,
                boolean persistentDamageDue,
                AABB bounds,
                Vec3 start,
                Vec3 end
        ) {
            var intersectsDamageCorridor = intersectsExpandedAabb(
                    start, end, bounds, DAMAGE_HALF_EXTENT);
            var spawnStrike = spawnUnclaimed
                    && isSpawnStrikeAge(age)
                    && intersectsDamageCorridor;
            var persistentDamage = persistentDamageDue && intersectsDamageCorridor;
            return new TickActions(spawnStrike, persistentDamage);
        }

        static Vec3 pulledVelocity(Vec3 velocity, AABB bounds, Vec3 start, Vec3 end) {
            return pulledVelocity(velocity, bounds, start, end, Double.NaN);
        }

        static Vec3 pulledVelocity(
                Vec3 velocity, AABB bounds, Vec3 start, Vec3 end, double knownDistance
        ) {
            var boundedVelocity = clampVelocity(isFinite(velocity) ? velocity : Vec3.ZERO);
            if (!isFinite(bounds) || !isFinite(start) || !isFinite(end)) {
                return boundedVelocity;
            }

            var targetX = (bounds.minX + bounds.maxX) * 0.5;
            var targetY = (bounds.minY + bounds.maxY) * 0.5;
            var targetZ = (bounds.minZ + bounds.maxZ) * 0.5;
            if (!Double.isFinite(targetX) || !Double.isFinite(targetY)
                    || !Double.isFinite(targetZ)) return boundedVelocity;
            var directionX = end.x - start.x;
            var directionY = end.y - start.y;
            var directionZ = end.z - start.z;
            var lengthSquared = Math.fma(directionX, directionX,
                    Math.fma(directionY, directionY, directionZ * directionZ));
            if (!Double.isFinite(lengthSquared) || lengthSquared <= MIN_DISTANCE_SQUARED) {
                return Vec3.ZERO;
            }
            var projection = Math.fma(targetX - start.x, directionX,
                    Math.fma(targetY - start.y, directionY, (targetZ - start.z) * directionZ))
                    / lengthSquared;
            if (!Double.isFinite(projection)) return boundedVelocity;
            projection = Math.clamp(projection, 0.0, 1.0);
            var closestX = Math.fma(directionX, projection, start.x);
            var closestY = Math.fma(directionY, projection, start.y);
            var closestZ = Math.fma(directionZ, projection, start.z);
            var towardX = closestX - targetX;
            var towardY = closestY - targetY;
            var towardZ = closestZ - targetZ;
            var directionLength = Math.sqrt(Math.fma(towardX, towardX,
                    Math.fma(towardY, towardY, towardZ * towardZ)));
            if (!Double.isFinite(directionLength) || directionLength <= MIN_DISTANCE_SQUARED) {
                return Vec3.ZERO;
            }
            var distance = Double.isFinite(knownDistance)
                    ? knownDistance
                    : Math.max(Math.abs(targetX - closestX),
                    Math.max(Math.abs(targetY - closestY), Math.abs(targetZ - closestZ)));
            if (!Double.isFinite(distance) || distance < 0.0
                    || distance > ATTRACTION_HALF_EXTENT) {
                return boundedVelocity;
            }
            var direction = new Vec3(
                    towardX / directionLength, towardY / directionLength, towardZ / directionLength);
            var proximity = 1.0 - Math.clamp(distance / ATTRACTION_HALF_EXTENT, 0.0, 1.0);
            var targetSpeed = lerp(OUTER_TARGET_SPEED, MAX_PULL_SPEED,
                    smootherstep(0.0, 1.0, proximity));
            var capture = 1.0 - smootherstep(0.75, 1.75, distance);
            var response = lerp(OUTER_RESPONSE, INNER_RESPONSE, capture);
            var targetVelocity = direction.scale(targetSpeed);
            return clampVelocity(boundedVelocity.lerp(targetVelocity, response));
        }

        static double attractionDistance(AABB bounds, Vec3 start, Vec3 end) {
            if (!isFinite(bounds) || !isFinite(start) || !isFinite(end)) {
                return Double.POSITIVE_INFINITY;
            }
            var centerX = (bounds.minX + bounds.maxX) * 0.5;
            var centerY = (bounds.minY + bounds.maxY) * 0.5;
            var centerZ = (bounds.minZ + bounds.maxZ) * 0.5;
            var directionX = end.x - start.x;
            var directionY = end.y - start.y;
            var directionZ = end.z - start.z;
            var lengthSquared = Math.fma(directionX, directionX,
                    Math.fma(directionY, directionY, directionZ * directionZ));
            if (!Double.isFinite(centerX) || !Double.isFinite(centerY)
                    || !Double.isFinite(centerZ) || !Double.isFinite(lengthSquared)
                    || lengthSquared <= MIN_DISTANCE_SQUARED) return Double.POSITIVE_INFINITY;
            var projection = Math.fma(centerX - start.x, directionX,
                    Math.fma(centerY - start.y, directionY,
                            (centerZ - start.z) * directionZ)) / lengthSquared;
            if (!Double.isFinite(projection)) return Double.POSITIVE_INFINITY;
            projection = Math.clamp(projection, 0.0, 1.0);
            var closestX = Math.fma(directionX, projection, start.x);
            var closestY = Math.fma(directionY, projection, start.y);
            var closestZ = Math.fma(directionZ, projection, start.z);
            var distance = Math.max(Math.abs(centerX - closestX),
                    Math.max(Math.abs(centerY - closestY), Math.abs(centerZ - closestZ)));
            return Double.isFinite(distance) && distance <= ATTRACTION_HALF_EXTENT
                    ? distance
                    : Double.POSITIVE_INFINITY;
        }

        private static double smootherstep(double edge0, double edge1, double value) {
            if (!(edge1 > edge0) || !Double.isFinite(value)) return 0.0;
            var t = Math.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
            return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
        }

        private static double lerp(double from, double to, double amount) {
            return from + (to - from) * amount;
        }

        static boolean isFinite(Vec3 value) {
            return value != null && Double.isFinite(value.x)
                    && Double.isFinite(value.y) && Double.isFinite(value.z);
        }

        private static boolean isFinite(AABB bounds) {
            return bounds != null && Double.isFinite(bounds.minX)
                    && Double.isFinite(bounds.minY) && Double.isFinite(bounds.minZ)
                    && Double.isFinite(bounds.maxX) && Double.isFinite(bounds.maxY)
                    && Double.isFinite(bounds.maxZ);
        }

        private static Vec3 clampVelocity(Vec3 velocity) {
            var largestComponent = Math.max(Math.abs(velocity.x),
                    Math.max(Math.abs(velocity.y), Math.abs(velocity.z)));
            if (largestComponent == 0.0) return velocity;

            var normalized = velocity.scale(1.0 / largestComponent);
            var normalizedLength = normalized.length();
            if (!Double.isFinite(normalizedLength) || normalizedLength <= 0.0) return Vec3.ZERO;
            if (largestComponent <= MAX_PULL_SPEED / normalizedLength) return velocity;
            return normalized.scale(MAX_PULL_SPEED / normalizedLength);
        }

        public static record TickActions(boolean spawnStrike, boolean persistentDamage) {
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActivatePacket extends Packet<ServerGamePacketListenerImpl, ActivatePacket> {
        public static final ActivatePacket INSTANCE = new ActivatePacket();
        public static final StreamCodec<ByteBuf, ActivatePacket> CODEC = StreamCodec.unit(INSTANCE);

        private ActivatePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ActivatePacket> getPacketType() {
            return PacketTypes.SPACIAL_EXCISION_ACTIVATE.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class SegmentPacket extends Packet<ClientPacketListener, SegmentPacket> {
        public static final StreamCodec<ByteBuf, SegmentPacket> CODEC = StreamCodec.of(
                (buffer, packet) -> {
                    writeUuid(buffer, packet.sessionId);
                    ByteBufCodecs.LONG.encode(buffer, packet.startTick);
                    ByteBufCodecs.LONG.encode(buffer, packet.createdTick);
                    ByteBufCodecs.LONG.encode(buffer, packet.endTick);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, packet.dimension.toString());
                    Vec3.STREAM_CODEC.encode(buffer, packet.start);
                    Vec3.STREAM_CODEC.encode(buffer, packet.end);
                    ByteBufCodecs.FLOAT.encode(buffer, packet.preTeleportYaw);
                    ByteBufCodecs.LONG.encode(buffer, packet.seed);
                },
                buffer -> new SegmentPacket(
                        readUuid(buffer),
                        ByteBufCodecs.LONG.decode(buffer),
                        ByteBufCodecs.LONG.decode(buffer),
                        ByteBufCodecs.LONG.decode(buffer),
                        parseIdentifier(ByteBufCodecs.STRING_UTF8.decode(buffer)),
                        Vec3.STREAM_CODEC.decode(buffer),
                        Vec3.STREAM_CODEC.decode(buffer),
                        ByteBufCodecs.FLOAT.decode(buffer),
                        ByteBufCodecs.LONG.decode(buffer)
                )
        );

        private final UUID sessionId;
        private final long startTick;
        private final long createdTick;
        private final long endTick;
        private final Identifier dimension;
        private final Vec3 start;
        private final Vec3 end;
        private final float preTeleportYaw;
        private final long seed;

        public SegmentPacket(UUID sessionId, long startTick, long createdTick, long endTick, Identifier dimension,
                             Vec3 start, Vec3 end, float preTeleportYaw, long seed) {
            this.sessionId = sessionId;
            this.startTick = startTick;
            this.createdTick = createdTick;
            this.endTick = endTick;
            this.dimension = dimension;
            this.start = start;
            this.end = end;
            this.preTeleportYaw = preTeleportYaw;
            this.seed = seed;
        }

        public UUID sessionId() {
            return sessionId;
        }

        public long startTick() {
            return startTick;
        }

        public long createdTick() {
            return createdTick;
        }

        public long endTick() {
            return endTick;
        }

        public Identifier dimension() {
            return dimension;
        }

        public Vec3 start() {
            return start;
        }

        public Vec3 end() {
            return end;
        }

        public float preTeleportYaw() {
            return preTeleportYaw;
        }

        public long seed() {
            return seed;
        }

        @Override
        public PacketType<ClientPacketListener, SegmentPacket> getPacketType() {
            return PacketTypes.SPACIAL_EXCISION_SEGMENT.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class EndPacket extends Packet<ClientPacketListener, EndPacket> {
        public static final StreamCodec<ByteBuf, EndPacket> CODEC = StreamCodec.of(
                (buffer, packet) -> writeUuid(buffer, packet.sessionId),
                buffer -> new EndPacket(readUuid(buffer))
        );

        private final UUID sessionId;

        public EndPacket(UUID sessionId) {
            this.sessionId = sessionId;
        }

        public UUID sessionId() {
            return sessionId;
        }

        @Override
        public PacketType<ClientPacketListener, EndPacket> getPacketType() {
            return PacketTypes.SPACIAL_EXCISION_END.get();
        }
    }

    private static void writeUuid(ByteBuf buffer, UUID uuid) {
        buffer.writeLong(uuid.getMostSignificantBits());
        buffer.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuf buffer) {
        return new UUID(buffer.readLong(), buffer.readLong());
    }

    private static Identifier parseIdentifier(String value) {
        return Identifier.tryParse(value);
    }
}
