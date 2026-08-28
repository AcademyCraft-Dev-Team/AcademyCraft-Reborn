package org.academy.internal.client.data;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.ImpossibleTrigger;
import net.minecraft.advancements.triggers.PlayerTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.network.chat.Component;
import org.academy.AcademyCraft;
import org.academy.internal.common.advancement.AbilityAdvancements;
import org.academy.internal.common.world.item.Items;

import java.util.function.Consumer;

public final class AcademyCraftAdvancementProvider implements AdvancementSubProvider {
    @Override
    public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> output) {
        var root = Advancement.Builder.advancement()
                .display(
                        Items.ICON.get(),
                        title("root"),
                        description("root"),
                        AcademyCraft.academy("gui/advancements/ability_background"),
                        AdvancementType.TASK,
                        false,
                        false,
                        false
                )
                .addCriterion("tick", PlayerTrigger.TriggerInstance.tick())
                .build(AbilityAdvancements.ROOT);
        output.accept(root);

        branch(output, root, AbilityAdvancements.ACCELERATOR, net.minecraft.world.item.Items.FEATHER, "accelerator");
        branch(output, root, AbilityAdvancements.DARKMATTER, Items.DARKMATTER.get(), "darkmatter");
        branch(output, root, AbilityAdvancements.TELEPORT, net.minecraft.world.item.Items.ENDER_PEARL, "teleport");
        branch(output, root, AbilityAdvancements.MELTDOWNER, net.minecraft.world.item.Items.SLIME_BALL, "meltdowner");
        branch(output, root, AbilityAdvancements.MENTALOUT, net.minecraft.world.item.Items.COMPASS, "mentalout");
        branch(output, root, AbilityAdvancements.AEROMANIP, Items.WIND_GEN_FAN_ITEM.get(), "aeromanip");
        branch(output, root, AbilityAdvancements.ELECTROMASTER, Items.COIN.get(), "electromaster");
    }

    private static void branch(
            Consumer<AdvancementHolder> output,
            AdvancementHolder parent,
            net.minecraft.resources.Identifier id,
            net.minecraft.world.level.ItemLike icon,
            String name
    ) {
        output.accept(Advancement.Builder.advancement()
                .parent(parent)
                .display(
                        icon,
                        title(name),
                        description(name),
                        null,
                        AdvancementType.GOAL,
                        true,
                        true,
                        true
                )
                .addCriterion(
                        AbilityAdvancements.CRITERION,
                        CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance())
                )
                .build(id));
    }

    private static Component title(String name) {
        return Component.translatable("advancements.academy.ability." + name + ".title");
    }

    private static Component description(String name) {
        return Component.translatable("advancements.academy.ability." + name + ".description");
    }
}
