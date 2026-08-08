package com.example.foamdartbattle.ar

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import org.junit.Assert.*
import org.junit.Test

class ArForcefieldUtilsTest {

    @Test
    fun testGenerateZonePerimeter_countAndRadius() {
        val center = LatLng(37.4220, -122.0840)
        val radius = 100f // 100 meters
        
        val perimeter = ArForcefieldUtils.generateZonePerimeter(center, radius, pointsCount = 36)
        
        assertEquals(36, perimeter.size)
        
        // Verify that every point lies on the circle with radius 100m (with a small margin for spherical accuracy)
        for (point in perimeter) {
            val dist = SphericalUtil.computeDistanceBetween(center, point)
            assertEquals(100.0, dist, 0.5) // margin of 0.5 meters
        }
    }

    @Test
    fun testProjectWallPoints_centerAlignment() {
        // Player location and heading (facing North, 0 degrees)
        val playerLoc = LatLng(37.4220, -122.0840)
        val heading = 0f
        
        // Point is exactly North, 10 meters away
        val northPoint = SphericalUtil.computeOffset(playerLoc, 10.0, 0.0)
        val perimeter = listOf(northPoint)
        
        val screenWidth = 800f
        val screenHeight = 600f
        
        val projected = ArForcefieldUtils.projectWallPoints(
            playerLoc = playerLoc,
            heading = heading,
            perimeter = perimeter,
            fovDegrees = 60f,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
        
        assertEquals(1, projected.size)
        
        val p = projected.first()
        // Bearing is 0, Heading is 0. So it should be projected exactly in the center X of the screen.
        assertEquals(screenWidth / 2f, p.x, 0.01f)
        
        // Height check: yTop and yBottom should be centered around the horizon (300f)
        val centerHorizon = screenHeight / 2f
        assertEquals(centerHorizon, (p.yTop + p.yBottom) / 2f, 0.01f)
    }

    @Test
    fun testProjectWallPoints_heightInverselyProportionalToDistance() {
        val playerLoc = LatLng(37.4220, -122.0840)
        val heading = 90f // facing East
        
        // Point A: East, 10 meters away (close)
        val pointA = SphericalUtil.computeOffset(playerLoc, 10.0, 90.0)
        // Point B: East, 100 meters away (far)
        val pointB = SphericalUtil.computeOffset(playerLoc, 100.0, 90.0)
        
        val screenWidth = 800f
        val screenHeight = 600f
        
        val projectedA = ArForcefieldUtils.projectWallPoints(playerLoc, heading, listOf(pointA), 60f, screenWidth, screenHeight)
        val projectedB = ArForcefieldUtils.projectWallPoints(playerLoc, heading, listOf(pointB), 60f, screenWidth, screenHeight)
        
        assertEquals(1, projectedA.size)
        assertEquals(1, projectedB.size)
        
        val wallHeightA = projectedA.first().yBottom - projectedA.first().yTop
        val wallHeightB = projectedB.first().yBottom - projectedB.first().yTop
        
        // Closer point should yield a much taller wall than the far point
        assertTrue(wallHeightA > wallHeightB)
    }
}
