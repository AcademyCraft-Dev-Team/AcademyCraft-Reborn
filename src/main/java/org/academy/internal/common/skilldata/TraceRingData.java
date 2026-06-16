package org.academy.internal.common.skilldata;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

public final class TraceRingData extends SkillData {
    public static final Identifier ID = AcademyCraft.academy("trace_ring_data");

    @SerializedName("elapsedTicks")
    private int elapsedTicks = 0;

    @Override
    public Identifier getType() {
        return ID;
    }

    public int getElapsedTicks() {
        return elapsedTicks;
    }

    public void setElapsedTicks(int elapsedTicks) {
        this.elapsedTicks = elapsedTicks;
    }

    public void incrementTick() {
        elapsedTicks++;
    }

    public void resetTicks() {
        elapsedTicks = 0;
    }
}
