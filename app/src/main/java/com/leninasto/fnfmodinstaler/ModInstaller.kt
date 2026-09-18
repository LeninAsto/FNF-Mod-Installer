package com.leninasto.fnfmodinstaler

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.zip.ZipInputStream

private val modMetadataFileNames = setOf("pack.json", "_polymod_meta.json")

internal data class ZipEntryInfo(
    val path: String,
    val isDirectory: Boolean
)

internal data class ZipInstallPlan(
    val sourceRoot: String,
    val destinationFolderName: String
)

suspend fun extractZipWithProgress(
    context: Context,
    zipUri: Uri,
    targetFolderUri: Uri,
    onProgress: (ProgressState) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    try {
        val rootDoc = DocumentFile.fromTreeUri(context, targetFolderUri) ?: return@withContext false
        val startTime = System.currentTimeMillis()

        val archiveName = getDisplayName(context, zipUri)
        val zipEntries = mutableListOf<ZipEntryInfo>()
        context.contentResolver.openInputStream(zipUri)?.use { is1 ->
            ZipInputStream(is1).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val safePath = normalizeZipEntryPath(entry.name)
                    if (safePath != null && !isIgnoredZipPath(safePath)) {
                        zipEntries += ZipEntryInfo(safePath, entry.isDirectory)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        val installPlan = planZipInstall(zipEntries, archiveName) ?: return@withContext false
        val modFolder = createUniqueDirectory(rootDoc, installPlan.destinationFolderName) ?: return@withContext false
        val totalEntries = zipEntries.count { it.path.relativeTo(installPlan.sourceRoot) != null }
        if (totalEntries == 0) return@withContext false

        var currentEntry = 0
        context.contentResolver.openInputStream(zipUri)?.use { is2 ->
            ZipInputStream(is2).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val safePath = normalizeZipEntryPath(entry.name)
                    val relativePath = safePath?.relativeTo(installPlan.sourceRoot)
                    if (safePath == null || isIgnoredZipPath(safePath) || relativePath == null) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }

                    currentEntry++
                    val progress = currentEntry.toFloat() / totalEntries
                    val elapsed = System.currentTimeMillis() - startTime
                    val remaining = if (progress > 0) (elapsed / progress - elapsed).toLong() else 0L

                    withContext(Dispatchers.Main) {
                        onProgress(
                            ProgressState(
                                isRunning = true,
                                percentage = progress,
                                currentFile = relativePath.ifEmpty { safePath },
                                timeRemaining = formatTime(remaining),
                                processedUnits = "$currentEntry / $totalEntries items"
                            )
                        )
                    }

                    if (relativePath.isNotEmpty() && entry.isDirectory) {
                        createDirectoryChain(modFolder, relativePath)
                    } else if (relativePath.isNotEmpty()) {
                        val fileDoc = createFileChain(modFolder, relativePath)
                        fileDoc?.let { doc ->
                            context.contentResolver.openOutputStream(doc.uri)?.use { fos -> zis.copyTo(fos) }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

internal fun planZipInstall(entries: List<ZipEntryInfo>, archiveName: String?): ZipInstallPlan? {
    val files = entries.filterNot { it.isDirectory }
    if (files.isEmpty()) return null

    val markerRoots = files
        .filter { it.path.fileName().lowercase(Locale.ROOT) in modMetadataFileNames }
        .map { it.path.parentPath() }
        .distinct()

    val sourceRoot = when {
        markerRoots.isNotEmpty() -> markerRoots.minWith(compareBy<String> { it.pathDepth() }.thenBy { it.length })
        files.all { it.path.firstSegmentOrNull() != null && it.path.firstSegmentOrNull() == files.first().path.firstSegmentOrNull() } -> files.first().path.firstSegmentOrNull().orEmpty()
        else -> ""
    }

    val destinationName = if (sourceRoot.isNotEmpty()) {
        sourceRoot.fileName()
    } else {
        archiveName?.substringBeforeLast('.', archiveName).orEmpty()
    }

    return ZipInstallPlan(
        sourceRoot = sourceRoot,
        destinationFolderName = sanitizeDocumentName(destinationName).ifBlank { "Mod" }
    )
}

suspend fun copyFolderWithProgress(
    context: Context,
    sourceFolderUri: Uri,
    targetFolderUri: Uri,
    onProgress: (ProgressState) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    try {
        val sourceDoc = DocumentFile.fromTreeUri(context, sourceFolderUri) ?: return@withContext false
        val targetRoot = DocumentFile.fromTreeUri(context, targetFolderUri) ?: return@withContext false
        val totalFiles = countFilesRecursive(sourceDoc)
        val currentCount = intArrayOf(0)
        val startTime = System.currentTimeMillis()

        val modFolder = targetRoot.createDirectory(sourceDoc.name ?: "Mod") ?: return@withContext false
        copyRecursiveWithProgress(context, sourceDoc, modFolder, onProgress, totalFiles, currentCount, startTime)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private suspend fun copyRecursiveWithProgress(
    context: Context,
    source: DocumentFile,
    target: DocumentFile,
    onProgress: (ProgressState) -> Unit,
    total: Int,
    current: IntArray,
    startTime: Long
) {
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
                    processedUnits = "${current[0]} / $total files"
                )
            )
        }

        if (item.isDirectory) {
            val newDir = target.createDirectory(item.name ?: "dir")
            if (newDir != null) copyRecursiveWithProgress(context, item, newDir, onProgress, total, current, startTime)
        } else {
            val newFile = target.createFile(item.type ?: "application/octet-stream", item.name ?: "file")
            if (newFile != null) {
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}

fun createDirectoryChain(parent: DocumentFile, path: String): DocumentFile? {
    var current = parent
    path.split("/").filter { it.isNotEmpty() }.forEach { segment ->
        val next = current.findFile(segment) ?: current.createDirectory(segment)
        current = next ?: return null
    }
    return current
}

fun createFileChain(parent: DocumentFile, filePath: String): DocumentFile? {
    val segments = filePath.split("/")
    val fileName = segments.last()
    val dirPath = segments.dropLast(1).joinToString("/")
    val dir = if (dirPath.isEmpty()) parent else createDirectoryChain(parent, dirPath) ?: return null
    val extension = fileName.substringAfterLast('.', "")
    val mimeType = if (extension.isNotEmpty()) MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream" else "application/octet-stream"
    return dir.findFile(fileName) ?: dir.createFile(mimeType, fileName)
}

private fun normalizeZipEntryPath(rawName: String): String? {
    val parts = rawName
        .replace('\\', '/')
        .split("/")
        .filter { it.isNotBlank() }

    if (parts.isEmpty()) return null
    if (parts.any { it == "." || it == ".." || it.contains('\u0000') }) return null

    return parts.joinToString("/")
}

private fun isIgnoredZipPath(path: String): Boolean {
    return path == ".DS_Store" ||
        path.startsWith("__MACOSX/") ||
        path.endsWith("/.DS_Store")
}

private fun String.relativeTo(root: String): String? {
    return when {
        root.isEmpty() -> this
        this == root -> ""
        startsWith("$root/") -> removePrefix("$root/")
        else -> null
    }
}

private fun String.fileName(): String = substringAfterLast("/")

private fun String.parentPath(): String = substringBeforeLast("/", "")

private fun String.firstSegmentOrNull(): String? = split("/").firstOrNull { it.isNotBlank() }

private fun String.pathDepth(): Int = if (isEmpty()) 0 else count { it == '/' } + 1

private fun sanitizeDocumentName(name: String): String {
    return name
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .trim()
        .trim('.')
}

private fun createUniqueDirectory(parent: DocumentFile, preferredName: String): DocumentFile? {
    val baseName = sanitizeDocumentName(preferredName).ifBlank { "Mod" }
    val existing = parent.findFile(baseName)
    if (existing == null) return parent.createDirectory(baseName)
    if (existing.isDirectory) return existing

    for (index in 2..100) {
        val candidate = "$baseName ($index)"
        if (parent.findFile(candidate) == null) {
            return parent.createDirectory(candidate)
        }
    }
    return null
}

private fun getDisplayName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex)
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}
