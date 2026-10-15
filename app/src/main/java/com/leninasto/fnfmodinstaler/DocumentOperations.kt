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
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext

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
        return@withContext updateModsList(context, rootDir, modName, !isEnabled)
    }
}

suspend fun moveDocumentCompatWithProgress(
    context: Context,
    source: DocumentFile,
    sourceParent: DocumentFile,
    targetParent: DocumentFile,
    onProgress: (ProgressState) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    if (sourceParent.uri == targetParent.uri || targetParent.findFile(source.name ?: "") != null) return@withContext false
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
    val totalFiles = try { countFilesRecursive(source) } catch (_: Exception) { return@withContext false }
    val currentCount = intArrayOf(0)
    val startTime = System.currentTimeMillis()

    val success = copyDirectoryWithProgress(context, source, targetParent, onProgress, totalFiles, currentCount, startTime)
    if (success) {
        Log.d(DOCUMENT_TAG, "Copy successful, now deleting source...")
        return@withContext try {
            deleteDocumentRecursiveWithProgress(source, onProgress, totalFiles, intArrayOf(0), System.currentTimeMillis())
            true
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { false }
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
    val name = source.name ?: return false
    if (targetParent.findFile(name) != null) return false
    val newDir = targetParent.createDirectory(name) ?: return false
    var completed = false
    try {
        val budget = CopyBudget()
        val visited = mutableSetOf<Uri>()
        suspend fun copyChecked(src: DocumentFile, dst: DocumentFile, depth: Int) {
            if (depth > 32 || !src.canRead() || !visited.add(src.uri)) throw IOException("Unreadable or cyclic folder")
            for (item in src.listFiles()) {
                budget.entry()
                val itemName = item.name ?: throw IOException("Missing file name")
                if (normalizeZipEntryPath(itemName) != itemName || '/' in itemName) throw IOException("Unsafe file name")
                current[0]++
                val progress = current[0].toFloat() / total.coerceAtLeast(1)
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = (elapsed / progress.coerceAtLeast(.01f) - elapsed).toLong()
                withContext(Dispatchers.Main) {
                    onProgress(ProgressState(true, progress.coerceIn(0f, 1f), itemName,
                        formatTime(remaining), processedUnits = "${current[0]} / $total"))
                }
                if (item.isDirectory) {
                    val child = dst.createDirectory(itemName) ?: throw IOException("Cannot create directory")
                    copyChecked(item, child, depth + 1)
                } else {
                    val child = dst.createFile(item.type ?: "application/octet-stream", itemName)
                        ?: throw IOException("Cannot create file")
                    context.contentResolver.openInputStream(item.uri)?.use { input ->
                        writeDocument(context, input, child.uri, budget)
                    } ?: throw IOException("Cannot read file")
                }
            }
        }
        copyChecked(source, newDir, 0)
        completed = true
        return true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        return false
    } finally {
        if (!completed) deleteIncompleteInstallation(newDir)
    }
}

suspend fun deleteDocumentRecursiveWithProgress(
    file: DocumentFile,
    onProgress: (ProgressState) -> Unit,
    totalFiles: Int,
    currentProgress: IntArray,
    startTime: Long
) {
    currentCoroutineContext().ensureActive()
    val name = file.name.orEmpty()
    // Prefer the provider's recursive delete: some providers cannot list damaged folders.
    if (runCatching { file.delete() }.getOrDefault(false)) {
        currentProgress[0] = totalFiles.coerceAtLeast(1)
        withContext(Dispatchers.Main) {
            onProgress(ProgressState(true, 1f, name, "00:00"))
        }
        return
    }
    deleteChildrenThenFolder(file)
    currentProgress[0] = totalFiles.coerceAtLeast(1)
    withContext(Dispatchers.Main) {
        onProgress(ProgressState(true, 1f, name, "00:00"))
    }
}

private suspend fun deleteChildrenThenFolder(file: DocumentFile, depth: Int = 0) {
    currentCoroutineContext().ensureActive()
    if (depth > 128) throw IOException("Folder nesting is too deep to delete")
    if (file.isDirectory) {
        file.listFiles().forEach { child ->
            currentCoroutineContext().ensureActive()
            if (!runCatching { child.delete() }.getOrDefault(false)) {
                deleteChildrenThenFolder(child, depth + 1)
            }
        }
    }
    if (!file.delete()) throw IOException("Cannot delete document")
}

/** Cancellation must not interrupt removal of files from an incomplete copy. */
internal suspend fun deleteIncompleteInstallation(folder: DocumentFile) {
    withContext(NonCancellable + Dispatchers.IO) {
        runCatching {
            if (!folder.delete()) deleteChildrenThenFolder(folder)
        }.onFailure { Log.w(DOCUMENT_TAG, "Incomplete folder remains; remove it using folder cleanup", it) }
    }
}

fun countFilesRecursive(file: DocumentFile): Int {
    val visited = mutableSetOf<Uri>()
    fun count(doc: DocumentFile, depth: Int): Int {
        if (depth > 32 || !doc.canRead() || !visited.add(doc.uri) || visited.size > MAX_ARCHIVE_ENTRIES) throw IOException("Unreadable or oversized folder")
        return 1 + if (doc.isDirectory) doc.listFiles().sumOf { count(it, depth + 1) } else 0
    }
    return count(file, 0)
}

fun isUriPermissionValid(context: Context, uriString: String?): Boolean {
    if (uriString == null) return false
    return try {
        val uri = uriString.toUri()
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
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
