package org.academy.internal.common.ability.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.academy.AcademyCraft;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class AreaTeleportState {
    public static final int MAX_REGION_SIZE = 40;
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private AreaTeleportState() {
    }

    public static void setFirstCorner(UUID player, ResourceKey<Level> dimension, BlockPos corner) {
        var state = STATES.computeIfAbsent(player, ignored -> new State());
        state.dimension = dimension;
        state.pending = corner.immutable();
        state.selected = null;
        state.destinationDimension = null;
        state.destination = null;
        state.transform = Transform.IDENTITY;
        state.swap = false;
    }

    public static Region complete(UUID player, ResourceKey<Level> dimension, BlockPos second) {
        return complete(player, dimension, second, MAX_REGION_SIZE);
    }

    public static Region complete(UUID player, ResourceKey<Level> dimension, BlockPos second, int maxAxis) {
        var state = STATES.get(player);
        if (state == null || state.pending == null || !state.dimension.equals(dimension)) return null;
        state.selected = clamp(dimension, state.pending, second, Math.clamp(maxAxis, 1, MAX_REGION_SIZE));
        state.pending = null;
        return state.selected;
    }

    public static void setDestination(UUID player, ResourceKey<Level> dimension, BlockPos destination) {
        var state = STATES.computeIfAbsent(player, ignored -> new State());
        state.destinationDimension = dimension;
        state.destination = destination.immutable();
    }

    public static Transform cycleTransform(UUID player, boolean allowMirror) {
        var state = STATES.computeIfAbsent(player, ignored -> new State());
        state.transform = state.transform.next(allowMirror);
        return state.transform;
    }

    public static Transform transform(UUID player) {
        var state = STATES.get(player);
        return state == null ? Transform.IDENTITY : state.transform;
    }

    public static boolean toggleSwap(UUID player) {
        var state = STATES.computeIfAbsent(player, ignored -> new State());
        state.swap = !state.swap;
        return state.swap;
    }

    public static boolean swap(UUID player) {
        var state = STATES.get(player);
        return state != null && state.swap;
    }

    public static Snapshot snapshot(UUID player) {
        var state = STATES.get(player);
        if (state == null) return new Snapshot(null, null, null, null);
        return new Snapshot(state.dimension, state.pending, state.selected, destinationRegion(state));
    }

    public static Region selected(UUID player) {
        var state = STATES.get(player);
        return state == null ? null : state.selected;
    }

    public static Region destination(UUID player) {
        var state = STATES.get(player);
        return state == null ? null : destinationRegion(state);
    }

    public static boolean hasPending(UUID player) {
        var state = STATES.get(player);
        return state != null && state.pending != null;
    }

    public static void clear(UUID player) {
        STATES.remove(player);
    }

    private static Region destinationRegion(State state) {
        if (state.selected == null || state.destination == null || state.destinationDimension == null) return null;
        var source = state.selected;
        var swapAxes = state.transform == Transform.ROTATE_90 || state.transform == Transform.ROTATE_270;
        var sizeX = swapAxes ? source.sizeZ() : source.sizeX();
        var sizeZ = swapAxes ? source.sizeX() : source.sizeZ();
        var max = state.destination.offset(sizeX - 1, source.sizeY() - 1, sizeZ - 1);
        return new Region(state.destinationDimension, state.destination, max);
    }

    private static Region clamp(ResourceKey<Level> dimension, BlockPos first, BlockPos second, int maxAxis) {
        var minX = Math.min(first.getX(), second.getX());
        var minY = Math.min(first.getY(), second.getY());
        var minZ = Math.min(first.getZ(), second.getZ());
        var maxX = Math.min(Math.max(first.getX(), second.getX()), minX + maxAxis - 1);
        var maxY = Math.min(Math.max(first.getY(), second.getY()), minY + maxAxis - 1);
        var maxZ = Math.min(Math.max(first.getZ(), second.getZ()), minZ + maxAxis - 1);
        return new Region(dimension, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clear(event.getEntity().getUUID());
    }

    public record Region(ResourceKey<Level> dimension, BlockPos min, BlockPos max) {
        public int sizeX() { return max.getX() - min.getX() + 1; }
        public int sizeY() { return max.getY() - min.getY() + 1; }
        public int sizeZ() { return max.getZ() - min.getZ() + 1; }
        public long volume() { return (long) sizeX() * sizeY() * sizeZ(); }
        public boolean withinLimit() {
            return withinLimit(MAX_REGION_SIZE);
        }
        public boolean withinLimit(int maxAxis) {
            return sizeX() > 0 && sizeY() > 0 && sizeZ() > 0
                    && sizeX() <= maxAxis && sizeY() <= maxAxis && sizeZ() <= maxAxis;
        }
        public AABB box() {
            return new AABB(min.getX(), min.getY(), min.getZ(),
                    max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
        }
    }

    public record Snapshot(ResourceKey<Level> dimension, BlockPos pending, Region selected, Region destination) {
    }

    public enum Transform {
        IDENTITY,
        ROTATE_90,
        ROTATE_180,
        ROTATE_270,
        MIRROR_X,
        MIRROR_Z;

        public Transform next(boolean allowMirror) {
            return switch (this) {
                case IDENTITY -> ROTATE_90;
                case ROTATE_90 -> ROTATE_180;
                case ROTATE_180 -> ROTATE_270;
                case ROTATE_270 -> allowMirror ? MIRROR_X : IDENTITY;
                case MIRROR_X -> MIRROR_Z;
                case MIRROR_Z -> IDENTITY;
            };
        }

        public Transform inverse() {
            return switch (this) {
                case ROTATE_90 -> ROTATE_270;
                case ROTATE_270 -> ROTATE_90;
                default -> this;
            };
        }
    }

    private static final class State {
        private ResourceKey<Level> dimension;
        private BlockPos pending;
        private Region selected;
        private ResourceKey<Level> destinationDimension;
        private BlockPos destination;
        private Transform transform = Transform.IDENTITY;
        private boolean swap;
    }
}
