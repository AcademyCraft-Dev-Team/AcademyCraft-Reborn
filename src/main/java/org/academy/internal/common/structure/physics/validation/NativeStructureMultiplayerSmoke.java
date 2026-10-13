package org.academy.internal.common.structure.physics.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.structure.physics.NativeStructurePhysics;
import org.academy.internal.common.structure.space.NativeStructureQueries;
import org.academy.internal.common.structure.space.NativeStructureMiningSessions;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Three-process, opt-in fixture coordinator. Gameplay changes are made only by client packets. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class NativeStructureMultiplayerSmoke {
    public static final BlockPos CHEST = new BlockPos(0, 80, 0);
    public static final BlockPos SUPPORT = new BlockPos(2, 80, 0);
    public static final BlockPos PLACED = SUPPORT.south();
    public static final Component MARKER = Component.literal("Native multiplayer fixture");
    public static final String WORLD = "native-structure-multiplayer-smoke";
    public static final String ACTOR = "StructureActor", OBSERVER = "StructureWatch";
    public enum Phase { WAITING, ACTIVATE, QUERY_MOVED, QUERY_OLD, QUERY_CONTENT, QUERY_DETACH, QUERY_READY,
        OPEN, TAKE, RETURN, CLOSE, PLACE, USE_REPLAY,
        MINE, RACE, MINE_REPLAY, REOPEN, CURSOR, MOVE_AWAY, DONE, FAILED }
    private static final boolean ENABLED = Boolean.getBoolean("academy.nativeStructureMultiplayerSmoke");
    private static NativeStructureMultiplayerSmoke active;
    private final MinecraftServer server;
    private final ServerLevel host;
    private final Path root;
    private final NativeStructureRuntime runtime;
    private final List<ChunkPos> tickets = new ArrayList<>();
    private final StringBuilder evidence = new StringBuilder("actual_tcp_clients=2\nphysical_keyboard_mouse=false\nserver_gameplay_calls=false\n");
    private StructureHandle handle;
    private ServerLevel storage;
    private StructurePhysicsRegion region;
    private Phase phase = Phase.WAITING;
    private int ticks, age, stopStatus = 1;
    private long phaseSteps, phaseSequence;
    private StructurePose phasePose;
    private StructureHit queryHit;
    private Vec3 queryFrom, queryTo;
    private boolean prepared, finished, failed, stopping, watching, controlSkipReported;

    private NativeStructureMultiplayerSmoke(MinecraftServer server) {
        this.server = server;
        root = verifyRoot("server", server.getServerDirectory());
        try {
            require(server.getWorldPath(LevelResource.ROOT).toRealPath().equals(root.resolve("server").resolve(WORLD)),
                    "Unexpected dedicated test world path");
            var phases = root.resolve("coord/phases");
            Files.createDirectories(phases);
            require(phases.toRealPath().equals(phases), "Linked phase directory");
            try (var entries = Files.list(phases)) { require(entries.findAny().isEmpty(), "Old coordination phases exist"); }
        } catch (IOException error) { throw new IllegalStateException(error); }
        host = server.overworld();
        runtime = NativeStructureRuntime.backend(server);
        writePhase();
    }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (ENABLED && "server".equals(role())) {
            require(active == null, "Only one isolated multiplayer server is supported");
            active = new NativeStructureMultiplayerSmoke(event.getServer());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void tick(ServerTickEvent.Post event) {
        if (!ENABLED || active == null || active.server != event.getServer() || active.finished || active.failed) return;
        try { active.advance(); }
        catch (Throwable error) { active.fail(error); }
    }

    private void advance() {
        require(++ticks < 12000 && ++age < (phase == Phase.WAITING ? 9000 : 2000), "Multiplayer deadline at " + phase);
        for (var role : List.of("actor", "observer")) require(!Files.exists(root.resolve("coord").resolve(role + "-failure.txt")),
                "Client failed: " + role);
        if (phase == Phase.WAITING) {
            if (player("actor") == null || player("observer") == null || !exists("actor.ready") || !exists("observer.ready")) return;
            require(!player("actor").getUUID().equals(player("observer").getUUID()), "Two distinct players are required");
            for (var role : List.of("actor", "observer")) {
                var player = player(role);
                var ready = read(root.resolve("coord").resolve(role + ".ready"));
                require(player.getUUID().toString().equals(ready.getProperty("uuid")), "Client/server identity mismatch");
                require(!player.connection.getConnection().isMemoryConnection()
                                && player.connection.getConnection().getRemoteAddress() instanceof InetSocketAddress address
                                && address.getAddress().isLoopbackAddress(), "Fixture requires real loopback TCP clients");
                evidence.append(role).append("_uuid=").append(player.getUUID()).append('\n');
            }
            initialize(); next(Phase.ACTIVATE); return;
        }
        require(player("actor") != null && player("observer") != null, "A client disconnected during " + phase);
        for (var role : List.of("actor", "observer")) {
            require(player(role).level() == host && (storage == null || storage.players().isEmpty()), "Real player left host or entered storage");
        }
        if ((phase == Phase.MINE || phase == Phase.RACE) && age % 20 == 0) {
            for (var role : List.of("actor", "observer")) {
                var player = player(role);
                AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_MULTIPLAYER_MINING_SERVER phase={} age={} role={} session={} selected={} held={}",
                        phase, age, role, NativeStructureMiningSessions.snapshot(player),
                        player.getInventory().getSelectedSlot(), player.getMainHandItem());
            }
        }
        if (region != null && phase != Phase.DONE) {
            require(region.status() == StructurePhysicsRegion.Status.READY, "Physics unavailable: " + region.diagnostic());
            drive();
        }
        switch (phase) {
            case ACTIVATE -> {
                if (handle.lifecycle() != StructureLifecycle.ACTIVE) return;
                if (!prepared) { prepare(); return; }
                if (age < 25 || region.motion(handle.reference()).isEmpty()) return;
                captureQuery(); next(Phase.QUERY_MOVED);
            }
            case QUERY_MOVED -> {
                require(handle.geometryRevision() != queryHit.geometryRevision(), "History test requires a genuinely older physical pose");
                require(!StructureQueries.validate(host, queryFrom, queryTo, queryHit).succeeded(), "Strict query unexpectedly accepted an old pose");
                require(NativeStructureQueries.validateMoving(host, queryFrom, queryTo, queryHit).succeeded(),
                        "Server-published moving observation did not reconcile");
                var forged = new StructureHit(queryHit.block(), queryHit.runtimeId(), queryHit.geometryRevision(),
                        new StructurePose(queryHit.pose().worldOrigin().add(0.1, 0, 0), queryHit.pose().rotation()),
                        queryHit.localFace(), queryHit.localPoint(), queryHit.worldPoint().add(0.1, 0, 0), queryHit.inside());
                require(!NativeStructureQueries.validateMoving(host, queryFrom, queryTo, forged).succeeded(), "Forged historical pose was accepted");
                evidence.append("query_real_history_accepted=true\nquery_forged_pose_rejected=true\n");
                next(Phase.QUERY_OLD);
            }
            case QUERY_OLD -> {
                if (age < 8) return;
                require(!NativeStructureQueries.validateMoving(host, queryFrom, queryTo, queryHit).succeeded(), "Over-age observation was accepted");
                captureQuery();
                var extra = new BlockPos(4, 80, 0);
                put(extra, Blocks.STONE.defaultBlockState()); put(extra, Blocks.AIR.defaultBlockState());
                require(!NativeStructureQueries.validateMoving(host, queryFrom, queryTo, queryHit).succeeded(),
                        "Old observation survived content changes, even after returning the cell to air");
                evidence.append("query_overage_rejected=true\nquery_content_change_rejected=true\n");
                next(Phase.QUERY_CONTENT);
            }
            case QUERY_CONTENT -> { captureQuery(); next(Phase.QUERY_DETACH); }
            case QUERY_DETACH -> {
                require(handle.geometryRevision() != queryHit.geometryRevision(), "Detach test requires an older moving observation");
                require(region.detach(handle.reference()), "Cannot detach query fixture");
                region.attach(handle);
                require(!NativeStructureQueries.validateMoving(host, queryFrom, queryTo, queryHit).succeeded(),
                        "Historical authorization survived detach/reattach");
                evidence.append("query_reattach_history_rejected=true\n");
                next(Phase.QUERY_READY);
            }
            case QUERY_READY -> next(Phase.OPEN);
            case OPEN -> {
                if (!bothDone()) return;
                require(chestMenus() == 2 && ChestBlockEntity.getOpenCount(storage, CHEST) == 2, "Both real menus must own the same chest");
                require(count(Items.DIAMOND) == 7, "Initial diamonds changed");
                movingEvidence(); next(Phase.TAKE);
            }
            case TAKE -> {
                if (!bothDone() || age < 12) return;
                require(chest().getItem(0).isEmpty() && inventoryCount(Items.DIAMOND) == 7 && count(Items.DIAMOND) == 7,
                        "Competing menu clicks duplicated or lost diamonds");
                movingEvidence(); next(Phase.RETURN);
            }
            case RETURN -> {
                if (!bothDone() || chest().getItem(0).getCount() != 7) return;
                require(count(Items.DIAMOND) == 7 && inventoryCount(Items.DIAMOND) == 0, "QUICK_MOVE return must conserve seven items");
                movingEvidence(); next(Phase.CLOSE);
            }
            case CLOSE -> {
                if (!bothDone() || chestMenus() != 0) return;
                require(ChestBlockEntity.getOpenCount(storage, CHEST) == 0, "Closed menus retained openers");
                next(Phase.PLACE);
            }
            case PLACE -> {
                if (!bothDone()) return;
                require(storage.getBlockState(PLACED).is(Blocks.COBBLESTONE) && inventoryCount(Items.COBBLESTONE) == 15,
                        "Two concurrent placements must write once and consume exactly one of sixteen items");
                require(count(Items.COBBLESTONE) == 15, "Placement generated unexpected item entities");
                movingEvidence(); next(Phase.USE_REPLAY);
            }
            case USE_REPLAY -> {
                if (!bothDone() || age < 15) return;
                require(storage.getBlockState(PLACED).is(Blocks.COBBLESTONE) && count(Items.COBBLESTONE) == 15,
                        "Replayed use changed the placement/material result");
                evidence.append("use_nonce_replay_no_change=true\n");
                next(Phase.MINE);
            }
            case MINE -> {
                if (!bothDone() || !storage.getBlockState(PLACED).isAir()) return;
                require(count(Items.COBBLESTONE) == 16 && toolDamage() == 1, "Actual mining must drop one item and wear one tool once");
                movingEvidence(); evidence.append("observer_rendered_cracks=true\n"); next(Phase.RACE);
            }
            case RACE -> {
                if (!bothDone() || !storage.getBlockState(SUPPORT).isAir()) return;
                require(count(Items.COBBLESTONE) == 17 && toolDamage() == 2,
                        "Competing mining must yield exactly one extra drop and one extra tool wear");
                movingEvidence(); next(Phase.MINE_REPLAY);
            }
            case MINE_REPLAY -> {
                if (!bothDone() || age < 20) return;
                require(count(Items.COBBLESTONE) == 17 && toolDamage() == 2 && storage.getBlockState(SUPPORT).isAir(),
                        "Old mining packets replayed a finished destruction");
                evidence.append("mine_replay_no_change=true\n"); next(Phase.REOPEN);
            }
            case REOPEN -> {
                if (!bothDone() || chestMenus() != 2) return;
                require(count(Items.DIAMOND) == 7, "Diamonds changed before cursor test");
                movingEvidence(); next(Phase.CURSOR);
            }
            case CURSOR -> {
                if (!bothDone()) return;
                require(player("actor").containerMenu.getCarried().is(Items.DIAMOND)
                                && player("actor").containerMenu.getCarried().getCount() == 4 && count(Items.DIAMOND) == 7,
                        "Real right-click must carry four and leave three");
                next(Phase.MOVE_AWAY);
            }
            case MOVE_AWAY -> {
                if (!bothDone() || chestMenus() != 0) return;
                require(player("actor").containerMenu.getCarried().isEmpty()
                                && player("observer").containerMenu.getCarried().isEmpty()
                                && count(Items.DIAMOND) == 7 && ChestBlockEntity.getOpenCount(storage, CHEST) == 0,
                        "Moving out of range failed to close menus and return cursors exactly once");
                movingEvidence();
                captureQuery();
                region.close();
                require(runtime.unload(handle.reference()), "Fixture unload/save failed");
                require(!NativeStructureQueries.validateMoving(host, queryFrom, queryTo, queryHit).succeeded(), "Unloaded target remained authorized");
                releaseTickets();
                evidence.append("moving_menu_distance_close=true\nclosed_menu_replay_conserved=true\nquery_unloaded_rejected=true\nfinal_diamonds=7\nfinal_cobblestone=17\nstorage_players=0\n");
                next(Phase.DONE);
            }
            case DONE -> {
                if (!bothDone()) return;
                writeNew(root.resolve("server-result.txt"), "PASSED\n" + evidence + "ticks=" + ticks + "\n");
                finished = true; stopStatus = 0;
                AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_MULTIPLAYER_SERVER_PASSED ticks={}", ticks);
                server.halt(false);
            }
            default -> { }
        }
    }

    private void initialize() {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (host.setChunkForced(x, z, true)) tickets.add(new ChunkPos(x, z));
            host.getChunk(x, z);
        }
        for (var position : BlockPos.betweenClosed(new BlockPos(-11, 106, -11), new BlockPos(11, 120, 11))) {
            require(host.getBlockState(position).isAir(), "Fixture refuses existing host content at " + position);
        }
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        for (var position : BlockPos.betweenClosed(new BlockPos(-4, 107, -4), new BlockPos(4, 107, 4))) {
            require(host.setBlock(position, Blocks.STONE.defaultBlockState(), flags), "Cannot prepare floor");
        }
        handle = runtime.createEmpty(host, StructurePose.identity(new Vec3(0, 32, 0))).orElseThrow();
        storage = runtime.storageLevel(handle.reference()).orElse(null);
        for (var role : List.of("actor", "observer")) {
            var player = player(role);
            for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
                require(player.getInventory().getItem(index).isEmpty(), "Fresh fixture players must have empty inventories");
            }
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().setItem(1, marked(Items.COBBLESTONE, 8));
            player.getInventory().setItem(2, marked(Items.WOODEN_PICKAXE, 1));
            player.getAbilities().mayfly = true; player.getAbilities().flying = true; player.onUpdateAbilities();
            var feet = new Vec3(role.equals("actor") ? 1.5 : 0.4, 112.5 - player.getEyeHeight(), 3.65);
            require(player.teleport(new TeleportTransition(host, feet, Vec3.ZERO, 180, 0,
                    TeleportTransition.DO_NOTHING)) == player, "Fixture player positioning failed");
            player.inventoryMenu.broadcastChanges();
        }
    }

    private void prepare() {
        storage = runtime.storageLevel(handle.reference()).orElseThrow();
        put(CHEST, Blocks.CHEST.defaultBlockState()); put(SUPPORT, Blocks.STONE.defaultBlockState());
        chest().setItem(0, marked(Items.DIAMOND, 7)); chest().setChanged();
        region = StructurePhysics.openRegion(host, new AABB(-12, 106, -12, 12, 122, 12));
        region.attach(handle); prepared = true;
        evidence.append("structure_ref=").append(handle.reference()).append('\n');
        writePhase();
    }

    private void drive() {
        // Runtime physics/projection precede mining. A completed break can invalidate this
        // tick's geometry after stepping; let the next runtime tick rebuild it before controls.
        if (!runtime.isPresented(handle.reference())) {
            if (!controlSkipReported) {
                AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_MULTIPLAYER_CONTROL_SKIP phase={} reason=awaiting_current_geometry", phase);
                controlSkipReported = true;
            }
            return;
        }
        controlSkipReported = false;
        var center = handle.pose().localToWorldPoint(Vec3.atCenterOf(CHEST));
        double vx = phase == Phase.MOVE_AWAY ? (chestMenus() == 0 ? 0 : 1.5) : Math.cos(ticks * 0.06) * 0.16;
        double vy = 0.327 + (112.5 - center.y) * 5;
        region.setVelocity(handle.reference(), new Vec3(vx, vy, 0), new Vec3(0, Math.cos(ticks * 0.04) * 0.02, 0));
    }

    private void captureQuery() {
        queryFrom = handle.pose().localToWorldPoint(new Vec3(0.5, 80.5, 4));
        queryTo = handle.pose().localToWorldPoint(new Vec3(0.5, 80.5, -1));
        queryHit = StructureQueries.raycast(host, queryFrom, queryTo).orElseThrow();
        require(queryHit.block().localPosition().equals(CHEST), "Query fixture ray must hit the real chest");
    }

    private void put(BlockPos position, net.minecraft.world.level.block.state.BlockState state) {
        require(handle.world().compareAndSet(position, handle.world().read(position), new StructureBlockData(state))
                == StructureWriteResult.APPLIED, "Fixture storage setup failed");
    }
    private ChestBlockEntity chest() { return (ChestBlockEntity) storage.getBlockEntity(CHEST); }
    private ServerPlayer player(String role) { return server.getPlayerList().getPlayerByName(role.equals("actor") ? ACTOR : OBSERVER); }
    private int chestMenus() {
        int result = 0;
        for (var role : List.of("actor", "observer")) if (player(role).containerMenu instanceof ChestMenu menu && menu.slots.size() == 63) result++;
        return result;
    }
    private int inventoryCount(Item item) {
        int count = 0;
        for (var role : List.of("actor", "observer")) {
            var inventory = player(role).getInventory();
            for (int index = 0; index < inventory.getContainerSize(); index++) {
                var stack = inventory.getItem(index); if (stack.is(item)) count += stack.getCount();
            }
        }
        return count;
    }
    private int count(Item item) {
        int count = inventoryCount(item);
        for (int index = 0; index < chest().getContainerSize(); index++) {
            var stack = chest().getItem(index); if (stack.is(item)) count += stack.getCount();
        }
        for (var role : List.of("actor", "observer")) {
            var cursor = player(role).containerMenu.getCarried(); if (cursor.is(item)) count += cursor.getCount();
        }
        for (var level : List.of(host, storage)) for (var entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity drop && !drop.isRemoved() && drop.getItem().is(item)) count += drop.getItem().getCount();
        }
        return count;
    }
    private int toolDamage() {
        int damage = 0;
        for (var role : List.of("actor", "observer")) {
            var tool = player(role).getInventory().getItem(2);
            require(tool.is(Items.WOODEN_PICKAXE), "Fixture tool unexpectedly disappeared");
            damage += tool.getDamageValue();
        }
        return damage;
    }
    private NativeStructurePhysics.Diagnostics diagnostics() { return ((NativeStructurePhysics.Region) region).diagnostics(); }
    private void movingEvidence() {
        require(diagnostics().steps() > phaseSteps && !NativeStructurePhysicsWorldSmoke.samePose(phasePose, handle.pose()),
                "Operation occurred without real native motion at " + phase);
        evidence.append("moving_").append(phase.name().toLowerCase()).append("_steps=")
                .append(diagnostics().steps() - phaseSteps).append('\n');
    }
    private void next(Phase next) {
        phase = next; age = 0;
        if (region != null && handle.lifecycle() == StructureLifecycle.ACTIVE) {
            phaseSteps = diagnostics().steps(); phasePose = handle.pose();
        }
        writePhase();
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_MULTIPLAYER_PHASE {} tick={}", phase, ticks);
    }
    private void writePhase() {
        var values = new Properties(); values.setProperty("phase", phase.name()); values.setProperty("session", session());
        require(++phaseSequence <= 999999, "Phase sequence limit exceeded");
        values.setProperty("sequence", Long.toString(phaseSequence));
        if (handle != null) { values.setProperty("reference", handle.reference().id().toString()); values.setProperty("runtime", handle.runtimeId().toString()); }
        write(root.resolve("coord/phases").resolve(String.format(Locale.ROOT, "%06d.properties", phaseSequence)), values);
    }
    private boolean bothDone() { return exists("actor-" + phase + ".done") && exists("observer-" + phase + ".done"); }
    private boolean exists(String file) { return Files.isRegularFile(root.resolve("coord").resolve(file)); }
    private void releaseTickets() {
        while (!tickets.isEmpty()) { var chunk = tickets.getFirst(); host.setChunkForced(chunk.x(), chunk.z(), false); tickets.removeFirst(); }
    }
    private void fail(Throwable error) {
        failed = true; phase = Phase.FAILED;
        AcademyCraft.LOGGER.error("NATIVE_STRUCTURE_MULTIPLAYER_SERVER_FAILED", error);
        try { writeNew(root.resolve("server-result.txt"), "FAILED\n" + error + "\n" + evidence); writePhase(); }
        catch (Throwable suppressed) { error.addSuppressed(suppressed); }
        try { if (region != null) region.close(); } catch (Throwable suppressed) { error.addSuppressed(suppressed); }
        try { releaseTickets(); } catch (Throwable suppressed) { error.addSuppressed(suppressed); }
        server.halt(false);
    }

    public static String role() { return System.getProperty("academy.nativeStructureMultiplayerRole", ""); }
    public static String session() { return System.getProperty("academy.nativeStructureMultiplayerSession", ""); }
    public static Path verifyRoot(String role, Path actualDirectory) {
        try {
            require(ENABLED && List.of("server", "actor", "observer").contains(role), "Multiplayer probe is not enabled");
            require(session().matches("[a-zA-Z0-9_-]{1,64}"), "Invalid test session");
            var root = Path.of(System.getProperty("academy.nativeStructureMultiplayerRoot")).toRealPath();
            var run = Path.of(System.getProperty("academy.nativeStructureMultiplayerRunRoot")).toRealPath();
            require(root.startsWith(run) && root.getFileName().toString().equals("native-structure-multiplayer-" + session()), "Unrelated test root");
            require(actualDirectory.toRealPath().equals(root.resolve(role)), "Linked or unrelated game directory");
            require(root.resolve("coord").toRealPath().equals(root.resolve("coord")), "Linked coordination directory");
            require(!Files.exists(root.resolve(role + "-result.txt"), LinkOption.NOFOLLOW_LINKS), "Old role result exists");
            return root;
        } catch (IOException error) { throw new IllegalStateException("Cannot verify multiplayer isolation", error); }
    }
    public static Properties read(Path path) {
        var properties = new Properties();
        try (var input = Files.newInputStream(path)) { properties.load(input); return properties; }
        catch (IOException error) { throw new IllegalStateException("Cannot read coordination file " + path, error); }
    }
    public static void write(Path path, Properties values) {
        var temporary = path.resolveSibling(path.getFileName() + "." + ProcessHandle.current().pid() + ".tmp");
        try {
            require(!Files.exists(path, LinkOption.NOFOLLOW_LINKS), "Coordination records are immutable: " + path);
            try (var output = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) { values.store(output, session()); }
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unavailable) { Files.move(temporary, path); }
        } catch (IOException error) { throw new IllegalStateException("Cannot publish coordination file " + path, error); }
    }
    public static void writeNew(Path path, String text) {
        try { Files.writeString(path, text, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); }
        catch (IOException error) { throw new IllegalStateException("Cannot save multiplayer evidence", error); }
    }
    private static ItemStack marked(Item item, int count) { var stack = new ItemStack(item, count); stack.set(DataComponents.CUSTOM_NAME, MARKER); return stack; }
    public static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        if (active != null && active.server == event.getServer()) { active.stopping = true; active.releaseTickets(); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        var state = active;
        if (state == null || state.server != event.getServer() || state.watching) return;
        state.watching = true;
        Thread.ofPlatform().daemon().name("academy-multiplayer-smoke-exit").start(() -> {
            try { state.server.getRunningThread().join(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            int status = state.finished && !state.failed && state.stopping && !state.server.isCurrentlySaving() ? state.stopStatus : 1;
            try {
                var crashDirectory = state.root.resolve("server/crash-reports");
                if (Files.isDirectory(crashDirectory)) try (var paths = Files.list(crashDirectory)) { if (paths.findAny().isPresent()) status = 1; }
                writeNew(state.root.resolve("server-exit.txt"), "exit_status=" + status + "\nserver_thread_terminated=true\n");
            } catch (IOException | RuntimeException error) { status = 1; }
            ServerLifecycleHooks.handleExit(status);
        });
    }
}
