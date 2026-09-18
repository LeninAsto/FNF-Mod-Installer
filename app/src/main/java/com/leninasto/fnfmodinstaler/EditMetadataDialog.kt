package com.leninasto.fnfmodinstaler

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun EditMetadataDialog(mod: ModMetadata, isPolymod: Boolean, onDismiss: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentData = remember {
        val fileName = if (isPolymod) "_polymod_meta.json" else "pack.json"
        val file = mod.dir.findFile(fileName)
        if (file != null) {
            try {
                val jsonStr = context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
                if (jsonStr != null) JSONObject(jsonStr) else JSONObject()
            } catch (e: Exception) { JSONObject() }
        } else JSONObject()
    }

    // Common fields
    var title by remember { mutableStateOf(if (isPolymod) currentData.optString("title", mod.title) else currentData.optString("name", mod.title)) }
    var description by remember { mutableStateOf(currentData.optString("description", mod.description)) }
    
    // Polymod specific
    var author by remember { mutableStateOf(currentData.optString("author", mod.author)) }
    var modVersion by remember { mutableStateOf(currentData.optString("mod_version", mod.version)) }
    var apiVersion by remember { mutableStateOf(currentData.optString("api_version", mod.apiVersion)) }
    var license by remember { mutableStateOf(currentData.optString("license", mod.license)) }

    // Pack specific
    var restart by remember { mutableStateOf(currentData.optBoolean("restart", mod.restart)) }
    var runsGlobally by remember { mutableStateOf(currentData.optBoolean("runsGlobally", mod.runsGlobally)) }
    var discordRPC by remember { mutableStateOf(currentData.optString("discordRPC", mod.discordRPC)) }
    var versionPack by remember { mutableStateOf(currentData.optString("version", mod.version)) }
    
    // Color (Psych)
    val initialColor = remember { 
        val arr = currentData.optJSONArray("color")
        if (arr != null && arr.length() == 3) listOf(arr.getInt(0), arr.getInt(1), arr.getInt(2))
        else listOf(190, 190, 190)
    }
    var r by remember { mutableStateOf(initialColor[0].toString()) }
    var g by remember { mutableStateOf(initialColor[1].toString()) }
    var b by remember { mutableStateOf(initialColor[2].toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_metadata)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(if (isPolymod) R.string.title else R.string.title)) },
                    leadingIcon = { Icon(Icons.Default.Title, null) }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description)) },
                    leadingIcon = { Icon(Icons.Default.Description, null) }
                )

                if (isPolymod) {
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("Author") },
                        leadingIcon = { Icon(Icons.Default.Person, null) }
                    )
                    OutlinedTextField(
                        value = modVersion,
                        onValueChange = { modVersion = it },
                        label = { Text("Mod Version") },
                        leadingIcon = { Icon(Icons.Default.Numbers, null) }
                    )
                    OutlinedTextField(
                        value = apiVersion,
                        onValueChange = { apiVersion = it },
                        label = { Text("API Version") },
                        leadingIcon = { Icon(Icons.Default.Code, null) }
                    )
                    OutlinedTextField(
                        value = license,
                        onValueChange = { license = it },
                        label = { Text("License") },
                        leadingIcon = { Icon(Icons.Default.Description, null) }
                    )
                } else {
                    OutlinedTextField(
                        value = versionPack,
                        onValueChange = { versionPack = it },
                        label = { Text("Version") },
                        leadingIcon = { Icon(Icons.Default.Numbers, null) }
                    )
                    OutlinedTextField(
                        value = discordRPC,
                        onValueChange = { discordRPC = it },
                        label = { Text("Discord RPC ID") },
                        leadingIcon = { Icon(Icons.Default.Link, null) }
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = restart, onCheckedChange = { restart = it })
                        Text("Restart on change")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = runsGlobally, onCheckedChange = { runsGlobally = it })
                        Text("Runs Globally")
                    }
                    
                    Text("Background Color (RGB):", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = r, onValueChange = { if (it.all { c -> c.isDigit() }) r = it }, modifier = Modifier.weight(1f), label = { Text("R") })
                        OutlinedTextField(value = g, onValueChange = { if (it.all { c -> c.isDigit() }) g = it }, modifier = Modifier.weight(1f), label = { Text("G") })
                        OutlinedTextField(value = b, onValueChange = { if (it.all { c -> c.isDigit() }) b = it }, modifier = Modifier.weight(1f), label = { Text("B") })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    val data = mutableMapOf<String, Any>()
                    if (isPolymod) {
                        data["title"] = title
                        data["description"] = description
                        data["author"] = author
                        data["mod_version"] = modVersion
                        data["api_version"] = apiVersion
                        data["license"] = license
                    } else {
                        data["name"] = title
                        data["description"] = description
                        data["version"] = versionPack
                        data["restart"] = restart
                        data["runsGlobally"] = runsGlobally
                        data["discordRPC"] = discordRPC
                        val colorList = listOf(r.toIntOrNull() ?: 0, g.toIntOrNull() ?: 0, b.toIntOrNull() ?: 0)
                        data["color"] = colorList
                    }
                    saveModMetadata(context, mod.dir, isPolymod, data)
                    withContext(Dispatchers.Main) {
                        onSave()
                        onDismiss()
                    }
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
