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
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.meltdowner.skills.lv2.MiningBeam;
import org.academy.internal.common.ability.proficiency.ProficiencySkillSettings;
import org.academy.internal.common.ability.shared.linear.LinearSegment;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.transaction.PayloadIsolation;
import org.academy.internal.common.structure.upgrade.damage.DamageDomain;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.world.entity.skill.Plasma;

import java.nio.file.Files;
import java.util.*;

/** Actual blocks, loot callbacks and skill helpers in isolated native and mixed-suite clients. */
@EventBusSubscriber(modid=AcademyCraft.MOD_ID,value=Dist.CLIENT)
public final class StructurePathDamageSmoke {
    private static final boolean ENABLED=Boolean.getBoolean("academy.structurePathDamageSmoke");
    private static final List<String> CASES=List.of("iron", "no_drops", "simulate", "hollow", "slab_gap",
            "rotated_slab_gap", "rotated_chest", "tier_blocker", "world_blocker", "mixed_cells", "cancel",
            "replace", "loot_xp", "reentry", "locked", "silk", "fortune", "smelt", "plasma", "zero_length", "scan_budget",
            "outbound_blocker", "return_blocker", "change_budget", "fluid_silent", "program_mining", "supported_plant", "overhanging_fence");
    private static final List<Map<String,Object>> RESULTS=new ArrayList<>();
    private static volatile boolean ready,done,failed,closing;
    private static java.nio.file.Path directory;
    private static ServerPlayer player;
    private static BlockPos seed;
    private static UUID capture,lock;
    private static StructureRef reference;
    private static StructureHandle handle;
    private static DamageDomain domain;
    private static Vec3 center,start,end;
    private static int scenario=Integer.getInteger("academy.pathDamageCaseStart",0),phase,ticks,age,breaks,loot;
    private static String name() { return CASES.get(scenario); }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(!ENABLED||!ready||done||failed||event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            check(++age<4000,"fixture deadline"); ticks++;
            if(player==null) {
                player=event.getServer().getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
                DestroyBlocksSetting.setDestroyBlocksEnabled(player,true);
                var system=AbilitySystemServer.getSystem(player);
                system.setPlayerAbilityCategory(player.getUUID(),AbilityCategories.MELTDOWNER.get());
                system.setPlayerLevel(player.getUUID(),5);
                system.addPlayerSkill(player,Skills.MINING_BEAM.get().getKeyString());
                system.setPlayerSkillProficiency(player.getUUID(),Skills.MINING_BEAM.get(),3000);
                if(!Skills.MINING_BEAM.get().isEnabled(player)) system.toggleSkill(player.getUUID(),Skills.MINING_BEAM.get().getKeyString());
                system.setPlayerBaseMaxCP(player.getUUID(),100000);system.setPlayerAvailableCP(player.getUUID(),100000);
                system.setPlayerStatus(player.getUUID(),org.academy.api.common.data.AbilityData.Status.NORMAL);
                check(Skills.MINING_BEAM.get().hasProficiencyMilestone(player,3),"mining modes unavailable");
            }
            player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance();
            if(phase==0) {
                seed=new BlockPos(120+scenario*16,200,160);
                player.connection.teleport(seed.getX()-5,seed.getY(),seed.getZ(),0,0);
                for(var pos:BlockPos.betweenClosed(seed.offset(-7,-7,-7),seed.offset(7,7,7))) {
                    player.level().getChunkAt(pos); player.level().setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                }
                phase=1;ticks=0;return;
            }
            if(phase==1) {
                if(ticks<25) return;
                var state=name().contains("chest")?Blocks.CHEST.defaultBlockState():name().contains("slab")?Blocks.OAK_SLAB.defaultBlockState()
                        :Set.of("tier_blocker","outbound_blocker","return_blocker").contains(name())?Blocks.OBSIDIAN.defaultBlockState()
                        :Blocks.IRON_BLOCK.defaultBlockState();
                player.level().setBlock(seed,state,2);
                if(name().contains("chest")) ((Container)player.level().getBlockEntity(seed)).setItem(0,new ItemStack(Items.DIAMOND,19));
                var positions=new ArrayList<BlockPos>();positions.add(seed);
                if(name().equals("supported_plant")) {
                    player.level().setBlock(seed,Blocks.DIRT.defaultBlockState(),2);
                    positions.add(seed.above());player.level().setBlock(seed.above(),Blocks.SHORT_GRASS.defaultBlockState(),2);
                }
                if(name().equals("hollow")) { positions.add(seed.north(2));player.level().setBlock(seed.north(2),Blocks.IRON_BLOCK.defaultBlockState(),2); }
                var result=StructureTransactions.capture(player.level(),new StructureCaptureRequest(positions,16,8,StructureCaptureRequest.ALLOW,true));
                check(result.succeeded()||result.pending(),"capture rejected: "+result);
                capture=result.transactionId();phase=2;ticks=0;return;
            }
            if(phase==2) {
                var result=StructureTransactions.poll(event.getServer(),capture).orElseThrow();
                if(result.pending()) return;
                check(result.succeeded(),"capture failed: "+result);reference=result.structure().orElseThrow();
                handle=StructureSpaces.resolve(event.getServer(),reference).orElseThrow();
                center=handle.pose().localToWorldPoint(new Vec3(.5,.5,.5));
                domain=StructureBackendRouter.damageDomains(player.level(),new AABB(center,center).inflate(4),32).stream()
                        .filter(candidate->candidate.handle().reference().equals(reference)).findFirst().orElseThrow();
                // Ore capture is deliberately outside Sable's plain-block whitelist. Place the
                // real ore into a committed structure, just as an external build/normal edit can.
                if(Set.of("silk","fortune","smelt","program_mining").contains(name())) {
                    domain.storage().setBlock(domain.position(BlockPos.ZERO),name().equals("smelt")?Blocks.IRON_ORE.defaultBlockState():Blocks.DIAMOND_ORE.defaultBlockState(),2);
                    domain.changed();domain.flush();
                }
                if(name().equals("overhanging_fence")) {
                    domain.storage().setBlock(domain.position(BlockPos.ZERO),Blocks.OAK_FENCE.defaultBlockState(),2);
                    domain.changed();domain.flush();
                    player.level().setBlock(seed.east(2),Blocks.OAK_FENCE.defaultBlockState(),2);
                }
                if(name().startsWith("rotated")) rotate();
                center=handle.pose().localToWorldPoint(new Vec3(.5,.5,.5));
                var a=new Vec3(-2,.5,.5);var b=new Vec3(3,.5,.5);
                if(name().contains("slab")) {a=new Vec3(-2,.9,.5);b=new Vec3(3,.9,.5);}
                if(name().equals("overhanging_fence")) {a=new Vec3(-2,1.25,.5);b=new Vec3(4,1.25,.5);}
                if(name().equals("hollow")) {a=new Vec3(-2,.5,1.5);b=new Vec3(3,.5,1.5);}
                start=handle.pose().localToWorldPoint(a);end=handle.pose().localToWorldPoint(b);
                if(name().equals("zero_length")) start=end=center;
                if(name().equals("supported_plant")) {
                    start=handle.pose().localToWorldPoint(new Vec3(.5,-2,.5));end=handle.pose().localToWorldPoint(new Vec3(.5,3,.5));
                }
                if(name().equals("world_blocker")) player.level().setBlock(seed.west(),Blocks.BEDROCK.defaultBlockState(),2);
                if(name().equals("mixed_cells")) player.level().setBlock(seed.east(2),Blocks.IRON_BLOCK.defaultBlockState(),2);
                if(name().equals("locked")) {
                    lock=UUID.randomUUID();PayloadIsolation.lock(domain.storage(),List.of(domain.position(BlockPos.ZERO)),lock);
                }
                breaks=loot=0;
                if(name().equals("change_budget")) {checkChangeBudget();finish();return;}
                if(name().equals("program_mining")) {
                    player.level().setBlock(seed.east(),Blocks.BEDROCK.defaultBlockState(),2);
                    player.level().setBlock(seed.east(2),Blocks.DIAMOND_ORE.defaultBlockState(),2);
                    ProficiencySkillSettings.setMode(player,ProficiencySkillSettings.MINING_BEAM_HARVEST_MODE,MiningBeam.HarvestMode.SILK_TOUCH.ordinal());
                    var runtime=new org.academy.internal.common.ability.meltdowner.program.ServerMeltdownerProgramRuntime(player);
                    var direction=end.subtract(start).normalize();
                    var action=runtime.fireMiningBeam(new org.academy.api.common.ability.program.ProgramWorldPosition(player.level().dimension().identifier(),start.x,start.y,start.z),
                            new org.academy.api.common.ability.program.ProgramDirection(direction.x,direction.y,direction.z),null,null,1);
                    action.validate();action.apply();
                    var beams=player.level().getEntitiesOfClass(org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam.class,new AABB(center,center).inflate(8));
                    check(!beams.isEmpty(),"precision mining beam missing");
                    for(var beam:beams) {beam.setAttackDelayTicks(0);beam.tick();check(beam.getBeamLength()<5,"precision beam ignored blocking distance");beam.discard();}
                    check(player.level().getBlockState(seed.east(2)).is(Blocks.DIAMOND_ORE),"precision beam mined behind blocker");
                    verify();finish();return;
                }
                if(name().equals("plasma")) {
                    var plasma=new Plasma(org.academy.internal.common.world.entity.EntityTypes.PLASMA.get(),player.level());
                    plasma.setPos(center);plasma.launch(player.getUUID(),center,1,0,3,3,true,0);
                    check(player.level().addFreshEntity(plasma),"plasma spawn failed");phase=3;ticks=0;return;
                }
                if(Set.of("silk","fortune","smelt").contains(name())) {
                    var mode=name().equals("silk")?MiningBeam.HarvestMode.SILK_TOUCH:name().equals("fortune")?MiningBeam.HarvestMode.FORTUNE_III:MiningBeam.HarvestMode.AUTO_SMELT;
                    ProficiencySkillSettings.setMode(player,ProficiencySkillSettings.MINING_BEAM_HARVEST_MODE,mode.ordinal());
                    MiningBeam.executeMiningSegment(player.level(),new LinearSegment(start,end),.05f,false,player,player);
                } else if(name().equals("fluid_silent")) {
                    player.level().setBlock(seed.east(),Blocks.WATER.defaultBlockState(),2);
                    LevelUtil.destroyBlocksAlongPathSilently(player.level(),start,end,.05f,3,true,false,player);
                    check(player.level().getBlockState(seed.east()).isAir(),"silent fluid path retained water");
                } else if(name().equals("outbound_blocker")||name().equals("return_blocker")) {
                    var trace=LevelUtil.destroyBlocksAlongPath(player.level(),start,end,.05f,2,false,false,true,true,player);
                    var candidate=new org.academy.internal.common.ability.shared.linear.LinearReflectionCandidate(player,end,.5,.5,1,
                            end.subtract(start).normalize(),org.academy.internal.common.ability.shared.linear.LinearReflectionCandidate.Mode.REFLECTION);
                    var attack=org.academy.internal.common.ability.shared.linear.ResolvedLinearAttack.reflected(new LinearSegment(start,end),
                            new LinearSegment(start,end),new LinearSegment(start,end),candidate);
                    if(name().equals("outbound_blocker")) {
                        attack=attack.limitOutboundLength(trace.getRight());
                        check(!attack.isReflected()&&attack.outbound().length()<5,"unreached reflector survived clipping");
                    } else {
                        var limited=attack.limitReturnLength(trace.getRight());
                        check(limited.isReflected()&&limited.outbound()==attack.outbound()&&limited.returnSegment().orElseThrow().length()<5,"return clipping changed outbound");
                    }
                } else if(name().equals("scan_budget")) {
                    var actor=new StructureDamage.ActiveActor(new StructureOperation.ActorContext(player.level(),player));
                    var request=new StructurePathDamage.Request(center,center.add(1000,0,0),32,3,
                            StructureDamage.DropPolicy.NO_DROPS,false,true,true,false,false,null);
                    var trace=StructureDamages.service().path(actor,request);
                    check(trace.status()==StructureOperation.Status.BLOCKED && trace.distance()<1000,"unavailable/budget path reported clear");
                } else if(name().equals("supported_plant")) {
                    var originalPose=handle.pose();
                    check(!domain.retiringEmptyCell(BlockPos.ZERO,originalPose),"live content counted as retired empty plot");
                    var trace=StructureDamages.service().path(new StructureDamage.ActiveActor(new StructureOperation.ActorContext(player.level(),player)),
                            new StructurePathDamage.Request(start,end,.05f,3,StructureDamage.DropPolicy.EXISTING_SKILL_RULES,false,true,false,false,false,null));
                    check(trace.distance()>=start.distanceTo(end),"already removed neighbour blocked path: "+trace+", current="+domain.current()
                            +", support="+domain.storage().getBlockState(domain.position(BlockPos.ZERO))
                            +", plant="+domain.storage().getBlockState(domain.position(BlockPos.ZERO.above())));
                    verify();
                    if(reference.backend().equals(org.academy.internal.common.structure.sable.SableStructureAccess.ID)) {
                        check(domain.retiringEmptyCell(BlockPos.ZERO.above(),originalPose),"empty plot retirement proof missing");
                        check(!domain.retiringEmptyCell(BlockPos.ZERO,new StructurePose(originalPose.worldOrigin().add(1,0,0),
                                originalPose.rotationX(),originalPose.rotationY(),originalPose.rotationZ(),originalPose.rotationW())),"stale pose accepted");
                        var container=org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer.getContainer(player.level());
                        container.removeSubLevel(container.getSubLevel(reference.id()),org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelRemovalReason.REMOVED);
                        check(!domain.retiringEmptyCell(BlockPos.ZERO,originalPose),"released plot address reused as clear evidence");
                    }
                    finish();return;
                } else {
                    var trace=LevelUtil.destroyBlocksAlongPath(player.level(),start,end,.05f,name().equals("tier_blocker")?2:3,
                            !name().equals("no_drops"),false,true,name().equals("simulate"),player);
                    if(Set.of("tier_blocker","world_blocker","locked").contains(name())) check(trace.getLeft()&&trace.getRight()<5,"blocker not clipped: "+trace);
                    if(name().equals("simulate")) check(breaks==0&&loot==0,"simulation emitted mutation events");
                }
                verify();finish();
            } else if(phase==3&&ticks>=7) {verify();finish();}
        } catch(Throwable failure) {
            failed=true;AcademyCraft.LOGGER.error("STRUCTURE_PATH_SMOKE_FAILED {}",name(),failure);write(failure.toString());
        }
    }

    private static void verify() {
        var state=domain.storage().getBlockState(domain.position(BlockPos.ZERO));
        boolean preserved=Set.of("simulate","hollow","slab_gap","rotated_slab_gap","tier_blocker","world_blocker","cancel","replace","locked","scan_budget","outbound_blocker","return_blocker").contains(name());
        check(preserved?!state.isAir():state.isAir(),"unexpected source: "+state);
        if(name().equals("replace")) check(state.is(Blocks.GOLD_BLOCK),"callback replacement overwritten");
        var items=player.level().getEntitiesOfClass(ItemEntity.class,new AABB(center,center).inflate(6));
        java.util.function.ToIntFunction<net.minecraft.world.item.Item> count=item->items.stream().filter(entity->entity.getItem().is(item)).mapToInt(entity->entity.getItem().getCount()).sum();
        if(name().equals("iron")||name().equals("zero_length")||name().equals("reentry")) check(count.applyAsInt(Items.IRON_BLOCK)==1,"iron conservation");
        if(name().equals("mixed_cells")) check(count.applyAsInt(Items.IRON_BLOCK)==2,"mixed world/structure conservation");
        if(name().equals("overhanging_fence")) {
            check(count.applyAsInt(Items.OAK_FENCE)==2,"above-cell fence collision missed");
            check(player.level().getBlockState(seed.east(2)).isAir(),"host fence survived its upper collision hit");
        }
        if(name().equals("supported_plant")) {
            check(domain.storage().getBlockState(domain.position(BlockPos.ZERO.above())).isAir(),"unsupported plant survived");
            check(count.applyAsInt(Items.DIRT)==1,"support drop lost");
        }
        if(name().contains("chest")) check(count.applyAsInt(Items.DIAMOND)==19,"chest inventory conservation");
        if(name().equals("silk")||name().equals("program_mining")) check(count.applyAsInt(Items.DIAMOND_ORE)==1&&count.applyAsInt(Items.DIAMOND)==0,"silk mode lost");
        if(name().equals("fortune")) check(count.applyAsInt(Items.DIAMOND)>=1&&count.applyAsInt(Items.DIAMOND_ORE)==0,"fortune mode lost");
        if(name().equals("smelt")) check(count.applyAsInt(Items.IRON_INGOT)==1&&count.applyAsInt(Items.RAW_IRON)==0,"smelt mode lost");
        if(name().equals("loot_xp")) {
            check(count.applyAsInt(Items.EMERALD)==2,"loot replacement lost");
            check(player.level().getEntitiesOfClass(ExperienceOrb.class,new AABB(center,center).inflate(6)).stream().mapToInt(ExperienceOrb::getValue).sum()==7,"XP lost");
        }
        if(preserved||name().equals("no_drops")||name().equals("plasma")||name().equals("fluid_silent")) check(items.isEmpty(),"unexpected item outputs");
    }
    private static void checkChangeBudget() {
        // Real loaded content exceeds one call's write budget; no fake receipt or mocked world.
        for(var pos:BlockPos.betweenClosed(seed.offset(-5,-5,-5),seed.offset(5,5,5))) {
            if(!pos.equals(seed)) player.level().setBlock(pos,Blocks.IRON_BLOCK.defaultBlockState(),2);
        }
        var request=new StructurePathDamage.Request(Vec3.atCenterOf(seed.west(6)),Vec3.atCenterOf(seed.east(6)),5.5f,3,
                StructureDamage.DropPolicy.NO_DROPS,false,false,false,false,false,null);
        var trace=StructureDamages.service().path(new StructureDamage.ActiveActor(new StructureOperation.ActorContext(player.level(),player)),request);
        check(trace.status()==StructureOperation.Status.BLOCKED&&trace.receipt().broken()==1024&&trace.distance()<12,"mutation budget failed: "+trace);
        int remaining=0;
        for(var pos:BlockPos.betweenClosed(seed.offset(-5,-5,-5),seed.offset(5,5,5))) if(player.level().getBlockState(pos).is(Blocks.IRON_BLOCK)) remaining++;
        if(domain.storage().getBlockState(domain.position(BlockPos.ZERO)).is(Blocks.IRON_BLOCK)) remaining++;
        check(remaining==307,"write budget lost or duplicated content: "+remaining);
    }
    private static void rotate() {
        var rotation=new org.joml.Quaterniond().rotateXYZ(.3,.7,.2);
        if(org.academy.internal.common.sublevel.SubLevelStructureRefs.isSubLevel(reference)) {
            var runtime=org.academy.internal.common.sublevel.SubLevelServer.runtime(player.level()).orElseThrow();
            check(runtime.rotateTo(new org.academy.internal.common.sublevel.SubLevelId(reference.id()),new StructurePose(Vec3.ZERO,rotation)),"rotation failed");
        } else {
            var container=org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer.getContainer(player.level());
            container.getSubLevel(reference.id()).logicalPose().orientation().set(rotation);
        }
    }
    @SubscribeEvent public static void breaking(BreakBlockEvent event) {
        if(!ENABLED||phase<2||phase>3||domain==null||StructureContexts.current().isEmpty()) return;
        breaks++;
        check(event.getLevel()==player.level()&&Vec3.atCenterOf(event.getPos()).distanceTo(center)<8,"protection received private storage address");
        if(name().equals("cancel")) event.setCanceled(true);
        if(name().equals("replace")) domain.storage().setBlock(domain.position(BlockPos.ZERO),Blocks.GOLD_BLOCK.defaultBlockState(),2);
        if(name().equals("reentry")) {
            var result=StructureDamages.service().path(new StructureDamage.ActiveActor(new StructureOperation.ActorContext(player.level(),player)),
                    new StructurePathDamage.Request(start,end,.05f,3,StructureDamage.DropPolicy.EXISTING_SKILL_RULES,false,true,false,false,false,null));
            check(result.status()==StructureOperation.Status.CONFLICT,"reentrant path accepted");
        }
    }
    @SubscribeEvent public static void drops(BlockDropsEvent event) {
        if(!ENABLED||phase<2||phase>3||domain==null||StructureContexts.current().isEmpty()) return;
        loot++;
        if(name().equals("loot_xp")) {event.getDrops().getFirst().setItem(new ItemStack(Items.EMERALD,2));event.setDroppedExperience(7);}
    }
    private static void finish() {
        RESULTS.add(Map.of("case",name(),"passed",true,"backend",reference.backend().toString(),"break_events",breaks,"loot_events",loot));
        AcademyCraft.LOGGER.info("STRUCTURE_PATH_SMOKE_PASS {}",name());
        if(lock!=null) PayloadIsolation.release(player.level().getServer(),lock);
        lock=null;domain=null;handle=null;reference=null;scenario++;phase=0;ticks=0;
        if(scenario==CASES.size()) {done=true;write("");}
    }
    private static void write(String failure) {
        try {Files.writeString(directory.resolve("structure-path-damage-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed",!failed,"failure",failure,"cases",RESULTS,"ticks",age)));}
        catch(Exception error) {AcademyCraft.LOGGER.error("Path report failed",error);}
    }
    @SubscribeEvent public static void client(ClientTickEvent.Post event) {
        if(!ENABLED||closing) return;
        var minecraft=Minecraft.getInstance();
        if(done||failed) {closing=true;minecraft.stop();return;}
        if(minecraft.player==null||minecraft.level==null) return;
        if(minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) minecraft.gui.setScreen(null);
        directory=minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        check(directory.getFileName().toString().startsWith("native-sublevel-direct-smoke"),"isolated world required");
        if(!ready) org.lwjgl.glfw.GLFW.glfwIconifyWindow(minecraft.getWindow().handle());ready=true;
    }
    private static void check(boolean condition,String message) {if(!condition) throw new IllegalStateException(message);}
}
