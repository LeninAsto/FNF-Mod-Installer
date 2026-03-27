package com.leninasto.fnfmodinstaler

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.leninasto.fnfmodinstaler.ui.theme.FNFModInstalerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

class MainActivity : ComponentActivity() {

    private var incomingZipUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            FNFModInstalerTheme {
                FNFModInstalerApp(
                    incomingZip = incomingZipUri,
                    onDismissZip = { incomingZipUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_SEND) {
            val uri = if (intent.action == Intent.ACTION_SEND) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            } else {
                intent.data
            }
            incomingZipUri = uri
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FNFModInstalerApp(incomingZip: Uri?, onDismissZip: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fnf_prefs", Context.MODE_PRIVATE) }

    // Lista de motores habilitados (Original, Plus e Info son siempre visibles)
    var enabledDestinations by remember {
        mutableStateOf(
            AppDestinations.entries.filter {
                it.isStatic || prefs.getBoolean("engine_enabled_${it.name}", false)
            }
        )
    }

    var currentDestination by remember { mutableStateOf(AppDestinations.ORIGINAL) }
    var showSettings by remember { mutableStateOf(false) }

    // Folder URIs mapeados por nombre de destino
    val folderUris = remember { mutableStateMapOf<String, String?>() }

    // Cargar URIs al inicio
    LaunchedEffect(Unit) {
        AppDestinations.entries.forEach { dest ->
            folderUris[dest.name] = prefs.getString("folder_uri_${dest.name}", null)
            if (dest == AppDestinations.PSLICE_ENGINE) {
                folderUris["PSLICE_ALT"] = prefs.getString("folder_uri_PSLICE_ALT", null)
                folderUris["PSLICE_ACTIVE_IS_ALT"] = prefs.getString("pslice_active_is_alt", "false")
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val key = if (currentDestination == AppDestinations.PSLICE_ENGINE && folderUris["PSLICE_PICKING_ALT"] == "true") "PSLICE_ALT" else currentDestination.name
            prefs.edit().putString("folder_uri_$key", it.toString()).apply()
            folderUris[key] = it.toString()
        }
    }

    var manualZipUri by remember { mutableStateOf<Uri?>(null) }
    var manualFolderUri by remember { mutableStateOf<Uri?>(null) }
    val manualZipPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { manualZipUri = it }
    val manualFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { manualFolderUri = it }

    var showPermissionDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            showPermissionDialog = true
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Acceso a Archivos") },
            text = { Text("Para gestionar motores como Psych o NovaFlare, activa el acceso total a archivos.") },
            confirmButton = {
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                    showPermissionDialog = false
                }) { Text("Configurar") }
            },
            dismissButton = { TextButton(onClick = { showPermissionDialog = false }) { Text("Ignorar") } }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Gestionar Motores") },
            text = {
                LazyColumn {
                    items(AppDestinations.entries.filter { !it.isStatic }) { dest ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(dest.label, modifier = Modifier.weight(1f))
                            Switch(
                                checked = enabledDestinations.contains(dest),
                                onCheckedChange = { checked ->
                                    prefs.edit().putBoolean("engine_enabled_${dest.name}", checked).apply()
                                    enabledDestinations = AppDestinations.entries.filter {
                                        it.isStatic || prefs.getBoolean("engine_enabled_${it.name}", false)
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showSettings = false }) { Text("Aceptar") } }
        )
    }

    // Instalación
    val activeSource = (incomingZip ?: manualZipUri)?.let { it to false } ?: manualFolderUri?.let { it to true }
    activeSource?.let { (uri, isFolder) ->
        val targetUri = if (currentDestination == AppDestinations.PSLICE_ENGINE) {
            if (folderUris["PSLICE_ACTIVE_IS_ALT"] == "true") folderUris["PSLICE_ALT"] else folderUris[currentDestination.name]
        } else folderUris[currentDestination.name]

        ModInstallationDialog(
            sourceUri = uri,
            isFolder = isFolder,
            targetEngineName = currentDestination.label,
            targetFolderUri = targetUri,
            onSetupFolder = { folderPicker.launch(null) },
            onDismiss = {
                onDismissZip()
                manualZipUri = null
                manualFolderUri = null
            }
        )
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            enabledDestinations.forEach { dest ->
                item(
                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                    label = { Text(dest.label) },
                    selected = dest == currentDestination,
                    onClick = { currentDestination = dest }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentDestination.label) },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            floatingActionButton = {
                if (currentDestination != AppDestinations.INFO) {
                    var fabExpanded by remember { mutableStateOf(false) }
                    Column(horizontalAlignment = Alignment.End) {
                        if (fabExpanded) {
                            SmallFloatingActionButton(onClick = { manualFolderPicker.launch(null); fabExpanded = false }) { Icon(Icons.Default.Folder, null) }
                            Spacer(Modifier.height(8.dp))
                            SmallFloatingActionButton(onClick = { manualZipPicker.launch("application/zip"); fabExpanded = false }) { Icon(Icons.Default.FilePresent, null) }
                            Spacer(Modifier.height(8.dp))
                        }
                        FloatingActionButton(onClick = { fabExpanded = !fabExpanded }) {
                            Icon(if (fabExpanded) Icons.Default.Close else Icons.Default.Add, null)
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (currentDestination) {
                    AppDestinations.INFO -> InfoScreen()
                    AppDestinations.PSLICE_ENGINE -> PSliceScreen(
                        folderUris = folderUris,
                        onBindPrimary = { folderUris["PSLICE_PICKING_ALT"] = "false"; folderPicker.launch(null) },
                        onBindAlt = { folderUris["PSLICE_PICKING_ALT"] = "true"; folderPicker.launch(null) },
                        onTogglePath = { isAlt ->
                            val strValue = isAlt.toString()
                            prefs.edit().putString("pslice_active_is_alt", strValue).apply()
                            folderUris["PSLICE_ACTIVE_IS_ALT"] = strValue
                        }
                    )
                    else -> EngineScreen(
                        name = currentDestination.label,
                        folderUri = folderUris[currentDestination.name],
                        onBind = { folderPicker.launch(null) }
                    )
                }
            }
        }
    }
}

@Composable
fun PSliceScreen(
    folderUris: Map<String, String?>,
    onBindPrimary: () -> Unit,
    onBindAlt: () -> Unit,
    onTogglePath: (Boolean) -> Unit
) {
    val isAlt = folderUris["PSLICE_ACTIVE_IS_ALT"] == "true"
    val activeUri = if (isAlt) folderUris["PSLICE_ALT"] else folderUris[AppDestinations.PSLICE_ENGINE.name]

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("P-Slice: Configuración de Almacenamiento", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !isAlt, onClick = { onTogglePath(false) })
                        Text("External (.PSliceEngine)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isAlt, onClick = { onTogglePath(true) })
                        Text("Scoped (Android/data/...)")
                    }
                }
            }
        }

        EngineScreenContent(
            name = "P-Slice Engine",
            folderUri = activeUri,
            onBind = { if (isAlt) onBindAlt() else onBindPrimary() }
        )
    }
}

@Composable
fun EngineScreen(name: String, folderUri: String?, onBind: () -> Unit) {
    EngineScreenContent(name, folderUri, onBind)
}

@Composable
fun EngineScreenContent(name: String, folderUri: String?, onBind: () -> Unit) {
    val context = LocalContext.current
    var installedMods by remember(folderUri) { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var isLoading by remember(folderUri) { mutableStateOf(folderUri != null) }

    LaunchedEffect(folderUri) {
        if (folderUri != null) {
            isLoading = true
            withContext(Dispatchers.IO) {
                try {
                    val root = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
                    if (root != null && root.canRead()) {
                        installedMods = root.listFiles().filter { it.isDirectory }.sortedBy { it.name }
                    } else {
                        installedMods = emptyList()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (folderUri == null || !isUriPermissionValid(context, folderUri)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val msg = if (folderUri == null) "Carpeta no vinculada" else "Permiso revocado (App reinstalada)"
                    Text("⚠️ $msg", style = MaterialTheme.typography.titleMedium)
                    Text("Vincular la carpeta 'mods' para $name.")
                    Button(onClick = onBind, modifier = Modifier.padding(top = 8.dp)) { Text("Vincular Ahora") }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Carpeta vinculada correctamente", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onBind) { Text("Cambiar") }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Mods Instalados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (installedMods.isEmpty()) {
                Text("No se encontraron mods en esta carpeta.", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(installedMods) { mod ->
                        ListItem(
                            headlineContent = { Text(mod.name ?: "Sin nombre") },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.List, null, tint = MaterialTheme.colorScheme.secondary) },
                            supportingContent = { Text("Carpeta de mod") }
                        )
                    }
                }
            }
        }
    }
}

fun isUriPermissionValid(context: Context, uriString: String?): Boolean {
    if (uriString == null) return false
    return try {
        val uri = Uri.parse(uriString)
        context.contentResolver.persistedUriPermissions.any { it.uri == uri }
    } catch (e: Exception) { false }
}

@Composable
fun InfoScreen() {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Acerca de la App", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("FNF Mod Installer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Instalador multi-motor para Android.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/LeninAsto/FNF-Mod-Installer"))) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Source")
                        }
                        Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/lenin_anonimo_of"))) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Favorite, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Donar")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            InfoCard("¿Por qué se desvinculan las carpetas?", "Android revoca los permisos de carpeta al desinstalar una app por seguridad. Si reinstalas, solo presiona 'Vincular Ahora' de nuevo.")
        }
    }
}

@Composable
fun InfoCard(title: String, content: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ModInstallationDialog(sourceUri: Uri, isFolder: Boolean, targetEngineName: String, targetFolderUri: String?, onSetupFolder: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isInstalling by remember { mutableStateOf(false) }
    var complete by remember { mutableStateOf(false) }
    var currentFile by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = if (isInstalling) ({}) else onDismiss,
        title = { Text(if (complete) "¡Listo!" else "Instalar Mod") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (complete) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally), tint = MaterialTheme.colorScheme.primary)
                    Text("Mod listo en $targetEngineName", modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (isInstalling) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text(currentFile, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                } else {
                    Text("Motor: $targetEngineName")
                    if (targetFolderUri == null || !isUriPermissionValid(context, targetFolderUri)) {
                        Text("Error: Carpeta no vinculada.", color = MaterialTheme.colorScheme.error)
                        Button(onClick = onSetupFolder, modifier = Modifier.fillMaxWidth()) { Text("Vincular Ahora") }
                    } else {
                        Button(onClick = {
                            isInstalling = true
                            scope.launch {
                                val success = if (isFolder) copyFolderToData(context, sourceUri, Uri.parse(targetFolderUri)) { _, f -> currentFile = f }
                                else extractZipToDataFolder(context, sourceUri, Uri.parse(targetFolderUri)) { _, f -> currentFile = f }
                                isInstalling = false
                                if (success) complete = true else Toast.makeText(context, "Error", Toast.LENGTH_SHORT).show()
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Confirmar") }
                    }
                }
            }
        },
        confirmButton = { if (complete) Button(onClick = onDismiss) { Text("Finalizar") } else if (!isInstalling) TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

suspend fun extractZipToDataFolder(context: Context, zipUri: Uri, targetFolderUri: Uri, onProgress: (Float, String) -> Unit): Boolean = withContext(Dispatchers.IO) {
    try {
        val rootDoc = DocumentFile.fromTreeUri(context, targetFolderUri) ?: return@withContext false
        val inputStream = context.contentResolver.openInputStream(zipUri) ?: return@withContext false
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                onProgress(0.5f, entry.name)
                if (entry.isDirectory) createDirectoryChain(rootDoc, entry.name)
                else {
                    val fileDoc = createFileChain(rootDoc, entry.name)
                    fileDoc?.let { doc -> context.contentResolver.openOutputStream(doc.uri)?.use { fos -> zis.copyTo(fos) } }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        true
    } catch (e: Exception) { e.printStackTrace(); false }
}

suspend fun copyFolderToData(context: Context, sourceFolderUri: Uri, targetFolderUri: Uri, onProgress: (Float, String) -> Unit): Boolean = withContext(Dispatchers.IO) {
    try {
        val sourceDoc = DocumentFile.fromTreeUri(context, sourceFolderUri) ?: return@withContext false
        val targetRoot = DocumentFile.fromTreeUri(context, targetFolderUri) ?: return@withContext false
        val modFolder = targetRoot.createDirectory(sourceDoc.name ?: "Mod") ?: return@withContext false
        copyRecursive(context, sourceDoc, modFolder, onProgress)
        true
    } catch (e: Exception) { e.printStackTrace(); false }
}

private suspend fun copyRecursive(context: Context, source: DocumentFile, target: DocumentFile, onProgress: (Float, String) -> Unit) {
    source.listFiles().forEach { item ->
        onProgress(0.5f, item.name ?: "")
        if (item.isDirectory) {
            val newDir = target.createDirectory(item.name ?: "dir")
            if (newDir != null) copyRecursive(context, item, newDir, onProgress)
        } else {
            val newFile = target.createFile(item.type ?: "application/octet-stream", item.name ?: "file")
            if (newFile != null) context.contentResolver.openInputStream(item.uri)?.use { input -> context.contentResolver.openOutputStream(newFile.uri)?.use { output -> input.copyTo(output) } }
        }
    }
}

fun createDirectoryChain(parent: DocumentFile, path: String): DocumentFile? {
    var current = parent
    path.split("/").filter { it.isNotEmpty() }.forEach { segment ->
        val next = current.findFile(segment) ?: current.createDirectory(segment)
        current = next ?: return null
    }
    return current
}

fun createFileChain(parent: DocumentFile, filePath: String): DocumentFile? {
    val segments = filePath.split("/")
    val fileName = segments.last()
    val dirPath = segments.dropLast(1).joinToString("/")
    val dir = if (dirPath.isEmpty()) parent else createDirectoryChain(parent, dirPath) ?: return null
    val extension = fileName.substringAfterLast('.', "")
    val mimeType = if (extension.isNotEmpty()) MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream" else "application/octet-stream"
    return dir.findFile(fileName) ?: dir.createFile(mimeType, fileName)
}

enum class AppDestinations(val label: String, val icon: ImageVector, val isStatic: Boolean = false) {
    ORIGINAL("FNF Mobile", Icons.Default.Folder, true),
    PLUS_ENGINE("Plus Engine", Icons.Default.FolderZip, true),
    PSYCH_ENGINE("Psych Engine", Icons.Default.Psychology),
    NOVAFLARE_ENGINE("NovaFlare", Icons.Default.Flare),
    PSLICE_ENGINE("P-Slice", Icons.Default.PieChart),
    INFO("Información", Icons.Default.FolderSpecial, true),
}