package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.internal.client.sublevel.SubLevelClientState;
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.sublevel.validation.SubLevelBatchMultiplayerSmoke;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;
import static org.academy.internal.common.sublevel.validation.SubLevelBatchMultiplayerSmoke.*;

/** Packet and projection observations from a real remote client, without gameplay packet spoofing. */
@EventBusSubscriber(modid=AcademyCraft.MOD_ID,value=Dist.CLIENT)
public final class SubLevelBatchMultiplayerClientSmoke {
    private static Path root; private static String role; private static int ticks,phase,samples,disconnectTicks; private static boolean ready,stopped,success;
    private static List<SubLevelId> ids=List.of();
    private static final Map<SubLevelId,Vec3> origins=new HashMap<>();
    private static final Map<String,Object> evidence=new LinkedHashMap<>();
    private static SubLevelClientState.Diagnostics initial;
    private static net.minecraft.client.multiplayer.ClientLevel connectedLevel;
    private SubLevelBatchMultiplayerClientSmoke() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!ENABLED || role().equals("server") || stopped) return;
        try { advance(); } catch(Throwable error) {
            AcademyCraft.LOGGER.error("SUBLEVEL_BATCH_MULTIPLAYER_CLIENT_FAILED",error); stopped=true;
            try { if(root!=null) { json(root.resolve("coord/"+role+"-failure.json"),Map.of("error",error.toString())); json(root.resolve(role+"-result.json"),Map.of("passed",false,"error",error.toString(),"phase",phase)); } } catch(Throwable ignored) {}
            Minecraft.getInstance().stop();
        }
    }
    private static void advance() throws Exception {
        var mc=Minecraft.getInstance(); check(++ticks<6500,"client batch timeout");
        if(root==null) { role=role(); root=verifyRoot(role,mc.gameDirectory.toPath()); mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.options.pauseOnLostFocus=false; mc.options.framerateLimit().set(60); mc.options.enableVsync().set(false); mc.options.renderDistance().set(6); }
        if(Files.isRegularFile(root.resolve("server-result.json"))) {
            var result=com.google.gson.JsonParser.parseString(Files.readString(root.resolve("server-result.json"))).getAsJsonObject();
            check(result.get("passed").getAsBoolean(),"server fixture failed"); success=true;
        }
        if(mc.level==null || mc.player==null || mc.getConnection()==null) {
            if(success) {
                // A disconnect can occur after Minecraft.tick HEAD and before this Post event.
                // Wait for one subsequent complete tick; never invoke production cleanup here.
                if(++disconnectTicks<2) return;
                var lifecycle=SubLevelClientState.lifecycleDiagnostics();
                check(lifecycle.retainedLevels()==0 && lifecycle.projectionIds()==0 && lifecycle.pendingCarryFrames()==0,"old client level retained after disconnect: "+lifecycle);
                check(lifecycle.discardedProjections()>=8,"disconnect did not discard the live projection entities");
                check(connectedLevel!=null && org.academy.internal.common.sublevel.SubLevelCollisionRuntime.index(connectedLevel).isEmpty(),"old collision runtime still registered");
                evidence.put("client_ticks_observing_disconnect",disconnectTicks);
                evidence.put("lifecycle_after_disconnect",lifecycle); evidence.put("old_collision_runtime_unregistered",true);
                evidence.put("passed",true); evidence.put("actual_tcp_client",true); evidence.put("disconnect_observed",true);
                json(root.resolve(role+"-result.json"),evidence); stopped=true; mc.stop();
            }
            return;
        }
        check(mc.getSingleplayerServer()==null && !mc.getConnection().getConnection().isMemoryConnection(),"real remote TCP client required");
        mc.player.setNoGravity(true); mc.player.setDeltaMovement(Vec3.ZERO);
        if(!ready) {
            connectedLevel=mc.level;
            var props=new Properties(); props.setProperty("uuid",mc.player.getUUID().toString()); write(root.resolve("coord/"+role+".ready"),props); ready=true;
            evidence.put("role",role); evidence.put("uuid",mc.player.getUUID().toString());
        }
        int latest=phase; for(int n=phase+1;n<=6;n++) if(Files.isRegularFile(root.resolve("coord/phase-"+n+".properties"))) latest=n;
        if(latest!=phase) {
            phase=latest; samples=0; origins.clear();
            var props=read(root.resolve("coord/phase-"+phase+".properties"));
            ids=Arrays.stream(props.getProperty("ids").split(",")).map(UUID::fromString).map(SubLevelId::new).toList();
            check(ids.size()==8,"expected eight native bodies");
            initial=SubLevelClientState.diagnostics(mc.level);
            for(var id:ids) SubLevelClientState.snapshotPose(mc.level,id).ifPresent(pose -> origins.put(id,pose.worldOrigin()));
        }
        if(phase==0 || Files.exists(root.resolve("coord/"+role+"-"+phase+".json"))) return;
        var state=SubLevelClientState.diagnostics(mc.level);
        boolean absent=phase==5 || phase==3 && role.equals("observer");
        int expected=absent?0:8;
        if(state.snapshots()!=expected || state.projections()!=expected || state.contactPoses()!=expected) { samples=0; return; }
        if(!absent && ids.stream().anyMatch(id -> SubLevelClientState.snapshotPose(mc.level,id).isEmpty())) { samples=0; return; }
        samples++;
        if(phase==2) {
            if(samples<100 || origins.size()!=8 || ids.stream().anyMatch(id -> SubLevelClientState.snapshotPose(mc.level,id).orElseThrow().worldOrigin().distanceTo(origins.get(id))<.5)) return;
            long packets=state.batchPackets()-initial.batchPackets(), entries=state.poseEntries()-initial.poseEntries();
            check(packets>20 && entries>=packets*6,"pose batch did not combine moving bodies: packets="+packets+" entries="+entries);
            check(state.legacyPosePackets()==initial.legacyPosePackets(),"moving bodies used legacy pose packets");
            evidence.put("moving_batch_packets",packets); evidence.put("moving_pose_entries",entries);
            evidence.put("pose_packet_reduction_vs_one_packet_per_entry",1.0-(double)packets/entries);
            evidence.put("moving_samples",samples); evidence.put("legacy_pose_packets",state.legacyPosePackets());
            Screenshot.grab(mc.gameDirectory,"sublevel-batch-moving-"+role+".png",mc.gameRenderer.mainRenderTarget(),1,message -> {});
        } else if(samples<10) return;
        if(phase==3 && role.equals("observer")) evidence.put("state_packets_before_return",state.statePackets());
        if(phase==4 && role.equals("observer")) check(state.statePackets()-((Number)evidence.get("state_packets_before_return")).longValue()>=8,"returning viewer did not receive fresh full states");
        if(phase==5) {
            evidence.put("snapshots_after_remove",state.snapshots()); evidence.put("projections_after_remove",state.projections()); evidence.put("collision_poses_after_remove",state.contactPoses());
            evidence.put("removal_packets",state.removals()); evidence.put("state_packets",state.statePackets());
        }
        if(phase==6) {
            evidence.put("live_projections_before_disconnect",state.projections());
            evidence.put("state_packets_before_disconnect",state.statePackets());
        }
        evidence.put("phase_"+phase+"_passed",true);
        json(root.resolve("coord/"+role+"-"+phase+".json"),Map.of("passed",true,"samples",samples,"state",state));
    }
}
