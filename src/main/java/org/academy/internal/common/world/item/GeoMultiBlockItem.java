package org.academy.internal.common.world.item;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.resources.Identifier;
import org.academy.internal.client.renderer.item.GeoBlockItemRenderer;
import org.academy.internal.common.world.level.block.MultiBlock;

import java.util.function.Consumer;

/**
 * Shared {@link MultiBlockItem} + {@link GeoItem} for machines that reuse block geo assets
 * in inventory / hand. New models only need registration + asset id (and optional scale).
 */
public class GeoMultiBlockItem extends MultiBlockItem implements GeoItem {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final Identifier geoModelId;
    private final float renderScale;

    public GeoMultiBlockItem(MultiBlock block, Properties properties, Identifier geoModelId) {
        this(block, properties, geoModelId, 1.0F);
    }

    public GeoMultiBlockItem(
            MultiBlock block, Properties properties, Identifier geoModelId, float renderScale
    ) {
        super(block, properties);
        this.geoModelId = geoModelId;
        this.renderScale = renderScale;
    }

    public Identifier geoModelId() {
        return geoModelId;
    }

    public float renderScale() {
        return renderScale;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<?> renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (this.renderer == null) {
                    this.renderer = new GeoBlockItemRenderer<>(geoModelId, renderScale);
                }
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
