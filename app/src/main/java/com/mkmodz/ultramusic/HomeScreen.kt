package com.mkmodz.ultramusic

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun SeeAllHeader(title: String, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.crown), null, Modifier.height(32.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = Color.White, modifier = Modifier.weight(1f))
        Box(
            Modifier.clip(RoundedCornerShape(50)).border(1.5.dp, Look.p.c1, RoundedCornerShape(50))
                .background(Color(0x33000000)).clickable(onClick = onSeeAll)
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) { Text("See All  →", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Look.p.c2) }
    }
}

@Composable
fun RecentCard(s: Song, onPlay: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.width(96.dp).height(122.dp).clip(shape).border(2.dp, Look.accent(), shape)
            .background(Glass).clickable(onClick = onPlay)
    ) {
        Icon(Icons.Filled.MusicNote, null, tint = Purple, modifier = Modifier.align(Alignment.Center).size(34.dp))
        AsyncImage(model = ArtStore.model(s), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))).padding(6.dp)
        ) {
            Column {
                Text(s.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.artist, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier.align(Alignment.Center).size(30.dp).clip(CircleShape).background(Color(0x77000000))
                .border(1.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
fun HomeScreen(vm: MainViewModel, nav: Nav) {
    val recentSongs = Lib.recents.mapNotNull { vm.songOf(it) }
    val usingHistory = recentSongs.isNotEmpty()
    val shown = if (usingHistory) recentSongs else vm.songs.sortedByDescending { it.dateAdded }.take(12)

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        // header
        Box(Modifier.fillMaxWidth().height(124.dp)) {
            Image(
                painterResource(R.drawable.logo), null,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 62.dp).fillMaxWidth(0.5f),
                contentScale = ContentScale.Fit
            )
            Row(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                RoundIcon(Icons.Filled.Menu, "Menu") { nav.openLibrary(0) }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundIcon(Icons.Filled.Search, "Search") { nav.openLibrary(0) }
                RoundIcon(Icons.Filled.Notifications, "Recently added") { nav.openList("added") }
                RoundIcon(Icons.Filled.Settings, "Settings") { nav.openCustom() }
            }
        }

        // hero banner
        val heroShape = RoundedCornerShape(28.dp)
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(190.dp).clip(heroShape)
                .background(Brush.horizontalGradient(listOf(Color(0xFF0B0714), Color(0xFF2A0F4F))))
                .border(2.dp, Look.accent(), heroShape)
        ) {
            Image(
                painterResource(R.drawable.headphone), null,
                modifier = Modifier.align(Alignment.CenterEnd).offset(x = 22.dp).fillMaxHeight().aspectRatio(1f),
                contentScale = ContentScale.Crop
            )
            SpectrumView({ AudioFx.bars }, 0, Modifier.align(Alignment.BottomStart).padding(start = 150.dp, bottom = 14.dp).size(110.dp, 54.dp))
            Column(Modifier.align(Alignment.CenterStart).padding(start = 18.dp, bottom = 22.dp)) {
                Text("Feel The Music", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("In Every Beat", fontSize = 26.sp, fontFamily = FontFamily.Cursive, fontStyle = FontStyle.Italic, color = Gold)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(Brush.verticalGradient(listOf(Color(0xFFFFE08A), Gold, Color(0xFFB8860B))))
                        .clickable { if (vm.songs.isNotEmpty()) vm.play(vm.songs.shuffled(), 0) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.Black)
                    Spacer(Modifier.width(4.dp))
                    Text("Play Now", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            Box(Modifier.padding(3.dp).size(width = 22.dp, height = 7.dp).clip(RoundedCornerShape(50)).background(Gold))
            Box(Modifier.padding(3.dp).size(10.dp, 7.dp).clip(RoundedCornerShape(50)).background(Color(0x66FFFFFF)))
            Box(Modifier.padding(3.dp).size(10.dp, 7.dp).clip(RoundedCornerShape(50)).background(Color(0x66FFFFFF)))
        }

        // quick row
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp)) {
            Image(painterResource(R.drawable.quick_row), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
            Row(Modifier.matchParentSize()) {
                val actions: List<() -> Unit> = listOf(
                    { nav.openLibrary(0) }, { nav.openList("fav") }, { nav.openList("recent") },
                    { nav.openPlaylists() }, { nav.openLibrary(3) }
                )
                actions.forEach { a -> Box(Modifier.weight(1f).fillMaxHeight().clickable(onClick = a)) }
            }
        }

        SeeAllHeader(if (usingHistory) "Recently Played" else "Recently Added") { nav.openList(if (usingHistory) "recent" else "added") }
        Spacer(Modifier.height(8.dp))
        if (shown.isEmpty()) {
            Text("Your music will appear here.", Modifier.padding(16.dp), color = Color.White.copy(alpha = 0.6f))
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { s ->
                    RecentCard(s) { vm.play(shown, shown.indexOf(s)) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SeeAllHeader("Popular Genres") { nav.openLibrary(0) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val tiles = listOf(
                R.drawable.g_tophits to "top", R.drawable.g_edm to "edm", R.drawable.g_rock to "rock",
                R.drawable.g_classical to "classical", R.drawable.g_hiphop to "hiphop"
            )
            tiles.forEach { (res, key) ->
                Image(
                    painterResource(res), null,
                    modifier = Modifier.weight(1f).aspectRatio(1.18f).clip(RoundedCornerShape(14.dp)).clickable { nav.openList(key) },
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(Modifier.height(190.dp))
    }
}
