package com.mkmodz.ultramusic

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MusicRepository {

    /** Scans the real device music library through MediaStore. */
    suspend fun load(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val A = MediaStore.Audio.Media
        val base = listOf(
            A._ID, A.TITLE, A.ARTIST, A.ALBUM, A.ALBUM_ID, A.DURATION, A.DATA,
            A.DATE_ADDED, A.YEAR, A.TRACK, A.COMPOSER, A.MIME_TYPE
        )
        val list = try {
            if (Build.VERSION.SDK_INT >= 30) query(context, base + listOf("genre", "album_artist"))
            else query(context, base)
        } catch (e: Exception) {
            query(context, base)
        }
        list.map { MetaStore.apply(it) }
    }

    private fun query(context: Context, columns: List<String>): List<Song> {
        val out = ArrayList<Song>()
        val A = MediaStore.Audio.Media
        val collection = if (Build.VERSION.SDK_INT >= 29)
            A.getContentUri(MediaStore.VOLUME_EXTERNAL) else A.EXTERNAL_CONTENT_URI
        val selection = "${A.IS_MUSIC} != 0 AND ${A.DURATION} >= 20000"
        context.contentResolver.query(
            collection, columns.toTypedArray(), selection, null, "${A.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            fun idx(n: String) = c.getColumnIndex(n)
            val iId = idx(A._ID); val iTitle = idx(A.TITLE); val iArtist = idx(A.ARTIST)
            val iAlbum = idx(A.ALBUM); val iAlbumId = idx(A.ALBUM_ID); val iDur = idx(A.DURATION)
            val iData = idx(A.DATA); val iAdded = idx(A.DATE_ADDED); val iYear = idx(A.YEAR)
            val iTrack = idx(A.TRACK); val iComp = idx(A.COMPOSER); val iMime = idx(A.MIME_TYPE)
            val iGenre = idx("genre"); val iAA = idx("album_artist")
            fun str(i: Int): String = if (i >= 0) (c.getString(i) ?: "") else ""
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val path = str(iData)
                val artist = str(iArtist).let { if (it.isEmpty() || it == "<unknown>") "Unknown Artist" else it }
                out.add(
                    Song(
                        id = id,
                        title = str(iTitle).ifEmpty { "Unknown" },
                        artist = artist,
                        album = str(iAlbum).ifEmpty { "Unknown Album" },
                        albumId = c.getLong(iAlbumId),
                        durationMs = c.getLong(iDur),
                        uri = ContentUris.withAppendedId(A.EXTERNAL_CONTENT_URI, id),
                        folder = File(path).parentFile?.name ?: "Unknown",
                        dateAdded = c.getLong(iAdded),
                        albumArtist = str(iAA),
                        genre = str(iGenre),
                        year = if (iYear >= 0) c.getInt(iYear) else 0,
                        track = if (iTrack >= 0) c.getInt(iTrack) % 1000 else 0,
                        composer = str(iComp),
                        mime = str(iMime),
                        path = path
                    )
                )
            }
        }
        return out
    }
}
