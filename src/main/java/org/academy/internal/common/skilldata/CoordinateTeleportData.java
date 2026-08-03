package org.academy.internal.common.skilldata;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

import java.util.ArrayList;
import java.util.List;

public final class CoordinateTeleportData extends SkillData {
    public static final Identifier ID = AcademyCraft.academy("coordinate_teleport_data");
    public static final int MAX_POSITIONS = 32;

    @SerializedName("savedPositions")
    private List<SavedPosition> savedPositions = new ArrayList<>();

    @Override
    public Identifier getType() {
        return ID;
    }

    public List<SavedPosition> getSavedPositions() {
        if (savedPositions == null) savedPositions = new ArrayList<>();
        return savedPositions;
    }

    public void addPosition(SavedPosition pos) {
        var positions = getSavedPositions();
        if (positions.size() >= MAX_POSITIONS) positions.removeFirst();
        positions.add(pos);
    }

    public void removePosition(int index) {
        if (index >= 0 && index < savedPositions.size()) {
            savedPositions.remove(index);
        }
    }

    public record SavedPosition(
            @SerializedName("name") String name,
            @SerializedName("x") double x,
            @SerializedName("y") double y,
            @SerializedName("z") double z,
            @SerializedName("dimension") String dimension
    ) {
    }
}
