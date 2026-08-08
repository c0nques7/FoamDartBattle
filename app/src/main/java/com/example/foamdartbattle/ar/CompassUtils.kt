package com.example.foamdartbattle.ar

object CompassUtils {
    data class CompassMarker(
        val angle: Float,
        val label: String,
        val isCardinal: Boolean,
        val isMajor: Boolean
    )

    fun getAngularDifference(angle: Float, center: Float): Float {
        var diff = angle - center
        while (diff < -180f) diff += 360f
        while (diff > 180f) diff -= 360f
        return diff
    }

    fun buildCompassMarkers(): List<CompassMarker> {
        return buildList {
            for (angle in 0 until 360 step 5) {
                val angleFloat = angle.toFloat()
                val label = when (angle) {
                    0 -> "N"
                    45 -> "NE"
                    90 -> "E"
                    135 -> "SE"
                    180 -> "S"
                    225 -> "SW"
                    270 -> "W"
                    315 -> "NW"
                    30, 60, 120, 150, 210, 240, 300, 330 -> angle.toString()
                    else -> ""
                }
                val isCardinal = angle % 45 == 0
                val isMajor = angle % 30 == 0 && !isCardinal
                add(CompassMarker(angleFloat, label, isCardinal, isMajor))
            }
        }
    }
}
