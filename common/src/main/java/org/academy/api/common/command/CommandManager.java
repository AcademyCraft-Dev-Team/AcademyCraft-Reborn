package org.academy.api.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import io.netty.buffer.Unpooled;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.AbilitySystem;
import org.academy.AbilitySystemServer;
import org.academy.AcademyCraft;
import org.academy.api.client.network.AcademyCraftNetworkSystemClient;
import org.academy.api.client.network.ServerToClientPacketHandler;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.academy.api.common.network.packet.ClientToServerPacket;
import org.academy.api.common.network.packet.ServerToClientPacket;
import org.academy.api.server.network.AcademyCraftNetworkSystemServer;
import org.academy.internal.client.ui.AbilityDeveloperFragment;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class CommandManager {
    public static final class Client {
        public static final List<String> HISTORY = new CopyOnWriteArrayList<>();
        public static final CommandDispatcher<ConsoleSource> dispatcher = new CommandDispatcher<>();
        public static final LiteralArgumentBuilder<ConsoleSource> CONFIG = LiteralArgumentBuilder.literal("config");

        public static void registerCommands() {
            dispatcher.register(CONFIG);

            if (AcademyCraft.DEBUG_MODE) {
                dispatcher.register(
                        LiteralArgumentBuilder.<ConsoleSource>literal("debug")
                                .then(LiteralArgumentBuilder.<ConsoleSource>literal("category")
                                        .then(RequiredArgumentBuilder.<ConsoleSource, String>argument("category", StringArgumentType.string())
                                                .executes(DebugCommand::changeCategory)
                                        )
                                )
                );
            }

            dispatcher.register(
                    LiteralArgumentBuilder.<ConsoleSource>literal("help")
                            .executes(CommandManager.Client::help)
            );

            dispatcher.register(
                    LiteralArgumentBuilder.<ConsoleSource>literal("info")
                            .executes(CommandManager.Client::info)
            );

            dispatcher.register(
                    LiteralArgumentBuilder.<ConsoleSource>literal("skill_list")
                            .then(LiteralArgumentBuilder.<ConsoleSource>literal("all")
                                    .executes(CommandManager.Client::fetchAllSkill)
                            )
                            .then(LiteralArgumentBuilder.<ConsoleSource>literal("learned")
                                    .executes(CommandManager.Client::fetchLearnedSkill)
                            )
            );

            dispatcher.register(
                    LiteralArgumentBuilder.<ConsoleSource>literal("learn")
                            .then(LiteralArgumentBuilder.<ConsoleSource>literal("skill")
                                    .then(RequiredArgumentBuilder.<ConsoleSource, String>argument("identifier", StringArgumentType.string())
                                            .executes(context -> CommandManager.Client.learnSkill(context, StringArgumentType.getString(context, "identifier")))
                                    )
                            )
                            .then(LiteralArgumentBuilder.<ConsoleSource>literal("curriculum")
                                    .then(RequiredArgumentBuilder.<ConsoleSource, String>argument("identifier", StringArgumentType.string())
                                            .suggests((context, builder) -> CompletableFuture.supplyAsync(() -> {
                                                builder.suggest("computing_power_recovery_speed_curriculum");
                                                builder.suggest("maximum_computing_power_curriculum");
                                                return builder.build();
                                            }))
                                            .executes(context -> CommandManager.Client.learnCurriculum(context, StringArgumentType.getString(context, "identifier")))
                                    )
                            )
            );
        }

        @SuppressWarnings("unchecked")
        public static void registerPacketHandler() {
            AcademyCraftNetworkSystemClient.registerServerToClientPacketHandler(
                    AcademyCraftNetworkResourceLocations.S2C_LEARN_SKILL_PACKET, new ServerToClientPacketHandler() {
                        @Override
                        public void handle(@NotNull ClientPacketListener listener, @NotNull ServerToClientPacket packet) {
                        }
                    }
            );
            AcademyCraftNetworkSystemClient.registerServerToClientPacketHandler(
                    AcademyCraftNetworkResourceLocations.S2C_FETCH_ALL_SKILL_PACKET, ArrayList.class, list -> {
                        ArrayList<Skill> skills = (ArrayList<Skill>) list;
                        for (Skill skill : skills) {
                            AbilityDeveloperFragment.addHistory(skill.name);
                        }
                    }
            );
        }

        public static final class DebugCommand {
            public static int changeCategory(CommandContext<ConsoleSource> context) {
                AcademyCraftNetworkSystemClient.sendPacket(new ClientToServerPacket(AcademyCraftNetworkResourceLocations.C2S_DEBUG_CHANGE_CATEGORY_PACKET, new FriendlyByteBuf(Unpooled.buffer()).writeUtf(StringArgumentType.getString(context, "category"))));
                return 1;
            }
        }

        private static int info(CommandContext<ConsoleSource> context) {
            return 1;
        }

        private static int help(CommandContext<ConsoleSource> context) {
            Map<CommandNode<ConsoleSource>, String> map = dispatcher.getSmartUsage(dispatcher.getRoot(), context.getSource());
            AbilityDeveloperFragment.addHistory(map.values());
            return 1;
        }

        private static int fetchLearnedSkill(CommandContext<ConsoleSource> context) {
            return 1;
        }

        private static int fetchAllSkill(CommandContext<ConsoleSource> context) {
            AcademyCraftNetworkSystemClient.sendPacket(new ClientToServerPacket(AcademyCraftNetworkResourceLocations.C2S_FETCH_ALL_SKILL_PACKET));
            return 1;
        }

        private static int learnSkill(CommandContext<ConsoleSource> context, String identifier) {
            AcademyCraftNetworkSystemClient.sendPacket(new ClientToServerPacket(
                    AcademyCraftNetworkResourceLocations.C2S_LEARN_SKILL_REQUEST, identifier, context.getSource().mainPos)
            );
            return 1;
        }

        private static int learnCurriculum(CommandContext<ConsoleSource> context, String identifier) {
            return 1;
        }

        public static void executeCommand(String input, ConsoleSource source) {
            try {
                dispatcher.execute(input, source);
            } catch (CommandSyntaxException e) {
                AbilityDeveloperFragment.addHistory(e.getMessage());
            }
        }
    }

    public static final class Server {
        public static void registerPacketHandler() {
            AcademyCraftNetworkSystemServer.registerClientToServerPacketHandler(
                    AcademyCraftNetworkResourceLocations.C2S_DEBUG_CHANGE_CATEGORY_PACKET,
                    (serverPacketListener, packet) ->
                            AbilitySystemServer.setPlayerAbilityCategory(serverPacketListener.player.getUUID(),
                                    FriendlyByteBufDeserializers.ABILITY_CATEGORY_FRIENDLY_BYTE_BUF_DESERIALIZER
                                            .deserialize(packet.friendlyByteBuf))
            );
            AcademyCraftNetworkSystemServer.registerClientToServerPacketHandler(
                    AcademyCraftNetworkResourceLocations.C2S_LEARN_SKILL_PACKET,
                    (serverPacketListener, packet) -> {

                    }
            );
            AcademyCraftNetworkSystemServer.registerClientToServerPacketHandler(
                    AcademyCraftNetworkResourceLocations.C2S_FETCH_ALL_SKILL_PACKET,
                    (listener, packet) -> {
                        List<Skill> skills = new ArrayList<>(AbilitySystem.SKILL_MAP.values());
                        listener.send(new ServerToClientPacket(
                                AcademyCraftNetworkResourceLocations.S2C_FETCH_ALL_SKILL_PACKET, skills)
                        );
                    }
            );
        }
    }
}