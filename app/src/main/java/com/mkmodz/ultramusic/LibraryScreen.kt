package com.mkmodz.ultramusic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LibraryScreen(vm: MainViewModel, startTab: Int, nav: Nav) {
    var tab by remember(startTab) { mutableIntStateOf(startTab) }
    var query by remember { mutableStateOf("") }
    var openGroup by remember { mutableStateOf<Group?>(null) }
    val tabs = listOf("Songs", "Albums", "Artists", "Folders")

    BackHandler(enabled = openGroup != null) { openGroup = null }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Library", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Gold, modifier = Modifier.weight(1f))
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
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = Gold) {
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
                        LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
                            itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                                SongRow(s, onClick = { vm.play(list, i) }, onMore = { nav.more(s) })
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
                    LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
                        itemsIndexed(groups, key = { _, g -> g.name }) { _, g ->
                            GroupRow(g, round = tab == 2) { openGroup = g }
                        }
                    }
                }
            }
        }
    }
}
