package com.mkmodz.ultramusic

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

class Nav(
    val goHome: () -> Unit,
    val openLibrary: (Int) -> Unit,
    val openList: (String) -> Unit,
    val openPlaylists: () -> Unit,
    val openNow: () -> Unit,
    val openEq: () -> Unit,
    val openCustom: () -> Unit,
    val more: (Song) -> Unit
)

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun Art(model: Any?, size: Dp, round: Boolean = false) {
    Box(
        Modifier.size(size).clip(if (round) CircleShape else RoundedCornerShape(12.dp)).background(Glass),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.MusicNote, null, tint = Purple)
        if (model != null) AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun SongRow(s: Song, onClick: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(ArtStore.model(s), 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        }
        Text(fmt(s.durationMs), fontSize = 12.sp, color = Cyan)
        IconButton(onClick = onMore) { Icon(Icons.Filled.MoreVert, "More", tint = Color.White.copy(alpha = 0.8f)) }
    }
}

@Composable
fun GroupRow(g: Group, round: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(ArtStore.model(g.songs.first()), 56.dp, round)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(g.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text("${g.songs.size} songs", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Gold)
    }
}

@Composable
fun RoundIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color(0x88000000))
            .border(2.dp, Look.accent(), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

@Composable
fun MiniPlayer(vm: MainViewModel, nav: Nav) {
    LaunchedEffect(Unit) { while (true) { vm.refreshPosition(); delay(500) } }
    val dur = vm.durationMs.coerceAtLeast(1L)
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xF2120824), Color(0xF22A0F4F))))
            .border(2.dp, Look.accent(), shape)
            .clickable { nav.openNow() }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(54.dp).border(2.dp, Gold, CircleShape).padding(3.dp).clip(CircleShape).background(Glass)) {
            AsyncImage(model = vm.displayArt, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("${vm.displayArtist} - ${vm.displayTitle}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("${fmt(vm.positionMs)} / ${fmt(vm.durationMs)}", fontSize = 11.sp, color = Cyan)
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { (vm.positionMs.toFloat() / dur).coerceIn(0f, 1f) },
                color = Look.p.c2, trackColor = Color(0x33FFFFFF),
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )
        }
        IconButton(onClick = { vm.previous() }, modifier = Modifier.size(38.dp)) { Icon(Icons.Filled.SkipPrevious, "Previous") }
        Box(
            Modifier.size(44.dp).border(2.dp, Gold, CircleShape).clip(CircleShape).clickable { vm.togglePlay() },
            contentAlignment = Alignment.Center
        ) { Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", tint = Gold) }
        IconButton(onClick = { vm.next() }, modifier = Modifier.size(38.dp)) { Icon(Icons.Filled.SkipNext, "Next") }
        IconButton(onClick = { nav.openLibrary(0) }, modifier = Modifier.size(38.dp)) { Icon(Icons.Filled.QueueMusic, "Library", tint = Cyan) }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().padding(4.dp).clip(RoundedCornerShape(24.dp))
            .background(if (selected) Brush.verticalGradient(listOf(Color(0x66FFC857), Color(0x22FFC857))) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = if (selected) Gold else Color.White.copy(alpha = 0.85f), modifier = Modifier.size(26.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Gold else Color.White.copy(alpha = 0.85f))
    }
}

@Composable
fun BottomChrome(vm: MainViewModel, tab: Int, nav: Nav, modifier: Modifier = Modifier) {
    Column(modifier.navigationBarsPadding().padding(bottom = 6.dp)) {
        if (vm.hasItem) {
            MiniPlayer(vm, nav)
            Spacer(Modifier.height(6.dp))
        }
        val shape = RoundedCornerShape(34.dp)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(68.dp).clip(shape)
                .background(Color(0xF2050309)).border(2.dp, Look.accent(), shape),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(Icons.Filled.Home, "Home", tab == 0, Modifier.weight(1f)) { nav.goHome() }
            NavItem(Icons.Filled.LibraryMusic, "Library", tab == 1, Modifier.weight(1f)) { nav.openLibrary(0) }
            Box(Modifier.weight(1.1f).fillMaxHeight().clickable { nav.openNow() }.padding(horizontal = 6.dp, vertical = 12.dp)) {
                SpectrumView({ AudioFx.bars }, 0, Modifier.fillMaxSize())
            }
            NavItem(Icons.Filled.Equalizer, "Equalizer", false, Modifier.weight(1f)) { nav.openEq() }
            NavItem(Icons.Filled.Person, "Profile", false, Modifier.weight(1f)) { nav.openCustom() }
        }
    }
}
