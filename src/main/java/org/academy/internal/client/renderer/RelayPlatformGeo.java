package org.academy.internal.client.renderer;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.model.DefaultedBlockGeoModel;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.model.DefaultedItemGeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

/**
 * Launch pad + relay satellite share one atlas ({@code textures/block/relay_satellite.png}).
 * Model/animation ids stay separate; only the texture path is forced shared.
 */
public final class RelayPlatformGeo {
    /** Single atlas used by pad, seated sat, flying sat, and sat items. */
    public static final Identifier TEXTURE = AcademyCraft.academy("textures/block/relay_satellite.png");

    private RelayPlatformGeo() {
    }

    public static <T extends GeoAnimatable> DefaultedBlockGeoModel<T> blockModel(Identifier modelId) {
        return new DefaultedBlockGeoModel<>(modelId) {
            @Override
            public Identifier getTextureResource(GeoRenderState renderState) {
                return TEXTURE;
            }
        };
    }

    public static <T extends GeoAnimatable> DefaultedEntityGeoModel<T> entityModel(Identifier modelId) {
        return new DefaultedEntityGeoModel<>(modelId) {
            @Override
            public Identifier getTextureResource(GeoRenderState renderState) {
                return TEXTURE;
            }
        };
    }

    public static <T extends GeoAnimatable> DefaultedItemGeoModel<T> itemModel(Identifier modelId) {
        return new DefaultedItemGeoModel<>(modelId) {
            @Override
            public Identifier getTextureResource(GeoRenderState renderState) {
                return TEXTURE;
            }
        };
    }
}
