package com.leninasto.fnfmodinstaler

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.Locale
import java.util.concurrent.TimeUnit

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
    val dir: DocumentFile,
    val isEnabled: Boolean
)

data class ProgressState(
    val isRunning: Boolean = false,
    val percentage: Float = 0f,
    val currentFile: String = "",
    val timeRemaining: String = "Calculating...",
    val speed: String = "",
    val processedUnits: String = ""
)

private const val TAG = "ModUtils"

fun loadModMetadata(context: Context, dir: DocumentFile, isPolymod: Boolean, engineRoot: DocumentFile? = null): ModMetadata {
    val dirName = dir.name ?: "Unknown"
    
    val isEnabled = if (isPolymod) {
        // En Polymod, el estado depende de si está en la carpeta 'mods' o 'mods_disabled'
        val uriString = Uri.decode(dir.uri.toString())
        // Si el URI contiene mods_disabled es porque está ahí metido
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
        } catch (e: Exception) { Log.e(TAG, "Error loading JSON", e) }
    }

    iconFile?.let { file ->
        try {
            context.contentResolver.openInputStream(file.uri)?.use { 
                icon = BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) { Log.e(TAG, "Error loading icon", e) }
    }

    return ModMetadata(title, description, author, version, apiVersion, license, restart, runsGlobally, color, discordRPC, isApiOutdated, icon, dir, isEnabled)
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
        Log.e(TAG, "Error reading list file", e)
    }
    return true
}

suspend fun toggleModStatusWithProgress(
    context: Context, 
    modDir: DocumentFile, 
    rootDir: DocumentFile, 
    isPolymod: Boolean,
    onProgress: (ProgressState) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    val modName = modDir.name ?: return@withContext false
    Log.d(TAG, "toggleModStatusWithProgress: $modName, isPolymod: $isPolymod")
    
    if (isPolymod) {
        val modsDir = rootDir.findFile("mods") ?: rootDir
        val disabledDir = rootDir.findFile("mods_disabled") ?: rootDir.createDirectory("mods_disabled") ?: return@withContext false
        
        // Verificamos dónde está el mod realmente
        val modInEnabled = modsDir.findFile(modName)
        val modInDisabled = disabledDir.findFile(modName)
        
        val sourceParent: DocumentFile
        val targetParent: DocumentFile
        val actualMod: DocumentFile
        
        if (modInDisabled != null) {
            sourceParent = disabledDir
            targetParent = modsDir
            actualMod = modInDisabled
            Log.d(TAG, "Mod found in mods_disabled. Moving to mods.")
        } else if (modInEnabled != null) {
            sourceParent = modsDir
            targetParent = disabledDir
            actualMod = modInEnabled
            Log.d(TAG, "Mod found in mods. Moving to mods_disabled.")
        } else {
            // Si no lo encuentra por nombre en las carpetas esperadas, usamos el URI
            val uriString = Uri.decode(modDir.uri.toString())
            if (uriString.contains("/mods_disabled/")) {
                sourceParent = disabledDir
                targetParent = modsDir
            } else {
                // Asumimos que viene de mods si no dice disabled
                sourceParent = modsDir
                targetParent = disabledDir
            }
            actualMod = modDir
            Log.d(TAG, "Mod not found in children. Using inferred parents from URI.")
        }
        
        onProgress(ProgressState(isRunning = true, currentFile = "Preparing to move $modName..."))
        return@withContext moveDocumentCompatWithProgress(context, actualMod, sourceParent, targetParent, onProgress)
    } else {
        val isEnabled = checkModEnabledInList(context, modDir, modName, rootDir)
        updateModsList(context, rootDir, modName, !isEnabled)
        return@withContext true
    }
}

fun updateModsList(context: Context, rootDir: DocumentFile, modName: String, enabled: Boolean) {
    val listFile = rootDir.findFile("modsList.txt") 
                   ?: rootDir.createFile("text/plain", "modsList.txt") ?: return

    try {
        val lines = mutableListOf<String>()
        context.contentResolver.openInputStream(listFile.uri)?.use { inputStream ->
            inputStream.bufferedReader().readLines().forEach { if (it.isNotBlank()) lines.add(it) }
        }

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
            BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                lines.forEach { line ->
                    writer.write(line)
                    writer.newLine()
                }
                writer.flush()
            }
        }
    } catch (e: Exception) { 
        Log.e(TAG, "updateModsList error", e)
    }
}

suspend fun moveDocumentCompatWithProgress(
    context: Context, 
    source: DocumentFile, 
    sourceParent: DocumentFile, 
    targetParent: DocumentFile,
    onProgress: (ProgressState) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    try {
        Log.d(TAG, "Attempting DocumentsContract.moveDocument...")
        val resultUri = DocumentsContract.moveDocument(
            context.contentResolver,
            source.uri,
            sourceParent.uri,
            targetParent.uri
        )
        if (resultUri != null) {
            Log.d(TAG, "Move successful!")
            return@withContext true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Fast move failed: ${e.message}. Falling back to copy/delete", e)
    }
    
    // Fallback: Copiado y borrado con progreso
    Log.d(TAG, "Starting fallback copy/delete process...")
    val totalFiles = countFilesRecursive(source)
    val currentCount = intArrayOf(0)
    val startTime = System.currentTimeMillis()
    
    val success = copyDirectoryWithProgress(context, source, targetParent, onProgress, totalFiles, currentCount, startTime)
    if (success) {
        Log.d(TAG, "Copy successful, now deleting source...")
        deleteDocumentRecursiveWithProgress(source, onProgress, totalFiles, intArrayOf(0), System.currentTimeMillis())
        return@withContext true
    }
    return@withContext false
}

private suspend fun copyDirectoryWithProgress(
    context: Context, 
    source: DocumentFile, 
    targetParent: DocumentFile,
    onProgress: (ProgressState) -> Unit,
    total: Int,
    current: IntArray,
    startTime: Long
): Boolean {
    val newDir = targetParent.createDirectory(source.name ?: "Mod") ?: return false
    source.listFiles().forEach { item ->
        current[0]++
        val progress = current[0].toFloat() / total
        val elapsed = System.currentTimeMillis() - startTime
        val remaining = if (progress > 0) (elapsed / progress - elapsed).toLong() else 0L

        withContext(Dispatchers.Main) {
            onProgress(ProgressState(
                isRunning = true,
                percentage = progress,
                currentFile = item.name ?: "",
                timeRemaining = formatTime(remaining),
                processedUnits = "${current[0]} / $total items"
            ))
        }

        if (item.isDirectory) {
            copyDirectoryWithProgress(context, item, newDir, onProgress, total, current, startTime)
        } else {
            val newFile = newDir.createFile(item.type ?: "application/octet-stream", item.name ?: "file")
            if (newFile != null) {
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
    return true
}

suspend fun deleteDocumentRecursiveWithProgress(
    file: DocumentFile, 
    onProgress: (ProgressState) -> Unit,
    totalFiles: Int,
    currentProgress: IntArray,
    startTime: Long
) {
    if (file.isDirectory) {
        file.listFiles().forEach { deleteDocumentRecursiveWithProgress(it, onProgress, totalFiles, currentProgress, startTime) }
    }
    file.delete()
    currentProgress[0]++
    
    val elapsed = System.currentTimeMillis() - startTime
    val progress = currentProgress[0].toFloat() / totalFiles
    val remaining = if (progress > 0) (elapsed / progress - elapsed).toLong() else 0L
    
    withContext(Dispatchers.Main) {
        onProgress(ProgressState(
            isRunning = true,
            percentage = progress,
            currentFile = file.name ?: "",
            timeRemaining = formatTime(remaining),
            processedUnits = "${currentProgress[0]} / $totalFiles items"
        ))
    }
}

fun countFilesRecursive(file: DocumentFile): Int {
    if (!file.isDirectory) return 1
    var count = 1
    file.listFiles().forEach { count += countFilesRecursive(it) }
    return count
}

fun formatTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60))
    return if (hours > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun saveModMetadata(context: Context, dir: DocumentFile, isPolymod: Boolean, data: Map<String, Any>) {
    val fileName = if (isPolymod) "_polymod_meta.json" else "pack.json"
    val file = dir.findFile(fileName) ?: dir.createFile("application/json", fileName) ?: return
    try {
        val jsonStr = context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
        val json = if (!jsonStr.isNullOrEmpty()) JSONObject(jsonStr) else JSONObject()
        data.forEach { (key, value) ->
            if (value is List<*>) json.put(key, JSONArray(value))
            else json.put(key, value)
        }
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(json.toString(4).toByteArray()) }
    } catch (e: Exception) { e.printStackTrace() }
}

fun saveModIcon(context: Context, dir: DocumentFile, isPolymod: Boolean, sourceUri: Uri) {
    val fileName = if (isPolymod) "_polymod_icon.png" else "pack.png"
    val size = if (isPolymod) 256 else 150
    try {
        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, size, size, true)
            val file = dir.findFile(fileName) ?: dir.createFile("image/png", fileName) ?: return
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { 
                resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
}
