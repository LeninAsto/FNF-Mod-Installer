package com.leninasto.fnfmodinstaler

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile

/** Persist only user-selected trees, and release grants no longer used by any engine. */
fun bindEngineFolder(context: Context, key: String, uri: Uri): Boolean {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    return try {
        context.contentResolver.takePersistableUriPermission(uri, flags)
        val tree = DocumentFile.fromTreeUri(context, uri)
        if (tree == null || !tree.isDirectory || !tree.canRead() || !tree.canWrite()) {
            releaseUnusedFolder(context, uri.toString())
            return false
        }
        val prefs = context.getSharedPreferences("fnf_prefs", Context.MODE_PRIVATE)
        val old = prefs.getString("folder_uri_$key", null)
        prefs.edit { putString("folder_uri_$key", uri.toString()) }
        releaseUnusedFolder(context, old)
        true
    } catch (_: Exception) {
        false
    }
}

fun unlinkEngineFolder(context: Context, key: String) {
    val prefs = context.getSharedPreferences("fnf_prefs", Context.MODE_PRIVATE)
    val old = prefs.getString("folder_uri_$key", null)
    prefs.edit { remove("folder_uri_$key") }
    releaseUnusedFolder(context, old)
}

private fun releaseUnusedFolder(context: Context, value: String?) {
    if (value == null) return
    val prefs = context.getSharedPreferences("fnf_prefs", Context.MODE_PRIVATE)
    if (prefs.all.any { (key, uri) -> key.startsWith("folder_uri_") && uri == value }) return
    try {
        val grant = context.contentResolver.persistedUriPermissions.firstOrNull { it.uri.toString() == value } ?: return
        val flags = (if (grant.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (grant.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        context.contentResolver.releasePersistableUriPermission(grant.uri, flags)
    } catch (_: Exception) { /* The provider may already have revoked this grant. */ }
}

internal fun activeEngineFolderKey(engine: AppDestinations, folders: Map<String, String?>): String {
    val dual = engine == AppDestinations.PSLICE_ENGINE || engine == AppDestinations.PLUS_ENGINE
    return if (dual && folders["${engine.name}_ACTIVE_IS_ALT"] == "true") "${engine.name}_ALT" else engine.name
}
