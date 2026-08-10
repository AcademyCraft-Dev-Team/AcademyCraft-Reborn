package org.academy.internal.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.academy.api.common.profiler.AcademyProfiler;
import org.academy.api.common.profiler.ProfileDump;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class ClientProfileCommand {
    private ClientProfileCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("academy").then(profileCommand()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> profileCommand() {
        var start = Commands.literal("start")
                .executes(ctx -> start(ctx, 1))
                .then(Commands.argument("interval_ms", IntegerArgumentType.integer(1, 1000))
                        .executes(ctx -> start(ctx, IntegerArgumentType.getInteger(ctx, "interval_ms"))));
        return Commands.literal("profile")
                .then(start)
                .then(Commands.literal("stop").executes(ClientProfileCommand::stop))
                .then(Commands.literal("reset").executes(ClientProfileCommand::reset))
                .then(Commands.literal("status").executes(ClientProfileCommand::status))
                .then(zonesCommand())
                .then(samplerCommand())
                .then(Commands.literal("dump").executes(ClientProfileCommand::dump));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> zonesCommand() {
        var depth = Commands.argument("depth", IntegerArgumentType.integer(1, 20))
                .executes(ctx -> zones(ctx, StringArgumentType.getString(ctx, "thread"), IntegerArgumentType.getInteger(ctx, "depth")));
        var thread = Commands.argument("thread", StringArgumentType.word())
                .executes(ctx -> zones(ctx, StringArgumentType.getString(ctx, "thread"), 8))
                .then(depth);
        return Commands.literal("zones")
                .executes(ctx -> zones(ctx, null, 8))
                .then(thread);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> samplerCommand() {
        return Commands.literal("sampler")
                .executes(ctx -> sampler(ctx, 30))
                .then(Commands.argument("top", IntegerArgumentType.integer(1, 200))
                        .executes(ctx -> sampler(ctx, IntegerArgumentType.getInteger(ctx, "top"))));
    }

    private static int start(CommandContext<CommandSourceStack> ctx, int intervalMs) {
        AcademyProfiler.registerThread(Thread.currentThread());
        AcademyProfiler.startSampling(intervalMs * 1000L);
        AcademyProfiler.startZoneCapture();
        send(ctx, "§e[AC Profiler]§r Sampling started (interval " + intervalMs + " ms) + zone capture on.");
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        AcademyProfiler.stopSampling();
        AcademyProfiler.stopZoneCapture();
        send(ctx, "§e[AC Profiler]§r Stopped.");
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        AcademyProfiler.resetSampling();
        AcademyProfiler.resetZones();
        send(ctx, "§e[AC Profiler]§r Data cleared.");
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        send(ctx, ProfileDump.status(AcademyProfiler.snapshot()));
        return 1;
    }

    private static int zones(CommandContext<CommandSourceStack> ctx, String thread, int depth) {
        send(ctx, ProfileDump.zonesText(AcademyProfiler.snapshot(), thread, depth));
        return 1;
    }

    private static int sampler(CommandContext<CommandSourceStack> ctx, int top) {
        send(ctx, ProfileDump.samplerText(AcademyProfiler.snapshot(), top));
        return 1;
    }

    private static int dump(CommandContext<CommandSourceStack> ctx) {
        var snapshot = AcademyProfiler.snapshot();
        var logsDir = new File(Minecraft.getInstance().gameDirectory, "logs");
        logsDir.mkdirs();
        var file = new File(logsDir, "academy-profile-" + ProfileDump.timestamp() + ".txt");

        var sb = new StringBuilder();
        sb.append("AcademyCraft Performance Profile (client)\n");
        sb.append("Time: ").append(ProfileDump.timestamp()).append("\n\n");
        var sampler = snapshot.getSampler();
        if (sampler != null) {
            sb.append(ProfileDump.dumpSampler(sampler, 30)).append("\n\n");
        }
        for (var entry : snapshot.getZones().entrySet()) {
            sb.append(ProfileDump.dumpZones(entry.getValue(), 8)).append("\n\n");
        }
        try {
            Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
            send(ctx, "§e[AC Profiler]§r Dumped to " + file.getAbsolutePath());
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("§e[AC Profiler]§r Failed to write: " + e.getMessage()));
        }
        return 1;
    }

    private static void send(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
    }
}
