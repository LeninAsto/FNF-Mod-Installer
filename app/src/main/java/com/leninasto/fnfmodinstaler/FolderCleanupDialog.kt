package com.leninasto.fnfmodinstaler

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class CleanupFolder(val document: DocumentFile, val path: String)

@Composable
fun FolderCleanupDialog(folderUri: String, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var folders by remember(folderUri) { mutableStateOf<List<CleanupFolder>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var deleting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<CleanupFolder?>(null) }
    var revision by remember { mutableIntStateOf(0) }

    LaunchedEffect(folderUri, revision) {
        loading = true
        failed = false
        try {
            folders = withContext(Dispatchers.IO) {
                val root = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
                    ?: error("Folder unavailable")
                check(root.canRead())
                buildList {
                    for (child in root.listFiles().filter { it.isDirectory }) {
                        val name = child.name.orEmpty()
                        if (name == "mods" || name == "mods_disabled") {
                            for (mod in child.listFiles().filter { it.isDirectory }) {
                                add(CleanupFolder(mod, "$name/${mod.name}"))
                            }
                        } else {
                            add(CleanupFolder(child, name))
                        }
                    }
                }.sortedBy { it.path.lowercase() }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text(stringResource(R.string.cleanup_folders)) },
        text = {
            Column {
                Text(stringResource(R.string.cleanup_description))
                if (loading || deleting) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                if (failed) Text(stringResource(R.string.operation_failed), color = MaterialTheme.colorScheme.error)
                if (!loading && !failed && folders.isEmpty()) Text(stringResource(R.string.cleanup_empty))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(folders, key = { it.document.uri.toString() }) { folder ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(folder.path, Modifier.weight(1f).padding(vertical = 8.dp))
                            TextButton(enabled = !loading && !deleting, onClick = { selected = folder }) {
                                Text(stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !deleting, onClick = onDismiss) { Text(stringResource(R.string.finish)) } },
        dismissButton = {
            TextButton(enabled = !loading && !deleting, onClick = { revision++ }) { Text(stringResource(R.string.cleanup_refresh)) }
        }
    )

    selected?.let { folder ->
        AlertDialog(
            onDismissRequest = { if (!deleting) selected = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.cleanup_confirm, folder.path)) },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    deleting = true
                    failed = false
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                deleteDocumentRecursiveWithProgress(folder.document, {}, 1, intArrayOf(0), System.currentTimeMillis())
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            failed = true
                        } finally {
                            deleting = false
                            selected = null
                            onChanged()
                        }
                        // Refresh successful and partially deleted folders without hiding a deletion error.
                        if (!failed) revision++
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { selected = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
