package org.academy.internal.common.skilldata;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

public class CommonSkillData extends SkillData {
    public static final Identifier ID = AcademyCraft.academy("common");

    public CommonSkillData() {
    }

    public CommonSkillData(float exp) {
        super(exp);
    }

    @Override
    public Identifier getType() {
        return ID;
    }
}