package org.academy.internal.common.skilldata;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.darkmatter.creature.DarkmatterCreatureBlueprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistent four-slot blueprint library and server-authoritative summon index. */
public final class DarkmatterCreationData extends SkillData {
    public static final Identifier ID = AcademyCraft.academy("darkmatter_creation_data");
    public static final int BLUEPRINT_SLOTS = 4;

    /** Kept only to safely dismantle pre-blueprint beetles. */
    @SerializedName("ownedBeetles") private List<String> legacyOwnedBeetles = new ArrayList<>();
    @SerializedName("legacyMigrated") private boolean legacyMigrated;
    @SerializedName("selectedSlot") private int selectedSlot;
    @SerializedName("revision") private long revision;
    @SerializedName("blueprints") private List<DarkmatterCreatureBlueprint> blueprints = new ArrayList<>();
    @SerializedName("summons") private List<SummonRecord> summons = new ArrayList<>();

    @Override
    public Identifier getType() { return ID; }

    public List<DarkmatterCreatureBlueprint> getBlueprints(int level) {
        normalizeBlueprints(level);
        return blueprints.stream().map(DarkmatterCreatureBlueprint::copy).toList();
    }

    public DarkmatterCreatureBlueprint getBlueprint(int slot, int level) {
        normalizeBlueprints(level);
        return blueprints.get(Math.clamp(slot, 0, BLUEPRINT_SLOTS - 1)).copy();
    }

    public void setBlueprint(int slot, DarkmatterCreatureBlueprint blueprint, int level) {
        normalizeBlueprints(level);
        blueprints.set(Math.clamp(slot, 0, BLUEPRINT_SLOTS - 1), blueprint.copy());
        selectedSlot = Math.clamp(slot, 0, BLUEPRINT_SLOTS - 1);
        revision++;
    }

    public int getSelectedSlot() { return Math.clamp(selectedSlot, 0, BLUEPRINT_SLOTS - 1); }
    public void setSelectedSlot(int slot) {
        var normalized = Math.clamp(slot, 0, BLUEPRINT_SLOTS - 1);
        if (selectedSlot != normalized) { selectedSlot = normalized; revision++; }
    }
    public long getRevision() { return Math.max(0, revision); }

    public void bumpRevision() { revision = Math.max(0, revision) + 1; }

    public List<SummonRecord> getSummons() {
        if (summons == null) summons = new ArrayList<>();
        return summons.stream().map(SummonRecord::copy).toList();
    }

    public Optional<SummonRecord> getSummon(UUID uuid) {
        if (summons == null || uuid == null) return Optional.empty();
        return summons.stream().filter(record -> uuid.toString().equals(record.uuid)).findFirst()
                .map(SummonRecord::copy);
    }

    public void addSummon(UUID uuid, String name, int investment, int slot,
                          String dimension, double x, double y, double z) {
        if (summons == null) summons = new ArrayList<>();
        removeSummonInternal(uuid);
        summons.add(new SummonRecord(uuid, name, investment, slot, dimension, x, y, z));
        revision++;
    }

    public float removeSummon(UUID uuid) {
        if (summons == null || uuid == null) return 0.0f;
        for (var iterator = summons.iterator(); iterator.hasNext();) {
            var record = iterator.next();
            if (!uuid.toString().equals(record.uuid)) continue;
            iterator.remove();
            revision++;
            return record.reservation();
        }
        return 0.0f;
    }

    public void updateSummon(UUID uuid, String dimension, double x, double y, double z,
                             float health, float maxHealth, boolean loaded) {
        if (summons == null || uuid == null) return;
        for (var record : summons) {
            if (!uuid.toString().equals(record.uuid)) continue;
            record.dimension = dimension;
            record.x = finite(x); record.y = finite(y); record.z = finite(z);
            record.health = finiteNonNegative(health);
            record.maxHealth = finiteNonNegative(maxHealth);
            record.loaded = loaded;
            return;
        }
    }

    public List<UUID> getOwnedBeetles() {
        var result = new ArrayList<UUID>();
        if (summons != null) for (var record : summons) record.uuid().ifPresent(result::add);
        if (!legacyMigrated && legacyOwnedBeetles != null) for (var value : legacyOwnedBeetles) {
            try { result.add(UUID.fromString(value)); } catch (IllegalArgumentException ignored) { }
        }
        return result;
    }

    /** Compatibility helper for old tests/integrations; new summons use addSummon. */
    public void add(UUID uuid) {
        if (legacyOwnedBeetles == null) legacyOwnedBeetles = new ArrayList<>();
        legacyOwnedBeetles.remove(uuid.toString());
        legacyOwnedBeetles.add(uuid.toString());
    }

    public void remove(UUID uuid) {
        removeSummon(uuid);
        if (legacyOwnedBeetles != null) legacyOwnedBeetles.remove(uuid.toString());
    }

    public boolean needsLegacyMigration() {
        return !legacyMigrated && legacyOwnedBeetles != null && !legacyOwnedBeetles.isEmpty();
    }

    public List<UUID> consumeLegacyOwned() {
        var ids = new ArrayList<UUID>();
        if (legacyOwnedBeetles != null) for (var value : legacyOwnedBeetles) {
            try { ids.add(UUID.fromString(value)); } catch (IllegalArgumentException ignored) { }
        }
        legacyOwnedBeetles = new ArrayList<>();
        legacyMigrated = true;
        revision++;
        return ids;
    }

    public void clear() {
        if (summons == null) summons = new ArrayList<>();
        summons.clear();
        if (legacyOwnedBeetles == null) legacyOwnedBeetles = new ArrayList<>();
        legacyOwnedBeetles.clear();
        revision++;
    }

    private void normalizeBlueprints(int level) {
        if (blueprints == null) blueprints = new ArrayList<>();
        while (blueprints.size() < BLUEPRINT_SLOTS) {
            blueprints.add(DarkmatterCreatureBlueprint.defaultFor(blueprints.size(), level));
        }
        if (blueprints.size() > BLUEPRINT_SLOTS) {
            blueprints = new ArrayList<>(blueprints.subList(0, BLUEPRINT_SLOTS));
        }
    }

    private void removeSummonInternal(UUID uuid) {
        if (uuid != null) summons.removeIf(record -> uuid.toString().equals(record.uuid));
    }
    private static double finite(double value) { return Double.isFinite(value) ? value : 0.0; }
    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
    }

    public static final class SummonRecord {
        @SerializedName("uuid") private String uuid;
        @SerializedName("name") private String name;
        @SerializedName("investment") private int investment;
        @SerializedName("slot") private int slot;
        @SerializedName("dimension") private String dimension;
        @SerializedName("x") private double x;
        @SerializedName("y") private double y;
        @SerializedName("z") private double z;
        @SerializedName("health") private float health;
        @SerializedName("maxHealth") private float maxHealth;
        @SerializedName("loaded") private boolean loaded;

        public SummonRecord() { }
        private SummonRecord(UUID uuid, String name, int investment, int slot,
                             String dimension, double x, double y, double z) {
            this.uuid = uuid.toString(); this.name = name;
            this.investment = Math.max(0, investment);
            this.slot = Math.clamp(slot, 0, BLUEPRINT_SLOTS - 1);
            this.dimension = dimension;
            this.x = finite(x); this.y = finite(y); this.z = finite(z);
        }
        public SummonRecord copy() {
            var copy = new SummonRecord();
            copy.uuid = uuid; copy.name = name; copy.investment = investment; copy.slot = slot;
            copy.dimension = dimension; copy.x = x; copy.y = y; copy.z = z;
            copy.health = health; copy.maxHealth = maxHealth; copy.loaded = loaded;
            return copy;
        }
        public Optional<UUID> uuid() {
            try { return Optional.of(UUID.fromString(uuid)); }
            catch (RuntimeException ignored) { return Optional.empty(); }
        }
        public String name() { return name == null || name.isBlank() ? "Construct" : name; }
        public int investment() { return Math.max(0, investment); }
        public float reservation() { return investment(); }
        public int slot() { return Math.clamp(slot, 0, BLUEPRINT_SLOTS - 1); }
        public String dimension() { return dimension == null ? "minecraft:overworld" : dimension; }
        public double x() { return finite(x); }
        public double y() { return finite(y); }
        public double z() { return finite(z); }
        public float health() { return finiteNonNegative(health); }
        public float maxHealth() { return finiteNonNegative(maxHealth); }
        public boolean loaded() { return loaded; }
    }
}
