package com.mkmodz.ultramusic

import android.content.ContentUris
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val folder: String,
    val dateAdded: Long,
    val albumArtist: String = "",
    val genre: String = "",
    val year: Int = 0,
    val track: Int = 0,
    val composer: String = "",
    val lyrics: String = "",
    val mime: String = "",
    val path: String = ""
) {
    val artUri: Uri
        get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

    fun toMediaItem(): MediaItem {
        val art = ArtStore.file(id)?.let { Uri.fromFile(it) } ?: artUri
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(art)
                    .build()
            )
            .build()
    }
}

data class Group(val name: String, val songs: List<Song>)
