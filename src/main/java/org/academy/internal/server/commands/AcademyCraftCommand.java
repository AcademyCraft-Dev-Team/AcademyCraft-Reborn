package org.academy.internal.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.registries.Registries;
import org.academy.api.server.ability.AbilitySystemServer;

import java.util.concurrent.CompletableFuture;

@EventBusSubscriber
public final class AcademyCraftCommand {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("academy")
                .then(Commands.literal("learn_all")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(AcademyCraftCommand::learnAllSkills))
                .then(Commands.literal("learned")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(AcademyCraftCommand::listLearnedSkills))
                .then(Commands.literal("learn")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("skill_name", IdentifierArgument.id())
                                .suggests(AcademyCraftCommand::suggestLearnableSkills)
                                .executes(AcademyCraftCommand::learnSingleSkill)))
                .then(Commands.literal("set_category")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("category_name", IdentifierArgument.id())
                                .suggests(AcademyCraftCommand::suggestAbilityCategories)
                                .executes(AcademyCraftCommand::setAbilityCategory)))
                .then(Commands.literal("set_exp")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("skill_name", IdentifierArgument.id())
                                .suggests(AcademyCraftCommand::suggestLearnedSkills)
                                .then(Commands.argument("amount", FloatArgumentType.floatArg(0))
                                        .executes(AcademyCraftCommand::setSkillExp))))
        );
    }

    private static int learnAllSkills(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var playerUuid = player.getUUID();
        var currentCategory = AbilitySystemServer.getPlayerAbilityCategory(playerUuid);
        var categoryKey = Registries.ABILITY_CATEGORIES.getKey(currentCategory);
        var categoryName = categoryKey != null ? categoryKey.toString() : "Unknown";

        if (currentCategory.getSkills().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Current ability category " + categoryName + " has no skills to learn."), false);
            return 1;
        }

        for (var skill : currentCategory.getSkills()) {
            AbilitySystemServer.addPlayerSkill(playerUuid, skill.getKeyString());
        }

        context.getSource().sendSuccess(() -> Component.literal("All skills from ability category " + categoryName + " have been learned."), true);
        return 1;
    }

    private static int listLearnedSkills(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var playerUuid = player.getUUID();
        var learnedSkills = AbilitySystemServer.getPlayerData(playerUuid).getSkillData();

        if (learnedSkills.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("You have not learned any skills yet."), false);
        } else {
            var skillsString = String.join(", ", learnedSkills.keySet());
            context.getSource().sendSuccess(() -> Component.literal("Learned skills: " + skillsString), false);
        }
        return 1;
    }

    private static int learnSingleSkill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var playerUuid = player.getUUID();
        var skillIdentifier = IdentifierArgument.getId(context, "skill_name");

        var skillToLearn = Registries.SKILLS.get(skillIdentifier);

        if (skillToLearn.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Skill '" + skillIdentifier + "' not found."));
            return 0;
        }

        var playerCategory = AbilitySystemServer.getPlayerAbilityCategory(playerUuid);
        if (skillToLearn.get().value().getCategory() != playerCategory) {
            var playerCategoryKey = Registries.ABILITY_CATEGORIES.getKey(playerCategory);
            var playerCategoryName = playerCategoryKey != null ? playerCategoryKey.toString() : "None";
            context.getSource().sendFailure(Component.literal("Skill '" + skillIdentifier + "' does not belong to your current ability category (" + playerCategoryName + ")."));
            return 0;
        }

        if (AbilitySystemServer.getPlayerData(playerUuid).isSkillLearned(skillIdentifier.toString())) {
            context.getSource().sendFailure(Component.literal("You have already learned skill '" + skillIdentifier + "'."));
            return 0;
        }

        AbilitySystemServer.addPlayerSkill(playerUuid, skillIdentifier.toString());
        context.getSource().sendSuccess(() -> Component.literal("Successfully learned skill: " + skillIdentifier), true);
        return 1;
    }

    private static int setAbilityCategory(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var playerUuid = player.getUUID();
        var categoryIdentifier = IdentifierArgument.getId(context, "category_name");

        var categoryToSet = Registries.ABILITY_CATEGORIES.get(categoryIdentifier);

        if (categoryToSet.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Ability category '" + categoryIdentifier + "' not found."));
            return 0;
        }

        AbilitySystemServer.setPlayerAbilityCategory(playerUuid, categoryToSet.get().value());
        var learnedSkills = AbilitySystemServer.getPlayerData(playerUuid).getSkillData();
        if (learnedSkills != null) {
            learnedSkills.clear();
            var playerData = AbilitySystemServer.getPlayerData(playerUuid);
            if (playerData != null) playerData.markDirty();
            AbilitySystemServer.schedulePlayerSync(playerUuid, SyncTypes.SKILL_DATA);
        }

        context.getSource().sendSuccess(() -> Component.literal("Ability category set to: " + categoryIdentifier + ". All previous skills have been cleared."), true);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestLearnableSkills(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            var player = context.getSource().getPlayerOrException();
            var playerUuid = player.getUUID();
            var currentCategory = AbilitySystemServer.getPlayerAbilityCategory(playerUuid);
            var learnedSkills = AbilitySystemServer.getPlayerData(playerUuid).getSkillData();

            return SharedSuggestionProvider.suggest(
                    currentCategory.getSkills().stream()
                            .map(skill -> skill.getKey().toString())
                            .filter(skillName -> !learnedSkills.containsKey(skillName)),
                    builder
            );
        } catch (CommandSyntaxException e) {
            return Suggestions.empty();
        }
    }

    private static int setSkillExp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var playerUuid = player.getUUID();
        var skillIdentifier = IdentifierArgument.getId(context, "skill_name");
        var amount = FloatArgumentType.getFloat(context, "amount");

        var playerData = AbilitySystemServer.getPlayerData(playerUuid);
        var skillKey = skillIdentifier.toString();

        if (!playerData.isSkillLearned(skillKey)) {
            context.getSource().sendFailure(Component.literal("You do not have skill '" + skillIdentifier + "'."));
            return 0;
        }

        var skillData = playerData.getSkillData().get(skillKey);
        skillData.setExp(amount);

        playerData.markDirty();
        AbilitySystemServer.schedulePlayerSync(playerUuid, SyncTypes.SKILL_DATA);

        context.getSource().sendSuccess(() -> Component.literal("Set experience for " + skillIdentifier + " to " + amount), true);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestAbilityCategories(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                Registries.ABILITY_CATEGORIES.keySet().stream().map(Identifier::toString),
                builder
        );
    }

    private static CompletableFuture<Suggestions> suggestLearnedSkills(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            var player = context.getSource().getPlayerOrException();
            var playerUuid = player.getUUID();
            var learnedSkills = AbilitySystemServer.getPlayerData(playerUuid).getSkillData();

            return SharedSuggestionProvider.suggest(
                    learnedSkills.keySet(),
                    builder
            );
        } catch (CommandSyntaxException e) {
            return Suggestions.empty();
        }
    }
}