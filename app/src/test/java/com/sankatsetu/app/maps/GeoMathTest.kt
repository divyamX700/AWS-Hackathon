package com.sankatsetu.app.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class GeoMathTest {

    @Test
    fun `2km radius at the equator produces a box roughly 2km in every direction`() {
        val box = GeoMath.boundingBoxForRadius(centerLat = 0.0, centerLon = 0.0, radiusMeters = 2000.0)
        // At the equator, 1 degree of longitude is (almost) the same distance as 1 degree of latitude.
        assertEquals(box.maxLat - box.minLat, box.maxLon - box.minLon, 0.0001)
        // Roughly 2000m / 111320 m-per-degree in every direction from center.
        assertEquals(2000.0 / 111_320.0, box.maxLat - 0.0, 1e-6)
    }

    @Test
    fun `same radius produces a wider longitude span at high latitude than at the equator`() {
        // Real physical fact this function must account for: a degree of
        // longitude covers less ground distance the further you are from
        // the equator, so the SAME radius needs a wider degree-span in
        // longitude at (say) Delhi's latitude than it would at the equator.
        val atEquator = GeoMath.boundingBoxForRadius(0.0, 77.0, 2000.0)
        val atDelhiLatitude = GeoMath.boundingBoxForRadius(28.6, 77.0, 2000.0) // Delhi is ~28.6N
        val equatorLonSpan = atEquator.maxLon - atEquator.minLon
        val delhiLonSpan = atDelhiLatitude.maxLon - atDelhiLatitude.minLon
        assertTrue("expected Delhi's longitude span ($delhiLonSpan) to be wider than the equator's ($equatorLonSpan)", delhiLonSpan > equatorLonSpan)
        // And it should match the real cosine relationship exactly, not just "somewhat wider".
        assertEquals(equatorLonSpan / cos(Math.toRadians(28.6)), delhiLonSpan, 1e-6)
    }

    @Test
    fun `never produces a latitude outside the valid -90 to 90 range`() {
        val box = GeoMath.boundingBoxForRadius(centerLat = 89.999, centerLon = 0.0, radiusMeters = 50_000.0)
        assertTrue(box.maxLat <= 90.0)
        assertTrue(box.minLat >= -90.0)
    }

    @Test
    fun `box is centered on the input point`() {
        val box = GeoMath.boundingBoxForRadius(centerLat = 19.076, centerLon = 72.877, radiusMeters = 2000.0) // Mumbai
        val midLat = (box.minLat + box.maxLat) / 2
        val midLon = (box.minLon + box.maxLon) / 2
        assertEquals(19.076, midLat, 1e-9)
        assertEquals(72.877, midLon, 1e-9)
    }
}
