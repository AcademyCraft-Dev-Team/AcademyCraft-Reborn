package org.academy.internal.common.skilldata;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

public final class BioelectricSurgeData extends SkillData {
    public static final Identifier ID = AcademyCraft.academy("bioelectric_surge_data");

    @SerializedName("accumulatedActiveTicks")
    private int accumulatedActiveTicks = 0;

    @Override
    public Identifier getType() {
        return ID;
    }

    public int getAccumulatedActiveTicks() {
        return accumulatedActiveTicks;
    }

    public void setAccumulatedActiveTicks(int accumulatedActiveTicks) {
        this.accumulatedActiveTicks = accumulatedActiveTicks;
    }

    public void incrementActiveTick() {
        accumulatedActiveTicks++;
    }

    public void resetActiveTicks() {
        accumulatedActiveTicks = 0;
    }
}
