package org.academy.internal.common.structure.physics.validation;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.input.KeyInputEvent;
import org.academy.api.client.input.MouseScrollEvent;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.client.renderer.entity.NativeStructureProjectionRenderer;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.skills.lv2.PneumaticGrasp;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/** Real client projection/render evidence for the common server-action scenario. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class NativeStructurePhysicsWorldClientSmoke {
    private static boolean started, finished, finishRequested, capturePending;
    private static int ticks, absentTicks, movingSamples, gPress, gRelease, hRelease, scrolls, scrollSteps;
    private static long observedRevision = -1, firstRenderedFrame = -1;
    private static String captureStage = "";
    private static volatile String captureFailure;
    private static final Set<String> captures = new HashSet<>();
    private static InputSystem.KeyCombination originalStart, originalStop;
    private static boolean customBinding;
    private static Boolean originalPauseOnLostFocus;
    private static long manualStartedAt;
    private static String inputStage = "";

    private NativeStructurePhysicsWorldClientSmoke() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.nativeStructurePhysicsSmoke")
                || !"client".equals(System.getProperty("academy.nativeStructurePhysicsSmokeMode")) || finished) return;
        try { advance(); }
        catch (RuntimeException | Error failure) { restoreBindings(); throw failure; }
    }

    private static void advance() {
        var mc = Minecraft.getInstance();
        var graspState = NativeStructurePhysicsGraspSmokeChecks.manualState();
        boolean manualPhase = NativeStructurePhysicsWorldSmoke.clientState().phase().equals("GRASP")
                && !graspState.stage().equals("NONE") && !graspState.stage().equals("AUTO_HOLD");
        if (manualPhase) {
            if (manualStartedAt == 0) manualStartedAt = System.nanoTime();
            require(System.nanoTime() - manualStartedAt < 300_000_000_000L,
                    "Client input exceeded its separate 300-second wall-clock deadline");
        }
        require(manualPhase || ++ticks < 4500, "Structure physics client probe timed out");
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
        observeGraspInput(mc, graspState);
        require(state.failure() == null, "Server physics scenario failed: " + state.failure());
        if (state.complete()) {
            restoreBindings();
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PHYSICS_CLIENT_PASSED moving_samples={} screenshots={} actual_keyboard_actions={}",
                    movingSamples, captures, Boolean.getBoolean("academy.nativeStructurePhysicsGraspSmoke"));
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

    private static void observeGraspInput(Minecraft mc, NativeStructurePhysicsGraspSmokeChecks.ManualState state) {
        if (!Boolean.getBoolean("academy.nativeStructurePhysicsGraspSmoke")) return;
        if (state.restoreBindings()) { restoreBindings(); return; }
        if (!state.waiting()) return;
        if (originalStart == null) {
            originalPauseOnLostFocus = mc.options.pauseOnLostFocus;
            mc.options.pauseOnLostFocus = false;
            originalStart = InputSystem.getKeyBinding(PneumaticGrasp.Client.KEY_NAME_START);
            originalStop = InputSystem.getKeyBinding(PneumaticGrasp.Client.KEY_NAME_STOP);
            require(originalStart != null && originalStop != null, "Skill input bindings are unavailable");
            InputSystem.setKeyBinding(PneumaticGrasp.Client.KEY_NAME_START,
                    InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G, InputConstants.PRESS, 0));
            InputSystem.setKeyBinding(PneumaticGrasp.Client.KEY_NAME_STOP,
                    InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G, InputConstants.RELEASE, 0));
        }
        if (state.stage().equals("CUSTOM_G_START") && !customBinding) {
            InputSystem.setKeyBinding(PneumaticGrasp.Client.KEY_NAME_STOP,
                    InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_H, InputConstants.RELEASE, 0));
            customBinding = true;
        }
        if (!inputStage.equals(state.stage())) {
            inputStage = state.stage();
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.player.sendSystemMessage(Component.literal("[Physics input check] " + state.instruction()));
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_GRASP_CLIENT_INPUT stage={} custom_binding={} instruction={}",
                    state.stage(), customBinding, state.instruction());
        }
        publishInput(mc);
    }

    private static boolean observingInput(Minecraft mc) {
        return Boolean.getBoolean("academy.nativeStructurePhysicsGraspSmoke") && originalStart != null
                && NativeStructurePhysicsGraspSmokeChecks.manualState().waiting()
                && mc.player != null && mc.gui.screen() == null && mc.isWindowActive() && mc.mouseHandler.isMouseGrabbed();
    }

    /** Observes window callbacks only. Never dispatches, posts, sends or synthesizes an input. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void key(KeyInputEvent event) {
        var mc = Minecraft.getInstance();
        if (!observingInput(mc) || event.isCanceled()) return;
        if (event.key == InputConstants.KEY_G && event.action == InputConstants.PRESS
                && AbilitySystemClient.canUseSkillSilently(Skills.PNEUMATIC_GRASP.get())) gPress++;
        if (event.key == InputConstants.KEY_G && event.action == InputConstants.RELEASE) gRelease++;
        if (event.key == InputConstants.KEY_H && event.action == InputConstants.RELEASE) hRelease++;
        publishInput(mc);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void scroll(MouseScrollEvent event) {
        var mc = Minecraft.getInstance();
        if (!observingInput(mc) || event.yOffset == 0) return;
        scrolls++; scrollSteps += (int) Math.signum(event.yOffset); publishInput(mc);
    }

    private static void publishInput(Minecraft mc) {
        NativeStructurePhysicsGraspSmokeChecks.observeInputs(new NativeStructurePhysicsGraspSmokeChecks.Inputs(
                mc.player.getUUID(), gPress, gRelease, hRelease, scrolls, scrollSteps, customBinding,
                observingInput(mc) && AbilitySystemClient.canUseSkillSilently(Skills.PNEUMATIC_GRASP.get())));
    }

    private static void restoreBindings() {
        if (originalStart != null) InputSystem.setKeyBinding(PneumaticGrasp.Client.KEY_NAME_START, originalStart);
        if (originalStop != null) InputSystem.setKeyBinding(PneumaticGrasp.Client.KEY_NAME_STOP, originalStop);
        if (originalStart != null || originalStop != null) AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_GRASP_CLIENT_BINDINGS_RESTORED");
        if (originalPauseOnLostFocus != null) Minecraft.getInstance().options.pauseOnLostFocus = originalPauseOnLostFocus;
        originalPauseOnLostFocus = null;
        originalStart = null; originalStop = null; customBinding = false;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { restoreBindings(); }

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
