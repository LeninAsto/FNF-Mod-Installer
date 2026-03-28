package com.leninasto.fnfmodinstaler

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    
    var enabledDestinations by remember {
        mutableStateOf(
            AppDestinations.entries.filter { 
                it.isStatic || prefs.getBoolean("engine_enabled_${it.name}", false)
            }
        )
    }

    var currentDestination by remember { mutableStateOf(AppDestinations.ORIGINAL) }
    var showSettings by remember { mutableStateOf(false) }
    val folderUris = remember { mutableStateMapOf<String, String?>() }
    
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

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(stringResource(R.string.manage_engines)) },
            text = {
                LazyColumn {
                    items(AppDestinations.entries.filter { !it.isStatic }) { dest ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(stringResource(dest.labelRes), modifier = Modifier.weight(1f))
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
            confirmButton = { Button(onClick = { showSettings = false }) { Text(stringResource(R.string.close)) } }
        )
    }

    val activeSource = (incomingZip ?: manualZipUri)?.let { it to false } ?: manualFolderUri?.let { it to true }
    activeSource?.let { (uri, isFolder) ->
        val targetUri = if (currentDestination == AppDestinations.PSLICE_ENGINE) {
            if (folderUris["PSLICE_ACTIVE_IS_ALT"] == "true") folderUris["PSLICE_ALT"] else folderUris[currentDestination.name]
        } else folderUris[currentDestination.name]

        ModInstallationDialog(
            sourceUri = uri,
            isFolder = isFolder,
            targetEngineName = stringResource(currentDestination.labelRes),
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
                    icon = { 
                        if (dest.iconRes != null) {
                            Icon(painter = painterResource(id = dest.iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(imageVector = dest.materialIcon!!, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    },
                    label = { Text(stringResource(dest.labelRes)) },
                    selected = dest == currentDestination,
                    onClick = { currentDestination = dest }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(currentDestination.labelRes)) },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = null)
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
                        engine = currentDestination,
                        folderUri = folderUris[currentDestination.name],
                        onBind = { folderPicker.launch(null) }
                    )
                }
            }
        }
    }
}

@Composable
fun EngineScreen(engine: AppDestinations, folderUri: String?, onBind: () -> Unit) {
    val context = LocalContext.current
    var installedMods by remember(folderUri) { mutableStateOf<List<ModMetadata>>(emptyList()) }
    var isLoading by remember(folderUri) { mutableStateOf(folderUri != null) }
    val scope = rememberCoroutineScope()

    fun refreshMods() {
        if (folderUri != null) {
            isLoading = true
            scope.launch(Dispatchers.IO) {
                try {
                    val root = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
                    if (root != null && root.canRead()) {
                        val mods = root.listFiles().filter { it.isDirectory }.map { dir ->
                            loadModMetadata(context, dir, engine == AppDestinations.ORIGINAL)
                        }.sortedBy { it.title }
                        installedMods = mods
                    } else {
                        installedMods = emptyList()
                    }
                } catch (e: Exception) { e.printStackTrace() }
                isLoading = false
            }
        }
    }

    LaunchedEffect(folderUri) { refreshMods() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (folderUri == null || !isUriPermissionValid(context, folderUri)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⚠️ " + stringResource(R.string.folder_not_linked), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.link_folder_msg, stringResource(engine.labelRes)))
                        Button(onClick = onBind, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.link_now)) }
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
            Text(stringResource(R.string.installed_mods), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (installedMods.isEmpty()) {
                Text(stringResource(R.string.no_mods_found), modifier = Modifier.padding(top = 8.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(installedMods) { mod ->
                        ModCard(mod, onRefresh = { refreshMods() })
                    }
                }
            }
        }
    }
}

@Composable
fun ModCard(mod: ModMetadata, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_mod_confirm)) },
            confirmButton = {
                Button(onClick = {
                    scope.launch(Dispatchers.IO) {
                        mod.dir.delete()
                        withContext(Dispatchers.Main) {
                            showDeleteDialog = false
                            onRefresh()
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
            
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(mod.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (mod.author.isNotEmpty()) Text(stringResource(R.string.author, mod.author), style = MaterialTheme.typography.bodySmall)
                if (mod.version.isNotEmpty()) Text(stringResource(R.string.version, mod.version), style = MaterialTheme.typography.bodySmall)
                
                if (mod.isApiOutdated) {
                    Text(stringResource(R.string.api_outdated, mod.apiVersion), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

data class ModMetadata(
    val title: String,
    val description: String,
    val author: String,
    val version: String,
    val apiVersion: String,
    val isApiOutdated: Boolean,
    val icon: android.graphics.Bitmap?,
    val dir: DocumentFile
)

fun loadModMetadata(context: Context, dir: DocumentFile, isPolymod: Boolean): ModMetadata {
    var title = dir.name ?: "Unknown"
    var description = ""
    var author = ""
    var version = ""
    var apiVersion = ""
    var isApiOutdated = false
    var icon: android.graphics.Bitmap? = null

    val metaFile = if (isPolymod) dir.findFile("_polymod_meta.json") else dir.findFile("pack.json")
    val iconFile = if (isPolymod) dir.findFile("_polymod_icon.png") else dir.findFile("pack.png")

    metaFile?.let { file ->
        try {
            val jsonStr = context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
            if (jsonStr != null) {
                val json = JSONObject(jsonStr)
                if (isPolymod) {
                    title = json.optString("title", title)
                    description = json.optString("description", "")
                    author = json.optString("author", "")
                    version = json.optString("mod_version", "")
                    apiVersion = json.optString("api_version", "")
                    if (apiVersion.isNotEmpty() && apiVersion != "0.8.4") isApiOutdated = true
                } else {
                    title = json.optString("name", title)
                    description = json.optString("description", "")
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    iconFile?.let { file ->
        try {
            context.contentResolver.openInputStream(file.uri)?.use { 
                icon = BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    return ModMetadata(title, description, author, version, apiVersion, isApiOutdated, icon, dir)
}

@Composable
fun PSliceScreen(folderUris: Map<String, String?>, onBindPrimary: () -> Unit, onBindAlt: () -> Unit, onTogglePath: (Boolean) -> Unit) {
    val isAlt = folderUris["PSLICE_ACTIVE_IS_ALT"] == "true"
    val activeUri = if (isAlt) folderUris["PSLICE_ALT"] else folderUris[AppDestinations.PSLICE_ENGINE.name]
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.pslice_storage_config), style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !isAlt, onClick = { onTogglePath(false) })
                        Text(stringResource(R.string.external_storage))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isAlt, onClick = { onTogglePath(true) })
                        Text(stringResource(R.string.scoped_storage))
                    }
                }
            }
        }
        EngineScreen(engine = AppDestinations.PSLICE_ENGINE, folderUri = activeUri, onBind = { if (isAlt) onBindAlt() else onBindPrimary() })
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
            Text(stringResource(R.string.about_app), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.app_description), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/LeninAsto/FNF-Mod-Installer"))) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.source)) }
                        Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/lenin_anonimo_of"))) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.donate)) }
                        Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/LeninAsto/FNF-Mod-Installer/blob/main/T&C.txt"))) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.terms_conditions)) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            InfoCard(stringResource(R.string.installation_guide_title), stringResource(R.string.data_folder_install_content))
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
        title = { Text(if (complete) stringResource(R.string.install_ready) else stringResource(R.string.install_mod_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (complete) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.mod_ready_msg, targetEngineName), modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (isInstalling) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text(currentFile, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                } else {
                    Text(stringResource(R.string.engine_label, targetEngineName))
                    if (targetFolderUri == null || !isUriPermissionValid(context, targetFolderUri)) {
                        Text(stringResource(R.string.error_folder_not_linked), color = MaterialTheme.colorScheme.error)
                        Button(onClick = onSetupFolder, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.link_now)) }
                    } else {
                        Button(onClick = {
                            isInstalling = true
                            scope.launch {
                                val success = if (isFolder) copyFolderToData(context, sourceUri, Uri.parse(targetFolderUri)) { _, f -> currentFile = f }
                                else extractZipToDataFolder(context, sourceUri, Uri.parse(targetFolderUri)) { _, f -> currentFile = f }
                                isInstalling = false
                                if (success) complete = true else Toast.makeText(context, context.getString(R.string.install_error), Toast.LENGTH_SHORT).show()
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.confirm)) }
                    }
                }
            }
        },
        confirmButton = { if (complete) Button(onClick = onDismiss) { Text(stringResource(R.string.finish)) } else if (!isInstalling) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
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

enum class AppDestinations(
    val labelRes: Int, 
    val iconRes: Int? = null, 
    val materialIcon: ImageVector? = null, 
    val isStatic: Boolean = false
) {
    ORIGINAL(R.string.dest_funkin, iconRes = R.drawable.ic_funkin, isStatic = true),
    PLUS_ENGINE(R.string.dest_plus, iconRes = R.drawable.ic_plusengine),
    PSYCH_ENGINE(R.string.dest_psych, iconRes = R.drawable.ic_psych),
    NOVAFLARE_ENGINE(R.string.dest_novaflare, iconRes = R.drawable.ic_nfengine),
    PSLICE_ENGINE(R.string.dest_pslice, iconRes = R.drawable.ic_pslice),
    INFO(R.string.dest_info, materialIcon = Icons.Default.Info, isStatic = true),
}
