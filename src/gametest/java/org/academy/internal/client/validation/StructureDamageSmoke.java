package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
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
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.api.common.structure.upgrade.*;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv2.KineticEnergyApplied;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.upgrade.damage.DamageDomain;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;

import java.nio.file.*;
import java.util.*;

/** Real callbacks, inventory, XP and skill queues in a fresh copied world, on either backend. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructureDamageSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.structureDamageSmoke");
    private static final List<String> CASES = List.of("iron_drop", "iron_no_drop", "hollow", "slab_gap", "plant",
            "chest", "break_cancel", "replace_during_break", "loot_cancel", "loot_modify_xp",
            "forged_and_replay", "bounded", "stale_payload", "precision", "ordinary",
            "rotated_slab_gap", "rotated_chest", "stale_pose", "host_and_structure", "structure_click",
            "locked_payload", "stale_lock");
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static volatile boolean ready, done, failed, closing;
    private static Path directory;
    private static ServerPlayer player;
    private static BlockPos seed;
    private static StructureRef reference;
    private static DamageDomain domain;
    private static UUID transaction;
    private static UUID payloadLock;
    private static StructureDamage.ActiveActor authority;
    private static StructureDamage.Batch batch;
    private static StructureDamage.Request request;
    private static Vec3 center;
    private static int scenario = Integer.getInteger("academy.damageCaseStart", 0), phase, ticks, age, breaks, lootEvents, previousBroken;

    private static String name() { return CASES.get(scenario); }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || !ready || done || failed || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            check(++age < 3000, "fixture deadline"); ticks++;
            if (player == null) {
                player = event.getServer().getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
                DestroyBlocksSetting.setDestroyBlocksEnabled(player, true);
                var system = AbilitySystemServer.getSystem(player);
                system.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.ACCELERATOR.get());
                system.setPlayerLevel(player.getUUID(), 5);
                system.setPlayerBaseMaxCP(player.getUUID(), 100000); system.setPlayerAvailableCP(player.getUUID(), 100000);
                system.setPlayerStatus(player.getUUID(), org.academy.api.common.data.AbilityData.Status.NORMAL);
                var skill = Skills.KINETIC_ENERGY_APPLIED.get(); system.addPlayerSkill(player, skill.getKeyString());
                if (!skill.isEnabled(player)) system.toggleSkill(player.getUUID(), skill.getKeyString());
                player.setData(AttachmentTypes.KINETIC_BLOCK_BREAK_ENABLED.get(), true);
                player.setData(AttachmentTypes.KINETIC_BLOCK_DROPS_ENABLED.get(), true);
            }
            player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance();
            if (phase == 0) {
                seed = new BlockPos(120 + scenario * 16, 200, 120);
                player.connection.teleport(seed.getX() - 5, seed.getY(), seed.getZ(), 0, 0);
                for (var pos : BlockPos.betweenClosed(seed.offset(-8,-8,-8), seed.offset(8,8,8))) {
                    player.level().getChunkAt(pos); player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                phase = 1; ticks = 0; return;
            }
            if (phase == 1) {
                if (ticks < 25) return;
                var positions = new ArrayList<BlockPos>(); positions.add(seed);
                var state = name().contains("chest") ? Blocks.CHEST.defaultBlockState()
                        : name().contains("slab_gap") ? Blocks.OAK_SLAB.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState();
                player.level().setBlock(seed, state, 2);
                if (name().contains("chest")) ((Container) player.level().getBlockEntity(seed)).setItem(0, new ItemStack(Items.DIAMOND, 19));
                if (Set.of("iron_drop","iron_no_drop","hollow","bounded").contains(name())) {
                    positions.add(seed.east(2)); player.level().setBlock(seed.east(2), Blocks.IRON_BLOCK.defaultBlockState(),2);
                }
                if (name().equals("plant")) {
                    player.level().setBlock(seed, Blocks.DIRT.defaultBlockState(),2);
                    positions.add(seed.above()); player.level().setBlock(seed.above(), Blocks.SHORT_GRASS.defaultBlockState(),2);
                }
                var capture = StructureTransactions.capture(player.level(), new StructureCaptureRequest(positions, 16, 8, StructureCaptureRequest.ALLOW, true));
                check(capture.succeeded() || capture.pending(), "capture rejected: " + capture);
                transaction = capture.transactionId(); reference = capture.structure().orElse(null);
                phase = 2; ticks = 0; return;
            }
            if (phase == 2) {
                // A pending capture can already expose its reserved reference. Adoption must
                // wait for the committed result, not merely for a resolvable seed block.
                var capture = StructureTransactions.poll(event.getServer(), transaction).orElseThrow();
                if (capture.pending()) {
                    var actor=new StructureDamage.ActiveActor(new StructureOperation.ActorContext(player.level(),player));
                    var query=StructureDamages.service().query(actor,new StructureDamage.Request(UUID.randomUUID(),Vec3.atCenterOf(seed),3,1,32768,32,StructureDamage.DropPolicy.NO_DROPS));
                    check(query.value().orElseThrow().cells().isEmpty(),"pending source or target admitted for damage");
                    StructureDamages.service().cancel(actor,query.operationId().orElseThrow());
                    return;
                }
                check(capture.succeeded(), "capture failed: " + capture); reference = capture.structure().orElseThrow();
                check(player.level().getBlockState(seed).isAir(),"capture source still present");
                var handle = StructureSpaces.resolve(event.getServer(), reference).orElseThrow();
                center = handle.pose().localToWorldPoint(new Vec3(.5,.5,.5));
                domain = StructureBackendRouter.damageDomains(player.level(), new AABB(center,center).inflate(8), 256)
                        .stream().filter(value -> value.handle().reference().equals(reference)).findFirst().orElseThrow();
                if (name().startsWith("rotated_")) { rotateFixture(); center=handle.pose().localToWorldPoint(new Vec3(.5,.5,.5)); }
                if (name().equals("host_and_structure")) {
                    player.level().setBlock(seed.east(2),Blocks.IRON_BLOCK.defaultBlockState(),2);
                    player.level().setBlock(seed.west(2),Blocks.BEDROCK.defaultBlockState(),2);
                }
                authority = new StructureDamage.ActiveActor(new StructureOperation.ActorContext(player.level(),player));
                breaks = lootEvents = 0;
                previousBroken = 0;
                if (name().equals("bounded")) check(nearbyIronCells()==2,"split child left loaded world before damage");
                if (name().equals("locked_payload")) lockPayload();
                if (name().equals("structure_click")) {
                    var local=new Vec3(.5,.5,.5);
                    var hit=new StructureHit(new SpaceBlockRef(reference,BlockPos.ZERO),handle.runtimeId(),handle.geometryRevision(),
                            handle.pose(),net.minecraft.core.Direction.UP,local,center,false);
                    StructureContexts.run(handle,context -> KineticEnergyApplied.Events.onStructureMining(new StructureMiningEvent(
                            player,hit,context,StructureMiningEvent.Phase.START,handle.world().read(BlockPos.ZERO),ItemStack.EMPTY)));
                    phase=4; ticks=0; return;
                }
                if (name().equals("precision") || name().equals("ordinary")) {
                    boolean accepted = name().equals("precision")
                            ? KineticEnergyApplied.Server.executeConfiguredProgramImpact(player,center,new Vec3(0,1,0),1,true,3)
                            : KineticEnergyApplied.Server.tryExecuteProgramImpact(player,center,new Vec3(0,1,0),1);
                    check(accepted,"skill rejected"); phase = 4; ticks = 0; return;
                }
                double radius = 3;
                if (name().equals("hollow")) { center = handle.pose().localToWorldPoint(new Vec3(1.5,.5,.5)); radius=.2; }
                if (name().contains("slab_gap")) { center = handle.pose().localToWorldPoint(new Vec3(.5,.9,.5)); radius=.1; }
                if (name().equals("plant")) { center = handle.pose().localToWorldPoint(new Vec3(.5,1.5,.5)); radius=.1; }
                request = new StructureDamage.Request(UUID.randomUUID(), center, radius, 1, 32768,
                        name().equals("bounded") ? 1 : 32,
                        name().equals("iron_no_drop") ? StructureDamage.DropPolicy.NO_DROPS : StructureDamage.DropPolicy.EXISTING_SKILL_RULES);
                var query = StructureDamages.service().query(authority,request);
                check(query.value().isPresent(),"query failed: " + query); batch = query.value().orElseThrow();
                if (name().equals("locked_payload")) check(batch.cells().isEmpty(),"locked source or target entered query");
                if (name().equals("forged_and_replay")) {
                    check(StructureDamages.service().apply(authority,new StructureDamage.Batch(batch.planId(),request,batch.cells())).status()
                            == StructureOperation.Status.STALE,"copied plan accepted");
                    check(StructureDamages.service().query(authority,request).status()==StructureOperation.Status.CONFLICT,"effect resubmitted");
                }
                if (name().equals("stale_payload")) domain.storage().setBlock(domain.position(BlockPos.ZERO),Blocks.GOLD_BLOCK.defaultBlockState(),2);
                if (name().equals("stale_pose")) rotateFixture();
                if (name().equals("stale_lock")) lockPayload();
                apply(); phase = 3; ticks = 0;
            }
            if (phase == 3) {
                var progress = StructureDamages.service().status(authority,request.effectId()).value().orElseThrow();
                check(progress.phase()!=StructureDamage.Phase.RECOVERY_REQUIRED,"unexpected recovery: " + progress);
                if (progress.phase()==StructureDamage.Phase.READY) { batch=progress.preparedBatch().orElseThrow(); apply(); return; }
                if (progress.phase()!=StructureDamage.Phase.COMPLETED) return;
                verify(); finish();
            } else if (phase == 4) {
                if (ticks < 3) return;
                check(domain.storage().getBlockState(domain.position(BlockPos.ZERO)).isAir(),"skill left structure cell");
                check(breaks>0,"skill never entered shared break pipeline"); finish();
            }
        } catch (Throwable failure) {
            failed=true; AcademyCraft.LOGGER.error("STRUCTURE_DAMAGE_SMOKE_FAILED {}",name(),failure); write(failure.toString());
        }
    }
    private static void apply() {
        var result = StructureDamages.service().apply(authority,batch);
        check(result.value().isPresent(),"apply rejected: " + result);
        check(result.status()!=StructureOperation.Status.BLOCKED,"apply needs recovery: " + result);
        if (name().equals("forged_and_replay")) check(StructureDamages.service().apply(authority,batch).status()==StructureOperation.Status.STALE,"plan replayed");
        if (name().equals("bounded")) {
            int broken=result.value().orElseThrow().broken();
            check(broken-previousBroken<=1,"single-change budget exceeded"); previousBroken=broken;
        }
    }
    private static void verify() {
        var state=domain.storage().getBlockState(domain.position(BlockPos.ZERO));
        boolean preserved=Set.of("hollow","slab_gap","rotated_slab_gap","plant","break_cancel","replace_during_break","stale_payload","stale_pose","locked_payload","stale_lock").contains(name());
        check(preserved ? !state.isAir() : state.isAir(),"unexpected source state: " + state);
        if (name().equals("plant")) check(domain.storage().getBlockState(domain.position(BlockPos.ZERO.above())).isAir(),"non-collidable plant missed");
        if (name().equals("replace_during_break") || name().equals("stale_payload")) check(state.is(Blocks.GOLD_BLOCK),"replacement overwritten");
        var items=player.level().getEntitiesOfClass(ItemEntity.class,new AABB(center,center).inflate(6));
        int iron=items.stream().filter(item->item.getItem().is(Items.IRON_BLOCK)).mapToInt(item->item.getItem().getCount()).sum();
        if (name().equals("iron_drop") || name().equals("bounded") || name().equals("host_and_structure")) check(iron==2,"iron conservation: " + iron
                + "; progress=" + StructureDamages.service().status(authority,request.effectId())
                + "; remaining=" + StructureBackendRouter.damageDomains(player.level(),new AABB(center,center).inflate(12),256).stream()
                    .map(value -> value.handle().reference()+"@"+value.handle().pose()+"/"+value.localBounds()).toList());
        if (name().equals("host_and_structure")) {
            check(player.level().getBlockState(seed.east(2)).isAir(),"host block missed");
            check(player.level().getBlockState(seed.west(2)).is(Blocks.BEDROCK),"unbreakable host block removed");
        }
        if (Set.of("iron_no_drop","break_cancel","loot_cancel","replace_during_break","stale_payload","hollow","slab_gap").contains(name())) check(items.isEmpty(),"unexpected loot: " + items);
        if (name().contains("chest")) check(items.stream().filter(item->item.getItem().is(Items.DIAMOND)).mapToInt(item->item.getItem().getCount()).sum()==19,"chest inventory conservation");
        if (payloadLock!=null) {
            check(items.isEmpty() && breaks==0,"locked content emitted callbacks or loot");
            check(player.level().getBlockState(seed.east(2)).is(Blocks.IRON_BLOCK),"locked host source changed");
        }
        if (name().equals("loot_modify_xp")) {
            check(items.stream().filter(item->item.getItem().is(Items.EMERALD)).mapToInt(item->item.getItem().getCount()).sum()==2,"drop event replacement lost");
            check(player.level().getEntitiesOfClass(ExperienceOrb.class,new AABB(center,center).inflate(6)).stream().mapToInt(ExperienceOrb::getValue).sum()==7,"XP event lost");
        }
    }
    private static void lockPayload() {
        payloadLock=UUID.randomUUID();
        player.level().setBlock(seed.east(2),Blocks.IRON_BLOCK.defaultBlockState(),2);
        org.academy.internal.common.structure.transaction.PayloadIsolation.lock(player.level(),List.of(seed.east(2)),payloadLock);
        org.academy.internal.common.structure.transaction.PayloadIsolation.lock(domain.storage(),List.of(domain.position(BlockPos.ZERO)),payloadLock);
    }
    private static int nearbyIronCells() {
        int count=0;
        for (var candidate : StructureBackendRouter.damageDomains(player.level(),new AABB(center,center).inflate(4),32)) {
            var b=candidate.localBounds();
            check(b.getXsize()*b.getYsize()*b.getZsize()<=32,"unexpected fixture body");
            for (var local : BlockPos.betweenClosed(BlockPos.containing(b.minX,b.minY,b.minZ),BlockPos.containing(b.maxX-1,b.maxY-1,b.maxZ-1))) {
                if(candidate.handle().world().read(local).state().is(Blocks.IRON_BLOCK)) count++;
            }
        }
        return count;
    }
    /** Fixture-only pose injection makes the pre/apply stale-pose check deterministic. */
    private static void rotateFixture() {
        var rotation=new org.joml.Quaterniond().rotateXYZ(.3,.7,.2);
        if (org.academy.internal.common.sublevel.SubLevelStructureRefs.isSubLevel(reference)) {
            var runtime=org.academy.internal.common.sublevel.SubLevelServer.runtime(player.level()).orElseThrow();
            check(runtime.rotateTo(new org.academy.internal.common.sublevel.SubLevelId(reference.id()),new StructurePose(Vec3.ZERO,rotation)),"fixture rotation failed");
        } else {
            var container=org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer.getContainer(player.level());
            container.getSubLevel(reference.id()).logicalPose().orientation().set(rotation);
        }
    }
    @SubscribeEvent public static void breaking(BreakBlockEvent event) {
        if (!ENABLED || authority==null || phase<2 || phase>4 || StructureContexts.current().isEmpty()) return;
        breaks++;
        check(event.getLevel()==player.level() && Vec3.atCenterOf(event.getPos()).distanceTo(center)<8,"protection received plot coordinates");
        if (name().equals("break_cancel")) event.setCanceled(true);
        if (name().equals("replace_during_break")) domain.storage().setBlock(domain.position(BlockPos.ZERO),Blocks.GOLD_BLOCK.defaultBlockState(),2);
        if (name().equals("forged_and_replay")) check(StructureDamages.service().apply(authority,batch).value().isEmpty(),"callback reentry accepted");
    }
    @SubscribeEvent public static void drops(BlockDropsEvent event) {
        if (!ENABLED || authority==null || phase<2 || phase>4 || StructureContexts.current().isEmpty()) return;
        lootEvents++;
        if (name().equals("loot_cancel")) event.setCanceled(true);
        if (name().equals("loot_modify_xp")) { event.getDrops().getFirst().setItem(new ItemStack(Items.EMERALD,2)); event.setDroppedExperience(7); }
    }
    private static void finish() {
        if (payloadLock!=null) org.academy.internal.common.structure.transaction.PayloadIsolation.release(player.level().getServer(),payloadLock);
        payloadLock=null;
        RESULTS.add(Map.of("case",name(),"passed",true,"backend",reference.backend().toString(),"break_events",breaks,"loot_events",lootEvents));
        AcademyCraft.LOGGER.info("STRUCTURE_DAMAGE_SMOKE_PASS {}",name());
        authority=null; batch=null; domain=null; reference=null; scenario++; phase=0; ticks=0;
        if (scenario==CASES.size()) { done=true; write(""); }
    }
    private static void write(String failure) {
        try { Files.writeString(directory.resolve("structure-damage-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed",!failed,"failure",failure,"cases",RESULTS,"ticks",age))); }
        catch(Exception error) { AcademyCraft.LOGGER.error("Damage report failed",error); }
    }
    @SubscribeEvent public static void client(ClientTickEvent.Post event) {
        if (!ENABLED || closing) return;
        var minecraft=Minecraft.getInstance();
        if(done||failed) { closing=true; minecraft.stop(); return; }
        if(minecraft.player==null||minecraft.level==null) return;
        if(minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) minecraft.gui.setScreen(null);
        directory=minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        check(directory.getFileName().toString().startsWith("native-sublevel-direct-smoke"),"isolated world required");
        if(!ready) org.lwjgl.glfw.GLFW.glfwIconifyWindow(minecraft.getWindow().handle()); ready=true;
    }
    private static void check(boolean condition,String message) { if(!condition) throw new IllegalStateException(message); }
}
