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
        for (int i = 0; i < INITIAL_CAPACITY; i++) {
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
        if (count == arcs.length) {
            int newCap = arcs.length * 2;
            var newArr = new ArcCurve[newCap];
            for (int i = 0; i < newCap; i++) {
                newArr[i] = i < arcs.length ? arcs[i] : new ArcCurve();
            }
            arcs = newArr;
        }
        var arc = arcs[count];
        arc.clearPoints();
        arc.setAge(0f);
        arc.setFresh(true);
        arc.resetSimState();
        count++;
        return arc;
    }

    /**
     * 每帧递增 age，删除过期弧线（swap-remove）。先清全量 fresh 标记（M29b-02）。
     */
    public void advance(float dt, Random random) {
        for (int i = 0; i < count; i++) {
            arcs[i].setFresh(false);
        }
        int i = 0;
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
