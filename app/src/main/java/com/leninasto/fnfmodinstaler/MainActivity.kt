package com.leninasto.fnfmodinstaler

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.leninasto.fnfmodinstaler.ui.theme.FNFModInstalerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    
    var manualInstallationUri by remember { mutableStateOf<Uri?>(null) }
    var isManualFolder by remember { mutableStateOf(false) }

    var enabledDestinations by remember {
        mutableStateOf(
            AppDestinations.entries.filter { 
                it.isStatic || prefs.getBoolean("engine_enabled_${it.name}", false)
            }
        )
    }

    var currentDestination by remember { mutableStateOf(AppDestinations.ORIGINAL) }
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
            prefs.edit { putString("folder_uri_$key", it.toString()) }
            folderUris[key] = it.toString()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val activeUri = incomingZip ?: manualInstallationUri
    val activeIsFolder = if (incomingZip != null) false else isManualFolder
    val onDismissActive = {
        if (incomingZip != null) onDismissZip()
        manualInstallationUri = null
    }

    activeUri?.let { uri ->
        val targetUri = if (currentDestination == AppDestinations.PSLICE_ENGINE) {
            if (folderUris["PSLICE_ACTIVE_IS_ALT"] == "true") folderUris["PSLICE_ALT"] else folderUris[currentDestination.name]
        } else folderUris[currentDestination.name]

        ModInstallationDialog(
            sourceUri = uri,
            isFolder = activeIsFolder,
            targetEngineName = stringResource(currentDestination.labelRes),
            targetFolderUri = targetUri,
            onSetupFolder = { folderPicker.launch(null) },
            onDismiss = onDismissActive
        )
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            enabledDestinations.forEach { dest ->
                val iconResId = dest.iconRes
                val materialIcon = dest.materialIcon
                item(
                    icon = { 
                        if (iconResId != null) {
                            Icon(painter = painterResource(id = iconResId), contentDescription = null, modifier = Modifier.size(24.dp), tint = Color.Unspecified)
                        } else if (materialIcon != null) {
                            Icon(imageVector = materialIcon, contentDescription = null, modifier = Modifier.size(24.dp))
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
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = {
                        val isCollapsed = scrollBehavior.state.collapsedFraction > 0.5f
                        val iconResId = currentDestination.iconRes
                        val materialIcon = currentDestination.materialIcon

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isCollapsed) Alignment.Start else Alignment.CenterHorizontally
                        ) {
                            if (!isCollapsed) {
                                if (iconResId != null) {
                                    Icon(painter = painterResource(id = iconResId), contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Unspecified)
                                } else if (materialIcon != null) {
                                    Icon(imageVector = materialIcon, contentDescription = null, modifier = Modifier.size(48.dp))
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isCollapsed) {
                                    if (iconResId != null) {
                                        Icon(painter = painterResource(id = iconResId), contentDescription = null, modifier = Modifier.size(24.dp).padding(end = 8.dp), tint = Color.Unspecified)
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
                        }
                    },
                    actions = {
                        IconButton(onClick = { context.startActivity(Intent(context, OptionsActivity::class.java)) }) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
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
                            prefs.edit { putString("pslice_active_is_alt", strValue) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineScreen(engine: AppDestinations, folderUri: String?, onBind: () -> Unit) {
    val context = LocalContext.current
    var installedMods by remember(folderUri) { mutableStateOf<List<ModMetadata>>(emptyList()) }
    var isLoading by remember(folderUri) { mutableStateOf(folderUri != null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refreshMods() {
        if (folderUri != null) {
            isRefreshing = true
            scope.launch(Dispatchers.IO) {
                try {
                    val root = DocumentFile.fromTreeUri(context, folderUri.toUri())
                    if (root != null && root.canRead()) {
                        val mods = root.listFiles().filter { it.isDirectory }.map { dir ->
                            loadModMetadata(context, dir, engine == AppDestinations.ORIGINAL)
                        }.sortedBy { it.title }
                        installedMods = mods
                    } else {
                        installedMods = emptyList()
                    }
                } catch (e: Exception) { e.printStackTrace() }
                isRefreshing = false
                isLoading = false
            }
        }
    }

    LaunchedEffect(folderUri) { refreshMods() }

    val pullToRefreshState = rememberPullToRefreshState()
    
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { refreshMods() },
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
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
                            ModCard(mod, isPolymod = engine == AppDestinations.ORIGINAL, onRefresh = { refreshMods() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModCard(mod: ModMetadata, isPolymod: Boolean, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete)) },
            text = {
                if (isDeleting) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.deleting), modifier = Modifier.padding(top = 8.dp))
                    }
                } else {
                    Text(stringResource(R.string.delete_mod_confirm))
                }
            },
            confirmButton = {
                if (!isDeleting) {
                    Button(onClick = {
                        isDeleting = true
                        scope.launch(Dispatchers.IO) {
                            deleteDocumentRecursive(mod.dir)
                            withContext(Dispatchers.Main) {
                                isDeleting = false
                                showDeleteDialog = false
                                onRefresh()
                            }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text(stringResource(R.string.delete))
                    }
                }
            },
            dismissButton = {
                if (!isDeleting) {
                    TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(context, ModViewActivity::class.java).apply {
                    putExtra("folder_uri", mod.dir.uri.toString())
                    putExtra("is_polymod", isPolymod)
                }
                context.startActivity(intent)
            },
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
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
        val uri = uriString.toUri()
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
                        Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/LeninAsto/FNF-Mod-Installer".toUri())) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.source)) }
                        Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://buymeacoffee.com/lenin_anonimo_of".toUri())) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.donate)) }
                        Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://leninasto.github.io/FNF-Mod-Installer/privacy.html".toUri())) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.terms_conditions)) }
                    }
                    Button(onClick = {
                        context.startActivity(Intent(context, CreditsActivity::class.java))
                    }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.tester_credits)) }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.installation_guide_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            InfoCard(stringResource(R.string.guide_scoped_title), stringResource(R.string.guide_scoped_content))
            InfoCard(stringResource(R.string.guide_external_title), stringResource(R.string.guide_external_content))
            InfoCard(stringResource(R.string.guide_unlinking_title), stringResource(R.string.guide_unlinking_content))
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
                                val success = if (isFolder) copyFolderToData(context, sourceUri, targetFolderUri.toUri()) { _, f -> currentFile = f }
                                else extractZipToDataFolder(context, sourceUri, targetFolderUri.toUri()) { _, f -> currentFile = f }
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
        java.util.zip.ZipInputStream(inputStream).use { zis ->
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

private fun copyRecursive(context: Context, source: DocumentFile, target: DocumentFile, onProgress: (Float, String) -> Unit) {
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
    val mimeType = if (extension.isNotEmpty()) android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream" else "application/octet-stream"
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
