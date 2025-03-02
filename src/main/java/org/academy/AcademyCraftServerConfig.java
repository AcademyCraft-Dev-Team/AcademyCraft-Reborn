package org.academy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AcademyCraftServerConfig extends AcademyCraftConfig<AcademyCraftServerConfig> {
    @Override
    protected void writeDefaultConfig(AcademyCraftServerConfig academyCraftConfig) {
        Ability ability = academyCraftConfig.getAbility();
        Map<String, List<String>> metalBlocks = ability.getMetalBlocks();
        Map<String, List<String>> metalEntities = ability.getMetalEntities();
        Skill railGun = new Skill();
        railGun.getBooleanMap().put("enabled", true);
        railGun.getBooleanMap().put("destroyBlock", true);
        railGun.getFloatMap().put("damageScale", 1.0f);
        railGun.getFloatMap().put("cpConsumeSpeed", 1.0f);
        railGun.getFloatMap().put("overloadConsumeSpeed", 1.0f);
        railGun.getFloatMap().put("exp_incr_speed", 1.0f);

        List<String> minecraftMetalBlocks = new ArrayList<>();
        minecraftMetalBlocks.add("iron_block");
        minecraftMetalBlocks.add("iron_bars");
        minecraftMetalBlocks.add("iron_trapdoor");
        minecraftMetalBlocks.add("gold_block");
        List<String> academyMetalBlocks = new ArrayList<>();
        academyMetalBlocks.add("machine_frame");
        metalBlocks.put("minecraft", minecraftMetalBlocks);
        metalBlocks.put("academy", academyMetalBlocks);

        List<String> minecraftMetalEntities = new ArrayList<>();
        minecraftMetalEntities.add("villager_golem");
        List<String> academyMetalEntities = new ArrayList<>();
        academyMetalEntities.add("mag_hook");
        metalEntities.put("minecraft", minecraftMetalEntities);
        metalEntities.put("academy", academyMetalEntities);

        ability.getSkills().put("railgun", railGun);
        Generic generic = getGeneric();
        generic.getBooleanMap().put("attackPlayer", true);
        generic.getBooleanMap().put("destroyBlocks", true);
        generic.getBooleanMap().put("genOres", true);
        generic.getBooleanMap().put("genPhaseLiquid", true);
    }
}
