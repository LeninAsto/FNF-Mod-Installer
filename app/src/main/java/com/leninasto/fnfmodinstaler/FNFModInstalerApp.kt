package com.leninasto.fnfmodinstaler

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FNFModInstalerApp(incomingZip: Uri?, onDismissZip: () -> Unit, installState: MainActivity) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fnf_prefs", Context.MODE_PRIVATE) }
    val folderUris = remember { mutableStateMapOf<String, String?>() }
    var pendingFolderKey by rememberSaveable { mutableStateOf<String?>(null) }

    var manualInstallationUri by remember { mutableStateOf<Uri?>(null) }
    var isManualFolder by remember { mutableStateOf(false) }

    var enabledDestinations by remember {
        mutableStateOf(
            AppDestinations.entries.filter {
                defaultEngineEnabled(context, prefs, it)
            }
        )
    }

    var currentDestination by remember {
        mutableStateOf(if (enabledDestinations.contains(AppDestinations.ORIGINAL)) AppDestinations.ORIGINAL else enabledDestinations.firstOrNull() ?: AppDestinations.INFO)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                syncInstalledEngines(context, prefs)
                val updated = AppDestinations.entries.filter {
                    defaultEngineEnabled(context, prefs, it)
                }
                enabledDestinations = updated
                AppDestinations.entries.forEach { dest ->
                    folderUris[dest.name] = prefs.getString("folder_uri_${dest.name}", null)
                    folderUris["${dest.name}_ALT"] = prefs.getString("folder_uri_${dest.name}_ALT", null)
                }
                if (!updated.contains(currentDestination)) {
                    currentDestination = updated.firstOrNull() ?: AppDestinations.INFO
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        syncInstalledEngines(context, prefs)
        AppDestinations.entries.forEach { dest ->
            folderUris[dest.name] = prefs.getString("folder_uri_${dest.name}", null)
            if (dest == AppDestinations.PSLICE_ENGINE || dest == AppDestinations.PLUS_ENGINE) {
                folderUris["${dest.name}_ALT"] = prefs.getString("folder_uri_${dest.name}_ALT", null)
                folderUris["${dest.name}_ACTIVE_IS_ALT"] = prefs.getString("${dest.name}_ACTIVE_IS_ALT", "false")
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val key = pendingFolderKey
        pendingFolderKey = null
        if (uri != null && key != null) {
            if (bindEngineFolder(context, key, uri)) folderUris[key] = uri.toString()
            else android.widget.Toast.makeText(context, R.string.folder_permission_error, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    fun pickFolder(key: String) {
        pendingFolderKey = key
        folderPicker.launch(null)
    }

    @Composable
    fun getIconTint(dest: AppDestinations): Color {
        return if (dest == AppDestinations.ORIGINAL) LocalContentColor.current else Color.Unspecified
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val activeUri = incomingZip ?: manualInstallationUri
    val activeIsFolder = if (incomingZip != null) false else isManualFolder
    val onDismissActive = {
        if (incomingZip != null) onDismissZip()
        manualInstallationUri = null
    }

    activeUri?.let { uri ->
        if (currentDestination == AppDestinations.INFO) {
            AlertDialog(onDismissRequest = onDismissActive,
                title = { Text(stringResource(R.string.choose_install_engine)) },
                text = { Column { enabledDestinations.filterNot { it.isStatic }.forEach { dest ->
                    TextButton(onClick = { currentDestination = dest }) { Text(stringResource(dest.labelRes)) }
                } } },
                confirmButton = { TextButton(onClick = onDismissActive) { Text(stringResource(R.string.cancel)) } })
        } else ModInstallationDialog(
            sourceUri = uri,
            isFolder = activeIsFolder,
            targetEngineName = stringResource(currentDestination.labelRes),
            targetFolderUri = folderUris[activeEngineFolderKey(currentDestination, folderUris)],
            targetEngine = currentDestination,
            onSetupFolder = { pickFolder(activeEngineFolderKey(currentDestination, folderUris)) },
            onDismiss = onDismissActive,
            installState = installState
        )
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            enabledDestinations.forEach { dest ->
                item(
                    icon = {
                        if (dest.iconRes != null) {
                            Icon(painter = painterResource(id = dest.iconRes), contentDescription = null, modifier = Modifier.size(24.dp), tint = getIconTint(dest))
                        } else if (dest.materialIcon != null) {
                            Icon(imageVector = dest.materialIcon, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    },
                    label = { Text(stringResource(dest.labelRes)) },
                    selected = dest == currentDestination,
                    onClick = { if (!installState.progressState.isRunning) currentDestination = dest }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        val isCollapsed = scrollBehavior.state.collapsedFraction > 0.5f

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isCollapsed) Alignment.Start else Alignment.CenterHorizontally
                        ) {
                            if (!isCollapsed) {
                                val iconResId = currentDestination.iconRes
                                val materialIcon = currentDestination.materialIcon
                                if (iconResId != null) {
                                    Icon(painter = painterResource(id = iconResId), contentDescription = null, modifier = Modifier.size(48.dp), tint = getIconTint(currentDestination))
                                } else if (materialIcon != null) {
                                    Icon(imageVector = materialIcon, contentDescription = null, modifier = Modifier.size(48.dp))
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isCollapsed) {
                                    val iconResId = currentDestination.iconRes
                                    val materialIcon = currentDestination.materialIcon
                                    if (iconResId != null) {
                                        Icon(painter = painterResource(id = iconResId), contentDescription = null, modifier = Modifier.size(24.dp).padding(end = 8.dp), tint = getIconTint(currentDestination))
                                    } else if (materialIcon != null) {
                                        Icon(imageVector = materialIcon, contentDescription = null, modifier = Modifier.size(24.dp).padding(end = 8.dp))
                                    }
                                }
                                Text(
                                    stringResource(currentDestination.labelRes),
                                    textAlign = if (isCollapsed) TextAlign.Start else TextAlign.Center,
                                    style = if (isCollapsed) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium
                                )
                            }

                            if (installState.progressState.isRunning) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    LinearProgressIndicator(
                                        progress = { installState.progressState.percentage },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                                        Text("${(installState.progressState.percentage * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                                        Text(installState.progressState.timeRemaining, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(enabled = !installState.progressState.isRunning, onClick = { context.startActivity(Intent(context, OptionsActivity::class.java)) }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            },
            floatingActionButton = {
                if (currentDestination != AppDestinations.INFO) {
                    var fabExpanded by remember { mutableStateOf(false) }
                    val manualZipPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                        uri?.let { manualInstallationUri = it; isManualFolder = false }
                    }
                    val manualFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                        uri?.let { manualInstallationUri = it; isManualFolder = true }
                    }

                    FloatingActionButtonMenu(
                        expanded = fabExpanded,
                        button = {
                            ToggleFloatingActionButton(
                                checked = fabExpanded,
                                onCheckedChange = { fabExpanded = it }
                            ) {
                                Icon(Icons.Default.Add, stringResource(R.string.add_mod))
                            }
                        }
                    ) {
                        FloatingActionButtonMenuItem(
                            onClick = {
                                manualFolderPicker.launch(null)
                                fabExpanded = false
                            },
                            icon = { Icon(Icons.Default.Folder, null) },
                            text = { Text(stringResource(R.string.select_folder)) }
                        )
                        FloatingActionButtonMenuItem(
                            onClick = {
                                manualZipPicker.launch("application/zip")
                                fabExpanded = false
                            },
                            icon = { Icon(Icons.Default.FilePresent, null) },
                            text = { Text(stringResource(R.string.select_zip)) }
                        )
                    }
                }
            }
        ) { padding ->
            androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                run {
                    when (currentDestination) {
                        AppDestinations.INFO -> InfoScreen()
                        AppDestinations.PSLICE_ENGINE, AppDestinations.PLUS_ENGINE -> DualStorageEngineScreen(
                            engine = currentDestination,
                            folderUris = folderUris,
                            refreshToken = installState.installationRevision,
                            onBindPrimary = {
                                folderUris["${currentDestination.name}_PICKING_ALT"] = "false"
                                pickFolder(currentDestination.name)
                            },
                            onBindAlt = {
                                folderUris["${currentDestination.name}_PICKING_ALT"] = "true"
                                pickFolder("${currentDestination.name}_ALT")
                            },
                            onTogglePath = { isAlt ->
                                val strValue = isAlt.toString()
                                prefs.edit { putString("${currentDestination.name}_ACTIVE_IS_ALT", strValue) }
                                folderUris["${currentDestination.name}_ACTIVE_IS_ALT"] = strValue
                            }
                        )
                        else -> EngineScreen(
                            engine = currentDestination,
                            folderUri = folderUris[currentDestination.name],
                            refreshToken = installState.installationRevision,
                            onBind = { pickFolder(currentDestination.name) }
                        )
                    }
                }
            }
        }
    }
}
