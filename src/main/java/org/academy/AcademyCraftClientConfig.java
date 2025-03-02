package org.academy;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AcademyCraftClientConfig extends AcademyCraftConfig<AcademyCraftClientConfig> {
    @SerializedName("key")
    private final Map<Object, List<Integer>> key = new HashMap<>();

    public List<Integer> getKey(String name, List<Integer> defaultValue) {
        if (!key.containsKey(name)) {
            setKey(name, defaultValue);
        }
        return key.get(name);
    }

    public void setKey(String name, List<Integer> value) {
        key.put(name, value);
        saveConfig();
    }

    @Override
    protected void writeDefaultConfig(AcademyCraftClientConfig academyCraftConfig) {
        Generic generic = academyCraftConfig.getGeneric();
        generic.getBooleanMap().put("useMouseWheel", true);
    }
}
