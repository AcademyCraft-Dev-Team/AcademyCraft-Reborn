package org.academy.api.client.gui.msdf.core

import kotlin.math.abs

class PerpDistanceSelector : EdgeSelector {
    private val caches = ArrayList<EdgeCache>()
    private val tempDist = SignedDistance()
    private var minTrueDistance = SignedDistance()
    private var minNegativePerpendicularDistance = -abs(minTrueDistance.distance)
    private var minPositivePerpendicularDistance = abs(minTrueDistance.distance)
    private var nearEdge: EdgeSegment? = null
    private var nearEdgeParam = 0.0
    private var edgeIndex = 0

    fun reset(delta: Double) {
        minTrueDistance.distance += Arithmetic.nonZeroSign(minTrueDistance.distance) * delta
        minNegativePerpendicularDistance = -abs(minTrueDistance.distance)
        minPositivePerpendicularDistance = abs(minTrueDistance.distance)
        nearEdge = null
        nearEdgeParam = 0.0
        edgeIndex = 0
    }

    fun getNextCache(): EdgeCache {
        if (edgeIndex >= caches.size) {
            caches.add(EdgeCache())
        }
        return caches[edgeIndex++]
    }

    fun isEdgeRelevant(cache: EdgeCache, p: Point): Boolean {
        val absMinDist = abs(minTrueDistance.distance)
        if (cache.absDistance <= absMinDist) return true

        val dx = p.x - cache.point.x
        val dy = p.y - cache.point.y
        val d2 = dx * dx + dy * dy
        val factor2 = Constants.DISTANCE_DELTA_FACTOR * Constants.DISTANCE_DELTA_FACTOR

        if (cache.aDomainDistance * cache.aDomainDistance < factor2 * d2) return true
        if (cache.bDomainDistance * cache.bDomainDistance < factor2 * d2) return true

        if (cache.absDistance > absMinDist) {
            val diff = cache.absDistance - absMinDist
            if (diff * diff <= factor2 * d2) return true
        }

        if (cache.aDomainDistance > 0) {
            val apd = abs(cache.aPerpendicularDistance)
            if (apd <= absMinDist) return true
            val diff = apd - absMinDist
            if (diff * diff <= factor2 * d2) return true
        }

        if (cache.bDomainDistance > 0) {
            val bpd = abs(cache.bPerpendicularDistance)
            if (bpd <= absMinDist) return true
            val diff = bpd - absMinDist
            if (diff * diff <= factor2 * d2) return true
        }

        return false
    }

    fun addEdgeTrueDistance(edge: EdgeSegment, distance: SignedDistance, param: Double) {
        if (SignedDistance.lessThan(distance, minTrueDistance)) {
            minTrueDistance.distance = distance.distance
            minTrueDistance.dot = distance.dot
            nearEdge = edge
            nearEdgeParam = param
        }
    }

    fun addEdgePerpendicularDistance(distance: Double) {
        if (distance <= 0 && distance > minNegativePerpendicularDistance)
            minNegativePerpendicularDistance = distance
        if (distance in 0.0..<minPositivePerpendicularDistance)
            minPositivePerpendicularDistance = distance
    }

    override fun merge(other: EdgeSelector) {
        if (other is PerpDistanceSelector) {
            if (SignedDistance.lessThan(other.minTrueDistance, minTrueDistance)) {
                minTrueDistance.distance = other.minTrueDistance.distance
                minTrueDistance.dot = other.minTrueDistance.dot
                nearEdge = other.nearEdge
                nearEdgeParam = other.nearEdgeParam
            }
            if (other.minNegativePerpendicularDistance > minNegativePerpendicularDistance)
                minNegativePerpendicularDistance = other.minNegativePerpendicularDistance
            if (other.minPositivePerpendicularDistance < minPositivePerpendicularDistance)
                minPositivePerpendicularDistance = other.minPositivePerpendicularDistance
        }
    }

    fun computeDistance(p: Point): Double {
        var minDistance =
            if (minTrueDistance.distance < 0) minNegativePerpendicularDistance else minPositivePerpendicularDistance
        val edge = nearEdge
        if (edge != null) {
            tempDist.distance = minTrueDistance.distance
            tempDist.dot = minTrueDistance.dot
            edge.distanceToPerpendicularDistance(tempDist, p, nearEdgeParam)
            if (abs(tempDist.distance) < abs(minDistance))
                minDistance = tempDist.distance
        }
        return minDistance
    }

    override fun reset(origin: Point) {
        val delta = Constants.DISTANCE_DELTA_FACTOR * 0.0
        reset(delta)
    }

    override fun addEdge(prevEdge: EdgeSegment, edge: EdgeSegment, nextEdge: EdgeSegment) {
        val cache = getNextCache()

        val result = edge.signedDistance(cache.point)
        val param = result.param
        tempDist.distance = result.distance
        tempDist.dot = result.dot

        addEdgeTrueDistance(edge, tempDist, param)

        cache.point = Point(0.0, 0.0)
        cache.absDistance = abs(tempDist.distance)

        val ap = cache.point - edge.startPoint
        val bp = cache.point - edge.endPoint
        val aDir = edge.normalizedStartDir
        val bDir = edge.normalizedEndDir
        val prevDir = prevEdge.normalizedEndDir
        val nextDir = nextEdge.normalizedStartDir

        val add = ap dot (prevDir + aDir).normalized()
        val bdd = -(bp dot (bDir + nextDir).normalized())

        if (add > 0) {
            val pd = doubleArrayOf(tempDist.distance)
            if (getPerpendicularDistance(pd, ap, -aDir)) {
                pd[0] = -pd[0]
                addEdgePerpendicularDistance(pd[0])
            }
            cache.aPerpendicularDistance = pd[0]
        }

        if (bdd > 0) {
            val pd = doubleArrayOf(tempDist.distance)
            if (getPerpendicularDistance(pd, bp, bDir)) {
                addEdgePerpendicularDistance(pd[0])
            }
            cache.bPerpendicularDistance = pd[0]
        }

        cache.aDomainDistance = add
        cache.bDomainDistance = bdd
    }

    class EdgeCache {
        var point = Point(0.0, 0.0)
        var absDistance = 0.0
        var aDomainDistance = 0.0
        var bDomainDistance = 0.0
        var aPerpendicularDistance = 0.0
        var bPerpendicularDistance = 0.0
    }

    companion object {
        fun getPerpendicularDistance(distance: DoubleArray, ep: Vec2, edgeDir: Vec2): Boolean {
            val ts = ep dot edgeDir
            if (ts > 0) {
                val perpendicularDistance = ep cross edgeDir
                if (abs(perpendicularDistance) < abs(distance[0])) {
                    distance[0] = perpendicularDistance
                    return true
                }
            }
            return false
        }
    }
}

class MultiDistanceSelector : EdgeSelector {
    private val r = PerpDistanceSelector()
    private val g = PerpDistanceSelector()
    private val b = PerpDistanceSelector()
    private var p = Point(0.0, 0.0)

    override fun reset(origin: Point) {
        val delta = Constants.DISTANCE_DELTA_FACTOR * (origin - p).length()
        r.reset(delta)
        g.reset(delta)
        b.reset(delta)
        p = origin
    }

    override fun addEdge(prevEdge: EdgeSegment, edge: EdgeSegment, nextEdge: EdgeSegment) {
        val cache = r.getNextCache()

        val edgeColor = edge.color
        val redChannel = (edgeColor and EdgeColor.RED) != 0
        val greenChannel = (edgeColor and EdgeColor.GREEN) != 0
        val blueChannel = (edgeColor and EdgeColor.BLUE) != 0

        val redRelevant = redChannel && r.isEdgeRelevant(cache, p)
        val greenRelevant = greenChannel && g.isEdgeRelevant(cache, p)
        val blueRelevant = blueChannel && b.isEdgeRelevant(cache, p)

        if (!redRelevant && !greenRelevant && !blueRelevant) return

        val result = edge.signedDistance(p)
        val param = result.param
        val tempDist = SignedDistance().apply { distance = result.distance; dot = result.dot }

        if (redRelevant) r.addEdgeTrueDistance(edge, tempDist, param)
        if (greenRelevant) g.addEdgeTrueDistance(edge, tempDist, param)
        if (blueRelevant) b.addEdgeTrueDistance(edge, tempDist, param)

        cache.point = p
        cache.absDistance = abs(tempDist.distance)

        val ap = p - edge.startPoint
        val bp = p - edge.endPoint
        val aDir = edge.normalizedStartDir
        val bDir = edge.normalizedEndDir
        val prevDir = prevEdge.normalizedEndDir
        val nextDir = nextEdge.normalizedStartDir

        val add = ap dot (prevDir + aDir).normalized()
        val bdd = -(bp dot (bDir + nextDir).normalized())

        if (add > 0) {
            val pd = doubleArrayOf(tempDist.distance)
            if (PerpDistanceSelector.getPerpendicularDistance(pd, ap, -aDir)) {
                pd[0] = -pd[0]
                if (redChannel) r.addEdgePerpendicularDistance(pd[0])
                if (greenChannel) g.addEdgePerpendicularDistance(pd[0])
                if (blueChannel) b.addEdgePerpendicularDistance(pd[0])
            }
            cache.aPerpendicularDistance = pd[0]
        }

        if (bdd > 0) {
            val pd = doubleArrayOf(tempDist.distance)
            if (PerpDistanceSelector.getPerpendicularDistance(pd, bp, bDir)) {
                if (redChannel) r.addEdgePerpendicularDistance(pd[0])
                if (greenChannel) g.addEdgePerpendicularDistance(pd[0])
                if (blueChannel) b.addEdgePerpendicularDistance(pd[0])
            }
            cache.bPerpendicularDistance = pd[0]
        }

        cache.aDomainDistance = add
        cache.bDomainDistance = bdd
    }

    override fun merge(other: EdgeSelector) {
        if (other is MultiDistanceSelector) {
            r.merge(other.r)
            g.merge(other.g)
            b.merge(other.b)
        }
    }

    fun distance(): MultiDistance {
        return MultiDistance(
            r.computeDistance(p),
            g.computeDistance(p),
            b.computeDistance(p)
        )
    }
}

interface ContourCombiner<D> {
    fun reset(origin: Point)
    fun edgeSelector(index: Int): EdgeSelector
    fun combine(): D
}

class SimpleCombiner : ContourCombiner<MultiDistance> {
    private val selector = MultiDistanceSelector()

    override fun reset(origin: Point) = selector.reset(origin)
    override fun edgeSelector(index: Int) = selector
    override fun combine() = selector.distance()
}

class OverlappingCombiner(shape: Shape) : ContourCombiner<MultiDistance> {
    private val windings = IntArray(shape.contours.size) { shape.contours[it].winding }
    private val selectors = List(shape.contours.size) { MultiDistanceSelector() }
    private var p = Point(0.0, 0.0)

    override fun reset(origin: Point) {
        p = origin
        selectors.forEach { it.reset(origin) }
    }

    override fun edgeSelector(index: Int) = selectors[index]

    override fun combine(): MultiDistance {
        return overlappingDistance(windings, selectors, p)
    }

    private fun overlappingDistance(
        windings: IntArray,
        selectors: List<MultiDistanceSelector>,
        p: Point
    ): MultiDistance {
        val n = selectors.size

        val contourDists = Array(n) { selectors[it].distance() }
        val medians = DoubleArray(n) { median(contourDists[it]) }

        val shapeSel = MultiDistanceSelector()
        shapeSel.reset(p)
        for (i in 0 until n) shapeSel.merge(selectors[i])
        val shapeDist = shapeSel.distance()

        val innerSel = MultiDistanceSelector()
        val outerSel = MultiDistanceSelector()
        innerSel.reset(p)
        outerSel.reset(p)
        var hasInner = false
        var hasOuter = false
        for (i in 0 until n) {
            if (windings[i] > 0 && medians[i] >= 0) {
                innerSel.merge(selectors[i])
                hasInner = true
            }
            if (windings[i] < 0 && medians[i] <= 0) {
                outerSel.merge(selectors[i])
                hasOuter = true
            }
        }

        val innerDist = if (hasInner) innerSel.distance()
        else MultiDistance()
        val outerDist = if (hasOuter) outerSel.distance()
        else MultiDistance(r = Double.MAX_VALUE, g = Double.MAX_VALUE, b = Double.MAX_VALUE)
        val innerScalar = median(innerDist)
        val outerScalar = median(outerDist)

        var distance: MultiDistance
        var winding: Int

        if (hasInner && innerScalar >= 0 && abs(innerScalar) <= abs(outerScalar)) {
            distance = innerDist
            winding = 1
            for (i in 0 until n) {
                if (windings[i] > 0) {
                    if (abs(medians[i]) < abs(outerScalar) && medians[i] > median(distance))
                        distance = contourDists[i]
                }
            }
        } else if (hasOuter && outerScalar <= 0 && abs(outerScalar) < abs(innerScalar)) {
            distance = outerDist
            winding = -1
            for (i in 0 until n) {
                if (windings[i] < 0) {
                    if (abs(medians[i]) < abs(innerScalar) && medians[i] < median(distance))
                        distance = contourDists[i]
                }
            }
        } else {
            return shapeDist
        }

        for (i in 0 until n) {
            if (windings[i] != winding) {
                if (medians[i] * median(distance) >= 0 && abs(medians[i]) < abs(median(distance)))
                    distance = contourDists[i]
            }
        }

        return if (median(distance) == median(shapeDist)) shapeDist else distance
    }

    private fun median(d: MultiDistance): Double {
        return maxOf(minOf(d.r, d.g), minOf(maxOf(d.r, d.g), d.b))
    }
}