package org.academy.api.common.util;

import net.minecraft.locale.Language;

public final class L10nUtil {
    public static String get(String key) {
        return Language.getInstance().getOrDefault(key);
    }
}
