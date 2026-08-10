package org.academy.internal.common.skilldata;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

import java.util.ArrayList;
import java.util.List;

public final class LocationTeleportData extends SkillData {
    public static final Identifier ID = AcademyCraft.academy("location_teleport_data");

    @SerializedName("marks")
    private List<Mark> marks = new ArrayList<>();

    @SerializedName("selectedMarkIndex")
    private int selectedMarkIndex = -1;

    @SerializedName("quickMarkIndex")
    private int quickMarkIndex = -1;

    @SerializedName("defensiveMarkIndex")
    private int defensiveMarkIndex = -1;

    private static int adjustedAfterRemoval(int selected, int removed) {
        if (selected == removed) return -1;
        return selected > removed ? selected - 1 : selected;
    }

    @Override
    public Identifier getType() {
        return ID;
    }

    public List<Mark> getMarks() {
        if (marks == null) marks = new ArrayList<>();
        return marks;
    }

    @Deprecated(forRemoval = false)
    public int getSelectedMarkIndex() {
        return getQuickMarkIndex();
    }

    @Deprecated(forRemoval = false)
    public void setSelectedMarkIndex(int selectedMarkIndex) {
        this.selectedMarkIndex = selectedMarkIndex;
        quickMarkIndex = selectedMarkIndex;
        defensiveMarkIndex = selectedMarkIndex;
    }

    public int getQuickMarkIndex() {
        migrateLegacySelection();
        quickMarkIndex = validIndex(quickMarkIndex);
        return quickMarkIndex;
    }

    public void setQuickMarkIndex(int quickMarkIndex) {
        this.quickMarkIndex = validIndex(quickMarkIndex);
    }

    public int getDefensiveMarkIndex() {
        migrateLegacySelection();
        defensiveMarkIndex = validIndex(defensiveMarkIndex);
        return defensiveMarkIndex;
    }

    public void setDefensiveMarkIndex(int defensiveMarkIndex) {
        this.defensiveMarkIndex = validIndex(defensiveMarkIndex);
    }

    public void adjustSelectionsAfterRemoval(int removedIndex) {
        migrateLegacySelection();
        quickMarkIndex = validIndex(adjustedAfterRemoval(quickMarkIndex, removedIndex));
        defensiveMarkIndex = validIndex(adjustedAfterRemoval(defensiveMarkIndex, removedIndex));
    }

    private void migrateLegacySelection() {
        if (selectedMarkIndex < 0) return;
        var legacy = validIndex(selectedMarkIndex);
        if (quickMarkIndex < 0) quickMarkIndex = legacy;
        if (defensiveMarkIndex < 0) defensiveMarkIndex = legacy;
        selectedMarkIndex = -1;
    }

    private int validIndex(int index) {
        return index >= 0 && index < getMarks().size() ? index : -1;
    }

    public record Mark(
            @SerializedName("name") String name,
            @SerializedName("dimension") String dimension,
            @SerializedName("x") int x,
            @SerializedName("y") int y,
            @SerializedName("z") int z
    ) {
    }
}
