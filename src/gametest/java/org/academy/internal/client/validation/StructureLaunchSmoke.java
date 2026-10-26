package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.upgrade.damage.DamageDomain;
import org.academy.internal.common.structure.upgrade.launch.LiveStructureLaunches;
import org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.jspecify.annotations.Nullable;

import java.nio.file.*;
import java.util.*;

/** Real authorized mutations and durable restart denial, exclusively in fresh copied saves. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructureLaunchSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.structureLaunchSmoke");
    private static final String MODE = System.getProperty("academy.structureLaunchMode", "functional");
    private static final Identifier NPC = AcademyCraft.academy("validation_launch_npc");
    private static final Identifier OFFLINE = AcademyCraft.academy("validation_launch_absent");
    private static final Identifier VERSION = AcademyCraft.academy("validation_launch_version");
    private static final List<String> CASES = List.of("player_sphere", "npc_sphere", "npc_path", "npc_cut",
            "unknown_policy", "forged_identity", "expired", "revoked_before_query", "revoked_after_query",
            "cancel_consumes", "event_cancel", "event_revoke", "player_protection_cancel", "npc_removed",
            "explicit_absent_policy", "source_binding", "source_creation_changed", "path_simulation",
            "policy_gate_changed", "player_setting_changed", "cell_policy_changes_in_event", "policy_exception", "policy_reentry", "policy_changes_during_admission");
    private static final List<Map<String,Object>> RESULTS = new ArrayList<>();
    private static volatile boolean ready, done, failed, closing;
    private static Path directory;
    private static ServerPlayer player;
    private static ArmorStand npc;
    private static BlockPos seed;
    private static StructureRef reference;
    private static DamageDomain domain;
    private static UUID transaction;
    private static Vec3 center;
    private static StructureDamage.Launched authority;
    private static int scenario, phase, ticks, age, launchEvents, lootEvents;
    private static boolean policyAllowed = true, mutating;
    private static boolean cellAllowed = true;
    private static boolean policyThrows, policyReentry;
    private static int reentryDenied;
    private static boolean changingPolicy;
    private static int permissionReads;
    private static @Nullable Entity expectedLootActor;
    private static String name() { return CASES.get(scenario); }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || !ready || done || failed || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            check(++age < 3000, "fixture deadline"); ticks++;
            if (player == null) {
                player = event.getServer().getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
                DestroyBlocksSetting.setDestroyBlocksEnabled(player, true);
                StructureLaunches.registerPolicy(event.getServer(), NPC, 1, policy(false));
                StructureLaunches.registerPolicy(event.getServer(), OFFLINE, 1, policy(true));
                StructureLaunches.registerPolicy(event.getServer(), VERSION, MODE.equals("verify") ? 2 : 1, policy(true));
            }
            player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance();
            if (!MODE.equals("functional")) { restart(event.getServer()); return; }
            if (phase == 0) {
                seed = new BlockPos(120 + scenario * 16, 200, 120);
                player.connection.teleport(seed.getX()-5,seed.getY(),seed.getZ(),0,0);
                for (var pos : BlockPos.betweenClosed(seed.offset(-7,-5,-5), seed.offset(7,5,5))) {
                    player.level().getChunkAt(pos); player.level().setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                }
                phase=1; ticks=0; return;
            }
            if (phase == 1) {
                if (ticks<20) return;
                player.level().setBlock(seed,Blocks.IRON_BLOCK.defaultBlockState(),2);
                var capture=StructureTransactions.capture(player.level(),new StructureCaptureRequest(List.of(seed),1,1,StructureCaptureRequest.ALLOW,true)
                        .withSkillCreation(player.getUUID(),AcademyCraft.academy("kinetic_throw")));
                check(capture.succeeded() || capture.pending(),"capture rejected"); transaction=capture.transactionId(); phase=2; return;
            }
            if (phase == 2) {
                var capture=StructureTransactions.poll(event.getServer(),transaction).orElseThrow();
                if(capture.pending()) return;
                check(capture.succeeded(),"capture failed"); reference=capture.structure().orElseThrow();
                var handle=StructureSpaces.resolve(event.getServer(),reference).orElseThrow();
                center=handle.pose().localToWorldPoint(new Vec3(.5,.5,.5));
                domain=StructureBackendRouter.damageDomains(player.level(),new AABB(center,center).inflate(3),16).stream()
                        .filter(value -> value.handle().reference().equals(reference)).findFirst().orElseThrow();
                npc=new ArmorStand(player.level(),seed.getX()-3,seed.getY(),seed.getZ()); npc.setNoGravity(true);
                check(player.level().addFreshEntity(npc),"NPC not added");
                launchEvents=lootEvents=0; expectedLootActor=player;
                boolean nonPlayer=Set.of("npc_sphere","npc_path","npc_cut","event_cancel","event_revoke","npc_removed",
                        "explicit_absent_policy","policy_gate_changed","cell_policy_changes_in_event","policy_exception","policy_reentry","policy_changes_during_admission").contains(name());
                Entity issuer=nonPlayer?npc:player;
                var policy=name().equals("explicit_absent_policy")?OFFLINE:nonPlayer?NPC:StructureLaunches.PLAYER_PRESENT;
                var owner=nonPlayer?Optional.<UUID>empty():Optional.of(player.getUUID());
                var source=name().startsWith("source_")?Optional.of(reference):Optional.<StructureRef>empty();
                authority=issue(issuer,policy,owner,name().equals("expired")?1:1200,source);
                expectedLootActor=issuer;
                if(name().equals("expired")) { phase=3; ticks=0; return; }
                exercise(); return;
            }
            if(phase==3 && ticks>=3) { denyQuery(authority); check(intact(),"expired launch changed target"); finish(); }
        } catch(Throwable failure) {
            failed=true; AcademyCraft.LOGGER.error("Launch fixture failed",failure); write(failure.toString());
        }
    }

    private static StructureLaunches.Policy policy(boolean allowAbsent) {
        return new StructureLaunches.Policy() {
            @Override public boolean mayIssue(StructureOperation.ActorContext actor, StructureLaunches.Grant grant) {
                return actor.actor()==player || actor.actor()==npc;
            }
            @Override public boolean mayUse(ServerLevel host, StructureLaunches.Grant grant, @Nullable Entity actor) {
                if(policyThrows) throw new IllegalStateException("injected permission callback failure");
                if(changingPolicy && ++permissionReads>=4) return false;
                if(policyReentry) {
                    check(StructureDamages.service().query(authority,request()).status()==StructureOperation.Status.DENIED,"policy reentry accepted");
                    reentryDenied++;
                }
                return policyAllowed && (allowAbsent || actor!=null && actor.isAlive());
            }
            @Override public boolean mayBreak(ServerLevel host, StructureLaunches.Grant grant, @Nullable Entity actor, BlockPos world, BlockState state) {
                return policyAllowed && cellAllowed && host==player.level() && state.is(Blocks.IRON_BLOCK);
            }
        };
    }
    private static StructureDamage.Launched issue(Entity actor, Identifier policy, Optional<UUID> owner, int lifetime, Optional<StructureRef> source) {
        var result=StructureLaunches.issue(new StructureOperation.ActorContext(player.level(),actor),policy,owner,lifetime,source);
        check(result.status()==StructureOperation.Status.COMPLETED,"issuance failed: "+result);
        return new StructureDamage.Launched(result.value().orElseThrow());
    }
    private static StructureDamage.Request request() {
        return new StructureDamage.Request(UUID.randomUUID(),center,.65,1,32768,16,StructureDamage.DropPolicy.EXISTING_SKILL_RULES);
    }
    private static StructurePathDamage.Request path(boolean simulate) {
        return new StructurePathDamage.Request(center.add(-2,0,0),center.add(2,0,0),.05f,3,
                StructureDamage.DropPolicy.EXISTING_SKILL_RULES,false,false,simulate,true,false,null);
    }
    private static void denyQuery(StructureDamage.Authority claim) {
        check(StructureDamages.service().query(claim,request()).status()==StructureOperation.Status.DENIED,"untrusted query accepted");
    }
    private static boolean intact() { return domain.storage().getBlockState(domain.position(BlockPos.ZERO)).is(Blocks.IRON_BLOCK); }
    private static void exercise() {
        var damage=StructureDamages.service(); var claim=authority.authority();
        switch(name()) {
            case "policy_changes_during_admission" -> {
                changingPolicy=true; permissionReads=0;
                check(damage.path(authority,path(false)).status()==StructureOperation.Status.DENIED,"policy change was not denied at admission");
                changingPolicy=false; check(intact(),"changing authority mutated content"); finish(); return;
            }
            case "policy_exception" -> {
                policyThrows=true; denyQuery(authority); policyThrows=false;
                check(intact(),"throwing policy changed content"); finish(); return;
            }
            case "policy_reentry" -> policyReentry=true;
            case "unknown_policy" -> {
                check(StructureLaunches.issue(new StructureOperation.ActorContext(player.level(),npc),AcademyCraft.academy("missing_policy"),
                        Optional.empty(),100,Optional.empty()).status()==StructureOperation.Status.DENIED,"unknown policy issued");
                denyQuery(new StructureDamage.ActiveActor(new StructureOperation.ActorContext(player.level(),npc)));
                check(intact(),"unknown policy changed content"); finish(); return;
            }
            case "forged_identity" -> {
                for(var forged:List.of(new StructureImpactFeed.LaunchAuthority(player.level(),UUID.randomUUID(),claim.ownerId(),claim.policyId()),
                        new StructureImpactFeed.LaunchAuthority(player.level(),claim.launchId(),Optional.of(UUID.randomUUID()),claim.policyId()),
                        new StructureImpactFeed.LaunchAuthority(player.level(),claim.launchId(),claim.ownerId(),NPC),
                        new StructureImpactFeed.LaunchAuthority(player.level().getServer().getLevel(net.minecraft.world.level.Level.NETHER),claim.launchId(),claim.ownerId(),claim.policyId()))) {
                    denyQuery(new StructureDamage.Launched(forged));
                    check(damage.path(new StructureDamage.Launched(forged),path(false)).status()==StructureOperation.Status.DENIED,"forged path accepted");
                }
            }
            case "revoked_before_query" -> {
                check(StructureLaunches.revoke(player.level(),claim.launchId()),"revoke failed"); denyQuery(authority); finish(); return;
            }
            case "npc_removed" -> { npc.discard(); denyQuery(authority); check(intact(),"removed actor broke block"); finish(); return; }
            case "explicit_absent_policy" -> { npc.discard(); expectedLootActor=null; }
            case "source_creation_changed" -> {
                var old=LiveStructureProvenance.metadata(player.level().getServer(),reference).orElseThrow();
                check(LiveStructureProvenance.remove(player.level().getServer(),reference,old.creationOperationId()),"remove old test mark");
                check(LiveStructureProvenance.commitCapture(player.level().getServer(),reference,
                        StructureProvenance.SkillCreation.resting(player.getUUID(),AcademyCraft.academy("kinetic_throw")).capture(UUID.randomUUID(),player.level().getGameTime())),"replace test mark");
                denyQuery(authority); check(intact(),"replacement creation inherited launch"); finish(); return;
            }
            case "player_setting_changed" -> {
                DestroyBlocksSetting.setDestroyBlocksEnabled(player,false); denyQuery(authority);
                DestroyBlocksSetting.setDestroyBlocksEnabled(player,true); check(intact(),"disabled destruction still ran"); finish(); return;
            }
            case "source_binding" -> { center=Vec3.atCenterOf(seed.east(4)); player.level().setBlock(seed.east(4),Blocks.IRON_BLOCK.defaultBlockState(),2); }
        }
        if(name().equals("path_simulation")) {
            var preview=damage.path(authority,path(true));
            check(preview.status()==StructureOperation.Status.COMPLETED && intact() && launchEvents==0 && lootEvents==0,"simulation mutated or consumed launch");
        }
        if(name().equals("npc_path") || name().equals("path_simulation")) {
            mutating=true;
            try { check(damage.path(authority,path(false)).receipt().broken()==1,"launch path did not break one cell"); }
            finally { mutating=false; }
            check(!intact(),"path did not remove content"); denyQuery(authority); finish(); return;
        }
        if(name().equals("npc_cut")) {
            mutating=true;
            try { check(damage.cut(authority,new StructureCutDamage.Request(center.add(-2,0,0),center.add(2,0,0),new Vec3(0,1,0),
                    .6,.1,(state,view,pos)->state.is(Blocks.IRON_BLOCK),StructureDamage.DropPolicy.EXISTING_SKILL_RULES,false)).receipt().broken()==1,"cut failed"); }
            finally { mutating=false; }
            check(!intact(),"cut left content"); denyQuery(authority); finish(); return;
        }
        var query=damage.query(authority,request()); var batch=query.value().orElseThrow();
        check(batch.cells().size()==1,"expected one selected cell: "+batch.cells());
        if(name().equals("revoked_after_query") || name().equals("policy_gate_changed")) {
            if(name().equals("revoked_after_query")) check(StructureLaunches.revoke(player.level(),claim.launchId()),"revoke failed");
            else policyAllowed=false;
            check(damage.apply(authority,batch).status()==StructureOperation.Status.DENIED,"revoked batch applied");
            check(damage.status(authority,batch.request().effectId()).status()==StructureOperation.Status.DENIED,"unauthorized status disclosed");
            check(damage.cancel(authority,batch.request().effectId()).status()==StructureOperation.Status.DENIED,"unauthorized cancel accepted");
            policyAllowed=true; check(intact(),"revoked batch changed target"); finish(); return;
        }
        if(name().equals("cancel_consumes")) {
            check(damage.cancel(authority,batch.request().effectId()).value().orElseThrow().phase()==StructureDamage.Phase.CANCELLED,"cancel failed");
            denyQuery(authority); check(intact(),"cancelled effect changed content"); finish(); return;
        }
        mutating=true;
        StructureOperation.Result<StructureDamage.Receipt> applied;
        try { applied=damage.apply(authority,batch); } finally { mutating=false; }
        boolean blocked=Set.of("event_cancel","event_revoke","player_protection_cancel","cell_policy_changes_in_event").contains(name());
        check(applied.value().orElseThrow().broken()==(blocked?0:1),"wrong mutation receipt: "+applied);
        check(name().equals("source_binding") ? player.level().getBlockState(seed.east(4)).isAir() && intact() : intact()==blocked,"wrong content after launch");
        check(blocked ? lootEvents==0 : lootEvents==1,"wrong drop callback count "+lootEvents);
        denyQuery(authority);
        check(damage.path(authority,path(false)).status()==StructureOperation.Status.DENIED,"claimed launch admitted a second path");
        finish();
    }
    @SubscribeEvent public static void protection(StructureLaunchBreakEvent event) {
        if(!ENABLED || !mutating || authority==null || !event.grant().id().equals(authority.authority().launchId())) return;
        launchEvents++;
        check(event.host()==player.level() && event.position().distSqr(BlockPos.containing(center))<=1,"protection received storage coordinates");
        if(name().equals("event_cancel")) event.setCanceled(true);
        if(name().equals("event_revoke")) check(StructureLaunches.revoke(player.level(),event.grant().id()),"callback revoke failed");
        if(name().equals("cell_policy_changes_in_event")) cellAllowed=false;
    }
    @SubscribeEvent public static void playerProtection(BreakBlockEvent event) {
        if(ENABLED && mutating && MODE.equals("functional") && name().equals("player_protection_cancel")) event.setCanceled(true);
    }
    @SubscribeEvent public static void loot(BlockDropsEvent event) {
        if(!ENABLED || !mutating) return;
        lootEvents++; check(event.getBreaker()==expectedLootActor,"loot attribution used a substituted actor");
    }
    private static void finish() {
        if(name().equals("policy_reentry")) check(reentryDenied>0,"reentrant policy was not exercised");
        policyReentry=false;
        check(StructureLaunches.revoke(player.level(),authority.authority().launchId()),"cleanup revoke not durable");
        if(npc!=null && !npc.isRemoved()) npc.discard();
        RESULTS.add(Map.of("case",name(),"passed",true,"backend",reference.backend().toString(),"protection_events",launchEvents,"loot_events",lootEvents));
        AcademyCraft.LOGGER.info("STRUCTURE_LAUNCH_PASS {}",name());
        authority=null; domain=null; reference=null; scenario++; phase=0; ticks=0; cellAllowed=true;
        if(scenario==CASES.size()) { done=true; write(""); }
    }

    private static void restart(MinecraftServer server) throws Exception {
        if(age<25) return;
        var file=directory.resolve("launch-restart-identities.json");
        center=Vec3.atCenterOf(new BlockPos(120,200,120));
        player.level().getChunkAt(BlockPos.containing(center));
        if(MODE.equals("crash")) {
            var fresh=issue(player,StructureLaunches.PLAYER_PRESENT,Optional.of(player.getUUID()),1200,Optional.empty());
            var used=issue(player,StructureLaunches.PLAYER_PRESENT,Optional.of(player.getUUID()),1200,Optional.empty());
            var revoked=issue(player,StructureLaunches.PLAYER_PRESENT,Optional.of(player.getUUID()),1200,Optional.empty());
            var version=issue(player,VERSION,Optional.of(player.getUUID()),1200,Optional.empty());
            server.saveEverything(false,true,true);
            check(StructureLaunches.revoke(player.level(),revoked.authority().launchId()),"restart revoke failed");
            check(StructureDamages.service().query(used,request()).value().isPresent(),"restart claim failed");
            Files.writeString(file,new GsonBuilder().create().toJson(Map.of("fresh",fresh.authority().launchId().toString(),"used",used.authority().launchId().toString(),
                    "revoked",revoked.authority().launchId().toString(),"version",version.authority().launchId().toString(),"owner",player.getUUID().toString())));
            RESULTS.add(Map.of("case","durable_claim_and_revoke_before_halt","passed",true)); write(""); Runtime.getRuntime().halt(86);
        } else {
            var ids=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            check(ids.get("owner").getAsString().equals(player.getUUID().toString()),"restart player identity changed");
            for(var key:List.of("used","revoked","version")) {
                var claim=new StructureDamage.Launched(new StructureImpactFeed.LaunchAuthority(player.level(),UUID.fromString(ids.get(key).getAsString()),
                        Optional.of(player.getUUID()),key.equals("version")?VERSION:StructureLaunches.PLAYER_PRESENT));
                denyQuery(claim); RESULTS.add(Map.of("case","restart_denies_"+key,"passed",true));
            }
            var fresh=new StructureDamage.Launched(new StructureImpactFeed.LaunchAuthority(player.level(),UUID.fromString(ids.get("fresh").getAsString()),Optional.of(player.getUUID()),StructureLaunches.PLAYER_PRESENT));
            check(StructureDamages.service().query(fresh,request()).value().isPresent(),"unclaimed persisted launch not restored");
            RESULTS.add(Map.of("case","restart_restores_unclaimed_authority","passed",true)); done=true; write("");
        }
    }
    private static void check(boolean ok,String message) { if(!ok) throw new IllegalStateException(message); }
    private static void write(String failure) {
        try { Files.writeString(directory.resolve("structure-launch-"+MODE+"-result.json"),new GsonBuilder().setPrettyPrinting().create()
                .toJson(Map.of("passed",!failed,"failure",failure,"cases",RESULTS,"ticks",age,"mode",MODE))); }
        catch(Exception error) { AcademyCraft.LOGGER.error("Launch report failed",error); }
    }
    @SubscribeEvent public static void client(ClientTickEvent.Post event) {
        if(!ENABLED || closing) return; var minecraft=Minecraft.getInstance();
        if(done || failed) { closing=true; minecraft.stop(); return; }
        if(minecraft.player==null || minecraft.level==null) return;
        if(minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) minecraft.gui.setScreen(null);
        directory=minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        check(directory.getFileName().toString().startsWith("native-sublevel-direct-smoke"),"isolated save required");
        if(!ready) org.lwjgl.glfw.GLFW.glfwIconifyWindow(minecraft.getWindow().handle()); ready=true;
    }
}
