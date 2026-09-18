package com.leninasto.fnfmodinstaler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.leninasto.fnfmodinstaler.ui.theme.FNFModInstalerTheme

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
