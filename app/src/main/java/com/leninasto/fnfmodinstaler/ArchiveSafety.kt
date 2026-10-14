package com.leninasto.fnfmodinstaler

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.security.DigestInputStream
import java.security.MessageDigest

internal const val MAX_ARCHIVE_ENTRIES = 50_000
internal const val MAX_ENTRY_BYTES = 512L * 1024 * 1024
internal const val MAX_INSTALL_BYTES = 2L * 1024 * 1024 * 1024
internal const val MAX_METADATA_BYTES = 1024 * 1024

internal fun normalizeZipEntryPath(rawName: String): String? {
    val normalized = rawName.replace('\\', '/')
    if (normalized.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(normalized)) return null
    val parts = normalized.removeSuffix("/").split('/')
    if (parts.isEmpty() || parts.size > 32 || normalized.length > 1024) return null
    if (parts.any { it.isBlank() || it == "." || it == ".." || it.any { c -> c.code < 32 || c == ':' } }) return null
    return parts.joinToString("/")
}

/** Enforce actual decompressed bytes; ZIP headers are not trusted. */
internal class CopyBudget {
    private var bytes = 0L
    private var entries = 0
    fun entry() {
        if (++entries > MAX_ARCHIVE_ENTRIES) throw IOException("Too many files")
    }
    fun copy(input: InputStream, output: OutputStream?, checkCancelled: () -> Unit = {}): Long {
        var entryBytes = 0L
        val buffer = ByteArray(64 * 1024)
        while (true) {
            checkCancelled()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            entryBytes += count
            bytes += count
            if (entryBytes > MAX_ENTRY_BYTES || bytes > MAX_INSTALL_BYTES) throw IOException("Mod exceeds safe size limits")
            output?.write(buffer, 0, count)
        }
        return entryBytes
    }
}

internal fun readMetadata(input: InputStream): String {
    return input.readBytesLimited(MAX_METADATA_BYTES).toString(Charsets.UTF_8)
}

internal fun InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (output.size() + count > limit) throw IOException("File too large")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

/** Read the created file back before treating a copy as complete. */
internal suspend fun writeVerified(context: Context, input: InputStream, destination: Uri, budget: CopyBudget) {
    val coroutine = currentCoroutineContext()
    val sourceHash = MessageDigest.getInstance("SHA-256")
    val written = context.contentResolver.openOutputStream(destination, "wt")?.use { output ->
        budget.copy(DigestInputStream(input, sourceHash), output) { coroutine.ensureActive() }
    } ?: throw IOException("Cannot write file")
    val targetHash = MessageDigest.getInstance("SHA-256")
    val verified = context.contentResolver.openInputStream(destination)?.use { stream ->
        CopyBudget().copy(DigestInputStream(stream, targetHash), null) { coroutine.ensureActive() }
    } ?: throw IOException("Cannot verify copied file")
    if (written != verified || !MessageDigest.isEqual(sourceHash.digest(), targetHash.digest())) throw IOException("Copy verification failed")
}
