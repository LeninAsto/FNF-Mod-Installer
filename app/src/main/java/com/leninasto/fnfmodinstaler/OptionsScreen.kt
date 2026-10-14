package com.leninasto.fnfmodinstaler

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun OptionsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fnf_prefs", Context.MODE_PRIVATE) }
    val destinations = AppDestinations.entries.filter { !it.isStatic }
    var showChanges by remember { mutableStateOf(false) }
    if (showChanges) ChangelogDialog { showChanges = false }

    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        item {
            UpdateCard()
            var auto by remember { mutableStateOf(prefs.getBoolean("auto_updates", true)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.auto_updates_label), Modifier.weight(1f))
                Switch(checked = auto, onCheckedChange = {
                    auto = it
                    prefs.edit().putBoolean("auto_updates", it).apply()
                })
            }
            TextButton(onClick = { showChanges = true }) { Text(stringResource(R.string.changelog_label)) }
            TextButton(onClick = {
                prefs.edit().putBoolean("onboarding_complete", false).apply()
                (context as? android.app.Activity)?.finish()
            }) { Text(stringResource(R.string.setup_again)) }
            Text(stringResource(R.string.manage_engines), style = MaterialTheme.typography.titleLarge)
        }
        items(destinations) { dest ->
            val installed = remember(dest) { isEngineInstalled(context, dest) }
            var enabled by remember { mutableStateOf(defaultEngineEnabled(context, prefs, dest)) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(dest.labelRes))
                    if (installed) {
                        Text(
                            stringResource(R.string.engine_detected),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        enabled = checked
                        prefs.edit().putBoolean("engine_enabled_${dest.name}", checked).apply()
                    }
                )
            }
            var linked by remember { mutableStateOf(prefs.getString("folder_uri_${dest.name}", null) != null || prefs.getString("folder_uri_${dest.name}_ALT", null) != null) }
            if (linked) TextButton(onClick = {
                unlinkEngineFolder(context, dest.name)
                unlinkEngineFolder(context, "${dest.name}_ALT")
                linked = false
            }) { Text(stringResource(R.string.unlink_folder)) }
        }
    }
}
