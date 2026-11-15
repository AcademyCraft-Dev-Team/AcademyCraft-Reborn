package org.academy.api.client.render.vfxgraph.curve;

import java.util.Random;

public final class CurveBuffer {
    private static final int INITIAL_CAPACITY = 4;

    private TubeCurve[] arcs;
    private int count;

    public CurveBuffer() {
        arcs = new TubeCurve[INITIAL_CAPACITY];
        for (var i = 0; i < INITIAL_CAPACITY; i++) {
            arcs[i] = CurveFactories.get().create();
        }
    }

    public int count() {
        return count;
    }

    public TubeCurve arc(int index) {
        return arcs[index];
    }

    public TubeCurve add() {
        return add(0L);
    }

    public TubeCurve add(long replacementGroup) {
        var factory = CurveFactories.get();
        if (count == arcs.length) {
            var newCap = arcs.length * 2;
            var newArr = new TubeCurve[newCap];
            for (var i = 0; i < newCap; i++) {
                newArr[i] = i < arcs.length ? arcs[i] : factory.create();
            }
            arcs = newArr;
        }
        var arc = arcs[count];
        if (!factory.supports(arc)) {
            arc = factory.create();
            arcs[count] = arc;
        }
        arc.clearPoints();
        arc.setAge(0f);
        arc.setFresh(true);
        arc.resetSimState();
        arc.setReplacementGroup(replacementGroup);
        count++;
        return arc;
    }

    public void removeGroup(long replacementGroup) {
        if (replacementGroup == 0L) return;
        var retained = 0;
        for (var i = 0; i < count; i++) {
            if (arcs[i].replacementGroup() == replacementGroup) continue;
            var reusable = arcs[retained];
            arcs[retained] = arcs[i];
            arcs[i] = reusable;
            retained++;
        }
        count = retained;
    }

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
