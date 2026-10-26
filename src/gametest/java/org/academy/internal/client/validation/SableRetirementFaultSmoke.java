package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.internal.common.compat.SableContainerMixinGate;
import org.academy.internal.common.structure.sable.*;
import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.ServerSubLevel;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.HoldingSubLevel;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelRemovalReason;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.holding.SubLevelHoldingChunk;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.region.SubLevelStorageFile;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.serialization.SubLevelData;
import org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Destructive faults are confined to the newly captured test source in an isolated copied world. */
@EventBusSubscriber(modid=AcademyCraft.MOD_ID, value=Dist.CLIENT)
public final class SableRetirementFaultSmoke {
    private static final boolean ENABLED=Boolean.getBoolean("academy.sableRetirementFaultSmoke");
    private static final String COMMAND="academy debug structure sable_provenance";
    private static final List<String> CASES=List.of("empty_durable_index","empty_cached_index","holding_pointer_conflict",
            "live_foreign_image","closed_storage_channel","storage_header_write_failure","malformed_storage_image",
            "journal_completion_failure","foreign_slot_reused");
    private static final List<Map<String,Object>> RESULTS=new ArrayList<>();
    private static volatile boolean ready,done,failed,closing;
    private static Path directory;
    private static ServerPlayer player;
    private static BlockPos seed;
    private static int age,caseTicks,scenario,phase;
    private static UUID capture;
    private static StructureRef reference,helper;
    private static GlobalSavedSubLevelPointer pointer;
    private static SubLevelStorageFile file;
    private static CompoundTag image,foreign;
    private static HoldingSubLevel injectedHolding;
    private static boolean closed,helperDeleted,commandsChecked;
    private static java.nio.channels.SeekableByteChannel journalLock;
    private static java.nio.channels.FileChannel headerChannel;
    private static java.nio.channels.FileLock headerLock;
    private static SableProvenanceLifecycle.Observation blocked;

    private static String name(){return CASES.get(scenario);}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        if(!ENABLED || !ready || done || failed || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            check(++age<5000 && ++caseTicks<800,"fault fixture timeout "+scenario+" phase="+phase);
            if(player==null) {
                player=event.getServer().getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.CREATIVE); player.setNoGravity(true);
                if(Boolean.getBoolean("academy.sableRetirementCorruptJournal")) { phase=-1; return; }
                if(SableContainerMixinGate.state()!=SableContainerMixinGate.State.VERIFIED) { nativeOnly(); return; }
                player.level().getServer().getAcademyCraftServerConfig()
                        .<org.academy.internal.server.config.GenericConfig>getConfig(org.academy.internal.server.config.GenericConfig.KEY)
                        .structureRestoration.enabled=false;
                seed=new BlockPos(player.getBlockX()+6,player.level().getMaxY()-48,player.getBlockZ());
                for(var pos:BlockPos.betweenClosed(seed.offset(-4,-2,-3),seed.offset(8,3,4))) {
                    player.level().getChunkAt(pos); check(player.level().getBlockState(pos).isAir(),"arena occupied");
                }
                player.connection.teleport(seed.getX()-3,seed.getY()+2,seed.getZ()-3,0,0);
                return;
            }
            if(phase==-1) { corruptJournal(); return; }
            var server=player.level().getServer();
            var grid=SubLevelContainer.getContainer(player.level());
            if(phase==0) {
                if(age<12) return;
                if(!commandsChecked) { commands(); pass("diagnostic_commands_are_read_only"); commandsChecked=true; }
                capture=capture(seed); phase=1; return;
            }
            if(phase==1 || phase==2) {
                var result=StructureTransactions.poll(server,capture).orElseThrow();
                if(result.pending()) return;
                check(result.succeeded(),"fixture capture failed: "+result);
                if(phase==1) {
                    reference=result.structure().orElseThrow();
                    if(scenario==0) { capture=capture(seed.east(4)); phase=2; return; }
                } else helper=result.structure().orElseThrow();
                inject(); phase=3; return;
            }
            if(phase==3) {
                var snapshot=SableProvenanceLifecycle.diagnostics(server,reference);
                if(name().equals("foreign_slot_reused")) {
                    if(SableProvenanceLifecycle.pending(server,reference)) return;
                    check(foreign.equals(file.read(pointer.subLevelIndex())),"foreign slot was overwritten");
                    check(durableIndex().getSubLevelPointers().contains(pointer.local()),"foreign index was removed");
                    check(grid.getHoldingChunkMap().queuedDeletion().contains(pointer),"foreign deletion queue was altered");
                    grid.getHoldingChunkMap().queuedDeletion().remove(pointer);
                    file.write(pointer.subLevelIndex(),(CompoundTag)null); file.flush(); setDurableIndex(false);
                    completeCase(); return;
                }
                check(!snapshot.pending().isEmpty(),"fault was accepted as retired before repair: "+name());
                var observed=snapshot.pending().getFirst();
                if(observed.attempts()==0) return;
                check(observed.status()==expected(),"wrong failure reason: "+observed);
                check(LiveStructureProvenance.metadata(server,reference).isPresent()!=name().equals("journal_completion_failure"),
                        "creation mark did not match the durable retirement stage");
                if(name().equals("live_foreign_image")) {
                    var live=grid.getSubLevel(reference.id());
                    check(live!=null && SableRestorationIsolation.hidden(live),"conflicting live source was not retained/isolated");
                    check(foreign.equals(file.read(pointer.subLevelIndex())),"live conflict overwrote foreign image");
                }
                if(blocked==null) commands();
                if(blocked==null) {
                    blocked=observed;
                    if(helper!=null) {
                        grid.removeSubLevel(grid.getSubLevel(helper.id()),SubLevelRemovalReason.REMOVED);
                        helperDeleted=true;
                    }
                }
                // Wait for an actual retry. The second equal failure must schedule a longer delay.
                if(observed.attempts()<2) return;
                check(observed.nextTick()-observed.lastTick()>=40,"repeated failure did not back off");
                if(helperDeleted) {
                    if(SableProvenanceLifecycle.pending(server,helper)) return;
                    check(LiveStructureProvenance.metadata(server,helper).isEmpty(),"blocked source starved independent retirement");
                    pass("blocked_source_does_not_starve_other_retirement");
                }
                repair(); phase=4; return;
            }
            if(phase==4) {
                if(SableProvenanceLifecycle.pending(server,reference)) return;
                completeCase();
            }
        } catch(Throwable failure) {
            failed=true; AcademyCraft.LOGGER.error("Sable retirement fault fixture failed",failure);
            try { reopen(); } catch(Throwable cleanup) { failure.addSuppressed(cleanup); }
            try { if(journalLock!=null) journalLock.close(); } catch(Throwable cleanup) { failure.addSuppressed(cleanup); }
            try { unlockHeader(); } catch(Throwable cleanup) { failure.addSuppressed(cleanup); }
            write(failure.toString());
        }
    }

    private static UUID capture(BlockPos pos) {
        player.level().setBlock(pos,Blocks.IRON_BLOCK.defaultBlockState(),2);
        var result=StructureTransactions.capture(player.level(),new StructureCaptureRequest(List.of(pos),1,1,StructureCaptureRequest.ALLOW,true)
                .withSkillCreation(player.getUUID(),AcademyCraft.academy("kinetic_throw")));
        check(result.succeeded() || result.pending(),"capture admission rejected"); return result.transactionId();
    }
    private static void inject() throws Exception {
        var grid=SubLevelContainer.getContainer(player.level());
        var ship=(ServerSubLevel)grid.getSubLevel(reference.id());
        pointer=ship.getLastSerializationPointer(); check(pointer!=null,"fixture source unsaved");
        file=grid.getHoldingChunkMap().getStorage().getRegionStorageFile(pointer.chunkPos(),pointer.storageIndex());
        check(file.getPath().toAbsolutePath().normalize().startsWith(directory),"fault target outside isolated run");
        image=file.read(pointer.subLevelIndex()).copy();
        check(SubLevelData.fromTag(image).uuid().equals(reference.id()),"fixture owns wrong slot");
        foreign=image.copy(); foreign.put("uuid",UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE,UUID.randomUUID()).getOrThrow());
        if(name().equals("live_foreign_image")) {
            SableProvenanceLifecycle.removing(grid,ship.getPlot().plotPos().x()-grid.originX(),
                    ship.getPlot().plotPos().z()-grid.originZ(),SubLevelRemovalReason.REMOVED);
        } else grid.removeSubLevel(ship,SubLevelRemovalReason.REMOVED);
        check(SableProvenanceLifecycle.pending(player.level().getServer(),reference),"missing test removal intent");
        grid.getHoldingChunkMap().queuedDeletion().remove(pointer);
        switch(name()) {
            case "empty_durable_index" -> { file.write(pointer.subLevelIndex(),(CompoundTag)null); file.flush(); setDurableIndex(true); }
            case "empty_cached_index" -> {
                file.write(pointer.subLevelIndex(),(CompoundTag)null); file.flush(); setDurableIndex(false);
                var cached=grid.getHoldingChunkMap().loadedHoldingChunks().get(pointer.chunkPos().pack());
                check(cached!=null,"fixture requires cached holding chunk"); cached.getSubLevelPointers().add(pointer.local());
            }
            case "holding_pointer_conflict" -> {
                var wrong=GlobalSavedSubLevelPointer.create(pointer.chunkPos(),pointer.storageIndex(),(short)(pointer.subLevelIndex()+1));
                injectedHolding=SableReflection.construct(HoldingSubLevel.class,new Class<?>[]{SubLevelData.class,GlobalSavedSubLevelPointer.class},
                        SubLevelData.fromTag(image),wrong);
                rawHolding().put(reference.id(),SableReflection.unwrap(injectedHolding));
            }
            case "live_foreign_image" -> { file.write(pointer.subLevelIndex(),foreign); file.flush(); }
            case "closed_storage_channel" -> { file.close(); closed=true; }
            case "storage_header_write_failure" -> {
                headerChannel=java.nio.channels.FileChannel.open(file.getPath(),java.nio.file.StandardOpenOption.READ,java.nio.file.StandardOpenOption.WRITE);
                headerLock=headerChannel.lock(0,4096,false);
                check(image.equals(file.read(pointer.subLevelIndex())),"header lock also blocked the source payload read");
            }
            case "malformed_storage_image" -> { file.write(pointer.subLevelIndex(),new CompoundTag()); file.flush(); }
            case "journal_completion_failure" -> {
                var journalDirectory=player.level().getServer().getWorldPath(LevelResource.ROOT).resolve("data/academy/sable_provenance_retirements");
                Path owned=null;
                try(var paths=Files.list(journalDirectory)) {
                    for(var candidate:paths.filter(path -> path.toString().endsWith(".dat")).toList()) {
                        var tag=net.minecraft.nbt.NbtIo.readCompressed(candidate,net.minecraft.nbt.NbtAccounter.create(65536));
                        if(org.academy.internal.common.structure.upgrade.lifecycle.StructureProvenanceCodec
                                .decodeReference(tag.getCompound("reference").orElseThrow()).equals(reference)) owned=candidate;
                    }
                }
                check(owned!=null && owned.toAbsolutePath().normalize().startsWith(directory),"fixture journal identity missing");
                journalLock=Files.newByteChannel(owned,Set.of(java.nio.file.StandardOpenOption.READ,com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE));
            }
            case "foreign_slot_reused" -> {
                file.write(pointer.subLevelIndex(),foreign); file.flush(); setDurableIndex(true);
                @SuppressWarnings("unchecked") var queued=(Set<Object>)SableReflection.readField(grid.getHoldingChunkMap(),"queuedDeletion");
                queued.add(SableReflection.unwrap(pointer));
            }
        }
    }
    private static SableRetirementStatus expected() {
        return switch(name()) {
            case "empty_durable_index" -> SableRetirementStatus.EMPTY_SLOT_DURABLE_INDEX;
            case "empty_cached_index" -> SableRetirementStatus.EMPTY_SLOT_CACHED_INDEX;
            case "holding_pointer_conflict" -> SableRetirementStatus.HOLDING_POINTER_CONFLICT;
            case "live_foreign_image" -> SableRetirementStatus.LIVE_POINTER_CONFLICT;
            case "journal_completion_failure" -> SableRetirementStatus.JOURNAL_COMPLETION_FAILED;
            default -> SableRetirementStatus.STORAGE_FAILURE;
        };
    }
    private static SubLevelHoldingChunk durableIndex() throws Exception {
        var region=SubLevelContainer.getContainer(player.level()).getHoldingChunkMap().getStorage().getRegionFile(pointer.chunkPos());
        var tag=region.read(region.getIndex(pointer.chunkPos().getRegionLocalX(),pointer.chunkPos().getRegionLocalZ()));
        return tag==null ? SubLevelHoldingChunk.create(pointer.chunkPos()) : SubLevelHoldingChunk.from(pointer.chunkPos(),tag);
    }
    private static void setDurableIndex(boolean present) throws Exception {
        var region=SubLevelContainer.getContainer(player.level()).getHoldingChunkMap().getStorage().getRegionFile(pointer.chunkPos());
        var holding=durableIndex();
        holding.getSubLevelPointers().remove(pointer.local());
        if(present) holding.getSubLevelPointers().add(pointer.local());
        var tag=new CompoundTag(); holding.writeTo(tag);
        region.write(region.getIndex(pointer.chunkPos().getRegionLocalX(),pointer.chunkPos().getRegionLocalZ()),tag); region.flush();
    }
    private static void repair() throws Exception {
        var holding=SubLevelContainer.getContainer(player.level()).getHoldingChunkMap();
        switch(name()) {
            case "empty_durable_index" -> setDurableIndex(false);
            case "empty_cached_index" -> holding.loadedHoldingChunks().get(pointer.chunkPos().pack()).getSubLevelPointers().remove(pointer.local());
            case "holding_pointer_conflict" -> check(rawHolding().remove(reference.id(),SableReflection.unwrap(injectedHolding)),"fixture holding changed owner");
            case "closed_storage_channel" -> reopen();
            case "storage_header_write_failure" -> unlockHeader();
            case "journal_completion_failure" -> { journalLock.close(); journalLock=null; }
            default -> { file.write(pointer.subLevelIndex(),image); file.flush(); }
        }
    }
    private static void unlockHeader() throws Exception {
        if(headerLock!=null) { headerLock.release(); headerLock=null; }
        if(headerChannel!=null) { headerChannel.close(); headerChannel=null; }
    }
    @SuppressWarnings("unchecked") private static Map<UUID,Object> rawHolding() {
        return (Map<UUID,Object>)SableReflection.readField(SubLevelContainer.getContainer(player.level()).getHoldingChunkMap(),"allHoldingSubLevels");
    }
    @SuppressWarnings("unchecked") private static void reopen() throws Exception {
        if(!closed) return;
        var storage=SubLevelContainer.getContainer(player.level()).getHoldingChunkMap().getStorage();
        var cache=(Map<Long,Object>)SableReflection.readField(storage,"storageCache");
        check(cache.values().removeIf(value -> value==SableReflection.unwrap(file)),"closed fixture channel no longer cached");
        file=storage.getRegionStorageFile(pointer.chunkPos(),pointer.storageIndex()); closed=false;
        check(image.equals(file.read(pointer.subLevelIndex())),"channel fault damaged saved image");
    }
    private static void commands() throws Exception {
        var server=player.level().getServer();
        var before=SableProvenanceLifecycle.diagnostics(server,reference);
        var source=server.createCommandSourceStack();
        check(server.getCommands().getDispatcher().execute(COMMAND,source)>0,"summary command rejected");
        check(server.getCommands().getDispatcher().execute(COMMAND+" "+(reference==null?UUID.randomUUID():reference.id()),source)>0,"identity command rejected");
        check(before.equals(SableProvenanceLifecycle.diagnostics(server,reference)),"diagnostics retried or changed work");
        try {
            server.getCommands().getDispatcher().execute(COMMAND,source.withPermission(net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS));
            throw new IllegalStateException("unprivileged diagnostics accepted");
        } catch(CommandSyntaxException expected) { /* Parent and child are administrator-only. */ }
    }
    private static void nativeOnly() throws Exception {
        var server=player.level().getServer();
        var path=server.getWorldPath(LevelResource.ROOT).resolve("data/academy/sable_provenance_retirements");
        check(!Files.exists(path),"fresh native world has Sable retirement data");
        var snapshot=SableProvenanceLifecycle.diagnostics(server,null);
        check(snapshot.backend().equals("ABSENT") && snapshot.state().equals("backend_unavailable"),"missing backend misreported");
        pass("absent_backend_reported"); commands(); pass("commands_read_only_and_permission_checked");
        check(!Files.exists(path),"native diagnostics created Sable ledger"); pass("no_backend_no_world_file_creation");
        done=true; write("");
    }
    private static void corruptJournal() throws Exception {
        var server=player.level().getServer();
        var snapshot=SableProvenanceLifecycle.diagnostics(server,null);
        if(snapshot.state().equals("not_initialized")) return;
        check(snapshot.backend().equals("VERIFIED") && snapshot.state().equals("journal_unhealthy"),"corrupt journal was not diagnosed");
        check(snapshot.problemCount()==1 && snapshot.problems().size()==1
                && snapshot.problems().getFirst().file().equals("injected-corrupt.dat"),"missing corrupt file identity");
        pass("corrupt_journal_reports_bounded_problem");
        var path=server.getWorldPath(LevelResource.ROOT).resolve("data/academy/sable_provenance_retirements/injected-corrupt.dat");
        var before=Files.readAllBytes(path); commands();
        check(Arrays.equals(before,Files.readAllBytes(path)),"diagnostic changed corrupt evidence");
        pass("corrupt_evidence_preserved_by_command");
        check(snapshot.pending().isEmpty() && snapshot.recent().isEmpty(),"corrupt bytes became deletion authority");
        pass("corrupt_record_does_not_create_deletion_authority"); done=true; write("");
    }
    private static void completeCase() throws Exception {
        var server=player.level().getServer();
        check(LiveStructureProvenance.metadata(server,reference).isEmpty(),"completed source retains creation mark");
        var recent=SableProvenanceLifecycle.diagnostics(server,reference).recent();
        check(recent.stream().anyMatch(value -> value.status()==SableRetirementStatus.RETIRED),"missing completion observation");
        if(name().equals("storage_header_write_failure")) {
            try(var disk=java.nio.channels.FileChannel.open(file.getPath(),java.nio.file.StandardOpenOption.READ)) {
                var span=java.nio.ByteBuffer.allocate(4); check(disk.read(span,pointer.subLevelIndex()*4L)==4,"short on-disk header");
                span.flip(); check(span.getInt()==0,"retired receipt relied on cache instead of clearing the on-disk slot");
            }
        }
        pass(name()); scenario++; phase=0; caseTicks=0; reference=null; helper=null; injectedHolding=null; blocked=null; helperDeleted=false;
        if(scenario==CASES.size()) { done=true; write(""); }
    }
    private static void pass(String name) {
        RESULTS.add(Map.of("case",name,"passed",true,"tick",age)); AcademyCraft.LOGGER.info("SABLE_RETIREMENT_FAULT_PASS {}",name);
    }
    private static void check(boolean value,String message) { if(!value) throw new IllegalStateException(message); }
    private static void write(String failure) {
        try { Files.writeString(directory.resolve("sable-retirement-fault-result.json"),new GsonBuilder().setPrettyPrinting().create()
                .toJson(Map.of("passed",!failed,"failure",failure,"cases",RESULTS,"ticks",age))); }
        catch(Exception error) { AcademyCraft.LOGGER.error("Retirement fault report failed",error); }
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
