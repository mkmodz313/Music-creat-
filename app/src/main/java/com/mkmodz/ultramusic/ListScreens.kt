package com.mkmodz.ultramusic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SongListBody(title: String, songs: List<Song>, empty: String, vm: MainViewModel, nav: Nav, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, "Back") }
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Gold, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.play(songs.shuffled(), 0) }) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = Cyan) }
            IconButton(onClick = { vm.play(songs, 0) }) { Icon(Icons.Filled.PlayArrow, "Play all", tint = Gold) }
        }
        if (songs.isEmpty()) {
            Text(empty, Modifier.padding(24.dp), color = Color.White.copy(alpha = 0.7f))
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
                itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                    SongRow(s, onClick = { vm.play(songs, i) }, onMore = { nav.more(s) })
                }
            }
        }
    }
}

@Composable
fun ListScreen(key: String, vm: MainViewModel, nav: Nav, onClose: () -> Unit) {
    val title = when (key) {
        "fav" -> "Favorites"
        "recent" -> "Recently Played"
        "top" -> "Top Hits"
        "added" -> "Recently Added"
        "edm" -> "EDM"
        "rock" -> "Rock"
        "classical" -> "Classical"
        "hiphop" -> "Hip Hop"
        else -> "Songs"
    }
    val songs: List<Song> = when (key) {
        "fav" -> vm.songs.filter { it.id in Lib.favorites }
        "recent" -> Lib.recents.mapNotNull { vm.songOf(it) }
        "top" -> vm.songs.filter { Lib.count(it.id) > 0 }.sortedByDescending { Lib.count(it.id) }
        "added" -> vm.songs.sortedByDescending { it.dateAdded }.take(100)
        else -> vm.songs.filter { genreMatch(key, it) }
    }
    val empty = when (key) {
        "fav" -> "No favorites yet. Tap the heart on the Now Playing screen."
        "recent" -> "Nothing played yet."
        "top" -> "Play some songs and your most played will show up here."
        "added" -> "No songs found."
        else -> "No songs have a $title genre tag. Open a song's menu, choose Edit tags and set its genre."
    }
    Box(Modifier.fillMaxSize().background(Look.bg()).clickable(enabled = false) {}) {
        SongListBody(title, songs, empty, vm, nav, onClose)
    }
}

@Composable
fun PlaylistsScreen(vm: MainViewModel, nav: Nav, onClose: () -> Unit) {
    var open by remember { mutableStateOf<String?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    BackHandler(enabled = open != null) { open = null }

    Box(Modifier.fillMaxSize().background(Look.bg()).clickable(enabled = false) {}) {
        val o = open
        if (o != null) {
            val songs = (Lib.playlists[o] ?: emptyList()).mapNotNull { vm.songOf(it) }
            SongListBody(o, songs, "This playlist is empty. Use a song's menu and choose Add to playlist.", vm, nav) { open = null }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, "Back") }
                    Text("Playlists", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Gold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showNew = true }) { Icon(Icons.Filled.Add, "New playlist", tint = Gold) }
                }
                if (Lib.playlists.isEmpty()) {
                    Text("No playlists yet. Tap + to create one.", Modifier.padding(24.dp), color = Color.White.copy(alpha = 0.7f))
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
                    itemsIndexed(Lib.playlists.keys.toList()) { _, name ->
                        Row(
                            Modifier.fillMaxWidth().clickable { open = name }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.QueueMusic, null, tint = Look.p.c2)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.SemiBold)
                                Text("${Lib.playlists[name]?.size ?: 0} songs", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            }
                            IconButton(onClick = { Lib.deletePlaylist(name) }) { Icon(Icons.Filled.Delete, "Delete", tint = Pink) }
                        }
                    }
                }
            }
        }
    }

    if (showNew) {
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("New playlist") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { Lib.createPlaylist(newName); newName = ""; showNew = false }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } }
        )
    }
}
