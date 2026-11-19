package com.mkmodz.ultramusic

import android.content.Context
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Edited tags kept inside the app (applies to every format, survives re-scans). */
object MetaStore {
    fun apply(s: Song): Song {
        val raw = Prefs.sp.getString("meta_${s.id}", null) ?: return s
        return try {
            val o = JSONObject(raw)
            s.copy(
                title = o.optString("title", s.title),
                artist = o.optString("artist", s.artist),
                album = o.optString("album", s.album),
                albumArtist = o.optString("albumArtist", s.albumArtist),
                genre = o.optString("genre", s.genre),
                year = o.optInt("year", s.year),
                track = o.optInt("track", s.track),
                composer = o.optString("composer", s.composer),
                lyrics = o.optString("lyrics", s.lyrics)
            )
        } catch (e: Exception) { s }
    }

    fun save(s: Song) {
        val o = JSONObject()
            .put("title", s.title).put("artist", s.artist).put("album", s.album)
            .put("albumArtist", s.albumArtist).put("genre", s.genre).put("year", s.year)
            .put("track", s.track).put("composer", s.composer).put("lyrics", s.lyrics)
        Prefs.sp.edit().putString("meta_${s.id}", o.toString()).apply()
    }
}

/** Custom album artwork (shown everywhere in the app). */
object ArtStore {
    private lateinit var dir: File
    var custom by mutableStateOf<Set<Long>>(emptySet())
    var none by mutableStateOf<Set<Long>>(emptySet())

    fun init(ctx: Context) {
        if (::dir.isInitialized) return
        dir = File(ctx.filesDir, "art").apply { mkdirs() }
        val c = HashSet<Long>()
        val n = HashSet<Long>()
        dir.listFiles()?.forEach { f ->
            val id = f.nameWithoutExtension.toLongOrNull() ?: return@forEach
            if (f.extension == "jpg") c.add(id) else if (f.extension == "none") n.add(id)
        }
        custom = c
        none = n
    }

    fun file(id: Long): File? = if (::dir.isInitialized && id in custom) File(dir, "$id.jpg") else null

    fun model(s: Song): Any? = file(s.id) ?: (if (s.id in none) null else s.artUri)

    fun save(id: Long, bytes: ByteArray) {
        File(dir, "$id.jpg").writeBytes(bytes)
        File(dir, "$id.none").delete()
        custom = custom + id
        none = none - id
    }

    fun markNone(id: Long) {
        File(dir, "$id.jpg").delete()
        File(dir, "$id.none").createNewFile()
        custom = custom - id
        none = none + id
    }
}

/** Favorites, recently played, play counts and playlists. */
object Lib {
    var favorites by mutableStateOf<Set<Long>>(emptySet())
    var recents by mutableStateOf<List<Long>>(emptyList())
    var playlists by mutableStateOf<Map<String, List<Long>>>(emptyMap())

    fun load() {
        val s = Prefs.sp
        favorites = (s.getString("fav", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }.toSet()
        recents = (s.getString("recent", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }
        try {
            val o = JSONObject(s.getString("playlists", "{}") ?: "{}")
            val m = LinkedHashMap<String, List<Long>>()
            o.keys().forEach { k ->
                val a = o.getJSONArray(k)
                m[k] = List(a.length()) { a.getLong(it) }
            }
            playlists = m
        } catch (e: Exception) { playlists = emptyMap() }
    }

    fun count(id: Long): Int = Prefs.sp.getInt("count_$id", 0)

    fun recordPlay(id: Long) {
        recents = (listOf(id) + recents.filter { it != id }).take(30)
        Prefs.sp.edit()
            .putString("recent", recents.joinToString(","))
            .putInt("count_$id", count(id) + 1)
            .apply()
    }

    fun toggleFav(id: Long) {
        favorites = if (id in favorites) favorites - id else favorites + id
        Prefs.sp.edit().putString("fav", favorites.joinToString(",")).apply()
    }

    private fun savePlaylists() {
        val o = JSONObject()
        playlists.forEach { (k, v) -> o.put(k, JSONArray(v)) }
        Prefs.sp.edit().putString("playlists", o.toString()).apply()
    }

    fun createPlaylist(name: String) {
        val n = name.trim()
        if (n.isEmpty() || playlists.containsKey(n)) return
        playlists = playlists + (n to emptyList())
        savePlaylists()
    }

    fun deletePlaylist(name: String) {
        playlists = playlists - name
        savePlaylists()
    }

    fun addToPlaylist(name: String, id: Long) {
        val cur = playlists[name] ?: return
        if (id in cur) return
        playlists = playlists + (name to (cur + id))
        savePlaylists()
    }

    fun removeFromPlaylist(name: String, id: Long) {
        val cur = playlists[name] ?: return
        playlists = playlists + (name to cur.filter { it != id })
        savePlaylists()
    }
}

fun genreMatch(key: String, s: Song): Boolean {
    val g = s.genre.lowercase()
    if (g.isEmpty()) return false
    return when (key) {
        "edm" -> listOf("edm", "electro", "dance", "house", "techno", "trance").any { g.contains(it) }
        "rock" -> g.contains("rock") || g.contains("metal")
        "classical" -> g.contains("classic") || g.contains("orchestra")
        "hiphop" -> g.contains("hip") || g.contains("rap")
        else -> false
    }
}
