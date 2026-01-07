package org.academy.internal.server.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.ability.pakcet.SyncSkillDataPacket;
import org.academy.api.common.registries.Registries;
import org.jetbrains.annotations.NotNull;
import org.misaka.MisakaNetworkServer;

import java.util.function.BiConsumer;

public class SkillDataManager implements AbilitySubsystem {

    private final SyncManager syncManager;
    private final PlayerDataManager playerDataManager;

    private BiConsumer<ServerPlayer, Integer> onSkillLevelUp = (player, level) -> {
    };

    public SkillDataManager(PlayerDataManager playerDataManager, SyncManager syncManager) {
        this.syncManager = syncManager;
        this.playerDataManager = playerDataManager;
    }

    @Override
    public void onPlayerLogin(@NotNull ServerPlayer player) {
        syncManager.schedulePlayerSync(player.getUUID(), SyncTypes.SKILL_DATA);
    }

    @Override
    public void processSync(@NotNull ServerPlayer player, @NotNull Identifier type) {
        if (!type.equals(SyncTypes.SKILL_DATA)) return;
        var playerData = playerDataManager.getData(player.getUUID());
        if (playerData == null) return;

        var skills = playerData.getSkillData();
        var packet = new SyncSkillDataPacket(skills);
        MisakaNetworkServer.sendPacket(player, packet);
    }

    public void addSkillExp(ServerPlayer player, String skillKey, ExpEvent event) {
        var playerData = playerDataManager.getData(player.getUUID());
        if (playerData == null) return;

        var skillDataMap = playerData.getSkillData();
        var skillData = skillDataMap.get(skillKey);

        if (skillData == null) return;
        if (skillData.isMaxLevel() && skillData.isMaxExp()) return;

        skillData.setExp(skillData.getExp() + event.getIncrement());

        // 升级
        var isLevelUp = false;
        while (skillData.getExp() >= skillData.getMaxExp() && !skillData.isMaxLevel()) {
            skillData.setExp(skillData.getExp() - skillData.getMaxExp());
            skillData.level++;
            onSkillLevelUp.accept(player, 1);
            isLevelUp = true;
        }

        if (skillData.isMaxLevel()) {
            skillData.setExp(skillData.getMaxExp());
        }

        playerData.markDirty();

        if (isLevelUp) {
            syncManager.schedulePlayerSync(player.getUUID(), SyncTypes.SKILL_DATA);
        }
    }

    public float getSkillExp(ServerPlayer serverPlayer, String skillKey) {
        var uuid = serverPlayer.getUUID();
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return 0.0f;

        var skillData = playerData.getSkillData().get(skillKey);
        if (skillData == null) return 0.0f;

        return skillData.getExp();
    }

    public void addSkill(ServerPlayer serverPlayer, String skillKey) {
        var uuid = serverPlayer.getUUID();
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return;

        var skillReference = Registries.SKILLS.get(Identifier.parse(skillKey)).orElse(null);
        if (skillReference == null) return;

        var skill = skillReference.value();
        var skillData = playerData.getSkillData().putIfAbsent(skillKey, skill.createData(serverPlayer, 0.0f));
        if (skillData == null) {
            playerData.markDirty();
            syncManager.schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);
        }
    }

    public void removeSkill(ServerPlayer serverPlayer, String skillKey) {
        var uuid = serverPlayer.getUUID();
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return;

        var skillData = playerData.getSkillData().remove(skillKey);
        if (skillData == null) return;

        playerData.markDirty();
        syncManager.schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);
    }

    public void setOnSkillLevelUp(BiConsumer<ServerPlayer, Integer> onSkillLevelUp) {
        this.onSkillLevelUp = onSkillLevelUp;
    }

    public enum ExpEvent {
        //击杀生物
        KILL_ENTITY(4.0f),

        //生效时
        ACT_EFFECTIVE(2.0f),

        //维持开启状态
        TICK_ACTIVE(0.2f),

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
