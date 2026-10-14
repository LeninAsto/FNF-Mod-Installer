package com.leninasto.fnfmodinstaler

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoScreen() {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            UpdateCard()
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.about_app), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.app_description), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/LeninAsto/FNF-Mod-Installer".toUri())) }) {
                            Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.source))
                        }
                        Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://buymeacoffee.com/lenin_anonimo_of".toUri())) }) {
                            Icon(Icons.Default.Favorite, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.donate))
                        }
                        Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://leninasto.github.io/FNF-Mod-Installer/privacy.html".toUri())) }) {
                            Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.terms_conditions))
                        }
                        Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/LeninAsto/FNF-Mod-Installer/issues".toUri())) }) {
                            Icon(Icons.Default.Translate, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.translate_app))
                        }
                    }

                    Button(
                        onClick = { context.startActivity(Intent(context, CreditsActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Icon(Icons.Default.People, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.tester_credits))
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.installation_guide_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            InfoCard(stringResource(R.string.guide_scoped_title), stringResource(R.string.guide_scoped_content))
            InfoCard(stringResource(R.string.guide_external_title), stringResource(R.string.guide_external_content))
            InfoCard(stringResource(R.string.guide_funkin_mobile_title), stringResource(R.string.guide_funkin_mobile_content))
            InfoCard(stringResource(R.string.guide_engines_title), stringResource(R.string.guide_engines_content))
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
