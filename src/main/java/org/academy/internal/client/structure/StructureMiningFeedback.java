package org.academy.internal.client.structure;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import org.academy.internal.common.network.PlayerLeftClickSwingPacket;
import org.misaka.MisakaNetworkClient;

/** Vanilla input-event and swing semantics with state-aware effects at the moving block's hit point. */
public final class StructureMiningFeedback {
    private StructureMiningFeedback() {
    }

    public static boolean click(Minecraft minecraft, BlockState state, Vec3 point, boolean sound) {
        var event = ClientHooks.onClickInput(0, minecraft.options.keyAttack, InteractionHand.MAIN_HAND);
        if (event.shouldSwingHand()) {
            minecraft.player.swing(InteractionHand.MAIN_HAND);
            // Minecraft's swing injection is skipped when the structure input consumes startAttack.
            MisakaNetworkClient.send(PlayerLeftClickSwingPacket.INSTANCE);
            if (!state.isAir()) {
                minecraft.particleEngine.createParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        point.x, point.y, point.z, 0.0, 0.02, 0.0);
            }
        }
        if (!event.isCanceled() && sound && !state.isAir()) {
            var type = state.getSoundType(minecraft.level, BlockPos.containing(point), minecraft.player);
            minecraft.level.playLocalSound(point.x, point.y, point.z, type.getHitSound(), SoundSource.BLOCKS,
                    (type.getVolume() + 1.0F) / 8.0F, type.getPitch() * 0.5F, false);
        }
        return !event.isCanceled();
    }
}
