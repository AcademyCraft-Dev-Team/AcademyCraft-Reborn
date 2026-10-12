package org.academy.internal.common.structure.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.academy.AcademyCraft;

import java.util.List;
import java.util.stream.IntStream;

/** Each reserved dimension is a single, bounded structure container, never a player destination. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class NativeStructureDimensions {
    public static final int SLOT_COUNT = 4;
    public static final int MIN_COORDINATE = -32;
    public static final int MAX_COORDINATE_EXCLUSIVE = 32;
    private static final List<ResourceKey<Level>> SLOT_KEYS = IntStream.range(0, SLOT_COUNT)
            .mapToObj(slot -> ResourceKey.create(Registries.DIMENSION,
                    AcademyCraft.academy("structure_space_" + slot)))
            .toList();

    private NativeStructureDimensions() {
    }

    public static ResourceKey<Level> slotKey(int slot) {
        return SLOT_KEYS.get(slot);
    }

    public static boolean isStorageLevel(ResourceKey<Level> dimension) {
        return SLOT_KEYS.contains(dimension);
    }

    /** Y is deliberately checked against the actual level's build height by the caller. */
    public static boolean contains(BlockPos position) {
        return position.getX() >= MIN_COORDINATE && position.getX() < MAX_COORDINATE_EXCLUSIVE
                && position.getZ() >= MIN_COORDINATE && position.getZ() < MAX_COORDINATE_EXCLUSIVE;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (isStorageLevel(event.getDimension())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !isStorageLevel(player.level().dimension())) return;
        // Loading a saved player directly adds it to its level and bypasses the travel event.
        // Only our four reserved keys are recovered; ordinary player data is never inspected or moved.
        try {
            var overworld = player.level().getServer().overworld();
            var respawn = overworld.getRespawnData();
            var position = Vec3.atBottomCenterOf(player.adjustSpawnLocation(overworld, respawn.pos()));
            // The vanilla spawn search may process queued server tasks while waiting for chunks.
            if (player.isRemoved() || !isStorageLevel(player.level().dimension())) return;
            if (player.teleport(new TeleportTransition(overworld, position, Vec3.ZERO,
                    respawn.yaw(), respawn.pitch(), TeleportTransition.PLACE_PORTAL_TICKET)) == null) {
                AcademyCraft.LOGGER.error("Could not recover player {} from a reserved structure dimension",
                        player.getUUID());
            }
        } catch (RuntimeException failure) {
            AcademyCraft.LOGGER.error("Failed to recover player {} from a reserved structure dimension",
                    player.getUUID(), failure);
        }
    }
}
