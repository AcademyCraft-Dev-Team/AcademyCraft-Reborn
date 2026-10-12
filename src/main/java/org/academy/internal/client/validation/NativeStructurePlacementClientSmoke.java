package org.academy.internal.client.validation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.internal.client.renderer.entity.NativeStructureProjectionRenderer;
import org.academy.internal.client.structure.NativeStructureClientInteraction;
import org.academy.internal.client.structure.NativeStructureRaycast;
import org.academy.internal.common.network.NativeStructureUsePacket;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.structure.BlockStructureOrientedBox;
import org.academy.internal.common.structure.space.NativeStructureInteraction;
import org.academy.internal.common.structure.space.NativeStructurePlaceContext;
import org.academy.internal.common.structure.space.NativeStructurePlacementCollision;
import org.academy.internal.common.structure.space.NativeStructurePlacementPolicy;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.academy.mixin.common.MixinServerGamePacketListenerNativeStructureAccess;
import org.joml.Quaterniond;
import org.misaka.MisakaNetworkClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Explicitly isolated actual-client placement probe. Never clears or replaces player inventory. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class NativeStructurePlacementClientSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.nativeStructurePlacementSmoke");
    private static final String WORLD_NAME = "Native Structure Placement Smoke";
    private static final Component MARKER = Component.literal("Native placement conservation fixture");
    private static final Component FAULT_MARKER = Component.literal("Native placement post-write fault fixture");
    private static final BlockPos STAIRS = new BlockPos(0, 81, 0);
    private static final BlockPos SLAB = new BlockPos(2, 81, 0);
    private static final BlockPos LEVER = new BlockPos(4, 81, 0);
    private static final BlockPos NEGATIVE = new BlockPos(-2, 81, 0);
    private static final BlockPos FAULT_CELL = new BlockPos(8, 81, 0);
    private enum Phase { PREPARE, STAIRS, REPLAY, SLAB, MERGE, LEVER, TOGGLE, NEGATIVES, FAULT, RELOAD, PHOTO, FINISHED }
    private enum ProtectionCase { CANCEL, CHANGE_HAND, CHANGE_HIT, GUARD }
    private static volatile Phase phase = Phase.PREPARE;
    private static volatile MinecraftServer owner;
    private static volatile UUID playerId;
    private static volatile StructureRef reference;
    private static volatile UUID runtimeId;
    private static volatile String failure;
    private static volatile boolean replaySent;
    private static volatile boolean screenshotSaved;
    private static volatile int stairsSlot, leverSlot, stoneSlot, emptySlot, faultSlot;
    private static boolean started, finished, inputSent, screenshotRequested;
    private static Phase clientPhase;
    private static int clientTicks, stableTicks;
    private static volatile StructureHit firstHit;
    private static Path screenshot;
    // Server-thread-owned state below.
    private static NativeStructureRuntime runtime;
    private static StructureHandle handle, other, faultHandle;
    private static ServerLevel faultStorage;
    private static ServerLevel storage;
    private static StructurePose pose;
    private static int serverTicks, phaseTick, replayObserved = -1, negativeIndex, nextNegativeTick;
    private static ProtectionCase protectionCase;
    private static boolean protectionObserved;
    private static boolean faultArmed, faultObserved;
    private static StructureHit faultHit;
    private static int faultTick;
    private static int placementEvents, phasePlacementEvents;

    private NativeStructurePlacementClientSmoke() {}

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        require(failure == null, "Placement server probe failed: " + failure);
        require(++clientTicks < 2400, "Placement client timeout at " + phase);
        if (!started) {
            var server = mc.getSingleplayerServer();
            require(server != null, "Placement probe requires integrated server");
            verifyWorld(mc, server);
            started = true;
            owner = server;
            playerId = mc.player.getUUID();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server.execute(() -> guarded(() -> initialize(server)));
        }
        if (clientPhase != phase) { clientPhase = phase; inputSent = false; stableTicks = 0; }
        var view = projection(mc);
        if (phase == Phase.STAIRS || phase == Phase.SLAB || phase == Phase.MERGE
                || phase == Phase.LEVER || phase == Phase.TOGGLE) {
            if (inputSent || view == null || !view.viewState().runtimeId().equals(runtimeId)
                    || mc.gui.screen() != null) return;
            var target = phase == Phase.STAIRS ? STAIRS.below() : phase == Phase.SLAB ? SLAB.below()
                    : phase == Phase.MERGE ? SLAB : phase == Phase.LEVER ? LEVER.below() : LEVER;
            int selected = phase == Phase.STAIRS ? stairsSlot
                    : phase == Phase.SLAB || phase == Phase.MERGE ? emptySlot : leverSlot;
            // Keep the empty slot selected throughout the first aim stabilization. Switching
            // immediately before tryUse below leaves no gameMode.tick to synchronize it for us.
            mc.player.getInventory().setSelectedSlot(phase == Phase.STAIRS ? emptySlot : selected);
            var held = phase == Phase.STAIRS ? mc.player.getInventory().getItem(stairsSlot)
                    : mc.player.getItemInHand(phase == Phase.SLAB || phase == Phase.MERGE
                            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            var expected = phase == Phase.STAIRS ? Items.OAK_STAIRS
                    : phase == Phase.SLAB || phase == Phase.MERGE ? Items.OAK_SLAB : Items.LEVER;
            if (!held.is(expected) || !MARKER.equals(held.get(DataComponents.CUSTOM_NAME))) return;
            var hit = NativeStructureRaycast.pick(mc.player, mc.player.blockInteractionRange(), 1).orElse(null);
            if (hit == null || !hit.reference().equals(reference) || !hit.localPosition().equals(target)) {
                stableTicks = 0;
                return;
            }
            if (++stableTicks < 15) return;
            if (phase == Phase.STAIRS) {
                firstHit = hit.structureHit();
                require(mc.player.getMainHandItem().isEmpty(), "First placement must switch away from the stabilized empty hand");
                mc.player.getInventory().setSelectedSlot(stairsSlot);
            }
            require(NativeStructureClientInteraction.tryUse(mc), "Actual client placement route was not consumed");
            inputSent = true;
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PLACEMENT_CLIENT_USE phase={} target={} selected={}", phase, target, selected);
        } else if (phase == Phase.REPLAY && !replaySent) {
            if (++stableTicks < 8) return;
            require(firstHit != null, "Replay requires original real-client hit");
            MisakaNetworkClient.send(new NativeStructureUsePacket(firstHit, InteractionHand.MAIN_HAND));
            replaySent = true;
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PLACEMENT_OLD_PACKET_SENT runtime={} revision={}", firstHit.runtimeId(), firstHit.geometryRevision());
        } else if (phase == Phase.PHOTO) {
            if (view == null || !view.viewState().runtimeId().equals(runtimeId) || mc.gui.screen() != null) return;
            if (!screenshotRequested && ++stableTicks >= 25) {
                require(NativeStructureProjectionRenderer.submittedFrames(view.getId()) >= 10,
                        "Screenshot requires actual post-reload model submissions");
                screenshotRequested = true;
                var name = "native-structure-placement-" + ProcessHandle.current().pid() + "-" + System.currentTimeMillis() + ".png";
                screenshot = mc.gameDirectory.toPath().resolve("screenshots").resolve(name).toAbsolutePath();
                Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, message -> {
                    try {
                        require(Files.isRegularFile(screenshot) && Files.size(screenshot) > 0, "Screenshot failed: " + message.getString());
                        screenshotSaved = true;
                        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PLACEMENT_SCREENSHOT path={}", screenshot);
                    } catch (Throwable error) { fail(error); }
                });
            }
        } else if (phase == Phase.FINISHED && view == null && ++stableTicks >= 15) {
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PLACEMENT_SMOKE_PASSED ref={} actual_client=true main_stairs=true "
                    + "off_slab_merge=true lever_support=true interact_before_place=true stale_packet=true conserved_units=8 "
                    + "server_direct_protection=true server_direct_collision=true post_write_quarantine=true fault_units=2 save_reload=true screenshot={}", reference, screenshot);
            finished = true;
            mc.stop();
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (!ENABLED || owner != event.getServer() || handle == null || failure != null || phase == Phase.FINISHED) return;
        guarded(() -> {
            ++serverTicks;
            int age = serverTicks - phaseTick;
            if (age == 60 || age == 280) diagnoseWaitingPhase();
            require(serverTicks < 1800 && age < 300, "Placement server timeout at " + phase);
            var player = player();
            player.setDeltaMovement(Vec3.ZERO);
            if (handle.lifecycle() != StructureLifecycle.ACTIVE || phase == Phase.PREPARE
                    && (other.lifecycle() != StructureLifecycle.ACTIVE || faultHandle.lifecycle() != StructureLifecycle.ACTIVE)) return;
            if (phase == Phase.PREPARE) prepare(player);
            require(storage.players().isEmpty(), "Player entered the storage world");
            require(player.level().dimension().identifier().equals(reference.dimension()), "Player left host world");
            assertConservation();
            switch (phase) {
                case STAIRS -> {
                    var state = storage.getBlockState(STAIRS);
                    if (state.is(Blocks.OAK_STAIRS)) {
                        require(state.getValue(StairBlock.FACING) == Direction.NORTH && state.getValue(StairBlock.HALF) == Half.BOTTOM,
                                "Rotated stair must use local NORTH and BOTTOM");
                        require(markedCount(Items.OAK_STAIRS) == 1 && markedCount(Items.OAK_SLAB) == 2,
                                "Main stair must consume exactly one without using offhand slab");
                        advance(Phase.REPLAY);
                    }
                }
                case REPLAY -> {
                    require(markedCount(Items.OAK_STAIRS) == 1 && storage.getBlockState(STAIRS.above()).isAir(), "Old packet consumed/placed twice");
                    if (replaySent && replayObserved < 0) replayObserved = serverTicks;
                    if (replayObserved >= 0 && serverTicks - replayObserved >= 15) { aimTop(player, SLAB.below(), 1); advance(Phase.SLAB); }
                }
                case SLAB -> {
                    var state = storage.getBlockState(SLAB);
                    if (state.is(Blocks.OAK_SLAB)) {
                        require(state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM && markedCount(Items.OAK_SLAB) == 1,
                                "Offhand first slab must be bottom and consume one");
                        aimTop(player, SLAB, 0.5);
                        advance(Phase.MERGE);
                    }
                }
                case MERGE -> {
                    if (storage.getBlockState(SLAB).getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
                        require(markedCount(Items.OAK_SLAB) == 0 && storage.getBlockState(SLAB.above()).isAir(), "Merge must consume second slab at same cell");
                        aimTop(player, LEVER.below(), 1);
                        advance(Phase.LEVER);
                    }
                }
                case LEVER -> {
                    var state = storage.getBlockState(LEVER);
                    if (state.is(Blocks.LEVER)) {
                        require(state.getValue(LeverBlock.FACE) == AttachFace.FLOOR && state.getValue(LeverBlock.FACING) == Direction.NORTH,
                                "Rotated lever must attach to local floor facing NORTH");
                        require(markedCount(Items.LEVER) == 1, "Lever placement must consume one");
                        aimTop(player, LEVER, 0.25);
                        advance(Phase.TOGGLE);
                    }
                }
                case TOGGLE -> {
                    if (storage.getBlockState(LEVER).getValue(LeverBlock.POWERED)) {
                        require(markedCount(Items.LEVER) == 1, "Ordinary use of lever must interact before placing held lever");
                        collisionChecks(player);
                        aimTop(player, NEGATIVE.below(), 1);
                        player.getInventory().setSelectedSlot(stoneSlot);
                        nextNegativeTick = serverTicks + 20;
                        advance(Phase.NEGATIVES);
                    }
                }
                case NEGATIVES -> negativeChecks(player);
                case FAULT -> faultChecks(player);
                case RELOAD -> {
                    assertFinalCells();
                    aimPhoto(player);
                    advance(Phase.PHOTO);
                }
                case PHOTO -> {
                    if (screenshotSaved) {
                        assertFinalCells();
                        require(runtime.unload(reference), "Final placement fixture unload failed");
                        advance(Phase.FINISHED);
                    }
                }
                default -> { }
            }
        });
    }

    private static void initialize(MinecraftServer server) {
        var player = player();
        require(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty(), "Fresh placement world must have empty hands");
        var index = server.overworld().getDataStorage().computeIfAbsent(FixtureIndex.TYPE);
        require(index.references.isEmpty(), "Placement probe requires a fresh world copy, preserving old evidence");
        runtime = NativeStructureRuntime.backend(server);
        handle = runtime.createEmpty(player.level(), StructurePose.identity(Vec3.ZERO)).orElseThrow();
        other = runtime.createEmpty(player.level(), StructurePose.identity(Vec3.ZERO)).orElseThrow();
        faultHandle = runtime.createEmpty(player.level(), StructurePose.identity(Vec3.ZERO)).orElseThrow();
        index.references.add(handle.reference()); index.references.add(other.reference()); index.references.add(faultHandle.reference()); index.setDirty();
        reference = handle.reference(); runtimeId = handle.runtimeId();
    }

    private static void prepare(ServerPlayer player) {
        storage = runtime.storageLevel(reference).orElseThrow();
        for (var pos : List.of(STAIRS.below(), SLAB.below(), LEVER.below(), NEGATIVE.below())) {
            require(handle.world().read(pos).state().isAir(), "Fresh fixture support occupied");
            put(handle, pos, new StructureBlockData(Blocks.STONE.defaultBlockState()));
        }
        var empty = new ArrayList<Integer>();
        for (int slot = 0; slot < 9; slot++) if (player.getInventory().getItem(slot).isEmpty()) empty.add(slot);
        require(empty.size() >= 5 && player.getOffhandItem().isEmpty(), "Need five empty hotbar slots and empty offhand; never replace existing items");
        stairsSlot = empty.get(0); leverSlot = empty.get(1); stoneSlot = empty.get(2); emptySlot = empty.get(3);
        faultSlot = empty.get(4);
        player.getInventory().setItem(stairsSlot, marked(Items.OAK_STAIRS, 2));
        player.getInventory().setItem(leverSlot, marked(Items.LEVER, 2));
        player.getInventory().setItem(stoneSlot, marked(Items.STONE, 2));
        player.setItemInHand(InteractionHand.OFF_HAND, marked(Items.OAK_SLAB, 2));
        var faultItems = new ItemStack(Items.STONE, 2); faultItems.set(DataComponents.CUSTOM_NAME, FAULT_MARKER);
        player.getInventory().setItem(faultSlot, faultItems);
        player.getInventory().setSelectedSlot(emptySlot);
        player.setGameMode(GameType.SURVIVAL);
        require(!player.hasInfiniteMaterials(), "Consumption probe must run in survival");
        player.getAbilities().mayfly = true; player.getAbilities().flying = true; player.onUpdateAbilities();
        var center = Vec3.atCenterOf(emptyHostArea(player.level(), player.blockPosition()));
        var rotation = new StructurePose(Vec3.ZERO, new Quaterniond().rotationXYZ(0.31, 0.57, -0.22));
        pose = new StructurePose(center.subtract(rotation.localToWorldPoint(Vec3.atCenterOf(SLAB.below()))), rotation.rotation());
        handle.setPose(pose); other.setPose(pose); faultHandle.setPose(pose);
        faultStorage = runtime.storageLevel(faultHandle.reference()).orElseThrow();
        put(faultHandle, FAULT_CELL.below(), new StructureBlockData(Blocks.STONE.defaultBlockState()));
        require(NativeStructurePlacementPolicy.canPlace(player.getInventory().getItem(stairsSlot)), "Audited marked stairs must be eligible");
        aimTop(player, STAIRS.below(), 1);
        advance(Phase.STAIRS);
    }

    private static void negativeChecks(ServerPlayer player) {
        if (serverTicks < nextNegativeTick || !runtime.isPresented(reference)) return;
        player.getInventory().setSelectedSlot(stoneSlot);
        var before = handle.world().read(NEGATIVE.below());
        var from = player.getEyePosition();
        var hit = StructureQueries.raycast(player.level(), from, from.add(player.getLookAngle().scale(player.blockInteractionRange()))).orElseThrow();
        require(hit.block().localPosition().equals(NEGATIVE.below()), "Protection probe needs exact support ray");
        protectionCase = ProtectionCase.values()[negativeIndex]; protectionObserved = false;
        try {
            var result = NativeStructureInteraction.useBlock(player, hit, InteractionHand.MAIN_HAND);
            require(result != NativeStructureInteraction.Result.APPLIED && result != NativeStructureInteraction.Result.BUSY,
                    "Protection callback failed to refuse placement: " + protectionCase + " / " + result);
            require(protectionObserved && storage.getBlockState(NEGATIVE).isAir() && markedCount(Items.STONE) == 2,
                    "Denied placement changed fixture ownership or skipped protection event");
        } finally {
            player.getInventory().setSelectedSlot(stoneSlot);
            if (!handle.world().read(NEGATIVE.below()).equals(before)) put(handle, NEGATIVE.below(), before);
            protectionCase = null;
        }
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PLACEMENT_SERVER_DIRECT_PROTECTION case={} passed=true", negativeIndex);
        if (++negativeIndex < ProtectionCase.values().length) { nextNegativeTick = serverTicks + 8; return; }
        player.getInventory().setSelectedSlot(faultSlot);
        aimTop(player, FAULT_CELL.below(), 1);
        nextNegativeTick = serverTicks + 20;
        advance(Phase.FAULT);
    }

    private static void faultChecks(ServerPlayer player) {
        if (serverTicks < nextNegativeTick) return;
        player.getInventory().setSelectedSlot(faultSlot);
        if (!faultObserved) {
            if (!runtime.isPresented(faultHandle.reference())) return;
            var from = player.getEyePosition();
            faultHit = StructureQueries.raycast(player.level(), from,
                    from.add(player.getLookAngle().scale(player.blockInteractionRange()))).orElseThrow();
            require(faultHit.block().structure().equals(faultHandle.reference())
                    && faultHit.block().localPosition().equals(FAULT_CELL.below()), "Fault probe must hit only its own support");
            faultArmed = true;
            NativeStructureInteraction.Result result;
            try { result = NativeStructureInteraction.useBlock(player, faultHit, InteractionHand.MAIN_HAND); }
            finally { faultArmed = false; }
            require(result != NativeStructureInteraction.Result.APPLIED && faultObserved,
                    "Successful-write sound fault must be caught by runtime quarantine");
            assertFaultConservation();
            faultTick = serverTicks;
            return;
        }
        if (serverTicks - faultTick < 8) return;
        var replay = NativeStructureInteraction.useBlock(player, faultHit, InteractionHand.MAIN_HAND);
        require(replay != NativeStructureInteraction.Result.APPLIED && replay != NativeStructureInteraction.Result.BUSY,
                "Quarantined old packet must be refused without a second reservation");
        assertFaultConservation();
        require(!runtime.unload(faultHandle.reference()), "Quarantined fixture must retain its recovery evidence");
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PLACEMENT_SERVER_DIRECT_POST_WRITE_FAULT ref={} block_retained=true inventory=1 total=2 quarantine=true replay_refused=true",
                faultHandle.reference());
        assertFinalCells();
        require(runtime.save(reference), "Placed structure must save");
        var oldRuntime = handle.runtimeId();
        require(runtime.unload(other.reference()) && runtime.unload(reference), "Both placement fixtures must unload");
        handle = runtime.load(reference).orElseThrow(); runtimeId = handle.runtimeId();
        require(!runtimeId.equals(oldRuntime), "Reload must replace the runtime identity");
        advance(Phase.RELOAD);
    }

    @SubscribeEvent
    public static void placementSoundFault(PlayLevelSoundEvent.AtPosition event) {
        if (!ENABLED || !faultArmed || !(event.getLevel() instanceof ServerLevel level)
                || level.getServer() != owner || !level.getServer().isSameThread()
                || level != player().level() || event.getSource() != SoundSource.BLOCKS
                || event.getSound() == null || event.getSound().value() != SoundEvents.STONE_PLACE) return;
        var context = StructureContexts.current().orElse(null);
        if (context == null || !context.reference().equals(faultHandle.reference())
                || event.getPosition().distanceToSqr(pose.localToWorldPoint(Vec3.atCenterOf(FAULT_CELL))) > 1.0e-12) return;
        require(faultStorage.getBlockState(FAULT_CELL).is(Blocks.STONE) && faultInventoryCount() == 1,
                "Injected sound failure must occur after the block write and one material reservation");
        faultObserved = true;
        throw new IllegalStateException("NATIVE_STRUCTURE_PLACEMENT_EXPECTED_POST_WRITE_SOUND_FAILURE");
    }

    private static void assertFaultConservation() {
        require(faultHandle.lifecycle() == StructureLifecycle.RECOVERY_REQUIRED
                && faultStorage.getBlockState(FAULT_CELL).is(Blocks.STONE)
                && faultStorage.getBlockState(FAULT_CELL.above()).isAir()
                && faultInventoryCount() == 1, "Post-write failure must retain one world block and one inventory material");
        for (var level : List.of(player().level(), faultStorage)) for (var entity : level.getAllEntities()) {
            require(!(entity instanceof ItemEntity item) || item.isRemoved()
                    || !FAULT_MARKER.equals(item.getItem().get(DataComponents.CUSTOM_NAME)), "Fault must not refund/drop another material");
        }
    }

    private static int faultInventoryCount() {
        int count = 0;
        for (int slot = 0; slot < player().getInventory().getContainerSize(); slot++) {
            var item = player().getInventory().getItem(slot);
            if (FAULT_MARKER.equals(item.get(DataComponents.CUSTOM_NAME))) count += item.getCount();
        }
        return count;
    }

    @SubscribeEvent
    public static void protection(StructurePlacementEvent event) {
        if (!ENABLED || !event.actor().getUUID().equals(playerId)
                || !event.hit().block().structure().equals(reference)
                && (faultHandle == null || !event.hit().block().structure().equals(faultHandle.reference()))) return;
        placementEvents++;
        phasePlacementEvents++;
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PLACEMENT_EVENT phase={} total={} phaseCount={} target={} proposed={} hand={} selected={} bounds={}",
                phase, placementEvents, phasePlacementEvents, event.target(), event.proposedState(), event.hand(),
                event.actor().getInventory().getSelectedSlot(), event.actor().getBoundingBox());
        if (protectionCase == null || !event.hit().block().structure().equals(reference)) return;
        protectionObserved = true;
        switch (protectionCase) {
            case CANCEL -> event.setCanceled(true);
            case CHANGE_HAND -> event.actor().getInventory().setSelectedSlot(emptySlot);
            case CHANGE_HIT -> storage.setBlock(NEGATIVE.below(), Blocks.COBBLESTONE.defaultBlockState(), 3);
            case GUARD -> {
                require(!runtime.save(reference) && !runtime.unload(reference), "Save/unload must refuse inside placement callback");
                var cell = handle.world().read(NEGATIVE);
                require(handle.world().compareAndSet(NEGATIVE, cell, cell) == StructureWriteResult.NOT_READY, "CAS must refuse inside placement callback");
                boolean poseDenied = false;
                try { handle.setPose(pose); } catch (IllegalStateException expected) { poseDenied = true; }
                require(poseDenied, "setPose must refuse inside placement callback");
                event.setCanceled(true);
            }
        }
    }

    private static void collisionChecks(ServerPlayer player) {
        var state = Blocks.STONE.defaultBlockState();
        var stairs = Blocks.OAK_STAIRS.defaultBlockState();
        var lever = Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR);
        require(NativeStructurePlacementCollision.canPlace(player.level(), handle, storage, NEGATIVE, state), "Empty rotated space should permit solid placement");
        var original = player.position();
        boolean building = player.blocksBuilding;
        try {
            player.blocksBuilding = true;
            player.setPos(pose.localToWorldPoint(Vec3.atCenterOf(NEGATIVE)).add(0, -0.3, 0));
            require(!NativeStructurePlacementCollision.canPlace(player.level(), handle, storage, NEGATIVE, state), "Stone must not intersect real player");
            require(!NativeStructurePlacementCollision.canPlace(player.level(), handle, storage, NEGATIVE, stairs), "Stairs must not intersect real player");
            require(NativeStructurePlacementCollision.canPlace(player.level(), handle, storage, NEGATIVE, lever), "Noncolliding lever may intersect real player");
        } finally { player.setPos(original); player.blocksBuilding = building; }
        var hostCell = BlockPos.containing(pose.localToWorldPoint(new Vec3(NEGATIVE.getX() + 0.5, NEGATIVE.getY() + 0.2, NEGATIVE.getZ() + 0.5)));
        require(player.level().getBlockState(hostCell).isAir() && player.level().getBlockEntity(hostCell) == null, "Host collision fixture must be previously empty");
        try {
            player.level().setBlock(hostCell, state, 3);
            require(!NativeStructurePlacementCollision.canPlace(player.level(), handle, storage, NEGATIVE, state), "Host stone must reject intersecting stone");
            require(!NativeStructurePlacementCollision.canPlace(player.level(), handle, storage, NEGATIVE, lever), "Host stone must reject embedded lever model");
        } finally { player.level().setBlock(hostCell, Blocks.AIR.defaultBlockState(), 3); }
        put(other, NEGATIVE, new StructureBlockData(state));
        require(!NativeStructurePlacementCollision.canPlace(player.level(), handle, storage, NEGATIVE, state), "Second active structure must block placement");
        put(other, NEGATIVE, new StructureBlockData(Blocks.AIR.defaultBlockState()));
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PLACEMENT_SERVER_DIRECT_COLLISION player_stone=true player_stairs=true player_lever_allowed=true host_stone=true host_lever=true other_structure=true");
    }

    private static void assertFinalCells() {
        require(storage.getBlockState(STAIRS).is(Blocks.OAK_STAIRS)
                && storage.getBlockState(STAIRS).getValue(StairBlock.FACING) == Direction.NORTH
                && storage.getBlockState(SLAB).is(Blocks.OAK_SLAB)
                && storage.getBlockState(SLAB).getValue(SlabBlock.TYPE) == SlabType.DOUBLE
                && storage.getBlockState(LEVER).is(Blocks.LEVER) && storage.getBlockState(LEVER).getValue(LeverBlock.POWERED),
                "Placed stair/slab/lever state changed across save/reload");
        assertConservation();
        if (faultObserved) assertFaultConservation();
    }

    private static void assertConservation() {
        int placed = storage.getBlockState(STAIRS).is(Blocks.OAK_STAIRS) ? 1 : 0;
        var slab = storage.getBlockState(SLAB);
        if (slab.is(Blocks.OAK_SLAB)) placed += slab.getValue(SlabBlock.TYPE) == SlabType.DOUBLE ? 2 : 1;
        if (storage.getBlockState(LEVER).is(Blocks.LEVER)) placed++;
        int inventory = 0;
        for (int i = 0; i < player().getInventory().getContainerSize(); i++) inventory += markedCount(player().getInventory().getItem(i));
        int dropped = 0;
        for (var level : List.of(player().level(), storage)) for (var entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity item && !item.isRemoved()) dropped += markedCount(item.getItem());
        }
        require(placed + inventory + dropped == 8 && dropped == 0, "Eight placement units must be conserved; placed=" + placed + " inventory=" + inventory + " dropped=" + dropped);
    }

    private static ItemStack marked(Item item, int count) { var result = new ItemStack(item, count); result.set(DataComponents.CUSTOM_NAME, MARKER); return result; }
    private static int markedCount(ItemStack item) { return MARKER.equals(item.get(DataComponents.CUSTOM_NAME)) ? item.getCount() : 0; }
    private static int markedCount(Item item) {
        int count = 0;
        for (int slot = 0; slot < player().getInventory().getContainerSize(); slot++) {
            var stack = player().getInventory().getItem(slot); if (stack.is(item)) count += markedCount(stack);
        }
        return count;
    }
    private static ServerPlayer player() { var player = owner.getPlayerList().getPlayer(playerId); require(player != null, "Probe player missing"); return player; }
    private static void put(StructureHandle target, BlockPos pos, StructureBlockData data) {
        require(target.world().compareAndSet(pos, target.world().read(pos), data) == StructureWriteResult.APPLIED, "Fixture CAS failed at " + pos);
    }
    private static void aimTop(ServerPlayer player, BlockPos cell, double height) {
        var point = new Vec3(cell.getX() + 0.5, cell.getY() + height, cell.getZ() + 0.5);
        var eye = pose.localToWorldPoint(point.add(0, 2.6, 1.0));
        aimAt(player, eye, pose.localToWorldPoint(point));
    }
    private static void aimPhoto(ServerPlayer player) {
        var point = pose.localToWorldPoint(new Vec3(1.5, 81, 0.5));
        aimAt(player, point.add(1, 1.2, 5.8), point);
    }
    private static void aimAt(ServerPlayer player, Vec3 eye, Vec3 target) {
        var direction = target.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
        // A later lookAt cannot replace the rotation recorded in the pending teleport. Its
        // acknowledgement must preserve the aimed ray even when the client view did not change.
        require(player.teleport(new TeleportTransition(player.level(), eye.add(0, -player.getEyeHeight(), 0),
                Vec3.ZERO, yaw, pitch, TeleportTransition.DO_NOTHING)) == player, "Host aim failed");
    }
    private static void diagnoseWaitingPhase() {
        if (storage == null || pose == null) return;
        try {
            var player = player();
            var from = player.getEyePosition();
            var to = from.add(player.getLookAngle().scale(player.blockInteractionRange()));
            var fresh = StructureQueries.raycast(player.level(), from, to).orElse(null);
            var target = phase == Phase.STAIRS ? STAIRS : phase == Phase.SLAB || phase == Phase.MERGE ? SLAB
                    : phase == Phase.LEVER || phase == Phase.TOGGLE ? LEVER : phase == Phase.FAULT ? FAULT_CELL : NEGATIVE;
            var candidateHandle = phase == Phase.FAULT ? faultHandle : handle;
            var candidateStorage = phase == Phase.FAULT ? faultStorage : storage;
            var hand = phase == Phase.SLAB || phase == Phase.MERGE ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            var held = player.getItemInHand(hand);
            var proposed = phase == Phase.STAIRS ? Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH)
                    : phase == Phase.SLAB || phase == Phase.MERGE ? Blocks.OAK_SLAB.defaultBlockState()
                    : phase == Phase.LEVER || phase == Phase.TOGGLE ? Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
                    : Blocks.STONE.defaultBlockState();
            if (fresh != null && NativeStructurePlacementPolicy.canPlace(held)) {
                var context = new NativeStructurePlaceContext(candidateStorage, player, hand, held, fresh);
                var computed = ((net.minecraft.world.item.BlockItem) held.getItem()).getBlock().getStateForPlacement(context);
                AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PLACEMENT_WAIT_CONTEXT phase={} clicked={} target={} canPlace={} computed={}",
                        phase, fresh.block(), context.getClickedPos(), context.canPlace(), computed);
                if (computed != null) proposed = computed;
            }
            var worldCell = BlockStructureOrientedBox.transform(new AABB(target), pose);
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PLACEMENT_WAIT phase={} age={} eventCount={} phaseEvents={} selected={} main={} off={} canUse={} menuInventory={} loaded={} awaitingTeleport={} presented={} revision={} fresh={} originalValidation={}",
                    phase, serverTicks - phaseTick, placementEvents, phasePlacementEvents, player.getInventory().getSelectedSlot(),
                    player.getMainHandItem(), player.getOffhandItem(), NativeStructureInteraction.canUseMenu(player),
                    player.containerMenu == player.inventoryMenu, player.connection.hasClientLoaded(),
                    ((MixinServerGamePacketListenerNativeStructureAccess) player.connection).academy$awaitingPositionFromClient(),
                    runtime.isPresented(candidateHandle.reference()), candidateHandle.geometryRevision(), fresh,
                    firstHit == null ? null : StructureQueries.validate(player.level(), from, to, firstHit));
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PLACEMENT_WAIT_GEOMETRY phase={} target={} state={} eye={} look={} playerBounds={} cellBounds={} cellIntersectsPlayer={} blocksBuilding={} collisionCanPlace={} storageUnobstructed={} hostMayInteract={} cooldown={}",
                    phase, target, proposed, from, player.getLookAngle(), player.getBoundingBox(), worldCell.bounds(),
                    worldCell.intersects(BlockStructureOrientedBox.axisAligned(player.getBoundingBox())), player.blocksBuilding,
                    NativeStructurePlacementCollision.canPlace(player.level(), candidateHandle, candidateStorage, target, proposed),
                    candidateStorage.isUnobstructed(proposed, target, CollisionContext.placementContext(player)),
                    player.level().mayInteract(player, BlockPos.containing(pose.localToWorldPoint(Vec3.atCenterOf(target)))),
                    player.getCooldowns().isOnCooldown(held));
        } catch (RuntimeException diagnosticFailure) {
            AcademyCraft.LOGGER.warn("NATIVE_STRUCTURE_PLACEMENT_WAIT_DIAGNOSTIC_FAILED", diagnosticFailure);
        }
    }
    private static void advance(Phase next) { phaseTick = serverTicks; phasePlacementEvents = 0; phase = next; AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PLACEMENT_PHASE phase={} tick={}", next, serverTicks); }
    private static NativeStructureProjectionEntity projection(Minecraft mc) {
        if (reference == null) return null;
        for (var entity : mc.level.entitiesForRendering()) if (entity instanceof NativeStructureProjectionEntity view && reference.equals(view.viewState().reference())) return view;
        return null;
    }
    private static BlockPos emptyHostArea(ServerLevel level, BlockPos start) {
        for (int rise = 9; rise < 42; rise += 5) {
            var center = start.above(rise); boolean clear = center.getY() + 8 <= level.getMaxY();
            if (!clear) continue;
            for (var pos : BlockPos.betweenClosed(center.offset(-9, -7, -9), center.offset(9, 8, 9))) {
                if (!level.getBlockState(pos).isAir() || level.getBlockEntity(pos) != null) { clear = false; break; }
            }
            if (clear) return center;
        }
        throw new IllegalStateException("No empty host fixture area");
    }
    private static void verifyWorld(Minecraft mc, MinecraftServer server) {
        try {
            var directory = mc.gameDirectory.toPath().toRealPath(); var saves = directory.resolve("saves").toRealPath();
            var world = server.getWorldPath(LevelResource.ROOT).toRealPath();
            require(directory.getFileName().toString().startsWith("native-structure-placement-smoke") && world.startsWith(saves)
                    && world.getFileName().toString().equals(WORLD_NAME), "Placement smoke requires explicitly named isolated world");
        } catch (IOException error) { throw new IllegalStateException("Cannot verify placement fixture", error); }
    }
    private static void guarded(Runnable action) { if (failure == null) try { action.run(); } catch (Throwable error) { fail(error); } }
    private static void fail(Throwable error) { failure = error.toString(); AcademyCraft.LOGGER.error("NATIVE_STRUCTURE_PLACEMENT_SMOKE_FAILED", error); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private static final class FixtureIndex extends SavedData {
        private static final Codec<StructureRef> REF_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(StructureRef::id), Identifier.CODEC.fieldOf("dimension").forGetter(StructureRef::dimension),
                Identifier.CODEC.fieldOf("backend").forGetter(StructureRef::backend), Codec.LONG.fieldOf("generation").forGetter(StructureRef::generation)
        ).apply(instance, StructureRef::new));
        private static final Codec<FixtureIndex> CODEC = REF_CODEC.listOf().xmap(FixtureIndex::new, value -> List.copyOf(value.references));
        private static final SavedDataType<FixtureIndex> TYPE = new SavedDataType<>(AcademyCraft.academy("native_structure_placement_smoke"), FixtureIndex::new, CODEC);
        private final List<StructureRef> references;
        private FixtureIndex() { this(List.of()); }
        private FixtureIndex(List<StructureRef> references) { this.references = new ArrayList<>(references); }
    }
}
