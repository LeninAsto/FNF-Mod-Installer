package com.leninasto.fnfmodinstaler

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun AppEntry(incomingZip: Uri?, onDismissZip: () -> Unit, installState: MainActivity) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fnf_prefs", Context.MODE_PRIVATE) }
    var ready by remember { mutableStateOf(prefs.getBoolean("onboarding_complete", false)) }
    var showChanges by rememberSaveable { mutableStateOf(false) }
    var showUpdate by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ready = prefs.getBoolean("onboarding_complete", false)
                if (ready) PlayUpdates.check(context, automatic = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(ready) {
        if (ready && shouldShowChangelog(prefs.getInt("last_seen_version", 0), BuildConfig.VERSION_CODE)) {
            showChanges = true
        }
        if (ready) PlayUpdates.check(context, automatic = true)
    }
    LaunchedEffect(ready, showChanges, PlayUpdates.status) {
        if (ready && !showChanges && PlayUpdates.status == UpdateStatus.AVAILABLE &&
            PlayUpdates.availableVersionCode > prefs.getInt("last_update_prompt", 0)) showUpdate = true
    }
    if (!ready) {
        WelcomeScreen {
            prefs.edit { putBoolean("onboarding_complete", true) }
            ready = true
        }
    } else {
        FNFModInstalerApp(incomingZip, onDismissZip, installState)
        if (showChanges) {
            ChangelogDialog {
                prefs.edit { putInt("last_seen_version", BuildConfig.VERSION_CODE) }
                showChanges = false
            }
        }
        if (showUpdate && !showChanges) {
            val dismiss = {
                prefs.edit { putInt("last_update_prompt", PlayUpdates.availableVersionCode) }
                showUpdate = false
            }
            AlertDialog(onDismissRequest = dismiss,
                title = { Text(stringResource(R.string.update_available)) },
                text = { Text(stringResource(R.string.update_description)) },
                confirmButton = { Button(onClick = {
                    if (PlayUpdates.openStore(context)) dismiss()
                    else Toast.makeText(context, R.string.store_open_error, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.open_play_store)) } },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.close)) } })
        }
    }
}

internal fun shouldShowChangelog(lastSeen: Int, installed: Int): Boolean = installed > lastSeen

@Composable
fun ChangelogDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whats_new_version, BuildConfig.VERSION_NAME)) },
        text = { Text(stringResource(R.string.release_notes), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.continue_label)) } }
    )
}

@Composable
private fun WelcomeScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fnf_prefs", Context.MODE_PRIVATE) }
    val engines = remember { AppDestinations.entries.filterNot { it.isStatic } }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable {
        mutableStateOf(engines.filter { defaultEngineEnabled(context, prefs, it) }.map { it.name })
    }
    var pendingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var folderRevision by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val key = pendingKey
        pendingKey = null
        if (uri != null && key != null) {
            if (!bindEngineFolder(context, key, uri)) {
                Toast.makeText(context, R.string.folder_permission_error, Toast.LENGTH_LONG).show()
            }
            folderRevision++
        }
    }
    Scaffold(bottomBar = {
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) OutlinedButton(onClick = { step-- }) { Text(stringResource(R.string.back_label)) }
                Button(
                    enabled = step != 1 || selected.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (step < 2) step++ else {
                            prefs.edit {
                                engines.forEach {
                                    putBoolean("engine_enabled_${it.name}", it.name in selected)
                                    putBoolean("engine_detected_${it.name}", isEngineInstalled(context, it))
                                }
                            }
                            onFinish()
                        }
                    }
                ) { Text(stringResource(if (step == 2) R.string.start_label else R.string.continue_label)) }
            }
        }
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.setup_step, step + 1), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(when (step) { 0 -> R.string.welcome_title; 1 -> R.string.choose_engines; else -> R.string.link_your_folders }), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(when (step) { 0 -> R.string.welcome_description; 1 -> R.string.choose_engines_description; else -> R.string.folder_access_description }))
            if (step == 0) {
                Card { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.welcome_steps), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.welcome_privacy))
                } }
            } else if (step == 1) engines.forEach { engine ->
                Card {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = engine.name in selected, onCheckedChange = { checked ->
                            selected = if (checked) selected + engine.name else selected - engine.name
                        })
                        Column {
                            Text(stringResource(engine.labelRes), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(if (isEngineInstalled(context, engine)) R.string.engine_detected else R.string.engine_manual), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                engines.filter { it.name in selected }.forEach { engine ->
                    val uri = remember(folderRevision, engine) { prefs.getString("folder_uri_${engine.name}", null) }
                    val linked = isUriPermissionValid(context, uri)
                    Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(engine.labelRes), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (linked) R.string.linked_correctly else R.string.folder_not_linked))
                        Text(stringResource(R.string.folder_location_hint), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { pendingKey = engine.name; picker.launch(null) }) {
                            Text(stringResource(if (linked) R.string.change else R.string.link_folder))
                        }
                    } }
                }
                Text(stringResource(R.string.link_later_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
