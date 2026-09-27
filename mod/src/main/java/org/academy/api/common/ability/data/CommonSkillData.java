package org.academy.api.common.ability.data;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

public class CommonSkillData extends SkillData {
    public static final Identifier ID = AcademyCraft.academy("common");

    public CommonSkillData() {
    }

    @Override
    public Identifier getType() {
        return ID;
    }
}
