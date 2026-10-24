package org.academy.internal.common.structure.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.internal.common.structure.transaction.NativeCaptureWhitelist;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/** State-only terrestrial additions supported by Sable's real plot block-copy path. */
final class SableCaptureAdmission {
    private SableCaptureAdmission() {}

    private static final class Terrain {
        // No multi-cell plants, fluids, block entities or arbitrary partial mod blocks.
        private static final Set<Block> BLOCKS = Set.of(Blocks.SHORT_GRASS, Blocks.FERN, Blocks.DEAD_BUSH,
                Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS,
                Blocks.FARMLAND, Blocks.DIRT_PATH);
    }

    static @Nullable String rejectionDetail(ServerLevel host, BlockPos position, BlockState state,
                                             @Nullable BlockEntity blockEntity) {
        return allowTerrain(NativeCaptureWhitelist.rejectionDetail(host, position, state, blockEntity),
                Terrain.BLOCKS.contains(state.getBlock()), state.getDestroySpeed(host, position));
    }

    /** Only the curated-set/full-cube restrictions are extended; stronger rejections survive. */
    static @Nullable String allowTerrain(@Nullable String previous, boolean supportedTerrain, float destroySpeed) {
        if (!supportedTerrain || !("not_in_plain_block_whitelist".equals(previous)
                || "not_full_block".equals(previous))) return previous;
        // The old whitelist checks hardness after its content/shape gate. Do not skip that
        // final restriction when accepting a specifically supported terrain cell here.
        return destroySpeed < 0 ? "unbreakable" : null;
    }

    static boolean seedable(boolean collision, double mass) {
        return collision && Double.isFinite(mass) && mass > 0;
    }
}
