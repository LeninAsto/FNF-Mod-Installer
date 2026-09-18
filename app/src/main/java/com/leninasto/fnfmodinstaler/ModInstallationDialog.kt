package com.leninasto.fnfmodinstaler

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.launch

@Composable
fun ModInstallationDialog(
    sourceUri: Uri,
    isFolder: Boolean,
    targetEngineName: String,
    targetFolderUri: String?,
    onSetupFolder: () -> Unit,
    onDismiss: () -> Unit,
    installState: MainActivity
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var complete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!installState.progressState.isRunning) onDismiss() },
        title = { Text(if (complete) stringResource(R.string.install_ready) else stringResource(R.string.install_mod_title), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (complete) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.mod_ready_msg, targetEngineName), textAlign = TextAlign.Center)
                } else if (installState.progressState.isRunning) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(progress = { installState.progressState.percentage })
                        Spacer(Modifier.height(8.dp))
                        Text("${(installState.progressState.percentage * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Text(installState.progressState.currentFile, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
                        Text("Time remaining: ${installState.progressState.timeRemaining}", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        Text(installState.progressState.processedUnits, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                } else {
                    Text(stringResource(R.string.engine_label, targetEngineName), textAlign = TextAlign.Center)
                    if (targetFolderUri == null || !isUriPermissionValid(context, targetFolderUri)) {
                        Text(stringResource(R.string.error_folder_not_linked), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Button(onClick = onSetupFolder, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.link_now)) }
                    } else {
                        Button(onClick = {
                            scope.launch {
                                val rootDoc = DocumentFile.fromTreeUri(context, targetFolderUri.toUri())
                                val targetUri = rootDoc?.findFile("mods")?.uri ?: targetFolderUri.toUri()

                                val success = if (isFolder) {
                                    copyFolderWithProgress(context, sourceUri, targetUri) { installState.progressState = it }
                                } else {
                                    extractZipWithProgress(context, sourceUri, targetUri) { installState.progressState = it }
                                }

                                installState.progressState = ProgressState()
                                if (success) {
                                    complete = true
                                } else {
                                    Toast.makeText(context, context.getString(R.string.install_error), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.confirm)) }
                    }
                }
            }
        },
        confirmButton = {
            if (complete) Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.finish)) }
            else if (!installState.progressState.isRunning) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
