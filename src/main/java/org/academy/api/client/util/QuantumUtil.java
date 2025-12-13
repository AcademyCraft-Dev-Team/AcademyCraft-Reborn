package org.academy.api.client.util;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.client.renderer.entity.layers.quantum.QuantumData;
import org.academy.internal.client.renderer.entity.layers.quantum.QuantumSyncPayload;

//生物量子化特效开关
public class QuantumUtil {

    public static void enableQuantum(LivingEntity target, float intensity, int color) {
        var data = new QuantumData(true, intensity, color, 600);

        target.setData(AttachmentTypes.QUANTUM_DATA.get(), data);

        //因为需要同步到所有追踪玩家，MisakaNetwork没有这个方法，所以用了NeoForge的PacketDistributor
        if (!target.level().isClientSide()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    target,
                    new QuantumSyncPayload(target.getId(), data)
            );
        }
    }

    public static void disableQuantum(LivingEntity target) {
        var defaultData = QuantumData.getDefault();
        target.setData(AttachmentTypes.QUANTUM_DATA.get(), defaultData);

        if (!target.level().isClientSide()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    target,
                    new QuantumSyncPayload(target.getId(), defaultData)
            );
        }
    }
}