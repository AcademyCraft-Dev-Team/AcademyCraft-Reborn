package org.academy.internal.common.advancement;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;

import java.util.List;

public final class AbilityAdvancements {
    public static final String CRITERION = "achieved";
    public static final Identifier ROOT = AcademyCraft.academy("ability/root");
    public static final Identifier ACCELERATOR = AcademyCraft.academy("ability/accelerator");
    public static final Identifier DARKMATTER = AcademyCraft.academy("ability/darkmatter");
    public static final Identifier TELEPORT = AcademyCraft.academy("ability/teleport");
    public static final Identifier MELTDOWNER = AcademyCraft.academy("ability/meltdowner");
    public static final Identifier MENTALOUT = AcademyCraft.academy("ability/mentalout");
    public static final Identifier AEROMANIP = AcademyCraft.academy("ability/aeromanip");
    public static final Identifier ELECTROMASTER = AcademyCraft.academy("ability/electromaster");
    public static final List<Identifier> BRANCHES = List.of(
            ACCELERATOR,
            DARKMATTER,
            TELEPORT,
            MELTDOWNER,
            MENTALOUT,
            AEROMANIP,
            ELECTROMASTER
    );

    private AbilityAdvancements() {
    }

    public static void onCategoryChanged(ServerPlayer player, AbilityCategory category) {
        revokeAll(player);
        if (category == AbilityCategories.AEROMANIP.get()) {
            completeExclusively(player, AEROMANIP);
        }
    }

    public static void onLevelChanged(ServerPlayer player, AbilityCategory category, int level) {
        if (category == AbilityCategories.ACCELERATOR.get()) {
            setThresholdBranch(player, ACCELERATOR, level >= 5);
        } else if (category == AbilityCategories.DARKMATTER.get()) {
            setThresholdBranch(player, DARKMATTER, level >= 5);
        } else if (category == AbilityCategories.TELEPORT.get()) {
            setThresholdBranch(player, TELEPORT, level >= 4);
        } else if (category == AbilityCategories.MENTALOUT.get()) {
            setThresholdBranch(player, MENTALOUT, level >= 5);
        }
    }

    public static void onSkillLearned(ServerPlayer player, AbilityCategory category, String skillId) {
        if (category == AbilityCategories.MELTDOWNER.get()
                && skillId(SkillNames.SINGLE_HIGH_SPEED_ELECTRON_BEAM).equals(skillId)) {
            completeExclusively(player, MELTDOWNER);
        } else if (category == AbilityCategories.ELECTROMASTER.get()
                && skillId(SkillNames.RAILGUN).equals(skillId)) {
            completeExclusively(player, ELECTROMASTER);
        }
    }

    public static void onSkillRemoved(ServerPlayer player, String skillId) {
        if (skillId(SkillNames.SINGLE_HIGH_SPEED_ELECTRON_BEAM).equals(skillId)) {
            setCompleted(player, MELTDOWNER, false);
        } else if (skillId(SkillNames.RAILGUN).equals(skillId)) {
            setCompleted(player, ELECTROMASTER, false);
        }
    }

    public static void revokeAll(ServerPlayer player) {
        for (var branch : BRANCHES) {
            setCompleted(player, branch, false);
        }
    }

    private static void setThresholdBranch(ServerPlayer player, Identifier branch, boolean achieved) {
        if (achieved) {
            completeExclusively(player, branch);
        } else {
            setCompleted(player, branch, false);
        }
    }

    private static void completeExclusively(ServerPlayer player, Identifier selected) {
        for (var branch : BRANCHES) {
            if (!branch.equals(selected)) {
                setCompleted(player, branch, false);
            }
        }
        setCompleted(player, selected, true);
    }

    private static String skillId(String skillName) {
        return AcademyCraft.academy(skillName).toString();
    }

    private static void setCompleted(ServerPlayer player, Identifier id, boolean completed) {
        var advancement = player.level().getServer().getAdvancements().get(id);
        if (advancement == null) return;

        var advancements = player.getAdvancements();
        for (var criterion : advancement.value().criteria().keySet()) {
            if (completed) {
                advancements.award(advancement, criterion);
            } else {
                advancements.revoke(advancement, criterion);
            }
        }
    }
}
