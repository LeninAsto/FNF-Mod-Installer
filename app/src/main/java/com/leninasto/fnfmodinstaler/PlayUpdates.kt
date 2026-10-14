package com.leninasto.fnfmodinstaler

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability

enum class UpdateStatus { IDLE, CHECKING, CURRENT, AVAILABLE, UNAVAILABLE }

/** Google Play reports the version available to this user's account and release track. */
object PlayUpdates {
    var status by mutableStateOf(UpdateStatus.IDLE)
        private set
    var availableVersionCode by mutableIntStateOf(0)
        private set

    fun check(context: Context, automatic: Boolean = false) {
        val prefs = context.getSharedPreferences("fnf_prefs", Context.MODE_PRIVATE)
        if (status == UpdateStatus.CHECKING) return
        if (automatic && (!prefs.getBoolean("auto_updates", true) ||
                System.currentTimeMillis() - prefs.getLong("last_update_check", 0) < 24 * 60 * 60 * 1000L)) return
        status = UpdateStatus.CHECKING
        try {
        AppUpdateManagerFactory.create(context.applicationContext).appUpdateInfo
            .addOnSuccessListener { info ->
                status = when (info.updateAvailability()) {
                    UpdateAvailability.UPDATE_AVAILABLE, UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> UpdateStatus.AVAILABLE
                    UpdateAvailability.UPDATE_NOT_AVAILABLE -> UpdateStatus.CURRENT
                    else -> UpdateStatus.UNAVAILABLE
                }
                availableVersionCode = if (status == UpdateStatus.AVAILABLE) info.availableVersionCode() else 0
                prefs.edit { putLong("last_update_check", System.currentTimeMillis()) }
            }
            .addOnFailureListener {
                status = UpdateStatus.UNAVAILABLE
                prefs.edit { putLong("last_update_check", System.currentTimeMillis()) }
            }
        } catch (_: Exception) {
            status = UpdateStatus.UNAVAILABLE
            prefs.edit { putLong("last_update_check", System.currentTimeMillis()) }
        }
    }

    fun openStore(context: Context): Boolean {
        val storePackage = "com.leninasto.fnfmodinstaler"
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$storePackage".toUri())
            .setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(market)
            true
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=$storePackage".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (_: Exception) { false }
        }
    }
}

@Composable
fun UpdateCard() {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.app_version_label, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(when (PlayUpdates.status) {
                UpdateStatus.CHECKING -> R.string.update_checking
                UpdateStatus.CURRENT -> R.string.update_current
                UpdateStatus.AVAILABLE -> R.string.update_available
                UpdateStatus.UNAVAILABLE -> R.string.update_unavailable
                else -> R.string.update_description
            }))
            OutlinedButton(onClick = { PlayUpdates.check(context) }, enabled = PlayUpdates.status != UpdateStatus.CHECKING) {
                Text(stringResource(R.string.check_updates))
            }
            TextButton(onClick = {
                if (!PlayUpdates.openStore(context)) android.widget.Toast.makeText(context, R.string.store_open_error, android.widget.Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.open_play_store)) }
        }
    }
}
