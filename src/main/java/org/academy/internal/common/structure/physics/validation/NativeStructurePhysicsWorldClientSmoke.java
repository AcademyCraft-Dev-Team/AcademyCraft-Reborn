package org.academy.internal.common.structure.physics.validation;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.client.renderer.entity.NativeStructureProjectionRenderer;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/** Real client projection/render evidence for the common server-action scenario. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class NativeStructurePhysicsWorldClientSmoke {
    private static boolean started, finished, finishRequested, capturePending;
    private static int ticks, absentTicks, movingSamples;
    private static long observedRevision = -1, firstRenderedFrame = -1;
    private static String captureStage = "";
    private static volatile String captureFailure;
    private static final Set<String> captures = new HashSet<>();

    private NativeStructurePhysicsWorldClientSmoke() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.nativeStructurePhysicsSmoke")
                || !"client".equals(System.getProperty("academy.nativeStructurePhysicsSmokeMode")) || finished) return;
        var mc = Minecraft.getInstance();
        require(++ticks < 4000, "Structure physics client probe timed out");
        require(captureFailure == null, "Screenshot failed: " + captureFailure);
        if (mc.level == null || mc.player == null) return;
        var server = mc.getSingleplayerServer();
        require(server != null, "Structure physics client probe requires its own integrated world");
        if (!started) {
            NativeStructurePhysicsWorldSmoke.verifyDirectory(server, "client");
            started = true;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            var playerId = mc.player.getUUID();
            server.execute(() -> NativeStructurePhysicsWorldSmoke.beginClient(server, playerId));
        }
        if (mc.player.getAbilities().mayfly && !mc.player.getAbilities().flying) {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        }
        var state = NativeStructurePhysicsWorldSmoke.clientState();
        require(state.failure() == null, "Server physics scenario failed: " + state.failure());
        if (state.complete()) {
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PHYSICS_CLIENT_PASSED moving_samples={} screenshots={} actual_keyboard_actions=false",
                    movingSamples, captures);
            finished = true;
            mc.stop();
            return;
        }
        var projection = projection(mc, state.first());
        if (state.scenarioFinished()) {
            if (projection != null || projection(mc, state.second()) != null) { absentTicks = 0; return; }
            if (++absentTicks < 15 || finishRequested) return;
            require(captures.contains("before") && captures.contains("landed") && movingSamples >= 3,
                    "Completion requires both rendered screenshots and moving pose synchronization");
            finishRequested = true;
            server.execute(() -> NativeStructurePhysicsWorldSmoke.finishClient(server));
            return;
        }
        if (projection == null) return;
        var stamp = projection.viewState();
        boolean coherent = state.recentFrames().stream().anyMatch(frame -> frame.reference().equals(stamp.reference())
                && frame.runtimeId().equals(stamp.runtimeId()) && frame.revision() == stamp.revision()
                && NativeStructurePhysicsWorldSmoke.samePose(frame.pose(), stamp.pose()));
        if (!coherent) return; // A normal network delay may leave a client outside this bounded recent-frame window.
        require(NativeStructurePhysicsWorldSmoke.samePose(stamp.pose(), projection.renderPose(1)),
                "Renderer uses a pose different from synchronized structure state");
        if (stamp.revision() != observedRevision && (state.phase().equals("FALL") || state.phase().equals("TERRAIN_FALL"))) {
            observedRevision = stamp.revision(); movingSamples++;
        }
        if (state.capture().isEmpty() || captures.contains(state.capture()) || capturePending) return;
        if (!captureStage.equals(state.capture())) {
            captureStage = state.capture(); firstRenderedFrame = -1;
        }
        require(projection.view().cells().size() == 3, "Captured compound must contain its three real storage cells");
        if (NativeStructureProjectionRenderer.lastSubmittedRevision(projection.getId()) != stamp.revision()) return;
        long rendered = NativeStructureProjectionRenderer.submittedFrames(projection.getId());
        if (firstRenderedFrame < 0) firstRenderedFrame = rendered;
        if (rendered - firstRenderedFrame < 20) return;
        capturePending = true;
        var stage = captureStage;
        var name = "native-structure-physics-" + stage + "-" + ProcessHandle.current().pid() + ".png";
        var path = mc.gameDirectory.toPath().resolve("screenshots").resolve(name).toAbsolutePath();
        Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, message -> {
            try {
                require(Files.isRegularFile(path) && Files.size(path) > 0, "Screenshot PNG was not saved: " + message.getString());
                mc.execute(() -> {
                    captures.add(stage); capturePending = false;
                    NativeStructurePhysicsWorldSmoke.captured(stage, path);
                    AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PHYSICS_SCREENSHOT stage={} frames={} revision={} path={}",
                            stage, rendered, stamp.revision(), path);
                });
            } catch (Throwable failure) { captureFailure = failure.toString(); }
        });
    }

    private static NativeStructureProjectionEntity projection(Minecraft mc, StructureRef reference) {
        if (reference == null) return null;
        NativeStructureProjectionEntity found = null;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof NativeStructureProjectionEntity projection && reference.equals(projection.viewState().reference())) {
                require(found == null, "One structure has duplicate projections");
                found = projection;
            }
        }
        return found;
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
