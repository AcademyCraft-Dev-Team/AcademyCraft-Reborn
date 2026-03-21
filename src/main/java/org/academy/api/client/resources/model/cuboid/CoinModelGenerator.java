package org.academy.api.client.resources.model.cuboid;

import com.mojang.math.Quadrant;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.cuboid.ItemModelGenerator;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.ExtraFaceData;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;


public class CoinModelGenerator implements UnbakedModel {
    public static final CoinModelGenerator INSTANCE = new CoinModelGenerator();

    public static final String TEXTURE_KEY_FRONT = "front";
    public static final String TEXTURE_KEY_BACK = "back";

    @Override
    public @Nullable UnbakedGeometry geometry() {
        return CoinModelGenerator::bake;
    }

    private static QuadCollection bake(TextureSlots textureSlots, ModelBaker modelBaker, ModelState modelState, ModelDebugName name) {
        var singleResult = QuadCollection.EMPTY;

        var frontMaterial = textureSlots.getMaterial(TEXTURE_KEY_FRONT);
        var backMaterial = textureSlots.getMaterial(TEXTURE_KEY_BACK);
        if (frontMaterial != null && backMaterial != null) {
            singleResult = modelBaker.compute(modelBakery -> {
                var builder = new QuadCollection.Builder();

                var front = modelBaker.materials().get(frontMaterial, name);
                var back = modelBaker.materials().get(backMaterial, name);
                var frontInfo = modelBakery.interner().materialInfo(BakedQuad.MaterialInfo.of(front, front.sprite().transparency(), 0, true, 0, true));
                var backInfo = modelBakery.interner().materialInfo(BakedQuad.MaterialInfo.of(back, back.sprite().transparency(), 0, true, 0, true));
                var interner = modelBakery.interner();
                var from = new Vector3f(0.0F, 0.0F, ItemModelGenerator.MIN_Z);
                var to = new Vector3f(16.0F, 16.0F, ItemModelGenerator.MAX_Z);
                builder.addUnculledFace(
                        FaceBakery.bakeQuad(interner, from, to, ItemModelGenerator.SOUTH_FACE_UVS, Quadrant.R0, frontInfo, Direction.SOUTH, modelState, null, ExtraFaceData.DEFAULT)
                );
                builder.addUnculledFace(
                        FaceBakery.bakeQuad(interner, from, to, ItemModelGenerator.NORTH_FACE_UVS, Quadrant.R0, backInfo, Direction.NORTH, modelState, null, ExtraFaceData.DEFAULT)
                );
                ItemModelGenerator.bakeSideFaces(builder, interner, modelState, frontInfo);
                return builder.build();
            });
        }
        return singleResult;
    }

    private CoinModelGenerator() {
    }
}