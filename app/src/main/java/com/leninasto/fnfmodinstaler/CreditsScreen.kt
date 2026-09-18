package com.leninasto.fnfmodinstaler

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class Tester(val name: String, val link: String)

@Composable
fun CreditsScreen(modifier: Modifier = Modifier) {
    val testers = listOf(
        Tester("Literally Asbellin", "https://www.youtube.com/@LiterallyAsbelin"),
        Tester("Yeisón Kajale", "https://www.facebook.com/profile.php?id=100087169724719"),
        Tester("Cami Dibujos", "https://www.facebook.com/camilo.XD.707251"),
        Tester("Abimator", "https://www.facebook.com/profile.php?id=100093530895516"),
        Tester("Chester Brawl", "https://www.facebook.com/Excalay"),
        Tester("Criss Dracometa", "https://www.facebook.com/PrototypeCriss26"),
        Tester("Justin HC", "https://www.facebook.com/justinjesus.huamannahuicardenas.3"),
        Tester("Omarux Garcia", "https://www.facebook.com/omaralfonzo.garcia"),
        Tester("Reiver Figueroa", "https://www.facebook.com/reiver.figueroa"),
        Tester("Ruffles Dx", "https://www.facebook.com/RufflesDx"),
        Tester("Jhunior RS", "https://www.facebook.com/jhunior.188"),
        Tester("Ricardo Uriarte", "https://www.facebook.com/profile.php?id=100083327745800"),
        Tester("Geiser Acosta", "https://www.facebook.com/geiser.acosta.586015"),
        Tester("Andres Gamer", "https://youtube.com/@andres_gamer210worker7?si=--6XJgNZSKEqCevS"),
        Tester("Santu144", "https://youtube.com/@santuu_144?si=B_t9U-qR3u2DH6gZ"),
        Tester("Monika_black", "")
    )

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.thanks_testers), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(testers) { tester ->
                TesterItem(tester)
            }
        }
    }
}

@Composable
private fun TesterItem(tester: Tester) {
    val context = LocalContext.current
    val hasLink = tester.link.isNotEmpty()

    val labelRes = when {
        tester.link.contains("youtube.com") || tester.link.contains("youtu.be") -> R.string.visit_youtube
        tester.link.contains("github.com") -> R.string.visit_github
        tester.link.contains("facebook.com") -> R.string.visit_facebook
        else -> R.string.visit_web
    }

    OutlinedButton(
        onClick = {
            if (hasLink) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tester.link)))
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = hasLink,
        colors = if (!hasLink) {
            ButtonDefaults.outlinedButtonColors(
                disabledContentColor = MaterialTheme.colorScheme.onSurface
            )
        } else {
            ButtonDefaults.outlinedButtonColors()
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(tester.name, fontWeight = FontWeight.Bold)

            if (hasLink) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
