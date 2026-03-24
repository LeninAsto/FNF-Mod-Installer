package com.leninasto.fnfmodinstaler

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun FNFModInstalerApp(incomingZip: Uri?, onDismissZip: () -> Unit) {
    var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fnf_prefs", Context.MODE_PRIVATE) }
    
    // Almacenar el URI de la carpeta de mods persistentemente
    var modsFolderUri by remember { 
        mutableStateOf(prefs.getString("mods_folder_uri", null)) 
    }
    
    var showPermissionDialog by remember { mutableStateOf(false) }

    val safariLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // Persistir el permiso a nivel de sistema para que no expire
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            // Guardar en SharedPreferences para que sobreviva al cierre de la app
            prefs.edit().putString("mods_folder_uri", it.toString()).apply()
            modsFolderUri = it.toString()
            Toast.makeText(context, "Carpeta de Mods vinculada permanentemente", Toast.LENGTH_SHORT).show()
        }
    }

    // Verificar permisos de MANAGE_EXTERNAL_STORAGE (Acceso a todos los archivos)
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showPermissionDialog = true
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Acceso a Archivos") },
            text = { Text("Para una instalación más fluida, activa el acceso total a archivos.") },
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
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("Ignorar") }
            }
        )
    }

    incomingZip?.let { uri ->
        ModInstallationDialog(
            zipUri = uri, 
            savedModsFolderUri = modsFolderUri,
            onSetupFolder = { safariLauncher.launch(null) },
            onDismiss = onDismissZip
        )
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = { Icon(it.icon, contentDescription = it.label) },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text("FNF Mod Installer", style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.height(24.dp))
                
                if (modsFolderUri == null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("⚠️ Carpeta no vinculada", style = MaterialTheme.typography.titleMedium)
                            Text("Debes vincular Android/data/me.funkin.fnf/files/mods para poder instalar.")
                            Button(
                                onClick = { safariLauncher.launch(null) },
                                modifier = Modifier.padding(top = 8.dp)
                            ) { Text("Vincular Ahora") }
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Carpeta vinculada correctamente")
                        }
                    }
                    TextButton(onClick = { safariLauncher.launch(null) }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Cambiar carpeta vinculada")
                    }
                }
            }
        }
    }
}

@Composable
fun ModInstallationDialog(
    zipUri: Uri, 
    savedModsFolderUri: String?,
    onSetupFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isInstalling by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentFile by remember { mutableStateOf("") }
    var installComplete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = if (isInstalling) ({}) else onDismiss,
        title = { Text(if (installComplete) "¡Mod Instalado!" else "Confirmar Instalación") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (installComplete) {
                    Icon(
                        Icons.Default.CheckCircle, 
                        contentDescription = null, 
                        modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("El mod se ha extraído con éxito.", modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (isInstalling) {
                    Text("Extrayendo archivos...", style = MaterialTheme.typography.labelLarge)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    )
                    Text(text = currentFile, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                } else {
                    Text("Estás a punto de instalar:", style = MaterialTheme.typography.labelLarge)
                    Text(zipUri.path?.substringAfterLast('/') ?: "Archivo ZIP", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (savedModsFolderUri == null) {
                        Text("Error: Debes vincular la carpeta de mods primero.", color = MaterialTheme.colorScheme.error)
                        Button(onClick = onSetupFolder, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text("Vincular Carpeta")
                        }
                    } else {
                        Button(
                            onClick = {
                                isInstalling = true
                                scope.launch {
                                    val success = extractZipToDataFolder(context, zipUri, Uri.parse(savedModsFolderUri)) { prog, file ->
                                        progress = prog
                                        currentFile = file
                                    }
                                    isInstalling = false
                                    if (success) installComplete = true
                                    else Toast.makeText(context, "Error en la extracción. Verifica los permisos.", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Instalar en Funkin Oficial")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (installComplete) {
                Button(onClick = onDismiss) { Text("Finalizar") }
            } else if (!isInstalling) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

suspend fun extractZipToDataFolder(
    context: android.content.Context,
    zipUri: Uri,
    targetFolderUri: Uri,
    onProgress: (Float, String) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    try {
        val rootDoc = DocumentFile.fromTreeUri(context, targetFolderUri) ?: return@withContext false
        val inputStream = context.contentResolver.openInputStream(zipUri) ?: return@withContext false
        
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                onProgress(0.5f, entry.name)
                
                if (entry.isDirectory) {
                    createDirectoryChain(rootDoc, entry.name)
                } else {
                    val fileDoc = createFileChain(rootDoc, entry.name)
                    fileDoc?.let { doc ->
                        context.contentResolver.openOutputStream(doc.uri)?.use { fos ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
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
    
    val dir = if (dirPath.isEmpty()) parent else createDirectoryChain(parent, dirPath)
    return dir?.findFile(fileName) ?: dir?.createFile("application/zip", fileName)
}

enum class AppDestinations(val label: String, val icon: ImageVector) {
    HOME("Inicio", Icons.Default.Home),
    FAVORITES("Favoritos", Icons.Default.Favorite),
    PROFILE("Perfil", Icons.Default.AccountBox),
}
