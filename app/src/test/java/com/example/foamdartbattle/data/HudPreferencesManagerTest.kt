package com.example.foamdartbattle.data

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HudPreferencesManagerTest {

    private class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any>()

        override fun getAll(): Map<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = map[key] as? Set<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(this)

        // Unused overrides for a simple mock
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
            private val tempMap = mutableMapOf<String, Any>()
            private var clearCalled = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                if (value != null) tempMap[key] = value else tempMap.remove(key)
                return this
            }
            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
                if (values != null) tempMap[key] = values else tempMap.remove(key)
                return this
            }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                tempMap[key] = value
                return this
            }
            override fun remove(key: String): SharedPreferences.Editor {
                tempMap.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearCalled = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearCalled) {
                    prefs.map.clear()
                }
                prefs.map.putAll(tempMap)
            }
        }
    }

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var manager: HudPreferencesManager

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        manager = HudPreferencesManager(fakePrefs)
    }

    @Test
    fun testSaveAndGetElementState() {
        val key = "hp_element"
        val state = HudElementState(120f, -45f, 1.5f)
        
        manager.saveElementState(key, state)
        
        val retrieved = manager.getElementState(key)
        assertEquals(120f, retrieved.xOffset, 0.01f)
        assertEquals(-45f, retrieved.yOffset, 0.01f)
        assertEquals(1.5f, retrieved.scale, 0.01f)
    }

    @Test
    fun testGetElementState_defaultValues() {
        val key = "round_element"
        val retrieved = manager.getElementState(key, 50f, 100f)
        assertEquals(50f, retrieved.xOffset, 0.01f)
        assertEquals(100f, retrieved.yOffset, 0.01f)
        assertEquals(1.0f, retrieved.scale, 0.01f)
    }

    @Test
    fun testClearAll() {
        val state1 = HudElementState(10f, 20f, 1.2f)
        val state2 = HudElementState(30f, 40f, 0.8f)
        manager.saveElementState("elem1", state1)
        manager.saveElementState("elem2", state2)
        
        manager.clearAll()
        
        val retrieved1 = manager.getElementState("elem1")
        assertEquals(0f, retrieved1.xOffset, 0.01f)
        assertEquals(0f, retrieved1.yOffset, 0.01f)
        assertEquals(1.0f, retrieved1.scale, 0.01f)
    }
}
