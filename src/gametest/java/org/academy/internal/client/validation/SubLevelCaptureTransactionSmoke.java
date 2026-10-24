package org.academy.internal.client.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureCaptureResult;
import org.academy.api.common.structure.StructureTransactions;
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.sublevel.SubLevelServer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises permission revalidation, reentrant claims and source mutation after target copy. */
final class SubLevelCaptureTransactionSmoke {
    private SubLevelCaptureTransactionSmoke() {}

    static void run(MinecraftServer server) {
        var host = server.overworld();
        var runtime = SubLevelServer.runtime(host).orElseThrow();
        var source = new BlockPos(1216, host.getMaxY() - 32, 1216);
        host.getChunkAt(source);
        int plots = runtime.storage().allocated();
        int bodies = runtime.universe().activeCount();
        host.setBlock(source, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        var calls = new AtomicInteger();
        var revoked = StructureTransactions.capture(host, new StructureCaptureRequest(List.of(source), 1, 1,
                (level, position) -> calls.incrementAndGet() == 1, true));
        require(revoked.status() == StructureCaptureResult.Status.PERMISSION_DENIED, "revoked permission accepted");
        require(host.getBlockState(source).is(Blocks.IRON_BLOCK), "revoked permission removed source");
        require(runtime.storage().allocated() == plots && runtime.universe().activeCount() == bodies,
                "revoked permission leaked target");

        calls.set(0);
        var changed = StructureTransactions.capture(host, new StructureCaptureRequest(List.of(source), 1, 1,
                (level, position) -> {
                    if (calls.incrementAndGet() == 2) level.setBlock(position, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
                    return true;
                }, true));
        require(changed.status() == StructureCaptureResult.Status.SOURCE_CHANGED, "changed source was committed: " + changed);
        require(host.getBlockState(source).is(Blocks.DIAMOND_BLOCK), "rollback overwrote a later source edit");
        require(runtime.storage().allocated() == plots && runtime.universe().activeCount() == bodies,
                "rollback leaked prepared physics or plot");

        host.setBlock(source, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        calls.set(0);
        var conflict = new AtomicReference<StructureCaptureResult>();
        var claimed = StructureTransactions.capture(host, new StructureCaptureRequest(List.of(source), 1, 1,
                (level, position) -> {
                    if (calls.incrementAndGet() == 2) conflict.set(StructureTransactions.capture(level,
                            new StructureCaptureRequest(List.of(position), 1, 1, StructureCaptureRequest.ALLOW, true)));
                    return true;
                }, true));
        require(claimed.succeeded() && conflict.get() != null && !conflict.get().succeeded()
                && "position_claimed".equals(conflict.get().detail()), "reentrant capture ignored ownership claim");
        require(StructureTransactions.discard(server, claimed.structure().orElseThrow()), "claim fixture cleanup failed");

        var copper = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                net.minecraft.resources.Identifier.withDefaultNamespace("copper_block"));
        for (var state : List.of(copper.defaultBlockState(), Blocks.IRON_BARS.defaultBlockState(),
                org.academy.internal.common.world.level.block.Blocks.LABORATORY_LIGHT_GRATE.get().defaultBlockState())) {
            host.setBlock(source, state, 3);
            var capture = StructureTransactions.capture(host, new StructureCaptureRequest(List.of(source), 1, 1,
                    StructureCaptureRequest.ALLOW, true));
            require(capture.succeeded(), "plot-compatible content rejected: " + state + " " + capture);
            var ref = capture.structure().orElseThrow();
            var body = runtime.get(new SubLevelId(ref.id())).orElseThrow();
            require(runtime.storage().blockState(body.plot(), BlockPos.ZERO).equals(state), "capture changed block state");
            require(StructureTransactions.discard(server, ref), "content fixture cleanup failed");
        }
        require(host.getBlockState(source).isAir() && runtime.storage().allocated() == plots,
                "transaction fixture left content or reservations");
        AcademyCraft.LOGGER.info("SUBLEVEL_CAPTURE_TRANSACTION_PASSED permission_revalidated=true claims=true"
                + " source_change_rollback=true body_cleanup=true copper=true iron_bars=true mod_block=true");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("SubLevel capture transaction: " + message);
    }
}
