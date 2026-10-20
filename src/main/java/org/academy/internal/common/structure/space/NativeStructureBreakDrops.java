package org.academy.internal.common.structure.space;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructurePose;
import org.academy.mixin.common.EntityNativeStructureDropAccess;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Scoped ownership of real removal outputs. Recovery records are never automatically replayed. */
public final class NativeStructureBreakDrops {
    private static final int MAX_OUTPUTS = 512;
    private static final int MAX_EXPERIENCE = 32_767;
    private static final int MAX_OUTPUT_BYTES = 262_144;
    private static final int MAX_EVIDENCE_BYTES = 900_000;
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private NativeStructureBreakDrops() {}

    public static Scope open(ServerLevel host, ServerLevel storage, StructureHit hit,
                             StructureBlockData before, @Nullable BlockEntity oldBlockEntity) {
        return open(host, storage, hit, before, oldBlockEntity, BlockPos.ZERO,
                new AABB(NativeStructureDimensions.MIN_COORDINATE, storage.getMinY(),
                        NativeStructureDimensions.MIN_COORDINATE, NativeStructureDimensions.MAX_COORDINATE_EXCLUSIVE,
                        storage.getMaxY() + 1.0, NativeStructureDimensions.MAX_COORDINATE_EXCLUSIVE));
    }

    /**
     * The event retains the real storage level/position and the original breaker/tool, like an
     * ordinary plot block. Only the resulting entities are transformed into the visible host.
     */
    public static Scope open(ServerLevel host, ServerLevel storage, StructureHit hit,
                             StructureBlockData before, @Nullable BlockEntity oldBlockEntity,
                             BlockPos storageOrigin, AABB storageBounds) {
        if (!host.getServer().isSameThread() || host.getServer() != storage.getServer()
                || CURRENT.get() != null || !NativeStructureDimensions.isStorageLevel(storage.dimension())
                || NativeStructureDimensions.isStorageLevel(host.dimension())) {
            throw new IllegalStateException("Invalid or nested native mining output scope");
        }
        var scope = new Scope(host, storage, hit, before, oldBlockEntity, storageOrigin, storageBounds);
        CURRENT.set(scope);
        return scope;
    }

    /** Only fresh item/XP entities from the exact scoped storage level are intercepted. */
    public static boolean tryCapture(ServerLevel level, Entity entity) {
        var scope = CURRENT.get();
        if (scope == null || scope.storage != level) return false;
        if (entity instanceof ItemEntity item) scope.guard(() -> scope.captureItem(item));
        else if (entity instanceof ExperienceOrb orb) scope.guard(() -> scope.captureOrb(orb));
        else return false;
        return true;
    }

    /** Intercept before vanilla can merge XP into an existing storage orb. */
    public static boolean tryCaptureExperience(ServerLevel level, Vec3 position, Vec3 direction, int amount) {
        var scope = CURRENT.get();
        if (scope == null || scope.storage != level) return false;
        scope.guard(() -> scope.captureExperience(position, direction, amount));
        return true;
    }

    /** Avoid Block's global, non-finally-protected capturedDrops list only inside this scope. */
    public static boolean tryDropResources(BlockState state, Level level, BlockPos position,
                                           @Nullable BlockEntity blockEntity, @Nullable Entity breaker, ItemStack tool) {
        var scope = CURRENT.get();
        if (scope == null || scope.storage != level) return false;
        scope.guard(() -> scope.dropResources(state, position, blockEntity, breaker, tool));
        return true;
    }

    /** Route scoped resources before Block's process-wide capturedDrops list can claim them. */
    public static boolean tryPopResource(Level level, Supplier<ItemEntity> entityFactory, ItemStack stack) {
        var scope = CURRENT.get();
        if (scope == null || scope.storage != level) return false;
        scope.guard(() -> {
            if (stack.isEmpty() || !scope.storage.getGameRules().get(GameRules.BLOCK_DROPS)
                    || scope.storage.restoringBlockSnapshots) return;
            var entity = entityFactory.get();
            entity.setDefaultPickUpDelay();
            if (scope.preparingLoot != null) {
                // Vanilla also captures resources produced inside loot calculation for its event.
                scope.preparingLoot.entities.add(entity);
                if (scope.preparingLoot.entities.size() > MAX_OUTPUTS) {
                    throw new IllegalStateException("Native loot entity budget exceeded");
                }
            } else scope.captureItem(entity);
        });
        return true;
    }

    public static final class Scope implements AutoCloseable {
        private final ServerLevel host;
        private final ServerLevel storage;
        private final StructureHit hit;
        private final StructurePose pose;
        private final StructureBlockData before;
        private final @Nullable BlockEntity oldBlockEntity;
        private final Vec3 storageOrigin;
        private final AABB storageBounds;
        private final NativeStructureDropLedger<Output> ledger = new NativeStructureDropLedger<>(MAX_OUTPUTS);
        private final IdentityHashMap<Entity, Output> capturedEntities = new IdentityHashMap<>();
        private final List<LootStage> lootStages = new ArrayList<>();
        private final List<ExperienceRequest> experienceRequests = new ArrayList<>();
        private @Nullable LootStage preparingLoot;
        private boolean closed;
        private boolean failed;
        private boolean publishing;
        private boolean completed;
        private long experienceTotal;

        private Scope(ServerLevel host, ServerLevel storage, StructureHit hit,
                      StructureBlockData before, @Nullable BlockEntity oldBlockEntity,
                      BlockPos storageOrigin, AABB storageBounds) {
            this.host = host;
            this.storage = storage;
            this.hit = hit;
            this.pose = hit.pose();
            this.before = before;
            this.oldBlockEntity = oldBlockEntity;
            this.storageOrigin = Vec3.atLowerCornerOf(Objects.requireNonNull(storageOrigin));
            this.storageBounds = Objects.requireNonNull(storageBounds);
        }

        private void requireActive() {
            if (!host.getServer().isSameThread() || CURRENT.get() != this || closed) {
                throw new IllegalStateException("Native mining output scope is not current");
            }
        }

        private void guard(Runnable action) {
            try {
                action.run();
            } catch (RuntimeException | Error failure) {
                failed = true;
                throw failure;
            }
        }

        private void captureItem(ItemEntity item) {
            requireActive();
            if (capturedEntities.containsKey(item)) throw new IllegalStateException("Repeated native item entity output");
            var output = new Output(item, item.position(), item.getDeltaMovement(), 0);
            capturedEntities.put(item, output);
            ledger.capture(output);
            // Capture precedes validation: Containers may already have split the real stack.
            if (publishing || item.getClass() != ItemEntity.class || item.level() != storage
                    || item.isAddedToLevel() || item.isRemoved() || item.isPassenger() || item.isVehicle()) {
                throw new IllegalStateException("Unsupported native item entity output");
            }
            requireLocalPoint(output.position);
            requireFinite(output.velocity);
            requireOutputBudget();
        }

        private void captureExperience(Vec3 position, Vec3 direction, int amount) {
            requireActive();
            if (amount <= 0) return;
            var request = new ExperienceRequest(position, direction, amount);
            experienceRequests.add(request);
            experienceTotal += amount;
            if (publishing || experienceTotal > MAX_EXPERIENCE) throw new IllegalStateException("Native experience output budget exceeded");
            requireLocalPoint(position);
            requireFinite(direction);
            int remaining = amount;
            while (remaining > 0) {
                int value = ExperienceOrb.getExperienceValue(remaining);
                // Transfer this amount to a receipt before checking the receipt count limit.
                request.unassigned -= value;
                ledger.capture(new Output(null, position, direction, value));
                remaining -= value;
            }
            requireOutputBudget();
        }

        private void captureOrb(ExperienceOrb orb) {
            requireActive();
            if (capturedEntities.containsKey(orb)) throw new IllegalStateException("Repeated native experience orb output");
            var output = new Output(orb, orb.position(), orb.getDeltaMovement(), orb.getValue());
            capturedEntities.put(orb, output);
            ledger.capture(output);
            if (publishing || orb.getClass() != ExperienceOrb.class || orb.level() != storage
                    || orb.isAddedToLevel() || orb.isRemoved() || orb.isPassenger() || orb.isVehicle()) {
                throw new IllegalStateException("Unsupported native experience orb output");
            }
            long count = saveEntity(orb).getInt("Count").orElse(1);
            experienceTotal += count * orb.getValue();
            if (count < 1 || orb.getValue() < 1 || experienceTotal > MAX_EXPERIENCE) {
                throw new IllegalStateException("Native experience output budget exceeded");
            }
            requireLocalPoint(output.position);
            requireFinite(output.velocity);
            requireOutputBudget();
        }

        private void dropResources(BlockState state, BlockPos position, @Nullable BlockEntity blockEntity,
                                   @Nullable Entity breaker, ItemStack tool) {
            requireActive();
            if (publishing || lootStages.size() >= MAX_OUTPUTS) throw new IllegalStateException("Nested native loot budget exceeded");
            requireLocalPoint(Vec3.atCenterOf(position));
            var stage = new LootStage(position.immutable());
            lootStages.add(stage);
            var enclosingLoot = preparingLoot;
            preparingLoot = stage;
            try {
                stage.rawItems = Block.getDrops(state, storage, position, blockEntity, breaker, tool);
                if (stage.rawItems.size() > MAX_OUTPUTS) throw new IllegalStateException("Native loot stack budget exceeded");
                for (var stack : stage.rawItems) {
                    // Block.popResource samples the position even when its later gates reject the stack.
                    double halfHeight = EntityTypes.ITEM.getHeight() / 2.0;
                    var random = storage.getRandom();
                    double x = position.getX() + 0.5 + Mth.nextDouble(random, -0.25, 0.25);
                    double y = position.getY() + 0.5 + Mth.nextDouble(random, -0.25, 0.25) - halfHeight;
                    double z = position.getZ() + 0.5 + Mth.nextDouble(random, -0.25, 0.25);
                    if (stack.isEmpty() || !storage.getGameRules().get(GameRules.BLOCK_DROPS)
                            || storage.restoringBlockSnapshots) continue;
                    var item = new ItemEntity(storage, x, y, z, stack);
                    item.setDefaultPickUpDelay();
                    stage.entities.add(item);
                    if (stage.entities.size() > MAX_OUTPUTS) throw new IllegalStateException("Native loot entity budget exceeded");
                }
            } finally {
                preparingLoot = enclosingLoot;
            }
            stage.phase = "event";
            var event = new BlockDropsEvent(storage, position, state, blockEntity, stage.entities, breaker, tool);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCanceled()) {
                stage.phase = "canceled";
                return;
            }
            stage.phase = "accepted";
            for (var entity : stage.entities) captureItem(entity);
            state.spawnAfterBreak(storage, position, tool, false);
            if (event.getDroppedExperience() > 0) state.getBlock().popExperience(storage, position, event.getDroppedExperience());
            stage.phase = "captured";
        }

        /** Publish once, after the block and vanilla loot callbacks have completed. */
        public void publish() {
            requireActive();
            if (failed) throw new IllegalStateException("Native drop scope cannot publish after an intercepted failure");
            requireOutputBudget();
            recoveryEvidence(); // Reject an unpersistable journal before publishing any output.
            publishing = true;
            ledger.publish(output -> {
                if (failed) throw new IllegalStateException("Native drop scope failed during publication callbacks");
                var world = pose.localToWorldPoint(output.position.subtract(storageOrigin));
                requireHostPoint(world);
                Entity entity = output.entity;
                if (entity == null) {
                    // Standard vanilla orb and denominations, with later normal pickup/merging.
                    // Avoid awardWithDirection's partially successful immediate merge loop.
                    entity = new ExperienceOrb(host, world, pose.localToWorldDirection(output.velocity), output.experience);
                    entity.setUUID(output.id);
                    output.entity = entity;
                } else {
                    if (entity.level() != storage || entity.isAddedToLevel() || entity.isRemoved()) {
                        throw new IllegalStateException("Native item output ownership changed before publication");
                    }
                    ((EntityNativeStructureDropAccess) entity).academy$setNativeDropLevel(host);
                    entity.setPos(world);
                    entity.setDeltaMovement(pose.localToWorldDirection(output.velocity));
                }
                if (entity.isAddedToLevel() || entity.isRemoved()) throw new IllegalStateException("Native drop was already published or removed");
                return host.addFreshEntity(entity);
            });
            if (failed) throw new IllegalStateException("Native drop scope failed during publication callbacks");
            completed = true;
        }

        private void requireHostPoint(Vec3 point) {
            requireFinite(point);
            var center = BlockPos.containing(point);
            if (host.getServer().getLevel(host.dimension()) != host || host.isOutsideBuildHeight(center)
                    || !host.getWorldBorder().isWithinBounds(center)) throw new IllegalStateException("Native drop host is unavailable");
            // Constructors and ordinary orb unsticking may query the immediate neighbourhood.
            for (var cell : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
                if (!host.hasChunkAt(cell)) throw new IllegalStateException("Native drop host chunk is unloaded");
            }
        }

        private void requireLocalPoint(Vec3 point) {
            requireFinite(point);
            var cell = BlockPos.containing(point);
            if (!storageBounds.contains(point) || storage.isOutsideBuildHeight(cell)
                    || !storage.hasChunkAt(cell)) throw new IllegalStateException("Native drop left its loaded storage domain");
        }

        private void requireOutputBudget() {
            long bytes = 0;
            for (var entry : ledger.entries()) {
                var entity = entry.value().entity;
                if (entity != null) bytes += saveEntity(entity).sizeInBytes();
                else bytes += 128;
                if (bytes > MAX_OUTPUT_BYTES) throw new IllegalStateException("Native drop encoded output budget exceeded");
            }
        }

        /**
         * Versioned, diagnostic-only evidence. Outputs are the owned receipts; before_cell and
         * raw_loot are observations, never a full-container restoration instruction. If encoding
         * exceeds its budget this throws so the runtime retains this supplier and all live objects.
         */
        public CompoundTag recoveryEvidence() {
            if (!host.getServer().isSameThread()) throw new IllegalStateException("Native drop evidence requires the server thread");
            var result = new CompoundTag();
            result.putString("schema", "native_mining_drops_v1");
            result.putBoolean("manual_recovery_only", true);
            result.putString("structure_id", hit.block().structure().id().toString());
            result.putString("runtime_id", hit.runtimeId().toString());
            result.putString("host_dimension", host.dimension().identifier().toString());
            result.put("storage_origin", vector(storageOrigin));
            result.putLong("local_position", hit.block().localPosition().asLong());
            result.putBoolean("completed", completed);
            result.putBoolean("failed", failed);
            var original = new CompoundTag();
            original.putInt("block_state", Block.getId(before.state()));
            if (before.blockEntityData() != null) original.put("block_entity", Objects.requireNonNull(before.blockEntityData()));
            result.put("before_cell_observation_do_not_restore", original);
            if (oldBlockEntity != null) {
                var reporter = new ProblemReporter.Collector();
                var output = TagValueOutput.createWithContext(reporter, storage.registryAccess());
                oldBlockEntity.saveWithFullMetadata(output);
                if (!reporter.isEmpty()) throw new IllegalStateException("Cannot encode remaining native block entity: " + reporter.getReport());
                result.put("remaining_block_entity", output.buildResult());
            }
            var outputs = new ListTag();
            for (var entry : ledger.entries()) {
                var value = entry.value();
                var row = new CompoundTag();
                row.putString("kind", value.experience == 0 ? "item" : "experience");
                row.putString("uuid", value.id.toString());
                row.putString("status", entry.state().name());
                row.putInt("experience", value.experience);
                row.put("local_position", vector(value.position));
                row.put("local_velocity", vector(value.velocity));
                if (value.entity != null) row.put("entity", saveEntity(value.entity));
                outputs.add(row);
            }
            result.put("outputs", outputs);
            var outstandingExperience = new ListTag();
            for (var request : experienceRequests) if (request.unassigned != 0) {
                var row = new CompoundTag();
                row.putInt("amount", request.unassigned);
                row.put("local_position", vector(request.position));
                row.put("local_direction", vector(request.direction));
                outstandingExperience.add(row);
            }
            result.put("unassigned_experience", outstandingExperience);
            var unsubmittedLoot = new ListTag();
            for (var stage : lootStages) {
                if (stage.phase.equals("canceled") || stage.phase.equals("captured")) continue;
                var row = new CompoundTag();
                row.putString("phase", stage.phase);
                row.putLong("local_position", stage.position.asLong());
                var items = new ListTag();
                for (var entity : stage.entities) if (!capturedEntities.containsKey(entity)) items.add(saveEntity(entity));
                row.put("unsubmitted_entities", items);
                var reporter = new ProblemReporter.Collector();
                var raw = TagValueOutput.createWithContext(reporter, storage.registryAccess());
                raw.store("items", ItemStack.OPTIONAL_CODEC.listOf(), stage.rawItems);
                if (!reporter.isEmpty()) throw new IllegalStateException("Cannot encode unfinished native loot: " + reporter.getReport());
                row.put("raw_loot_observation_do_not_replay", raw.buildResult());
                unsubmittedLoot.add(row);
            }
            result.put("unfinished_loot_stages", unsubmittedLoot);
            if (result.sizeInBytes() < 0 || result.sizeInBytes() > MAX_EVIDENCE_BYTES) {
                throw new IllegalStateException("Native mining evidence exceeds the durable journal budget");
            }
            return result;
        }

        private CompoundTag saveEntity(Entity entity) {
            var reporter = new ProblemReporter.Collector();
            var output = TagValueOutput.createWithContext(reporter, storage.registryAccess());
            output.putString("id", entity.typeHolder().getRegisteredName());
            entity.saveWithoutId(output);
            if (!reporter.isEmpty()) throw new IllegalStateException("Cannot encode native output: " + reporter.getReport());
            return output.buildResult();
        }

        @Override public void close() {
            if (closed) return;
            if (CURRENT.get() != this) throw new IllegalStateException("Native drop scope stack was changed");
            CURRENT.remove();
            closed = true;
            // Failed receipts remain strongly owned by the runtime's recovery supplier.
        }
    }

    private static final class Output {
        private final UUID id;
        private @Nullable Entity entity;
        private final Vec3 position;
        private final Vec3 velocity;
        private final int experience;

        private Output(@Nullable Entity entity, Vec3 position, Vec3 velocity, int experience) {
            this.entity = entity;
            this.id = entity == null ? UUID.randomUUID() : entity.getUUID();
            this.position = position;
            this.velocity = velocity;
            this.experience = experience;
        }
    }

    private static final class LootStage {
        private final BlockPos position;
        private final List<ItemEntity> entities = new ArrayList<>();
        private List<ItemStack> rawItems = List.of();
        private String phase = "calculating";
        private LootStage(BlockPos position) { this.position = position; }
    }

    private static final class ExperienceRequest {
        private final Vec3 position;
        private final Vec3 direction;
        private int unassigned;
        private ExperienceRequest(Vec3 position, Vec3 direction, int amount) {
            this.position = position; this.direction = direction; this.unassigned = amount;
        }
    }

    private static CompoundTag vector(Vec3 point) {
        var result = new CompoundTag();
        result.putDouble("x", point.x); result.putDouble("y", point.y); result.putDouble("z", point.z);
        return result;
    }

    private static void requireFinite(Vec3 point) {
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) {
            throw new IllegalStateException("Non-finite native drop coordinates");
        }
    }
}
