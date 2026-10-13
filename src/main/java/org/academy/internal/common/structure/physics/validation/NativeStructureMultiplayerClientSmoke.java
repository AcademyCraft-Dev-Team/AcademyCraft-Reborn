package org.academy.internal.common.structure.physics.validation;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.CameraType;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureHit;
import org.academy.internal.client.renderer.entity.NativeStructureProjectionRenderer;
import org.academy.internal.client.structure.NativeStructureClientInteraction;
import org.academy.internal.client.structure.NativeStructureMiningClient;
import org.academy.internal.client.structure.NativeStructureRaycast;
import org.academy.internal.common.network.NativeStructureMinePacket;
import org.academy.internal.common.network.NativeStructureUsePacket;
import org.academy.internal.common.network.NativeStructureUseResultPacket;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.academy.mixin.client.MixinMinecraftNativeMiningAccess;

import org.misaka.MisakaNetworkClient;

import java.net.InetSocketAddress;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;

import static org.academy.internal.common.structure.physics.validation.NativeStructureMultiplayerSmoke.*;

/** Real client input/packet actions. Shared files coordinate phases, never inventory or block mutations. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class NativeStructureMultiplayerClientSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.nativeStructureMultiplayerSmoke");
    private static final long RACE_REQUEST = 1_000_000;
    private static Path root;
    private static String role, reference;
    private static Phase phase;
    private static int ticks, age, stable, raceAge;
    private static int chestScreenTicks;
    private static int oldMenuId = -1, oldMenuStateId, menuReplayAge;
    private static ContainerScreen chestScreen;
    private static boolean sent, done, ready, focusNoted, captureRequested, waitingForDisconnect, stopped;
    private static boolean chestCaptureRequested;
    private static volatile boolean captureSaved;
    private static volatile boolean chestCaptureSaved;
    private static volatile String captureFailure;
    private static long requestId, placeRequest;
    private static long lastPhaseSequence;
    private static Properties lastPhaseRecord;
    private static StructureHit placeHit, raceHit;
    private static String acknowledgement;
    private static long firstRevision = -1;
    private static int movingRevisions;

    private NativeStructureMultiplayerClientSmoke() {}

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED || "server".equals(role()) || stopped) return;
        try { advance(); }
        catch (Throwable error) {
            AcademyCraft.LOGGER.error("NATIVE_STRUCTURE_MULTIPLAYER_CLIENT_FAILED role={}", role, error);
            if (root != null) {
                try { writeNew(root.resolve("coord").resolve(role + "-failure.txt"), error.toString()); }
                catch (Throwable suppressed) { error.addSuppressed(suppressed); }
                try { writeNew(root.resolve(role + "-result.txt"), "FAILED\n" + error + "\n"); }
                catch (Throwable suppressed) { error.addSuppressed(suppressed); }
            }
            stopped = true; Minecraft.getInstance().stop();
        }
    }

    private static void advance() {
        var mc = Minecraft.getInstance();
        require(++ticks < 13000, "Multiplayer client deadline");
        require(captureFailure == null, "Capture failed: " + captureFailure);
        if (root == null) {
            role = role(); root = verifyRoot(role, mc.gameDirectory.toPath());
            mc.options.pauseOnLostFocus = false;
            mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
            mc.options.framerateLimit().set(60); mc.options.enableVsync().set(false);
            mc.options.renderDistance().set(6); mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        // This channel must remain observable after disconnect, even if publishing FAILED failed.
        var serverResult = root.resolve("server-result.txt");
        if (Files.isRegularFile(serverResult)) {
            try (var reader = Files.newBufferedReader(serverResult)) {
                require(!"FAILED".equals(reader.readLine()), "Dedicated server result is FAILED");
            } catch (IOException error) { throw new IllegalStateException("Cannot inspect server result", error); }
        }
        if (waitingForDisconnect) {
            if (mc.level == null) { stopped = true; mc.stop(); }
            return;
        }
        if (mc.level == null || mc.player == null || mc.getConnection() == null) return;
        require(mc.getSingleplayerServer() == null && !mc.getConnection().getConnection().isMemoryConnection(), "A real remote TCP server is required");
        require(mc.getConnection().getConnection().getRemoteAddress() instanceof InetSocketAddress address
                && address.getAddress().isLoopbackAddress(), "Probe refuses a non-loopback server");
        require(mc.player.getGameProfile().name().equals(role.equals("actor") ? ACTOR : OBSERVER), "Unexpected test player identity");
        if (!ready) {
            if (++stable < 40 || mc.gui.screen() != null) return;
            var values = new Properties(); values.setProperty("uuid", mc.player.getUUID().toString()); values.setProperty("session", session());
            write(root.resolve("coord").resolve(role + ".ready"), values); ready = true; stable = 0;
        }
        if (mc.player.getAbilities().mayfly && !mc.player.getAbilities().flying) {
            mc.player.getAbilities().flying = true; mc.player.onUpdateAbilities();
        }
        var state = latestPhase();
        if (state == null) return;
        var next = Phase.valueOf(state.getProperty("phase"));
        require(next != Phase.FAILED, "Dedicated server reported failure");
        reference = state.getProperty("reference");
        if (phase != next) {
            releaseMining(mc);
            phase = next; age = 0; sent = false; done = false; stable = 0; requestId = 0; acknowledgement = "";
            chestScreen = null; chestScreenTicks = 0;
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_MULTIPLAYER_CLIENT_PHASE role={} phase={}", role, phase);
        }
        require(++age < 2200, "Client phase deadline: " + phase);
        var projection = projection(mc);
        if ((phase == Phase.MINE || phase == Phase.RACE) && age % 20 == 0) {
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_MULTIPLAYER_MINING_CLIENT phase={} age={} role={} status={} stage={} request={} active={} hasFocus={} mouseGrabbed={} sent={} selected={} remoteCracks={}",
                    phase, age, role, NativeStructureMiningClient.currentStatus(), NativeStructureMiningClient.currentStage(),
                    NativeStructureMiningClient.currentRequestId(), NativeStructureMiningClient.isActive(), mc.isWindowActive(),
                    mc.mouseHandler.isMouseGrabbed(), sent, mc.player.getInventory().getSelectedSlot(),
                    projection == null ? null : NativeStructureMiningClient.cracks(projection));
        }
        if (projection != null && projection.viewState().revision() != firstRevision) {
            if (firstRevision >= 0) movingRevisions++;
            firstRevision = projection.viewState().revision();
        }
        if (done) return;
        switch (phase) {
            case OPEN, REOPEN -> {
                if (!sent) use(mc, projection, CHEST, 0);
                boolean acknowledged = false;
                if (requestId != 0) {
                    var ack = NativeStructureClientInteraction.lastAcknowledgement();
                    if (ack != null && ack.requestId() == requestId) {
                        require(ack.status() == NativeStructureUseResultPacket.Status.APPLIED,
                                "Moving chest use rejected: " + ack.status());
                        acknowledged = true;
                    }
                }
                if (mc.gui.screen() instanceof ContainerScreen screen && screen.getMenu() == mc.player.containerMenu) {
                    if (chestScreen != screen) { chestScreen = screen; chestScreenTicks = 0; }
                    chestScreenTicks++;
                } else { chestScreen = null; chestScreenTicks = 0; }
                if (mc.player.containerMenu instanceof ChestMenu menu && menu.slots.size() == 63
                        && menu.getSlot(0).getItem().is(Items.DIAMOND) && menu.getSlot(0).getItem().getCount() == 7) {
                    if (phase == Phase.OPEN && !chestCaptureRequested && projection != null
                            && acknowledged && chestScreenTicks >= 8
                            && NativeStructureProjectionRenderer.submittedChestFrames(projection.getId()) >= 5) {
                        chestCaptureRequested = true;
                        captureChest(mc);
                    }
                    if (++stable >= 10 && acknowledged && chestScreenTicks >= 8 && chestCaptureSaved)
                        finishPhase("real_chest_menu=63\nactual_chest_screenshot=true");
                }
            }
            case TAKE -> {
                if (!(mc.player.containerMenu instanceof ChestMenu menu)) return;
                if (!sent) { click(mc, menu, 0, 0, ContainerInput.QUICK_MOVE); sent = true; }
                if (age >= 15 && menu.getSlot(0).getItem().isEmpty()) finishPhase("actual_quick_move=true");
            }
            case RETURN -> {
                if (!(mc.player.containerMenu instanceof ChestMenu menu)) return;
                if (!sent) {
                    for (int index = 27; index < menu.slots.size(); index++) if (menu.getSlot(index).getItem().is(Items.DIAMOND)) {
                        click(mc, menu, index, 0, ContainerInput.QUICK_MOVE); break;
                    }
                    sent = true;
                }
                if (age >= 15 && menu.getSlot(0).getItem().is(Items.DIAMOND) && menu.getSlot(0).getItem().getCount() == 7)
                    finishPhase("actual_return=true");
            }
            case CLOSE -> {
                if (!sent) { mc.player.closeContainer(); sent = true; }
                if (mc.player.containerMenu == mc.player.inventoryMenu && age >= 10) finishPhase("closed=true");
            }
            case PLACE -> {
                if (!sent) {
                    use(mc, projection, SUPPORT, 1);
                    if (sent) { placeRequest = requestId; }
                }
                var ack = NativeStructureClientInteraction.lastAcknowledgement();
                if (sent && ack != null && ack.requestId() == requestId) {
                    acknowledgement = ack.status().name();
                    require(ack.status() == NativeStructureUseResultPacket.Status.APPLIED
                                    || ack.status() == NativeStructureUseResultPacket.Status.STALE
                                    || ack.status() == NativeStructureUseResultPacket.Status.DENIED,
                            "Unexpected concurrent placement acknowledgement " + ack.status());
                    finishPhase("use_ack=" + acknowledgement + "\nrequest=" + requestId);
                }
            }
            case USE_REPLAY -> {
                if (!sent) {
                    require(placeHit != null && placeRequest > 0, "Missing real placement observation for replay");
                    MisakaNetworkClient.send(new NativeStructureUsePacket(placeRequest, placeHit, InteractionHand.MAIN_HAND));
                    sent = true;
                }
                if (age >= 15) finishPhase("old_use_packet_sent=true");
            }
            case MINE -> mine(mc, projection);
            case RACE -> {
                if (projection == null) return;
                if (projection.view().getBlockState(SUPPORT).isAir()) {
                    require(sent, "Racing peer never sent a mining START"); finishPhase("racing_packets=true"); return;
                }
                var picked = aim(mc, projection, SUPPORT, 2);
                if (picked == null) return;
                sync(mc);
                if (!sent) {
                    raceHit = picked.structureHit(); raceAge = 0;
                    MisakaNetworkClient.send(new NativeStructureMinePacket(NativeStructureMinePacket.Action.START, RACE_REQUEST, raceHit));
                    sent = true;
                } else {
                    var action = ++raceAge >= 60 ? NativeStructureMinePacket.Action.STOP : NativeStructureMinePacket.Action.HOLD;
                    MisakaNetworkClient.send(new NativeStructureMinePacket(action, RACE_REQUEST, picked.structureHit()));
                }
            }
            case MINE_REPLAY -> {
                if (!sent) {
                    require(raceHit != null, "No real race observation to replay");
                    for (var action : new NativeStructureMinePacket.Action[] { NativeStructureMinePacket.Action.START,
                            NativeStructureMinePacket.Action.HOLD, NativeStructureMinePacket.Action.STOP })
                        MisakaNetworkClient.send(new NativeStructureMinePacket(action, RACE_REQUEST, raceHit));
                    sent = true;
                }
                if (age >= 20) finishPhase("old_mining_packets_sent=true");
            }
            case CURSOR -> {
                if (!(mc.player.containerMenu instanceof ChestMenu menu)) return;
                oldMenuId = menu.containerId; oldMenuStateId = menu.getStateId();
                if (role.equals("observer")) { finishPhase("observer_waiting=true"); return; }
                if (!sent) { click(mc, menu, 0, 1, ContainerInput.PICKUP); sent = true; }
                if (age >= 10 && menu.getCarried().is(Items.DIAMOND) && menu.getCarried().getCount() == 4)
                    finishPhase("actual_cursor_count=4");
            }
            case MOVE_AWAY -> {
                if (mc.player.containerMenu != mc.player.inventoryMenu || !mc.player.containerMenu.getCarried().isEmpty()) return;
                if (!sent) {
                    require(oldMenuId > 0, "Missing real closed menu identity");
                    mc.getConnection().send(new ServerboundContainerClickPacket(oldMenuId, oldMenuStateId,
                            (short) 0, (byte) 0, ContainerInput.QUICK_MOVE,
                            new Int2ObjectOpenHashMap<HashedStack>(), HashedStack.EMPTY));
                    sent = true; menuReplayAge = age;
                    AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_MULTIPLAYER_STALE_MENU_SENT role={} oldMenu={} state={}",
                            role, oldMenuId, oldMenuStateId);
                }
                if (age - menuReplayAge >= 20) finishPhase("server_closed_moving_menu=true\nold_menu_packet_sent=true\nold_menu_wait_ticks=20");
            }
            case DONE -> {
                if (projection != null) return;
                require(movingRevisions >= 10, "Missing moving projections");
                require(chestCaptureSaved, "Missing actual menu and chest capture for this client");
                if (role.equals("observer")) require(captureSaved, "Observer never captured submitted mining cracks");
                writeNew(root.resolve(role + "-result.txt"), "PASSED\nreal_tcp=true\nphysical_keyboard_mouse=false\nmoving_revisions="
                        + movingRevisions + "\nshared_chest_capture=" + chestCaptureSaved + "\nobserver_cracks_capture=" + captureSaved + "\n");
                finishPhase("client_validated=true"); waitingForDisconnect = true;
                AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_MULTIPLAYER_CLIENT_PASSED role={} moving_revisions={}", role, movingRevisions);
            }
            default -> { }
        }
    }

    private static void mine(Minecraft mc, NativeStructureProjectionEntity projection) {
        if (projection == null) return;
        if (role.equals("observer")) {
            aim(mc, projection, PLACED, 0);
            var cracks = NativeStructureMiningClient.cracks(projection);
            if (!captureRequested && cracks.getOrDefault(PLACED, -1) >= 1
                    && NativeStructureProjectionRenderer.lastSubmittedCrackStage(projection.getId()) >= 1) {
                captureRequested = true;
                var name = "observer-moving-cracks-" + ProcessHandle.current().pid() + ".png";
                var path = root.resolve("observer/screenshots").resolve(name);
                Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, message -> {
                    try { require(Files.isRegularFile(path) && Files.size(path) > 0, "Cracks capture did not save"); captureSaved = true; }
                    catch (Throwable error) { captureFailure = error.toString(); }
                });
            }
            if (captureSaved && projection.view().getBlockState(PLACED).isAir()
                    && NativeStructureMiningClient.cracks(projection).isEmpty()) finishPhase("remote_cracks_submitted_captured_cleared=true");
            return;
        }
        if (!focusNoted) {
            writeNew(root.resolve("actor-focus-required.txt"), "Activate the StructureActor Minecraft window and allow its mouse capture.\n");
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_MULTIPLAYER_ACTOR_FOCUS_REQUIRED"); focusNoted = true;
        }
        if (projection.view().getBlockState(PLACED).isAir()) {
            require(sent, "Actor target disappeared before input"); releaseMining(mc); finishPhase("minecraft_attack_input=true"); return;
        }
        var picked = aim(mc, projection, PLACED, 2);
        if (picked == null || !mc.isWindowActive()) { stable = 0; return; }
        if (!mc.mouseHandler.isMouseGrabbed()) { mc.mouseHandler.grabMouse(); stable = 0; return; }
        // grabMouse gives vanilla a large missTime; keep attack released until normal key handling clears it.
        if (++stable < 5) return;
        var input = (MixinMinecraftNativeMiningAccess) mc;
        mc.options.keyAttack.setDown(true);
        if (!sent) { input.academy$startNativeMiningAttack(); sent = true; }
        else input.academy$continueNativeMiningAttack(true);
    }

    private static void use(Minecraft mc, NativeStructureProjectionEntity projection, BlockPos target, int slot) {
        if (projection == null || mc.gui.screen() != null) return;
        var picked = aim(mc, projection, target, slot);
        if (picked == null || ++stable < 5) return;
        long before = NativeStructureClientInteraction.lastRequestId();
        require(NativeStructureClientInteraction.tryUse(mc), "Actual structure use input was not consumed");
        long after = NativeStructureClientInteraction.lastRequestId();
        if (after <= before) return;
        requestId = after; sent = true;
        if (phase == Phase.PLACE) placeHit = picked.structureHit();
    }

    private static void captureChest(Minecraft mc) {
        var name = role + "-moving-shared-chest-" + ProcessHandle.current().pid() + ".png";
        var path = root.resolve(role).resolve("screenshots").resolve(name);
        Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, message -> {
            try { require(Files.isRegularFile(path) && Files.size(path) > 0, "Shared chest capture did not save"); chestCaptureSaved = true; }
            catch (Throwable error) { captureFailure = error.toString(); }
        });
    }

    private static NativeStructureRaycast.Hit aim(Minecraft mc, NativeStructureProjectionEntity projection, BlockPos target, int slot) {
        if (mc.gui.screen() != null) return null;
        mc.player.getInventory().setSelectedSlot(slot);
        var point = projection.renderPose(1).localToWorldPoint(new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.999));
        var direction = point.subtract(mc.player.getEyePosition());
        mc.player.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        mc.player.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))));
        var picked = NativeStructureRaycast.pick(mc.player, mc.player.blockInteractionRange(), 1).orElse(null);
        return picked != null && picked.reference().id().toString().equals(reference) && picked.localPosition().equals(target) ? picked : null;
    }
    private static void sync(Minecraft mc) {
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(mc.player.getInventory().getSelectedSlot()));
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(mc.player.getYRot(), mc.player.getXRot(),
                mc.player.onGround(), mc.player.horizontalCollision));
    }
    private static void click(Minecraft mc, ChestMenu menu, int slot, int button, ContainerInput input) {
        require(mc.gameMode != null, "Missing real client game mode");
        mc.gameMode.handleContainerInput(menu.containerId, slot, button, input, mc.player);
    }
    private static void releaseMining(Minecraft mc) {
        mc.options.keyAttack.setDown(false);
        if (mc.player != null && mc.gameMode != null) ((MixinMinecraftNativeMiningAccess) mc).academy$continueNativeMiningAttack(false);
    }
    private static void finishPhase(String detail) {
        if (done) return;
        writeNew(root.resolve("coord").resolve(role + "-" + phase + ".done"), "session=" + session() + "\n" + detail + "\n");
        done = true;
    }
    private static Properties latestPhase() {
        var directory = root.resolve("coord/phases");
        if (!Files.isDirectory(directory)) return null;
        final Path latest;
        try (var entries = Files.list(directory)) {
            latest = entries.filter(path -> path.getFileName().toString().matches("[0-9]{6}\\.properties"))
                    .max(Comparator.comparing(path -> path.getFileName().toString())).orElse(null);
        } catch (IOException error) { throw new IllegalStateException("Cannot inspect immutable phases", error); }
        if (latest == null) return null;
        long sequence = Long.parseLong(latest.getFileName().toString().substring(0, 6));
        require(sequence >= lastPhaseSequence && sequence > 0, "Coordination sequence moved backwards");
        if (sequence == lastPhaseSequence) return lastPhaseRecord;
        var values = read(latest);
        require(session().equals(values.getProperty("session")), "Wrong coordination session");
        require(Long.toString(sequence).equals(values.getProperty("sequence")), "Phase sequence differs from immutable filename");
        lastPhaseSequence = sequence; lastPhaseRecord = values;
        return values;
    }
    private static NativeStructureProjectionEntity projection(Minecraft mc) {
        NativeStructureProjectionEntity found = null;
        for (var entity : mc.level.entitiesForRendering()) if (entity instanceof NativeStructureProjectionEntity candidate
                && candidate.viewState().reference().id().toString().equals(reference)) {
            require(found == null, "Duplicate structure projection"); found = candidate;
        }
        return found;
    }
}
