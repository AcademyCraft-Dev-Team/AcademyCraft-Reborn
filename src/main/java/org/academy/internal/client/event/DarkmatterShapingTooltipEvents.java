package org.academy.internal.client.event;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.darkmatter.DarkmatterShapingRegistries;
import org.academy.internal.common.world.item.DarkmatterItemUtil;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class DarkmatterShapingTooltipEvents {
    private DarkmatterShapingTooltipEvents() { }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (!DarkmatterItemUtil.isShapedItem(stack)) return;
        var profile = DarkmatterItemUtil.shapingProfile(stack);
        var total = Math.max(1, profile.alphaPoints() + profile.betaPoints());
        var alpha = Math.round(profile.alphaPoints() * 100.0f / total);
        event.getToolTip().add(Component.translatable(
                "tooltip.academy.darkmatter_shaping.phase", alpha, 100 - alpha)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (profile.modifiers().isEmpty()) return;
        event.getToolTip().add(Component.translatable(
                "tooltip.academy.darkmatter_shaping.modifiers")
                .withStyle(ChatFormatting.GRAY));
        profile.modifiers().forEach((id, level) -> DarkmatterShapingRegistries.modifier(id)
                .ifPresent(type -> event.getToolTip().add(Component.literal("  ")
                        .append(Component.translatable(type.nameKey()))
                        .append(Component.literal(" " + level))
                        .withStyle(ChatFormatting.AQUA))));
    }
}
