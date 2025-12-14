package org.academy.api.client.util;

import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.client.renderer.entity.layers.quantum.QuantumData;
import org.academy.internal.common.attachment.AttachmentTypes;

public class QuantumUtil {
    public static void enableQuantum(LivingEntity target, float intensity, int color) {
        var data = new QuantumData(true, intensity, color, 600);
        target.setData(AttachmentTypes.QUANTUM_DATA.get(), data);
    }

    public static void disableQuantum(LivingEntity target) {
        target.removeData(AttachmentTypes.QUANTUM_DATA.get());
    }
}