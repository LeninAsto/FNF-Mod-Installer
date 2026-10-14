package com.leninasto.fnfmodinstaler

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ModCard(mod: ModMetadata, isPolymod: Boolean, rootEngineUri: String, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteProgress by remember { mutableStateOf(ProgressState()) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (mod.isEnabled) 1f else 0.6f)
            .clickable {
                val intent = Intent(context, ModViewActivity::class.java).apply {
                    putExtra("folder_uri", mod.dir.uri.toString())
                    putExtra("is_polymod", isPolymod)
                    putExtra("root_uri", rootEngineUri)
                }
                context.startActivity(intent)
            },
        elevation = CardDefaults.cardElevation(if (mod.isEnabled) 2.dp else 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (mod.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                if (mod.icon != null) {
                    Image(
                        bitmap = mod.icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
                if (!mod.isEnabled) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(0.4f), CircleShape).padding(4.dp).size(16.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = mod.title + if (mod.isEnabled) "" else " (Disabled)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (mod.isEnabled) Color.Unspecified else MaterialTheme.colorScheme.outline
                )
                if (mod.author.isNotEmpty()) Text(stringResource(R.string.author, mod.author), style = MaterialTheme.typography.bodySmall)
                if (isPolymod) {
                    Text(stringResource(compatibilityMessage(mod.compatibility)), color = if (mod.compatibility == ModCompatibility.MATCH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }

            FilledTonalIconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!deleteProgress.isRunning) showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                if (deleteProgress.isRunning) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(progress = { deleteProgress.percentage })
                        Spacer(Modifier.height(8.dp))
                        Text("${(deleteProgress.percentage * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Text(deleteProgress.currentFile, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
                        Text(stringResource(R.string.time_remaining, deleteProgress.timeRemaining), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                } else {
                    Text(stringResource(R.string.delete_mod_confirm), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                if (!deleteProgress.isRunning) {
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val total = countFilesRecursive(mod.dir)
                                deleteDocumentRecursiveWithProgress(mod.dir, { deleteProgress = it }, total,
                                    intArrayOf(0), System.currentTimeMillis())
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, R.string.operation_failed, android.widget.Toast.LENGTH_LONG).show()
                                }
                            } finally {
                                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                                    deleteProgress = ProgressState()
                                    showDeleteDialog = false
                                    onRefresh()
                                }
                            }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text(stringResource(R.string.delete))
                    }
                }
            },
            dismissButton = {
                if (!deleteProgress.isRunning) {
                    TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }
}
