package com.example.foamdartbattle.data

import android.content.Context
import android.content.SharedPreferences

data class HudElementState(
    val xOffset: Float = 0f,
    val yOffset: Float = 0f,
    val scale: Float = 1.0f
)

class HudPreferencesManager(private val prefs: SharedPreferences) {
    
    constructor(context: Context) : this(
        context.getSharedPreferences("hud_layout_prefs", Context.MODE_PRIVATE)
    )

    fun saveElementState(key: String, state: HudElementState) {
        prefs.edit()
            .putFloat("${key}_x", state.xOffset)
            .putFloat("${key}_y", state.yOffset)
            .putFloat("${key}_scale", state.scale)
            .apply()
    }

    fun getElementState(key: String, defaultX: Float = 0f, defaultY: Float = 0f): HudElementState {
        val x = prefs.getFloat("${key}_x", defaultX)
        val y = prefs.getFloat("${key}_y", defaultY)
        val scale = prefs.getFloat("${key}_scale", 1.0f)
        return HudElementState(x, y, scale)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
