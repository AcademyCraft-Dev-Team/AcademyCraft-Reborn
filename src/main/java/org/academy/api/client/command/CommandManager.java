package org.academy.api.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import org.academy.api.client.network.AcademyCraftNetworkSystemClient;
import org.academy.api.client.network.packet.C2SRequestPacket;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.FriendlyByteBufIdentifiers;
import org.academy.api.common.network.Response;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public class CommandManager {
    public static final List<String> HISTORY = new CopyOnWriteArrayList<>();
    public static final CommandDispatcher<ConsoleSource> dispatcher = new CommandDispatcher<>();
    public static final LiteralArgumentBuilder<ConsoleSource> CONFIG;

    static {
        CONFIG = LiteralArgumentBuilder.literal("config");
    }

    public static void registerCommands() {
        dispatcher.register(CONFIG);

        dispatcher.register(
                LiteralArgumentBuilder.<ConsoleSource>literal("help")
                        .executes(CommandManager::executeHelpCommand)
        );

        dispatcher.register(
                LiteralArgumentBuilder.<ConsoleSource>literal("skill_list")
                        .then(LiteralArgumentBuilder.<ConsoleSource>literal("all")
                                .executes(CommandManager::fetchAllSkill)
                        )
                        .then(LiteralArgumentBuilder.<ConsoleSource>literal("learned")
                                .executes(CommandManager::fetchLearnedSkill)
                        )
        );

        dispatcher.register(
                LiteralArgumentBuilder.<ConsoleSource>literal("learn")
                        .then(LiteralArgumentBuilder.<ConsoleSource>literal("skill")
                                .then(RequiredArgumentBuilder.<ConsoleSource, String>argument("identifier", StringArgumentType.string())
                                        .suggests(new SuggestionProvider<ConsoleSource>() {
                                            @Override
                                            public CompletableFuture<Suggestions> getSuggestions(CommandContext<ConsoleSource> context, SuggestionsBuilder builder) throws CommandSyntaxException {
                                                return CompletableFuture.supplyAsync(new Supplier<Suggestions>() {
                                                    @Override
                                                    public Suggestions get() {
                                                        builder.suggest("learn");
                                                        return builder.build();
                                                    }
                                                });
                                            }
                                        })
                                        .executes(context -> CommandManager.learnSkill(context, StringArgumentType.getString(context, "identifier")))
                                )
                        )
                        .then(LiteralArgumentBuilder.<ConsoleSource>literal("curriculum")
                                .then(RequiredArgumentBuilder.<ConsoleSource, String>argument("identifier", StringArgumentType.string())
                                        .suggests((context, builder) -> CompletableFuture.supplyAsync(() -> {
                                            builder.suggest("computing_power_recovery_speed_curriculum");
                                            builder.suggest("maximum_computing_power_curriculum");
                                            return builder.build();
                                        }))
                                        .executes(context -> CommandManager.learnCurriculum(context, StringArgumentType.getString(context, "identifier")))
                                )
                        )
        );
    }

    private static int executeHelpCommand(CommandContext<ConsoleSource> context) {
        Map<CommandNode<ConsoleSource>, String> map = dispatcher.getSmartUsage(dispatcher.getRoot(), context.getSource());
        HISTORY.addAll(map.values());
        return 1;
    }

    private static int fetchLearnedSkill(CommandContext<ConsoleSource> context) {
        Response response = new Response();
        response.runnable = () -> {
            for (Object o : response.dataList) {
                HISTORY.add((String) o);
            }
        };
        AcademyCraftNetworkSystemClient.CLIENT_RESPONSE_MAP.put(AcademyCraftNetworkResourceLocations.S2C_GET_LEARNED_SKILL_RESPONSE, response);
        AcademyCraftNetworkSystemClient.sendPacket(new C2SRequestPacket(AcademyCraftNetworkResourceLocations.C2S_GET_LEARNED_SKILL_REQUEST));
        return 1;
    }

    private static int fetchAllSkill(CommandContext<ConsoleSource> context) {
        Response response = new Response();
        response.runnable = () -> {
            for (Object o : response.dataList) {
                HISTORY.add((String) o);
            }
        };
        AcademyCraftNetworkSystemClient.CLIENT_RESPONSE_MAP.put(AcademyCraftNetworkResourceLocations.S2C_GET_ALL_SKILL_RESPONSE, response);
        AcademyCraftNetworkSystemClient.sendPacket(new C2SRequestPacket(AcademyCraftNetworkResourceLocations.C2S_GET_ALL_SKILL_REQUEST));
        return 1;
    }

    private static int learnSkill(CommandContext<ConsoleSource> context, String identifier) {
        Response response = new Response();
        response.runnable = () -> {
            if ((boolean) response.dataList.get(0)) {
                HISTORY.add("Success");

            } else {
                HISTORY.add("Fail," + response.dataList.get(1));
            }
        };
        AcademyCraftNetworkSystemClient.CLIENT_RESPONSE_MAP.put(AcademyCraftNetworkResourceLocations.S2C_LEARN_SKILL_RESPONSE, response);
        AcademyCraftNetworkSystemClient.sendPacket(new C2SRequestPacket(AcademyCraftNetworkResourceLocations.C2S_LEARN_SKILL_REQUEST));
        return 1;
    }

    private static int learnCurriculum(CommandContext<ConsoleSource> context, String identifier) {
        Response response = new Response();
        response.runnable = () -> {
            if ((boolean) response.dataList.get(0)) {
                HISTORY.add("Success");

            } else {
                HISTORY.add("Fail," + response.dataList.get(1));
            }
        };
        AcademyCraftNetworkSystemClient.CLIENT_RESPONSE_MAP.put(AcademyCraftNetworkResourceLocations.S2C_LEARN_CURRICULUM_RESPONSE, response);
        AcademyCraftNetworkSystemClient.sendPacket(new C2SRequestPacket(AcademyCraftNetworkResourceLocations.C2S_LEARN_CURRICULUM_REQUEST, FriendlyByteBufIdentifiers.STRING, identifier));
        return 1;
    }

    public static void executeCommand(String input, ConsoleSource source) {
        try {
            dispatcher.execute(input, source);
        } catch (CommandSyntaxException e) {
            HISTORY.add(e.getMessage());
        }
    }

    public static ParseResults<ConsoleSource> parse(String input, ConsoleSource source) {
        return dispatcher.parse(input, source);
    }
}
