package com.leninasto.fnfmodinstaler

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject

private const val METADATA_TAG = "ModMetadataStore"

fun loadModMetadata(context: Context, dir: DocumentFile, isPolymod: Boolean, engineRoot: DocumentFile? = null): ModMetadata {
    val dirName = dir.name ?: "Unknown"

    val isEnabled = if (isPolymod) {
        val uriString = Uri.decode(dir.uri.toString())
        !uriString.contains("/mods_disabled/") && !uriString.endsWith("/mods_disabled")
    } else {
        checkModEnabledInList(context, dir, dirName, engineRoot)
    }

    var title = dirName
    var description = ""
    var author = ""
    var version = ""
    var apiVersion = ""
    var license = ""
    var restart = false
    var runsGlobally = false
    var color = emptyList<Int>()
    var discordRPC = ""
    var isApiOutdated = false
    var compatibility = ModCompatibility.UNKNOWN
    var icon: Bitmap? = null

    val fileName = if (isPolymod) "_polymod_meta.json" else "pack.json"
    val metaFile = dir.findFile(fileName)
    val iconFile = if (isPolymod) dir.findFile("_polymod_icon.png") else dir.findFile("pack.png")

    metaFile?.let { file ->
        try {
            val jsonStr = context.contentResolver.openInputStream(file.uri)?.use { readMetadata(it) }
            if (!jsonStr.isNullOrEmpty()) {
                val json = JSONObject(jsonStr)
                if (isPolymod) {
                    title = json.optString("title", title)
                    description = json.optString("description", "")
                    author = json.optString("author", "")
                    version = json.optString("mod_version", "")
                    apiVersion = json.optString("api_version", "")
                    license = json.optString("license", "")
                    compatibility = installedFunkinApi(context, apiVersion)
                    isApiOutdated = compatibility == ModCompatibility.MISMATCH || compatibility == ModCompatibility.INVALID
                } else {
                    title = json.optString("name", title)
                    description = json.optString("description", "")
                    author = json.optString("author", "")
                    version = json.optString("version", "")
                    restart = json.optBoolean("restart", false)
                    runsGlobally = json.optBoolean("runsGlobally", false)
                    discordRPC = json.optString("discordRPC", "")
                    val colorArray = json.optJSONArray("color")
                    if (colorArray != null) {
                        val list = mutableListOf<Int>()
                        for (i in 0 until colorArray.length()) list.add(colorArray.getInt(i))
                        color = list
                    }
                }
            }
        } catch (e: Exception) {
            compatibility = ModCompatibility.INVALID
            Log.e(METADATA_TAG, "Error loading JSON", e)
        }
    }

    iconFile?.let { file ->
        try {
            context.contentResolver.openInputStream(file.uri)?.use {
                val bytes = it.readBytesLimited(8 * 1024 * 1024)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options()
                while (bounds.outWidth / options.inSampleSize > 512 || bounds.outHeight / options.inSampleSize > 512) options.inSampleSize *= 2
                icon = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
        } catch (e: Exception) {
            Log.e(METADATA_TAG, "Error loading icon", e)
        }
    }

    return ModMetadata(title, description, author, version, apiVersion, license, restart, runsGlobally, color, discordRPC, isApiOutdated, icon, dir, isEnabled, compatibility)
}

fun checkModEnabledInList(context: Context, modDir: DocumentFile, modName: String, engineRoot: DocumentFile?): Boolean {
    val listFile = engineRoot?.findFile("modsList.txt")
        ?: modDir.parentFile?.findFile("modsList.txt")

    if (listFile == null) return true

    try {
        context.contentResolver.openInputStream(listFile.uri)?.use { inputStream ->
            val lines = inputStream.bufferedReader().readLines()
            val entry = lines.find { it.startsWith("$modName|") }
            if (entry != null) {
                return entry.substringAfter("|", "1").trim() == "1"
            }
        }
    } catch (e: Exception) {
        Log.e(METADATA_TAG, "Error reading list file", e)
    }
    return true
}

fun updateModsList(context: Context, rootDir: DocumentFile, modName: String, enabled: Boolean): Boolean {
    if (modName.any { it == '|' || it == '\n' || it == '\r' }) return false
    val listFile = rootDir.findFile("modsList.txt")
        ?: rootDir.createFile("text/plain", "modsList.txt") ?: return false

    try {
        val lines = mutableListOf<String>()
        context.contentResolver.openInputStream(listFile.uri)?.use { inputStream ->
            readMetadata(inputStream).lineSequence().forEach { if (it.isNotBlank()) lines.add(it) }
        } ?: return false

        val entry = "$modName|${if (enabled) 1 else 0}"
        var found = false
        for (i in lines.indices) {
            if (lines[i].startsWith("$modName|")) {
                lines[i] = entry
                found = true
                break
            }
        }
        if (!found) lines.add(entry)

        context.contentResolver.openOutputStream(listFile.uri, "wt")?.use { outputStream ->
            outputStream.bufferedWriter().use { writer ->
                lines.forEach { line ->
                    writer.write(line)
                    writer.newLine()
                }
                writer.flush()
            }
        } ?: return false
        return true
    } catch (e: Exception) {
        Log.e(METADATA_TAG, "updateModsList error", e)
        return false
    }
}

fun saveModMetadata(context: Context, dir: DocumentFile, isPolymod: Boolean, data: Map<String, Any>) {
    val fileName = if (isPolymod) "_polymod_meta.json" else "pack.json"
    val file = dir.findFile(fileName) ?: dir.createFile("application/json", fileName) ?: return
    try {
        val jsonStr = context.contentResolver.openInputStream(file.uri)?.use { readMetadata(it) }
        val json = if (!jsonStr.isNullOrEmpty()) JSONObject(jsonStr) else JSONObject()
        data.forEach { (key, value) ->
            if (value is List<*>) json.put(key, JSONArray(value))
            else json.put(key, value)
        }
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(json.toString(4).toByteArray()) }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun saveModIcon(context: Context, dir: DocumentFile, isPolymod: Boolean, sourceUri: Uri) {
    val fileName = if (isPolymod) "_polymod_icon.png" else "pack.png"
    val size = if (isPolymod) 256 else 150
    try {
        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            val bytes = inputStream.readBytesLimited(8 * 1024 * 1024)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options()
            while (bounds.outWidth / options.inSampleSize > 512 || bounds.outHeight / options.inSampleSize > 512) options.inSampleSize *= 2
            val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return
            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, size, size, true)
            val file = dir.findFile(fileName) ?: dir.createFile("image/png", fileName) ?: return
            context.contentResolver.openOutputStream(file.uri, "wt")?.use {
                resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
