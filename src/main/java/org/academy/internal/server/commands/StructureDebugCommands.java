package org.academy.internal.server.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import org.academy.api.common.structure.StructurePhysics;
import org.academy.internal.common.structure.NativeStructureRuntime;

/** Administrative inspection and safe cleanup of native structure projections. */
final class StructureDebugCommands {
    private StructureDebugCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("structure")
                .then(Commands.literal("status").executes(StructureDebugCommands::status))
                .then(Commands.literal("cleanup").executes(StructureDebugCommands::cleanup));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        var server = context.getSource().getServer();
        var runtime = NativeStructureRuntime.existing(server).orElse(null);
        if (runtime == null) {
            context.getSource().sendFailure(Component.literal("No native structure runtime is active."));
            return 0;
        }
        var references = runtime.loadedReferences();
        var attached = 0;
        var orphaned = 0;
        var sample = new StringBuilder();
        for (var reference : references) {
            var host = server.getLevel(ResourceKey.create(Registries.DIMENSION, reference.dimension()));
            if (host == null) continue;
            var hasRegion = StructurePhysics.attachedRegion(host, reference).isPresent()
                    || StructurePhysics.activeRegion(host, reference).isPresent();
            if (hasRegion) attached++;
            else {
                orphaned++;
                if (sample.length() < 400) sample.append('\n').append(reference.id());
            }
        }
        final var attachedCount = attached;
        final var orphanedCount = orphaned;
        final var details = sample.toString();
        context.getSource().sendSuccess(() -> Component.literal(
                "[AC Structure] loaded=" + references.size() + " attached=" + attachedCount
                        + " orphaned=" + orphanedCount
                        + (details.isEmpty() ? "" : "\norphaned ids:" + details)), false);
        return 1;
    }

    private static int cleanup(CommandContext<CommandSourceStack> context) {
        var server = context.getSource().getServer();
        var runtime = NativeStructureRuntime.existing(server).orElse(null);
        if (runtime == null) {
            context.getSource().sendFailure(Component.literal("No native structure runtime is active."));
            return 0;
        }
        var removed = 0;
        var failed = 0;
        for (var reference : runtime.loadedReferences()) {
            var host = server.getLevel(ResourceKey.create(Registries.DIMENSION, reference.dimension()));
            if (host == null) {
                failed++;
                continue;
            }
            if (StructurePhysics.attachedRegion(host, reference).isPresent()
                    || StructurePhysics.activeRegion(host, reference).isPresent()) continue;
            if (runtime.unload(reference)) removed++;
            else failed++;
        }
        final var removedCount = removed;
        final var failedCount = failed;
        context.getSource().sendSuccess(() -> Component.literal(
                "[AC Structure] unloaded orphan projections=" + removedCount
                        + " failed=" + failedCount + "; stored blocks were retained."), true);
        return removed + failed == 0 ? 0 : 1;
    }
}
