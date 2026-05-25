package org.academy.api.client.gui.msdf.atlas.allocator

import java.util.*
import kotlin.math.max

class SkylineAllocator(private val width: Int, private val height: Int) {
    private val skyline = ArrayList<SkylineNode>()

    init {
        skyline.add(SkylineNode(0, 0, width))
    }

    fun allocate(w: Int, h: Int): Optional<Rect> {
        var bestHeight = Int.MAX_VALUE
        var bestIndex = -1
        var bestY = -1

        for (i in skyline.indices) {
            val y = canFit(i, w, h)
            if (y != -1) {
                if (y + h < bestHeight) {
                    bestHeight = y + h
                    bestIndex = i
                    bestY = y
                }
            }
        }

        if (bestIndex != -1) {
            val rect = Rect(skyline[bestIndex].x, bestY, w, h)
            addSkylineNode(bestIndex, rect.x, rect.y + rect.height, rect.width)
            return Optional.of<Rect>(rect)
        }

        return Optional.empty<Rect>()
    }

    private fun canFit(index: Int, w: Int, h: Int): Int {
        val x = skyline[index].x
        if (x + w > width) return -1

        var widthLeft = w
        var y = skyline[index].y
        var i = index
        while (widthLeft > 0) {
            if (i >= skyline.size) return -1
            val node = skyline[i]
            y = max(y, node.y)
            if (y + h > height) return -1
            widthLeft -= node.width
            i++
        }
        return y
    }

    private fun addSkylineNode(index: Int, x: Int, y: Int, w: Int) {
        val newNode = SkylineNode(x, y, w)
        skyline.add(index, newNode)

        var i = index + 1
        while (i < skyline.size) {
            val node = skyline[i]
            val prev = skyline[i - 1]
            if (node.x < prev.x + prev.width) {
                val shrink = prev.x + prev.width - node.x
                node.x += shrink
                node.width -= shrink
                if (node.width <= 0) {
                    skyline.removeAt(i)
                    i--
                } else break
            } else break
            i++
        }
        merge()
    }

    private fun merge() {
        var i = 0
        while (i < skyline.size - 1) {
            if (skyline[i].y == skyline[i + 1].y) {
                skyline[i].width += skyline[i + 1].width
                skyline.removeAt(i + 1)
                i--
            }
            i++
        }
    }
}