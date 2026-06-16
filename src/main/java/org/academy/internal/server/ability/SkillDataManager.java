package org.academy.internal.server.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.ability.pakcet.SyncSkillDataPacket;
import org.academy.api.common.registries.Registries;
import org.academy.internal.common.skilldata.SkillData;
import org.misaka.MisakaNetworkServer;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SkillDataManager implements AbilitySubsystem {

    private final SyncManager syncManager;
    private final PlayerDataManager playerDataManager;

    private BiConsumer<UUID, Integer> onSkillLevelUp = (uuid, level) -> {
    };

    public SkillDataManager(PlayerDataManager playerDataManager, SyncManager syncManager) {
        this.syncManager = syncManager;
        this.playerDataManager = playerDataManager;
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        syncManager.schedulePlayerSync(player.getUUID(), SyncTypes.SKILL_DATA);
    }

    @Override
    public void processSync(ServerPlayer player) {
        var playerData = playerDataManager.getData(player.getUUID());
        if (playerData == null) return;

        var skills = playerData.getSkillDataMap();
        var packet = new SyncSkillDataPacket(skills);
        MisakaNetworkServer.send(player, packet);
    }

    private void modify(UUID uuid, String skillId, Consumer<SkillData> action) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return;

        var data = playerData.getSkillDataMap().get(skillId);
        if (data == null) return;

        action.accept(data);
        playerData.markDirty();
        syncManager.schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);
    }

    private void query(UUID uuid, String skillId, Consumer<SkillData> action) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return;

        var data = playerData.getSkillDataMap().get(skillId);
        if (data == null) return;

        action.accept(data);
    }

    public int getSkillLevel(UUID uuid, String skillKey) {
        final var result = new int[]{0};
        query(uuid, skillKey, data -> result[0] = data.getLevel());
        return result[0];
    }

    public void addSkillExp(UUID uuid, Skill skill, ExpEvent event) {
        modify(uuid, skill.getKeyString(), skillData -> {
            var maxLevel = skill.getMaxSkillLevel();

            if (skillData.getLevel() >= maxLevel && skillData.isMaxExp()) return;

            skillData.setExp(skillData.getExp() + event.getIncrement());

            while (skillData.getExp() >= skillData.getMaxExp() && skillData.getLevel() < maxLevel) {
                skillData.setExp(skillData.getExp() - skillData.getMaxExp());
                skillData.setLevel(skillData.getLevel() + 1);
                onSkillLevelUp.accept(uuid, 1);
            }

            if (skillData.getLevel() >= maxLevel) {
                skillData.setExp((float) skillData.getMaxExp());
            }
        });
    }

    public float getSkillExp(UUID uuid, String skillKey) {
        final var result = new float[]{0.0f};
        query(uuid, skillKey, data -> result[0] = data.getExp());
        return result[0];
    }

    public void addSkill(ServerPlayer serverPlayer, String skillKey) {
        var uuid = serverPlayer.getUUID();
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return;

        Registries.SKILLS.get(Identifier.parse(skillKey)).ifPresent(skillReference -> {
            var skillData = playerData.getSkillDataMap().putIfAbsent(skillKey, skillReference.value().createData(serverPlayer));
            if (skillData == null) {
                playerData.markDirty();
                syncManager.schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);
            }
        });
    }

    public void removeSkill(UUID uuid, String skillKey) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return;

        var skillData = playerData.getSkillDataMap().remove(skillKey);
        if (skillData == null) return;

        playerData.markDirty();
        syncManager.schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);
    }

    public void toggleSkill(UUID uuid, String skillId) {
        modify(uuid, skillId, SkillData::toggleEnabled);
    }

    public void setOnSkillLevelUp(BiConsumer<UUID, Integer> onSkillLevelUp) {
        this.onSkillLevelUp = onSkillLevelUp;
    }

    public enum ExpEvent {
        //击杀生物
        KILL_ENTITY(8.0f),

        //击中生物
        HIT_ENTITY(4.0f),

        //释放技能
        ACT_EFFECTIVE(2.0f),

        //被动开启
        TICK_PASSIVE(0.01f);

        private final float increment;

        ExpEvent(float increment) {
            this.increment = increment;
        }

        public float getIncrement() {
            return increment;
        }
    }
}
