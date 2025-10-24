package org.academy.api.common.vanilla;

public interface Tickable {
    /**
     * 绝对不能在 tick 中进行插值动画等喵, 动画就老老实实在 render 中进行喵
     */
    void tick();
}