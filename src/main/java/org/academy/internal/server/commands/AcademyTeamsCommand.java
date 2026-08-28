package org.academy.internal.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.academy.internal.server.team.AcademyTeamData;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Self-service persistent teams supplementing vanilla scoreboard teams. */
public final class AcademyTeamsCommand {
    private AcademyTeamsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("teams")
                .executes(AcademyTeamsCommand::info)
                .then(Commands.literal("info").executes(AcademyTeamsCommand::info))
                .then(Commands.literal("list").executes(AcademyTeamsCommand::list))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(AcademyTeamsCommand::create)))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(AcademyTeamsCommand::invite)))
                .then(Commands.literal("accept")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(AcademyTeamsCommand::suggestInvitations)
                                .executes(AcademyTeamsCommand::accept)))
                .then(Commands.literal("leave").executes(AcademyTeamsCommand::leave))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(AcademyTeamsCommand::kick)))
                .then(Commands.literal("disband").executes(AcademyTeamsCommand::disband))
        );
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var data = data(context);
        var teamName = data.teamFor(player.getUUID()).orElse(null);
        if (teamName == null) return failure(context, AcademyTeamData.MutationResult.NOT_IN_TEAM);
        var memberNames = data.members(teamName).stream()
                .map(id -> {
                    var online = context.getSource().getServer().getPlayerList().getPlayer(id);
                    return online == null ? id.toString() : online.getScoreboardName();
                })
                .sorted()
                .toList();
        success(context, "command.academy.teams.info", teamName, String.join(", ", memberNames));
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        var names = data(context).teamNames();
        success(context, "command.academy.teams.list",
                names.isEmpty() ? "-" : String.join(", ", names));
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var data = data(context);
        var result = data.create(player.getUUID(), StringArgumentType.getString(context, "name"));
        if (result != AcademyTeamData.MutationResult.SUCCESS) return failure(context, result);
        success(context, "command.academy.teams.created", data.teamFor(player.getUUID()).orElse(""));
        return 1;
    }

    private static int invite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var target = EntityArgument.getPlayer(context, "player");
        var result = data(context).invite(player.getUUID(), target.getUUID());
        if (result != AcademyTeamData.MutationResult.SUCCESS) return failure(context, result);
        success(context, "command.academy.teams.invited", target.getDisplayName());
        return 1;
    }

    private static int accept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var name = StringArgumentType.getString(context, "name");
        var result = data(context).accept(player.getUUID(), name);
        if (result != AcademyTeamData.MutationResult.SUCCESS) return failure(context, result);
        success(context, "command.academy.teams.joined", name.toLowerCase(Locale.ROOT));
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var result = data(context).leave(player.getUUID());
        if (result == AcademyTeamData.MutationResult.TEAM_DISBANDED) {
            success(context, "command.academy.teams.owner_left");
            return 1;
        }
        if (result != AcademyTeamData.MutationResult.SUCCESS) return failure(context, result);
        success(context, "command.academy.teams.left");
        return 1;
    }

    private static int kick(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var target = EntityArgument.getPlayer(context, "player");
        var result = data(context).kick(player.getUUID(), target.getUUID());
        if (result != AcademyTeamData.MutationResult.SUCCESS) return failure(context, result);
        success(context, "command.academy.teams.kicked", target.getDisplayName());
        return 1;
    }

    private static int disband(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var result = data(context).disband(player.getUUID());
        if (result != AcademyTeamData.MutationResult.SUCCESS) return failure(context, result);
        success(context, "command.academy.teams.disbanded");
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestInvitations(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        return SharedSuggestionProvider.suggest(data(context).invitationsFor(player.getUUID()), builder);
    }

    private static AcademyTeamData data(CommandContext<CommandSourceStack> context) {
        return AcademyTeamData.get(context.getSource().getServer());
    }

    private static int failure(
            CommandContext<CommandSourceStack> context,
            AcademyTeamData.MutationResult result
    ) {
        context.getSource().sendFailure(Component.translatable(
                "command.academy.teams.error." + result.name().toLowerCase(Locale.ROOT)));
        return 0;
    }

    private static void success(
            CommandContext<CommandSourceStack> context,
            String key,
            Object... arguments
    ) {
        context.getSource().sendSuccess(() -> Component.translatable(key, arguments), false);
    }
}
