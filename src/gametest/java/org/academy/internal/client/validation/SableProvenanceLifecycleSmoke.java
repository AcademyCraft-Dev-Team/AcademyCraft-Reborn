package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.api.common.structure.upgrade.StructureProvenance;
import org.academy.internal.common.structure.sable.*;
import org.academy.internal.common.structure.sable.reflect.api.api.SubLevelAssemblyHelper;
import org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer;
import org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3i;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelRemovalReason;
import org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance;

import java.nio.file.Files;
import java.util.*;

/** Actual assembly, storage, unload and removal callbacks in an isolated four-mod world. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SableProvenanceLifecycleSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.sableProvenanceSmoke");
    private static final String MODE = System.getProperty("academy.sableProvenanceMode", "functional");
    private static final List<String> PASSED = new ArrayList<>();
    private static volatile boolean ready, done, failed, closing;
    private static java.nio.file.Path directory;
    private static ServerPlayer player;
    private static BlockPos seed;
    private static UUID capture;
    private static StructureRef root, child, grandchild, unmarked;
    private static StructureProvenance.Metadata original;
    private static net.minecraft.nbt.CompoundTag deletedEvidence;
    private static BlockPos restoredCell;
    private static int stage, ticks;

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || !ready || done || failed || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            check(++ticks < 900, "lifecycle fixture timeout stage=" + stage);
            if (player == null) {
                player = event.getServer().getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.CREATIVE); player.setNoGravity(true);
                player.level().getServer().getAcademyCraftServerConfig()
                        .<org.academy.internal.server.config.GenericConfig>getConfig(org.academy.internal.server.config.GenericConfig.KEY)
                        .structureRestoration.enabled = false;
                if (MODE.equals("verify")) { readRestart(); stage = 8; return; }
                seed = new BlockPos(player.getBlockX() + 6, player.level().getMaxY() - 48, player.getBlockZ());
                for (var pos : BlockPos.betweenClosed(seed.offset(-4,-2,-3), seed.offset(13,3,5))) {
                    player.level().getChunkAt(pos); check(player.level().getBlockState(pos).isAir(), "arena occupied");
                }
                player.connection.teleport(seed.getX()-3, seed.getY()+2, seed.getZ()-3, 0, 0);
                stage = 1; return;
            }
            var server = player.level().getServer();
            var grid = SubLevelContainer.getContainer(player.level());
            check(grid != null, "Sable backend required");
            var transactions = new SableStructureTransactions(server);
            if (stage == 1) {
                if (ticks < 12) return;
                capture = capture(seed, 5, true); stage = 2; return;
            }
            if (stage == 2) {
                var result = StructureTransactions.poll(server, capture).orElseThrow();
                if (result.pending()) return;
                check(result.succeeded(), "marked capture failed"); root = result.structure().orElseThrow();
                original = LiveStructureProvenance.metadata(server, root).orElseThrow();
                var parent = (ServerSubLevel) grid.getSubLevel(root.id());
                deletedEvidence = transactions.restorationEvidence(root).orElseThrow();
                var center = parent.getPlot().getCenterBlock();
                var fragment = split(List.of(center.east(3), center.east(4)));
                child = SableProvenanceLifecycle.reference(fragment);
                verifyFragment(child, root); pass("marked_split_inherits_exact_policy_owner_root");
                var second = split(List.of(fragment.getPlot().getCenterBlock().east()));
                grandchild = SableProvenanceLifecycle.reference(second);
                verifyFragment(grandchild, child); pass("nested_split_keeps_root_and_direct_parent");
                check(LiveStructureProvenance.commitFragment(server, child, root, player.level().getGameTime()),"repeat split rejected");
                verifyFragment(child, root); pass("duplicate_notification_idempotent");
                check(transactions.save(root) && transactions.checkpointRestoration(root)
                        && transactions.checkpointRestoration(child) && transactions.checkpointRestoration(grandchild), "split save failed");
                capture = capture(seed.south(3), 3, false); stage = 3; return;
            }
            if (stage == 3) {
                var result = StructureTransactions.poll(server, capture).orElseThrow();
                if (result.pending()) return;
                check(result.succeeded(), "unmarked capture failed"); unmarked = result.structure().orElseThrow();
                var outsider = (ServerSubLevel) grid.getSubLevel(unmarked.id());
                var part = split(List.of(outsider.getPlot().getCenterBlock().east(2)));
                check(LiveStructureProvenance.metadata(server, unmarked).isEmpty()
                        && LiveStructureProvenance.metadata(server, SableProvenanceLifecycle.reference(part)).isEmpty(), "outsider marked");
                pass("unmarked_split_stays_unmarked");
                grid.removeSubLevel(part, SubLevelRemovalReason.REMOVED);
                check(!SableProvenanceLifecycle.hasPending(server), "outsider produced retirement intent");
                pass("unmarked_delete_no_provenance_intent");
                var parent = (ServerSubLevel) grid.getSubLevel(root.id());
                if (MODE.equals("crash")) {
                    check(transactions.save(grandchild) && transactions.checkpointRestoration(grandchild), "restart child save");
                    server.saveEverything(false, true, true);
                    writeRestart();
                }
                grid.removeSubLevel(parent, SubLevelRemovalReason.REMOVED);
                check(SableProvenanceLifecycle.pending(server, root), "deletion not journaled");
                check(LiveStructureProvenance.metadata(server, root).isPresent(), "mark retired before durable backend cleanup");
                pass("permanent_delete_waits_for_durable_retirement");
                if (MODE.equals("crash")) { write(""); Runtime.getRuntime().halt(86); }
                // Allocate immediately: the old plot may be reused before metadata retirement.
                capture = capture(seed.east(8), 1, false); stage = 4; return;
            }
            if (stage == 4) {
                if (SableProvenanceLifecycle.pending(server, root)) return;
                check(LiveStructureProvenance.metadata(server, root).isEmpty(), "removed root retains mark");
                verifyFragment(child, root); verifyFragment(grandchild, child);
                pass("retirement_keeps_descendant_lineage");
                var result = StructureTransactions.poll(server, capture).orElseThrow();
                if (result.pending()) return;
                check(result.succeeded(), "replacement capture failed");
                var replacement = StructureSpaces.resolve(server, result.structure().orElseThrow()).orElseThrow();
                check(replacement.world().read(BlockPos.ZERO).state().is(Blocks.IRON_BLOCK), "replacement content erased");
                var replacementEvidence=transactions.restorationEvidence(result.structure().orElseThrow()).orElseThrow();
                check(replacementEvidence.getInt("plot_x").equals(deletedEvidence.getInt("plot_x"))
                        && replacementEvidence.getInt("plot_z").equals(deletedEvidence.getInt("plot_z")), "fixture did not reuse deleted plot");
                var oldImage=grid.getHoldingChunkMap().getStorage().getRegionStorageFile(
                        new ChunkPos(deletedEvidence.getInt("chunk_x").orElseThrow(),deletedEvidence.getInt("chunk_z").orElseThrow()),
                        deletedEvidence.getInt("storage").orElseThrow()).read(deletedEvidence.getInt("slot").orElseThrow());
                check(oldImage==null || !org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.serialization.SubLevelData
                        .fromTag(oldImage).uuid().equals(root.id()), "deleted source remains in durable slot");
                pass("replacement_survives_previous_plot_retirement");
                var target = (ServerSubLevel) grid.getSubLevel(grandchild.id());
                grid.removeSubLevel(target, SubLevelRemovalReason.REMOVED); stage = 5; return;
            }
            if (stage == 5) {
                if (SableProvenanceLifecycle.pending(server, grandchild)) return;
                check(LiveStructureProvenance.metadata(server, grandchild).isEmpty(), "removed child retains mark");
                verifyFragment(child, root); pass("fragment_permanent_delete_retires_only_itself");
                var unloading = (ServerSubLevel) grid.getSubLevel(child.id());
                grid.getHoldingChunkMap().moveToUnloaded(unloading, unloading.getLastSerializationPointer().chunkPos());
                check(!SableProvenanceLifecycle.pending(server, child), "unload treated as permanent deletion");
                verifyFragment(child, root); pass("unload_preserves_fragment_mark");
                capture = capture(seed.east(10), 3, true); stage = 6; return;
            }
            if (stage == 6) {
                var result = StructureTransactions.poll(server, capture).orElseThrow();
                if (result.pending()) return;
                check(result.succeeded(), "automatic split capture failed"); root=result.structure().orElseThrow();
                original=LiveStructureProvenance.metadata(server,root).orElseThrow();
                var source=(ServerSubLevel)grid.getSubLevel(root.id());
                player.level().destroyBlock(source.getPlot().getCenterBlock().east(),false);
                stage=7; return;
            }
            if (stage == 7) {
                var fragment=LiveStructureProvenance.entries(server).stream()
                        .filter(entry -> entry.metadata().parent().equals(Optional.of(root))).findFirst().orElse(null);
                if(fragment==null) return;
                child=fragment.reference(); verifyFragment(child,root); pass("heat_map_auto_split_inherits_creation");
                var source=(ServerSubLevel)grid.getSubLevel(child.id());
                check(source!=null,"automatic child unexpectedly unloaded");
                player.level().destroyBlock(source.getPlot().getCenterBlock(),false);
                stage=9; return;
            }
            if (stage == 9) {
                if(grid.getSubLevel(child.id())!=null || SableProvenanceLifecycle.pending(server,child)) return;
                check(LiveStructureProvenance.metadata(server,child).isEmpty(),"empty automatic child retains mark");
                pass("zero_mass_child_removal_retires_mark");
                var remaining=(ServerSubLevel)grid.getSubLevel(root.id());
                var bounds=remaining.getPlot().getBoundingBox();
                BlockPos lastCell=null;
                for(var pos:BlockPos.betweenClosed(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ())) {
                    if(player.level().getBlockState(pos).is(Blocks.IRON_BLOCK)) { lastCell=pos.immutable(); break; }
                }
                check(lastCell!=null,"remaining parent has no iron cell");
                var fragment=split(List.of(lastCell));
                child=SableProvenanceLifecycle.reference(fragment); verifyFragment(child,root);
                pass("last_cell_assembly_inherits_before_parent_is_retired");
                check(fragment.getLastSerializationPointer()==null,"fixture child was already saved");
                var pose=StructureSpaces.resolve(server,child).orElseThrow().pose();
                var center=pose.localToWorldPoint(new net.minecraft.world.phys.Vec3(.5,.5,.5));
                var floor=BlockPos.containing(center).below(4);
                restoredCell=floor.above();
                for(var pos:BlockPos.betweenClosed(floor.offset(-3,0,-3),floor.offset(3,0,3))) {
                    player.level().getChunkAt(pos); player.level().setBlock(pos,Blocks.GLOWSTONE.defaultBlockState(),2);
                }
                player.level().getServer().getAcademyCraftServerConfig()
                        .<org.academy.internal.server.config.GenericConfig>getConfig(org.academy.internal.server.config.GenericConfig.KEY)
                        .structureRestoration.enabled=true;
                stage=10; return;
            }
            if(stage==10) {
                if(LiveStructureProvenance.metadata(server,child).isPresent()) return;
                check(grid.getSubLevel(child.id())==null,"restored fragment still active");
                int restored=0;
                for(var pos:BlockPos.betweenClosed(restoredCell.offset(-3,-1,-3),restoredCell.offset(3,2,3))) {
                    if(player.level().getBlockState(pos).is(Blocks.IRON_BLOCK)) restored++;
                }
                check(restored==1,"fragment did not restore exactly one block: "+restored);
                pass("unsaved_fragment_allocates_source_checkpoint_and_auto_restores"); done=true; write("");
            }
            if (stage == 8) {
                if (SableProvenanceLifecycle.pending(server, root)) return;
                check(LiveStructureProvenance.metadata(server, root).isEmpty(), "crashed removal not recovered");
                check(grid.getSubLevel(root.id()) == null, "deleted source revived after crash");
                verifyFragment(grandchild, child); verifyFragment(child, root);
                if (grid.getSubLevel(grandchild.id()) == null) return;
                pass("crash_after_backend_remove_replays_exact_retirement");
                pass("saved_nested_fragment_survives_restart_without_parent");
                done = true; write("");
            }
        } catch (Throwable failure) {
            failed = true; AcademyCraft.LOGGER.error("Sable provenance smoke failed", failure); write(failure.toString());
        }
    }

    private static UUID capture(BlockPos origin, int count, boolean marked) {
        var cells = new ArrayList<BlockPos>();
        for (int i=0;i<count;i++) { var pos=origin.east(i); player.level().setBlock(pos,Blocks.IRON_BLOCK.defaultBlockState(),2); cells.add(pos); }
        var request = new StructureCaptureRequest(cells, 8, 8, StructureCaptureRequest.ALLOW, true);
        if (marked) request=request.withSkillCreation(player.getUUID(),AcademyCraft.academy("kinetic_throw"));
        var result=StructureTransactions.capture(player.level(),request);
        check(result.succeeded() || result.pending(),"capture rejected: "+result); return result.transactionId();
    }
    private static ServerSubLevel split(List<BlockPos> positions) {
        var child=SubLevelAssemblyHelper.assembleBlocks(player.level(),positions.getFirst(),positions,BoundingBox3i.from(positions).expand(1,1,1));
        check(child!=null && !child.isRemoved(),"assembly failed"); child.updateBoundingBox(); return child;
    }
    private static void verifyFragment(StructureRef reference, StructureRef parent) {
        var metadata=LiveStructureProvenance.metadata(player.level().getServer(),reference).orElseThrow();
        check(metadata.source()==StructureProvenance.CreationKind.SKILL_FRAGMENT && metadata.parent().orElseThrow().equals(parent)
                && metadata.rootLineageId().equals(original.rootLineageId()) && metadata.ownerId().equals(original.ownerId())
                && metadata.skillId().equals(original.skillId()) && metadata.sourceGridRotation().equals(original.sourceGridRotation())
                && metadata.restorePolicy().equals(original.restorePolicy()),"wrong fragment metadata");
    }
    private static void writeRestart() throws Exception {
        var tag=new net.minecraft.nbt.CompoundTag();
        tag.put("root",org.academy.internal.common.structure.upgrade.lifecycle.StructureProvenanceCodec.encodeReference(root));
        tag.put("child",org.academy.internal.common.structure.upgrade.lifecycle.StructureProvenanceCodec.encodeReference(child));
        tag.put("grandchild",org.academy.internal.common.structure.upgrade.lifecycle.StructureProvenanceCodec.encodeReference(grandchild));
        tag.put("original",org.academy.internal.common.structure.upgrade.lifecycle.StructureProvenanceCodec.encode(original));
        net.minecraft.nbt.NbtIo.writeCompressed(tag,directory.resolve("provenance-restart.dat"));
    }
    private static void readRestart() throws Exception {
        var tag=net.minecraft.nbt.NbtIo.readCompressed(directory.resolve("provenance-restart.dat"),net.minecraft.nbt.NbtAccounter.create(65536));
        root=org.academy.internal.common.structure.upgrade.lifecycle.StructureProvenanceCodec.decodeReference(tag.getCompound("root").orElseThrow());
        child=org.academy.internal.common.structure.upgrade.lifecycle.StructureProvenanceCodec.decodeReference(tag.getCompound("child").orElseThrow());
        grandchild=org.academy.internal.common.structure.upgrade.lifecycle.StructureProvenanceCodec.decodeReference(tag.getCompound("grandchild").orElseThrow());
        original=org.academy.internal.common.structure.upgrade.lifecycle.StructureProvenanceCodec.decode(tag.getCompound("original").orElseThrow());
    }
    private static void pass(String name) { PASSED.add(name); AcademyCraft.LOGGER.info("SABLE_PROVENANCE_PASS {}",name); }
    private static void check(boolean value,String message) { if(!value) throw new IllegalStateException(message); }
    private static void write(String failure) {
        try { Files.writeString(directory.resolve("sable-provenance-"+MODE+"-result.json"),new GsonBuilder().setPrettyPrinting().create()
                .toJson(Map.of("passed",!failed,"mode",MODE,"failure",failure,"cases",PASSED,"ticks",ticks))); }
        catch(Exception error) { AcademyCraft.LOGGER.error("Lifecycle report failed",error); }
    }
    @SubscribeEvent public static void client(ClientTickEvent.Post event) {
        if(!ENABLED || closing) return; var minecraft=Minecraft.getInstance();
        if(done || failed) { closing=true; minecraft.stop(); return; }
        if(minecraft.player==null || minecraft.level==null) return;
        if(minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) minecraft.gui.setScreen(null);
        directory=minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        check(directory.getFileName().toString().startsWith("native-sublevel-direct-smoke"),"isolated world required");
        if(!ready) org.lwjgl.glfw.GLFW.glfwIconifyWindow(minecraft.getWindow().handle()); ready=true;
    }
}
