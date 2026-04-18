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
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.leninasto.fnfmodinstaler.ui.theme.FNFModInstalerTheme
import kotlinx.coroutines.*
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var incomingZipUri by mutableStateOf<Uri?>(null)
    
    var progressState by mutableStateOf(ProgressState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            FNFModInstalerTheme {
                FNFModInstalerApp(
                    incomingZip = incomingZipUri,
                    onDismissZip = { incomingZipUri = null },
                    installState = this
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
fun FNFModInstalerApp(incomingZip: Uri?, onDismissZip: () -> Unit, installState: MainActivity) {
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
            onDismiss = onDismissActive,
            installState = installState
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
                            
                            if (installState.progressState.isRunning) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    LinearProgressIndicator(
                                        progress = { installState.progressState.percentage },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${(installState.progressState.percentage * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                                        Text(installState.progressState.timeRemaining, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { context.startActivity(Intent(context, OptionsActivity::class.java)) }) {
                            Icon(Icons.Default.Settings, contentDescription = null)
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
                } catch (e: Exception) { e.printStackTrace() }
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
fun ModCard(mod: ModMetadata, isPolymod: Boolean, rootEngineUri: String, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteProgress by remember { mutableStateOf(ProgressState()) }

    Card(
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
                if (mod.isApiOutdated) {
                    Text(stringResource(R.string.api_outdated, mod.apiVersion), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            
            IconButton(onClick = { showDeleteDialog = true }) {
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
                        Text("Remaining: ${deleteProgress.timeRemaining}", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                } else {
                    Text(stringResource(R.string.delete_mod_confirm), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                if (!deleteProgress.isRunning) {
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val total = countFilesRecursive(mod.dir)
                            deleteDocumentRecursiveWithProgress(
                                mod.dir, 
                                { deleteProgress = it },
                                total,
                                intArrayOf(0),
                                System.currentTimeMillis()
                            )
                            withContext(Dispatchers.Main) {
                                deleteProgress = ProgressState()
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
                if (!deleteProgress.isRunning) {
                    TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }
}

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
                                if (success) complete = true else Toast.makeText(context, context.getString(R.string.install_error), Toast.LENGTH_SHORT).show()
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

suspend fun extractZipWithProgress(context: Context, zipUri: Uri, targetFolderUri: Uri, onProgress: (ProgressState) -> Unit): Boolean = withContext(Dispatchers.IO) {
    try {
        val rootDoc = DocumentFile.fromTreeUri(context, targetFolderUri) ?: return@withContext false
        val startTime = System.currentTimeMillis()
        
        // Contar entradas para el porcentaje
        var totalEntries = 0
        context.contentResolver.openInputStream(zipUri)?.use { is1 ->
            java.util.zip.ZipInputStream(is1).use { zis ->
                while (zis.nextEntry != null) { totalEntries++; zis.closeEntry() }
            }
        }
        
        var currentEntry = 0
        context.contentResolver.openInputStream(zipUri)?.use { is2 ->
            java.util.zip.ZipInputStream(is2).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    currentEntry++
                    val progress = currentEntry.toFloat() / totalEntries
                    val elapsed = System.currentTimeMillis() - startTime
                    val remaining = if (progress > 0) (elapsed / progress - elapsed).toLong() else 0L
                    
                    withContext(Dispatchers.Main) {
                        onProgress(ProgressState(
                            isRunning = true,
                            percentage = progress,
                            currentFile = entry!!.name,
                            timeRemaining = formatTime(remaining),
                            processedUnits = "$currentEntry / $totalEntries items"
                        ))
                    }

                    if (entry.isDirectory) createDirectoryChain(rootDoc, entry.name)
                    else {
                        val fileDoc = createFileChain(rootDoc, entry.name)
                        fileDoc?.let { doc -> context.contentResolver.openOutputStream(doc.uri)?.use { fos -> zis.copyTo(fos) } }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        true
    } catch (e: Exception) { e.printStackTrace(); false }
}

suspend fun copyFolderWithProgress(context: Context, sourceFolderUri: Uri, targetFolderUri: Uri, onProgress: (ProgressState) -> Unit): Boolean = withContext(Dispatchers.IO) {
    try {
        val sourceDoc = DocumentFile.fromTreeUri(context, sourceFolderUri) ?: return@withContext false
        val targetRoot = DocumentFile.fromTreeUri(context, targetFolderUri) ?: return@withContext false
        val totalFiles = countFilesRecursive(sourceDoc)
        val currentCount = intArrayOf(0)
        val startTime = System.currentTimeMillis()
        
        val modFolder = targetRoot.createDirectory(sourceDoc.name ?: "Mod") ?: return@withContext false
        copyRecursiveWithProgress(context, sourceDoc, modFolder, onProgress, totalFiles, currentCount, startTime)
        true
    } catch (e: Exception) { e.printStackTrace(); false }
}

private suspend fun copyRecursiveWithProgress(
    context: Context, 
    source: DocumentFile, 
    target: DocumentFile, 
    onProgress: (ProgressState) -> Unit,
    total: Int,
    current: IntArray,
    startTime: Long
) {
    source.listFiles().forEach { item ->
        current[0]++
        val progress = current[0].toFloat() / total
        val elapsed = System.currentTimeMillis() - startTime
        val remaining = if (progress > 0) (elapsed / progress - elapsed).toLong() else 0L

        withContext(Dispatchers.Main) {
            onProgress(ProgressState(
                isRunning = true,
                percentage = progress,
                currentFile = item.name ?: "",
                timeRemaining = formatTime(remaining),
                processedUnits = "${current[0]} / $total files"
            ))
        }

        if (item.isDirectory) {
            val newDir = target.createDirectory(item.name ?: "dir")
            if (newDir != null) copyRecursiveWithProgress(context, item, newDir, onProgress, total, current, startTime)
        } else {
            val newFile = target.createFile(item.type ?: "application/octet-stream", item.name ?: "file")
            if (newFile != null) {
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                        input.copyTo(output)
                    }
                }
            }
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
