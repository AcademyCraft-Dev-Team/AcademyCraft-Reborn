package org.academy.api.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.network.packet.C2SRequestPacket;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * You can register command.
 */
public class CommandManager {
    public static final List<String> HISTORY = Collections.synchronizedList(new ArrayList<>());
    public static final CommandDispatcher<ConsoleSource> dispatcher = new CommandDispatcher<>();

    static {
        registerCommands();
    }

    private static void registerCommands() {
        dispatcher.register(
                LiteralArgumentBuilder.<ConsoleSource>literal("help")
                        .executes(CommandManager::helpCommand)
        );

        dispatcher.register(
                LiteralArgumentBuilder.<ConsoleSource>literal("skill_list")
                        .executes(CommandManager::skillList)
        );

        dispatcher.register(
                LiteralArgumentBuilder.<ConsoleSource>literal("learn")
                        .then(RequiredArgumentBuilder.<ConsoleSource, String>argument("name", StringArgumentType.word())
                                .executes(CommandManager::learnSkill))
        );
    }

    private static int helpCommand(CommandContext<ConsoleSource> context) {
        Map<CommandNode<ConsoleSource>, String> map = dispatcher.getSmartUsage(dispatcher.getRoot(), context.getSource());
        HISTORY.addAll(map.values());
        return 1;
    }

    private static int skillList(CommandContext<ConsoleSource> context) {
        Response response = new Response();
        response.runnable = () -> {
            for (Object o : response.dataList) {
                HISTORY.add((String) o);
            }
        };
        NetworkSystemClient.CLIENT_RESPONSE_MAP.put(AcademyCraftNetworkResourceLocations.S2C_GET_SKILL_LIST_RESPONSE, response);
        NetworkSystemClient.sendPacket(new C2SRequestPacket(AcademyCraftNetworkResourceLocations.C2S_GET_SKILL_LIST_REQUEST));
        return 1;
    }

    private static int learnSkill(CommandContext<ConsoleSource> context) {
        Response response = new Response();
        response.runnable = () -> {
            if ((boolean) response.dataList.get(0)) {
                HISTORY.add("Success");

            } else {
                HISTORY.add("Fail," + response.dataList.get(1));
            }
        };
        NetworkSystemClient.CLIENT_RESPONSE_MAP.put(AcademyCraftNetworkResourceLocations.S2C_LEARN_SKILL_RESPONSE, response);
        NetworkSystemClient.sendPacket(new C2SRequestPacket(AcademyCraftNetworkResourceLocations.C2S_LEARN_SKILL_REQUEST));
        return 1;
    }

    public static void executeCommand(String input, ConsoleSource source) {
        try {
            dispatcher.execute(input, source);
        } catch (CommandSyntaxException e) {
            HISTORY.add(e.getMessage());
        }
    }
}
