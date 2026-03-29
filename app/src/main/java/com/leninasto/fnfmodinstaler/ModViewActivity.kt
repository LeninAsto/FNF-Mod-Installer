package com.leninasto.fnfmodinstaler

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.leninasto.fnfmodinstaler.ui.theme.FNFModInstalerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ModViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val folderUri = intent.getStringExtra("folder_uri") ?: return finish()
        val isPolymod = intent.getBooleanExtra("is_polymod", false)
        
        setContent {
            FNFModInstalerTheme {
                ModViewScreen(folderUri, isPolymod, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModViewScreen(folderUri: String, isPolymod: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    var modMetadata by remember { mutableStateOf<ModMetadata?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    fun refresh() {
        val dir = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
        if (dir != null) {
            modMetadata = loadModMetadata(context, dir, isPolymod)
        }
    }

    LaunchedEffect(Unit) { refresh() }

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

            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Imagen con tamaño fijo cuadrado normal
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
                                .size(250.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }

                Column(modifier = Modifier.padding(16.dp)) {
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
                        
                        if (mod.color.size == 3) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.Default.Palette, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Color: ", fontWeight = FontWeight.Bold)
                                Box(modifier = Modifier.size(20.dp).background(Color(mod.color[0], mod.color[1], mod.color[2]), RoundedCornerShape(4.dp)))
                                Text(" [${mod.color[0]}, ${mod.color[1]}, ${mod.color[2]}]")
                            }
                        }
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
