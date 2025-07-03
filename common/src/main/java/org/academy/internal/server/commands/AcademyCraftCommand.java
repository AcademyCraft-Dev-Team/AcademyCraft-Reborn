package org.academy.internal.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.academy.api.common.ability.AbilitySystem;
import org.academy.api.common.ability.Skill;
import org.academy.api.server.ability.AbilitySystemServer;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AcademyCraftCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("academy")
                .then(Commands.literal("learn_all")
                        .requires(source -> source.hasPermission(2))
                        .executes(AcademyCraftCommand::learnAllSkills))
                .then(Commands.literal("learned")
                        .requires(source -> source.hasPermission(2))
                        .executes(AcademyCraftCommand::listLearnedSkills))
                .then(Commands.literal("learn")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("skill_name", StringArgumentType.string())
                                .suggests(AcademyCraftCommand::suggestLearnableSkills)
                                .executes(AcademyCraftCommand::learnSingleSkill)))
                .then(Commands.literal("set_category")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("category_name", StringArgumentType.string())
                                .suggests(AcademyCraftCommand::suggestAbilityCategories)
                                .executes(AcademyCraftCommand::setAbilityCategory))));
    }

    private static int learnAllSkills(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var playerUuid = player.getUUID();
        var currentCategory = AbilitySystemServer.getPlayerAbilityCategory(playerUuid);

        if (currentCategory == null) {
            context.getSource().sendFailure(Component.literal("Could not retrieve current ability category."));
            return 0;
        }

        if (currentCategory.skillList.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Current ability category " + currentCategory.name + " has no skills to learn."), false);
            return 1;
        }

        for (Skill skill : currentCategory.skillList) {
            AbilitySystemServer.addPlayerSkill(playerUuid, skill.name);
        }

        context.getSource().sendSuccess(() -> Component.literal("All skills from ability category " + currentCategory.name + " have been learned."), true);
        return 1;
    }

    private static int listLearnedSkills(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var playerUuid = player.getUUID();
        var learnedSkills = AbilitySystemServer.getPlayerSkills(playerUuid);

        if (learnedSkills.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("You have not learned any skills yet."), false);
        } else {
            String skillsString = String.join(", ", learnedSkills);
            context.getSource().sendSuccess(() -> Component.literal("Learned skills: " + skillsString), false);
        }
        return 1;
    }

    private static int learnSingleSkill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var playerUuid = player.getUUID();
        var skillName = StringArgumentType.getString(context, "skill_name");

        var skillToLearn = AbilitySystem.SKILL_MAP.get(skillName);

        if (skillToLearn == null) {
            context.getSource().sendFailure(Component.literal("Skill '" + skillName + "' not found."));
            return 0;
        }

        var playerCategory = AbilitySystemServer.getPlayerAbilityCategory(playerUuid);
        if (playerCategory == null || !playerCategory.skillList.contains(skillToLearn)) {
            context.getSource().sendFailure(Component.literal("Skill '" + skillName + "' does not belong to your current ability category (" + (playerCategory != null ? playerCategory.name : "None") + ")."));
            return 0;
        }

        if (AbilitySystemServer.getPlayerSkills(playerUuid).contains(skillName)) {
            context.getSource().sendFailure(Component.literal("You have already learned skill '" + skillName + "'."));
            return 0;
        }

        AbilitySystemServer.addPlayerSkill(playerUuid, skillName);
        context.getSource().sendSuccess(() -> Component.literal("Successfully learned skill: " + skillName), true);
        return 1;
    }

    private static int setAbilityCategory(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var playerUuid = player.getUUID();
        var categoryName = StringArgumentType.getString(context, "category_name");

        var categoryToSet = AbilitySystem.ABILITY_CATEGORY_MAP.get(categoryName);

        if (categoryToSet == null) {
            context.getSource().sendFailure(Component.literal("Ability category '" + categoryName + "' not found."));
            return 0;
        }

        AbilitySystemServer.setPlayerAbilityCategory(playerUuid, categoryToSet);
        AbilitySystemServer.getPlayerSkills(playerUuid).clear();
        AbilitySystemServer.schedulePlayerSync(playerUuid, AbilitySystemServer.SyncType.SKILLS);
        context.getSource().sendSuccess(() -> Component.literal("Ability category set to: " + categoryName + ". All previous skills have been cleared."), true);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestLearnableSkills(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            var player = context.getSource().getPlayerOrException();
            var playerUuid = player.getUUID();
            var currentCategory = AbilitySystemServer.getPlayerAbilityCategory(playerUuid);
            var learnedSkills = AbilitySystemServer.getPlayerSkills(playerUuid);

            if (currentCategory != null) {
                return SharedSuggestionProvider.suggest(
                        currentCategory.skillList.stream()
                                .map(skill -> skill.name)
                                .filter(skillName -> !learnedSkills.contains(skillName))
                                .collect(Collectors.toList()),
                        builder
                );
            }
        } catch (CommandSyntaxException e) {
            return Suggestions.empty();
        }
        return Suggestions.empty();
    }

    private static CompletableFuture<Suggestions> suggestAbilityCategories(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                AbilitySystem.ABILITY_CATEGORY_MAP.keySet().stream(),
                builder
        );
    }
}