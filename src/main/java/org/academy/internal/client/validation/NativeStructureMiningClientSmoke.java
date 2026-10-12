package org.academy.internal.client.validation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.internal.client.renderer.entity.NativeStructureProjectionRenderer;
import org.academy.internal.client.structure.NativeStructureMiningClient;
import org.academy.internal.client.structure.NativeStructureRaycast;
import org.academy.internal.common.network.NativeStructureMinePacket;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.structure.space.NativeStructureMining;
import org.academy.internal.common.structure.space.NativeStructureMiningSessions;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.academy.mixin.client.MixinMinecraftNativeMiningAccess;
import org.joml.Quaterniond;
import org.misaka.MisakaNetworkClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Actual input/network/render probe, explicitly confined to a fresh disposable world. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class NativeStructureMiningClientSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.nativeStructureMiningSmoke");
    private static final String WORLD_NAME = "Native Structure Mining Smoke";
    private static final Component MARKER = Component.literal("Native mining fixture");
    private static final Component FAULT_MARKER = Component.literal("Native mining recovery fixture");
    private static final BlockPos STONE = new BlockPos(0, 80, 0);
    private static final BlockPos CHEST = new BlockPos(3, 80, 0);
    private static final BlockPos ORE = new BlockPos(6, 80, 0);
    private static final BlockPos SILK = new BlockPos(9, 80, 0);
    private static final BlockPos FAULT = new BlockPos(12, 80, 0);
    private enum Phase { PREPARE, ABORT, RELEASE, TOOL, TOOL_RELEASE, CANCEL_START, CANCEL_FINISH,
        STONE, REPLAY, CHEST, ORE, SILK, EMPTY, CREATIVE_PREPARE, CREATIVE, FAULT, FINISHED }
    private static volatile Phase phase = Phase.PREPARE;
    private static volatile MinecraftServer owner;
    private static volatile UUID playerId;
    private static volatile StructureRef reference;
    private static volatile String failure;
    private static volatile int pickSlot, silkSlot, axeSlot, emptySlot;
    private static volatile boolean releaseDone, inputSent, replaySent, screenshotSaved, chestScreenshotSaved;
    private static boolean started, finished, screenshotRequested, chestScreenshotRequested, screenshotArmed, hudWasHidden;
    private static Phase clientPhase;
    private static int clientTicks, stableTicks;
    private static volatile long firstRequest;
    private static volatile StructureHit firstHit;
    private static Path screenshot, chestScreenshot;
    // Server-only fields.
    private static NativeStructureRuntime runtime;
    private static StructureHandle handle, faultHandle;
    private static ServerLevel storage, faultStorage;
    private static StructurePose pose;
    private static int serverTicks, phaseTick, cancelEvents, faultSpawnAttempts, faultAt = -1;
    private static int oreExperience, experienceBefore;
    private static boolean poseMoved, faultArmed, faultCanceled;
    private static StructureHit faultHit;

    private NativeStructureMiningClientSmoke() {}

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        // Survival can clear flight while the initial world/teleport still reports onGround.
        // Maintain the explicitly granted fixture flight through the normal abilities packet.
        if (started && mc.player.getAbilities().mayfly && !mc.player.getAbilities().flying) {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        }
        require(failure == null, "Mining server probe failed: " + failure);
        require(++clientTicks < 4200, "Mining client timeout at " + phase);
        var input = (MixinMinecraftNativeMiningAccess) mc;
        if (!started) {
            owner = mc.getSingleplayerServer();
            require(owner != null, "Mining probe requires an integrated server");
            verifyWorld(mc, owner);
            started = true; playerId = mc.player.getUUID();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            owner.execute(() -> guarded(() -> initialize(owner)));
        }
        if (clientPhase != phase) {
            if (phase != Phase.TOOL_RELEASE) {
                mc.options.keyAttack.setDown(false);
                input.academy$continueNativeMiningAttack(false);
            }
            clientPhase = phase; stableTicks = 0; inputSent = false; releaseDone = false;
        }
        if (phase == Phase.RELEASE || phase == Phase.TOOL_RELEASE) {
            if (phase == Phase.TOOL_RELEASE && !releaseDone) {
                mc.player.getInventory().setSelectedSlot(emptySlot);
                mc.options.keyAttack.setDown(true);
                input.academy$continueNativeMiningAttack(true);
            }
            mc.options.keyAttack.setDown(false);
            input.academy$continueNativeMiningAttack(false);
            releaseDone = true;
            return;
        }
        if (phase == Phase.REPLAY && !replaySent) {
            if (++stableTicks < 8) return;
            require(firstRequest > 0 && firstHit != null, "A real first request is needed for replay");
            MisakaNetworkClient.send(new NativeStructureMinePacket(NativeStructureMinePacket.Action.START, firstRequest, firstHit));
            MisakaNetworkClient.send(new NativeStructureMinePacket(NativeStructureMinePacket.Action.STOP, firstRequest, null));
            replaySent = true;
            return;
        }
        if (phase == Phase.FINISHED) {
            if (++stableTicks < 20) return;
            require(screenshotSaved && chestScreenshotSaved && projection(mc) == null, "Completion requires both cracks screenshots and no empty projection");
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_MINING_SMOKE_PASSED ref={} actual_input=true abort=true tool_change=true "
                    + "start_finish_cancel=true moving_pose=true old_packets=true chest_units=7 tool_wear=true ore_xp={} silk=true "
                    + "creative=true empty_reload=true partial_delivery_quarantine=true fault_units=12 screenshot={} chest_screenshot={}",
                    reference, oreExperience, screenshot, chestScreenshot);
            finished = true; mc.stop(); return;
        }
        if (!clientMiningPhase()) return;
        var target = target();
        int slot = phase == Phase.CHEST ? axeSlot : phase == Phase.SILK ? silkSlot : pickSlot;
        if (!inputSent) {
            mc.player.getInventory().setSelectedSlot(slot);
            if (!MARKER.equals(mc.player.getMainHandItem().get(DataComponents.CUSTOM_NAME))) return;
            var picked = NativeStructureRaycast.pick(mc.player, mc.player.blockInteractionRange(), 1).orElse(null);
            if (clientTicks % 100 == 0) AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_MINING_CLIENT_WAIT phase={} picked={} slot={} flying={} position={}",
                    phase, picked == null ? null : picked.localPosition(), slot, mc.player.getAbilities().flying, mc.player.position());
            if (picked == null || !picked.reference().equals(reference) || !picked.localPosition().equals(target)) {
                stableTicks = 0; return;
            }
            if (++stableTicks < 12) return;
            if (phase == Phase.ABORT) firstHit = picked.structureHit();
            mc.options.keyAttack.setDown(true);
            input.academy$startNativeMiningAttack();
            inputSent = true;
            if (phase == Phase.ABORT) firstRequest = NativeStructureMiningClient.currentRequestId();
        } else {
            mc.options.keyAttack.setDown(true);
            input.academy$continueNativeMiningAttack(true);
        }
        boolean chestFrame = phase == Phase.CHEST;
        if ((phase == Phase.STONE && !screenshotRequested || chestFrame && !chestScreenshotRequested)
                && NativeStructureMiningClient.currentStage() >= 4) {
            if (!screenshotArmed) {
                screenshotArmed = true; hudWasHidden = mc.gui.hud.isHidden();
                if (!hudWasHidden) mc.gui.hud.toggle();
                return;
            }
            var view = projection(mc);
            if (view != null && NativeStructureMiningClient.currentStage() >= 7
                    && NativeStructureProjectionRenderer.lastSubmittedCrackStage(view.getId()) >= 6) {
                if (chestFrame) chestScreenshotRequested = true;
                else screenshotRequested = true;
                screenshotArmed = false;
                boolean restoreHud = hudWasHidden;
                var name = "native-structure-mining-" + (chestFrame ? "chest" : "stone") + "-cracks-"
                        + ProcessHandle.current().pid() + "-" + System.currentTimeMillis() + ".png";
                var path = mc.gameDirectory.toPath().resolve("screenshots").resolve(name).toAbsolutePath();
                if (chestFrame) chestScreenshot = path;
                else screenshot = path;
                Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, message -> {
                    try {
                        require(Files.isRegularFile(path) && Files.size(path) > 0, "Mining screenshot not saved");
                        if (chestFrame) chestScreenshotSaved = true;
                        else screenshotSaved = true;
                        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_MINING_SCREENSHOT path={}", path);
                    } catch (Throwable error) { fail(error); }
                    finally { mc.execute(() -> { if (mc.gui.hud.isHidden() != restoreHud) mc.gui.hud.toggle(); }); }
                });
            }
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (!ENABLED || owner != event.getServer() || handle == null || failure != null || phase == Phase.FINISHED) return;
        guarded(() -> {
            ++serverTicks;
            int age = serverTicks - phaseTick;
            if (age == 100 || age == 400) AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_MINING_WAIT phase={} age={} session={} canMine={} selected={}",
                    phase, age, NativeStructureMiningSessions.snapshot(player()),
                    org.academy.internal.common.structure.space.NativeStructureInteraction.canUseMenu(player()), player().getInventory().getSelectedSlot());
            require(serverTicks < 4000 && age < 700, "Mining server timeout at " + phase);
            player().setDeltaMovement(Vec3.ZERO);
            if (phase == Phase.PREPARE) {
                if (handle.lifecycle() != StructureLifecycle.ACTIVE || faultHandle.lifecycle() != StructureLifecycle.ACTIVE) return;
                prepare(); return;
            }
            require(storage.players().isEmpty() && faultStorage.players().isEmpty(), "Real player entered storage");
            require(player().level().dimension().identifier().equals(reference.dimension()), "Real player left host");
            var session = NativeStructureMiningSessions.snapshot(player()).orElse(null);
            switch (phase) {
                case ABORT -> { if (session != null && session.progress() >= 0.12F) advance(Phase.RELEASE); }
                case RELEASE -> {
                    if (!releaseDone || age < 12) return;
                    assertUntouched(); require(session == null, "Released attack retained a mining session");
                    advance(Phase.TOOL);
                }
                case TOOL -> { if (session != null && session.progress() >= 0.12F) advance(Phase.TOOL_RELEASE); }
                case TOOL_RELEASE -> {
                    if (!releaseDone || age < 12) return;
                    assertUntouched(); require(session == null, "Changed tool retained a mining session");
                    advance(Phase.CANCEL_START);
                }
                case CANCEL_START, CANCEL_FINISH -> {
                    if (cancelEvents == 0 || age < 15) return;
                    assertUntouched();
                    advance(phase == Phase.CANCEL_START ? Phase.CANCEL_FINISH : Phase.STONE);
                }
                case STONE -> {
                    if (!poseMoved && session != null && session.progress() >= 0.05F) {
                        pose = new StructurePose(pose.worldOrigin().add(0.035, 0, 0), pose.rotation());
                        handle.setPose(pose); poseMoved = true;
                    }
                    if (!storage.getBlockState(STONE).isAir()) return;
                    require(poseMoved && screenshotSaved && toolDamage(pickSlot) == 1, "Stone break skipped movement/cracks or wrong durability");
                    require(count(Items.COBBLESTONE, MARKER) == 1, "Stone must drop exactly one cobblestone");
                    advance(Phase.REPLAY);
                }
                case REPLAY -> {
                    if (!replaySent || age < 25) return;
                    require(count(Items.COBBLESTONE, MARKER) == 1 && toolDamage(pickSlot) == 1, "Old packets repeated a break");
                    aim(CHEST); advance(Phase.CHEST);
                }
                case CHEST -> {
                    if (!storage.getBlockState(CHEST).isAir()) return;
                    require(chestScreenshotSaved && count(Items.DIAMOND, MARKER) == 7 && count(Items.CHEST, MARKER) == 1
                            && toolDamage(axeSlot) == 1, "Chest inventory or tool durability not conserved");
                    require(storage.getBlockEntity(CHEST) == null, "Destroyed chest BE remained attached");
                    experienceBefore = totalExperience(); aim(ORE); advance(Phase.ORE);
                }
                case ORE -> {
                    if (!storage.getBlockState(ORE).isAir()) return;
                    oreExperience = totalExperience() - experienceBefore;
                    require(count(Items.DIAMOND, MARKER) == 8 && oreExperience >= 3 && oreExperience <= 7
                            && toolDamage(pickSlot) == 2, "Ore loot/experience/tool wear differs from vanilla");
                    experienceBefore = totalExperience(); aim(SILK); advance(Phase.SILK);
                }
                case SILK -> {
                    if (!storage.getBlockState(SILK).isAir()) return;
                    require(count(Items.DIAMOND_ORE, MARKER) == 1 && count(Items.DIAMOND, MARKER) == 8
                            && totalExperience() == experienceBefore && toolDamage(silkSlot) == 1,
                            "Silk touch must retain ore, suppress XP and consume durability once");
                    advance(Phase.EMPTY);
                }
                case EMPTY -> {
                    if (age < 12) return;
                    require(runtime.presentationStatus(reference) == StructurePresentationStatus.EMPTY,
                            "Last cell removal must hide the empty projection without deleting ownership");
                    require(runtime.save(reference) && runtime.unload(reference), "Empty structure save/unload failed");
                    var old = handle.runtimeId(); handle = runtime.load(reference).orElseThrow();
                    require(!handle.runtimeId().equals(old), "Reload must renew runtime identity");
                    advance(Phase.CREATIVE_PREPARE);
                }
                case CREATIVE_PREPARE -> {
                    if (handle.lifecycle() != StructureLifecycle.ACTIVE) return;
                    storage = runtime.storageLevel(reference).orElseThrow();
                    for (var pos : List.of(STONE, CHEST, ORE, SILK)) require(storage.getBlockState(pos).isAir(), "Reload resurrected a mined cell");
                    put(handle, STONE, new StructureBlockData(Blocks.STONE.defaultBlockState()));
                    player().setGameMode(GameType.CREATIVE); player().getAbilities().flying = true; player().onUpdateAbilities();
                    aim(STONE); advance(Phase.CREATIVE);
                }
                case CREATIVE -> {
                    if (!storage.getBlockState(STONE).isAir()) return;
                    require(toolDamage(pickSlot) == 2 && count(Items.COBBLESTONE, MARKER) == 1,
                            "Creative break consumed a tool or generated block loot");
                    player().setGameMode(GameType.SURVIVAL); player().getAbilities().mayfly = true;
                    player().getAbilities().flying = true; player().onUpdateAbilities();
                    player().getInventory().setSelectedSlot(axeSlot);
                    aim(FAULT); advance(Phase.FAULT);
                }
                case FAULT -> faultChecks(age);
                default -> { }
            }
        });
    }

    @SubscribeEvent
    public static void protection(StructureMiningEvent event) {
        if (!ENABLED || owner == null || event.actor() != player() || !event.hit().block().structure().equals(reference)) return;
        if (phase == Phase.CANCEL_START && event.phase() == StructureMiningEvent.Phase.START
                || phase == Phase.CANCEL_FINISH && event.phase() == StructureMiningEvent.Phase.FINISH) {
            require(StructureContexts.current().orElseThrow() == event.context(), "Protection scope not installed");
            require(!runtime.save(reference) && !runtime.unload(reference), "Protection allowed reentrant save/unload");
            cancelEvents++; event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void output(EntityJoinLevelEvent event) {
        if (!ENABLED || owner == null || event.getLevel() != owner.overworld() || !owner.isSameThread()) return;
        var context = StructureContexts.current().orElse(null);
        if (context == null || !(event.getEntity() instanceof ItemEntity item)) return;
        if (context.reference().equals(reference)) item.getItem().set(DataComponents.CUSTOM_NAME, MARKER);
        if (faultArmed && context.reference().equals(faultHandle.reference())) {
            faultSpawnAttempts++;
            if (faultSpawnAttempts == 2) { faultCanceled = true; event.setCanceled(true); }
        }
    }

    private static void initialize(MinecraftServer server) {
        var player = player();
        require(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty(), "Fresh mining fixture needs empty hands");
        var index = server.overworld().getDataStorage().computeIfAbsent(FixtureIndex.TYPE);
        require(index.references.isEmpty(), "Never reuse a completed or failed mining fixture");
        runtime = NativeStructureRuntime.backend(server);
        handle = runtime.createEmpty(player.level(), StructurePose.identity(Vec3.ZERO)).orElseThrow();
        faultHandle = runtime.createEmpty(player.level(), StructurePose.identity(Vec3.ZERO)).orElseThrow();
        index.references.add(handle.reference()); index.references.add(faultHandle.reference()); index.setDirty();
        reference = handle.reference();
    }

    private static void prepare() {
        var player = player(); storage = runtime.storageLevel(reference).orElseThrow();
        faultStorage = runtime.storageLevel(faultHandle.reference()).orElseThrow();
        var slots = new ArrayList<Integer>();
        for (int i = 0; i < 9; i++) if (player.getInventory().getItem(i).isEmpty()) slots.add(i);
        require(slots.size() >= 4 && player.getOffhandItem().isEmpty(), "Need four empty hotbar slots without replacing inventory");
        pickSlot = slots.get(0); silkSlot = slots.get(1); axeSlot = slots.get(2); emptySlot = slots.get(3);
        player.getInventory().setItem(pickSlot, marked(Items.IRON_PICKAXE, 1, MARKER));
        var silk = marked(Items.IRON_PICKAXE, 1, MARKER);
        silk.enchant(player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        player.getInventory().setItem(silkSlot, silk);
        player.getInventory().setItem(axeSlot, marked(Items.IRON_AXE, 1, MARKER));
        player.getInventory().setSelectedSlot(emptySlot);
        player.setGameMode(GameType.SURVIVAL); player.getAbilities().mayfly = true;
        player.getAbilities().flying = true; player.onUpdateAbilities();
        var rotation = new StructurePose(Vec3.ZERO, new Quaterniond().rotationXYZ(0.31, 0.57, -0.22));
        var center = Vec3.atCenterOf(emptyHostArea(player.level(), player.blockPosition()));
        pose = new StructurePose(center.subtract(rotation.localToWorldPoint(Vec3.atCenterOf(CHEST))), rotation.rotation());
        handle.setPose(pose); faultHandle.setPose(pose);
        put(handle, STONE, new StructureBlockData(Blocks.STONE.defaultBlockState()));
        put(handle, ORE, new StructureBlockData(Blocks.DIAMOND_ORE.defaultBlockState()));
        put(handle, SILK, new StructureBlockData(Blocks.DIAMOND_ORE.defaultBlockState()));
        putChest(handle, storage, CHEST, false); putChest(faultHandle, faultStorage, FAULT, true);
        require(totalExperience() == player.totalExperience, "Use a fixture without existing experience orbs");
        aim(STONE); advance(Phase.ABORT);
    }

    private static void putChest(StructureHandle target, ServerLevel level, BlockPos pos, boolean fault) {
        var state = Blocks.CHEST.defaultBlockState(); var chest = new ChestBlockEntity(pos, state);
        chest.setItem(0, marked(Items.DIAMOND, 7, fault ? FAULT_MARKER : MARKER));
        if (fault) chest.setItem(1, marked(Items.EMERALD, 5, FAULT_MARKER));
        put(target, pos, new StructureBlockData(state, chest.saveWithFullMetadata(level.registryAccess())));
    }

    private static void faultChecks(int age) {
        if (age < 18) return;
        var player = player();
        if (faultAt < 0) {
            var from = player.getEyePosition();
            faultHit = StructureQueries.raycast(player.level(), from, from.add(player.getLookAngle().scale(player.blockInteractionRange()))).orElseThrow();
            require(faultHit.block().structure().equals(faultHandle.reference()), "Fault ray missed dedicated chest");
            faultArmed = true;
            try {
                require(!NativeStructureMining.destroy(player, faultHit, () -> currentFaultHit(player)), "Partial output refusal falsely completed");
            } finally { faultArmed = false; }
            require(faultCanceled && faultSpawnAttempts == 2 && faultHandle.lifecycle() == StructureLifecycle.RECOVERY_REQUIRED,
                    "Partial host delivery must quarantine once and stop subsequent emissions");
            faultAt = serverTicks;
            verifyFaultEvidence();
            return;
        }
        if (serverTicks - faultAt < 10) return;
        int delivered = count(Items.DIAMOND, FAULT_MARKER) + count(Items.EMERALD, FAULT_MARKER);
        require(!NativeStructureMining.destroy(player, faultHit, () -> currentFaultHit(player)) && !runtime.unload(faultHandle.reference()),
                "Quarantined break must not replay or discard evidence");
        require(delivered == count(Items.DIAMOND, FAULT_MARKER) + count(Items.EMERALD, FAULT_MARKER), "Replay emitted more items");
        verifyFaultEvidence();
        require(runtime.save(reference) && runtime.unload(reference), "Final empty structure save/unload failed");
        advance(Phase.FINISHED);
    }

    private static StructureHit currentFaultHit(ServerPlayer player) {
        var from = player.getEyePosition();
        return StructureQueries.validate(player.level(), from,
                from.add(player.getLookAngle().scale(player.blockInteractionRange())), faultHit).currentHit().orElse(null);
    }

    private static void verifyFaultEvidence() {
        var directory = owner.getWorldPath(LevelResource.ROOT).resolve("data/academy/native_structures");
        try {
            var files = Files.list(directory);
            try (files) {
                var evidencePath = files.filter(path -> path.getFileName().toString().startsWith("recovery_")).findFirst().orElseThrow();
                CompoundTag record = NbtIo.readCompressed(evidencePath, NbtAccounter.create(1024 * 1024));
                var evidence = record.getCompoundOrEmpty("adapter_evidence");
                require(evidence.getStringOr("schema", "").equals("native_mining_drops_v1")
                        && evidence.getBooleanOr("manual_recovery_only", false), "Missing versioned durable mining output evidence");
                int pending = 0, published = 0;
                for (var tag : evidence.getListOrEmpty("outputs")) {
                    require(tag instanceof CompoundTag, "Malformed output receipt");
                    var row = (CompoundTag) tag;
                    if (!row.getStringOr("kind", "").equals("item")) continue;
                    int units = faultUnits(row.getCompoundOrEmpty("entity").getCompoundOrEmpty("Item"));
                    if (row.getStringOr("status", "").equals("PUBLISHED")) published += units;
                    else {
                        require(!row.getStringOr("status", "").equals("UNCERTAIN"), "Canceled fixture should have definite delivery state");
                        pending += units;
                    }
                }
                int remaining = 0;
                for (var tag : evidence.getCompoundOrEmpty("remaining_block_entity").getListOrEmpty("Items")) {
                    require(tag instanceof CompoundTag, "Malformed remaining inventory");
                    remaining += faultUnits((CompoundTag) tag);
                }
                int delivered = count(Items.DIAMOND, FAULT_MARKER) + count(Items.EMERALD, FAULT_MARKER);
                require(delivered > 0 && published == delivered && pending > 0 && delivered + pending + remaining == 12,
                        "Partial delivery must conserve 12 owned units across host and durable receipts");
                AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_MINING_RECOVERY_EVIDENCE path={} delivered={} pending={} remaining={} total=12",
                        evidencePath, delivered, pending, remaining);
            }
        } catch (IOException error) { throw new IllegalStateException("Cannot read mining recovery evidence", error); }
    }

    private static int faultUnits(CompoundTag tag) {
        if (tag.isEmpty()) return 0;
        var stack = ItemStack.CODEC.parse(player().level().registryAccess().createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
        return (stack.is(Items.DIAMOND) || stack.is(Items.EMERALD))
                && FAULT_MARKER.equals(stack.get(DataComponents.CUSTOM_NAME)) ? stack.getCount() : 0;
    }

    private static void assertUntouched() {
        require(storage.getBlockState(STONE).is(Blocks.STONE) && toolDamage(pickSlot) == 0
                && count(Items.COBBLESTONE, MARKER) == 0, "Canceled mining consumed tool or block");
    }
    private static int toolDamage(int slot) { return player().getInventory().getItem(slot).getDamageValue(); }
    private static ItemStack marked(Item item, int count, Component marker) {
        var stack = new ItemStack(item, count); stack.set(DataComponents.CUSTOM_NAME, marker); return stack;
    }
    private static int count(Item type, Component marker) {
        int count = 0;
        for (int slot = 0; slot < player().getInventory().getContainerSize(); slot++) {
            var stack = player().getInventory().getItem(slot);
            if (stack.is(type) && marker.equals(stack.get(DataComponents.CUSTOM_NAME))) count += stack.getCount();
        }
        for (var level : List.of(player().level(), storage, faultStorage)) for (var entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity item && !item.isRemoved() && item.getItem().is(type)
                    && marker.equals(item.getItem().get(DataComponents.CUSTOM_NAME))) count += item.getItem().getCount();
        }
        return count;
    }
    private static int totalExperience() {
        int total = player().totalExperience;
        for (var entity : player().level().getAllEntities()) if (entity instanceof ExperienceOrb orb && !orb.isRemoved()) {
            var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                    net.minecraft.util.ProblemReporter.DISCARDING, player().level().registryAccess());
            orb.saveWithoutId(output);
            total += orb.getValue() * output.buildResult().getIntOr("Count", 1);
        }
        return total;
    }
    private static boolean clientMiningPhase() {
        return switch (phase) { case ABORT, TOOL, CANCEL_START, CANCEL_FINISH, STONE, CHEST, ORE, SILK, CREATIVE -> true; default -> false; };
    }
    private static BlockPos target() { return switch (phase) { case CHEST -> CHEST; case ORE -> ORE; case SILK -> SILK; default -> STONE; }; }
    private static ServerPlayer player() { var player = owner.getPlayerList().getPlayer(playerId); require(player != null, "Test player disappeared"); return player; }
    private static void put(StructureHandle target, BlockPos pos, StructureBlockData data) {
        require(target.world().compareAndSet(pos, target.world().read(pos), data) == StructureWriteResult.APPLIED, "Fixture CAS failed at " + pos);
    }
    private static void aim(BlockPos pos) {
        var sourcePose = pos.equals(FAULT) ? faultHandle.pose() : pose;
        var point = Vec3.atLowerCornerOf(pos).add(0.5, 0.8, 0.5);
        var eye = sourcePose.localToWorldPoint(point.add(0, 1.8, 2.1));
        var direction = sourcePose.localToWorldPoint(point).subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
        require(player().teleport(new TeleportTransition(player().level(), eye.add(0, -player().getEyeHeight(), 0),
                Vec3.ZERO, yaw, pitch, TeleportTransition.DO_NOTHING)) == player(), "Host camera teleport failed");
    }
    private static NativeStructureProjectionEntity projection(Minecraft mc) {
        if (reference == null) return null;
        for (var entity : mc.level.entitiesForRendering()) if (entity instanceof NativeStructureProjectionEntity view
                && reference.equals(view.viewState().reference())) return view;
        return null;
    }
    private static BlockPos emptyHostArea(ServerLevel level, BlockPos start) {
        for (int rise = 9; rise < 42; rise += 5) {
            var center = start.above(rise); boolean clear = center.getY() + 8 <= level.getMaxY();
            for (var pos : BlockPos.betweenClosed(center.offset(-14, -7, -12), center.offset(14, 8, 12))) {
                if (!level.getBlockState(pos).isAir() || level.getBlockEntity(pos) != null) { clear = false; break; }
            }
            if (clear) return center;
        }
        throw new IllegalStateException("No empty host region for isolated mining fixture");
    }
    private static void advance(Phase next) {
        phaseTick = serverTicks; cancelEvents = 0; phase = next;
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_MINING_PHASE phase={} tick={}", next, serverTicks);
    }
    private static void verifyWorld(Minecraft mc, MinecraftServer server) {
        try {
            var directory = mc.gameDirectory.toPath().toRealPath(); var world = server.getWorldPath(LevelResource.ROOT).toRealPath();
            require(directory.getFileName().toString().startsWith("native-structure-mining-smoke")
                    && world.startsWith(directory.resolve("saves").toRealPath()) && world.getFileName().toString().equals(WORLD_NAME),
                    "Mining probe only accepts its explicitly named disposable world");
        } catch (IOException error) { throw new IllegalStateException("Cannot verify mining fixture", error); }
    }
    private static void guarded(Runnable action) { if (failure == null) try { action.run(); } catch (Throwable error) { fail(error); } }
    private static void fail(Throwable error) { failure = error.toString(); AcademyCraft.LOGGER.error("NATIVE_STRUCTURE_MINING_SMOKE_FAILED", error); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private static final class FixtureIndex extends SavedData {
        private static final Codec<StructureRef> REF_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(StructureRef::id), Identifier.CODEC.fieldOf("dimension").forGetter(StructureRef::dimension),
                Identifier.CODEC.fieldOf("backend").forGetter(StructureRef::backend), Codec.LONG.fieldOf("generation").forGetter(StructureRef::generation)
        ).apply(instance, StructureRef::new));
        private static final Codec<FixtureIndex> CODEC = REF_CODEC.listOf().xmap(FixtureIndex::new, value -> List.copyOf(value.references));
        private static final SavedDataType<FixtureIndex> TYPE = new SavedDataType<>(AcademyCraft.academy("native_structure_mining_smoke"), FixtureIndex::new, CODEC);
        private final List<StructureRef> references;
        private FixtureIndex() { this(List.of()); }
        private FixtureIndex(List<StructureRef> references) { this.references = new ArrayList<>(references); }
    }
}
