package org.academy.api.client.render.vfxgraph.arc;

import java.util.Random;

/**
 * 帧级电弧集合（M22-Rev2）：swap-remove、自动老化/过期删除、容量倍增。
 *
 * <p>由 {@link ArcSimulator} 每帧驱动：{@link #advance} 递增 age 并删除过期弧线，
 * 新弧线经 {@link #add} 追加。渲染器通过 {@link #count} 和 {@link #arc} 遍历。</p>
 */
public final class ArcBuffer {
    private static final int INITIAL_CAPACITY = 4;

    private ArcCurve[] arcs;
    private int count;

    public ArcBuffer() {
        arcs = new ArcCurve[INITIAL_CAPACITY];
        for (var i = 0; i < INITIAL_CAPACITY; i++) {
            arcs[i] = new ArcCurve();
        }
    }

    public int count() {
        return count;
    }

    public ArcCurve arc(int index) {
        return arcs[index];
    }

    /**
     * 追加一条新弧线（返回可写引用，调用方填充数据后调用 {@link ArcCurve#setColor} 等）。
     */
    public ArcCurve add() {
        return add(0L);
    }

    /** 追加替换式瞬态弧；同组可在下一次采样前整批替换。 */
    public ArcCurve add(long replacementGroup) {
        if (count == arcs.length) {
            var newCap = arcs.length * 2;
            var newArr = new ArcCurve[newCap];
            for (var i = 0; i < newCap; i++) {
                newArr[i] = i < arcs.length ? arcs[i] : new ArcCurve();
            }
            arcs = newArr;
        }
        var arc = arcs[count];
        arc.clearPoints();
        arc.setAge(0f);
        arc.setFresh(true);
        arc.resetSimState();
        arc.setReplacementGroup(replacementGroup);
        count++;
        return arc;
    }

    /**
     * 移除某个替换式瞬态组的旧采样。用于实时跟随的几何电弧，
     * 避免参数变化时上一帧的寿命残留与当前帧分离。
     */
    public void removeGroup(long replacementGroup) {
        if (replacementGroup == 0L) return;
        int i = 0;
        while (i < count) {
            if (arcs[i].replacementGroup() == replacementGroup) {
                swapRemove(i);
            } else {
                i++;
            }
        }
    }

    /** 每帧递增 age，删除过期弧线（swap-remove）。先清全量 fresh 标记（M29b-02）。 */
    public void advance(float dt, Random random) {
        for (var i = 0; i < count; i++) {
            arcs[i].setFresh(false);
        }
        var i = 0;
        while (i < count) {
            var arc = arcs[i];
            arc.setAge(arc.age() + dt);
            if (!arc.isAlive()) {
                swapRemove(i);
            } else {
                i++;
            }
        }
    }

    /**
     * 清空全部弧线。
     */
    public void clear() {
        count = 0;
    }

    private void swapRemove(int index) {
        count--;
        var temp = arcs[index];
        arcs[index] = arcs[count];
        arcs[count] = temp;
    }
}
