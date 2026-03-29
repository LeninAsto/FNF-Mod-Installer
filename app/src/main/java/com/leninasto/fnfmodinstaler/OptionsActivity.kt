package com.leninasto.fnfmodinstaler

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leninasto.fnfmodinstaler.ui.theme.FNFModInstalerTheme

class OptionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FNFModInstalerTheme {
                Scaffold(
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = { Text(stringResource(R.string.settings)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                }
                            }
                        )
                    }
                ) { padding ->
                    OptionsScreen(Modifier.padding(padding))
                }
            }
        }
    }

    @Composable
    fun OptionsScreen(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences("fnf_prefs", Context.MODE_PRIVATE) }
        
        val destinations = AppDestinations.entries.filter { !it.isStatic }
        
        LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
            items(destinations) { dest ->
                var enabled by remember { mutableStateOf(prefs.getBoolean("engine_enabled_${dest.name}", false)) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(stringResource(dest.labelRes), modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { checked ->
                            enabled = checked
                            prefs.edit().putBoolean("engine_enabled_${dest.name}", checked).apply()
                        }
                    )
                }
            }
        }
    }
}
