package com.leninasto.fnfmodinstaler

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

enum class ModCompatibility { MATCH, MISMATCH, UNKNOWN, INVALID, WRONG_ENGINE }

internal data class ApiVersion(val major: Int, val minor: Int, val patch: Int) {
    companion object {
        fun parse(value: String): ApiVersion? {
            val parts = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$").matchEntire(value.trim())?.groupValues ?: return null
            return ApiVersion(parts[1].toIntOrNull() ?: return null, parts[2].toIntOrNull() ?: return null, parts[3].toIntOrNull() ?: return null)
        }
    }
}

/** Verified from FunkinCrew/Funkin v0.8.1–v0.8.4 PolymodHandler.hx.
 * Unknown ports/future builds stay UNKNOWN, rather than inventing a Polymod version.
 */
internal fun assessApi(api: String, gameVersion: String?, official: Boolean): ModCompatibility {
    val mod = ApiVersion.parse(api) ?: return ModCompatibility.INVALID
    val game = gameVersion?.let { ApiVersion.parse(it) } ?: return ModCompatibility.UNKNOWN
    if (!official || game.major != 0 || game.minor != 8 || game.patch !in 1..4) return ModCompatibility.UNKNOWN
    return if (mod.major == 0 && mod.minor == 8) ModCompatibility.MATCH else ModCompatibility.MISMATCH
}

internal fun installedFunkinApi(context: Context, api: String): ModCompatibility {
    return try {
        val info = context.packageManager.getPackageInfo("me.funkin.fnf", 0)
        assessApi(api, info.versionName, official = true)
    } catch (_: Exception) { assessApi(api, null, official = false) }
}

fun compatibilityMessage(status: ModCompatibility): Int = when (status) {
    ModCompatibility.MATCH -> R.string.compatibility_match
    ModCompatibility.MISMATCH -> R.string.compatibility_mismatch
    ModCompatibility.INVALID -> R.string.compatibility_invalid
    ModCompatibility.WRONG_ENGINE -> R.string.compatibility_wrong_engine
    ModCompatibility.UNKNOWN -> R.string.compatibility_unknown
}

internal suspend fun inspectModCompatibility(context: Context, uri: Uri, folder: Boolean, engine: AppDestinations): ModCompatibility = withContext(Dispatchers.IO) {
    val metadata: String?
    val polymod: Boolean
    if (folder) {
        val dir = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Cannot read folder")
        val poly = dir.findFile("_polymod_meta.json")
        val file = poly ?: dir.findFile("pack.json")
        polymod = poly != null
        metadata = file?.let { doc -> context.contentResolver.openInputStream(doc.uri)?.use { readMetadata(it) } }
    } else {
        val entries = mutableListOf<ZipEntryInfo>()
        val documents = mutableMapOf<String, String>()
        val budget = CopyBudget()
        val coroutine = currentCoroutineContext()
        val started = System.currentTimeMillis()
        val paths = mutableSetOf<String>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    coroutine.ensureActive()
                    val entry = zip.nextEntry ?: break
                    budget.entry()
                    val path = normalizeZipEntryPath(entry.name) ?: throw IOException("Unsafe ZIP path")
                    if (!paths.add(path)) throw IOException("Duplicate ZIP entry")
                    entries += ZipEntryInfo(path, entry.isDirectory)
                    val capture = if (!entry.isDirectory && path.substringAfterLast('/') in setOf("pack.json", "_polymod_meta.json")) ByteArrayOutputStream() else null
                    budget.copy(zip, capture) {
                        coroutine.ensureActive()
                        if (System.currentTimeMillis() - started > 120_000) throw IOException("ZIP scan timed out")
                        if (capture != null && capture.size() > MAX_METADATA_BYTES) throw IOException("Metadata too large")
                    }
                    capture?.let {
                        if (it.size() > MAX_METADATA_BYTES) throw IOException("Metadata too large")
                        documents[path] = it.toString("UTF-8")
                    }
                    zip.closeEntry()
                }
            }
        } ?: throw IOException("Cannot read ZIP")
        val plan = planZipInstall(entries, null) ?: throw IOException("Empty ZIP or multiple mods")
        val prefix = if (plan.sourceRoot.isEmpty()) "" else "${plan.sourceRoot}/"
        polymod = documents.containsKey("${prefix}_polymod_meta.json")
        metadata = documents["${prefix}_polymod_meta.json"] ?: documents["${prefix}pack.json"]
    }
    if (metadata == null) return@withContext ModCompatibility.UNKNOWN
    val json = JSONObject(metadata)
    if ((engine == AppDestinations.ORIGINAL) != polymod) return@withContext ModCompatibility.WRONG_ENGINE
    if (polymod) installedFunkinApi(context, json.optString("api_version")) else ModCompatibility.UNKNOWN
}
