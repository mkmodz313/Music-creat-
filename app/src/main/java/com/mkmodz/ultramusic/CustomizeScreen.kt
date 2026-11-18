package com.mkmodz.ultramusic

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CustomizeScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
            Look.globalBg = uri.toString()
            Look.bgMode = 2
            Look.save()
        }
    }
    Box(Modifier.fillMaxSize().background(Look.bg()).clickable(enabled = false) {}) {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, "Back") }
                Text("Customize", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Gold)
            }

            SectionTitle("LUXURY THEMES")
            Look.palettes.forEachIndexed { i, pal ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Glass)
                        .clickable { Look.themeIndex = i; Look.save() }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(pal.c1, pal.c2, pal.c3)))
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(pal.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (Look.themeIndex == i) Icon(Icons.Filled.Check, null, tint = Gold)
                }
            }

            SectionTitle("PLAYER BACKGROUND")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                MkChip("Blurred artwork", Look.bgMode == 0) { Look.bgMode = 0; Look.save() }
                MkChip("Gradient only", Look.bgMode == 1) { Look.bgMode = 1; Look.save() }
                MkChip("My image", Look.bgMode == 2) { Look.bgMode = 2; Look.save() }
            }
            Row {
                TextButton(onClick = { picker.launch(arrayOf("image/*")) }) { Text("Choose image from phone", color = Cyan) }
                if (Look.globalBg != null) {
                    TextButton(onClick = { Look.globalBg = null; if (Look.bgMode == 2) Look.bgMode = 0; Look.save() }) {
                        Text("Remove", color = Pink)
                    }
                }
            }
            Text(
                "Tip: for one song only, open it in Now Playing and tap the image icon.",
                fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f)
            )

            SectionTitle("VISUALIZER STYLE")
            Row {
                listOf("Bars", "Mirror", "Wave").forEachIndexed { i, n ->
                    MkChip(n, Look.vizStyle == i) { Look.vizStyle = i; Look.save() }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
