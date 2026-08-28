package org.academy.internal.client.ability.aeromanip;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.Render;
import org.academy.api.client.renderer.LineBoxRenderer;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Maintains owner-only persistent highlights for installed High-Speed Jet nozzles. */
public final class HighSpeedJetHighlightClient {
    public static final int WHITE_OUTLINE = 0xFFFFFFFF;
    private static volatile List<BlockFaceHighlight> blockHighlights = List.of();
    private static volatile Set<Integer> entityHighlights = Set.of();
    private static volatile int previewEntityId = -1;
    private static boolean initialized;

    private HighSpeedJetHighlightClient() {
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.register(HighSpeedJetHighlightClient.class);
    }

    public static void setPreviewEntity(@Nullable Entity entity) {
        previewEntityId = entity == null ? -1 : entity.getId();
    }

    public static void clearPreview() {
        previewEntityId = -1;
    }

    public static boolean shouldHighlightEntity(Entity entity) {
        var player = Minecraft.getInstance().player;
        return entity != null && player != null && entity.level() == player.level()
                && containsEntityId(entity.getId(), previewEntityId, entityHighlights);
    }

    public static boolean hasEntityHighlights() {
        return previewEntityId >= 0 || !entityHighlights.isEmpty();
    }

    static boolean containsEntityId(int entityId, int previewId, Set<Integer> persistentIds) {
        return entityId >= 0 && (entityId == previewId || persistentIds.contains(entityId));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null) {
            blockHighlights = List.of();
            entityHighlights = Set.of();
            previewEntityId = -1;
            return;
        }

        var blocks = new LinkedHashSet<BlockFaceHighlight>();
        var entities = new LinkedHashSet<Integer>();
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof HighSpeedJetNozzle nozzle) || !nozzle.isOwnedBy(player)) continue;
            if (nozzle.isEntityMounted()) {
                if (nozzle.supportEntityId() >= 0
                        && level.getEntity(nozzle.supportEntityId()) != null) {
                    entities.add(nozzle.supportEntityId());
                }
            } else {
                blocks.add(new BlockFaceHighlight(nozzle.supportPos(), nozzle.face()));
            }
        }
        blockHighlights = List.copyOf(blocks);
        entityHighlights = Set.copyOf(entities);
    }

    @SubscribeEvent
    public static void onLevelRender(LevelRenderEvent event) {
        var highlights = blockHighlights;
        if (highlights.isEmpty()) return;
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) return;
        var camera = minecraft.gameRenderer.mainCamera().position();
        var matrices = event.getMatrixStack();
        matrices.pushPose();
        matrices.translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
        event.submitCustomGeometry(Render.RenderTypes.MINE_DETECT_LINES, (snapshot, consumer) -> {
            for (var highlight : highlights) {
                var state = level.getBlockState(highlight.pos());
                var shape = state.getShape(level, highlight.pos());
                var box = shape.isEmpty()
                        ? new AABB(highlight.pos())
                        : shape.bounds().move(highlight.pos());
                LineBoxRenderer.renderFace(
                        snapshot, consumer, box, highlight.face(), 1.0f, 1.0f, 1.0f, 1.0f);
            }
        });
        matrices.popPose();
    }

    private record BlockFaceHighlight(BlockPos pos, Direction face) {
    }
}
