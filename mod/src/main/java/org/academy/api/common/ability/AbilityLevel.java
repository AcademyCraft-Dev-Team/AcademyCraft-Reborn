package org.academy.api.common.ability;

public enum AbilityLevel {
    LEVEL0(0, 100f),
    LEVEL1(1, 100f),
    LEVEL2(2, 100f),
    LEVEL3(3, 100f),
    LEVEL4(4, 100f),
    LEVEL5(5, 100f),
    LEVEL6(6, 100f);

    final float basicCP;
    final int levelCode;

    AbilityLevel(int levelCode, float basicCP) {
        this.basicCP = basicCP;
        this.levelCode = levelCode;
    }

    public static AbilityLevel fromLevelCode(int levelCode) {
        for (var level : values()) {
            if (level.getLevelCode() == levelCode) {
                return level;
            }
        }
        throw new IllegalArgumentException("No ability level found with level code " + levelCode);
    }

    public float getBasicCP() {
        return basicCP;
    }

    public int getLevelCode() {
        return levelCode;
    }
}
