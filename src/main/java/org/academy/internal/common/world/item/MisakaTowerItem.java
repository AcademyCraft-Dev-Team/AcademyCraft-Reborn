package org.academy.internal.common.world.item;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.academy.internal.client.renderer.item.MisakaTowerRenderer;
import org.academy.internal.common.network.misaka.MisakaSisterHandInteractPacket;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.misaka.MisakaNetworkClient;

import java.util.function.Consumer;

public final class MisakaTowerItem extends Item implements GeoItem {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public MisakaTowerItem(Properties properties) {
        super(properties
                // canAlwaysEat=true so players can eat off sister interactions; Item#use must
                // still refuse when the click is aimed at a sister (full / refuse paths).
                .component(DataComponents.FOOD, new FoodProperties(8, 0.6f, true))
                .component(DataComponents.CONSUMABLE, Consumables.DEFAULT_FOOD));
    }

    @Override
    public InteractionResult interactLivingEntity(
            ItemStack stack,
            Player player,
            net.minecraft.world.entity.LivingEntity target,
            InteractionHand hand
    ) {
        if (!(target instanceof MisakaSisterEntity sister)) {
            return InteractionResult.PASS;
        }
        // Runs after Entity.mobInteract when that path did not consume. Also covers cases
        // where the client predicted withoutItem but the server still needs an explicit act.
        if (player.level().isClientSide()) {
            MisakaNetworkClient.send(new MisakaSisterHandInteractPacket(sister.getUUID(), hand));
            return InteractionResult.SUCCESS.withoutItem();
        }
        return sister.forceMobInteract(player, hand);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // EntityInteract may miss while aim still hits (incap NoAI / overlap). Client must
        // notify the server — returning withoutItem alone never runs recover/feed.
        var aimed = MisakaSisterEntity.findAimedSister(player);
        if (aimed.isPresent()) {
            if (level.isClientSide()) {
                MisakaNetworkClient.send(new MisakaSisterHandInteractPacket(aimed.get().getUUID(), hand));
                return InteractionResult.SUCCESS.withoutItem();
            }
            return aimed.get().forceMobInteract(player, hand);
        }
        return super.use(level, player, hand);
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<?> renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (this.renderer == null) {
                    this.renderer = new MisakaTowerRenderer<>();
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

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.accept(Component.translatable("item.academy.misaka_tower.tooltip.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.academy.misaka_tower.tooltip.2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.academy.misaka_tower.tooltip.3")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
