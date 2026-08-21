package org.academy.internal.common.skilldata;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.academy.AcademyCraft;

public final class OutputControlData extends SkillData {
    public static final Identifier ID = AcademyCraft.academy("output_control_data");

    @SerializedName("abilityOutput")
    private float abilityOutput = 1.0f;

    @SerializedName("movementSpeed")
    private float movementSpeed = 1.0f;

    @SerializedName("jumpHeight")
    private float jumpHeight = 1.0f;

    @Override
    public Identifier getType() {
        return ID;
    }

    public float getAbilityOutput() {
        abilityOutput = clamp(abilityOutput, 0.0f, 2.0f, 1.0f);
        return abilityOutput;
    }

    public void setAbilityOutput(float abilityOutput) {
        this.abilityOutput = clamp(abilityOutput, 0.0f, 2.0f, 1.0f);
    }

    public float getMovementSpeed() {
        movementSpeed = clamp(movementSpeed, 0.0f, 1.0f, 1.0f);
        return movementSpeed;
    }

    public void setMovementSpeed(float movementSpeed) {
        this.movementSpeed = clamp(movementSpeed, 0.0f, 1.0f, 1.0f);
    }

    public float getJumpHeight() {
        jumpHeight = clamp(jumpHeight, 0.0f, 1.0f, 1.0f);
        return jumpHeight;
    }

    public void setJumpHeight(float jumpHeight) {
        this.jumpHeight = clamp(jumpHeight, 0.0f, 1.0f, 1.0f);
    }

    private static float clamp(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Mth.clamp(value, min, max) : fallback;
    }
}
