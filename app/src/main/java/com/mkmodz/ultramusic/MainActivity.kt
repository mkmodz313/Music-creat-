package com.mkmodz.ultramusic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Look.load()
        AudioFx.init()
        setContent { MkTheme { App(vm) } }
    }
}

@Composable
fun App(vm: MainViewModel) {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }

    LaunchedEffect(granted) {
        if (granted) {
            vm.loadSongs()
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(Modifier.fillMaxSize().background(Look.bg())) {
        if (granted && Look.bgMode == 2 && Look.globalBg != null) {
            AsyncImage(model = Look.globalBg, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color(0xBB05030C)))
        }
        if (!granted) {
            PermissionScreen(
                denied = denied,
                onGrant = { launcher.launch(perm) },
                onSettings = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                    )
                }
            )
        } else {
            MainScreen(vm)
        }
    }
}

@Composable
fun PermissionScreen(denied: Boolean, onGrant: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(120.dp).clip(CircleShape).background(Look.accent()),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.MusicNote, null, tint = Color.Black, modifier = Modifier.size(64.dp)) }
        Spacer(Modifier.height(28.dp))
        Text("MKMODZ ULTRA", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Gold)
        Text("MUSIC PLAYER PRO", fontSize = 14.sp, letterSpacing = 4.sp, color = Cyan)
        Spacer(Modifier.height(24.dp))
        Text(
            if (denied) "Music access was denied. The player needs it to find the songs stored on your phone. Nothing is uploaded anywhere."
            else "Allow access to your audio files so the player can scan your real music library.",
            textAlign = TextAlign.Center, color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onGrant, shape = RoundedCornerShape(50)) {
            Text(if (denied) "Try again" else "Allow music access")
        }
        if (denied) {
            TextButton(onClick = onSettings) { Text("Open app settings", color = Cyan) }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var openGroup by remember { mutableStateOf<Group?>(null) }
    var showNow by remember { mutableStateOf(false) }
    val tabs = listOf("Songs", "Albums", "Artists", "Folders")

    var showEq by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    BackHandler(enabled = showEq || showCustom || showNow || openGroup != null) {
        when {
            showCustom -> showCustom = false
            showEq -> showEq = false
            showNow -> showNow = false
            else -> openGroup = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("MKMODZ ULTRA", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Gold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showEq = true }) { Icon(Icons.Filled.Equalizer, "Equalizer", tint = Cyan) }
                IconButton(onClick = { showCustom = true }) { Icon(Icons.Filled.Palette, "Customize", tint = Pink) }
                IconButton(onClick = { vm.loadSongs() }) { Icon(Icons.Filled.Refresh, "Rescan", tint = Cyan) }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search songs, artists, albums") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            TabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,
                contentColor = Gold
            ) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i; openGroup = null }, text = { Text(t) })
                }
            }

            val q = query.trim().lowercase()
            val filtered = remember(vm.songs, q) {
                if (q.isEmpty()) vm.songs
                else vm.songs.filter {
                    it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
                }
            }

            Box(Modifier.weight(1f)) {
                if (vm.loading && vm.songs.isEmpty()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Gold)
                } else if (vm.songs.isEmpty()) {
                    Text("No music found on this device.", Modifier.align(Alignment.Center), color = Color.White.copy(alpha = 0.7f))
                } else {
                    val group = openGroup
                    if (tab == 0 || group != null) {
                        val list = group?.songs?.filter { s -> filtered.contains(s) } ?: filtered
                        Column {
                            if (group != null) {
                                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { openGroup = null }) { Icon(Icons.Filled.ArrowBack, "Back") }
                                    Text(group.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            LazyColumn(contentPadding = PaddingValues(bottom = 90.dp)) {
                                itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                                    SongRow(s) { vm.play(list, i) }
                                }
                            }
                        }
                    } else {
                        val groups = remember(filtered, tab) {
                            when (tab) {
                                1 -> filtered.groupBy { it.album }
                                2 -> filtered.groupBy { it.artist }
                                else -> filtered.groupBy { it.folder }
                            }.map { Group(it.key, it.value) }.sortedBy { it.name.lowercase() }
                        }
                        LazyColumn(contentPadding = PaddingValues(bottom = 90.dp)) {
                            itemsIndexed(groups, key = { _, g -> g.name }) { _, g ->
                                GroupRow(g, round = tab == 2) { openGroup = g }
                            }
                        }
                    }
                }
            }
        }

        if (vm.hasItem) {
            MiniPlayer(vm, Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(10.dp)) { showNow = true }
        }
        if (showNow) NowPlaying(vm, onEq = { showEq = true }) { showNow = false }
        if (showEq) EqualizerScreen { showEq = false }
        if (showCustom) CustomizeScreen { showCustom = false }
    }
}

@Composable
fun Art(uri: Uri?, size: androidx.compose.ui.unit.Dp, round: Boolean = false) {
    Box(
        Modifier.size(size).clip(if (round) CircleShape else RoundedCornerShape(12.dp)).background(Glass),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.MusicNote, null, tint = Purple)
        if (uri != null) AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun SongRow(s: Song, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(s.artUri, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        }
        Text(fmt(s.durationMs), fontSize = 12.sp, color = Cyan)
    }
}

@Composable
fun GroupRow(g: Group, round: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(g.songs.first().artUri, 56.dp, round)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(g.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text("${g.songs.size} songs", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Gold)
    }
}

@Composable
fun MiniPlayer(vm: MainViewModel, modifier: Modifier, onOpen: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xEE241446))
            .clickable(onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(vm.artUri, 46.dp, round = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(vm.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(vm.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        }
        IconButton(onClick = { vm.previous() }) { Icon(Icons.Filled.SkipPrevious, "Previous") }
        IconButton(onClick = { vm.togglePlay() }) {
            Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", tint = Gold)
        }
        IconButton(onClick = { vm.next() }) { Icon(Icons.Filled.SkipNext, "Next") }
    }
}

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
