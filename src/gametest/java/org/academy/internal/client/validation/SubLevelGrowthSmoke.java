package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.api.common.structure.upgrade.*;
import org.academy.internal.client.sublevel.SubLevelClientState;
import org.academy.internal.common.structure.physics.*;
import org.academy.internal.common.structure.transaction.*;
import org.academy.internal.common.structure.upgrade.lifecycle.*;
import org.academy.internal.common.sublevel.*;
import java.nio.file.*;
import java.util.*;

/** Disposable real-client checks for growth, legacy migration, restart and marked restoration. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelGrowthSmoke {
    private static final String MODE = System.getProperty("academy.sublevelGrowthSmoke", "");
    private static final List<String> CHECKS = new ArrayList<>();
    private static final List<SubLevel> BODIES = new ArrayList<>();
    private static final Map<BlockPos, StructureBlockData> RESTORED = new LinkedHashMap<>();
    private static volatile boolean ready, complete, failed, closing, clientSawExpansion;
    private static Path directory;
    private static ServerPlayer player;
    private static SubLevelRuntime runtime;
    private static int ticks, phase;
    private static boolean cancelPlacement;
    private static int dropEvents;
    private static SubLevel restoring;

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (MODE.isEmpty() || !ready || complete || failed) return;
        try {
            check(++ticks < 2400, "growth fixture timeout phase=" + phase);
            if (player == null) {
                player = event.getServer().getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                org.academy.internal.common.world.damagesource.DestroyBlocksSetting.setDestroyBlocksEnabled(player, true);
                runtime = SubLevelServer.runtime(player.level()).orElseThrow();
            }
            player.setNoGravity(true); player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance();
            if (ticks < 40) return;
            if (phase == 0) {
                if (MODE.equals("verify")) { recover(); phase = 3; return; }
                if (MODE.startsWith("recover_")) { recover(); phase = 1; return; }
                if (MODE.startsWith("crash_")) { create(true,new Vec3(-2100,280,-1400)); phase = 1; return; }
                create(false, new Vec3(-2100, 280, -1400));
                create(true, new Vec3(-2088, 280, -1400));
                phase = 1; return;
            }
            if (phase == 1) {
                if (ticks < 70 || BODIES.stream().anyMatch(body -> runtime.storage().loadedEntities(body.plot()).isEmpty())) return;
                if (MODE.startsWith("crash_")) {
                    writeImages();
                    runtime.observeMigration(checkpoint -> {
                        if (MODE.equals("crash_"+checkpoint.phase())) {
                            CHECKS.add("durable_migration_checkpoint_"+checkpoint.phase()); write("");
                            Runtime.getRuntime().halt(86);
                        }
                    });
                }
                verifyWideCodec();
                for (var body : BODIES) expand(body);
                phase = 2; return;
            }
            if (phase == 2) {
                if (!clientSawExpansion) return;
                if (BODIES.stream().anyMatch(body -> runtime.storage().loadedEntities(body.plot()).isEmpty())) return;
                writeImages();
                CHECKS.add("client_snapshot_contains_negative_and_cross_chunk_cells");
                complete = true; write(""); return;
            }
            if (phase == 3) {
                restoring = BODIES.getFirst();
                var handle = handle(restoring);
                var metadata = StructureProvenance.SkillCreation.resting(player.getUUID(), AcademyCraft.academy("kinetic_throw"))
                        .capture(restoring.id().value(), player.level().getGameTime());
                check(LiveStructureProvenance.commitCapture(event.getServer(), handle.reference(), metadata), "cannot mark restart fixture");
                // A broad flat support isolates restoration from intentional terrain rejection.
                var bounds = restoring.worldBounds(); int y = (int)Math.floor(bounds.minY) - 1;
                for (var pos : BlockPos.betweenClosed((int)bounds.minX-3,y,(int)bounds.minZ-3,(int)bounds.maxX+3,y,(int)bounds.maxZ+3)) {
                    player.level().getChunkAt(pos); player.level().setBlock(pos, Blocks.GLOWSTONE.defaultBlockState(), 2);
                }
                phase = 4; return;
            }
            if (phase == 4 && runtime.get(restoring.id()).isEmpty()) {
                check(!RESTORED.isEmpty(), "missing restoration placements");
                for (var entry : RESTORED.entrySet()) check(StructureCellPayload.read(player.level(), entry.getKey()).equals(entry.getValue()), "restored payload differs");
                CHECKS.add("expanded_marked_structure_auto_restored_with_inventory");
                complete = true; write("");
            }
        } catch (Throwable error) {
            failed = true; AcademyCraft.LOGGER.error("SUBLEVEL_GROWTH_SMOKE_FAILED mode={} phase={}",MODE,phase,error); write(error.toString());
        }
    }

    private static void create(boolean legacy, Vec3 origin) {
        var storage = runtime.storage();
        var lease = legacy ? storage.claim(new PlotPos(0,0)).orElseThrow() : storage.allocate().orElseThrow();
        storage.setBlock(lease.plot(), BlockPos.ZERO, Blocks.CHEST.defaultBlockState(), SubLevelCaptureService.TRANSACTION_FLAGS);
        ((Container)storage.blockEntity(lease.plot(),BlockPos.ZERO)).setItem(0,new ItemStack(Items.DIAMOND,37));
        storage.setBlock(lease.plot(), new BlockPos(1,0,0), Blocks.IRON_BLOCK.defaultBlockState(), SubLevelCaptureService.TRANSACTION_FLAGS);
        var bounds = new AABB(0,0,0,2,1,1);
        var geometry = PlotGeometry.capture(storage,lease.plot(),bounds);
        var body = runtime.createInLease(SubLevelId.random(),lease,bounds,
                new PhysicsPose(new PhysicsVector(origin.x,origin.y,origin.z),new PhysicsQuaternion(0,0,0,1)),geometry.boxes());
        BODIES.add(body);
        check(runtime.flushPlotChunks(body.id()),"initial save"); runtime.persist();
        player.connection.teleport(origin.x,origin.y+5,origin.z,0,90);
    }
    private static SubLevelStructureHandle handle(SubLevel body) {
        return new SubLevelStructureHandle(body,runtime.storage(),player.level().dimension().identifier());
    }
    private static void expand(SubLevel body) {
        var before = body.pose(); var oldPlot = body.plot(); var world = handle(body).world();
        var chest = StructureCellPayload.normalize(world.read(BlockPos.ZERO));
        if (!PlotLayout.spacious(oldPlot)) {
            var center = body.pose().localToWorldPoint(Vec3.atCenterOf(BlockPos.ZERO));
            player.connection.teleport(center.x,center.y+2,center.z,0,90);
            check(SubLevelInteractionService.useBlock(player.level().getServer(),body.id(),BlockPos.ZERO,Direction.UP,center,player,InteractionHand.MAIN_HAND).consumesAction(),"legacy chest menu did not open");
            check(!runtime.prepareEdit(body.id(),new BlockPos(-1,0,0),0,1),"legacy menu did not defer migration");
            check(body.plot().equals(oldPlot),"open menu migrated its backing inventory");
            player.closeContainer(); CHECKS.add("legacy_menu_defers_storage_migration");
        }
        for (var pos : List.of(new BlockPos(-33,0,0),new BlockPos(34,0,0),new BlockPos(0,-1,0),
                new BlockPos(0,2,0),new BlockPos(0,0,-1),new BlockPos(0,0,1))) {
            check(world.compareAndSet(pos,world.read(pos),new StructureBlockData(Blocks.STONE.defaultBlockState())) == StructureWriteResult.APPLIED,"CAS expansion "+pos);
            check(before.equals(body.pose()),"growth moved the original world pose");
            check(runtime.collisionIndex().entry(body.id()).orElseThrow().localBoxes().stream().anyMatch(box -> box.contains(Vec3.atCenterOf(pos))),"missing expanded collider "+pos);
            check(runtime.view(body.id()).getBlockState(pos).is(Blocks.STONE),"missing expanded view "+pos);
        }
        check(chest.equals(StructureCellPayload.normalize(world.read(BlockPos.ZERO))),"migration changed chest payload");
        check(PlotLayout.spacious(body.plot()),"legacy not migrated");
        if (!PlotLayout.spacious(oldPlot)) {
            check(runtime.storage().lease(oldPlot).isEmpty(),"legacy lease not released");
            check(runtime.storage().level().getBlockState(runtime.storage().worldPosition(oldPlot,BlockPos.ZERO)).isAir(),"legacy inventory copy survived");
            CHECKS.add("legacy_migration_preserves_inventory_and_world_pose");
        }
        place(body,new BlockPos(-33,0,0),Direction.WEST,Items.STONE,true);
        place(body,new BlockPos(-33,0,0),Direction.WEST,Items.SLIME_BLOCK,false);
        var outer = new BlockPos(-34,0,0);
        check(body.localBounds().minX == -34,"negative face did not expand");
        mineOuter(body,outer);
        check(body.localBounds().minX == -33,"bounds did not shrink");
        check(runtime.collisionIndex().entry(body.id()).orElseThrow().localBoxes().stream().noneMatch(box -> box.contains(Vec3.atCenterOf(outer))),"ghost collider after shrink");
        place(body,new BlockPos(1,0,0),Direction.UP,Items.OAK_DOOR,false);
        check(!world.read(new BlockPos(1,2,0)).state().isAir(),"door upper half outside bounds");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE,5));
        check(SubLevelInteractionService.placeBlock(player.level().getServer(),body.id(),new BlockPos(34,0,0),Direction.EAST,
                body.pose().localToWorldPoint(new Vec3(35,.5,.5)),player,InteractionHand.MAIN_HAND).consumesAction(),"legacy placement route refused expansion");
        check(world.read(new BlockPos(35,0,0)).state().is(Blocks.STONE),"legacy placement outside refreshed content");
        check(!runtime.prepareEdit(body.id(),new BlockPos(400,0,0),0,1),"oversized edit accepted");
        check(runtime.storage().lease(body.plot()).orElseThrow().chunks().size() < 32,"entire virtual plot loaded");
        CHECKS.add("six_direction_growth_crosses_original_32_block_storage");
        CHECKS.add("placement_cancel_preserves_stack_and_door_keeps_both_halves");
        CHECKS.add("removal_shrinks_view_and_collision_without_rebasing");
    }
    private static void place(SubLevel body, BlockPos clicked, Direction face, net.minecraft.world.item.Item item, boolean cancel) {
        var point = Vec3.atCenterOf(clicked).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(.5));
        var eye = body.pose().localToWorldPoint(point.add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(2.5)));
        player.connection.teleport(eye.x,eye.y-player.getEyeHeight(),eye.z,0,0);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item,5));
        var handle = handle(body);
        var hit = new StructureHit(new SpaceBlockRef(handle.reference(),clicked),handle.runtimeId(),body.geometryRevision(),body.pose(),face,point,body.pose().localToWorldPoint(point),false);
        cancelPlacement = cancel;
        var receipt = SubLevelEnhancementBootstrap.INTERACTIONS.place(player,new StructureInteractionController.ActionRequest(100+ticks,hit,InteractionHand.MAIN_HAND));
        cancelPlacement = false;
        check(receipt.status() == (cancel ? StructureInteractionController.ActionStatus.REJECTED : StructureInteractionController.ActionStatus.APPLIED),"placement "+receipt);
        check(player.getMainHandItem().getCount() == (cancel ? 5 : 4),"placement consumed wrong count");
    }
    @SubscribeEvent public static void placement(StructurePlacementEvent event) { if (!MODE.isEmpty() && cancelPlacement) event.setCanceled(true); }

    private static void mineOuter(SubLevel body, BlockPos local) {
        var point = Vec3.atCenterOf(local).add(-.5,0,0);
        var eye = body.pose().localToWorldPoint(point.add(-2.5,0,0));
        player.connection.teleport(eye.x,eye.y-player.getEyeHeight(),eye.z,0,0);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));
        var handle = handle(body);
        var hit = new StructureHit(new SpaceBlockRef(handle.reference(),local),handle.runtimeId(),body.geometryRevision(),body.pose(),Direction.WEST,point,body.pose().localToWorldPoint(point),false);
        int prior = dropEvents;
        var controller = SubLevelEnhancementBootstrap.INTERACTIONS;
        var receipt = controller.mine(player,new StructureInteractionController.MiningRequest(controller.nextRequestId(),StructureInteractionController.MiningAction.START,hit));
        check(receipt.status() == StructureInteractionController.MiningStatus.STARTED,"outer mining rejected "+receipt);
        controller.tick(player.level().getServer(),SubLevelServer.MINING_FEEDBACK);
        check(handle.world().read(local).state().isAir(),"outer mining did not remove block");
        check(dropEvents == prior+1,"outer mining bypassed global BlockDropsEvent");
        var drops = player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new AABB(body.pose().localToWorldPoint(Vec3.atCenterOf(local)),body.pose().localToWorldPoint(Vec3.atCenterOf(local))).inflate(2));
        check(drops.stream().filter(drop -> drop.getItem().is(Items.SLIME_BLOCK)).mapToInt(drop -> drop.getItem().getCount()).sum() == 1,"outer drop was lost or duplicated");
        CHECKS.add("outer_survival_mining_emits_global_drop_event_and_one_host_drop");
    }
    @SubscribeEvent public static void drops(net.neoforged.neoforge.event.level.BlockDropsEvent event) {
        if (!MODE.isEmpty() && runtime != null && event.getLevel() == runtime.storage().level()) dropEvents++;
    }

    private static void writeImages() throws Exception {
        var root = new CompoundTag(); var rows = new ListTag();
        for (var body : BODIES) {
            var row = new CompoundTag(); row.putString("id",body.id().value().toString()); var cells = new ListTag();
            for (var cell : new SubLevelCaptureTarget(player.level().getServer()).restorationCells(handle(body).reference()).orElseThrow()) {
                var tag = StructureCellPayload.encode(player.level(),cell.data());
                tag.putInt("x",cell.localPosition().getX()); tag.putInt("y",cell.localPosition().getY()); tag.putInt("z",cell.localPosition().getZ()); cells.add(tag);
            }
            row.put("cells",cells); rows.add(row); check(runtime.flushPlotChunks(body.id()),"expanded save");
        }
        runtime.persist(); root.put("bodies",rows); NbtIo.writeCompressed(root,directory.resolve("growth-images.nbt"));
    }
    private static void recover() throws Exception {
        var root = NbtIo.readCompressed(directory.resolve("growth-images.nbt"),NbtAccounter.create(4_000_000));
        for (var value : root.getList("bodies").orElseThrow()) {
            var row = (CompoundTag)value; var body = runtime.get(new SubLevelId(UUID.fromString(row.getString("id").orElseThrow()))).orElseThrow();
            check(PlotLayout.spacious(body.plot()) != MODE.equals("recover_COPY_SAVED"),"restart chose wrong storage owner"); BODIES.add(body);
            var world = handle(body).world();
            for (var item : row.getList("cells").orElseThrow()) {
                var cell = (CompoundTag)item; var pos = new BlockPos(cell.getInt("x").orElseThrow(),cell.getInt("y").orElseThrow(),cell.getInt("z").orElseThrow());
                check(StructureCellPayload.normalize(world.read(pos)).equals(StructureCellPayload.decode(player.level(),cell)),"restart lost expanded cell "+pos);
            }
        }
        CHECKS.add("restart_restores_all_expanded_cells_and_full_chest_payload");
    }
    private static void verifyWideCodec() {
        var view = new org.academy.internal.common.structure.space.NativeStructureView(List.of(
                new org.academy.internal.common.structure.space.NativeStructureView.Cell(new BlockPos(-200,-2,201),Blocks.STONE.defaultBlockState())));
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),player.level().registryAccess(),
                net.neoforged.neoforge.network.connection.ConnectionType.OTHER);
        try {
            org.academy.internal.common.structure.space.NativeStructureView.STREAM_CODEC.encode(buffer,view);
            var decoded = org.academy.internal.common.structure.space.NativeStructureView.STREAM_CODEC.decode(buffer);
            check(decoded.cells().equals(view.cells()) && !buffer.isReadable(),"wide view codec truncated coordinates");
        } finally { buffer.release(); }
        CHECKS.add("wide_view_codec_preserves_signed_coordinates");
    }
    @SubscribeEvent public static void restoration(StructureRestorationEvent event) {
        if (restoring == null || !event.reference().id().equals(restoring.id().value())) return;
        var alignment = RestorationGrid.align(restoring.pose(),event.placements().stream().map(value -> value.localPosition()).toList());
        RESTORED.clear(); var world = handle(restoring).world();
        for (var placement : event.placements()) {
            var data = StructureCellPayload.normalize(world.read(placement.localPosition()));
            RESTORED.put(placement.targetPosition(),new StructureBlockData(data.state().rotate(alignment.rotation()),data.blockEntityData()));
        }
    }
    private static void write(String failure) {
        try { Files.writeString(directory.resolve("growth-"+MODE+"-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed",!failed,"failure",failure,"checks",CHECKS,"server_ticks",ticks))); }
        catch (Exception error) { AcademyCraft.LOGGER.error("Growth report failed",error); }
    }
    @SubscribeEvent public static void clientTick(ClientTickEvent.Post event) {
        if (MODE.isEmpty() || closing) return;
        var mc = Minecraft.getInstance();
        if (complete || failed) { closing = true; mc.stop(); return; }
        if (mc.player == null || mc.level == null) return;
        directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        check(directory.getFileName().toString().startsWith("native-sublevel-direct-smoke"),"isolated fixture required");
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) mc.gui.setScreen(null);
        if (!ready) org.lwjgl.glfw.GLFW.glfwIconifyWindow(mc.getWindow().handle());
        ready = true;
        clientSawExpansion |= SubLevelClientState.snapshots(mc.level).stream().anyMatch(snapshot ->
                !snapshot.view().getBlockState(new BlockPos(-33,0,0)).isAir() && !snapshot.view().getBlockState(new BlockPos(34,0,0)).isAir());
    }
    private static void check(boolean condition,String detail) { if (!condition) throw new IllegalStateException(detail); }
}
