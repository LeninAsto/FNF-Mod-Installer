package com.leninasto.fnfmodinstaler

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private const val DOCUMENT_TAG = "DocumentOperations"

suspend fun toggleModStatusWithProgress(
    context: Context,
    modDir: DocumentFile,
    rootDir: DocumentFile,
    isPolymod: Boolean,
    onProgress: (ProgressState) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    val modName = modDir.name ?: return@withContext false
    Log.d(DOCUMENT_TAG, "toggleModStatusWithProgress: $modName, isPolymod: $isPolymod")

    if (isPolymod) {
        val modsDir = rootDir.findFile("mods") ?: rootDir
        val disabledDir = rootDir.findFile("mods_disabled") ?: rootDir.createDirectory("mods_disabled") ?: return@withContext false

        val modInEnabled = modsDir.findFile(modName)
        val modInDisabled = disabledDir.findFile(modName)

        val sourceParent: DocumentFile
        val targetParent: DocumentFile
        val actualMod: DocumentFile

        if (modInDisabled != null) {
            sourceParent = disabledDir
            targetParent = modsDir
            actualMod = modInDisabled
            Log.d(DOCUMENT_TAG, "Mod found in mods_disabled. Moving to mods.")
        } else if (modInEnabled != null) {
            sourceParent = modsDir
            targetParent = disabledDir
            actualMod = modInEnabled
            Log.d(DOCUMENT_TAG, "Mod found in mods. Moving to mods_disabled.")
        } else {
            val uriString = Uri.decode(modDir.uri.toString())
            if (uriString.contains("/mods_disabled/")) {
                sourceParent = disabledDir
                targetParent = modsDir
            } else {
                sourceParent = modsDir
                targetParent = disabledDir
            }
            actualMod = modDir
            Log.d(DOCUMENT_TAG, "Mod not found in children. Using inferred parents from URI.")
        }

        onProgress(ProgressState(isRunning = true, currentFile = "Preparing to move $modName..."))
        return@withContext moveDocumentCompatWithProgress(context, actualMod, sourceParent, targetParent, onProgress)
    } else {
        val isEnabled = checkModEnabledInList(context, modDir, modName, rootDir)
        updateModsList(context, rootDir, modName, !isEnabled)
        return@withContext true
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
        Log.d(DOCUMENT_TAG, "Attempting DocumentsContract.moveDocument...")
        val resultUri = DocumentsContract.moveDocument(
            context.contentResolver,
            source.uri,
            sourceParent.uri,
            targetParent.uri
        )
        if (resultUri != null) {
            Log.d(DOCUMENT_TAG, "Move successful!")
            return@withContext true
        }
    } catch (e: Exception) {
        Log.e(DOCUMENT_TAG, "Fast move failed: ${e.message}. Falling back to copy/delete", e)
    }

    Log.d(DOCUMENT_TAG, "Starting fallback copy/delete process...")
    val totalFiles = countFilesRecursive(source)
    val currentCount = intArrayOf(0)
    val startTime = System.currentTimeMillis()

    val success = copyDirectoryWithProgress(context, source, targetParent, onProgress, totalFiles, currentCount, startTime)
    if (success) {
        Log.d(DOCUMENT_TAG, "Copy successful, now deleting source...")
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
            onProgress(
                ProgressState(
                    isRunning = true,
                    percentage = progress,
                    currentFile = item.name ?: "",
                    timeRemaining = formatTime(remaining),
                    processedUnits = "${current[0]} / $total items"
                )
            )
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
        onProgress(
            ProgressState(
                isRunning = true,
                percentage = progress,
                currentFile = file.name ?: "",
                timeRemaining = formatTime(remaining),
                processedUnits = "${currentProgress[0]} / $totalFiles items"
            )
        )
    }
}

fun countFilesRecursive(file: DocumentFile): Int {
    if (!file.isDirectory) return 1
    var count = 1
    file.listFiles().forEach { count += countFilesRecursive(it) }
    return count
}

fun isUriPermissionValid(context: Context, uriString: String?): Boolean {
    if (uriString == null) return false
    return try {
        val uri = uriString.toUri()
        context.contentResolver.persistedUriPermissions.any { it.uri == uri }
    } catch (e: Exception) {
        false
    }
}

fun formatTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = millis / (1000 * 60 * 60)
    return if (hours > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
