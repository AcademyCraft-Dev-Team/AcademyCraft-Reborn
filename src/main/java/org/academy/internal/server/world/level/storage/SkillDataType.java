package org.academy.internal.server.world.level.storage;

import org.academy.internal.common.skilldata.SkillData;

public record SkillDataType<T extends SkillData>(Class<T> clazz) {
}