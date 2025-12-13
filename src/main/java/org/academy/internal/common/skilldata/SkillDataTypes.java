package org.academy.internal.common.skilldata;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;
import org.academy.api.common.registries.Registries;
import org.academy.internal.server.world.level.storage.SkillDataType;

public class SkillDataTypes {
    public static final DeferredRegister<SkillDataType<?>> SKILL_DATA_TYPES =
            DeferredRegister.create(Registries.Keys.SKILL_DATA_TYPES, AcademyCraft.MOD_ID);

    public static final DeferredHolder<SkillDataType<?>, SkillDataType<CommonSkillData>> COMMON =
            SKILL_DATA_TYPES.register(CommonSkillData.ID.getPath(), () -> new SkillDataType<>(CommonSkillData.class));
}
