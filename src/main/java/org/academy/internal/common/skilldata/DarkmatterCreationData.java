package org.academy.internal.common.skilldata;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DarkmatterCreationData extends SkillData {
    public static final Identifier ID = AcademyCraft.academy("darkmatter_creation_data");

    @SerializedName("ownedBeetles")
    private List<String> ownedBeetles = new ArrayList<>();

    @Override
    public Identifier getType() {
        return ID;
    }

    public List<UUID> getOwnedBeetles() {
        if (ownedBeetles == null) ownedBeetles = new ArrayList<>();
        var result = new ArrayList<UUID>(ownedBeetles.size());
        for (var value : ownedBeetles) {
            try {
                result.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    public void add(UUID uuid) {
        if (ownedBeetles == null) ownedBeetles = new ArrayList<>();
        var value = uuid.toString();
        ownedBeetles.remove(value);
        ownedBeetles.add(value);
    }

    public void remove(UUID uuid) {
        if (ownedBeetles != null) ownedBeetles.remove(uuid.toString());
    }

    public void clear() {
        if (ownedBeetles == null) ownedBeetles = new ArrayList<>();
        ownedBeetles.clear();
    }
}
