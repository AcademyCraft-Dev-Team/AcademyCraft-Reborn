package org.academy.internal.common.skilldata;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

public final class HellFlareData extends SkillData {
    public static final Identifier ID = AcademyCraft.academy("hell_flare_data");

    @SerializedName("lockTicks")
    private int lockTicks = 0;

    @SerializedName("phase")
    private int phase = 1;

    @Override
    public Identifier getType() {
        return ID;
    }

    public int getLockTicks() {
        return lockTicks;
    }

    public void setLockTicks(int lockTicks) {
        this.lockTicks = lockTicks;
    }

    public int getPhase() {
        return phase;
    }

    public void setPhase(int phase) {
        this.phase = Math.max(1, Math.min(3, phase));
    }

    public void reset() {
        lockTicks = 0;
        phase = 1;
    }
}
