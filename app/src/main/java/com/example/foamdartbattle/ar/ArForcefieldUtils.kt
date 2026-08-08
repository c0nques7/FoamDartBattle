package com.example.foamdartbattle.ar

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil

object ArForcefieldUtils {

    data class WallPoint(
        val x: Float,
        val yTop: Float,
        val yBottom: Float,
        val distance: Float
    )

    // Generates a perimeter of N points around a center coordinate
    fun generateZonePerimeter(center: LatLng, radius: Float, pointsCount: Int = 36): List<LatLng> {
        val points = mutableListOf<LatLng>()
        val angleStep = 360f / pointsCount
        for (i in 0 until pointsCount) {
            val angle = i * angleStep
            val point = SphericalUtil.computeOffset(center, radius.toDouble(), angle.toDouble())
            points.add(point)
        }
        return points
    }

    // Projects geographic perimeter coordinates into 2D screen coordinates based on a physical wall height (in meters)
    fun projectWallPoints(
        playerLoc: LatLng,
        heading: Float,
        perimeter: List<LatLng>,
        fovDegrees: Float = 70f,
        screenWidth: Float,
        screenHeight: Float,
        physicalHeightMeters: Float = 10f
    ): List<WallPoint> {
        val wallPoints = mutableListOf<WallPoint>()
        val halfFov = fovDegrees / 2f
        val horizonY = screenHeight / 2f

        // Focal length in vertical pixels, roughly half the screen height in landscape mode
        val focalLength = 0.5f * screenHeight

        for (point in perimeter) {
            val distance = SphericalUtil.computeDistanceBetween(playerLoc, point).toFloat()
            val bearing = SphericalUtil.computeHeading(playerLoc, point).toFloat()

            // Normalize bearing to 0..360
            val normalizedBearing = (bearing + 360f) % 360f

            // Shortest angular difference from current phone heading
            val diff = CompassUtils.getAngularDifference(normalizedBearing, heading)

            // If the point is within the camera's horizontal FOV
            if (diff in -halfFov..halfFov) {
                // Map the relative bearing angle linearly to the screen's X axis
                val relativeXPercent = diff / halfFov // value from -1.0 to +1.0
                val x = (screenWidth / 2f) + relativeXPercent * (screenWidth / 2f)

                // Calculate the visible height of the wall at this distance.
                // Avoid division by zero and cap extreme heights at close range.
                val safeDistance = distance.coerceAtLeast(1f)
                val wallHeight = ((focalLength * physicalHeightMeters) / safeDistance).coerceAtMost(screenHeight * 1.5f)

                val yTop = horizonY - (wallHeight / 2f)
                val yBottom = horizonY + (wallHeight / 2f)

                wallPoints.add(WallPoint(x, yTop, yBottom, distance))
            }
        }

        // Sort projected points by X coordinate so they can be drawn sequentially to form a clean path
        return wallPoints.sortedBy { it.x }
    }
}
