package org.academy.internal.server.world.level.storage;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.data.AbilityData;
import org.academy.api.common.registries.Registries;
import org.academy.internal.common.ability.darkmatter.DarkmatterStateData;
import org.academy.internal.common.skilldata.CommonSkillData;
import org.academy.internal.common.skilldata.SkillData;

import java.util.*;

public final class Player {
    private static final Map<String, String> LEGACY_SKILL_ALIASES = Map.ofEntries(
            Map.entry("pulse_charge", "current_recharge"),
            Map.entry("vector_reduction", "vector_deviation"),
            Map.entry("lightning_spear", "thunder_lance"),
            Map.entry("thunder_clap", "thunderclap"),
            Map.entry("penetrate_teleport", "piercing_teleportation"),
            Map.entry("cut_through", "piercing_teleportation"),
            Map.entry("area_teleport_setup", "area_teleport_select"),
            Map.entry("area_teleport_start", "area_teleport_select"),
            Map.entry("assault_jet", "jet_strike"),
            Map.entry("brain_development_lv1", "brain_domain_development"),
            Map.entry("brain_development_lv2", "multiple_brain_domain_segmentation"),
            Map.entry("brain_development_lv3", "parallel_thought_computation"),
            Map.entry("brain_development_lv4", "complete_consciousness_analysis"),
            Map.entry("brain_development_lv5", "absolute_self_control"),
            Map.entry("level0_passive_lv1", "brain_domain_development"),
            Map.entry("level0_passive_lv2", "multiple_brain_domain_segmentation"),
            Map.entry("level0_passive_lv3", "parallel_thought_computation"),
            Map.entry("level0_passive_lv4", "complete_consciousness_analysis"),
            Map.entry("level0_passive_lv5", "absolute_self_control"),
            Map.entry("spreading_blast", "scatter_bomb"),
            Map.entry("kinetic_superposition", "kinetic_energy_applied"),
            Map.entry("directed_shock", "kinetic_energy_applied"),
            Map.entry("bioelectric_surge", "bioelectric_operation"),
            Map.entry("electron_barrier", "light_shield"),
            Map.entry("darkmatter_radiation", "darkmatter_interference")
    );
    private static final Set<String> RETIRED_SKILLS = Set.of(
            "academy:hell_flare",
            "academy:hyper_accelerate",
            "academy:chain_fusion"
    );
    @SerializedName("skillData")
    private final Map<String, SkillData> skillDataMap = new HashMap<>();
    @SerializedName("retainedSkillProficiencies")
    private final Map<String, Float> retainedSkillProficiencies = new HashMap<>();
    @SerializedName("abilityProgramBooks")
    private Map<String, String> abilityProgramBooks = new HashMap<>();
    @SerializedName("skills")
    private Set<String> legacySkills;
    @SerializedName("abilityCategory")
    private String abilityCategory;
    @SerializedName("propsData")
    private PropsData propsData = new PropsData();

    // CP
    @SerializedName("cpOccupations")
    private List<AbilityData.CpOccupationData> cpOccupations = new ArrayList<>();
    @SerializedName("cpData")
    private AbilityData cpData = new AbilityData();
    @SerializedName("darkmatterState")
    private DarkmatterStateData darkmatterState = new DarkmatterStateData();
    @SerializedName("appliedCommonSkillMaxCpBonus")
    private float appliedCommonSkillMaxCpBonus;
    @SerializedName("maxCpInitialized")
    private boolean maxCpInitialized;
    @SerializedName("challengeCpBonus")
    private float challengeCpBonus;

    @SerializedName("level")
    private Integer legacyLevel;
    @SerializedName("computingPower")
    private Float legacyComputingPower;
    @SerializedName("maxComputingPower")
    private Float legacyMaxComputingPower;
    @SerializedName("computingPowerRecoverySpeed")
    private Float legacyComputingPowerRecoverySpeed;

    private transient volatile boolean isDirty = false;

    static String canonicalizeSkillId(String skillId) {
        if (skillId == null || skillId.isBlank()) return skillId;

        var separator = skillId.indexOf(':');
        if (separator >= 0) {
            var namespace = skillId.substring(0, separator);
            if (!AcademyCraft.MOD_ID.equals(namespace)) return skillId;
        }
        var path = separator >= 0 ? skillId.substring(separator + 1) : skillId;
        var targetPath = LEGACY_SKILL_ALIASES.getOrDefault(path, path);
        return AcademyCraft.MOD_ID + ":" + targetPath;
    }

    private static void mergeSkillData(SkillData target, SkillData source) {
        target.setProficiency(Math.max(target.getProficiency(), source.getProficiency()));
        target.setEnabled(target.isEnabled() || source.isEnabled());
    }

    private static int resolveMaxSkillLevel(String skillId) {
        var id = Identifier.tryParse(skillId);
        if (id == null) return 3;
        return Registries.SKILLS.get(id)
                .map(reference -> reference.value().getMaxSkillLevel())
                .orElse(3);
    }

    public void markDirty() {
        isDirty = true;
    }

    public void clean() {
        isDirty = false;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public String getAbilityCategory() {
        return abilityCategory;
    }

    public void setAbilityCategory(String abilityCategory) {
        if (!Objects.equals(this.abilityCategory, abilityCategory)) {
            this.abilityCategory = abilityCategory;
            markDirty();
        }
    }

    public Map<String, SkillData> getSkillDataMap() {
        return skillDataMap;
    }

    public DarkmatterStateData getDarkmatterState() {
        if (darkmatterState == null) darkmatterState = new DarkmatterStateData();
        return darkmatterState;
    }

    public boolean isSkillLearned(String skillId) {
        return skillDataMap.containsKey(skillId);
    }

    public void retainSkillProficiency(String skillId, SkillData data) {
        if (skillId == null || data == null) return;
        retainedSkillProficiencies.merge(skillId, data.getProficiency(), Math::max);
        markDirty();
    }

    public void restoreRetainedSkillProficiency(String skillId, SkillData data) {
        if (skillId == null || data == null) return;
        var retained = retainedSkillProficiencies.get(skillId);
        if (retained != null) data.setProficiency(Math.max(data.getProficiency(), retained));
    }

    public Map<String, Float> getRetainedSkillProficiencies() {
        return retainedSkillProficiencies;
    }

    public String getAbilityProgramBook(String category) {
        return abilityProgramBooks().get(category);
    }

    public Map<String, String> getAbilityProgramBooks() {
        return Map.copyOf(abilityProgramBooks());
    }

    public void setAbilityProgramBook(String category, String encodedBook) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(encodedBook, "encodedBook");
        var previous = abilityProgramBooks().put(category, encodedBook);
        if (!Objects.equals(previous, encodedBook)) markDirty();
    }

    public void removeAbilityProgramBook(String category) {
        if (category != null && abilityProgramBooks().remove(category) != null) markDirty();
    }

    public AbilityData getCpData() {
        return cpData;
    }

    public void setCpData(AbilityData cpData) {
        if (!Objects.equals(this.cpData, cpData)) {
            this.cpData = cpData;
            markDirty();
        }
    }

    public List<AbilityData.CpOccupationData> getCpOccupations() {
        return cpOccupations;
    }

    public void setCpOccupations(List<AbilityData.CpOccupationData> cpOccupations) {
        this.cpOccupations = cpOccupations;
        markDirty();
    }

    public float getAppliedCommonSkillMaxCpBonus() {
        return appliedCommonSkillMaxCpBonus;
    }

    public void setAppliedCommonSkillMaxCpBonus(float bonus) {
        if (Float.compare(appliedCommonSkillMaxCpBonus, bonus) != 0) {
            appliedCommonSkillMaxCpBonus = bonus;
            markDirty();
        }
    }

    public boolean isMaxCpInitialized() {
        return maxCpInitialized;
    }

    public void setMaxCpInitialized(boolean initialized) {
        if (maxCpInitialized != initialized) {
            maxCpInitialized = initialized;
            markDirty();
        }
    }

    public float getChallengeCpBonus() {
        return Mth.clamp(challengeCpBonus, 0.0f, 200.0f);
    }

    public boolean addChallengeCpBonus(float amount) {
        if (!Float.isFinite(amount) || amount <= 0.0f) return false;
        var next = Mth.clamp(getChallengeCpBonus() + amount, 0.0f, 200.0f);
        if (Float.compare(challengeCpBonus, next) == 0) return false;
        challengeCpBonus = next;
        markDirty();
        return true;
    }

    public PropsData getPropsData() {
        if (propsData == null) propsData = new PropsData();
        return propsData;
    }

    boolean migrateLegacyData() {
        var changed = migrateAbilityCategory();
        changed |= migrateSkillData();
        changed |= migrateLegacySkillSet();
        changed |= migrateRetainedSkillProficiencies();
        changed |= repairAbilityProgramBooks();
        changed |= removeRetiredSkills();
        changed |= migrateOccupations();
        changed |= migrateLegacyAbilityData();
        changed |= migrateDarkmatterResource();
        changed |= getDarkmatterState().repair();
        changed |= getPropsData().repair();
        if (changed) markDirty();
        return changed;
    }

    private Map<String, String> abilityProgramBooks() {
        if (abilityProgramBooks == null) abilityProgramBooks = new HashMap<>();
        return abilityProgramBooks;
    }

    private boolean repairAbilityProgramBooks() {
        if (abilityProgramBooks == null) {
            abilityProgramBooks = new HashMap<>();
            return true;
        }
        return abilityProgramBooks.entrySet().removeIf(entry ->
                Identifier.tryParse(entry.getKey()) == null
                        || entry.getValue() == null
                        || entry.getValue().isBlank());
    }

    private boolean migrateAbilityCategory() {
        if (abilityCategory == null || abilityCategory.isBlank() || abilityCategory.indexOf(':') >= 0) {
            return false;
        }
        abilityCategory = AcademyCraft.MOD_ID + ":" + abilityCategory;
        return true;
    }

    private boolean migrateSkillData() {
        var original = new ArrayList<>(skillDataMap.entrySet());
        var changed = false;
        for (var entry : original) {
            var sourceId = entry.getKey();
            var targetId = canonicalizeSkillId(sourceId);
            var sourceData = entry.getValue();
            if (sourceData != null && sourceData.hasLegacyProgress()) {
                sourceData.migrateLegacyProgress(resolveMaxSkillLevel(targetId));
                changed = true;
            }
            if (sourceId.equals(targetId)) continue;

            sourceData = skillDataMap.remove(sourceId);
            if (sourceData == null) continue;
            var targetData = skillDataMap.get(targetId);
            if (targetData == null) {
                skillDataMap.put(targetId, sourceData);
            } else {
                mergeSkillData(targetData, sourceData);
            }
            changed = true;
        }
        return changed;
    }

    private boolean migrateLegacySkillSet() {
        if (legacySkills == null) return false;
        for (var legacySkill : legacySkills) {
            skillDataMap.putIfAbsent(canonicalizeSkillId(legacySkill), new CommonSkillData());
        }
        legacySkills = null;
        return true;
    }

    private boolean removeRetiredSkills() {
        var changed = skillDataMap.keySet().removeIf(RETIRED_SKILLS::contains);
        changed |= cpOccupations.removeIf(occupation ->
                RETIRED_SKILLS.contains(canonicalizeSkillId(occupation.getSkillId()))
        );
        return changed;
    }

    private boolean migrateOccupations() {
        var changed = false;
        var migrated = new ArrayList<AbilityData.CpOccupationData>(cpOccupations.size());
        for (var occupation : cpOccupations) {
            var targetId = canonicalizeSkillId(occupation.getSkillId());
            changed |= !targetId.equals(occupation.getSkillId());
            var stackGroup = occupation.getStackGroup().equals(occupation.getSkillId())
                    ? targetId
                    : occupation.getStackGroup();
            migrated.add(new AbilityData.CpOccupationData(
                    occupation.getAmount(),
                    occupation.getIterationTicks(),
                    targetId,
                    occupation.isPermanent(),
                    stackGroup
            ));
        }
        if (changed) cpOccupations = migrated;
        return changed;
    }

    private boolean migrateLegacyAbilityData() {
        var changed = legacyLevel != null
                || legacyComputingPower != null
                || legacyMaxComputingPower != null
                || legacyComputingPowerRecoverySpeed != null;
        if (!changed) return false;

        if (legacyLevel != null) {
            cpData.setLevel(AbilityLevel.fromLevelCode(Mth.clamp(legacyLevel, 0, 6)));
        }
        if (legacyMaxComputingPower != null && Float.isFinite(legacyMaxComputingPower)) {
            cpData.setMaxCP(Math.max(0.0f, legacyMaxComputingPower));
        }
        if (legacyComputingPower != null && Float.isFinite(legacyComputingPower)) {
            cpData.setAvailableCP(Math.max(0.0f, legacyComputingPower), cpData.getMaxCP());
        }

        legacyLevel = null;
        legacyComputingPower = null;
        legacyMaxComputingPower = null;
        legacyComputingPowerRecoverySpeed = null;
        return true;
    }

    private boolean migrateDarkmatterResource() {
        var changed = false;
        var shaping = skillDataMap.get("academy:darkmatter_shaping");
        if (shaping != null && !skillDataMap.containsKey("academy:darkmatter_generation")) {
            var generation = new CommonSkillData();
            generation.setProficiency(shaping.getProficiency());
            skillDataMap.put("academy:darkmatter_generation", generation);
            changed = true;
        }

        // Preserve legacy MP until DarkmatterResourceManager can split it against the migrated
        // permanent generation occupation. Dropping it here made debt-free legacy saves lose MP.
        return changed;
    }

    private boolean migrateRetainedSkillProficiencies() {
        if (retainedSkillProficiencies.isEmpty()) return false;
        var migrated = new HashMap<String, Float>();
        retainedSkillProficiencies.forEach((skillId, proficiency) -> {
            if (proficiency == null || !Float.isFinite(proficiency)) return;
            migrated.merge(
                    canonicalizeSkillId(skillId),
                    Mth.clamp(proficiency, SkillData.MIN_PROFICIENCY, SkillData.MAX_PROFICIENCY),
                    Math::max
            );
        });
        if (migrated.equals(retainedSkillProficiencies)) return false;
        retainedSkillProficiencies.clear();
        retainedSkillProficiencies.putAll(migrated);
        return true;
    }
}
