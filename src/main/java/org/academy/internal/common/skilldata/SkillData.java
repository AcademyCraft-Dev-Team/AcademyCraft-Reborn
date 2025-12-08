package org.academy.internal.common.skilldata;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;

public abstract class SkillData {
        @SerializedName("exp")
        public float exp;

        public SkillData() {
            this.exp = 0;
        }

        public SkillData(float exp) {
            this.exp = exp;
        }

        public float getExp() {
            return exp;
        }

        public void setExp(float exp) {
            this.exp = exp;
        }

        public abstract Identifier getType();
    }