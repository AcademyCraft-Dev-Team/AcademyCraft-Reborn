package org.academy.internal.client.validation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
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
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureQueries;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureWriteResult;
import org.academy.api.server.time.TemporalChannel;
import org.academy.api.server.time.TemporalField;
import org.academy.api.server.time.TemporalFieldLease;
import org.academy.api.server.time.TemporalScope;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.renderer.entity.NativeStructureProjectionRenderer;
import org.academy.internal.client.structure.NativeStructureClientInteraction;
import org.academy.internal.client.structure.NativeStructureRaycast;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.structure.space.NativeStructureChestMenu;
import org.academy.internal.common.structure.space.NativeStructureInteraction;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.academy.internal.server.time.TemporalRuntime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Actual client inventory packets against one isolated native chest; never replaces player inventory. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class NativeStructureChestClientSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.nativeStructureChestSmoke");
    private static final BlockPos CHEST = new BlockPos(-8, 80, -8);
    private static final String WORLD_NAME = "Native Structure Chest Smoke";
    private static final Component MARKER = Component.literal("Native chest conservation fixture");
    private static final List<ItemStackTemplate> BUNDLE_ITEMS = List.of(new ItemStackTemplate(Items.STONE), new ItemStackTemplate(Items.DIRT));
    private enum Phase {
        PREPARE, AIM_FIRST, FIRST_OPEN, PAUSED_CLICK, QUICK_OUT, QUICK_IN, SPLIT_MOVE, WAIT_MOVE_CLOSE,
        AIM_SECOND, RETURN_AFTER_MOVE, SPLIT_UNLOAD, RELOAD, AIM_THIRD, RETURN_AFTER_UNLOAD,
        REPLACE, NEGATIVE, FINAL_VIEW, FINISHED
    }

    private static volatile Phase phase = Phase.PREPARE;
    private static volatile MinecraftServer owner;
    private static volatile UUID playerId;
    private static volatile StructureRef reference;
    private static volatile UUID expectedRuntime;
    private static volatile String failure;
    private static volatile boolean guiSaved;
    private static volatile boolean worldSaved;
    private static volatile boolean lidVerified;
    private static volatile boolean staleSent;
    private static volatile boolean rejectedClickCorrected;
    private static boolean started;
    private static boolean finished;
    private static boolean guiRequested;
    private static boolean worldRequested;
    private static int clientTicks;
    private static int stableTicks;
    private static Phase clientPhase;
    private static boolean inputSent;
    private static int oldMenuId;
    private static int oldStateId;
    private static Path guiScreenshot;
    private static Path worldScreenshot;

    // The integrated server alone owns fixture mutation and phase advancement.
    private static NativeStructureRuntime runtime;
    private static StructureHandle handle;
    private static ServerLevel nativeLevel;
    private static StructurePose nearPose;
    private static int serverTicks;
    private static int phaseTick;
    private static int openerTicks;
    private static int staleObservedTick = -1;
    private static int negativeCase;
    private static int nextNegativeTick;
    private static CompoundTag unlockedData;
    private static ChestBlockEntity replacedChest;
    private static TemporalFieldLease actionPause;

    private NativeStructureChestClientSmoke() {}

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        require(failure == null, "Chest server probe failed: " + failure);
        require(++clientTicks <= 2400, "Chest client probe timed out at " + phase);
        if (!started) {
            var server = minecraft.getSingleplayerServer();
            require(server != null, "Chest smoke requires an integrated server");
            verifyWorld(minecraft, server);
            started = true;
            owner = server;
            playerId = minecraft.player.getUUID();
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            server.execute(() -> guardedServer(() -> initialize(server)));
        }
        var current = phase;
        if (clientPhase != current) {
            clientPhase = current;
            inputSent = false;
            stableTicks = 0;
        }
        var projection = findProjection(minecraft);
        if (current == Phase.AIM_FIRST || current == Phase.AIM_SECOND || current == Phase.AIM_THIRD) {
            if (inputSent || projection == null || !projection.viewState().runtimeId().equals(expectedRuntime)
                    || minecraft.gui.screen() != null) return;
            selectEmptyHand(minecraft);
            var hit = NativeStructureRaycast.pick(minecraft.player, minecraft.player.blockInteractionRange(), 1).orElse(null);
            if (hit == null || !hit.reference().equals(reference) || !hit.localPosition().equals(CHEST)) {
                stableTicks = 0;
                return;
            }
            if (++stableTicks < 15) return;
            require(NativeStructureClientInteraction.tryUse(minecraft), "Actual empty-hand use route must send chest packet");
            inputSent = true;
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_CHEST_CLIENT_USE phase={} ref={}", current, reference);
        } else if (current == Phase.FIRST_OPEN) {
            if (!(minecraft.player.containerMenu instanceof ChestMenu menu) || menu.slots.size() != 63) return;
            require(marked(menu.getSlot(0).getItem()) == 7, "Client chest must receive the seven real marked items");
            oldMenuId = menu.containerId;
            oldStateId = menu.getStateId();
            if (!inputSent) {
                require(menu.getSlot(1).getItem().is(Items.BUNDLE), "Client must receive the original fixture bundle");
                menu.setSelectedBundleItemIndex(1, 1);
                require(BundleItem.getSelectedItemIndex(menu.getSlot(1).getItem()) == 1, "Client bundle selection must be valid");
                minecraft.getConnection().send(new ServerboundSelectBundleItemPacket(1, 1));
                inputSent = true;
            }
            if (projection != null && projection.openChests().isOpen(CHEST)
                    && projection.chestOpenness(CHEST, 1) >= 0.99F
                    && NativeStructureProjectionRenderer.submittedChestFrames(projection.getId()) > 10
                    && NativeStructureProjectionRenderer.lastSubmittedChestOpenness(projection.getId()) >= 0.99F) {
                lidVerified = true;
            }
            if (!guiRequested && ++stableTicks >= 20) {
                guiRequested = true;
                guiScreenshot = screenshot(minecraft, "gui", () -> guiSaved = true);
            }
        } else if (current == Phase.PAUSED_CLICK) {
            if (!(minecraft.player.containerMenu instanceof ChestMenu menu)) return;
            if (!inputSent) {
                if (marked(menu.getSlot(0).getItem()) != 7 || !menu.getCarried().isEmpty()) return;
                require(minecraft.gameMode != null, "Client game mode unavailable");
                minecraft.gameMode.handleContainerInput(menu.containerId, 0, 0, ContainerInput.PICKUP, minecraft.player);
                require(marked(menu.getCarried()) == 7 && menu.getSlot(0).getItem().isEmpty(),
                        "Rejected click probe must first make an actual client prediction");
                menu.setSelectedBundleItemIndex(1, 0);
                require(BundleItem.getSelectedItemIndex(menu.getSlot(1).getItem()) == 0, "Rejected bundle probe must make a valid local selection");
                minecraft.getConnection().send(new ServerboundSelectBundleItemPacket(1, 0));
                inputSent = true;
            } else if (marked(menu.getSlot(0).getItem()) == 7 && menu.getCarried().isEmpty()) {
                if (++stableTicks >= 5) rejectedClickCorrected = true;
            }
        } else if (current == Phase.QUICK_OUT || current == Phase.QUICK_IN
                || current == Phase.RETURN_AFTER_MOVE || current == Phase.RETURN_AFTER_UNLOAD
                || current == Phase.SPLIT_MOVE || current == Phase.SPLIT_UNLOAD) {
            if (inputSent || !(minecraft.player.containerMenu instanceof ChestMenu menu)) return;
            int slot = 0;
            var input = ContainerInput.QUICK_MOVE;
            int button = 0;
            if (current == Phase.SPLIT_MOVE || current == Phase.SPLIT_UNLOAD) {
                if (marked(menu.getSlot(0).getItem()) != 7 || !menu.getCarried().isEmpty()) return;
                if (current == Phase.SPLIT_UNLOAD) {
                    oldMenuId = menu.containerId;
                    oldStateId = menu.getStateId();
                }
                input = ContainerInput.PICKUP;
                button = 1;
            } else if (current != Phase.QUICK_OUT) {
                slot = findMarkedPlayerSlot(menu);
                if (slot < 0) return;
            } else if (marked(menu.getSlot(0).getItem()) != 7) return;
            require(minecraft.gameMode != null, "Client game mode unavailable");
            minecraft.gameMode.handleContainerInput(menu.containerId, slot, button, input, minecraft.player);
            inputSent = true;
        } else if (current == Phase.RELOAD && !staleSent && projection != null
                && projection.viewState().runtimeId().equals(expectedRuntime)
                && minecraft.player.containerMenu == minecraft.player.inventoryMenu) {
            require(minecraft.getConnection() != null, "Client connection unavailable");
            minecraft.getConnection().send(new ServerboundContainerClickPacket(oldMenuId, oldStateId,
                    (short) 0, (byte) 0, ContainerInput.QUICK_MOVE,
                    new Int2ObjectOpenHashMap<HashedStack>(), HashedStack.EMPTY));
            staleSent = true;
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_CHEST_CLIENT_STALE_MENU_SENT id={} epoch={}", oldMenuId, expectedRuntime);
        } else if (current == Phase.FINAL_VIEW) {
            if (projection == null || minecraft.gui.screen() != null || projection.openChests().isOpen(CHEST)
                    || projection.chestOpenness(CHEST, 1) > 0.001F) return;
            if (!worldRequested && ++stableTicks >= 20) {
                require(NativeStructureProjectionRenderer.submittedChestFrames(projection.getId()) > 10,
                        "World screenshot requires actual ChestModel submissions");
                worldRequested = true;
                worldScreenshot = screenshot(minecraft, "world", () -> worldSaved = true);
            }
        } else if (current == Phase.FINISHED) {
            if (projection != null || ++stableTicks < 20) return;
            require(guiSaved && worldSaved && lidVerified, "Both screenshots and real lid animation must be verified");
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_CHEST_SMOKE_PASSED ref={} real_packets=true quick_move=true "
                    + "split_cursor=true transformed_close=true unload_close=true epoch=true stale_menu=true "
                    + "be_identity=true blocked_lock_loot=true rejected_click_resync=true bundle_guard=true "
                    + "conserved_items=7 opener=true lid=true gui={} world={}",
                    reference, guiScreenshot, worldScreenshot);
            finished = true;
            minecraft.stop();
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (!ENABLED || owner != event.getServer() || handle == null || phase == Phase.FINISHED || failure != null) return;
        guardedServer(() -> {
            require(++serverTicks <= 1800, "Chest server probe timed out at " + phase);
            require(serverTicks - phaseTick <= 300, "Chest phase timed out: " + phase);
            var player = player();
            player.setDeltaMovement(Vec3.ZERO);
            require(player.level().dimension().identifier().equals(reference.dimension()), "Player must stay in host dimension");
            if (handle.lifecycle() != StructureLifecycle.ACTIVE) return;
            if (phase == Phase.PREPARE) prepare(player);
            require(nativeLevel.players().isEmpty(), "Real player must never enter storage");
            assertConservation();
            switch (phase) {
                case AIM_FIRST -> {
                    if (player.containerMenu instanceof NativeStructureChestMenu) advance(Phase.FIRST_OPEN);
                }
                case FIRST_OPEN -> {
                    require(player.containerMenu instanceof NativeStructureChestMenu, "First menu unexpectedly closed");
                    require(ChestBlockEntity.getOpenCount(nativeLevel, CHEST) == 1, "Original opener count must remain one");
                    require(player.containerMenu.slots.size() == 63, "Vanilla 27 chest plus 36 player slots required");
                    if (++openerTicks >= 15 && guiSaved && lidVerified && BundleItem.getSelectedItemIndex(chest().getItem(1)) == 1) {
                        var context = (MinecraftServerContext) owner;
                        var temporal = (TemporalRuntime) context.getAcademyCraftServer().getTemporalService();
                        actionPause = temporal.acquireField(TemporalField.scale(TemporalScope.entities(Set.of(playerId)),
                                Set.of(TemporalChannel.ENTITY), 0));
                        require(!temporal.isPlayerActionTick(player) && NativeStructureInteraction.canMaintainMenu(player),
                                "Temporal fixture must deny actions while retaining the existing menu");
                        advance(Phase.PAUSED_CLICK);
                    }
                }
                case PAUSED_CLICK -> {
                    require(player.containerMenu instanceof NativeStructureChestMenu && chestCount() == 7
                                    && player.containerMenu.getCarried().isEmpty() && playerCount() == 0,
                            "Rejected temporal click must leave menu open and native inventory untouched");
                    require(BundleItem.getSelectedItemIndex(chest().getItem(1)) == 1,
                            "Separate bundle-selection packet must honor temporal action guard");
                    if (rejectedClickCorrected) {
                        actionPause.close();
                        actionPause = null;
                        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_CHEST_REJECTED_CLICK_CORRECTED prediction=true server_unchanged=true");
                        advance(Phase.QUICK_OUT);
                    }
                }
                case QUICK_OUT -> {
                    if (chestCount() == 0 && playerCount() == 7) advance(Phase.QUICK_IN);
                }
                case QUICK_IN -> {
                    if (chestCount() == 7 && playerCount() == 0) advance(Phase.SPLIT_MOVE);
                }
                case SPLIT_MOVE -> {
                    if (chestCount() == 3 && marked(player.containerMenu.getCarried()) == 4) {
                        handle.setPose(new StructurePose(nearPose.worldOrigin().add(24, 0, 0),
                                nearPose.rotationX(), nearPose.rotationY(), nearPose.rotationZ(), nearPose.rotationW()));
                        advance(Phase.WAIT_MOVE_CLOSE);
                    }
                }
                case WAIT_MOVE_CLOSE -> {
                    if (player.containerMenu == player.inventoryMenu) {
                        require(playerCount() == 4 && player.containerMenu.getCarried().isEmpty(), "Moved chest must return carried four items");
                        require(ChestBlockEntity.getOpenCount(nativeLevel, CHEST) == 0, "Moved chest must close its original opener");
                        handle.setPose(nearPose);
                        advance(Phase.AIM_SECOND);
                    }
                }
                case AIM_SECOND -> {
                    if (player.containerMenu instanceof NativeStructureChestMenu) advance(Phase.RETURN_AFTER_MOVE);
                }
                case RETURN_AFTER_MOVE -> {
                    if (chestCount() == 7 && playerCount() == 0) advance(Phase.SPLIT_UNLOAD);
                }
                case SPLIT_UNLOAD -> {
                    if (chestCount() == 3 && marked(player.containerMenu.getCarried()) == 4) {
                        var previousRuntime = handle.runtimeId();
                        require(runtime.unload(reference), "Unload with active menu must save successfully");
                        require(player.containerMenu == player.inventoryMenu && playerCount() == 4,
                                "Unload must close menu and return cursor before saving");
                        require(ChestBlockEntity.getOpenCount(nativeLevel, CHEST) == 0, "Unload must clear original opener count");
                        assertConservation();
                        handle = runtime.load(reference).orElseThrow();
                        expectedRuntime = handle.runtimeId();
                        require(!previousRuntime.equals(expectedRuntime), "Reload must replace runtime identity");
                        advance(Phase.RELOAD);
                    }
                }
                case RELOAD -> {
                    require(player.containerMenu == player.inventoryMenu && chestCount() == 3 && playerCount() == 4,
                            "Stale menu packet must not transfer inventory after reload");
                    if (staleSent && staleObservedTick < 0) staleObservedTick = serverTicks;
                    if (staleObservedTick >= 0 && serverTicks - staleObservedTick >= 20) advance(Phase.AIM_THIRD);
                }
                case AIM_THIRD -> {
                    if (player.containerMenu instanceof NativeStructureChestMenu) advance(Phase.RETURN_AFTER_UNLOAD);
                }
                case RETURN_AFTER_UNLOAD -> {
                    if (chestCount() == 7 && playerCount() == 0) {
                        replacedChest = chest();
                        var replacement = BlockEntity.loadStatic(CHEST, nativeLevel.getBlockState(CHEST),
                                replacedChest.saveWithFullMetadata(nativeLevel.registryAccess()), nativeLevel.registryAccess());
                        require(replacement instanceof ChestBlockEntity, "Replacement must retain actual chest payload");
                        nativeLevel.removeBlockEntity(CHEST);
                        nativeLevel.setBlockEntity(replacement);
                        require(chest() != replacedChest, "Fixture must replace actual BE object identity");
                        advance(Phase.REPLACE);
                    }
                }
                case REPLACE -> {
                    if (player.containerMenu == player.inventoryMenu) {
                        require(chestCount() == 7, "Replacement must preserve all seven items");
                        unlockedData = chest().saveWithFullMetadata(nativeLevel.registryAccess());
                        put(CHEST.above(), new StructureBlockData(Blocks.STONE.defaultBlockState()));
                        nextNegativeTick = serverTicks + 8;
                        advance(Phase.NEGATIVE);
                    }
                }
                case NEGATIVE -> negativeChecks(player);
                case FINAL_VIEW -> {
                    if (worldSaved) {
                        require(chestCount() == 7 && playerCount() == 0, "Final chest must own original seven items");
                        require(runtime.unload(reference), "Final verified fixture must save/unload");
                        assertConservation();
                        advance(Phase.FINISHED);
                    }
                }
                default -> { }
            }
        });
    }

    private static void initialize(MinecraftServer server) {
        var player = player();
        require(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()
                && player.getAbilities().mayBuild && !player.isSpectator(), "Prepare isolated player with empty hands and building permission");
        runtime = NativeStructureRuntime.backend(server);
        var fixtures = server.overworld().getDataStorage().computeIfAbsent(FixtureIndex.TYPE);
        require(fixtures.references.size() <= 1, "Chest fixture may own only one permanent slot");
        if (fixtures.references.isEmpty()) {
            handle = runtime.createEmpty(player.level(), StructurePose.identity(Vec3.ZERO)).orElseThrow();
            fixtures.references.add(handle.reference());
            fixtures.setDirty();
        } else handle = runtime.load(fixtures.references.getFirst()).orElseThrow();
        reference = handle.reference();
        expectedRuntime = handle.runtimeId();
    }

    private static void prepare(ServerPlayer player) {
        nativeLevel = runtime.storageLevel(reference).orElseThrow();
        require(playerCount() == 0 && droppedCount(player.level()) == 0 && droppedCount(nativeLevel) == 0,
                "Fixture marker must not already exist outside its original chest");
        var state = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
        var old = handle.world().read(CHEST);
        if (old.state().isAir()) {
            var fixture = new ChestBlockEntity(CHEST, state);
            var items = new ItemStack(Items.DIAMOND, 7);
            items.set(DataComponents.CUSTOM_NAME, MARKER);
            fixture.setItem(0, items);
            var bundle = new ItemStack(Items.BUNDLE);
            bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(BUNDLE_ITEMS));
            fixture.setItem(1, bundle);
            put(CHEST, new StructureBlockData(state, fixture.saveWithFullMetadata(nativeLevel.registryAccess())));
        } else {
            require(old.state().is(Blocks.CHEST) && chestCount() == 7, "Existing fixture chest must contain exactly seven original items");
        }
        require(nativeLevel.getBlockState(CHEST.above()).isAir(), "Fixture lid space must be empty");
        var center = Vec3.atCenterOf(findEmptyHostArea(player.level(), player.blockPosition()));
        double sine = Math.sin(0.55 / 2) / Math.sqrt(5.36);
        var rotation = new StructurePose(Vec3.ZERO, sine, 2 * sine, 0.6 * sine, Math.cos(0.55 / 2));
        nearPose = new StructurePose(center.subtract(rotation.localToWorldPoint(Vec3.atCenterOf(CHEST))),
                rotation.rotationX(), rotation.rotationY(), rotation.rotationZ(), rotation.rotationW());
        handle.setPose(nearPose);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        require(player.teleport(new TeleportTransition(player.level(), center.add(0, 0.1 - player.getEyeHeight(), 3.3),
                Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING)) == player, "Host camera placement failed");
        player.lookAt(EntityAnchorArgument.Anchor.EYES, center);
        advance(Phase.AIM_FIRST);
    }

    private static void negativeChecks(ServerPlayer player) {
        if (serverTicks < nextNegativeTick || !runtime.isPresented(reference)) return;
        var from = player.getEyePosition();
        var hit = StructureQueries.raycast(player.level(), from,
                from.add(player.getLookAngle().scale(player.blockInteractionRange()))).orElseThrow();
        require(hit.block().localPosition().equals(CHEST), "Negative test must target chest");
        if (negativeCase == 1) require(chest().isLocked() && !chest().canOpen(player), "Lock fixture must actually deny real player");
        if (negativeCase == 2) chest().setLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.withDefaultNamespace("chests/simple_dungeon")));
        try {
            require(NativeStructureInteraction.useBlock(player, hit) == NativeStructureInteraction.Result.DENIED
                            && player.containerMenu == player.inventoryMenu,
                    "Blocked/locked/loot chest must deny menu: " + negativeCase);
            if (negativeCase == 2) require(chest().getLootTable() != null, "Denied loot table must remain unresolved");
        } finally {
            if (negativeCase == 2) chest().setLootTable(null);
        }
        if (negativeCase == 0) {
            put(CHEST.above(), new StructureBlockData(Blocks.AIR.defaultBlockState()));
            var locked = unlockedData.copy();
            var lock = new CompoundTag();
            lock.putString("items", "minecraft:tripwire_hook");
            locked.put("lock", lock);
            put(CHEST, new StructureBlockData(nativeLevel.getBlockState(CHEST), locked));
        } else if (negativeCase == 1) {
            put(CHEST, new StructureBlockData(nativeLevel.getBlockState(CHEST), unlockedData));
        } else {
            require(!chest().isLocked() && chest().getLootTable() == null, "Negative fixtures must restore clean original chest");
            advance(Phase.FINAL_VIEW);
        }
        negativeCase++;
        nextNegativeTick = serverTicks + 8;
    }

    private static void selectEmptyHand(Minecraft minecraft) {
        if (minecraft.player.getMainHandItem().isEmpty()) return;
        for (int slot = 0; slot < 9; slot++) {
            if (!minecraft.player.getInventory().getItem(slot).isEmpty()) continue;
            minecraft.player.getInventory().setSelectedSlot(slot);
            minecraft.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
            return;
        }
        throw new IllegalStateException("Smoke needs an empty hotbar slot without changing inventory");
    }

    private static int findMarkedPlayerSlot(ChestMenu menu) {
        for (int slot = 27; slot < menu.slots.size(); slot++) if (marked(menu.getSlot(slot).getItem()) > 0) return slot;
        return -1;
    }

    private static int marked(ItemStack stack) {
        return stack.is(Items.DIAMOND) && MARKER.equals(stack.get(DataComponents.CUSTOM_NAME)) ? stack.getCount() : 0;
    }

    private static int chestCount() {
        int count = 0;
        for (int slot = 0; slot < chest().getContainerSize(); slot++) count += marked(chest().getItem(slot));
        return count;
    }

    private static int playerCount() {
        int count = 0;
        var inventory = player().getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) count += marked(inventory.getItem(slot));
        return count;
    }

    private static int droppedCount(ServerLevel level) {
        int count = 0;
        for (var entity : level.getAllEntities()) if (entity instanceof ItemEntity item && !item.isRemoved()) count += marked(item.getItem());
        return count;
    }

    private static void assertConservation() {
        int count = chestCount() + playerCount() + marked(player().containerMenu.getCarried())
                + droppedCount(player().level()) + droppedCount(nativeLevel);
        require(count == 7, "Seven marked items must be conserved across chest, player, cursor and both worlds; count=" + count);
        var bundle = chest().getItem(1);
        require(bundle.is(Items.BUNDLE) && bundle.getCount() == 1
                        && bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).items().equals(BUNDLE_ITEMS),
                "Selection tests must preserve the original bundle and its stone/dirt contents");
    }

    private static ChestBlockEntity chest() {
        require(nativeLevel.getBlockEntity(CHEST) instanceof ChestBlockEntity, "Original fixture chest missing");
        return (ChestBlockEntity) nativeLevel.getBlockEntity(CHEST);
    }

    private static ServerPlayer player() {
        var player = owner.getPlayerList().getPlayer(playerId);
        require(player != null, "Integrated smoke player disappeared");
        return player;
    }

    private static void put(BlockPos position, StructureBlockData data) {
        require(handle.world().compareAndSet(position, handle.world().read(position), data) == StructureWriteResult.APPLIED,
                "Chest fixture CAS failed at " + position);
    }

    private static void advance(Phase next) {
        phaseTick = serverTicks;
        phase = next;
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_CHEST_PHASE phase={} server_tick={}", next, serverTicks);
    }

    private static NativeStructureProjectionEntity findProjection(Minecraft minecraft) {
        if (reference == null) return null;
        NativeStructureProjectionEntity found = null;
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof NativeStructureProjectionEntity view && reference.equals(view.viewState().reference())) {
                require(found == null, "Exactly one chest projection may be tracked");
                found = view;
            }
        }
        return found;
    }

    private static BlockPos findEmptyHostArea(ServerLevel level, BlockPos start) {
        for (int rise = 5; rise <= 29; rise += 4) {
            var candidate = start.above(rise);
            if (candidate.getY() + 4 > level.getMaxY()) continue;
            boolean empty = true;
            for (var pos : BlockPos.betweenClosed(candidate.offset(-3, -3, -3), candidate.offset(3, 3, 6))) {
                if (!level.getBlockState(pos).isAir() || level.getBlockEntity(pos) != null) { empty = false; break; }
            }
            if (empty) return candidate;
        }
        throw new IllegalStateException("No existing empty host area available");
    }

    private static Path screenshot(Minecraft minecraft, String label, Runnable success) {
        var name = "native-structure-chest-" + label + "-" + ProcessHandle.current().pid() + "-" + System.currentTimeMillis() + ".png";
        var path = minecraft.gameDirectory.toPath().resolve("screenshots").resolve(name).toAbsolutePath();
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.gameRenderer.mainRenderTarget(), 1, message -> {
            try {
                require(Files.isRegularFile(path) && Files.size(path) > 0, "Screenshot was not saved: " + message.getString());
                success.run();
                AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_CHEST_SCREENSHOT_SAVED label={} path={}", label, path);
            } catch (Throwable error) { fail(error); }
        });
        return path;
    }

    private static void verifyWorld(Minecraft minecraft, MinecraftServer server) {
        try {
            var directory = minecraft.gameDirectory.toPath().toRealPath();
            var saves = directory.resolve("saves").toRealPath();
            var world = server.getWorldPath(LevelResource.ROOT).toRealPath();
            require(directory.getFileName().toString().startsWith("native-structure-chest-smoke")
                            && world.startsWith(saves) && world.getFileName().toString().equals(WORLD_NAME),
                    "Chest smoke must use its explicitly named isolated world and directory");
        } catch (IOException error) { throw new IllegalStateException("Cannot verify isolated chest world", error); }
    }

    private static void guardedServer(Runnable action) {
        if (failure != null) return;
        try { action.run(); } catch (Throwable error) {
            if (actionPause != null) { actionPause.close(); actionPause = null; }
            fail(error);
        }
    }

    private static void fail(Throwable error) {
        failure = error.toString();
        AcademyCraft.LOGGER.error("NATIVE_STRUCTURE_CHEST_SMOKE_FAILED", error);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class FixtureIndex extends SavedData {
        private static final Codec<StructureRef> REF_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(StructureRef::id),
                Identifier.CODEC.fieldOf("dimension").forGetter(StructureRef::dimension),
                Identifier.CODEC.fieldOf("backend").forGetter(StructureRef::backend),
                Codec.LONG.fieldOf("generation").forGetter(StructureRef::generation)
        ).apply(instance, StructureRef::new));
        private static final Codec<FixtureIndex> CODEC = REF_CODEC.listOf().xmap(FixtureIndex::new, index -> List.copyOf(index.references));
        private static final SavedDataType<FixtureIndex> TYPE = new SavedDataType<>(
                AcademyCraft.academy("native_structure_chest_smoke"), FixtureIndex::new, CODEC);
        private final List<StructureRef> references;
        private FixtureIndex() { this(List.of()); }
        private FixtureIndex(List<StructureRef> references) { this.references = new ArrayList<>(references); }
    }
}
