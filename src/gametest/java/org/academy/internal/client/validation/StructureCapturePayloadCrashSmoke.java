package org.academy.internal.client.validation;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.api.common.structure.upgrade.StructureRestorationEvent;
import org.academy.internal.common.structure.transaction.*;
import org.academy.internal.common.structure.upgrade.lifecycle.LiveStructureProvenance;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Abrupt exits only in disposable fixture saves; verifies a single usable inventory after restart. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructureCapturePayloadCrashSmoke {
    private static final String MODE = System.getProperty("academy.structureCapturePayloadCrash", "");
    private static volatile boolean ready, done, closing;
    private static Path directory;
    private static ServerPlayer player;
    private static BlockPos source;
    private static StructureRef reference;
    private static JsonObject evidence;
    private static int ticks, stableTicks;

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (MODE.isEmpty() || !ready || done || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        try {
            check(++ticks < 1000, "capture recovery timeout");
            if (player == null) {
                player = event.getServer().getPlayerList().getPlayers().getFirst();
                if (MODE.equals("verify")) {
                    evidence = JsonParser.parseString(Files.readString(directory.resolve("capture-payload-crash.json"))).getAsJsonObject();
                    var pos = evidence.getAsJsonArray("source"); source = new BlockPos(pos.get(0).getAsInt(),pos.get(1).getAsInt(),pos.get(2).getAsInt());
                    reference = new StructureRef(UUID.fromString(evidence.get("id").getAsString()), Identifier.parse(evidence.get("dimension").getAsString()),
                            Identifier.parse(evidence.get("backend").getAsString()),0);
                } else source = new BlockPos(-2448,player.level().getMaxY()-48,-1648);
                player.level().getChunkAt(source); player.connection.teleport(source.getX()-3,source.getY()+2,source.getZ()-3,0,0);
            }
            player.setNoGravity(true); player.setDeltaMovement(Vec3.ZERO); player.resetFallDistance();
            if (ticks < 25) return;
            if (!MODE.equals("verify")) {
                check(player.level().getBlockState(source).isAir(), "occupied fixture source");
                StructurePayloadFixtureSupport.chest(player,source);
                var before = StructureCellPayload.read(player.level(),source);
                var expected = StructureCaptureCoordinator.CheckpointPhase.valueOf(MODE.toUpperCase(Locale.ROOT));
                StructureCaptureCoordinator.observeCheckpoints(event.getServer(), checkpoint -> {
                    if (checkpoint.phase() != expected) return;
                    var saved = new LinkedHashMap<String,Object>();
                    saved.put("phase",expected.name()); saved.put("id",checkpoint.target().id().toString());
                    saved.put("backend",checkpoint.target().backend().toString()); saved.put("dimension",checkpoint.target().dimension().toString());
                    saved.put("source",List.of(source.getX(),source.getY(),source.getZ())); saved.put("digest",StructureCellPayload.digest(before));
                    saved.put("state",before.state().toString());
                    byte[] json = new GsonBuilder().setPrettyPrinting().create().toJson(saved).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    try (var file = FileChannel.open(directory.resolve("capture-payload-crash.json"),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
                        var bytes = ByteBuffer.wrap(json); while(bytes.hasRemaining()) file.write(bytes); file.force(true);
                    } catch (Exception error) { throw new IllegalStateException(error); }
                    AcademyCraft.LOGGER.warn("CAPTURE_PAYLOAD_INTENTIONAL_HALT {} {}",expected,checkpoint.operation());
                    Runtime.getRuntime().halt(expected == StructureCaptureCoordinator.CheckpointPhase.COPIED_SAVED ? 83 : 84);
                });
                var result = StructureTransactions.capture(player.level(),new StructureCaptureRequest(List.of(source),8,8,StructureCaptureRequest.ALLOW,true)
                        .withSkillCreation(player.getUUID(),AcademyCraft.academy("kinetic_throw")));
                throw new IllegalStateException("fault checkpoint not reached: "+result);
            }
            var live = LiveStructureProvenance.liveReference(event.getServer(),reference);
            boolean sourceOwned = matches(StructureCellPayload.read(player.level(),source));
            boolean targetOwned = live.flatMap(ref -> StructureSpaces.resolve(event.getServer(),ref)).map(handle -> matches(handle.world().read(BlockPos.ZERO))).orElse(false);
            boolean forward = evidence.get("phase").getAsString().equals("COMMIT_SAVED");
            boolean resolved = !StructureCaptureCoordinator.isPending(event.getServer(),reference)
                    && (forward ? !sourceOwned && player.level().getBlockState(source).isAir() && targetOwned : sourceOwned && !targetOwned && live.isEmpty());
            stableTicks = resolved ? stableTicks+1 : 0;
            if (stableTicks >= 40) {
                Files.writeString(directory.resolve("capture-payload-recovery-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                        "passed",true,"phase",evidence.get("phase").getAsString(),"backend",reference.backend().toString(),
                        "source_copies",sourceOwned?1:0,"target_copies",targetOwned?1:0,"payload_verified",true,"stable_ticks",stableTicks)));
                done=true;
            }
        } catch (Throwable error) {
            AcademyCraft.LOGGER.error("CAPTURE_PAYLOAD_RECOVERY_FAILED",error);
            try { Files.writeString(directory.resolve("capture-payload-recovery-result.json"),new Gson().toJson(Map.of("passed",false,"failure",error.toString()))); }
            catch(Exception ignored) {} done=true;
        }
    }
    private static boolean matches(StructureBlockData data) {
        return data.state().toString().equals(evidence.get("state").getAsString()) && StructureCellPayload.digest(data).equals(evidence.get("digest").getAsString());
    }
    @SubscribeEvent public static void noSettlement(StructureRestorationEvent event) {
        if (MODE.equals("verify")) event.setCanceled(true);
    }
    @SubscribeEvent public static void client(ClientTickEvent.Post event) {
        if (MODE.isEmpty() || closing) return;
        var minecraft = Minecraft.getInstance();
        if (done) { closing=true; minecraft.stop(); return; }
        if (minecraft.player == null || minecraft.level == null) return;
        directory=minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        check(directory.getFileName().toString().startsWith("native-sublevel-direct-smoke"),"isolated fixture required");
        if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) minecraft.gui.setScreen(null);
        if (!ready) org.lwjgl.glfw.GLFW.glfwIconifyWindow(minecraft.getWindow().handle());
        ready=true;
    }
    private static void check(boolean value,String message) { if(!value) throw new IllegalStateException(message); }
}
