package org.academy.internal.common.world.item;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import org.academy.internal.client.renderer.item.GeoBlockItemRenderer;

import java.util.function.Consumer;

/** {@link BlockItem} + {@link GeoItem} for single-block machines that reuse block geo assets. */
public class GeoBlockItem extends BlockItem implements GeoItem {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final Identifier geoModelId;
    private final float renderScale;
    private final boolean sharedRelayPlatformTexture;

    public GeoBlockItem(Block block, Properties properties, Identifier geoModelId) {
        this(block, properties, geoModelId, 1.0F, false);
    }

    public GeoBlockItem(Block block, Properties properties, Identifier geoModelId, float renderScale) {
        this(block, properties, geoModelId, renderScale, false);
    }

    public GeoBlockItem(
            Block block,
            Properties properties,
            Identifier geoModelId,
            float renderScale,
            boolean sharedRelayPlatformTexture
    ) {
        super(block, properties);
        this.geoModelId = geoModelId;
        this.renderScale = renderScale;
        this.sharedRelayPlatformTexture = sharedRelayPlatformTexture;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<?> renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (this.renderer == null) {
                    this.renderer = new GeoBlockItemRenderer<>(
                            geoModelId, renderScale, sharedRelayPlatformTexture
                    );
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
