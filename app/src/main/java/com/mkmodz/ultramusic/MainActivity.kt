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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Look.load()
        AudioFx.init()
        ArtStore.init(this)
        Lib.load()
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
            Shell(vm)
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
        Text("MKMODZ", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Gold)
        Text("MUSIC PLAYER", fontSize = 14.sp, letterSpacing = 4.sp, color = Cyan)
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

@Composable
fun PickPlaylistDialog(song: Song, onDismiss: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                Lib.playlists.keys.toList().forEach { n ->
                    TextButton(onClick = { Lib.addToPlaylist(n, song.id); onDismiss() }) { Text(n) }
                }
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it }, singleLine = true,
                    label = { Text("New playlist name") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (newName.isNotBlank()) { Lib.createPlaylist(newName); Lib.addToPlaylist(newName.trim(), song.id) }
                onDismiss()
            }) { Text("Create & add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun Shell(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var libTab by remember { mutableIntStateOf(0) }
    var showNow by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }
    var listKey by remember { mutableStateOf<String?>(null) }
    var editSong by remember { mutableStateOf<Song?>(null) }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var pickFor by remember { mutableStateOf<Song?>(null) }

    val nav = remember {
        Nav(
            goHome = { tab = 0; listKey = null; showPlaylists = false },
            openLibrary = { t -> libTab = t; tab = 1; listKey = null; showPlaylists = false },
            openList = { k -> listKey = k },
            openPlaylists = { showPlaylists = true },
            openNow = { showNow = true },
            openEq = { showEq = true },
            openCustom = { showCustom = true },
            more = { s -> menuSong = s }
        )
    }

    // bars fall back to zero when paused
    LaunchedEffect(vm.isPlaying) {
        if (!vm.isPlaying) {
            while (true) { delay(50); Spectrum.decay() }
        }
    }

    BackHandler(enabled = editSong != null || showCustom || showEq || showNow || showPlaylists || listKey != null || tab != 0) {
        when {
            editSong != null -> editSong = null
            showCustom -> showCustom = false
            showEq -> showEq = false
            showNow -> showNow = false
            showPlaylists -> showPlaylists = false
            listKey != null -> listKey = null
            else -> tab = 0
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (Look.bgMode == 2 && Look.globalBg != null) {
            AsyncImage(model = Look.globalBg, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color(0xBB05030C)))
        }

        if (tab == 0) HomeScreen(vm, nav) else LibraryScreen(vm, libTab, nav)

        listKey?.let { k -> ListScreen(k, vm, nav) { listKey = null } }
        if (showPlaylists) PlaylistsScreen(vm, nav) { showPlaylists = false }

        BottomChrome(vm, tab, nav, Modifier.align(Alignment.BottomCenter))

        if (showNow) NowPlaying(vm, onEq = { showEq = true }, onEdit = { s -> editSong = s }) { showNow = false }
        if (showEq) EqualizerScreen { showEq = false }
        if (showCustom) CustomizeScreen { showCustom = false }
        editSong?.let { s -> TagEditorScreen(vm, s) { editSong = null } }
    }

    menuSong?.let { s ->
        val fav = s.id in Lib.favorites
        AlertDialog(
            onDismissRequest = { menuSong = null },
            title = { Text(s.title, maxLines = 1) },
            text = {
                Column {
                    TextButton(onClick = { vm.play(listOf(s), 0); menuSong = null }) { Text("Play") }
                    TextButton(onClick = { editSong = s; menuSong = null }) { Text("Edit tags") }
                    TextButton(onClick = { Lib.toggleFav(s.id); menuSong = null }) {
                        Text(if (fav) "Remove from favorites" else "Add to favorites")
                    }
                    TextButton(onClick = { pickFor = s; menuSong = null }) { Text("Add to playlist") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { menuSong = null }) { Text("Close") } }
        )
    }
    pickFor?.let { s -> PickPlaylistDialog(s) { pickFor = null } }
}
