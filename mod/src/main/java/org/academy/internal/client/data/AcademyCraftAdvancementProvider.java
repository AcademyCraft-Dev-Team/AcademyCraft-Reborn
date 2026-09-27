package org.academy.internal.client.data;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.ImpossibleTrigger;
import net.minecraft.advancements.triggers.PlayerTrigger;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.academy.AcademyCraft;
import org.academy.internal.common.advancement.AbilityAdvancements;
import org.academy.internal.common.world.item.Items;

public final class AcademyCraftAdvancementProvider extends AdvancementSubProvider {
    public AcademyCraftAdvancementProvider(BootstrapContext<Advancement> output) {
        super(output);
    }

    @Override
    public void generate() {
        var root = Advancement.Builder.advancement()
                .rootDisplay(
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
        root.register(output);

        branch(root, AbilityAdvancements.ACCELERATOR, net.minecraft.world.item.Items.FEATHER, "accelerator");
        branch(root, AbilityAdvancements.DARKMATTER, Items.DARKMATTER.get(), "darkmatter");
        branch(root, AbilityAdvancements.TELEPORT, net.minecraft.world.item.Items.ENDER_PEARL, "teleport");
        branch(root, AbilityAdvancements.MELTDOWNER, net.minecraft.world.item.Items.SLIME_BALL, "meltdowner");
        branch(root, AbilityAdvancements.MENTALOUT, net.minecraft.world.item.Items.COMPASS, "mentalout");
        branch(root, AbilityAdvancements.AEROMANIP, Items.WIND_GEN_FAN_ITEM.get(), "aeromanip");
        branch(root, AbilityAdvancements.ELECTROMASTER, Items.COIN.get(), "electromaster");
    }

    private void branch(
            AdvancementHolder parent,
            Identifier id,
            Item icon,
            String name
    ) {
        Advancement.Builder.advancement()
                .parent(parent)
                .display(
                        icon,
                        title(name),
                        description(name),
                        AdvancementType.GOAL,
                        true,
                        true,
                        true
                )
                .addCriterion(
                        AbilityAdvancements.CRITERION,
                        CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance())
                )
                .build(id)
                .register(output);
    }

    private static Component title(String name) {
        return Component.translatable("advancements.academy.ability." + name + ".title");
    }

    private static Component description(String name) {
        return Component.translatable("advancements.academy.ability." + name + ".description");
    }
}
