package org.academy.api.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.AcademyCraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.network.ServerToClientPacketHandler;
import org.academy.api.common.ability.AbilitySystem;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.academy.api.common.network.NetworkResourceLocations;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.internal.client.render.ui.AbilityDeveloperFragment;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CommandManager {
    public static final class Client {
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
                            .executes(Client::help)
            );

            dispatcher.register(
                    LiteralArgumentBuilder.<ConsoleSource>literal("info")
                            .executes(Client::info)
            );

            dispatcher.register(
                    LiteralArgumentBuilder.<ConsoleSource>literal("skill_list")
                            .then(LiteralArgumentBuilder.<ConsoleSource>literal("all")
                                    .executes(Client::fetchAllSkill)
                            )
                            .then(LiteralArgumentBuilder.<ConsoleSource>literal("learned")
                                    .executes(Client::fetchLearnedSkill)
                            )
            );

            dispatcher.register(
                    LiteralArgumentBuilder.<ConsoleSource>literal("learn")
                            .then(LiteralArgumentBuilder.<ConsoleSource>literal("skill")
                                    .then(RequiredArgumentBuilder.<ConsoleSource, String>argument("name", StringArgumentType.string())
                                            .suggests((commandContext, builder) ->
                                                    CompletableFuture.supplyAsync(() -> {
                                                        if (AbilitySystemClient.getCategory() != null) {
                                                            for (Skill skill : AbilitySystemClient.getCategory().skillList) {
                                                                builder.suggest(skill.name);
                                                            }
                                                        }
                                                        return builder.build();
                                                    }))
                                            .executes(context ->
                                                    Client.learnSkill(context, context.getArgument(
                                                            "name", String.class))
                                            )
                                    )
                            )
                            .then(LiteralArgumentBuilder.<ConsoleSource>literal("curriculum")
                                    .then(RequiredArgumentBuilder.<ConsoleSource, String>argument("identifier", StringArgumentType.string())
                                            .suggests((context, builder) -> CompletableFuture.supplyAsync(() -> {
                                                builder.suggest("computing_power_recovery_speed_curriculum");
                                                builder.suggest("maximum_computing_power_curriculum");
                                                return builder.build();
                                            }))
                                            .executes(context -> Client.learnCurriculum(context, StringArgumentType.getString(context, "identifier")))
                                    )
                            )
            );
        }

        @SuppressWarnings("unchecked")
        public static void registerPacketHandler() {
            NetworkSystemClient.registerServerToClientPacketHandler(
                    NetworkResourceLocations.S2C_LEARN_SKILL_PACKET, new ServerToClientPacketHandler() {
                        @Override
                        public void handle(@NotNull ClientPacketListener listener, @NotNull S2CPacket packet) {
                        }
                    }
            );
            NetworkSystemClient.registerServerToClientPacketHandler(
                    NetworkResourceLocations.S2C_FETCH_ALL_SKILL_PACKET, ArrayList.class, list -> {
                        ArrayList<Skill> skills = (ArrayList<Skill>) list;
                        for (Skill skill : skills) {
                            AbilityDeveloperFragment.addHistory(skill.name);
                        }
                    }
            );
            NetworkSystemClient.registerServerToClientPacketHandler(
                    NetworkResourceLocations.S2C_INFO_PACKET, new ServerToClientPacketHandler() {
                        @Override
                        public void handle(@NotNull ClientPacketListener listener, @NotNull S2CPacket packet) {

                        }
                    }
            );
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
            NetworkSystemClient.sendPacket(new C2SPacket(
                    NetworkResourceLocations.C2S_FETCH_ALL_SKILL_PACKET)
            );
            return 1;
        }

        private static int learnSkill(CommandContext<ConsoleSource> context, String skillName) {
            Minecraft.getInstance().execute(() -> NetworkSystemClient.sendPacket(new C2SPacket(
                    NetworkResourceLocations.C2S_LEARN_SKILL_PACKET, skillName, new BlockPos(context.getSource().mainPos))
            ));
            return 1;
        }

        private static int learnCurriculum(CommandContext<ConsoleSource> context, String identifier) {
            AcademyCraft.LOGGER.info("Learned curriculum: " + identifier);
            return 1;
        }

        public static void executeCommand(String input, ConsoleSource source) {
            try {
                dispatcher.execute(input, source);
            } catch (CommandSyntaxException e) {
                AbilityDeveloperFragment.addHistory(e.getMessage());
            }
        }

        public static final class DebugCommand {
            public static int changeCategory(CommandContext<ConsoleSource> context) {
                NetworkSystemClient.sendPacket(new C2SPacket(NetworkResourceLocations.C2S_DEBUG_CHANGE_CATEGORY_PACKET, new FriendlyByteBuf(Unpooled.buffer()).writeUtf(StringArgumentType.getString(context, "category"))));
                return 1;
            }
        }
    }

    public static final class Server {
        public static void registerPacketHandler() {
            NetworkSystemServer.registerC2SPacketHandler(
                    NetworkResourceLocations.C2S_DEBUG_CHANGE_CATEGORY_PACKET,
                    (serverPacketListener, packet) ->
                            AbilitySystemServer.setPlayerAbilityCategory(serverPacketListener.player.getUUID(),
                                    FriendlyByteBufDeserializers.ABILITY_CATEGORY_FRIENDLY_BYTE_BUF_DESERIALIZER
                                            .deserialize(packet.friendlyByteBuf))
            );
            NetworkSystemServer.registerC2SPacketHandler(
                    NetworkResourceLocations.C2S_FETCH_ALL_SKILL_PACKET,
                    (listener, packet) -> {
                        List<Skill> skills = new ArrayList<>(AbilitySystem.SKILL_MAP.values());
                        listener.send(new S2CPacket(
                                NetworkResourceLocations.S2C_FETCH_ALL_SKILL_PACKET, skills)
                        );
                    }
            );
        }
    }
}