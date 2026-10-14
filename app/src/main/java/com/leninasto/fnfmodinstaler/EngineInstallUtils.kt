package com.leninasto.fnfmodinstaler

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.edit

private fun engineEnabledPreferenceKey(engine: AppDestinations): String = "engine_enabled_${engine.name}"
private fun engineDetectedPreferenceKey(engine: AppDestinations): String = "engine_detected_${engine.name}"

fun isEngineInstalled(context: Context, engine: AppDestinations): Boolean {
    return findInstalledEnginePackage(context, engine) != null
}

fun findInstalledEnginePackage(context: Context, engine: AppDestinations): String? {
    return engine.packageCandidates.firstOrNull { packageName ->
        isPackageInstalled(context.packageManager, packageName)
    }
}

fun defaultEngineEnabled(context: Context, prefs: SharedPreferences, engine: AppDestinations): Boolean {
    if (engine.isStatic) return true
    val key = engineEnabledPreferenceKey(engine)
    if (prefs.contains(key)) return prefs.getBoolean(key, false)
    return engine == AppDestinations.ORIGINAL || isEngineInstalled(context, engine)
}

fun syncInstalledEngines(context: Context, prefs: SharedPreferences) {
    AppDestinations.entries
        .filter { !it.isStatic }
        .forEach { engine ->
            val key = engineEnabledPreferenceKey(engine)
            val detectedKey = engineDetectedPreferenceKey(engine)
            if (isEngineInstalled(context, engine) && !prefs.getBoolean(detectedKey, false)) {
                prefs.edit {
                    if (!prefs.contains(key)) putBoolean(key, true)
                    putBoolean(detectedKey, true)
                }
            }
        }
}

fun openEngineGame(context: Context, engine: AppDestinations): Boolean {
    val packageName = findInstalledEnginePackage(context, engine) ?: return false
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    return try {
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) { false }
}

fun openLinkedFolder(context: Context, folderUri: String?): Boolean {
    if (folderUri == null) return false
    return try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(folderUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}

fun showEngineNotInstalledToast(context: Context) {
    Toast.makeText(context, context.getString(R.string.engine_not_installed), Toast.LENGTH_SHORT).show()
}

fun showFolderOpenErrorToast(context: Context) {
    Toast.makeText(context, context.getString(R.string.open_folder_error), Toast.LENGTH_SHORT).show()
}

private fun isPackageInstalled(packageManager: PackageManager, packageName: String): Boolean {
    return try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
