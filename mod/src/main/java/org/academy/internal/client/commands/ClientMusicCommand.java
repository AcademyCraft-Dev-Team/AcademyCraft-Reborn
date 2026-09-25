package org.academy.internal.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import org.academy.internal.client.app.music.session.SharedAccountClient;

import java.util.List;
import java.util.Locale;

public final class ClientMusicCommand {
    private ClientMusicCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("academy")
                .then(Commands.literal("music")
                        .then(Commands.literal("account")
                                .then(Commands.literal("upload")
                                        .then(Commands.argument("provider", StringArgumentType.word())
                                                .suggests((_, builder) -> SharedSuggestionProvider.suggest(
                                                        List.of("qq", "netease"), builder))
                                                .executes(ClientMusicCommand::upload)))
                                .then(Commands.literal("status")
                                        .then(Commands.argument("provider", StringArgumentType.word())
                                                .suggests((_, builder) -> SharedSuggestionProvider.suggest(
                                                        List.of("qq", "netease"), builder))
                                                .executes(ClientMusicCommand::status))))));
    }

    private static int upload(CommandContext<CommandSourceStack> context) {
        var provider = StringArgumentType.getString(context, "provider").toLowerCase(Locale.ROOT);
        if (SharedAccountClient.INSTANCE.uploadLocalCredential(provider)) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "§e[AC Music]§r 已请求上传 " + provider + " 共享账号，结果见音乐播放器-设置"), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal(
                "§e[AC Music]§r 本地没有可上传的 " + provider + " 登录凭证，请先在音乐播放器中登录"));
        return 0;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        var provider = StringArgumentType.getString(context, "provider").toLowerCase(Locale.ROOT);
        SharedAccountClient.INSTANCE.queryStatus(provider);
        context.getSource().sendSuccess(() -> Component.literal(
                "§e[AC Music]§r 已查询 " + provider + " 共享账号状态，结果见音乐播放器-设置"), false);
        return 1;
    }
}
