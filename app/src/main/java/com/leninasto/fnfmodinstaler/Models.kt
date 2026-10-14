package com.leninasto.fnfmodinstaler

import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.documentfile.provider.DocumentFile

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
    val isEnabled: Boolean,
    val compatibility: ModCompatibility = ModCompatibility.UNKNOWN
)

data class ProgressState(
    val isRunning: Boolean = false,
    val percentage: Float = 0f,
    val currentFile: String = "",
    val timeRemaining: String = "Calculating...",
    val speed: String = "",
    val processedUnits: String = ""
)

enum class AppDestinations(
    val labelRes: Int,
    val iconRes: Int? = null,
    val materialIcon: ImageVector? = null,
    val isStatic: Boolean = false,
    val externalDirName: String? = null,
    val packageCandidates: List<String> = emptyList()
) {
    ORIGINAL(
        R.string.dest_funkin,
        iconRes = R.drawable.ic_funkin,
        packageCandidates = listOf(
            "me.funkin.fnf",
            "com.ninjamuffin99.funkin",
            "com.fnf.mobile",
            "com.leninasto.funkinmobile"
        )
    ),
    PLUS_ENGINE(
        R.string.dest_plus,
        iconRes = R.drawable.ic_plusengine,
        externalDirName = ".PlusEngine",
        packageCandidates = listOf("com.leninasto.plusengine")
    ),
    PSYCH_ENGINE(
        R.string.dest_psych,
        iconRes = R.drawable.ic_psych,
        externalDirName = ".PsychEngine",
        packageCandidates = listOf(
            "com.shadowmario.psychengine",
            "com.psychengine.mobile",
            "com.leninasto.psychengine"
        )
    ),
    NOVAFLARE_ENGINE(
        R.string.dest_novaflare,
        iconRes = R.drawable.ic_nfengine,
        externalDirName = ".NovaFlare",
        packageCandidates = listOf(
            "com.novaflare.engine",
            "com.leninasto.novaflare"
        )
    ),
    PSLICE_ENGINE(
        R.string.dest_pslice,
        iconRes = R.drawable.ic_pslice,
        externalDirName = ".PSliceEngine",
        packageCandidates = listOf(
            "com.pslice.engine",
            "com.leninasto.pslice"
        )
    ),
    INFO(R.string.dest_info, materialIcon = Icons.Default.Info, isStatic = true),
}
