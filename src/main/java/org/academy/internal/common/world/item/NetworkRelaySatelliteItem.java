package org.academy.internal.common.world.item;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.academy.internal.client.renderer.item.RelaySatelliteItemRenderer;

import java.util.function.Consumer;

/** Standard Misaka network relay satellite (overworld coverage only). */
public final class NetworkRelaySatelliteItem extends Item implements GeoItem {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public NetworkRelaySatelliteItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    public static boolean isSatellite(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.is(Items.NETWORK_RELAY_SATELLITE.get())
                || stack.is(Items.HYPER_NETWORK_RELAY_SATELLITE.get()));
    }

    public static boolean isHyper(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.HYPER_NETWORK_RELAY_SATELLITE.get());
    }

    public static ResourceKey<Level> targetDimension(ItemStack stack) {
        if (isHyper(stack)) {
            return HyperNetworkRelaySatelliteItem.targetDimension(stack);
        }
        return Level.OVERWORLD;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<?> renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (this.renderer == null) {
                    this.renderer = new RelaySatelliteItemRenderer<>();
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
