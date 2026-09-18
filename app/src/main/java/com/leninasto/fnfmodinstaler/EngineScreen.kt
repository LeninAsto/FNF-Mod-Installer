package com.leninasto.fnfmodinstaler

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineScreen(engine: AppDestinations, folderUri: String?, onBind: () -> Unit) {
    val context = LocalContext.current
    var installedMods by remember(folderUri) { mutableStateOf<List<ModMetadata>>(emptyList()) }
    var isLoading by remember(folderUri) { mutableStateOf(folderUri != null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val engineInstalled = remember(engine) { isEngineInstalled(context, engine) }
    val scope = rememberCoroutineScope()

    fun refreshMods() {
        if (folderUri != null) {
            isRefreshing = true
            scope.launch(Dispatchers.IO) {
                try {
                    val root = DocumentFile.fromTreeUri(context, folderUri.toUri())
                    if (root != null && root.canRead()) {
                        val isPolymod = engine == AppDestinations.ORIGINAL
                        val modsDir = root.findFile("mods") ?: root

                        val activeMods = modsDir.listFiles().filter { it.isDirectory && it.name != "mods_disabled" }.map { dir ->
                            loadModMetadata(context, dir, isPolymod, root)
                        }

                        val disabledMods = if (isPolymod) {
                            val disabledDir = root.findFile("mods_disabled")
                            disabledDir?.listFiles()?.filter { it.isDirectory }?.map { dir ->
                                loadModMetadata(context, dir, isPolymod, root)
                            } ?: emptyList()
                        } else {
                            emptyList()
                        }

                        installedMods = (activeMods + disabledMods).sortedBy { it.title }
                    } else {
                        installedMods = emptyList()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                isRefreshing = false
                isLoading = false
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshMods()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(folderUri) { refreshMods() }

    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { refreshMods() },
        state = pullToRefreshState
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (folderUri == null || !isUriPermissionValid(context, folderUri)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("⚠️ " + stringResource(R.string.folder_not_linked), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.link_folder_msg, stringResource(engine.labelRes)))
                            EngineActionsRow(
                                engine = engine,
                                folderUri = folderUri,
                                onBind = onBind,
                                engineInstalled = engineInstalled,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.linked_correctly), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onBind) { Text(stringResource(R.string.change)) }
                }
                EngineActionsRow(
                    engine = engine,
                    folderUri = folderUri,
                    onBind = onBind,
                    engineInstalled = engineInstalled,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(stringResource(R.string.installed_mods), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (installedMods.isEmpty()) {
                    Text(stringResource(R.string.no_mods_found), modifier = Modifier.padding(top = 8.dp))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        items(installedMods) { mod ->
                            ModCard(mod, isPolymod = engine == AppDestinations.ORIGINAL, rootEngineUri = folderUri, onRefresh = { refreshMods() })
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineActionsRow(
    engine: AppDestinations,
    folderUri: String?,
    onBind: () -> Unit,
    engineInstalled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        if (engineInstalled) {
            Button(
                onClick = {
                    if (!openEngineGame(context, engine)) {
                        showEngineNotInstalledToast(context)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.SportsEsports, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.open_game))
            }
        }

        OutlinedButton(
            onClick = {
                if (!openLinkedFolder(context, folderUri)) {
                    onBind()
                    if (folderUri != null) showFolderOpenErrorToast(context)
                }
            },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.FolderOpen, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (folderUri == null) R.string.link_folder else R.string.open_folder))
        }
    }
}

@Composable
fun DualStorageEngineScreen(
    engine: AppDestinations,
    folderUris: Map<String, String?>,
    onBindPrimary: () -> Unit,
    onBindAlt: () -> Unit,
    onTogglePath: (Boolean) -> Unit
) {
    val activeIsAltKey = "${engine.name}_ACTIVE_IS_ALT"
    val altUriKey = "${engine.name}_ALT"

    val isAlt = folderUris[activeIsAltKey] == "true"
    val activeUri = if (isAlt) folderUris[altUriKey] else folderUris[engine.name]

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.engine_storage_config, stringResource(engine.labelRes)), style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = !isAlt, onClick = { onTogglePath(false) })
                        Text(stringResource(R.string.scoped_storage))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = isAlt, onClick = { onTogglePath(true) })
                        val extName = engine.externalDirName ?: ""
                        Text(stringResource(R.string.external_storage, extName))
                    }
                }
            }
        }
        EngineScreen(engine = engine, folderUri = activeUri, onBind = { if (isAlt) onBindAlt() else onBindPrimary() })
    }
}
