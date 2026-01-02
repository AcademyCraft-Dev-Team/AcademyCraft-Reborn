package org.academy.api.client.util;

import net.minecraft.server.level.ServerLevel;
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

    public static void quantumHealthFluctuation(LivingEntity self) {
        if (!(self.level() instanceof ServerLevel level)) return;

        var data = self.getData(AttachmentTypes.QUANTUM_DATA.get());
        if (!data.active()) return;

        if (updateQuantumDuration(self, data)) {
            if (self.tickCount % 20 == 0) applyHealthFluctuation(self, level);
        }
    }

    private static boolean updateQuantumDuration(LivingEntity self, QuantumData data) {
        var newDuration = data.duration() - 1;

        if (newDuration <= 0) {
            disableQuantum(self);
            return false;
        }

        self.setData(AttachmentTypes.QUANTUM_DATA.get(),
                new QuantumData(true, data.intensity(), data.color(), newDuration));
        return true;
    }

    private static void applyHealthFluctuation(LivingEntity self, ServerLevel level) {
        var maxHealth = self.getMaxHealth();
        var currentHealth = self.getHealth();

        if (currentHealth <= 2.0f) {
            return;
        }

        var amplitude = Math.max(maxHealth * 0.05f, 1.0f);
        var randomFactor = self.getRandom().nextFloat();
        var change = (randomFactor - 0.75f) * amplitude - 0.2f;

        var nextHealth = currentHealth + change;
        if (change < 0) {
            if (nextHealth < 1.0f) return;
            self.setHealth(nextHealth);
        } else if (change > 0) {
            self.setHealth(Math.min(nextHealth, maxHealth));
        }
    }
}