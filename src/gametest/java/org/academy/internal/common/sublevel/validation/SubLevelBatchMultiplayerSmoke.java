package org.academy.internal.common.sublevel.validation;

import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.sublevel.*;
import org.academy.internal.common.structure.physics.PhysicsVector;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Eight real native bodies, a dedicated server and two distinct loopback TCP clients. */
@EventBusSubscriber(modid=AcademyCraft.MOD_ID)
public final class SubLevelBatchMultiplayerSmoke {
    public static final String WORLD="sublevel-batch-multiplayer-smoke";
    public static final boolean ENABLED=Boolean.getBoolean("academy.subLevelBatchMultiplayerSmoke");
    private static final Map<MinecraftServer,State> STATES=new WeakHashMap<>();
    private SubLevelBatchMultiplayerSmoke() {}
    public static String role() { return System.getProperty("academy.nativeStructureMultiplayerRole",""); }
    public static Path verifyRoot(String role,Path actual) {
        try {
            check(ENABLED && List.of("server","actor","observer").contains(role),"fixture disabled");
            var session=System.getProperty("academy.nativeStructureMultiplayerSession","");
            check(session.matches("[A-Za-z0-9_-]{1,64}"),"invalid session");
            var run=Path.of(System.getProperty("academy.nativeStructureMultiplayerRunRoot")).toRealPath();
            var root=Path.of(System.getProperty("academy.nativeStructureMultiplayerRoot")).toRealPath();
            check(root.startsWith(run) && root.getFileName().toString().equals("sublevel-batch-multiplayer-"+session),"unrelated root");
            check(actual.toRealPath().equals(root.resolve(role)) && root.resolve("coord").toRealPath().equals(root.resolve("coord")),"linked role directory");
            check(!Files.exists(root.resolve(role+"-result.json")),"old result");
            return root;
        } catch(Exception error) { throw new IllegalStateException("Invalid batch test root",error); }
    }
    public static void check(boolean value,String detail) { if(!value) throw new IllegalStateException(detail); }
    public static Properties read(Path path) {
        var p=new Properties(); try(var stream=Files.newInputStream(path)) { p.load(stream); return p; }
        catch(Exception error) { throw new IllegalStateException(error); }
    }
    public static void write(Path path,Properties values) {
        var bytes=new java.io.ByteArrayOutputStream();
        try { values.store(bytes,"isolated TCP batch test"); publish(path,bytes.toByteArray()); }
        catch(Exception error) { throw new IllegalStateException(error); }
    }
    public static void json(Path path,Object values) {
        publish(path,new GsonBuilder().setPrettyPrinting().create().toJson(values).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static void publish(Path path,byte[] bytes) {
        var temporary=path.resolveSibling(path.getFileName()+"."+ProcessHandle.current().pid()+".tmp");
        try {
            check(!Files.exists(path),"old coordination record "+path);
            Files.write(temporary,bytes,StandardOpenOption.CREATE_NEW);
            try { Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException unavailable) { Files.move(temporary,path); }
        } catch(Exception error) { throw new IllegalStateException(error); }
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(ENABLED && role().equals("server")) STATES.put(event.getServer(),new State(event.getServer()));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var state=STATES.get(event.getServer()); if(state==null || state.done) return;
        try { state.tick(); } catch(Throwable error) { state.fail(error); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        var state=STATES.remove(event.getServer());
        if(state!=null) json(state.root.resolve("server-exit.json"),Map.of("normal_stop_event",true,"fixture_completed",state.done));
    }
    private static final class State {
        final MinecraftServer server; final Path root; final List<SubLevelId> ids=new ArrayList<>();
        final List<net.minecraft.world.level.ChunkPos> tickets=new ArrayList<>();
        SubLevelRuntime runtime; int ticks,phase,phaseAge,bodiesAfterRemove=-1; long moveSteps,previousMovingTick; boolean done;
        final List<Long> intervals=new ArrayList<>();
        State(MinecraftServer server) {
            this.server=server; root=verifyRoot("server",server.getServerDirectory());
            try { check(server.getWorldPath(LevelResource.ROOT).toRealPath().equals(root.resolve("server").resolve(WORLD)),"unrelated world"); }
            catch(Exception error) { throw new IllegalStateException(error); }
        }
        ServerPlayer player(String role) { return server.getPlayerList().getPlayerByName(role.equals("actor")?"StructureActor":"StructureWatch"); }
        boolean acknowledged() { return Files.isRegularFile(root.resolve("coord/actor-"+phase+".json")) && Files.isRegularFile(root.resolve("coord/observer-"+phase+".json")); }
        void phase(int value) {
            phase=value; phaseAge=0; var props=new Properties(); props.setProperty("phase",Integer.toString(value));
            props.setProperty("ids",String.join(",",ids.stream().map(id -> id.value().toString()).toList()));
            props.setProperty("bodies","8"); write(root.resolve("coord/phase-"+value+".properties"),props);
        }
        void createBodies() {
            var host=server.overworld();
            for(int i=0;i<8;i++) {
                    var seed=new BlockPos(i*3,100,0); host.getChunkAt(seed); check(host.getBlockState(seed).isAir(),"occupied fixture arena");
                    host.setBlock(seed,Blocks.IRON_BLOCK.defaultBlockState(),3);
                    var capture=SubLevelCaptureService.capture(host,runtime,List.of(seed));
                    check(capture.id()!=null && capture.blocks()==1 && capture.boxes()>0,"collidable single-block capture failed");
                    check(runtime.universe().motion(capture.id()).orElseThrow().mass()>0,"dynamic native body has no mass");
                    ids.add(capture.id());
                }
        }
        void tick() {
            check(++ticks<6000,"batch fixture timeout phase="+phase); phaseAge++;
            for(var role:List.of("actor","observer")) check(!Files.exists(root.resolve("coord/"+role+"-failure.json")),"client failure "+role);
            if(phase==0) {
                if(player("actor")==null || player("observer")==null || !Files.exists(root.resolve("coord/actor.ready")) || !Files.exists(root.resolve("coord/observer.ready"))) return;
                check(!player("actor").getUUID().equals(player("observer").getUUID()),"players must differ");
                for(var role:List.of("actor","observer")) {
                    var player=player(role); var connection=player.connection.getConnection();
                    check(!connection.isMemoryConnection() && connection.getRemoteAddress() instanceof InetSocketAddress address && address.getAddress().isLoopbackAddress(),"real TCP required");
                    check(read(root.resolve("coord/"+role+".ready")).getProperty("uuid").equals(player.getUUID().toString()),"TCP player identity mismatch");
                }
                var host=server.overworld(); check(SubLevelServer.ensureForMaintenance(server),"native backend unavailable");
                runtime=SubLevelServer.runtime(host).orElseThrow(); check(runtime.size()==0,"fresh test world has existing bodies");
                for(int x=-1;x<=2;x++) for(int z=-1;z<=1;z++) { host.setChunkForced(x,z,true); tickets.add(new net.minecraft.world.level.ChunkPos(x,z)); }
                createBodies();
                for(var role:List.of("actor","observer")) { var p=player(role); p.setGameMode(GameType.CREATIVE); p.setNoGravity(true); p.connection.teleport(role.equals("actor")?10:14,100.5,-24,0,5); }
                check(runtime.size()==8,"expected exactly eight native bodies");
                for(var id:ids) for(var role:List.of("actor","observer")) check(SubLevelSnapshotVisibility.interested(player(role).position(),runtime.get(id).orElseThrow().worldBounds(),false),"body outside initial viewer interest");
                phase(1); return;
            }
            check(player("actor")!=null && player("observer")!=null,"TCP player disconnected before final phase");
            for(var role:List.of("actor","observer")) { var p=player(role); p.setNoGravity(true); p.setDeltaMovement(Vec3.ZERO); }
            if(phase==1) {
                for(var id:ids) check(runtime.universe().setVelocity(id,new PhysicsVector(0,0,0),new PhysicsVector(0,0,0)),"body stability setup failed");
                if(acknowledged()) phase(2); return;
            }
            if(phase==2) {
                long now=System.nanoTime();
                if(previousMovingTick!=0) intervals.add(now-previousMovingTick);
                previousMovingTick=now;
            }
            if(phase>=2 && phase<=4) {
                for(var id:ids) check(runtime.universe().setVelocity(id,new PhysicsVector(1,0,0),new PhysicsVector(0,.15,0)),"body velocity rejected");
                moveSteps++;
            }
            if(phase==2 && phaseAge>=200 && acknowledged()) { player("observer").connection.teleport(1200,100.5,-24,0,5); phase(3); }
            else if(phase==3 && acknowledged()) { player("observer").connection.teleport(14,100.5,-24,0,5); phase(4); }
            else if(phase==4 && acknowledged()) {
                for(var id:ids) check(runtime.remove(id),"body removal failed"); phase(5);
            } else if(phase==5 && acknowledged()) {
                check(runtime.size()==0,"server body leak"); bodiesAfterRemove=runtime.size();
                ids.clear(); createBodies(); phase(6);
            } else if(phase==6 && acknowledged()) {
                check(runtime.size()==8,"expected live bodies at disconnect");
                var ordered=intervals.stream().sorted().toList();
                var result=new LinkedHashMap<String,Object>();
                result.putAll(Map.of("passed",true,"actual_tcp_clients",2,"bodies",8,"moving_server_ticks",moveSteps,
                        "near_far_return_remove",true,"server_bodies_after_remove",bodiesAfterRemove,
                        "moving_tick_interval_samples",ordered.size(),"moving_tick_interval_p50_ms",ordered.get(ordered.size()/2)/1e6,
                        "moving_tick_interval_p95_ms",ordered.get((ordered.size()-1)*95/100)/1e6,
                        "server_velocity_fixture_driver",true));
                result.put("live_bodies_at_disconnect",runtime.size());
                json(root.resolve("server-result.json"),result);
                // Close both TCP channels while their eight projections are still live. The
                // clients must clean the old level even without receiving removal packets.
                for(var role:List.of("actor","observer")) player(role).connection.getConnection().disconnect(
                        net.minecraft.network.chat.Component.literal("Batch snapshot lifecycle validation complete"));
                done=true; for(var chunk:tickets) server.overworld().setChunkForced(chunk.x(),chunk.z(),false); server.halt(false);
            }
        }
        void fail(Throwable error) {
            AcademyCraft.LOGGER.error("SUBLEVEL_BATCH_MULTIPLAYER_FAILED",error); done=true;
            try { json(root.resolve("server-result.json"),Map.of("passed",false,"phase",phase,"error",error.toString())); } catch(Throwable ignored) {}
            server.halt(false);
        }
    }
}
