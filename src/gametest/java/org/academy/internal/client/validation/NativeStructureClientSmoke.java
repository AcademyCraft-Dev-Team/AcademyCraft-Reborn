package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
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
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureWriteResult;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.mixin.common.LevelTicksAccessor;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Two-process persistence probe; runs only in an explicitly selected disposable integrated world. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class NativeStructureClientSmoke {
    private static final String MODE = System.getProperty("academy.nativeStructureSmoke", "");
    private static final String WORLD_NAME = "Native Structure Smoke";
    private static final BlockPos CHEST = new BlockPos(-8, 80, -8);
    private static final BlockPos FURNACE = new BlockPos(-12, 80, -12);
    private static final BlockPos LAMP = new BlockPos(-4, 80, -12);
    private static final Vec3 ENTITY_POSITION = new Vec3(-16.5, 84.25, -10.5);
    private static final String ENTITY_NAME = "academy_native_structure_restart_fixture";
    private static final int EVIDENCE_SCHEMA = 2;
    private static final int SCHEDULED_DELAY = 1000;
    private static final long PROCESS_ID = ProcessHandle.current().pid();
    private static final long PROCESS_START = ManagementFactory.getRuntimeMXBean().getStartTime();
    private static volatile MinecraftServer owner;
    private static volatile String failure;
    private static volatile boolean serverComplete;
    private static boolean started;
    private static boolean finished;
    private static int clientTicks;
    // Everything below is read/written only by the integrated server thread.
    private static NativeStructureRuntime runtime;
    private static StructureHandle handle;
    private static CompoundTag evidence;
    private static Path evidencePath;
    private static UUID localEntityId;
    private static int serverTicks;
    private static int settledTicks;
    private static boolean prepared;
    private static boolean initialized;

    private NativeStructureClientSmoke() {
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (MODE.isEmpty() || finished) return;
        require(MODE.equals("write") || MODE.equals("read"),
                "academy.nativeStructureSmoke must be write or read");
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        require(failure == null, "Native structure server probe failed: " + failure);
        require(++clientTicks <= 2400, "Native structure client probe timed out");
        if (!started) {
            var server = minecraft.getSingleplayerServer();
            require(server != null, "Native structure smoke requires an integrated server");
            var world = isolatedWorld(minecraft, server);
            var path = world.resolve("academy-native-structure-smoke.nbt");
            require(MODE.equals("write") ? !Files.exists(path) : Files.isRegularFile(path),
                    "Write needs a fresh fixture; read needs the preceding write process's evidence");
            started = true;
            owner = server;
            var playerId = minecraft.player.getUUID();
            server.execute(() -> {
                try {
                    var player = server.getPlayerList().getPlayer(playerId);
                    require(player != null, "Integrated player is absent");
                    runtime = NativeStructureRuntime.backend(server);
                    evidencePath = path;
                    if (MODE.equals("write")) {
                        var pose = StructurePose.fromYawAroundPivot(new Vec3(321.25, 96.5, -73.75),
                                90, new Vec3(0.5, 0, 0.5));
                        handle = runtime.createEmpty(player.level(), pose).orElseThrow(() ->
                                new IllegalStateException("The isolated smoke world has no free native slot"));
                    } else {
                        evidence = NbtIo.readCompressed(evidencePath, NbtAccounter.unlimitedHeap());
                        require(evidence.getIntOr("schema", -1) == EVIDENCE_SCHEMA,
                                "Unsupported smoke evidence format; create a fresh write fixture");
                        require(evidence.getLongOr("process_id", -1) != PROCESS_ID
                                        || evidence.getLongOr("process_start", -1) != PROCESS_START,
                                "Read must run in a second real JVM process");
                        var ref = readReference(evidence.getCompoundOrEmpty("reference"));
                        // Deliberately do not call load(): startup must restore the saved ACTIVE entry.
                        handle = runtime.resolve(ref).orElseThrow(() ->
                                new IllegalStateException("Saved ACTIVE structure did not reactivate on server startup"));
                        require(handle.reference().equals(ref), "Restart changed the stable structure identity");
                    }
                    initialized = true;
                } catch (Throwable error) {
                    fail(error);
                }
            });
        }
        if (!serverComplete) return;
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_SMOKE_{}_PASSED pid={} second_process={}",
                MODE.toUpperCase(java.util.Locale.ROOT), PROCESS_ID, MODE.equals("read"));
        finished = true;
        minecraft.stop();
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (MODE.isEmpty() || event.getServer() != owner || !initialized || serverComplete || failure != null) return;
        try {
            require(++serverTicks <= 1700, "Native structure server probe timed out");
            if (handle.lifecycle() != StructureLifecycle.ACTIVE) {
                require(serverTicks <= 300, "Restored native structure never became ACTIVE");
                return;
            }
            var level = runtime.storageLevel(handle.reference()).orElseThrow();
            require(level.players().isEmpty(), "Native storage must have no resident player");
            require(level.isPositionTickingWithEntitiesLoaded(ChunkPos.pack(CHEST)),
                    "ACTIVE storage must have a real ticking chunk with loaded entities");
            if (!prepared) {
                if (MODE.equals("write")) prepareWrite(level);
                else prepareRead(level);
                prepared = true;
            }
            if (MODE.equals("write")) advanceWrite(level);
            else advanceRead(level);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private static void prepareWrite(ServerLevel level) {
        for (var position : List.of(CHEST, FURNACE, LAMP)) {
            require(handle.world().read(position).state().isAir(), "New smoke storage must start empty");
        }
        var chest = new ChestBlockEntity(CHEST, Blocks.CHEST.defaultBlockState());
        chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
        put(CHEST, new StructureBlockData(Blocks.CHEST.defaultBlockState(),
                chest.saveWithFullMetadata(level.registryAccess())));
        put(FURNACE, new StructureBlockData(Blocks.FURNACE.defaultBlockState()));
        var furnace = furnace(level);
        furnace.setItem(0, new ItemStack(Items.IRON_ORE, 1));
        furnace.setItem(1, new ItemStack(Items.COAL, 1));
        furnace.setChanged();
        var entity = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
        require(entity != null, "Native fixture could not create its local armor stand");
        entity.setPos(ENTITY_POSITION);
        entity.setNoGravity(true);
        entity.setCustomName(Component.literal(ENTITY_NAME));
        localEntityId = entity.getUUID();
        require(level.addFreshEntity(entity), "Native storage rejected its local armor stand fixture");
        assertLocalEntity(level);
    }

    private static void advanceWrite(ServerLevel level) throws IOException {
        var furnaceData = furnace(level).saveWithFullMetadata(level.registryAccess());
        var progress = furnaceData.getIntOr("cooking_time_spent", -1);
        if (progress < 40) return;
        require(progress < furnaceData.getIntOr("cooking_total_time", -1)
                        && furnace(level).getItem(0).is(Items.IRON_ORE) && furnace(level).getItem(2).isEmpty(),
                "Write must save a genuinely unfinished, natively ticking furnace");
        put(LAMP, new StructureBlockData(Blocks.REDSTONE_LAMP.defaultBlockState()
                .setValue(RedstoneLampBlock.LIT, true)));
        level.scheduleTick(LAMP, Blocks.REDSTONE_LAMP, SCHEDULED_DELAY, TickPriority.HIGH);
        var pending = lampTicks(level);
        require(pending.size() == 1 && pending.getFirst().triggerTick() > level.getGameTime(),
                "Write must have exactly one future native block tick before saving");
        assertLocalEntity(level);
        evidence = new CompoundTag();
        evidence.putInt("schema", EVIDENCE_SCHEMA);
        evidence.putLong("process_id", PROCESS_ID);
        evidence.putLong("process_start", PROCESS_START);
        evidence.put("reference", writeReference(handle.reference()));
        evidence.put("pose", writePose(handle.pose()));
        evidence.put("chest", handle.world().read(CHEST).blockEntityData());
        evidence.put("furnace", furnaceData);
        var localEntity = new CompoundTag();
        localEntity.putString("id", localEntityId.toString());
        localEntity.putString("name", ENTITY_NAME);
        localEntity.putDouble("x", ENTITY_POSITION.x);
        localEntity.putDouble("y", ENTITY_POSITION.y);
        localEntity.putDouble("z", ENTITY_POSITION.z);
        evidence.put("local_entity", localEntity);
        evidence.putLong("write_game_time", level.getGameTime());
        evidence.putLong("scheduled_trigger", pending.getFirst().triggerTick());
        evidence.putInt("scheduled_delay", SCHEDULED_DELAY);
        require(runtime.save(handle.reference()), "Native active structure flush failed");
        require(handle.lifecycle() == StructureLifecycle.ACTIVE
                        && runtime.resolve(handle.reference()).orElseThrow() == handle,
                "Write must preserve ACTIVE ownership for restart auto-activation");
        // CREATE_NEW prevents accidentally replacing proof from a previous write process.
        try (var output = Files.newOutputStream(evidencePath, java.nio.file.StandardOpenOption.CREATE_NEW)) {
            NbtIo.writeCompressed(evidence, output);
        }
        AcademyCraft.LOGGER.info(
                "NATIVE_STRUCTURE_SMOKE_WRITE_SAVED ref={} cooking={}/{} diamonds=7 future_tick={} local_entity={} active=true evidence={}",
                handle.reference(), progress, furnaceData.getIntOr("cooking_total_time", -1),
                pending.getFirst().triggerTick(), localEntityId, evidencePath);
        serverComplete = true;
    }

    private static void prepareRead(ServerLevel level) {
        var localEntity = evidence.getCompoundOrEmpty("local_entity");
        localEntityId = UUID.fromString(localEntity.getStringOr("id", ""));
        var savedEntityPosition = new Vec3(localEntity.getDoubleOr("x", Double.NaN),
                localEntity.getDoubleOr("y", Double.NaN), localEntity.getDoubleOr("z", Double.NaN));
        require(savedEntityPosition.equals(ENTITY_POSITION)
                        && localEntity.getStringOr("name", "").equals(ENTITY_NAME),
                "Saved local entity evidence must identify the original fixture contents");
        assertLocalEntity(level);
        require(handle.pose().equals(readPose(evidence.getCompoundOrEmpty("pose"))),
                "Normal JVM restart changed the saved pose");
        require(evidence.getCompoundOrEmpty("chest").equals(handle.world().read(CHEST).blockEntityData()),
                "Normal JVM restart changed complete chest NBT");
        assertDiamonds(level);
        require(level.getBlockEntity(FURNACE) instanceof FurnaceBlockEntity,
                "Restart must recreate and register a real furnace block entity");
        var originalProgress = evidence.getCompoundOrEmpty("furnace").getIntOr("cooking_time_spent", -1);
        var data = furnace(level).saveWithFullMetadata(level.registryAccess());
        if (!furnace(level).getItem(0).isEmpty()) {
            require(data.getIntOr("cooking_time_spent", -1) >= originalProgress,
                    "Restart reset the unfinished furnace's saved progress");
        }
        assertFurnaceConservation(level);
        var pending = lampTicks(level);
        require(level.getBlockState(LAMP).is(Blocks.REDSTONE_LAMP)
                        && level.getBlockState(LAMP).getValue(RedstoneLampBlock.LIT)
                        && pending.size() == 1 && pending.getFirst().priority() == TickPriority.HIGH,
                "Second JVM must load the still-lit lamp and its single saved HIGH-priority block tick");
        var remaining = pending.getFirst().triggerTick() - level.getGameTime();
        require(remaining > 0 && remaining <= evidence.getIntOr("scheduled_delay", -1),
                "Saved scheduled tick must retain a positive bounded remaining delay");
        AcademyCraft.LOGGER.info(
                "NATIVE_STRUCTURE_SMOKE_READ_RESTORED ref={} cooking={} original_cooking={} future_remaining={} local_entity={} auto_active=true",
                handle.reference(), data.getIntOr("cooking_time_spent", -1), originalProgress, remaining, localEntityId);
    }

    private static void advanceRead(ServerLevel level) {
        assertDiamonds(level);
        assertLocalEntity(level);
        assertFurnaceConservation(level);
        var furnace = furnace(level);
        if (!furnace.getItem(0).isEmpty() || !furnace.getItem(2).is(Items.IRON_INGOT)
                || furnace.getItem(2).getCount() != 1
                || level.getBlockState(LAMP).getValue(RedstoneLampBlock.LIT)
                || !lampTicks(level).isEmpty()) {
            settledTicks = 0;
            return;
        }
        // Observe a stable completed result rather than merely the first state transition.
        if (++settledTicks < 20) return;
        require(runtime.save(handle.reference()), "Completed read phase could not flush native contents");
        require(runtime.unload(handle.reference()), "Completed read phase could not deactivate its fixture");
        AcademyCraft.LOGGER.info(
                "NATIVE_STRUCTURE_SMOKE_READ_COMPLETED ref={} diamonds=7 iron=1 pending_ticks=0 lamp_lit=false local_entity={} entity_count=1 server_ticks={}",
                handle.reference(), localEntityId, serverTicks);
        serverComplete = true;
    }

    private static void assertLocalEntity(ServerLevel level) {
        var entity = level.getEntity(localEntityId);
        require(entity instanceof ArmorStand && !entity.isRemoved() && entity.level() == level
                        && entity.position().distanceToSqr(ENTITY_POSITION) < 1.0E-12
                        && entity.isNoGravity() && Component.literal(ENTITY_NAME).equals(entity.getCustomName()),
                "The local armor stand must preserve its UUID, position, no-gravity flag and custom name");
        var bounds = new AABB(-32, level.getMinY(), -32, 32, level.getMaxY() + 1, 32);
        var entities = level.getEntitiesOfClass(ArmorStand.class, bounds);
        require(entities.size() == 1 && entities.getFirst() == entity,
                "Native entity persistence must retain exactly one armor stand without copying it");
    }

    private static void assertDiamonds(ServerLevel level) {
        require(level.getBlockEntity(CHEST) instanceof ChestBlockEntity, "Saved chest must be a real block entity");
        var chest = (ChestBlockEntity) level.getBlockEntity(CHEST);
        require(chest.getLevel() == level && chest.getBlockPos().equals(CHEST)
                        && chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 7,
                "Restart lost or moved the seven diamonds");
        for (var slot = 1; slot < chest.getContainerSize(); slot++) {
            require(chest.getItem(slot).isEmpty(), "Restart duplicated chest contents");
        }
    }

    private static FurnaceBlockEntity furnace(ServerLevel level) {
        require(level.getBlockEntity(FURNACE) instanceof FurnaceBlockEntity, "Native furnace is absent");
        return (FurnaceBlockEntity) level.getBlockEntity(FURNACE);
    }

    private static void assertFurnaceConservation(ServerLevel level) {
        var furnace = furnace(level);
        var input = furnace.getItem(0);
        var output = furnace.getItem(2);
        require((input.isEmpty() || input.is(Items.IRON_ORE))
                        && (output.isEmpty() || output.is(Items.IRON_INGOT))
                        && input.getCount() + output.getCount() == 1,
                "Exactly one ore/iron unit must survive restart and native smelting");
    }

    @SuppressWarnings("unchecked")
    private static List<ScheduledTick<Block>> lampTicks(ServerLevel level) {
        var result = new ArrayList<ScheduledTick<Block>>();
        var containers = ((LevelTicksAccessor<Block>) level.getBlockTicks()).academy$getAllContainers();
        for (var container : containers.values()) {
            container.getAll().filter(tick -> tick.type() == Blocks.REDSTONE_LAMP && tick.pos().equals(LAMP))
                    .forEach(result::add);
        }
        return result;
    }

    private static void put(BlockPos position, StructureBlockData replacement) {
        require(handle.world().compareAndSet(position, handle.world().read(position), replacement)
                        == StructureWriteResult.APPLIED,
                "Native fixture CAS failed at " + position);
    }

    private static Path isolatedWorld(Minecraft minecraft, MinecraftServer server) {
        try {
            var directory = minecraft.gameDirectory.toPath().toRealPath();
            var saves = directory.resolve("saves").toRealPath();
            var world = server.getWorldPath(LevelResource.ROOT).toRealPath();
            require(directory.getFileName().toString().startsWith("native-structure-client-smoke"),
                    "Use a dedicated game directory beginning native-structure-client-smoke");
            require(world.startsWith(saves) && world.getFileName().toString().equals(WORLD_NAME),
                    "Smoke may access only the Native Structure Smoke world inside its isolated saves folder");
            return world;
        } catch (IOException error) {
            throw new IllegalStateException("Could not verify the isolated native smoke world", error);
        }
    }

    private static CompoundTag writeReference(StructureRef reference) {
        var tag = new CompoundTag();
        tag.putString("id", reference.id().toString());
        tag.putString("dimension", reference.dimension().toString());
        tag.putString("backend", reference.backend().toString());
        tag.putLong("generation", reference.generation());
        return tag;
    }

    private static StructureRef readReference(CompoundTag tag) {
        return new StructureRef(UUID.fromString(tag.getStringOr("id", "")),
                Identifier.parse(tag.getStringOr("dimension", "")),
                Identifier.parse(tag.getStringOr("backend", "")), tag.getLongOr("generation", -1));
    }

    private static CompoundTag writePose(StructurePose pose) {
        var tag = new CompoundTag();
        tag.putDouble("x", pose.worldOrigin().x);
        tag.putDouble("y", pose.worldOrigin().y);
        tag.putDouble("z", pose.worldOrigin().z);
        tag.putDouble("qx", pose.rotationX());
        tag.putDouble("qy", pose.rotationY());
        tag.putDouble("qz", pose.rotationZ());
        tag.putDouble("qw", pose.rotationW());
        return tag;
    }

    private static StructurePose readPose(CompoundTag tag) {
        return new StructurePose(new Vec3(tag.getDoubleOr("x", Double.NaN),
                tag.getDoubleOr("y", Double.NaN), tag.getDoubleOr("z", Double.NaN)),
                tag.getDoubleOr("qx", Double.NaN), tag.getDoubleOr("qy", Double.NaN),
                tag.getDoubleOr("qz", Double.NaN), tag.getDoubleOr("qw", Double.NaN));
    }

    private static void fail(Throwable error) {
        failure = error.toString();
        AcademyCraft.LOGGER.error("Native structure {} smoke failed", MODE, error);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
