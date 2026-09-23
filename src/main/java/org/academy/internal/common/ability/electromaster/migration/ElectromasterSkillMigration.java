package org.academy.internal.common.ability.electromaster.migration;

import org.academy.internal.common.skilldata.CommonSkillData;
import org.academy.internal.common.skilldata.SkillData;

import java.util.Map;

/** One-time merge; called before hybrid skill modes or MP are initialized. */
public final class ElectromasterSkillMigration {
    public static final String IRON_SAND = "academy:iron_sand_arsenal";
    public static final String SHIELD = "academy:electromagnetic_shield";
    private ElectromasterSkillMigration() {}

    public static boolean merge(Map<String, SkillData> skills) {
        var shield = skills.remove(SHIELD);
        var sand = skills.get(IRON_SAND);
        if (shield == null && sand == null) return false;
        if (sand == null || !(sand instanceof CommonSkillData)) {
            var replacement = new CommonSkillData();
            if (sand != null) replacement.setProficiency(sand.getProficiency());
            sand = replacement;
            skills.put(IRON_SAND, sand);
        }
        if (shield != null) sand.setProficiency(Math.max(sand.getProficiency(), shield.getProficiency()));
        sand.setEnabled(true);
        return true;
    }
}
