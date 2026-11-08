package com.brickarchivio.app.data

import android.content.Context

/** Impostazioni salvate sul telefono (SharedPreferences). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var rebrickableKey: String?
        get() = prefs.getString(KEY_REBRICKABLE, null)
        set(value) = prefs.edit().putString(KEY_REBRICKABLE, value?.trim()).apply()

    /** Catalogo mostrato a griglia (true) o a lista (false). */
    var catalogGrid: Boolean
        get() = prefs.getBoolean(KEY_GRID, true)
        set(value) = prefs.edit().putBoolean(KEY_GRID, value).apply()

    private companion object {
        const val KEY_GRID = "catalog_grid"
        const val KEY_REBRICKABLE = "rebrickable_api_key"
    }
}
