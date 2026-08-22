package org.academy.internal.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.projectile.TridentModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import org.joml.Vector3fc;

import java.util.function.Consumer;

import static org.academy.AcademyCraft.academy;

/** Vanilla trident geometry rendered with Academy's animated dark-matter material. */
public final class DarkmatterTridentSpecialRenderer implements NoDataSpecialModelRenderer {
    /**
     * Entity-model textures are resolved as complete texture paths, unlike item
     * model texture slots.  Omitting the {@code textures/} prefix and extension
     * makes the vanilla trident special renderer fall back to the missing texture.
     */
    public static final Identifier TEXTURE =
            academy("textures/entity/darkmatter_trident.png");

    private final TridentModel model;

    private DarkmatterTridentSpecialRenderer(TridentModel model) {
        this.model = model;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
                       int overlayCoords, boolean hasFoil, int outlineColor) {
        collector.order(0).submitModel(model, Unit.INSTANCE, poseStack,
                TEXTURE, lightCoords, overlayCoords,
                outlineColor, null);
        if (hasFoil) {
            collector.order(1).submitModel(model, Unit.INSTANCE, poseStack,
                    RenderTypes.entityGlint(), lightCoords, overlayCoords,
                    outlineColor, null);
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        var poseStack = new PoseStack();
        model.root().getExtentsForGui(poseStack, output);
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Void> {
        public static final Unbaked INSTANCE = new Unbaked();
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
            return new DarkmatterTridentSpecialRenderer(new TridentModel(
                    context.entityModelSet().bakeLayer(ModelLayers.TRIDENT)));
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
