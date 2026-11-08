package com.brickarchivio.app.data

import android.content.Context

/** Impostazioni salvate sul telefono (SharedPreferences). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var rebrickableKey: String?
        get() = prefs.getString(KEY_REBRICKABLE, null)
        set(value) = prefs.edit().putString(KEY_REBRICKABLE, value?.trim()).apply()

    private companion object {
        const val KEY_REBRICKABLE = "rebrickable_api_key"
    }
}
