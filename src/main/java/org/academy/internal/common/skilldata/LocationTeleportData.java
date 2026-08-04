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

    @Override
    public Identifier getType() {
        return ID;
    }

    public List<Mark> getMarks() {
        if (marks == null) marks = new ArrayList<>();
        return marks;
    }

    public int getSelectedMarkIndex() {
        if (selectedMarkIndex >= getMarks().size()) selectedMarkIndex = -1;
        return selectedMarkIndex;
    }

    public void setSelectedMarkIndex(int selectedMarkIndex) {
        this.selectedMarkIndex = selectedMarkIndex;
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
