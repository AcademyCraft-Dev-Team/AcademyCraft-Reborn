package org.academy.api.client.renderer.model;

import com.mojang.math.Quadrant;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;

public class CoinModelGenerator implements UnbakedModel {
    public static final CoinModelGenerator INSTANCE = new CoinModelGenerator();

    public static final String TEXTURE_KEY_FRONT = "front";
    public static final String FRONT_TEXTURE_REF = "#" + TEXTURE_KEY_FRONT;
    public static final String TEXTURE_KEY_BACK = "back";
    public static final String BACK_TEXTURE_REF = "#" + TEXTURE_KEY_BACK;

    @Override
    public @Nullable UnbakedGeometry geometry() {
        return CoinModelGenerator::bake;
    }

    private static QuadCollection bake(TextureSlots textureSlots, ModelBaker baker, ModelState modelState, ModelDebugName debugName) {
        var outputElements = new ArrayList<BlockElement>();

        // Default Material
        var material = textureSlots.getMaterial(TEXTURE_KEY_FRONT);

        if (material != null) {
            var coreFaces = new HashMap<Direction, BlockElementFace>();
            coreFaces.put(Direction.SOUTH, new BlockElementFace(null, 0, FRONT_TEXTURE_REF, ItemModelGenerator.SOUTH_FACE_UVS, Quadrant.R0));
            coreFaces.put(Direction.NORTH, new BlockElementFace(null, 0, BACK_TEXTURE_REF, ItemModelGenerator.NORTH_FACE_UVS, Quadrant.R0));

            var coreElement = new BlockElement(
                    new Vector3f(0.0F, 0.0F, ItemModelGenerator.MIN_Z),
                    new Vector3f(16.0F, 16.0F, ItemModelGenerator.MAX_Z),
                    coreFaces
            );
            outputElements.add(coreElement);

            var sprite = baker.sprites().get(material, debugName);
            var frontSpriteContents = sprite.contents();

            outputElements.addAll(ItemModelGenerator.createSideElements(frontSpriteContents, FRONT_TEXTURE_REF, 0));
        }

        return SimpleUnbakedGeometry.bake(outputElements, textureSlots, baker, modelState, debugName);
    }

    private CoinModelGenerator() {
    }
}