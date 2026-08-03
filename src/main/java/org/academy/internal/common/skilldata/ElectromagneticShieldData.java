package org.academy.internal.common.skilldata;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

public final class ElectromagneticShieldData extends SkillData {
    public static final Identifier ID = AcademyCraft.academy("electromagnetic_shield_data");

    @SerializedName("absorbedDamage")
    private float absorbedDamage;

    @SerializedName("capacity")
    private float capacity = 100.0f;

    @Override
    public Identifier getType() {
        return ID;
    }

    public float getAbsorbedDamage() {
        return absorbedDamage;
    }

    public void setAbsorbedDamage(float absorbedDamage) {
        this.absorbedDamage = Float.isFinite(absorbedDamage)
                ? Math.max(0, absorbedDamage)
                : 0;
    }

    public float getCapacity() {
        return capacity;
    }

    public void setCapacity(float capacity) {
        this.capacity = Float.isFinite(capacity) ? Math.max(0, capacity) : 0;
        absorbedDamage = Math.min(absorbedDamage, this.capacity);
    }
}
