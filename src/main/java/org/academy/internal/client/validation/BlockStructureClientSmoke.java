package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.BlockStructureApi;
import org.academy.api.common.structure.BlockStructureCaptureOptions;
import org.academy.api.common.structure.BlockStructureSettlementResult;
import org.academy.internal.common.world.entity.structure.BlockStructureEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Opt-in network/render smoke test, restricted to a dedicated disposable game directory. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class BlockStructureClientSmoke {
    private static final int QUIET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
    private static boolean started;
    private static boolean restoreRequested;
    private static boolean finished;
    private static int waited;
    private static int synchronizedTicks;
    private static int restoredTicks;
    private static volatile boolean serverReady;
    private static volatile boolean serverRestored;
    private static volatile int entityId = -1;
    private static volatile UUID structureId;
    private static volatile BlockPos sourcePosition;
    private static volatile CompoundTag expectedBlockEntityData;
    private static volatile String failure;

    private BlockStructureClientSmoke() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.blockStructureSmoke") || finished) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        var server = minecraft.getSingleplayerServer();
        require(server != null, "Smoke test requires an isolated integrated server");
        if (!started) {
            verifyIsolatedWorld(minecraft);
            started = true;
            var playerId = minecraft.player.getUUID();
            server.execute(() -> {
                try {
                    var player = server.getPlayerList().getPlayer(playerId);
                    require(player != null, "Integrated player is absent");
                    var level = player.level();
                    var source = findEmptyCell(level, player.blockPosition());
                    sourcePosition = source;
                    require(level.setBlock(source, Blocks.CHEST.defaultBlockState(), QUIET_FLAGS),
                            "Could not place the test chest");
                    require(level.getBlockEntity(source) instanceof ChestBlockEntity,
                            "Test chest has no block entity");
                    var chest = (ChestBlockEntity) level.getBlockEntity(source);
                    chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
                    chest.setChanged();
                    expectedBlockEntityData = chest.saveWithFullMetadata(level.registryAccess());
                    var captured = BlockStructureApi.capture(level, List.of(source),
                            BlockStructureCaptureOptions.defaults().withGravity(false));
                    require(captured.succeeded(), "Capture failed: " + captured.status());
                    var structure = captured.structure().orElseThrow();
                    require(level.getBlockState(source).isAir(), "Capture left the source chest");
                    assertNoDrops(level, source);
                    require(level.setBlock(source, Blocks.STONE.defaultBlockState(), QUIET_FLAGS),
                            "Could not place the settlement blocker");
                    var retained = structure.settleToGrid();
                    require(retained.status() == BlockStructureSettlementResult.Status.RETAINED
                                    && retained.placedBlocks() == 0 && retained.droppedBlocks() == 0
                                    && !structure.asEntity().isRemoved(),
                            "Blocked settlement did not retain the entire structure: " + retained);
                    require(structure.snapshot().blocks().getFirst().blockEntityData().orElseThrow()
                                    .equals(expectedBlockEntityData),
                            "Retained structure changed the chest payload");
                    assertNoDrops(level, source);
                    entityId = structure.asEntity().getId();
                    structureId = structure.asEntity().getUUID();
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(source));
                    AcademyCraft.getLogger().info(
                            "BLOCK_STRUCTURE_SMOKE_SERVER_RETAINED uuid={} position={} diamonds=7",
                            structureId, source);
                    serverReady = true;
                } catch (Throwable error) {
                    failOnServer(error);
                }
            });
        }
        require(failure == null, "Server smoke failed: " + failure);
        require(++waited <= 1_000, "Client synchronization timed out");
        if (!serverReady) return;

        if (!restoreRequested) {
            var candidate = minecraft.level.getEntity(entityId);
            if (!(candidate instanceof BlockStructureEntity structure)) return;
            require(structure.getUUID().equals(structureId), "Structure UUID changed during synchronization");
            // Spawn data, the pending flag, pose and world block updates may arrive in different ticks.
            // Require a stable complete view below; the global timeout still rejects missing synchronization.
            if (structure.isTransferPending()
                    || structure.position().distanceToSqr(Vec3.atLowerCornerOf(sourcePosition)) > 1.0e-6) {
                synchronizedTicks = 0;
                return;
            }
            var snapshot = structure.snapshot();
            if (snapshot.isEmpty()) return;
            require(snapshot.blockCount() == 1 && snapshot.blocks().getFirst().state().is(Blocks.CHEST),
                    "Client received an incorrect structure snapshot");
            require(snapshot.blocks().getFirst().blockEntityData().orElseThrow()
                            .equals(expectedBlockEntityData),
                    "Client did not receive the complete retained chest payload");
            var collisionShapes = new ArrayList<VoxelShape>();
            structure.collectCollisionShapes(new AABB(sourcePosition).inflate(0.1), collisionShapes::add);
            if (collisionShapes.stream().noneMatch(shape -> !shape.isEmpty())) {
                synchronizedTicks = 0;
                return;
            }
            require(minecraft.getEntityRenderDispatcher().getRenderer(structure) != null,
                    "Client structure has no registered renderer");
            if (!minecraft.level.getBlockState(sourcePosition).is(Blocks.STONE)) return;
            if (++synchronizedTicks < 20) return;
            AcademyCraft.getLogger().info(
                    "BLOCK_STRUCTURE_SMOKE_CLIENT_SNAPSHOT_OK uuid={} blocks=1 collision_shapes={}",
                    structureId, collisionShapes.size());
            restoreRequested = true;
            var dimension = minecraft.level.dimension();
            server.execute(() -> {
                try {
                    var level = server.getLevel(dimension);
                    require(level != null, "Integrated dimension disappeared");
                    require(level.getEntity(structureId) instanceof BlockStructureEntity,
                            "Retained server structure disappeared before restoration");
                    var serverStructure = (BlockStructureEntity) level.getEntity(structureId);
                    require(level.getBlockState(sourcePosition).is(Blocks.STONE),
                            "Settlement blocker changed unexpectedly");
                    require(level.setBlock(sourcePosition, Blocks.AIR.defaultBlockState(), QUIET_FLAGS),
                            "Could not remove the settlement blocker");
                    var restored = serverStructure.restoreToGrid();
                    require(restored.succeeded() && restored.affectedBlocks() == 1
                                    && serverStructure.isRemoved(),
                            "Restoration did not complete: " + restored);
                    require(level.getBlockEntity(sourcePosition) instanceof ChestBlockEntity,
                            "Restoration did not recreate the chest block entity");
                    var chest = (ChestBlockEntity) level.getBlockEntity(sourcePosition);
                    require(chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 7,
                            "Restoration lost the seven diamonds");
                    for (var slot = 1; slot < chest.getContainerSize(); slot++) {
                        require(chest.getItem(slot).isEmpty(), "Restoration duplicated inventory contents");
                    }
                    assertNoDrops(level, sourcePosition);
                    AcademyCraft.getLogger().info(
                            "BLOCK_STRUCTURE_SMOKE_SERVER_RESTORED uuid={} diamonds=7 drops=0", structureId);
                    serverRestored = true;
                } catch (Throwable error) {
                    failOnServer(error);
                }
            });
            return;
        }

        if (!serverRestored || minecraft.level.getEntity(entityId) != null) return;
        if (!minecraft.level.getBlockState(sourcePosition).is(Blocks.CHEST)
                || !(minecraft.level.getBlockEntity(sourcePosition) instanceof ChestBlockEntity chest)) return;
        require(minecraft.getBlockEntityRenderDispatcher().getRenderer(chest) != null,
                "Restored client chest has no block-entity renderer");
        require(minecraft.level.getEntitiesOfClass(ItemEntity.class, new AABB(sourcePosition).inflate(1)).isEmpty(),
                "Restoration left client-visible item drops");
        if (++restoredTicks < 20) return;
        AcademyCraft.getLogger().info(
                "BLOCK_STRUCTURE_CLIENT_SMOKE_PASSED uuid={} retained=true snapshot=true collision=true "
                        + "server_diamonds=7 entity_removed=true restored_chest_renderer=true drops=0",
                structureId);
        finished = true;
        minecraft.stop();
    }

    private static void verifyIsolatedWorld(Minecraft minecraft) {
        try {
            var directory = minecraft.gameDirectory.toPath().toRealPath();
            require(directory.getFileName().toString().startsWith("block-structure-client-smoke"),
                    "Use a dedicated game directory named block-structure-client-smoke or with that prefix");
            var server = minecraft.getSingleplayerServer();
            require(server != null && server.getWorldPath(LevelResource.ROOT).toRealPath()
                            .startsWith(directory.resolve("saves").toRealPath()),
                    "Integrated world is outside the dedicated smoke directory");
        } catch (IOException error) {
            throw new IllegalStateException("Could not verify the isolated smoke world", error);
        }
    }

    private static BlockPos findEmptyCell(ServerLevel level, BlockPos player) {
        for (var offset : List.of(new BlockPos(4, 2, 0), new BlockPos(-4, 2, 0),
                new BlockPos(0, 2, 4), new BlockPos(0, 2, -4))) {
            var candidate = player.offset(offset);
            if (level.hasChunkAt(candidate) && level.isInWorldBounds(candidate)
                    && level.getBlockState(candidate).isAir()
                    && level.getBlockEntity(candidate) == null
                    && level.getEntitiesOfClass(ItemEntity.class, new AABB(candidate).inflate(1)).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("No empty loaded cell near the player for the smoke fixture");
    }

    private static void assertNoDrops(ServerLevel level, BlockPos position) {
        require(level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(1)).isEmpty(),
                "Container transfer created item drops");
    }

    private static void failOnServer(Throwable error) {
        AcademyCraft.getLogger().error("BLOCK_STRUCTURE_SMOKE_FAILED", error);
        failure = error.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Block-structure client smoke: " + message);
    }
}
