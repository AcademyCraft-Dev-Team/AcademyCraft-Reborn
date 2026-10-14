package org.academy.internal.common.structure.sable.validation;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.ConfigSync;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.academy.AcademyCraft;
import org.academy.api.common.data.AbilityData;
import org.academy.api.common.ability.event.SkillExecutionFinishEvent;
import org.academy.api.common.ability.aeromanip.PneumaticStructureControl;
import org.academy.api.common.ability.accelerator.KineticStructureControl;
import org.academy.api.common.structure.*;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv3.KineticThrow;
import org.academy.internal.common.ability.aeromanip.skills.lv2.PneumaticGrasp;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.structure.sable.SableContainerGuard;
import org.academy.internal.common.structure.sable.SableStructureAccess;
import org.academy.internal.common.structure.sable.SableStaffCompatibility;
import org.academy.internal.common.structure.transaction.StructureTransactionJournal;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Explicitly registered only by the verified optional bridge in an isolated opt-in run. */
public final class SableStructureSmoke {
    public static final String CLIENT_WORLD = "Academy Sable Smoke";
    public record ClientState(String phase, UUID structure, Vec3 center, boolean complete, String failure) {}
    private static volatile ClientState clientState = new ClientState("WAITING", null, null, false, null);
    private static volatile String screenshot;
    private static volatile int clientMotionSamples;
    private static SableStructureSmoke active;
    private static boolean initialized;
    private enum Phase { SKILL, COAST, CAPTURE, HEAVY_THROW, NPC, ACTOR_REMOVED, CALLBACK_WAIT, CALLBACK_COAST, CALLBACK_REACQUIRE, FINISH }
    private static final BlockPos BASE = new BlockPos(8, 200, 8);
    private static final Vec3 CELL_CENTER = new Vec3(0.5, 0.5, 0.5);
    private static final StructureTraction.Settings SETTINGS = new StructureTraction.Settings(32, 32, 120, 36, 1000, 3);
    private final MinecraftServer server;
    private final ServerLevel host;
    private final ServerSubLevelContainer container;
    private final String side;
    private final Path directory;
    private final StringBuilder evidence = new StringBuilder();
    private final List<ChunkPos> tickets = new ArrayList<>();
    private final List<ServerSubLevel> fixtures = new ArrayList<>();
    private ServerSubLevel body;
    private StructureHandle handle;
    private StructureTractionSession session;
    private StructureTraction.Settings actorSettings = SETTINGS;
    private ArmorStand actor;
    private ServerPlayer player;
    private Connection connection;
    private EmbeddedChannel channel;
    private Object staff;
    private UUID staffActor;
    private Vec3 fixtureEye, initialCenter, releaseVelocity, releasePosition, preVelocity;
    private final List<BlockPos> captureCells = new ArrayList<>();
    private final List<BlockState> captureStates = new ArrayList<>();
    private UUID captureTransaction;
    private StructureRef captureRef;
    private boolean captureStarted;
    private Phase phase = Phase.SKILL;
    private int ticks, age, impulseSubsteps, closedSubsteps;
    private int callbackStops, callbackClosedSubsteps;
    private double observedImpulseDelta;
    private float initialAir;
    private boolean complete, orderlyStop, cleaned;
    private boolean guardObserved;
    private String failure;
    private volatile String checkpointEvidence = "fixture_initialized=false\n";

    private SableStructureSmoke(MinecraftServer server) {
        this.server = server;
        host = server.overworld();
        side = System.getProperty("academy.sable.smoke.side", "server");
        directory = verifyDirectory(server, side);
        container = SubLevelContainer.getContainer(host);
        require(container != null, "Sable did not supply its real host container");
        require(StructureSpaces.status(server).mode() == StructureBackendStatus.Mode.SABLE,
                "Mixed smoke requires the compatible Sable backend: " + StructureSpaces.status(server));
        // A crash in an earlier global event listener can prevent our next tick and
        // ServerStoppingEvent. Keep an explicit failed result without inventing cleanup.
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("academy-sable-smoke-fallback").unstarted(this::writeMissingFailure));
    }

    public static synchronized void initialize() {
        if (initialized) return;
        require(Boolean.getBoolean("academy.sable.smoke"), "Sable smoke requires explicit opt-in");
        NeoForge.EVENT_BUS.register(SableStructureSmoke.class);
        if ("client".equals(System.getProperty("academy.sable.smoke.side"))) {
            // No client linkage is resolved by the dedicated server or ordinary optional bridge.
            try { Class.forName("org.academy.internal.common.structure.sable.validation.SableStructureClientSmoke")
                    .getMethod("initialize").invoke(null); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot register Sable client probe", error); }
        }
        initialized = true;
    }

    public static ClientState clientState() { return clientState; }
    public static void captured(Path path, int samples) { screenshot = path.toAbsolutePath().toString(); clientMotionSamples = samples; }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        if (active == null) {
            if ("client".equals(System.getProperty("academy.sable.smoke.side"))
                    && event.getServer().getPlayerList().getPlayers().isEmpty()) return;
            // Registration can occur during ServerStartedEvent; begin on the following tick.
            active = new SableStructureSmoke(event.getServer());
            active.guarded(active::prepare);
        } else if (active.server == event.getServer() && !active.complete && active.failure == null) {
            active.guarded(active::advance);
        }
    }

    private void prepare() {
        evidence.append("actions=server_fixture\nactual_keyboard_actions=false\nembedded_player_is_real_tcp=false\n")
                .append("runtime_group=").append(System.getProperty("academy.sableCompat.runtime")).append('\n');
        noNativeRuntime();
        // The full 32-block validation ray must stay in explicitly prepared host chunks.
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (host.setChunkForced(x, z, true)) tickets.add(new ChunkPos(x, z));
            host.getChunk(x, z);
        }
        for (var position : BlockPos.betweenClosed(BASE.offset(-5, -32, -5), BASE.offset(5, 8, 12))) {
            require(host.getBlockState(position).isAir() && host.getBlockEntity(position) == null,
                    "Smoke refuses existing host content: " + position);
        }
        queriesAndLifecycle();
        var physicalCells = new ArrayList<BlockPos>();
        int width = Boolean.getBoolean("academy.sable.smoke.compound") ? 5 : 1;
        for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) {
            var cell = BASE.offset(x, 0, z);
            require(host.getBlockState(cell).isAir(), "Compound fixture source is occupied");
            host.setBlockAndUpdate(cell, Blocks.STONE.defaultBlockState());
            physicalCells.add(cell);
        }
        body = assemble(physicalCells);
        handle = resolve(body);
        require(handle.world().read(BlockPos.ZERO).state().is(Blocks.STONE), "Physical fixture lost its real cell");
        double mass = body.getMassTracker().getMass();
        require(Double.isFinite(mass) && mass > 0 && mass <= 128, "Unexpected Sable test mass: " + mass);
        var observedMotion = StructureDynamics.motion(host, handle.reference()).orElseThrow();
        require(Math.abs(observedMotion.mass() - mass) < 1.0E-9, "Whole-body observation lost real solver mass");
        if (width > 1) {
            require(mass > 32, "Compound scenario did not exceed the old one-body admission limit");
            actorSettings = PneumaticStructureControl.settings(mass, 0, 0, 32);
            var near = rayToLocal(handle, CELL_CENTER);
            var far = rayToLocal(handle, CELL_CENTER.add(width - 1, 0, 0));
            require(near.block().structure().equals(far.block().structure())
                            && near.runtimeId().equals(far.runtimeId())
                            && !near.block().localPosition().equals(far.block().localPosition()),
                    "Different cells were not normalized to the same whole-body target");
            evidence.append("different_cells_share_reference_and_activation=true\n");
        }
        evidence.append("real_sable_mass=").append(mass).append("\nphysical_cells=").append(physicalCells.size())
                .append("\nwhole_body_motion_mass=true\nnpc_settings=").append(actorSettings).append('\n');
        initialCenter = center(); fixtureEye = initialCenter.add(0, 2, 6);
        if (side.equals("client")) {
            var camera = server.getPlayerList().getPlayers().getFirst();
            camera.setGameMode(GameType.SPECTATOR);
            aim(camera, initialCenter.add(8, 3, 10), initialCenter);
        }
        createSkillPlayer();
        aim(player, fixtureEye, center());
        var programResolver = new org.academy.internal.common.ability.program.ServerProgramTargetResolver(player, 32, 32);
        var programTarget = programResolver.lookStructureTarget().orElseThrow();
        require(programTarget.matches(handle) && Math.abs(programResolver.structureMotion(programTarget).orElseThrow().mass() - mass) < 1.0E-9,
                "Precision program did not resolve the whole physical body");
        require(programResolver.structureMotion(new org.academy.api.common.ability.program.ProgramStructureReference(
                programTarget.reference(), UUID.randomUUID())).isEmpty(), "Stale program activation read a live body's motion");
        evidence.append("program_whole_body_query=true\nprogram_stale_activation_rejected=true\n");
        verifyStaffCooperation();
        initialAir = AbilitySystemServer.getSystem(player).getPlayerCurrMP(player.getUUID());
        require(PneumaticGrasp.Server.start(player) && !PneumaticGrasp.Server.start(player), "Real skill START rejected or duplicated");
        publish();
    }

    private void queriesAndLifecycle() {
        host.setBlockAndUpdate(BASE, Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) host.getBlockEntity(BASE);
        require(chest != null, "Host chest BE missing");
        chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
        host.setBlockAndUpdate(BASE.east(), Blocks.STONE.defaultBlockState());
        host.setBlockAndUpdate(BASE.north(), Blocks.OAK_SLAB.defaultBlockState());
        var compound = assemble(List.of(BASE, BASE.east(), BASE.north()));
        var old = resolve(compound);
        var data = old.world().read(BlockPos.ZERO);
        require(data.state().is(Blocks.CHEST) && data.blockEntityData() != null, "Bridge did not expose real chest NBT");
        var plotChest = (ChestBlockEntity) host.getBlockEntity(compound.getPlot().getCenterBlock());
        require(plotChest != null && plotChest.getItem(0).is(Items.DIAMOND) && plotChest.getItem(0).getCount() == 7,
                "Public assembly lost chest contents");
        var tag = data.blockEntityData();
        require(tag.getIntOr("x", -1) == 0 && tag.getIntOr("y", -1) == 0 && tag.getIntOr("z", -1) == 0,
                "Read exposed private storage coordinates");
        tag.putInt("x", 777);
        require(old.world().read(BlockPos.ZERO).blockEntityData().getIntOr("x", -1) == 0,
                "Detached BE data aliases live state");
        require(old.world().compareAndSet(BlockPos.ZERO, data, new StructureBlockData(Blocks.AIR.defaultBlockState()))
                == StructureWriteResult.REJECTED, "Read-only bridge accepted an unsupported write");
        var localTarget = new Vec3(1.5, 0.5, 0.5);
        var first = rayToLocal(old, localTarget);
        require(first.block().localPosition().equals(BlockPos.ZERO.east()), "Initial ray missed stone local cell");
        var physics = container.physicsSystem();
        physics.getPipeline().teleport(compound, new Vector3d(compound.logicalPose().position()), new Quaterniond().rotationY(Math.PI / 2));
        physics.updatePose(compound); compound.updateLastPose();
        var second = rayToLocal(old, localTarget);
        require(second.block().localPosition().equals(BlockPos.ZERO.east()), "90-degree ray changed local cell");
        var expected = compound.logicalPose().transformPosition(Vec3.atLowerCornerOf(compound.getPlot().getCenterBlock()).add(second.localPoint()));
        require(second.worldPoint().distanceTo(expected) < 1.0E-7, "COM or plot origin was applied twice");
        var from = old.pose().localToWorldPoint(localTarget.add(0, 0, 5));
        var to = old.pose().localToWorldPoint(localTarget.add(0, 0, -1));
        require(StructureQueries.clearSightline(host, from, from.lerp(second.worldPoint(), 0.5))
                        && !StructureQueries.clearSightline(host, from, to),
                "Clear sightline confused empty foreground with a farther structure");
        compound.logicalPose().scale().set(1.1, 1.1, 1.1);
        try {
            require(StructureQueries.raycast(host, from, to).isEmpty() && !StructureQueries.clearSightline(host, from, to),
                    "Unsupported scaled foreground became an unobstructed ray");
        } finally { compound.logicalPose().scale().set(1, 1, 1); }
        require(StructureQueries.validate(host, from, to, first).status() == StructureHitValidation.Status.STALE,
                "Rotated hit remained valid");
        var obstruction = BlockPos.containing(from.lerp(second.worldPoint(), 0.5));
        require(host.getBlockState(obstruction).isAir(), "Host obstruction fixture is occupied");
        try {
            host.setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
            require(StructureQueries.raycast(host, from, to).isEmpty(), "Host solid failed to obscure Sable ray");
            require(!StructureQueries.clearSightline(host, from, to), "Host solid was reported as clear");
        } finally { host.setBlockAndUpdate(obstruction, Blocks.AIR.defaultBlockState()); }
        var uuid = compound.getUniqueId();
        var origin = container.getOrigin();
        int plotX = compound.getPlot().plotPos.x() - origin.x();
        int plotZ = compound.getPlot().plotPos.z() - origin.y();
        var pose = new Pose3d(compound.logicalPose());
        container.removeSubLevel(compound, SubLevelRemovalReason.UNLOADED);
        require(old.lifecycle() == StructureLifecycle.UNLOADED && StructureSpaces.resolve(server, old.reference()).isEmpty(),
                "Unloaded activation is still current");
        var replacement = (ServerSubLevel) container.allocateSubLevel(uuid, plotX, plotZ, pose);
        fixtures.add(replacement);
        replacement.getPlot().newEmptyChunk(replacement.getPlot().getCenterChunk());
        replacement.getPlot().getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        var current = resolve(replacement);
        require(current.reference().equals(old.reference()) && !current.runtimeId().equals(old.runtimeId()),
                "Same-tick UUID/plot reuse revived the old runtime handle");
        require(old.lifecycle() == StructureLifecycle.UNLOADED
                        && current.world().read(BlockPos.ZERO).state().is(Blocks.DIAMOND_BLOCK), "ABA replacement has wrong content");
        boolean rejected = false;
        try { old.world().read(BlockPos.ZERO); } catch (IllegalStateException expectedFailure) { rejected = true; }
        require(rejected, "Old world access revived after same-tick reload");
        remove(replacement);
        require(StructureSpaces.resolve(server, current.reference()).isEmpty(), "Removed replacement still resolves");
        evidence.append("public_assembly_real_cells=true\nreal_chest_inventory_and_detached_local_nbt=true\n")
                .append("read_only_bridge_write_rejected=true\nworld_plot_rotation90_raycast=true\n")
                .append("host_occlusion=true\npose_change_invalidates_hit=true\nsame_tick_uuid_plot_aba=true\n");
        evidence.append("empty_foreground_sightline_clear=true\nstructure_and_host_block_sightline=true\nunsupported_scale_blocks_sightline=true\n");
    }

    private StructureHit rayToLocal(StructureHandle target, Vec3 local) {
        return StructureQueries.raycast(host, target.pose().localToWorldPoint(local.add(0, 0, 5)),
                target.pose().localToWorldPoint(local.add(0, 0, -1))).orElseThrow(() -> new IllegalStateException("Real transformed ray missed"));
    }

    private void startCapture() {
        captureStarted = true;
        // A heavy real fixture (>128 mass) proves the H-press throw bypasses the traction mass gate.
        // It sits outside the default spawn protection radius so the permission check stays real.
        var origin = BASE.offset(0, 0, 7);
        var cells = new ArrayList<BlockPos>();
        for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++) for (int y = 0; y < 4; y++) {
            cells.add(origin.offset(x, y, z));
        }
        for (var cell : cells) {
            require(host.getBlockState(cell).isAir() && host.getBlockEntity(cell) == null,
                    "Sable capture source must start empty: " + cell);
            host.setBlockAndUpdate(cell, Blocks.STONE.defaultBlockState());
            require(host.getBlockState(cell).is(Blocks.STONE), "Sable capture source write failed: " + cell);
            captureCells.add(cell);
            captureStates.add(host.getBlockState(cell));
        }
        var result = StructureTransactions.capture(host, StructureCaptureRequest.of(captureCells));
        require(result.pending() || result.succeeded(),
                "Sable capture did not start: " + result.status() + "/" + result.detail());
        captureTransaction = result.transactionId();
        if (result.succeeded()) {
            captureRef = result.structure().orElseThrow(() ->
                    new IllegalStateException("Committed Sable capture must expose its target"));
        }
    }

    private void verifyCaptureCommit() {
        var handle = StructureSpaces.resolve(server, captureRef)
                .orElseThrow(() -> new IllegalStateException("Committed Sable capture must stay resolvable"));
        require(handle.lifecycle() == StructureLifecycle.ACTIVE, "Committed Sable capture must stay active");
        var origin = captureOrigin();
        for (int index = 0; index < captureCells.size(); index++) {
            var cell = captureCells.get(index);
            require(host.getBlockState(cell).isAir(), "Sable capture retained its source: " + cell);
            require(handle.world().read(cell.subtract(origin)).state().equals(captureStates.get(index)),
                    "Sable capture target lost the captured state: " + cell);
        }
        require(!captureJournalExists(captureTransaction), "Committed Sable capture did not recycle its journal");
        var area = new AABB(Vec3.atLowerCornerOf(origin), Vec3.atLowerCornerOf(origin.offset(6, 4, 6)));
        require(host.getEntitiesOfClass(ItemEntity.class, area).isEmpty(), "Sable capture left item drops");
        var captured = container.getSubLevel(captureRef.id());
        require(captured instanceof ServerSubLevel, "Committed Sable capture lost its ship");
        if (captured instanceof ServerSubLevel ship) fixtures.add(ship);
        evidence.append("sable_capture_committed=true\n");
    }

    private void verifyCaptureRejection() {
        var cell = BASE.offset(-2, 0, -4);
        require(host.getBlockState(cell).isAir() && host.getBlockEntity(cell) == null,
                "Sable rejection fixture must start empty: " + cell);
        host.setBlockAndUpdate(cell, Blocks.CHEST.defaultBlockState());
        require(host.getBlockEntity(cell) instanceof ChestBlockEntity,
                "Sable rejection fixture lost its block entity");
        var result = StructureTransactions.capture(host, StructureCaptureRequest.of(List.of(cell)));
        require(result.status() == StructureCaptureResult.Status.REJECTED
                        && result.detail().equals("block_entity_not_supported"),
                "Sable block-entity capture must be rejected: " + result.status() + "/" + result.detail());
        require(host.getBlockState(cell).is(Blocks.CHEST) && host.getBlockEntity(cell) != null,
                "Rejected Sable capture must preserve its source");
        require(!captureJournalExists(result.transactionId()), "Rejected Sable capture must not journal");
        host.removeBlockEntity(cell);
        host.setBlockAndUpdate(cell, Blocks.AIR.defaultBlockState());
        evidence.append("sable_capture_rejected_be_preserved=true\n");
    }

    private BlockPos captureOrigin() {
        int x = Integer.MAX_VALUE, y = Integer.MAX_VALUE, z = Integer.MAX_VALUE;
        for (var cell : captureCells) {
            x = Math.min(x, cell.getX());
            y = Math.min(y, cell.getY());
            z = Math.min(z, cell.getZ());
        }
        return new BlockPos(x, y, z);
    }

    private boolean captureJournalExists(UUID transactionId) {
        return Files.exists(server.getWorldPath(LevelResource.ROOT)
                .resolve("data/academy/" + StructureTransactionJournal.DIRECTORY_NAME)
                .resolve(transactionId + ".dat"));
    }

    private void advance() {
        ticks++; age++;
        require(ticks < 600, "Sable smoke timed out at " + phase);
        noNativeRuntime();
        if (!guardObserved) {
            require(SableContainerGuard.enteredCallbacks() > 0,
                    "Sable container tick never entered the guarded callback; the compatibility mixin is inactive");
            guardObserved = true;
            evidence.append("sable_container_tick_guard_active=true\n");
        }
        require(body != null && !body.isRemoved(), "Physical fixture disappeared");
        require(center().y > BASE.getY() - 25, "Physical fixture left its verified empty flight volume");
        switch (phase) {
            case SKILL -> {
                var diagnostic = PneumaticGrasp.Server.diagnostic(player).orElseThrow(() -> new IllegalStateException("Sable skill context ended"));
                require(diagnostic.nativeControlActive() && handle.reference().equals(diagnostic.nativeRef()),
                        "PneumaticGrasp failed to select Sable through world-space query: " + diagnostic);
                if (diagnostic.controlActiveTicks() < 20 || side.equals("client") && screenshot == null) break;
                require(center().distanceTo(initialCenter) > 0.1 && impulseSubsteps > 0 && observedImpulseDelta > 0.01,
                        "Real skill did not apply observable impulses in Sable prePhysics");
                require(AbilitySystemServer.getSystem(player).getPlayerCurrMP(player.getUUID()) < initialAir,
                        "Real skill did not charge compressed air");
                evidence.append("pneumatic_real_server_entry_selects_sable=true\npneumatic_real_air_charged=true\n")
                        .append("pneumatic_duplicate_start_rejected=true\nprephysics_impulse_substeps=").append(impulseSubsteps)
                        .append("\nprephysics_observed_velocity_delta=").append(observedImpulseDelta).append('\n');
                releaseVelocity = velocity(); releasePosition = center();
                var angular = angularVelocity();
                PneumaticGrasp.Server.stop(player); PneumaticGrasp.Server.stop(player);
                require(PneumaticGrasp.Server.diagnostic(player).isEmpty(), "STOP retained a skill context");
                require(velocity().equals(releaseVelocity) && angularVelocity().equals(angular), "Release overwrote real velocity");
                closePlayer();
                next(Phase.COAST);
            }
            case COAST -> {
                require(velocity().y < releaseVelocity.y - 0.01 && center().distanceTo(releasePosition) > 1.0E-5,
                        "Released Sable body did not retain motion and continue gravity");
                require(closedSubsteps > 0, "Released body had no real physics substeps");
                evidence.append("release_preserves_linear_angular_velocity=true\nrelease_continues_gravity=true\n")
                        .append("release_prephysics_has_no_traction_delta=true\n");
                actor = EntityTypes.ARMOR_STAND.create(host, EntitySpawnReason.COMMAND);
                require(actor != null, "Cannot create ordinary traction actor");
                actor.setNoGravity(true); aim(actor, fixtureEye, center());
                require(host.addFreshEntity(actor), "Cannot register ordinary traction actor");
                var hit = actorHit();
                session = StructureTraction.tryAcquire(actor, UUID.randomUUID(), hit, actorSettings, (_, _) -> true).session().orElseThrow();
                require(StructureTraction.tryAcquire(actor, UUID.randomUUID(), hit, actorSettings, (_, _) -> true).status()
                        == StructureTraction.Status.OCCUPIED, "NPC ownership was not exclusive");
                initialCenter = center();
                require(session.update(session.worldPoint().add(0, 1, 1), Vec3.ZERO) == StructureTraction.Status.ACTIVE,
                        "NPC could not update the public Sable session");
                next(Boolean.getBoolean("academy.sable.smoke.capture") ? Phase.CAPTURE : Phase.NPC);
            }
            case CAPTURE -> {
                // The NPC traction lease is short; keep it alive while capture spans ticks.
                require(session.update(session.targetPosition(), Vec3.ZERO) == StructureTraction.Status.ACTIVE,
                        "NPC traction ended during Sable capture");
                if (!captureStarted) {
                    startCapture();
                    break;
                }
                require(age < 200, "Sable capture transaction did not settle in time");
                var result = StructureTransactions.poll(server, captureTransaction)
                        .orElseThrow(() -> new IllegalStateException("Sable capture result vanished: " + captureTransaction));
                if (result.status() == StructureCaptureResult.Status.PENDING) break;
                require(result.succeeded(), "Sable capture failed: " + result.status() + "/" + result.detail());
                captureRef = result.structure().orElseThrow(() ->
                        new IllegalStateException("Committed Sable capture must expose its target"));
                verifyCaptureCommit();
                verifyCaptureRejection();
                next(Phase.HEAVY_THROW);
            }
            case HEAVY_THROW -> {
                require(captureRef != null, "Sable heavy throw requires the committed capture");
                var ship = container.getSubLevel(captureRef.id());
                require(ship instanceof ServerSubLevel, "Sable heavy capture lost its ship");
                var mass = ((ServerSubLevel) ship).getMassTracker().getMass();
                require(mass > KineticStructureControl.massBudget(0),
                        "Sable heavy fixture mass " + mass + " does not exceed the tier-0 traction budget");
                createKineticPlayer();
                var origin = captureOrigin();
                var shipCenter = new Vec3(origin.getX() + 3.0, origin.getY() + 2.0, origin.getZ() + 3.0);
                aim(player, shipCenter.add(0, 2, 6), shipCenter);
                var hit = StructureQueries.raycast(host, player.getEyePosition(),
                        player.getEyePosition().add(player.getLookAngle().scale(32)))
                        .orElseThrow(() -> new IllegalStateException(
                                "Sable heavy throw aim does not hit the captured ship"));
                require(hit.block().structure().equals(captureRef), "Sable heavy throw aimed at a foreign structure");
                var budgetSettings = KineticStructureControl.settings(mass, 0, 0, 32);
                require(StructureTraction.tryAcquire(player, UUID.randomUUID(), hit, budgetSettings, (_, _) -> true)
                                .status() == StructureTraction.Status.TOO_HEAVY,
                        "Sable heavy fixture was not rejected by the traction mass gate");
                require(KineticThrow.Server.pressHeld(player), "Sable H-press heavy throw was rejected");
                var launched = StructureDynamics.motion(host, captureRef).orElseThrow(() ->
                        new IllegalStateException("Sable heavy throw lost its motion"));
                var expected = player.getLookAngle().normalize().scale(KineticThrow.throwSpeed(false));
                require(launched.linearVelocity().distanceTo(expected) < 0.5,
                        "Sable heavy throw speed is not the calibrated value: " + launched.linearVelocity());
                evidence.append("sable_hpressed_heavy_throw=true\nsable_heavy_mass=").append(mass)
                        .append("\nsable_traction_too_heavy_bypassed=true\nsable_h_throw_speed=")
                        .append(launched.linearVelocity().length()).append('\n');
                KineticThrow.Server.stopHeld(player);
                closePlayer();
                remove((ServerSubLevel) ship);
                next(Phase.NPC);
            }
            case NPC -> {
                require(session.status() == StructureTraction.Status.ACTIVE, "NPC traction ended unexpectedly: " + session.status());
                if (age < 6) {
                    require(session.update(session.targetPosition(), Vec3.ZERO) == StructureTraction.Status.ACTIVE, "NPC renewal failed");
                    break;
                }
                require(center().distanceTo(initialCenter) > 0.01, "NPC session did not move the real Sable body");
                var old = session;
                actor.discard();
                require(old.status() == StructureTraction.Status.NOT_AVAILABLE
                        && old.update(old.targetPosition(), Vec3.ZERO) == StructureTraction.Status.NOT_AVAILABLE,
                        "Actor removal failed to terminate its Sable controller");
                actor = EntityTypes.ARMOR_STAND.create(host, EntitySpawnReason.COMMAND);
                require(actor != null, "Cannot create successor actor");
                actor.setNoGravity(true); aim(actor, fixtureEye, center()); require(host.addFreshEntity(actor), "Cannot register successor");
                session = StructureTraction.tryAcquire(actor, UUID.randomUUID(), actorHit(), actorSettings, (_, _) -> true).session().orElseThrow();
                old.close();
                require(session.update(session.worldPoint(), Vec3.ZERO) == StructureTraction.Status.ACTIVE,
                        "Old session close removed a successor controller");
                session.close(); actor.discard();
                evidence.append("npc_shared_entry_real_motion=true\nexclusive_academy_control=true\n")
                        .append("actor_removal_invalidates_before_next_physics=true\nold_session_close_preserves_successor=true\n");
                next(Phase.ACTOR_REMOVED);
            }
            case ACTOR_REMOVED -> {
                createSkillPlayer(); aim(player, fixtureEye, center());
                require(PneumaticGrasp.Server.start(player), "Cannot start resource-callback regression");
                next(Phase.CALLBACK_WAIT);
            }
            case CALLBACK_WAIT -> {
                if (callbackStops == 0) {
                    require(age < 20 && PneumaticGrasp.Server.diagnostic(player).map(
                            PneumaticGrasp.Server.Diagnostic::nativeControlActive).orElse(false),
                            "Resource callback did not observe a live Sable control");
                    break;
                }
                require(callbackStops == 1 && callbackClosedSubsteps > 0
                                && PneumaticGrasp.Server.diagnostic(player).isEmpty(),
                        "Resource STOP callback left a context or future physical command");
                releaseVelocity = velocity(); releasePosition = center();
                next(Phase.CALLBACK_COAST);
            }
            case CALLBACK_COAST -> {
                require(PneumaticGrasp.Server.diagnostic(player).isEmpty() && callbackClosedSubsteps >= 2
                                && velocity().y < releaseVelocity.y - 0.01 && center().distanceTo(releasePosition) > 1.0E-5,
                        "Callback-stopped body retained control instead of continuing free motion");
                evidence.append("resource_callback_stop_count=").append(callbackStops)
                        .append("\nresource_callback_context_empty=true\nresource_callback_future_force_absent=true\n");
                aim(player, fixtureEye, center());
                require(PneumaticGrasp.Server.start(player), "Skill could not restart after resource callback STOP");
                next(Phase.CALLBACK_REACQUIRE);
            }
            case CALLBACK_REACQUIRE -> {
                require(PneumaticGrasp.Server.diagnostic(player).map(diagnostic -> diagnostic.nativeControlActive()
                                && handle.reference().equals(diagnostic.nativeRef())).orElse(false),
                        "Callback STOP retained the Sable control slot");
                closePlayer();
                evidence.append("resource_callback_skill_can_reacquire=true\n");
                next(Phase.FINISH);
            }
            case FINISH -> finish();
        }
        publish();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeControl(ForgeSablePrePhysicsTickEvent event) {
        var probe = active;
        if (probe != null && probe.body != null && !probe.complete && probe.failure == null
                && event.getPhysicsSystem() == probe.container.physicsSystem()) probe.preVelocity = probe.velocity();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterControl(ForgeSablePrePhysicsTickEvent event) {
        var probe = active;
        if (probe == null || probe.body == null || probe.complete || probe.failure != null
                || event.getPhysicsSystem() != probe.container.physicsSystem() || probe.preVelocity == null) return;
        probe.guarded(() -> {
            double delta = probe.velocity().distanceTo(probe.preVelocity);
            if (probe.phase == Phase.SKILL || probe.phase == Phase.NPC) {
                if (delta > 1.0E-7) { probe.impulseSubsteps++; probe.observedImpulseDelta += delta; }
            } else if (probe.phase == Phase.COAST || probe.callbackStops > 0
                    && (probe.phase == Phase.CALLBACK_WAIT || probe.phase == Phase.CALLBACK_COAST)) {
                require(delta < 1.0E-7, "Closed skill still changed velocity during prePhysics: " + delta);
                if (probe.phase == Phase.COAST) probe.closedSubsteps++;
                else probe.callbackClosedSubsteps++;
            }
            probe.preVelocity = null;
        });
    }

    /** Reacts to the actual tenth-tick skill payment; never posts a synthetic skill event. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stopFromResourceCallback(SkillExecutionFinishEvent event) {
        var probe = active;
        if (probe == null || probe.phase != Phase.CALLBACK_WAIT || probe.callbackStops != 0
                || probe.failure != null || probe.player == null || event.context().player() != probe.player
                || event.context().skill() != Skills.PNEUMATIC_GRASP.get() || !event.continuous()) return;
        probe.guarded(() -> {
            require(event.successful() && event.failure() == null, "Real resource action failed before the STOP callback");
            var diagnostic = PneumaticGrasp.Server.diagnostic(probe.player).orElseThrow();
            require(diagnostic.nativeControlActive() && diagnostic.controlActiveTicks() == 10,
                    "STOP callback did not run during the first real resource interval");
            PneumaticGrasp.Server.stop(probe.player);
            probe.callbackStops++;
            require(PneumaticGrasp.Server.diagnostic(probe.player).isEmpty(), "Resource callback STOP retained its context");
            probe.evidence.append("resource_finish_callback_observed=true\n");
            probe.publish();
        });
    }

    private ServerSubLevel assemble(List<BlockPos> cells) {
        var result = SubLevelAssemblyHelper.assembleBlocks(host, BASE, cells,
                new BoundingBox3i(BASE.getX() - 1, BASE.getY() - 1, BASE.getZ() - 2,
                        BASE.getX() + 3, BASE.getY() + 2, BASE.getZ() + 2));
        fixtures.add(result);
        for (var cell : cells) require(host.getBlockState(cell).isAir(), "Public assembly retained original host cell");
        return result;
    }

    private StructureHandle resolve(ServerSubLevel subLevel) {
        return StructureSpaces.resolve(server, new StructureRef(subLevel.getUniqueId(), host.dimension().identifier(),
                SableStructureAccess.ID, 0)).orElseThrow();
    }

    private Vec3 center() { return handle.pose().localToWorldPoint(CELL_CENTER); }
    private Vec3 velocity() { return vector(container.physicsSystem().getPhysicsHandle(body).getLinearVelocity(new Vector3d())); }
    private Vec3 angularVelocity() { return vector(container.physicsSystem().getPhysicsHandle(body).getAngularVelocity(new Vector3d())); }
    private static Vec3 vector(Vector3d v) { return new Vec3(v.x, v.y, v.z); }
    private StructureHit actorHit() { return StructureQueries.raycast(host, actor.getEyePosition(),
            actor.getEyePosition().add(actor.getLookAngle().scale(32))).orElseThrow(); }

    private void createSkillPlayer() {
        openEmbeddedPlayer();
        var system = AbilitySystemServer.getSystem(player); var id = player.getUUID();
        system.setPlayerAbilityCategory(id, AbilityCategories.AEROMANIP.get()); system.setPlayerLevel(id, 5);
        system.setPlayerStatus(id, AbilityData.Status.NORMAL);
        system.setPlayerBaseMaxCP(id, 100_000); system.setPlayerAvailableCP(id, 100_000);
        for (var skill : new org.academy.api.common.ability.Skill[]{Skills.FLOW_SENSE.get(), Skills.PNEUMATIC_GRASP.get()}) {
            system.addPlayerSkill(player, skill.getKeyString());
            if (!skill.isEnabled(player)) system.toggleSkill(id, skill.getKeyString());
        }
        system.getAeromanipResourceManager().onPlayerLogin(player);
        system.setPlayerCurrMP(id, system.getAeromanipResourceManager().getCapacity(player));
    }

    private void createKineticPlayer() {
        openEmbeddedPlayer();
        var system = AbilitySystemServer.getSystem(player); var id = player.getUUID();
        system.setPlayerAbilityCategory(id, AbilityCategories.ACCELERATOR.get()); system.setPlayerLevel(id, 5);
        system.setPlayerStatus(id, AbilityData.Status.NORMAL);
        system.setPlayerBaseMaxCP(id, 100_000); system.setPlayerAvailableCP(id, 100_000);
        for (var skill : new org.academy.api.common.ability.Skill[]{
                Skills.KINETIC_ENERGY_APPLIED.get(), Skills.KINETIC_THROW.get()}) {
            system.addPlayerSkill(player, skill.getKeyString());
        }
        if (Skills.KINETIC_ENERGY_APPLIED.get().isEnabled(player)) {
            system.toggleSkill(id, Skills.KINETIC_ENERGY_APPLIED.get().getKeyString());
        }
        if (!Skills.KINETIC_THROW.get().isEnabled(player)) {
            system.toggleSkill(id, Skills.KINETIC_THROW.get().getKeyString());
        }
        require(Skills.KINETIC_THROW.get().isEnabled(player),
                "Kinetic throw is not enabled for the Sable probe player");
    }

    private void openEmbeddedPlayer() {
        var profile = new GameProfile(UUID.randomUUID(), "sable-probe");
        var cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false, ConnectionType.NEOFORGE);
        player = new ServerPlayer(server, host, profile, cookie.clientInformation());
        connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
        var configuration = new ServerConfigurationPacketListenerImpl(server, connection, cookie);
        connection.setupInboundProtocol(ConfigurationProtocols.SERVERBOUND, configuration);
        connection.setupOutboundProtocol(ConfigurationProtocols.CLIENTBOUND);
        // NeoForge's public GameTest helper advertises the real registered payload set to
        // this embedded fixture. It does not emulate TCP, UDP authentication or a client.
        NetworkRegistry.configureMockConnection(connection);
        evidence.append("embedded_neoforge_payloads_configured=true\nembedded_udp_authentication_tested=false\n");
        // Advertising NeoForge config payload support requires the real configuration
        // phase to create its pending-sync state before this ordinary player enters PLAY.
        // The embedded channel receives the emitted configs; no client ACK is claimed.
        ConfigSync.syncAllConfigs(configuration);
        connection.setupOutboundProtocol(GameProtocols.CLIENTBOUND_TEMPLATE.bind(
                RegistryFriendlyByteBuf.decorator(server.registryAccess(), ConnectionType.NEOFORGE)));
        NetworkRegistry.onConfigurationFinished(configuration);
        evidence.append("embedded_server_configs_emitted=true\nembedded_client_config_ack_tested=false\n");
        server.getConnection().getConnections().add(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        require(connection.getPacketListener() == player.connection && player.connection.getConnectionType().isNeoForge(),
                "Embedded configuration did not transition to its real NeoForge PLAY listener");
        player.connection.markClientLoaded(); player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
    }

    private static void aim(Entity entity, Vec3 eye, Vec3 point) {
        var direction = point.subtract(eye).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
        entity.snapTo(eye.x, eye.y - entity.getEyeHeight(), eye.z, yaw, pitch);
        if (entity instanceof ServerPlayer player) {
            player.getAbilities().mayfly = true; player.getAbilities().flying = true; player.onUpdateAbilities();
            player.connection.teleport(entity.getX(), entity.getY(), entity.getZ(), yaw, pitch);
        }
    }

    private void closePlayer() {
        if (player == null) return;
        PneumaticGrasp.Server.stop(player);
        if (connection != null) server.getConnection().getConnections().remove(connection);
        if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player);
        if (channel != null) channel.finishAndReleaseAll();
        require(host.getEntity(player.getUUID()) != player && server.getPlayerList().getPlayer(player.getUUID()) != player
                && !channel.isOpen() && !server.getConnection().getConnections().contains(connection), "Embedded player cleanup failed");
        player = null;
    }

    /** Only calls original public Suite methods; no reflective writes or fake constraints. */
    private void verifyStaffCooperation() {
        if (!"suite".equals(System.getProperty("academy.sableCompat.runtime"))) {
            require(SableStaffCompatibility.status(host, body) == SableStaffCompatibility.Status.FREE,
                    "Absent Suite staff blocked standalone Sable");
            evidence.append("staff_suite_present=false\n");
            return;
        }
        try {
            var type = Class.forName("dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler");
            staff = type.getMethod("get", ServerLevel.class).invoke(null, host);
            staffActor = player.getUUID();
            var hit = StructureQueries.raycast(host, player.getEyePosition(), player.getEyePosition().add(player.getLookAngle().scale(32))).orElseThrow();
            var owner = UUID.randomUUID();
            callStaff("toggleLock", new Class<?>[]{UUID.class}, body.getUniqueId());
            require(SableStaffCompatibility.status(host, body) == SableStaffCompatibility.Status.OCCUPIED
                            && StructureTraction.tryAcquire(player, owner, hit, actorSettings, (_, _) -> true).status() == StructureTraction.Status.OCCUPIED,
                    "A real staff lock did not exclude Academy traction");
            callStaff("removeLock", new Class<?>[]{SubLevel.class}, body);
            dragStaff(hit);
            require(SableStaffCompatibility.status(host, body) == SableStaffCompatibility.Status.OCCUPIED
                            && StructureTraction.tryAcquire(player, owner, hit, actorSettings, (_, _) -> true).status() == StructureTraction.Status.OCCUPIED,
                    "A real staff drag did not exclude Academy traction");
            callStaff("stopDragging", new Class<?>[]{UUID.class}, staffActor);
            var ours = StructureTraction.tryAcquire(player, owner, hit, actorSettings, (_, _) -> true).session().orElseThrow();
            try {
                callStaff("toggleLock", new Class<?>[]{UUID.class}, body.getUniqueId());
                dragStaff(hit);
                require(SableStaffCompatibility.status(host, body) == SableStaffCompatibility.Status.FREE
                        && ours.status() == StructureTraction.Status.ACTIVE,
                        "Staff stole a live Academy control slot");
            } finally { ours.close(); }
            callStaff("toggleLock", new Class<?>[]{UUID.class}, body.getUniqueId());
            require(SableStaffCompatibility.status(host, body) == SableStaffCompatibility.Status.OCCUPIED,
                    "Staff could not lock after Academy release");
            callStaff("removeLock", new Class<?>[]{SubLevel.class}, body);
            var reacquired = StructureTraction.tryAcquire(player, owner, hit, actorSettings, (_, _) -> true).session().orElseThrow();
            ours.close();
            require(reacquired.status() == StructureTraction.Status.ACTIVE, "Former Academy close removed replacement");
            reacquired.close();
            evidence.append("staff_suite_present=true\nstaff_lock_and_drag_exclude_academy=true\n")
                    .append("academy_excludes_staff_lock_and_drag=true\nstaff_release_restores_academy=true\n");
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Fixed Suite public staff API mismatch", error); }
        finally {
            if (staff != null) {
                callStaff("stopDragging", new Class<?>[]{UUID.class}, staffActor);
                callStaff("removeLock", new Class<?>[]{SubLevel.class}, body);
            }
        }
    }

    private void dragStaff(StructureHit hit) {
        var plot = body.getPlot().getCenterBlock();
        var local = hit.localPoint().add(Vec3.atLowerCornerOf(plot));
        var relativeGoal = hit.worldPoint().subtract(player.getEyePosition());
        callStaff("drag", new Class<?>[]{UUID.class, UUID.class, org.joml.Vector3dc.class,
                        org.joml.Vector3dc.class, org.joml.Quaterniondc.class},
                staffActor, body.getUniqueId(), new Vector3d(relativeGoal.x, relativeGoal.y, relativeGoal.z),
                new Vector3d(local.x, local.y, local.z), new Quaterniond(body.logicalPose().orientation()));
    }

    private void callStaff(String name, Class<?>[] parameters, Object... arguments) {
        try { staff.getClass().getMethod(name, parameters).invoke(staff, arguments); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Suite public fixture call failed: " + name, error); }
    }

    private void remove(ServerSubLevel subLevel) {
        if (container.getSubLevel(subLevel.getUniqueId()) != subLevel) return;
        var origin = container.getOrigin(); var plot = subLevel.getPlot();
        container.removeSubLevel(plot.plotPos.x() - origin.x(), plot.plotPos.z() - origin.y(), SubLevelRemovalReason.REMOVED);
    }

    private void cleanup() {
        if (cleaned) return;
        if (session != null) session.close();
        if (staff != null && body != null && !body.isRemoved()) {
            callStaff("stopDragging", new Class<?>[]{UUID.class}, staffActor);
            callStaff("removeLock", new Class<?>[]{SubLevel.class}, body);
        }
        if (actor != null && !actor.isRemoved()) actor.discard();
        closePlayer();
        for (var fixture : fixtures) remove(fixture);
        for (var chunk : tickets) host.setChunkForced(chunk.x(), chunk.z(), false);
        tickets.clear(); cleaned = true;
    }

    private void finish() {
        cleanup(); noNativeRuntime();
        if (side.equals("client")) {
            require(screenshot != null && clientMotionSamples >= 3, "Client lacks real Sable synchronization/screenshot evidence");
            evidence.append("client_moving_pose_samples=").append(clientMotionSamples).append("\nclient_screenshot=").append(screenshot).append('\n');
        }
        evidence.append("native_runtime_remained_absent=true\nos_dll_enumeration_performed=false\n")
                .append("fixture_cleanup_complete=true\nticks=").append(ticks).append('\n');
        write("PASSED\n" + evidence);
        complete = true; publish();
        AcademyCraft.LOGGER.info("SABLE_STRUCTURE_SMOKE_PASSED side={} ticks={} actions=server_fixture actual_keyboard_actions=false", side, ticks);
        if (side.equals("server")) server.halt(false);
    }

    private void noNativeRuntime() { require(NativeStructureRuntime.existing(server).isEmpty(), "Mixed mode initialized Academy native runtime"); }
    private void next(Phase next) { phase = next; age = 0; AcademyCraft.LOGGER.info("SABLE_STRUCTURE_SMOKE_PHASE {} tick={} center={} velocity={}", phase, ticks, center(), velocity()); }
    private void publish() {
        checkpointEvidence = "phase=" + phase + "\nticks=" + ticks + "\n" + evidence;
        clientState = new ClientState(phase.name(), body == null ? null : body.getUniqueId(),
                body == null || body.isRemoved() ? null : center(), complete, failure);
    }

    private void writeMissingFailure() {
        var path = directory.resolve("sable-structure-" + side + "-result.txt");
        if (Files.exists(path)) return;
        try {
            Files.writeString(path, "FAILED\nfailure=process_exited_before_smoke_completion\n"
                    + "external_failure_fallback=true\nfixture_cleanup_confirmed=false\n" + checkpointEvidence,
                    StandardOpenOption.CREATE_NEW);
        } catch (java.nio.file.FileAlreadyExistsException ignored) {
            // A normal completion won the race; its original result remains authoritative.
        } catch (IOException error) { System.err.println("SABLE_STRUCTURE_FALLBACK_WRITE_FAILED " + error); }
    }
    private void guarded(Runnable action) {
        try { action.run(); } catch (Throwable error) {
            failure = error.toString();
            AcademyCraft.LOGGER.error("SABLE_STRUCTURE_SMOKE_FAILED phase={} age={}", phase, age, error);
            try { cleanup(); } catch (Throwable cleanupFailure) { error.addSuppressed(cleanupFailure); }
            try { write("FAILED\nphase=" + phase + "\nfailure=" + error + "\n" + evidence); }
            catch (Throwable writing) { error.addSuppressed(writing); }
            publish(); if (side.equals("server")) server.halt(false);
        }
    }
    private void write(String text) {
        try { Files.writeString(directory.resolve("sable-structure-" + side + "-result.txt"), text, StandardOpenOption.CREATE_NEW); }
        catch (IOException error) { throw new IllegalStateException("Cannot write Sable result", error); }
    }

    public static Path verifyDirectory(MinecraftServer server, String side) {
        try {
            require(side.equals("server") || side.equals("client"), "Invalid Sable smoke side");
            var root = Path.of(System.getProperty("academy.sable.smoke.root")).toRealPath();
            var configured = Path.of(System.getProperty("academy.sable.smoke.directory")).toAbsolutePath().normalize();
            require(root.endsWith(Path.of("run", "sable-compat")), "Smoke root must be run/sable-compat");
            require(configured.startsWith(root) && configured.toRealPath().equals(configured), "Sable smoke refuses linked/unrelated directory");
            require(server.getServerDirectory().toRealPath().equals(configured), "Actual game directory differs from smoke directory");
            var world = side.equals("server") ? configured.resolve("academy-sable-smoke") : configured.resolve("saves").resolve(CLIENT_WORLD);
            require(server.getWorldPath(LevelResource.ROOT).toRealPath().equals(world), "Sable smoke refuses unrelated world");
            require(!Files.exists(configured.resolve("sable-structure-" + side + "-result.txt")), "Sable result already exists");
            if (side.equals("server")) require("127.0.0.1".equals(server.getLocalIp()), "Sable smoke server must bind only 127.0.0.1");
            return configured;
        } catch (IOException error) { throw new IllegalStateException("Cannot verify Sable smoke isolation", error); }
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        if (active != null && active.server == event.getServer()) { active.orderlyStop = true; active.cleanup(); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        var probe = active;
        if (probe == null || probe.server != event.getServer() || !probe.side.equals("server")) return;
        probe.writeMissingFailure();
        Thread.ofPlatform().daemon().name("academy-sable-smoke-exit").start(() -> {
            try { probe.server.getRunningThread().join(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); return; }
            int status = probe.complete && probe.failure == null && probe.orderlyStop && !probe.server.isCurrentlySaving() ? 0 : 1;
            try { Files.writeString(probe.directory.resolve("sable-structure-server-exit.txt"), "exit_status=" + status
                    + "\nserver_stopped_event=true\nserver_thread_terminated=true\n", StandardOpenOption.CREATE_NEW); }
            catch (IOException error) { status = 1; }
            System.out.println("SABLE_STRUCTURE_SERVER_THREAD_TERMINATED exit_status=" + status);
            ServerLifecycleHooks.handleExit(status);
        });
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
