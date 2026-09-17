package com.aistra.hail.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.LauncherApps
import com.aistra.hail.R
import com.aistra.hail.app.HailData

object HAppLauncher {
    // Require the user explicitly: the package name alone cannot identify a cloned app.
    fun launch(context: Context, packageName: String, userId: Int) {
        if (userId == HPackages.myUserId) {
            if (HailData.workingMode == HailData.MODE_ISLAND_HIDE) {
                HIsland.ensureLaunchIntentExists(packageName)
            }
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: throw ActivityNotFoundException(context.getString(R.string.activity_not_found))
            context.startActivity(intent)
        } else {
            val launcher = context.getSystemService(LauncherApps::class.java)
            val user = HPackages.userHandle(userId)
            val activity = launcher.getActivityList(packageName, user).firstOrNull()
                ?: throw ActivityNotFoundException(context.getString(R.string.activity_not_found))
            launcher.startMainActivity(activity.componentName, user, null, null)
        }
        // A launcher refusing a shortcut must not turn a successful app launch into an error.
        runCatching { HShortcuts.addDynamicShortcut(packageName, userId) }.onFailure { HLog.e(it) }
    }
}
