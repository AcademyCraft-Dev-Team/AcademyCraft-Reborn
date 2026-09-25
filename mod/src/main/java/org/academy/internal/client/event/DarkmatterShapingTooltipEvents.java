package org.academy.internal.client.event;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.darkmatter.DarkmatterShape;
import org.academy.api.common.ability.darkmatter.DarkmatterShapingProfile;
import org.academy.api.common.ability.darkmatter.DarkmatterShapingRegistries;
import org.academy.internal.common.world.item.DarkmatterBlockItem;
import org.academy.internal.common.world.item.DarkmatterItemUtil;
import org.academy.internal.common.world.item.Items;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class DarkmatterShapingTooltipEvents {
    private DarkmatterShapingTooltipEvents() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (DarkmatterItemUtil.isShapedItem(stack)
                && DarkmatterItemUtil.shape(stack)
                == DarkmatterShape.BLOCK) {
            var profile = DarkmatterBlockItem.profile(stack);
            event.getToolTip().add(Component.translatable(
                            "tooltip.academy.darkmatter_shaping.block_profile",
                            profile.hardness(), profile.explosionResistance(),
                            Component.translatable(profile.gravity()
                                    ? "screen.academy.darkmatter_shaping.block.gravity.enabled"
                                    : "screen.academy.darkmatter_shaping.block.gravity.disabled"))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        if (DarkmatterItemUtil.hasNativeItemEffects(stack)
                || stack.is(Items.DARKMATTER_COATING.get())) {
            appendProfile(event, DarkmatterItemUtil.shapingProfile(stack), false);
        }
        if (DarkmatterItemUtil.hasCoating(stack)) {
            event.getToolTip().add(Component.translatable(
                            "tooltip.academy.darkmatter_shaping.coating")
                    .withStyle(ChatFormatting.GRAY));
            appendProfile(event, DarkmatterItemUtil.coatingProfile(stack), true);
        }
    }

    private static void appendProfile(ItemTooltipEvent event,
                                      DarkmatterShapingProfile profile,
                                      boolean indented) {
        var total = Math.max(1, profile.alphaPoints() + profile.betaPoints());
        var alpha = Math.round(profile.alphaPoints() * 100.0f / total);
        var prefix = indented ? "  " : "";
        event.getToolTip().add(Component.literal(prefix).append(Component.translatable(
                        "tooltip.academy.darkmatter_shaping.phase", alpha, 100 - alpha))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (profile.modifiers().isEmpty()) return;
        event.getToolTip().add(Component.literal(prefix).append(Component.translatable(
                        "tooltip.academy.darkmatter_shaping.modifiers"))
                .withStyle(ChatFormatting.GRAY));
        profile.modifiers().forEach((id, level) -> DarkmatterShapingRegistries.modifier(id)
                .ifPresent(type -> event.getToolTip().add(Component.literal(prefix + "  ")
                        .append(Component.translatable(type.nameKey()))
                        .append(Component.literal(" " + level))
                        .withStyle(ChatFormatting.AQUA))));
    }
}
