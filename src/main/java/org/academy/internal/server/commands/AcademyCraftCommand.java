package org.academy.internal.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.academy.AcademyCraftServer;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.data.CPData;
import org.academy.api.common.registries.Registries;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber
public final class AcademyCraftCommand {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static final class CommandUtils {
        private CommandUtils() {
        }

        public static AcademyCraftServer getServer(CommandContext<CommandSourceStack> context) {
            var server = context.getSource().getServer();
            return ((MinecraftServerContext) server).getAcademyCraftServer();
        }

        public static AbilitySystemServer getSystem(CommandContext<CommandSourceStack> context) {
            return getServer(context).getAbilitySystemServer();
        }

        public static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) {
            try {
                return context.getSource().getPlayerOrException();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
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
                                        .executes(AcademyCraftCommand::setSkillExp)))
                )
                .then(Commands.literal("debug")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(CPDebugCommands.register())
                )
                .then(Commands.literal("dev")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("state", BoolArgumentType.bool())
                                .executes(AcademyCraftCommand::toggleDevMode))
                )
        );
    }

    private static class CPDebugCommands {

        static LiteralArgumentBuilder<CommandSourceStack> register() {
            return Commands.literal("cp")
                    .then(Commands.literal("info")
                            .executes(ctx -> info(ctx, ctx.getSource().getPlayerOrException(), false))
                            .then(Commands.argument("target", EntityArgument.player())
                                    .executes(ctx -> info(ctx, EntityArgument.getPlayer(ctx, "target"), false))
                                    .then(Commands.argument("broadcast", BoolArgumentType.bool())
                                            .executes(ctx -> info(ctx, EntityArgument.getPlayer(ctx, "target"), BoolArgumentType.getBool(ctx, "broadcast"))))))

                    .then(Commands.literal("get")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.literal("value").executes(ctx -> get(ctx, "value")))
                                    .then(Commands.literal("max").executes(ctx -> get(ctx, "max")))
                                    .then(Commands.literal("curr_sp").executes(ctx -> get(ctx, "curr_sp")))
                                    .then(Commands.literal("max_sp").executes(ctx -> get(ctx, "max_sp")))
                                    .then(Commands.literal("level").executes(ctx -> get(ctx, "level")))
                                    .then(Commands.literal("timer").executes(ctx -> get(ctx, "timer")))
                                    .then(Commands.literal("status").executes(ctx -> get(ctx, "status")))))

                    .then(Commands.literal("set")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.argument("value", FloatArgumentType.floatArg())
                                            .executes(ctx -> set(ctx, EntityArgument.getPlayer(ctx, "target"), FloatArgumentType.getFloat(ctx, "value"), false))
                                            .then(Commands.argument("broadcast", BoolArgumentType.bool())
                                                    .executes(ctx -> set(ctx, EntityArgument.getPlayer(ctx, "target"), FloatArgumentType.getFloat(ctx, "value"), BoolArgumentType.getBool(ctx, "broadcast")))))))

                    .then(Commands.literal("set_max")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.argument("value", FloatArgumentType.floatArg(0))
                                            .executes(ctx -> setMax(ctx, EntityArgument.getPlayer(ctx, "target"), FloatArgumentType.getFloat(ctx, "value"), false))
                                            .then(Commands.argument("broadcast", BoolArgumentType.bool())
                                                    .executes(ctx -> setMax(ctx, EntityArgument.getPlayer(ctx, "target"), FloatArgumentType.getFloat(ctx, "value"), BoolArgumentType.getBool(ctx, "broadcast")))))))

                    .then(Commands.literal("set_status")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.argument("status", StringArgumentType.word())
                                            .suggests(CPDebugCommands::suggestStatus)
                                            .executes(ctx -> setStatus(ctx, EntityArgument.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "status"), 0, false))
                                            .then(Commands.argument("timer", IntegerArgumentType.integer(0))
                                                    .executes(ctx -> setStatus(ctx, EntityArgument.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "status"), IntegerArgumentType.getInteger(ctx, "timer"), false))
                                                    .then(Commands.argument("broadcast", BoolArgumentType.bool())
                                                            .executes(ctx -> setStatus(ctx, EntityArgument.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "status"), IntegerArgumentType.getInteger(ctx, "timer"), BoolArgumentType.getBool(ctx, "broadcast"))))))));
        }

        private static int info(CommandContext<CommandSourceStack> context, ServerPlayer player, boolean broadcast) {
            var uuid = player.getUUID();
            var name = player.getName().getString();
            var system = CommandUtils.getSystem(context);

            var current = system.getPlayerAvailableCP(uuid);
            var max = system.getPlayerMaxCP(uuid);
            var level = system.getPlayerLevel(uuid);
            var currSP = system.getPlayerCurrSP(uuid);
            var maxSP = system.getPlayerMaxSP(uuid);
            var status = system.getPlayerStatus(uuid);
            var timer = system.getPlayerStateTimer(uuid);

            Component message = Component.literal(String.format(
                    """
                            §e[CP Debug: %s]§r
                            §7UUID: %s§r
                            §fLevel: §d%d§r
                            §fCP: §b%.2f§r / §3%.2f§r
                            §fSP: §e%d§r / §6%d§r
                            §fStatus: §a%s§r (Timer: §6%d§r)""",
                    name, uuid, level, current, max, currSP, maxSP, status, timer
            ));
            sendFeedback(context, message, broadcast);
            return 1;
        }

        private static int get(CommandContext<CommandSourceStack> context, String type) throws CommandSyntaxException {
            var target = EntityArgument.getPlayer(context, "target");
            var uuid = target.getUUID();
            var system = CommandUtils.getSystem(context);

            return switch (type) {
                case "value" -> (int) system.getPlayerAvailableCP(uuid);
                case "max" -> (int) system.getPlayerMaxCP(uuid);
                case "curr_sp" -> system.getPlayerCurrSP(uuid);
                case "max_sp" -> system.getPlayerMaxSP(uuid);
                case "level" -> system.getPlayerLevel(uuid);
                case "timer" -> system.getPlayerStateTimer(uuid);
                case "status" -> system.getPlayerStatus(uuid).ordinal();
                default -> 0;
            };
        }

        private static int set(CommandContext<CommandSourceStack> context, ServerPlayer player, float value, boolean broadcast) {
            var uuid = player.getUUID();
            var serverContext = (MinecraftServerContext) player.level().getServer();
            var abilitySystemServer = serverContext.getAcademyCraftServer().getAbilitySystemServer();
            abilitySystemServer.setPlayerAvailableCP(uuid, value);

            Component message = Component.literal(String.format("§e[AC Debug]§r Set Available CP for %s to: %.2f", player.getName().getString(), value));
            sendFeedback(context, message, broadcast);
            return 1;
        }

        private static int setMax(CommandContext<CommandSourceStack> context, ServerPlayer player, float value, boolean broadcast) {
            var uuid = player.getUUID();
            var serverContext = (MinecraftServerContext) player.level().getServer();
            var abilitySystemServer = serverContext.getAcademyCraftServer().getAbilitySystemServer();
            abilitySystemServer.setPlayerMaxCP(uuid, value);

            Component message = Component.literal(String.format("§e[AC Debug]§r Set Max CP for %s to: %.2f", player.getName().getString(), value));
            sendFeedback(context, message, broadcast);
            return 1;
        }

        private static int setStatus(CommandContext<CommandSourceStack> context, ServerPlayer player, String statusName, int timer, boolean broadcast) {
            var uuid = player.getUUID();
            try {
                var status = CPData.Status.valueOf(statusName.toUpperCase());
                var serverContext = (MinecraftServerContext) player.level().getServer();
                var abilitySystemServer = serverContext.getAcademyCraftServer().getAbilitySystemServer();
                abilitySystemServer.setPlayerStatus(uuid, status);
                abilitySystemServer.setPlayerStateTimer(uuid, timer);

                Component message = Component.literal(String.format("§e[AC Debug]§r Set Status for %s to: %s, Timer: %d", player.getName().getString(), status, timer));
                sendFeedback(context, message, broadcast);
            } catch (IllegalArgumentException e) {
                context.getSource().sendFailure(Component.literal("Invalid status: " + statusName));
                return 0;
            }
            return 1;
        }

        private static void sendFeedback(CommandContext<CommandSourceStack> context, Component message, boolean broadcast) {
            if (broadcast) {
                context.getSource().getServer().getPlayerList().broadcastSystemMessage(message, false);
            } else {
                context.getSource().sendSuccess(() -> message, true);
            }
        }

        private static CompletableFuture<Suggestions> suggestStatus(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
            return SharedSuggestionProvider.suggest(
                    Arrays.stream(CPData.Status.values()).map(Enum::name),
                    builder
            );
        }
    }

    private static int toggleDevMode(CommandContext<CommandSourceStack> context) {
        var enabled = BoolArgumentType.getBool(context, "state");
        AbilitySystemServer.setDevMode(enabled);
        context.getSource().sendSuccess(
                () -> Component.literal("§e[AC Dev]§r Dev mode: " + (enabled ? "§aON" : "§cOFF")),
                true);
        return 1;
    }

    private static int learnAllSkills(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var value = 1;

        var player = context.getSource().getPlayerOrException();
        var playerUuid = player.getUUID();
        var serverContext = (MinecraftServerContext) player.level().getServer();
        var abilitySystemServer = serverContext.getAcademyCraftServer().getAbilitySystemServer();
        var currentCategory = abilitySystemServer.getPlayerAbilityCategory(playerUuid);
        var categoryKey = Registries.ABILITY_CATEGORIES.getKey(currentCategory);
        var categoryName = categoryKey != null ? categoryKey.toString() : "Unknown";

        if (currentCategory.getSkills().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Current ability category " + categoryName + " has no skills to learn."), false);
            return value;
        }

        for (var skill : currentCategory.getSkills()) {
            abilitySystemServer.addPlayerSkill(player, skill.getKeyString());
        }

        context.getSource().sendSuccess(() -> Component.literal("All skills from ability category " + categoryName + " have been learned."), true);
        return value;
    }

    private static int listLearnedSkills(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var playerUuid = player.getUUID();
        var serverContext = (MinecraftServerContext) player.level().getServer();

        var learnedSkills = serverContext.getAcademyCraftServer()
                .getAbilitySystemServer().getPlayerData(playerUuid).getSkillDataMap();

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

        var serverContext = (MinecraftServerContext) player.level().getServer();
        var abilitySystemServer = serverContext.getAcademyCraftServer().getAbilitySystemServer();

        var playerCategory = abilitySystemServer.getPlayerAbilityCategory(playerUuid);
        if (skillToLearn.get().value().getCategory() != playerCategory) {
            var playerCategoryKey = Registries.ABILITY_CATEGORIES.getKey(playerCategory);
            var playerCategoryName = playerCategoryKey != null ? playerCategoryKey.toString() : "None";
            context.getSource().sendFailure(Component.literal("Skill '" + skillIdentifier + "' does not belong to your current ability category (" + playerCategoryName + ")."));
            return 0;
        }

        if (
                serverContext.getAcademyCraftServer().getAbilitySystemServer().getPlayerData(playerUuid)
                        .isSkillLearned(skillIdentifier.toString())
        ) {
            context.getSource().sendFailure(Component.literal("You have already learned skill '" + skillIdentifier + "'."));
            return 0;
        }

        abilitySystemServer.addPlayerSkill(player, skillIdentifier.toString());
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

        var serverContext = (MinecraftServerContext) player.level().getServer();
        var abilitySystemServer = serverContext.getAcademyCraftServer().getAbilitySystemServer();
        abilitySystemServer.setPlayerAbilityCategory(playerUuid, categoryToSet.get().value());
        var learnedSkills = abilitySystemServer.getPlayerData(playerUuid).getSkillDataMap();
        learnedSkills.clear();
        var playerData = abilitySystemServer.getPlayerData(playerUuid);
        if (playerData != null) playerData.markDirty();
        abilitySystemServer.schedulePlayerSync(playerUuid, SyncTypes.SKILL_DATA);

        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Ability category set to: " + categoryIdentifier +
                                ". All previous skills have been cleared."
                ), true
        );
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestLearnableSkills(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            var player = context.getSource().getPlayerOrException();
            var playerUuid = player.getUUID();
            var serverContext = (MinecraftServerContext) player.level().getServer();
            var abilitySystemServer = serverContext.getAcademyCraftServer().getAbilitySystemServer();
            var currentCategory = abilitySystemServer.getPlayerAbilityCategory(playerUuid);
            var learnedSkills = abilitySystemServer.getPlayerData(playerUuid).getSkillDataMap();

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

        var serverContext = (MinecraftServerContext) player.level().getServer();
        var abilitySystemServer = serverContext.getAcademyCraftServer().getAbilitySystemServer();

        var playerData = abilitySystemServer.getPlayerData(playerUuid);
        var skillKey = skillIdentifier.toString();

        if (!playerData.isSkillLearned(skillKey)) {
            context.getSource().sendFailure(Component.literal("You do not have skill '" + skillIdentifier + "'."));
            return 0;
        }

        var skillData = playerData.getSkillDataMap().get(skillKey);
        skillData.setExp(amount);

        playerData.markDirty();
        abilitySystemServer.schedulePlayerSync(playerUuid, SyncTypes.SKILL_DATA);

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
            var serverContext = (MinecraftServerContext) player.level().getServer();
            var abilitySystemServer = serverContext.getAcademyCraftServer().getAbilitySystemServer();
            var learnedSkills = abilitySystemServer.getPlayerData(playerUuid).getSkillDataMap();

            return SharedSuggestionProvider.suggest(
                    learnedSkills.keySet(),
                    builder
            );
        } catch (CommandSyntaxException e) {
            return Suggestions.empty();
        }
    }
}