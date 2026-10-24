package org.academy.internal.client.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.api.common.structure.upgrade.*;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.upgrade.lifecycle.*;
import org.academy.internal.common.sublevel.*;

import java.util.*;

/** Actual command dispatch in disposable worlds, with remote plots, two hosts and a foreign Sable control. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructureLocalRestorationSmoke {
    private static final String COMMAND = "academy debug sublevel restore_all";
    private static final List<Fixture> FIXTURES = new ArrayList<>();
    private static final Map<UUID, List<StructureGridRestoration.Placement>> TARGETS = new HashMap<>();
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static UUID cancelled, sableTransaction;
    private static StructureRef sableControl;
    private static boolean started, retry;
    private static int ticks;
    private static BlockPos leaf, floor;
    private static ServerPlayer faultPlayer;
    private static final boolean FAULT = System.getProperty("academy.structureRestorationSmokeMode", "").startsWith("admin_crash_");

    private StructureLocalRestorationSmoke() {}

    static void prepare(ServerPlayer player) throws Exception {
        var server = player.level().getServer();
        check(SubLevelServer.ensureForMaintenance(server), "local runtime unavailable");
        var arena = player.blockPosition().offset(12, 0, 0).atY(player.level().getMaxY() - 35);
        player.connection.teleport(arena.getX() - 6, arena.getY() + 5, arena.getZ() - 6, 0, 0);
        floor = arena.below();
        player.level().getChunkAt(floor);
        check(player.level().getBlockState(floor).isAir(), "fixture floor occupied");
        player.level().setBlock(floor, Blocks.GLOWSTONE.defaultBlockState(), 3);
        create(player.level(), arena, null, false, false);
        if (FAULT) { faultPlayer = player; return; }
        var marked = create(player.level(), arena.east(5), null, true, false);
        leaf = arena.east(5);
        player.level().setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.PERSISTENT, true), 3);
        check(LiveStructureProvenance.commitCapture(server, marked.reference(), StructureProvenance.SkillCreation
                .resting(player.getUUID(), AcademyCraft.academy("kinetic_throw")).capture(UUID.randomUUID(), player.level().getGameTime())), "mark setup failed");
        create(player.level(), arena.east(10), new BlockPos(12008, arena.getY(), 12008), true, false);
        var nether = server.getLevel(Level.NETHER);
        check(nether != null, "missing second host");
        create(nether, new BlockPos(8, nether.getMaxY() - 24, 8), new BlockPos(12008, nether.getMaxY() - 24, 12008), true, false);
        create(player.level(), arena.east(15), null, true, true);
        cancelled = create(player.level(), arena.east(20), null, true, false).reference().id();
        if (StructureBackendRouter.status(server).mode() == org.academy.api.common.structure.StructureBackendStatus.Mode.SABLE) {
            var pos = arena.east(25);
            player.level().getChunkAt(pos);
            check(player.level().getBlockState(pos).isAir(), "foreign fixture site occupied");
            player.level().setBlock(pos, Blocks.IRON_BLOCK.defaultBlockState(), 3);
            var capture = StructureTransactions.capture(player.level(), new StructureCaptureRequest(List.of(pos), 1, 1,
                    StructureCaptureRequest.ALLOW, true));
            check(capture.succeeded() || capture.pending(), "Sable control capture rejected");
            sableTransaction = capture.transactionId();
        }
        var unmarked = FIXTURES.getFirst();
        var denied = MarkedGridRestoration.INSTANCE.restoreAdministrative(server.createCommandSourceStack()
                .withPermission(net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS), unmarked.host(), unmarked.reference());
        check(denied.status() == StructureOperation.Status.DENIED, "permission-free administrator restoration accepted");
    }

    private static Fixture create(ServerLevel host, BlockPos pos, BlockPos remote, boolean inactive, boolean chest) {
        host.getChunkAt(pos);
        check(host.getBlockState(pos).isAir(), "local fixture source occupied");
        host.setBlock(pos, chest ? Blocks.CHEST.defaultBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true)
                : Blocks.IRON_BLOCK.defaultBlockState(), 3);
        if (chest) ((net.minecraft.world.level.block.entity.ChestBlockEntity) host.getBlockEntity(pos))
                .setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        var runtime = SubLevelServer.runtime(host).orElseThrow();
        var capture = SubLevelCaptureService.capture(host, runtime, List.of(pos));
        var body = runtime.get(capture.id()).orElseThrow();
        if (inactive) check(runtime.deactivate(capture.id()), "fixture did not deactivate");
        if (remote != null) {
            check(host.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null, "remote destination already loaded");
            check(runtime.shift(capture.id(), Vec3.atLowerCornerOf(remote.subtract(pos))), "remote fixture shift failed");
        }
        var ref = SubLevelStructureRefs.of(body, host.dimension().identifier(), SubLevelActivationRegistry.find(capture.id()).orElseThrow().generation());
        var fixture = new Fixture(host, ref, chest, remote != null);
        FIXTURES.add(fixture);
        AcademyCraft.LOGGER.info("ADMIN_RESTORATION_FIXTURE {}", fixture);
        return fixture;
    }

    static Optional<List<Map<String, Object>>> tick(ServerPlayer player) throws Exception {
        check(++ticks < 1200, "administrator batch timed out");
        var server = player.level().getServer();
        if (!started) {
            if (FAULT) {
                check(server.getCommands().getDispatcher().execute(COMMAND, server.createCommandSourceStack().withEntity(player)) > 0,
                        "fault command rejected");
                started = true; return Optional.empty();
            }
            if (sableTransaction != null) {
                var capture = StructureTransactions.poll(server, sableTransaction).orElseThrow();
                if (capture.pending()) return Optional.empty();
                check(capture.succeeded(), "Sable control capture failed");
                sableControl = capture.structure().orElseThrow();
                check(!SubLevelStructureRefs.isSubLevel(sableControl), "foreign control is not Sable");
            }
            execute(player, COMMAND);
            execute(player, COMMAND + " cancel");
            check(!LocalStructureRestorationBatch.status(server).orElseThrow().running(), "cancel kept task running");
            execute(player, COMMAND);
            started = true;
            return Optional.empty();
        }
        var status = LocalStructureRestorationBatch.status(server).orElseThrow();
        if (status.running()) return Optional.empty();
        check(!FAULT, "administrator interruption checkpoint not reached: " + status);
        check(status.recoveryPending() == 0, "normal command left recovery work");
        check(status.retained().size() == (retry ? 0 : 1), "unexpected retained items: " + status);
        check(status.restored() == (retry ? 1 : 5), "wrong restoration count: " + status);
        check(player.level().getBlockState(leaf).is(Blocks.OAK_LEAVES)
                && player.level().getBlockState(leaf.above()).is(Blocks.IRON_BLOCK), "clearance lift overwrote leaves or failed to restore above them");
        for (var fixture : FIXTURES) {
            var runtime = SubLevelServer.runtime(fixture.host()).orElseThrow();
            boolean retained = !retry && fixture.reference().id().equals(cancelled);
            var body = runtime.get(new SubLevelId(fixture.reference().id()));
            check(body.isPresent() == retained, "source owner mismatch: " + fixture);
            if (!retained) {
                check(!runtime.hasPersisted(new SubLevelId(fixture.reference().id())), "manifest still owns restored source");
                check(LiveStructureProvenance.metadata(server, fixture.reference()).isEmpty(), "restored source still marked");
                var placements = TARGETS.get(fixture.reference().id());
                check(placements != null && placements.size() == 1, "missing placement receipt");
                for (var placement : placements) {
                    fixture.host().getChunkAt(placement.targetPosition());
                    check(fixture.host().getBlockState(placement.targetPosition()).is(fixture.chest() ? Blocks.CHEST : Blocks.IRON_BLOCK), "missing restored block");
                    if (fixture.chest()) {
                        var entity = (net.minecraft.world.level.block.entity.ChestBlockEntity) fixture.host().getBlockEntity(placement.targetPosition());
                        check(entity != null && entity.getItem(0).is(net.minecraft.world.item.Items.DIAMOND) && entity.getItem(0).getCount() == 3,
                                "administrator restoration changed inventory");
                        check(entity.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED),
                                "administrator restoration lost waterlogging");
                    }
                }
                check(SubLevelCollisionRuntime.index(fixture.host()).flatMap(index -> index.entry(
                        new SubLevelId(fixture.reference().id()))).isEmpty(), "ghost collider remained");
            }
        }
        if (sableControl != null) {
            check(StructureSpaces.resolve(server, sableControl).isPresent(), "administrator command touched Sable control");
            check(LiveStructureProvenance.metadata(server, sableControl).isEmpty(), "foreign structure gained provenance");
        }
        RESULTS.add(Map.of("case", retry ? "admin_retry_and_idempotence" : "admin_all_hosts_unloaded_unmarked_and_protected",
                "passed", true, "restored", status.restored(), "retained", status.retained(), "foreign_sable_untouched", sableControl != null));
        if (!retry) { cancelled = null; retry = true; execute(player, COMMAND); return Optional.empty(); }
        for (var fixture : FIXTURES) {
            var runtime = SubLevelServer.runtime(fixture.host()).orElseThrow();
            runtime.get(new SubLevelId(fixture.reference().id())).ifPresent(body -> runtime.remove(body.id()));
            for (var placement : TARGETS.getOrDefault(fixture.reference().id(), List.of()))
                fixture.host().setBlock(placement.targetPosition(), Blocks.AIR.defaultBlockState(), 3);
        }
        if (sableControl != null) check(StructureTransactions.discard(server, sableControl), "Sable control cleanup failed");
        player.level().setBlock(leaf, Blocks.AIR.defaultBlockState(), 3);
        player.level().setBlock(floor, Blocks.AIR.defaultBlockState(), 3);
        return Optional.of(List.copyOf(RESULTS));
    }

    private static void execute(ServerPlayer player, String command) throws Exception {
        var server = player.level().getServer();
        check(server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack()) > 0, "command rejected: " + command);
    }

    @SubscribeEvent
    public static void restore(StructureRestorationEvent event) {
        if (FIXTURES.stream().noneMatch(value -> value.reference().id().equals(event.reference().id()))) return;
        check(event.cause() == StructureGridRestoration.Cause.ADMINISTRATIVE_REQUEST, "fixture restored without admin command");
        if (FAULT) StructureRestorationCrashSmoke.arm(faultPlayer, event.reference(), event.placements());
        if (event.reference().id().equals(cancelled)) { event.setCanceled(true); return; }
        TARGETS.put(event.reference().id(), event.placements());
    }

    private static void check(boolean value, String detail) { if (!value) throw new IllegalStateException(detail); }
    private record Fixture(ServerLevel host, StructureRef reference, boolean chest, boolean remote) {}
}
