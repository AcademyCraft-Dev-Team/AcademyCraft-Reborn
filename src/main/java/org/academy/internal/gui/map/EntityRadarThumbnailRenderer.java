package org.academy.internal.gui.map;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.teleport.ChunkLeapPackets;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Creates and submits type-cached, head-focused entity textures for the chunk-map radar. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class EntityRadarThumbnailRenderer {
    static final int MIN_DIAMETER = 6;
    private static final int MAX_SCALE = 32;

    private final Map<Integer, LivingEntity> prototypes = new HashMap<>();
    private final Set<Integer> unavailableTypes = new HashSet<>();
    private ClientLevel prototypeLevel;

    @SubscribeEvent
    public static void onRegisterPictureInPictureRenderers(RegisterPictureInPictureRenderersEvent event) {
        event.register(ThumbnailState.class, ThumbnailPictureInPictureRenderer::new);
    }

    /** Renders a front-facing entity thumbnail, or returns false so the caller can draw its category dot. */
    boolean render(GuiGraphicsExtractor graphics, ChunkLeapPackets.Marker marker,
                   int centerX, int centerY, int diameter) {
        if (graphics == null || marker == null || marker.playerId() != null || diameter < MIN_DIAMETER
                || marker.category() == ChunkLeapPackets.CAT_ITEM
                || marker.category() == ChunkLeapPackets.CAT_PROJECTILE) {
            return false;
        }
        var entity = entityFor(marker);
        if (entity == null) return false;

        var half = diameter / 2;
        var left = centerX - half;
        var top = centerY - half;
        graphics.submitPictureInPictureRenderState(new ThumbnailState(
                marker.entityId(),
                marker.typeId(),
                entity,
                focusHeight(entity.getBbHeight(), entity.getEyeHeight()),
                left,
                top,
                left + diameter,
                top + diameter,
                fitScale(entity.getBbWidth(), entity.getBbHeight(), diameter),
                graphics.peekScissorStack()
        ));
        return true;
    }

    private LivingEntity entityFor(ChunkLeapPackets.Marker marker) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) return null;
        if (prototypeLevel != level) {
            prototypes.clear();
            unavailableTypes.clear();
            prototypeLevel = level;
        }
        if (unavailableTypes.contains(marker.typeId())) return null;

        var cached = prototypes.get(marker.typeId());
        if (cached != null) return cached;
        try {
            var type = BuiltInRegistries.ENTITY_TYPE.byId(marker.typeId());
            var created = type == null ? null : type.create(level, EntitySpawnReason.LOAD);
            if (created instanceof LivingEntity living) {
                living.setId(-1);
                prototypes.put(marker.typeId(), living);
                return living;
            }
        } catch (RuntimeException ignored) {
        }
        unavailableTypes.add(marker.typeId());
        return null;
    }

    /** Fits the head region inside the square icon; very broad entities remain centered and intentionally cropped. */
    static int fitScale(double width, double height, int diameter) {
        var extent = Math.max(0.5, Math.min(Math.max(0.0, width), Math.max(0.0, height) * 0.45));
        return Math.clamp((int) Math.floor(Math.max(1, diameter) / extent), 1, MAX_SCALE);
    }

    static float focusHeight(double height, double eyeHeight) {
        if (!Double.isFinite(height) || height <= 0.0) return 0.0f;
        if (!Double.isFinite(eyeHeight) || eyeHeight <= 0.0) return (float) (height * 0.5);
        return (float) Math.clamp(eyeHeight, 0.0, height);
    }

    /**
     * Includes the server entity id so two same-type mobs at the same coordinates remain distinct PIP states.
     * The cached prototype identity is deliberately stable for the lifetime of one client level.
     */
    private record ThumbnailState(int markerId, int typeId, LivingEntity prototype, float focusHeight,
                                  int x0, int y0, int x1, int y1, float scale,
                                  @Nullable ScreenRectangle scissorArea,
                                  @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
        private ThumbnailState(int markerId, int typeId, LivingEntity prototype, float focusHeight,
                               int x0, int y0, int x1, int y1, float scale,
                               @Nullable ScreenRectangle scissorArea) {
            this(markerId, typeId, prototype, focusHeight, x0, y0, x1, y1, scale, scissorArea,
                    PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
        }
    }

    /**
     * NeoForge pools one instance per visible PIP state. Remembering the prototype turns subsequent frames into
     * texture blits, including while the map pans, instead of resubmitting every entity model on every frame.
     */
    private static final class ThumbnailPictureInPictureRenderer
            extends PictureInPictureRenderer<ThumbnailState> {
        private final net.minecraft.client.renderer.entity.EntityRenderDispatcher dispatcher =
                Minecraft.getInstance().getEntityRenderDispatcher();
        private LivingEntity cachedPrototype;

        @Override
        public Class<ThumbnailState> getRenderStateClass() {
            return ThumbnailState.class;
        }

        @Override
        protected void renderToTexture(ThumbnailState state, PoseStack poseStack,
                                       SubmitNodeCollector submitNodeCollector) {
            cachedPrototype = state.prototype();
            try {
                Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
                var renderState = extractRenderState(state.prototype());
                poseStack.translate(0.0f, state.focusHeight(), 0.0f);
                poseStack.mulPose(new Quaternionf().rotateZ((float) Math.PI));
                dispatcher.submit(renderState, new CameraRenderState(), 0.0, 0.0, 0.0,
                        poseStack, submitNodeCollector);
            } catch (RuntimeException ignored) {
                // The category marker underneath remains visible when a third-party renderer cannot be previewed.
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private EntityRenderState extractRenderState(LivingEntity entity) {
            EntityRenderer renderer = dispatcher.getRenderer(entity);
            EntityRenderState renderState = renderer.createRenderState(entity, 1.0f);
            renderState.shadowPieces.clear();
            renderState.outlineColor = 0;
            renderState.lightCoords = LightCoordsUtil.FULL_BRIGHT;
            if (renderState instanceof LivingEntityRenderState livingState) {
                livingState.bodyRot = 180.0f;
                livingState.yRot = 0.0f;
                livingState.xRot = 0.0f;
                livingState.boundingBoxWidth /= livingState.scale;
                livingState.boundingBoxHeight /= livingState.scale;
                livingState.scale = 1.0f;
            }
            return renderState;
        }

        @Override
        protected boolean textureIsReadyToBlit(ThumbnailState state) {
            return cachedPrototype == state.prototype();
        }

        @Override
        public boolean canBeReusedFor(ThumbnailState state, int textureWidth, int textureHeight) {
            return (cachedPrototype == null || cachedPrototype == state.prototype())
                    && super.canBeReusedFor(state, textureWidth, textureHeight);
        }

        @Override
        protected String getTextureLabel() {
            return "academy_chunk_map_entity";
        }
    }
}
