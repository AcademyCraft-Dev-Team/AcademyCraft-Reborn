package org.academy.internal.server.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.academy.AcademyCraft;
import org.academy.internal.common.music.provider.MusicProviders;
import org.academy.internal.common.network.MusicAccountPackets;
import org.academy.internal.server.music.MusicRoomManager;
import org.academy.internal.server.music.PresetPlaylistStore;
import org.academy.internal.server.music.ServerJukeboxManager;
import org.academy.internal.server.music.SharedAccountService;
import org.misaka.MisakaNetworkServer;

import java.util.List;

/**
 * /academy music 命令组：音乐室子命令（创建/申请/邀请/同意/拒绝/退出/控制），
 * 后续全服点播与服主账号管理子命令也挂在本节点下喵。
 */
public final class MusicCommand {
    private MusicCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("music")
                .then(roomCommands())
                .then(jukeboxCommands())
                .then(accountCommands())
                .then(playlistCommands());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> accountCommands() {
        return Commands.literal("account")
                .then(Commands.literal("status")
                        .then(Commands.argument("provider", StringArgumentType.word())
                                .suggests((_, builder) -> SharedSuggestionProvider.suggest(
                                        List.of("qq", "netease"), builder))
                                .executes(MusicCommand::accountStatus)))
                .then(Commands.literal("clear")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("provider", StringArgumentType.word())
                                .suggests((_, builder) -> SharedSuggestionProvider.suggest(
                                        List.of("qq", "netease"), builder))
                                .executes(MusicCommand::accountClear)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> playlistCommands() {
        return Commands.literal("playlist")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("import")
                        .then(Commands.argument("provider", StringArgumentType.word())
                                .suggests((_, builder) -> SharedSuggestionProvider.suggest(
                                        List.of("qq", "netease"), builder))
                                .then(Commands.argument("playlist_id", StringArgumentType.word())
                                        .executes(MusicCommand::playlistImport))))
                .then(Commands.literal("list").executes(MusicCommand::playlistList))
                .then(Commands.literal("clear").executes(MusicCommand::playlistClear))
                .then(Commands.literal("enable").executes(ctx -> playlistSetEnabled(ctx, true)))
                .then(Commands.literal("disable").executes(ctx -> playlistSetEnabled(ctx, false)))
                .then(Commands.literal("mode")
                        .then(Commands.argument("value", StringArgumentType.word())
                                .suggests((_, builder) -> SharedSuggestionProvider.suggest(
                                        List.of("SEQUENTIAL", "SHUFFLE", "LOOP"), builder))
                                .executes(MusicCommand::playlistSetMode)))
                .then(Commands.literal("reload").executes(MusicCommand::playlistReload));
    }

    private static int accountStatus(CommandContext<CommandSourceStack> context) {
        var provider = StringArgumentType.getString(context, "provider").toLowerCase(java.util.Locale.ROOT);
        var status = SharedAccountService.status(provider);
        var source = context.getSource();
        if (!SharedAccountService.isEnabled()) {
            source.sendSuccess(() -> Component.translatable("message.academy.music_account.disabled"), false);
            return 0;
        }
        if (status == null) {
            source.sendSuccess(() -> Component.translatable(
                    "message.academy.music_account.none", provider), false);
            return 0;
        }
        var valid = status.valid();
        source.sendSuccess(() -> Component.translatable(
                "message.academy.music_account.status_line", provider, valid), false);
        return 1;
    }

    private static int accountClear(CommandContext<CommandSourceStack> context) {
        var provider = StringArgumentType.getString(context, "provider").toLowerCase(java.util.Locale.ROOT);
        SharedAccountService.clear(provider);
        context.getSource().sendSuccess(() -> Component.translatable(
                "message.academy.music_account.cleared", provider), true);
        return 1;
    }

    private static int playlistImport(CommandContext<CommandSourceStack> context) {
        var provider = StringArgumentType.getString(context, "provider").toLowerCase(java.util.Locale.ROOT);
        var playlistId = StringArgumentType.getString(context, "playlist_id");
        var source = context.getSource();
        var server = source.getServer();
        source.sendSuccess(() -> Component.translatable(
                "message.academy.playlist.importing", playlistId), false);
        java.util.concurrent.CompletableFuture
                .supplyAsync(() -> {
                    try {
                        var credential = SharedAccountService.credentialFor(provider);
                        if (credential == null) {
                            throw new java.io.IOException("no shared account for " + provider);
                        }
                        var api = MusicProviders.byName(provider).orElseThrow();
                        return api.importPlaylist(playlistId, credential);
                    } catch (java.io.IOException exception) {
                        throw new RuntimeException(exception);
                    }
                }, AcademyCraft.executorService)
                .whenComplete((entries, throwable) -> server.execute(() -> {
                    if (throwable != null) {
                        var message = rootMessage(throwable);
                        source.sendFailure(Component.translatable(
                                "message.academy.playlist.import_failed", message));
                        return;
                    }
                    var store = PresetPlaylistStore.getInstance();
                    store.save(store.isEnabled(), store.mode(), entries);
                    broadcastPlaylist(server);
                    var size = entries.size();
                    source.sendSuccess(() -> Component.translatable(
                            "message.academy.playlist.imported", size), true);
                }));
        return 1;
    }

    private static int playlistList(CommandContext<CommandSourceStack> context) {
        var store = PresetPlaylistStore.getInstance();
        var tracks = store.tracks();
        if (tracks.isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.translatable("message.academy.playlist.empty"), false);
            return 0;
        }
        var size = tracks.size();
        context.getSource().sendSuccess(() -> Component.translatable(
                "message.academy.playlist.list_header", size, store.mode(),
                store.isEnabled()), false);
        var preview = tracks.subList(0, Math.min(10, tracks.size()));
        for (var index = 0; index < preview.size(); index++) {
            final var display = (index + 1) + ". " + preview.get(index).displayText();
            context.getSource().sendSuccess(() -> Component.literal("  " + display), false);
        }
        return 1;
    }

    private static int playlistClear(CommandContext<CommandSourceStack> context) {
        var store = PresetPlaylistStore.getInstance();
        store.save(store.isEnabled(), store.mode(), List.of());
        broadcastPlaylist(context.getSource().getServer());
        context.getSource().sendSuccess(
                () -> Component.translatable("message.academy.playlist.cleared"), true);
        return 1;
    }

    private static int playlistSetEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        var store = PresetPlaylistStore.getInstance();
        store.save(enabled, store.mode(), store.tracks());
        broadcastPlaylist(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.translatable(enabled
                ? "message.academy.playlist.enabled"
                : "message.academy.playlist.disabled"), true);
        return 1;
    }

    private static int playlistSetMode(CommandContext<CommandSourceStack> context) {
        var raw = StringArgumentType.getString(context, "value").toUpperCase(java.util.Locale.ROOT);
        var store = PresetPlaylistStore.getInstance();
        try {
            var mode = PresetPlaylistStore.Mode.valueOf(raw);
            store.save(store.isEnabled(), mode, store.tracks());
            broadcastPlaylist(context.getSource().getServer());
            context.getSource().sendSuccess(() -> Component.translatable(
                    "message.academy.playlist.mode_set", mode.name()), true);
            return 1;
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal("Unknown mode: " + raw));
            return 0;
        }
    }

    private static int playlistReload(CommandContext<CommandSourceStack> context) {
        PresetPlaylistStore.getInstance().load();
        broadcastPlaylist(context.getSource().getServer());
        var size = PresetPlaylistStore.getInstance().tracks().size();
        context.getSource().sendSuccess(() -> Component.translatable(
                "message.academy.playlist.reloaded", size), true);
        return 1;
    }

    private static void broadcastPlaylist(net.minecraft.server.MinecraftServer server) {
        var store = PresetPlaylistStore.getInstance();
        var packet = new MusicAccountPackets.ServerPlaylistPacket(
                store.isEnabled(), store.mode().name(), store.tracks());
        for (var player : server.getPlayerList().getPlayers()) {
            MisakaNetworkServer.send(player, packet);
        }
    }

    private static String rootMessage(Throwable throwable) {
        var current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> jukeboxCommands() {
        return Commands.literal("jukebox")
                .then(Commands.literal("skip").executes(MusicCommand::voteSkip))
                .then(Commands.literal("toggle").executes(MusicCommand::toggleSubscribe))
                .then(Commands.literal("force_skip")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(MusicCommand::forceSkip))
                .then(Commands.literal("clear")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(MusicCommand::clearQueue));
    }

    private static int voteSkip(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerJukeboxManager.voteSkip(context.getSource().getPlayerOrException());
        return 1;
    }

    private static int toggleSubscribe(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var subscribed = !ServerJukeboxManager.isSubscribed(player.getUUID());
        ServerJukeboxManager.setSubscribed(player, subscribed);
        context.getSource().sendSuccess(() -> Component.translatable(subscribed
                ? "message.academy.jukebox.subscribed"
                : "message.academy.jukebox.unsubscribed"), false);
        return 1;
    }

    private static int forceSkip(CommandContext<CommandSourceStack> context) {
        ServerJukeboxManager.forceSkip();
        context.getSource().sendSuccess(() -> Component.literal("§e[AC Jukebox]§r Skipped."), true);
        return 1;
    }

    private static int clearQueue(CommandContext<CommandSourceStack> context) {
        ServerJukeboxManager.clearQueue();
        context.getSource().sendSuccess(() -> Component.literal("§e[AC Jukebox]§r Queue cleared."), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> roomCommands() {
        return Commands.literal("room")
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(MusicCommand::createRoom)))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(MusicCommand::renameRoom)))
                .then(Commands.literal("list").executes(MusicCommand::listRooms))
                .then(Commands.literal("apply")
                        .then(Commands.argument("code", StringArgumentType.word())
                                .executes(MusicCommand::applyRoom)))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(MusicCommand::invitePlayer)))
                .then(Commands.literal("accept")
                        .then(Commands.argument("token", StringArgumentType.word())
                                .executes(MusicCommand::acceptToken)))
                .then(Commands.literal("reject")
                        .then(Commands.argument("token", StringArgumentType.word())
                                .executes(MusicCommand::rejectToken)))
                .then(Commands.literal("leave").executes(MusicCommand::leaveRoom))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(MusicCommand::kickMember)))
                .then(Commands.literal("pause").executes(MusicCommand::pausePlayback))
                .then(Commands.literal("resume").executes(MusicCommand::resumePlayback))
                .then(Commands.literal("next").executes(MusicCommand::nextTrack))
                .then(Commands.literal("previous").executes(MusicCommand::previousTrack))
                .then(Commands.literal("seek")
                        .then(Commands.argument("seconds", FloatArgumentType.floatArg(0.0f))
                                .executes(MusicCommand::seekPlayback)));
    }

    private static int createRoom(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MusicRoomManager.create(
                context.getSource().getPlayerOrException(),
                StringArgumentType.getString(context, "name")
        );
        return 1;
    }

    private static int renameRoom(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MusicRoomManager.rename(
                context.getSource().getPlayerOrException(),
                StringArgumentType.getString(context, "name")
        );
        return 1;
    }

    private static int listRooms(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MusicRoomManager.sendListTo(context.getSource().getPlayerOrException());
        return 1;
    }

    private static int applyRoom(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MusicRoomManager.apply(
                context.getSource().getPlayerOrException(),
                StringArgumentType.getString(context, "code")
        );
        return 1;
    }

    private static int invitePlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MusicRoomManager.invite(
                context.getSource().getPlayerOrException(),
                EntityArgument.getPlayer(context, "player").getGameProfile().name()
        );
        return 1;
    }

    private static int acceptToken(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MusicRoomManager.accept(
                context.getSource().getPlayerOrException(),
                StringArgumentType.getString(context, "token")
        );
        return 1;
    }

    private static int rejectToken(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MusicRoomManager.reject(
                context.getSource().getPlayerOrException(),
                StringArgumentType.getString(context, "token")
        );
        return 1;
    }

    private static int leaveRoom(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MusicRoomManager.leave(context.getSource().getPlayerOrException());
        return 1;
    }

    private static int kickMember(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MusicRoomManager.kick(
                context.getSource().getPlayerOrException(),
                EntityArgument.getPlayer(context, "player").getGameProfile().name()
        );
        return 1;
    }

    private static int pausePlayback(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MusicRoomManager.pause(context.getSource().getPlayerOrException());
        return 1;
    }

    private static int resumePlayback(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MusicRoomManager.resume(context.getSource().getPlayerOrException());
        return 1;
    }

    private static int nextTrack(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MusicRoomManager.next(context.getSource().getPlayerOrException());
        return 1;
    }

    private static int previousTrack(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MusicRoomManager.previous(context.getSource().getPlayerOrException());
        return 1;
    }

    private static int seekPlayback(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MusicRoomManager.seek(
                context.getSource().getPlayerOrException(),
                FloatArgumentType.getFloat(context, "seconds")
        );
        return 1;
    }
}
