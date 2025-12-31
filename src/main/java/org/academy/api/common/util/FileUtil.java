package org.academy.api.common.util;

import java.io.File;
import java.io.IOException;

public final class FileUtil {
    public static void checkFile(File file) {
        try {
            var parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new RuntimeException(
                    "Failed to create directories: " + parent.getPath()
            );
            if (!file.createNewFile() && !file.exists()) throw new RuntimeException(
                    "Failed to create file: " + file.getPath()
            );
        } catch (IOException e) {
            throw new RuntimeException(
                    "An error occurred while creating the file: " + file.getAbsolutePath() + " - " + e.getMessage()
            );
        }
    }
}