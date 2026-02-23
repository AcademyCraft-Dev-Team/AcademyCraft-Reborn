package org.academy.api.client.renderer.model;

import com.mojang.math.Quadrant;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.ExtraFaceData;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import static net.minecraft.client.renderer.block.model.ItemModelGenerator.MAX_Z;
import static net.minecraft.client.renderer.block.model.ItemModelGenerator.MIN_Z;

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
                var frontInfo = modelBakery.interner().spriteInfo(BakedQuad.SpriteInfo.of(front, front.sprite().transparency()));
                var backInfo = modelBakery.interner().spriteInfo(BakedQuad.SpriteInfo.of(back, back.sprite().transparency()));
                var interner = modelBakery.interner();
                var from = new Vector3f(0.0F, 0.0F, MIN_Z);
                var to = new Vector3f(16.0F, 16.0F, MAX_Z);
                builder.addUnculledFace(
                        FaceBakery.bakeQuad(interner, from, to, ItemModelGenerator.SOUTH_FACE_UVS, Quadrant.R0, 0, frontInfo, Direction.SOUTH, modelState, null, true, 0, ExtraFaceData.DEFAULT)
                );
                builder.addUnculledFace(
                        FaceBakery.bakeQuad(interner, from, to, ItemModelGenerator.NORTH_FACE_UVS, Quadrant.R0, 0, backInfo, Direction.NORTH, modelState, null, true, 0, ExtraFaceData.DEFAULT)
                );
                ItemModelGenerator.bakeSideFaces(builder, interner, modelState, frontInfo, 0);
                return builder.build();
            });
        }
        return singleResult;
    }

    private CoinModelGenerator() {
    }
}