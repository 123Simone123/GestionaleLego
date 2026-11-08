package com.brickarchivio.app

import android.content.Context

/** Versione letta dal PackageManager (evita di dover abilitare BuildConfig). */
object BuildConfigInfo {
    fun versionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
}
