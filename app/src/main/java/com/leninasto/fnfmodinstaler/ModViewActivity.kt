package com.leninasto.fnfmodinstaler

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.documentfile.provider.DocumentFile
import com.leninasto.fnfmodinstaler.ui.theme.FNFModInstalerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val folderUri = intent.getStringExtra("folder_uri") ?: return finish()
        val rootUri = intent.getStringExtra("root_uri") ?: return finish()
        val isPolymod = intent.getBooleanExtra("is_polymod", false)
        
        setContent {
            FNFModInstalerTheme {
                ModViewScreen(folderUri, rootUri, isPolymod, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModViewScreen(folderUri: String, rootUri: String, isPolymod: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var modMetadata by remember { mutableStateOf<ModMetadata?>(null) }
    var currentUri by remember { mutableStateOf(folderUri) }
    var showEditDialog by remember { mutableStateOf(false) }
    var toggleProgress by remember { mutableStateOf(ProgressState()) }

    fun refresh(newUri: String = currentUri) {
        val dir = DocumentFile.fromTreeUri(context, Uri.parse(newUri))
        val rootDir = DocumentFile.fromTreeUri(context, Uri.parse(rootUri))
        if (dir != null) {
            modMetadata = loadModMetadata(context, dir, isPolymod, rootDir)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val dir = DocumentFile.fromTreeUri(context, Uri.parse(currentUri))
            if (dir != null) {
                scope.launch(Dispatchers.IO) {
                    saveModIcon(context, dir, isPolymod, it)
                    withContext(Dispatchers.Main) {
                        refresh()
                    }
                }
            }
        }
    }

    LaunchedEffect(currentUri) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mod_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        modMetadata?.let { mod ->
            if (showEditDialog) {
                EditMetadataDialog(mod, isPolymod, onDismiss = { showEditDialog = false }, onSave = { refresh() })
            }

            if (toggleProgress.isRunning) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(if (mod.isEnabled) "Disabling Mod..." else "Enabling Mod...", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(progress = { toggleProgress.percentage })
                            Spacer(Modifier.height(8.dp))
                            Text("${(toggleProgress.percentage * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                            Text(toggleProgress.currentFile, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
                            Text("Remaining: ${toggleProgress.timeRemaining}", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        }
                    },
                    confirmButton = {}
                )
            }

            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (mod.icon != null) {
                        Image(
                            bitmap = mod.icon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(if (isPolymod) 250.dp else 180.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                    
                    SmallFloatingActionButton(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Change Icon")
                    }
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    Surface(
                        tonalElevation = 2.dp,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (mod.isEnabled) "Mod Enabled" else "Mod Disabled",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isPolymod) {
                                        if (mod.isEnabled) "Visible for the game." else "Moved to 'mods_disabled' folder."
                                    } else {
                                        if (mod.isEnabled) "Active in modsList.txt" else "Inactive in modsList.txt"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = mod.isEnabled,
                                onCheckedChange = {
                                    scope.launch {
                                        val rootDir = DocumentFile.fromTreeUri(context, Uri.parse(rootUri))
                                        if (rootDir != null) {
                                            val success = toggleModStatusWithProgress(context, mod.dir, rootDir, isPolymod) {
                                                toggleProgress = it
                                            }
                                            if (success) {
                                                toggleProgress = ProgressState()
                                                Toast.makeText(context, "Mod status updated!", Toast.LENGTH_SHORT).show()
                                                if (isPolymod) {
                                                    onBack()
                                                } else {
                                                    refresh()
                                                }
                                            } else {
                                                toggleProgress = ProgressState()
                                                Toast.makeText(context, "Error updating mod status", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }

                    Text(mod.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    
                    if (isPolymod) {
                        InfoRow(Icons.Default.Person, "Author", mod.author)
                        InfoRow(Icons.Default.Numbers, "Version", mod.version)
                        InfoRow(Icons.Default.Code, "API Version", mod.apiVersion)
                        InfoRow(Icons.Default.Description, "License", mod.license)
                    } else {
                        InfoRow(Icons.Default.Numbers, "Version", mod.version)
                        InfoRow(Icons.Default.Link, "Discord RPC", mod.discordRPC)
                        InfoRow(Icons.Default.Refresh, "Restart on Change", if (mod.restart) "Yes" else "No")
                        InfoRow(Icons.Default.Public, "Runs Globally", if (mod.runsGlobally) "Yes" else "No")
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.description), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(mod.description, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    if (value.isNotEmpty()) {
        Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("$label: ", fontWeight = FontWeight.Bold)
            Text(value)
        }
    }
}
