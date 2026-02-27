package com.osamaalek.kiosklauncher.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.osamaalek.kiosklauncher.model.AppInfo
import java.util.Locale


class AppsUtil {

    companion object {

        fun getAllApps(context: Context): List<AppInfo> {
            val packageManager: PackageManager = context.packageManager
            val uniqueApps = LinkedHashMap<String, AppInfo>()
            val i = Intent(Intent.ACTION_MAIN, null)
            i.addCategory(Intent.CATEGORY_LAUNCHER)
            val allApps = packageManager.queryIntentActivities(i, 0)
            for (ri in allApps) {
                val packageName = ri.activityInfo.packageName ?: continue
                if (uniqueApps.containsKey(packageName)) continue
                val app = AppInfo(
                    ri.loadLabel(packageManager),
                    packageName,
                    ri.activityInfo.loadIcon(packageManager)
                )
                uniqueApps[packageName] = app
            }
            return uniqueApps.values.sortedWith(
                compareBy<AppInfo>(
                    { it.label?.toString()?.lowercase(Locale.getDefault()) ?: "" },
                    { it.packageName?.toString() ?: "" }
                )
            )
        }

        fun getAllowedApps(context: Context, allowedPackages: Set<String>): List<AppInfo> {
            if (allowedPackages.isEmpty()) return emptyList()
            val allowedSet = allowedPackages.toSet()
            return getAllApps(context).filter { info ->
                val packageName = info.packageName?.toString().orEmpty()
                packageName in allowedSet
            }
        }

    }
}