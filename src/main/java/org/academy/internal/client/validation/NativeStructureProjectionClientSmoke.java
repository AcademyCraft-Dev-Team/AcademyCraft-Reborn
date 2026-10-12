package org.academy.internal.client.validation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.StructureContexts;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructureInteractionEvent;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureQueries;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureWriteResult;
import org.academy.internal.client.renderer.entity.NativeStructureProjectionRenderer;
import org.academy.internal.client.structure.NativeStructureRaycast;
import org.academy.internal.common.network.NativeStructureUsePacket;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.structure.space.NativeStructureInteraction;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.misaka.MisakaNetworkClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Opt-in real network/render probe restricted to a pre-existing, disposable integrated world. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class NativeStructureProjectionClientSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.nativeStructureProjectionSmoke");
    private static final String WORLD_NAME = "Native Structure Projection Smoke";
    private static final BlockPos SUPPORT = new BlockPos(-8, 80, -8);
    private static final BlockPos LEVER = SUPPORT.above();
    private static final BlockPos LAMP = SUPPORT.east();
    private static final BlockPos FURNACE = SUPPORT.west();
    private static final BlockPos STAIRS = SUPPORT.east(2);
    private static final List<BlockPos> CELLS = List.of(SUPPORT, LEVER, LAMP, FURNACE, STAIRS);

    private static volatile MinecraftServer owner;
    private static volatile UUID playerId;
    private static volatile StructureRef reference;
    private static volatile StructurePose expectedPose;
    private static volatile String failure;
    private static volatile boolean prepared;
    private static volatile boolean protectionsPassed;
    private static volatile boolean useSent;
    private static volatile boolean replayReady;
    private static volatile boolean replaySent;
    private static volatile boolean visualReady;
    private static volatile boolean screenshotSaved;
    private static volatile boolean unloaded;
    private static boolean started;
    private static boolean finished;
    private static boolean screenshotRequested;
    private static boolean unloadRequested;
    private static int clientTicks;
    private static int stableTicks;
    private static int absentTicks;
    private static long firstRenderedFrame = -1;
    private static StructureHit originalHit;
    private static Path screenshotPath;

    // The integrated server exclusively owns the fields below.
    private static NativeStructureRuntime runtime;
    private static StructureHandle handle;
    private static BlockPos hostBase;
    private static int serverTicks;
    private static int acceptedAt = -1;
    private static int replayAt = -1;
    private static int protectionCase;
    private static int nextProtectionTick;

    private NativeStructureProjectionClientSmoke() {
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        require(failure == null, "Projection server probe failed: " + failure);
        require(++clientTicks <= 2400, "Projection client probe timed out");
        if (!started) {
            var server = minecraft.getSingleplayerServer();
            require(server != null, "Projection smoke requires an integrated server");
            verifyWorld(minecraft, server);
            started = true;
            owner = server;
            playerId = minecraft.player.getUUID();
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            server.execute(() -> guardedServer(() -> initialize(server)));
        }
        if (!prepared || !protectionsPassed) return;
        var projection = findProjection(minecraft);
        if (unloaded) {
            if (projection != null) {
                absentTicks = 0;
                return;
            }
            if (++absentTicks < 20) return;
            require(screenshotSaved, "Rendered screenshot must finish before successful shutdown");
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PROJECTION_SMOKE_PASSED ref={} protections=true packet=true stale_replay=true "
                    + "furnace_state=true rotated_render=true unload=true inventory_retained=true screenshot={}",
                    reference, screenshotPath);
            finished = true;
            minecraft.stop();
            return;
        }
        if (projection == null) return;
        var view = projection.view();
        var stamp = projection.viewState();
        if (!view.getBlockState(FURNACE).is(Blocks.FURNACE)
                || !view.getBlockState(FURNACE).getValue(AbstractFurnaceBlock.LIT)) return;
        if (!useSent) {
            if (!samePose(stamp.pose(), expectedPose) || !view.getBlockState(LEVER).is(Blocks.LEVER)
                    || view.getBlockState(LEVER).getValue(LeverBlock.POWERED)
                    || view.cells().size() != CELLS.size()) return;
            var picked = NativeStructureRaycast.pick(minecraft.player,
                    minecraft.player.blockInteractionRange(), 1.0F).orElse(null);
            if (picked == null || !picked.reference().equals(reference) || !picked.localPosition().equals(LEVER)) {
                stableTicks = 0;
                return;
            }
            if (++stableTicks < 20) return;
            originalHit = picked.structureHit();
            useSent = true;
            require(org.academy.internal.client.structure.NativeStructureClientInteraction.tryUse(minecraft),
                    "The empty-hand client input route must send the actual structure use packet");
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PROJECTION_CLIENT_USE_SENT ref={} local={} revision={}",
                    reference, LEVER, originalHit.geometryRevision());
            return;
        }
        if (replayReady && !replaySent) {
            if (!view.getBlockState(LEVER).getValue(LeverBlock.POWERED)
                    || !view.getBlockState(LAMP).getValue(RedstoneLampBlock.LIT)
                    || stamp.revision() == originalHit.geometryRevision()) return;
            replaySent = true;
            MisakaNetworkClient.send(new NativeStructureUsePacket(originalHit));
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PROJECTION_CLIENT_STALE_REPLAY_SENT old={} current={}",
                    originalHit.geometryRevision(), stamp.revision());
            return;
        }
        if (!visualReady || !samePose(stamp.pose(), expectedPose) || !view.getBlockState(LAMP).getValue(RedstoneLampBlock.LIT)) return;
        var frames = NativeStructureProjectionRenderer.submittedFrames(projection.getId());
        if (NativeStructureProjectionRenderer.lastSubmittedRevision(projection.getId()) != stamp.revision()) return;
        if (firstRenderedFrame < 0) firstRenderedFrame = frames;
        if (frames - firstRenderedFrame < 20) return;
        if (!screenshotRequested) {
            screenshotRequested = true;
            var name = "native-structure-projection-" + ProcessHandle.current().pid() + "-" + System.currentTimeMillis() + ".png";
            screenshotPath = minecraft.gameDirectory.toPath().resolve("screenshots").resolve(name).toAbsolutePath();
            Screenshot.grab(minecraft.gameDirectory, name, minecraft.gameRenderer.mainRenderTarget(), 1, message -> {
                try {
                    require(Files.isRegularFile(screenshotPath) && Files.size(screenshotPath) > 0,
                            "Screenshot callback did not save the expected PNG: " + message.getString());
                    screenshotSaved = true;
                    AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PROJECTION_SCREENSHOT_SAVED path={} frames={} revision={}",
                            screenshotPath, frames, stamp.revision());
                } catch (Throwable error) {
                    fail(error);
                }
            });
            return;
        }
        if (screenshotSaved && !unloadRequested) {
            unloadRequested = true;
            owner.execute(() -> guardedServer(NativeStructureProjectionClientSmoke::verifyUnload));
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (!ENABLED || owner != event.getServer() || handle == null || unloaded || failure != null) return;
        guardedServer(() -> {
            require(++serverTicks <= 1800, "Projection server probe timed out");
            if (handle.lifecycle() != StructureLifecycle.ACTIVE) {
                require(serverTicks < 300, "Projection fixture did not activate");
                return;
            }
            var player = player();
            require(player.level().dimension().identifier().equals(handle.reference().dimension()),
                    "Interaction must leave the real player in the host dimension");
            require(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty(),
                    "Projection fixture requires empty real hands without replacing inventory");
            player.setDeltaMovement(Vec3.ZERO);
            var level = storage();
            require(level.players().isEmpty(), "Projection must not move a real player into storage");
            if (!prepared) prepare(player, level);
            assertFurnaceConservation(level);
            if (!protectionsPassed) {
                advanceProtectionChecks(player, level);
                return;
            }
            if (!useSent) return;
            var powered = level.getBlockState(LEVER).getValue(LeverBlock.POWERED);
            var lit = level.getBlockState(LAMP).getValue(RedstoneLampBlock.LIT);
            if (acceptedAt < 0) {
                if (!powered || !lit) return;
                acceptedAt = serverTicks;
                AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PROJECTION_SERVER_LEVER_ACCEPTED ref={} lamp=true", reference);
            }
            require(powered && lit, "Stale replay must never toggle the lever or lamp back off");
            if (serverTicks - acceptedAt >= 8) replayReady = true;
            if (replaySent && replayAt < 0) replayAt = serverTicks;
            if (replayAt >= 0 && serverTicks - replayAt >= 20 && !visualReady) rotateForScreenshot(player);
        });
    }

    private static void initialize(MinecraftServer server) {
        var player = player();
        require(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty(),
                "Prepare the isolated smoke player with empty hands");
        require(player.getAbilities().mayBuild && !player.isSpectator(), "Smoke requires a player allowed to build");
        runtime = NativeStructureRuntime.backend(server);
        var fixtures = server.overworld().getDataStorage().computeIfAbsent(FixtureIndex.TYPE);
        require(fixtures.references.size() <= 1, "Projection fixture index may own only one slot");
        if (fixtures.references.isEmpty()) {
            handle = runtime.createEmpty(player.level(), StructurePose.identity(Vec3.ZERO)).orElseThrow(() ->
                    new IllegalStateException("Projection smoke needs one free permanent slot"));
            fixtures.references.add(handle.reference());
            fixtures.setDirty();
        } else {
            handle = runtime.load(fixtures.references.getFirst()).orElseThrow(() ->
                    new IllegalStateException("Saved projection fixture is unavailable"));
        }
        reference = handle.reference();
    }

    private static void prepare(ServerPlayer player, ServerLevel level) {
        hostBase = findEmptyHostArea(player.level(), player.blockPosition());
        expectedPose = StructurePose.identity(Vec3.atLowerCornerOf(hostBase.subtract(SUPPORT)));
        handle.setPose(expectedPose);
        put(SUPPORT, new StructureBlockData(Blocks.STONE.defaultBlockState()));
        put(LAMP, new StructureBlockData(Blocks.REDSTONE_LAMP.defaultBlockState()));
        var furnaceState = Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH);
        var furnace = new FurnaceBlockEntity(FURNACE, furnaceState);
        furnace.setItem(0, new ItemStack(Items.IRON_ORE, 3));
        furnace.setItem(1, new ItemStack(Items.COAL, 1));
        put(FURNACE, new StructureBlockData(furnaceState, furnace.saveWithFullMetadata(level.registryAccess())));
        put(STAIRS, new StructureBlockData(Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH)));
        put(LEVER, new StructureBlockData(Blocks.LEVER.defaultBlockState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
                .setValue(LeverBlock.FACING, Direction.NORTH).setValue(LeverBlock.POWERED, false)));
        var eyeTarget = expectedPose.localToWorldPoint(Vec3.atLowerCornerOf(LEVER).add(0.5, 0.2, 0.5));
        positionPlayer(player, Vec3.atLowerCornerOf(hostBase).add(0.5, 0, 3.5), eyeTarget);
        prepared = true;
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PROJECTION_SERVER_PREPARED ref={} cells={} host={}",
                reference, CELLS.size(), hostBase);
    }

    private static void rotateForScreenshot(ServerPlayer player) {
        var localPivot = Vec3.atCenterOf(SUPPORT).add(0.5, 0.35, 0);
        var worldPivot = handle.pose().localToWorldPoint(localPivot);
        var sine = Math.sin(0.55 / 2) / Math.sqrt(5.36);
        var rotation = new StructurePose(Vec3.ZERO, sine, 2 * sine, 0.6 * sine, Math.cos(0.55 / 2));
        expectedPose = new StructurePose(worldPivot.subtract(rotation.localToWorldPoint(localPivot)),
                rotation.rotationX(), rotation.rotationY(), rotation.rotationZ(), rotation.rotationW());
        handle.setPose(expectedPose);
        positionPlayer(player, worldPivot.add(0, 0.4 - player.getEyeHeight(), 5.5), worldPivot);
        visualReady = true;
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PROJECTION_SERVER_ROTATED ref={} stale_replay_observed_ticks=20 pose={}",
                reference, expectedPose);
    }

    private static void advanceProtectionChecks(ServerPlayer player, ServerLevel level) {
        if (serverTicks < nextProtectionTick || !runtime.isPresented(reference)) return;
        if (protectionCase == 3) {
            protectionsPassed = true;
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PROJECTION_PROTECTIONS_PASSED ref={} may_build=false "
                    + "event_cancel=true listener_pose_stale=true callback_unload_save_cas_pose_rejected=true", reference);
            return;
        }
        var from = player.getEyePosition();
        var to = from.add(player.getLookAngle().scale(player.blockInteractionRange()));
        var hit = StructureQueries.raycast(player.level(), from, to).orElse(null);
        if (hit == null || !hit.block().structure().equals(reference) || !hit.block().localPosition().equals(LEVER)) return;
        require(!level.getBlockState(LEVER).getValue(LeverBlock.POWERED)
                        && !level.getBlockState(LAMP).getValue(RedstoneLampBlock.LIT),
                "Negative interaction checks must start with an unpowered lever and lamp");
        if (protectionCase == 0) {
            var mayBuild = player.getAbilities().mayBuild;
            try {
                player.getAbilities().mayBuild = false;
                require(NativeStructureInteraction.useLever(player, hit) == NativeStructureInteraction.Result.DENIED,
                        "A real player without mayBuild must be denied before any block effect");
            } finally {
                player.getAbilities().mayBuild = mayBuild;
            }
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PROJECTION_MAY_BUILD_DENIED ref={}", reference);
        } else if (protectionCase == 1) {
            var callbacks = new int[1];
            Consumer<StructureInteractionEvent> cancel = event -> {
                if (event.actor() == player && event.hit().block().structure().equals(reference)) {
                    callbacks[0]++;
                    require(event.context().reference().equals(reference)
                                    && StructureContexts.current().orElseThrow() == event.context(),
                            "Protection listener must receive the actual active structure scope");
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(cancel);
            try {
                require(NativeStructureInteraction.useLever(player, hit) == NativeStructureInteraction.Result.DENIED
                                && callbacks[0] == 1,
                        "The real protection event must fire once and cancel the interaction");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PROJECTION_EVENT_CANCEL_DENIED ref={} callbacks=1", reference);
        } else {
            verifyListenerMutationAndGuard(player, hit);
        }
        require(!level.getBlockState(LEVER).getValue(LeverBlock.POWERED)
                        && !level.getBlockState(LAMP).getValue(RedstoneLampBlock.LIT)
                        && handle.lifecycle() == StructureLifecycle.ACTIVE
                        && runtime.resolve(reference).orElseThrow() == handle,
                "Rejected actions must preserve the lever, lamp and active runtime ownership");
        require(StructureContexts.current().isEmpty(), "Protection callbacks must restore the previous empty scope");
        protectionCase++;
        // The real per-player limiter remains enabled; no reflection or test reset is used.
        nextProtectionTick = serverTicks + 6;
    }

    private static void verifyListenerMutationAndGuard(ServerPlayer player, StructureHit hit) {
        var originalPose = handle.pose();
        var changedPose = new StructurePose(originalPose.worldOrigin().add(0.25, 0, 0),
                originalPose.rotationX(), originalPose.rotationY(), originalPose.rotationZ(), originalPose.rotationW());
        var originalSupport = handle.world().read(SUPPORT);
        var callbacks = new int[1];
        Consumer<StructureInteractionEvent> mutate = event -> {
            if (event.actor() != player || !event.hit().block().structure().equals(reference)) return;
            callbacks[0]++;
            require(runtime.runInteraction(reference, () -> {
                require(!runtime.unload(reference) && !runtime.save(reference),
                        "An active native callback must reject unload and save reentry");
                require(!runtime.runInteraction(reference, () -> {
                    throw new AssertionError("Nested interaction must not execute");
                }), "An active native callback must reject a nested interaction");
                require(handle.world().compareAndSet(SUPPORT, originalSupport,
                                new StructureBlockData(Blocks.DIAMOND_BLOCK.defaultBlockState()))
                                == StructureWriteResult.NOT_READY,
                        "An active native callback must reject a competing CAS");
                var rejectedPose = false;
                try {
                    handle.setPose(changedPose);
                } catch (IllegalStateException expected) {
                    rejectedPose = true;
                }
                require(rejectedPose && handle.pose().equals(originalPose)
                                && handle.world().read(SUPPORT).equals(originalSupport),
                        "A rejected callback pose/content write must leave the original values intact");
            }), "The outer no-op callback must complete normally after rejecting reentry");
            // Before the block callback starts, protection listeners may legitimately change pose.
            // The adapter must revalidate the original hit and refuse to act on the new version.
            handle.setPose(changedPose);
        };
        NeoForge.EVENT_BUS.addListener(mutate);
        try {
            require(NativeStructureInteraction.useLever(player, hit) == NativeStructureInteraction.Result.STALE
                            && callbacks[0] == 1 && handle.pose().equals(changedPose),
                    "A pose-changing protection listener must make the original hit stale");
        } finally {
            NeoForge.EVENT_BUS.unregister(mutate);
            if (handle.lifecycle() == StructureLifecycle.ACTIVE) handle.setPose(originalPose);
        }
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PROJECTION_LISTENER_MUTATION_REJECTED ref={} callbacks=1 "
                + "unload=false save=false cas=NOT_READY pose=denied nested=false outcome=STALE", reference);
    }

    private static void positionPlayer(ServerPlayer player, Vec3 feet, Vec3 eyeTarget) {
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        var direction = eyeTarget.subtract(feet.add(0, player.getEyeHeight(), 0));
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
        // The teleport acknowledgement must retain this view instead of restoring zero rotation.
        require(player.teleport(new TeleportTransition(player.level(), feet, Vec3.ZERO, yaw, pitch,
                        TeleportTransition.DO_NOTHING)) == player,
                "Host-only camera positioning was rejected");
    }

    private static void verifyUnload() {
        var level = storage();
        var before = furnace(level).saveWithFullMetadata(level.registryAccess());
        require(runtime.unload(reference), "Projection fixture save/unload failed");
        require(before.equals(furnace(level).saveWithFullMetadata(level.registryAccess())),
                "Removing the projection must preserve the full native furnace payload");
        require(runtime.resolve(reference).isEmpty(), "Unloaded fixture must not retain an active handle");
        assertFurnaceConservation(level);
        unloaded = true;
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PROJECTION_SERVER_UNLOADED ref={} native_inventory_preserved=true", reference);
    }

    private static NativeStructureProjectionEntity findProjection(Minecraft minecraft) {
        NativeStructureProjectionEntity result = null;
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof NativeStructureProjectionEntity projection
                    && reference.equals(projection.viewState().reference())) {
                require(result == null, "One structure must never have two client projection entities");
                result = projection;
            }
        }
        return result;
    }

    private static BlockPos findEmptyHostArea(ServerLevel level, BlockPos playerPosition) {
        for (var rise = 4; rise <= 28; rise += 4) {
            var candidate = playerPosition.above(rise);
            if (candidate.getY() + 5 > level.getMaxY()) continue;
            var empty = true;
            for (var position : BlockPos.betweenClosed(candidate.offset(-3, -1, -2), candidate.offset(4, 4, 7))) {
                if (!level.getBlockState(position).isAir() || level.getBlockEntity(position) != null) {
                    empty = false;
                    break;
                }
            }
            if (empty) return candidate;
        }
        throw new IllegalStateException("No existing empty host area is available for projection smoke");
    }

    private static void put(BlockPos position, StructureBlockData replacement) {
        require(handle.world().compareAndSet(position, handle.world().read(position), replacement)
                        == StructureWriteResult.APPLIED, "Projection fixture CAS failed at " + position);
    }

    private static FurnaceBlockEntity furnace(ServerLevel level) {
        require(level.getBlockEntity(FURNACE) instanceof FurnaceBlockEntity, "Native furnace is absent");
        return (FurnaceBlockEntity) level.getBlockEntity(FURNACE);
    }

    private static void assertFurnaceConservation(ServerLevel level) {
        var furnace = furnace(level);
        var input = furnace.getItem(0);
        var output = furnace.getItem(2);
        require((input.isEmpty() || input.is(Items.IRON_ORE)) && (output.isEmpty() || output.is(Items.IRON_INGOT))
                        && input.getCount() + output.getCount() == 3,
                "Projection and interaction must preserve three ore/iron units");
    }

    private static ServerPlayer player() {
        var player = owner.getPlayerList().getPlayer(playerId);
        require(player != null, "Integrated smoke player disappeared");
        return player;
    }

    private static ServerLevel storage() {
        return runtime.storageLevel(reference).orElseThrow();
    }

    private static void verifyWorld(Minecraft minecraft, MinecraftServer server) {
        try {
            var directory = minecraft.gameDirectory.toPath().toRealPath();
            var saves = directory.resolve("saves").toRealPath();
            var world = server.getWorldPath(LevelResource.ROOT).toRealPath();
            require(directory.getFileName().toString().startsWith("native-structure-projection-smoke"),
                    "Use a dedicated native-structure-projection-smoke directory");
            require(world.startsWith(saves) && world.getFileName().toString().equals(WORLD_NAME),
                    "Projection smoke may only access its explicitly named isolated world");
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot verify isolated projection smoke world", failure);
        }
    }

    private static void guardedServer(Runnable action) {
        if (failure != null) return;
        try { action.run(); }
        catch (Throwable error) { fail(error); }
    }

    private static void fail(Throwable error) {
        failure = error.toString();
        AcademyCraft.LOGGER.error("NATIVE_STRUCTURE_PROJECTION_SMOKE_FAILED", error);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static boolean samePose(StructurePose actual, StructurePose expected) {
        if (expected == null || actual.worldOrigin().distanceToSqr(expected.worldOrigin()) > 1.0E-16) return false;
        var dot = actual.rotationX() * expected.rotationX() + actual.rotationY() * expected.rotationY()
                + actual.rotationZ() * expected.rotationZ() + actual.rotationW() * expected.rotationW();
        return Math.abs(dot) >= 1.0 - 1.0E-12;
    }

    private static final class FixtureIndex extends SavedData {
        private static final Codec<StructureRef> REF_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(StructureRef::id),
                Identifier.CODEC.fieldOf("dimension").forGetter(StructureRef::dimension),
                Identifier.CODEC.fieldOf("backend").forGetter(StructureRef::backend),
                Codec.LONG.fieldOf("generation").forGetter(StructureRef::generation)
        ).apply(instance, StructureRef::new));
        private static final Codec<FixtureIndex> CODEC = REF_CODEC.listOf()
                .xmap(FixtureIndex::new, index -> List.copyOf(index.references));
        private static final SavedDataType<FixtureIndex> TYPE = new SavedDataType<>(
                AcademyCraft.academy("native_structure_projection_smoke"), FixtureIndex::new, CODEC);
        private final List<StructureRef> references;

        private FixtureIndex() { this(List.of()); }
        private FixtureIndex(List<StructureRef> references) { this.references = new ArrayList<>(references); }
    }
}
