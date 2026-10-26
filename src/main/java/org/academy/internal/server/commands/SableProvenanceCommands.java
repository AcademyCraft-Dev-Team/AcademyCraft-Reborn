package org.academy.internal.server.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.sable.SableProvenanceLifecycle;
import org.jspecify.annotations.Nullable;

/** Read-only administrative view. Deliberately provides no force-delete or repair command. */
final class SableProvenanceCommands {
    private SableProvenanceCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("sable_provenance")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> report(context, null))
                .then(Commands.argument("id", UuidArgument.uuid()).executes(context -> report(context,
                        new StructureRef(UuidArgument.getUuid(context, "id"), context.getSource().getLevel().dimension().identifier(),
                                StructureBackendRouter.SABLE_ID, 0))));
    }

    private static int report(CommandContext<CommandSourceStack> context, @Nullable StructureRef target) {
        var source = context.getSource();
        var snapshot = SableProvenanceLifecycle.diagnostics(source.getServer(), target);
        source.sendSuccess(() -> Component.literal("[AC Sable provenance] backend=" + snapshot.backend()
                + " state=" + snapshot.state() + " loaded_pending=" + snapshot.pendingCount()
                + " problems=" + snapshot.problemCount()
                + (target == null ? " (pending sample <= 10, recent <= 16)" : " dimension=" + target.dimension() + " id=" + target.id())), false);
        for (var problem : snapshot.problems()) source.sendSuccess(() -> Component.literal(
                "problem file=" + problem.file() + " reason=" + problem.reason()), false);
        for (var observation : snapshot.pending()) report(source, "pending", observation);
        for (var observation : snapshot.recent()) report(source, "recent", observation);
        if (target != null && snapshot.pending().isEmpty() && snapshot.recent().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No observed retirement for this identity; absence is not deletion proof."), false);
        }
        return 1;
    }

    private static void report(CommandSourceStack source, String kind, SableProvenanceLifecycle.Observation observation) {
        source.sendSuccess(() -> Component.literal(kind + " dimension=" + observation.reference().dimension()
                + " id=" + observation.reference().id() + " status=" + observation.status()
                + " attempts=" + observation.attempts() + " last_tick=" + observation.lastTick()
                + " next_tick=" + observation.nextTick()), false);
    }
}
