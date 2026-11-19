package com.mkmodz.ultramusic

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun NowPlaying(vm: MainViewModel, onEq: () -> Unit, onEdit: (Song) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { while (true) { vm.refreshPosition(); delay(400) } }

    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = vm.durationMs.coerceAtLeast(1L)
    val value = drag ?: (vm.positionMs.toFloat() / dur).coerceIn(0f, 1f)

    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(vm.isPlaying) {
        while (vm.isPlaying) { withFrameNanos { angle = (angle + 0.2f) % 360f } }
    }

    val song = vm.currentSong
    var showLyrics by remember { mutableStateOf(false) }

    var songBg by remember(vm.mediaId) { mutableStateOf(Prefs.sp.getString("bg_${vm.mediaId}", null)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
            Prefs.sp.edit().putString("bg_${vm.mediaId}", uri.toString()).apply()
            songBg = uri.toString()
        }
    }

    val artModel = vm.displayArt
    val bgModel: Any? = songBg ?: when (Look.bgMode) {
        2 -> Look.globalBg
        0 -> artModel
        else -> null
    }
    val blurIt = songBg == null && Look.bgMode == 0 && Build.VERSION.SDK_INT >= 31

    Box(Modifier.fillMaxSize().background(Look.bg()).clickable(enabled = false) {}) {
        if (bgModel != null) {
            AsyncImage(
                model = bgModel, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (blurIt) Modifier.blur(40.dp) else Modifier)
            )
            Box(Modifier.fillMaxSize().background(Color(0xAA05030C)))
        }
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Close", modifier = Modifier.size(32.dp)) }
                Spacer(Modifier.weight(1f))
                Text("NOW PLAYING", letterSpacing = 3.sp, fontSize = 12.sp, color = Look.p.c1)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { picker.launch(arrayOf("image/*")) }) { Icon(Icons.Filled.Image, "Song background", tint = Look.p.c3) }
                IconButton(onClick = { song?.let(onEdit) }) { Icon(Icons.Filled.Edit, "Edit tags", tint = Look.p.c3) }
                IconButton(onClick = onEq) { Icon(Icons.Filled.Equalizer, "Equalizer", tint = Look.p.c3) }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth(0.72f).aspectRatio(1f)
                    .graphicsLayer { rotationZ = angle }
                    .border(4.dp, Look.accent(), CircleShape)
                    .padding(6.dp).clip(CircleShape).background(Glass),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MusicNote, null, tint = Purple, modifier = Modifier.size(96.dp))
                AsyncImage(model = artModel, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(20.dp))
            Text(vm.displayTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(vm.displayArtist, color = Look.p.c3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val fav = song != null && song.id in Lib.favorites
                IconButton(onClick = { song?.let { Lib.toggleFav(it.id) } }) {
                    Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite", tint = if (fav) Pink else Color.White)
                }
                IconButton(onClick = { showLyrics = true }) { Icon(Icons.Filled.Subject, "Lyrics", tint = Look.p.c1) }
                if (songBg != null) {
                    TextButton(onClick = { Prefs.sp.edit().remove("bg_${vm.mediaId}").apply(); songBg = null }) {
                        Text("Reset background", color = Pink, fontSize = 12.sp)
                    }
                }
            }

            SpectrumView(
                { AudioFx.bars }, Look.vizStyle,
                Modifier.fillMaxWidth().height(90.dp).clickable { Look.vizStyle = (Look.vizStyle + 1) % 3; Look.save() }
            )

            Slider(
                value = value,
                onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { vm.seekTo((it * dur).toLong()) }; drag = null },
                colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Look.p.c2)
            )
            Row(Modifier.fillMaxWidth()) {
                Text(fmt((value * dur).toLong()), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(fmt(vm.durationMs), fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.toggleShuffle() }) {
                    Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (vm.shuffle) Gold else Color.White.copy(alpha = 0.5f))
                }
                IconButton(onClick = { vm.previous() }) { Icon(Icons.Filled.SkipPrevious, "Previous", modifier = Modifier.size(40.dp)) }
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Look.accent()).clickable { vm.togglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", tint = Color.Black, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { vm.next() }) { Icon(Icons.Filled.SkipNext, "Next", modifier = Modifier.size(40.dp)) }
                IconButton(onClick = { vm.cycleRepeat() }) {
                    Icon(
                        if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat",
                        tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.5f) else Gold
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showLyrics) {
        AlertDialog(
            onDismissRequest = { showLyrics = false },
            title = { Text(vm.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Text(
                    song?.lyrics?.takeIf { it.isNotBlank() } ?: "No lyrics saved for this song. Add them in Edit tags.",
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = { TextButton(onClick = { showLyrics = false }) { Text("Close") } }
        )
    }
}
