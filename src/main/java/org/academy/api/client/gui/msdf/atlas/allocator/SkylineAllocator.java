package org.academy.api.client.gui.msdf.atlas.allocator;

import java.util.ArrayList;
import java.util.Optional;

public class SkylineAllocator {
    private final int width;
    private final int height;
    private final ArrayList<SkylineNode> skyline = new ArrayList<>();

    public SkylineAllocator(int width, int height) {
        this.width = width;
        this.height = height;
        skyline.add(new SkylineNode(0, 0, width));
    }

    public Optional<Rect> allocate(int w, int h) {
        var bestHeight = Integer.MAX_VALUE;
        var bestIndex = -1;
        var bestY = -1;

        for (var i = 0; i < skyline.size(); i++) {
            var y = canFit(i, w, h);
            if (y != -1) {
                if (y + h < bestHeight) {
                    bestHeight = y + h;
                    bestIndex = i;
                    bestY = y;
                }
            }
        }

        if (bestIndex != -1) {
            var rect = new Rect(skyline.get(bestIndex).x, bestY, w, h);
            addSkylineNode(bestIndex, rect.x(), rect.y() + rect.height(), rect.width());
            return Optional.of(rect);
        }

        return Optional.empty();
    }

    private int canFit(int index, int w, int h) {
        var x = skyline.get(index).x;
        if (x + w > width) return -1;

        var widthLeft = w;
        var y = skyline.get(index).y;
        var i = index;
        while (widthLeft > 0) {
            if (i >= skyline.size()) return -1;
            var node = skyline.get(i);
            y = Math.max(y, node.y);
            if (y + h > height) return -1;
            widthLeft -= node.width;
            i++;
        }
        return y;
    }

    private void addSkylineNode(int index, int x, int y, int w) {
        var newNode = new SkylineNode(x, y, w);
        skyline.add(index, newNode);

        var i = index + 1;
        while (i < skyline.size()) {
            var node = skyline.get(i);
            var prev = skyline.get(i - 1);
            if (node.x < prev.x + prev.width) {
                var shrink = prev.x + prev.width - node.x;
                node.x += shrink;
                node.width -= shrink;
                if (node.width <= 0) {
                    skyline.remove(i);
                    i--;
                } else {
                    break;
                }
            } else {
                break;
            }
            i++;
        }
        merge();
    }

    private void merge() {
        var i = 0;
        while (i < skyline.size() - 1) {
            if (skyline.get(i).y == skyline.get(i + 1).y) {
                skyline.get(i).width += skyline.get(i + 1).width;
                skyline.remove(i + 1);
                i--;
            }
            i++;
        }
    }
}
