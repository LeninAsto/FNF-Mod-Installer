package com.leninasto.fnfmodinstaler

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject

data class ModMetadata(
    val title: String,
    val description: String,
    val author: String,
    val version: String,
    val apiVersion: String,
    val license: String = "",
    val restart: Boolean = false,
    val runsGlobally: Boolean = false,
    val color: List<Int> = emptyList(),
    val discordRPC: String = "",
    val isApiOutdated: Boolean,
    val icon: Bitmap?,
    val dir: DocumentFile
)

fun loadModMetadata(context: Context, dir: DocumentFile, isPolymod: Boolean): ModMetadata {
    var title = dir.name ?: "Unknown"
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
    var icon: Bitmap? = null

    val fileName = if (isPolymod) "_polymod_meta.json" else "pack.json"
    val metaFile = dir.findFile(fileName)
    val iconFile = if (isPolymod) dir.findFile("_polymod_icon.png") else dir.findFile("pack.png")

    metaFile?.let { file ->
        try {
            val jsonStr = context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
            if (!jsonStr.isNullOrEmpty()) {
                val json = JSONObject(jsonStr)
                if (isPolymod) {
                    title = json.optString("title", title)
                    description = json.optString("description", "")
                    author = json.optString("author", "")
                    version = json.optString("mod_version", "")
                    apiVersion = json.optString("api_version", "")
                    license = json.optString("license", "")
                    if (apiVersion.isNotEmpty() && apiVersion != "0.8.4") isApiOutdated = true
                } else {
                    title = json.optString("name", title)
                    description = json.optString("description", "")
                    author = json.optString("author", "") // Psych sometimes has it?
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
        } catch (e: Exception) { e.printStackTrace() }
    }

    iconFile?.let { file ->
        try {
            context.contentResolver.openInputStream(file.uri)?.use { 
                icon = BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    return ModMetadata(title, description, author, version, apiVersion, license, restart, runsGlobally, color, discordRPC, isApiOutdated, icon, dir)
}

fun saveModMetadata(
    context: Context, 
    dir: DocumentFile, 
    isPolymod: Boolean, 
    data: Map<String, Any>
) {
    val fileName = if (isPolymod) "_polymod_meta.json" else "pack.json"
    val file = dir.findFile(fileName) ?: dir.createFile("application/json", fileName) ?: return
    try {
        val jsonStr = context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
        val json = if (!jsonStr.isNullOrEmpty()) JSONObject(jsonStr) else JSONObject()
        
        data.forEach { (key, value) ->
            if (value is List<*>) {
                json.put(key, JSONArray(value))
            } else {
                json.put(key, value)
            }
        }
        
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { 
            it.write(json.toString(4).toByteArray())
        }
    } catch (e: Exception) { e.printStackTrace() }
}

fun deleteDocumentRecursive(file: DocumentFile): Boolean {
    if (file.isDirectory) {
        file.listFiles().forEach { deleteDocumentRecursive(it) }
    }
    return file.delete()
}
