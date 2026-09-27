package com.opencontacts.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object LauncherIconManager {
    const val DEFAULT = "DEFAULT"
    const val BLUE = "BLUE"
    const val EMERALD = "EMERALD"
    const val SUNSET = "SUNSET"
    const val LAVENDER = "LAVENDER"

    private val aliases = mapOf(
        DEFAULT to "com.opencontacts.app.DefaultLauncherAlias",
        BLUE to "com.opencontacts.app.BlueLauncherAlias",
        EMERALD to "com.opencontacts.app.EmeraldLauncherAlias",
        SUNSET to "com.opencontacts.app.SunsetLauncherAlias",
        LAVENDER to "com.opencontacts.app.LavenderLauncherAlias",
    )

    fun allAliases(): List<String> = aliases.keys.toList()

    fun applyAlias(context: Context, alias: String) {
        val packageManager = context.packageManager
        aliases.forEach { (key, className) ->
            packageManager.setComponentEnabledSetting(
                ComponentName(context, className),
                if (key == alias) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
