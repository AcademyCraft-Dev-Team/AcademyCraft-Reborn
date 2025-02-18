package org.academy.api.common.util;

import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

public class MathUtil {
    public static class WeightedRandom {
        private final NavigableMap<Double, String> map = new TreeMap<>();
        private final Random random = new Random();
        private double totalWeight = 0.0;

        public void addItem(String item, double probability) {
            totalWeight += probability;
            map.put(totalWeight, item);
        }

        public String getRandomItem() {
            double r = random.nextDouble() * totalWeight;
            return map.ceilingEntry(r).getValue();
        }
    }
}