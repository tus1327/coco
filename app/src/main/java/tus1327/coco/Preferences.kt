package tus1327.coco

import android.content.Context
import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class Preferences(preferences: SharedPreferences) {
    constructor(context: Context, name: String) : this(context.getSharedPreferences(name, Context.MODE_PRIVATE))

    var cocoFolder: String by PreferenceDelegate(preferences, "coco_folder", "")
}

private class PreferenceDelegate<T>(private val prefs: SharedPreferences,
                            private val name: String,
                            private val default: T) : ReadWriteProperty<Any?, T> {

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        prefs.run {
            val res: Any = when (default) {
                is Long -> getLong(property.name, default)
                is String -> getString(name, default)
                is Int -> getInt(name, default)
                is Boolean -> getBoolean(name, default)
                is Float -> getFloat(name, default)
                else -> throw IllegalArgumentException("잘못된 타입")
            }
            @Suppress("UNCHECKED_CAST")
            return res as T
        }
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        prefs.edit().apply {
            when (value) {
                is Long -> putLong(name, value)
                is String -> putString(name, value)
                is Int -> putInt(name, value)
                is Boolean -> putBoolean(name, value)
                is Float -> putFloat(name, value)
                else -> throw IllegalArgumentException("잘못된 타입")
            }
        }.apply()
    }
}

