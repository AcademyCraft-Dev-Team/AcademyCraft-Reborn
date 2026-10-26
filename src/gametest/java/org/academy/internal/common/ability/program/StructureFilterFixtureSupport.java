package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.structure.*;
import org.academy.internal.common.ability.program.registry.*;
import org.academy.internal.common.structure.space.StructureBlockSummaries;

import java.util.*;

/** Loaded registries, real plots, tags and provenance on both physical backends. */
public final class StructureFilterFixtureSupport {
    private StructureFilterFixtureSupport() {}

    public static void verify(ServerPlayer player, ProgramTargetResolver resolver, ProgramStructureReference target, ProgramStructureReference neighbor) {
        check(resolver.isStructureValid(target) && resolver.isStructureValid(neighbor), "validity filter lost loaded bodies");
        verifyUnavailable(new ServerProgramTargetResolver(player, 1, 128), target);
        var pure = resolver.structureContents(target).orElseThrow();
        var mixed = resolver.structureContents(neighbor).orElseThrow();
        check(pure.counts().equals(Map.of(Blocks.IRON_BLOCK.defaultBlockState(), 1)), "pure summary: " + pure);
        check(mixed.counts().equals(Map.of(Blocks.IRON_BLOCK.defaultBlockState(), 1, Blocks.OAK_LOG.defaultBlockState(), 1)), "mixed summary: " + mixed);
        check(matches(pure, "minecraft:iron_block", "all") && !matches(mixed, "minecraft:iron_block", "all"), "ALL ignored mixed cells");
        check(matches(mixed, "minecraft:iron_block", "any"), "ANY lost mixed body");
        check(matches(mixed, "minecraft:iron_block,#minecraft:logs", "all"), "block + tag union failed");
        check(matches(mixed, "#minecraft:logs", "any") && !matches(pure, "#minecraft:logs", "any"), "loaded block tag failed");
        check(!matches(mixed, "academy:missing_block", "any") && !matches(mixed, "#academy:missing_tag", "any"), "unknown selector matched");
        check(StructureSpaces.resolve(player.level().getServer(), neighbor.reference()).orElseThrow().world().summarize(1).isEmpty(), "summary returned partial budget match");
        var mine = resolver.structureSource(target).orElseThrow();
        check(mine.ownedByCaster() && mine.metadata().isPresent(), "skill extraction lost attribution");
        check(resolver.structureSource(neighbor).orElseThrow().metadata().isEmpty(), "generic capture was marked");
        var skill = mine.metadata().orElseThrow().skillId().toString();
        var config = new JsonObject(); config.addProperty("source_mode", "skill"); config.addProperty("skill", skill);
        check(filter(CommonProgramNodeIds.FILTER_STRUCTURE_SOURCE, config, List.of(neighbor, target), resolver).equals(List.of(target)), "source skill filter failed");
        config = new JsonObject(); config.addProperty("selectors", "minecraft:iron_block"); config.addProperty("match", "all");
        check(filter(CommonProgramNodeIds.FILTER_STRUCTURE_BLOCKS, config, List.of(neighbor, target, target), resolver).equals(List.of(target)), "block filter did not deduplicate or excluded wrong body");
        config = new JsonObject(); config.addProperty("source_mode", "unmarked");
        check(filter(CommonProgramNodeIds.FILTER_STRUCTURE_SOURCE, config, List.of(target, neighbor), resolver).equals(List.of(neighbor)), "unmarked filter failed");
        // A complete scan includes non-colliding decorations and fluid cells, while omitting air gaps.
        var states = List.of(Blocks.IRON_BLOCK.defaultBlockState(), Blocks.AIR.defaultBlockState(), Blocks.WATER.defaultBlockState(), Blocks.TORCH.defaultBlockState());
        var summary = StructureBlockSummaries.scan(BlockPos.ZERO, new BlockPos(3, 0, 0), 4,
                pos -> Optional.of(states.get(pos.getX()))).orElseThrow();
        check(summary.scannedCells() == 4 && summary.counts().size() == 3 && !matches(summary, "minecraft:iron_block", "all"), "non-collision cells omitted");
        check(StructureBlockSummaries.scan(BlockPos.ZERO, new BlockPos(1, 0, 0), 2,
                pos -> pos.getX() == 0 ? Optional.of(states.getFirst()) : Optional.empty()).isEmpty(), "partial matching summary escaped");
    }

    public static void verifyUnavailable(ProgramTargetResolver resolver, ProgramStructureReference reference) {
        check(!resolver.isStructureValid(reference) && resolver.structureContents(reference).isEmpty() && resolver.structureSource(reference).isEmpty(), "unavailable reference reported usable");
        var config = new JsonObject(); config.addProperty("source_mode", "unmarked");
        check(filter(CommonProgramNodeIds.FILTER_STRUCTURE_SOURCE, config, List.of(reference), resolver).isEmpty(), "unknown provenance treated as unmarked");
    }

    private static List<?> filter(net.minecraft.resources.Identifier id, JsonObject config, List<ProgramStructureReference> values, ProgramTargetResolver resolver) {
        @SuppressWarnings("unchecked") var executor = (ProgramNodeExecutor<Object>) CommonProgramExecutors.INSTANCE.find(id);
        var parsed = CommonProgramNodeCatalog.INSTANCE.find(id).configurationCodec().parse(JsonOps.INSTANCE, config).getOrThrow();
        return (List<?>) executor.execute(new ProgramVmContext(0, new HashMap<>(), new HashMap<>(), resolver), parsed,
                new ProgramInputView(Map.of("structures", List.of(new ProgramValue<>(ProgramValueTypes.STRUCTURE_SET, values)))))
                .outputs().get("structures").value();
    }
    private static boolean matches(StructureBlockSummary summary, String selectors, String mode) { return new ProgramStructureFilters.Blocks(selectors, mode).matches(summary); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
