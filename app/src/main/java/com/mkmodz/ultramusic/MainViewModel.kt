package com.mkmodz.ultramusic

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    var songs by mutableStateOf<List<Song>>(emptyList())
    var loading by mutableStateOf(false)

    var hasItem by mutableStateOf(false)
    var mediaId by mutableStateOf("")
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var artUri by mutableStateOf<Uri?>(null)
    var isPlaying by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
    var positionMs by mutableStateOf(0L)
    var durationMs by mutableStateOf(0L)

    val currentSong: Song? get() = songs.firstOrNull { it.id.toString() == mediaId }
    val displayTitle: String get() = currentSong?.title ?: title
    val displayArtist: String get() = currentSong?.artist ?: artist
    val displayArt: Any?
        get() {
            val s = currentSong
            return if (s != null) ArtStore.model(s) else artUri
        }

    private var controller: MediaController? = null

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    sync(player)
                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                        player.currentMediaItem?.mediaId?.toLongOrNull()?.let { Lib.recordPlay(it) }
                    }
                }
            })
            sync(c)
        }, ContextCompat.getMainExecutor(app))
    }

    private fun sync(p: Player) {
        hasItem = p.mediaItemCount > 0
        mediaId = p.currentMediaItem?.mediaId ?: ""
        val md = p.mediaMetadata
        title = md.title?.toString() ?: ""
        artist = md.artist?.toString() ?: ""
        artUri = md.artworkUri
        isPlaying = p.isPlaying
        shuffle = p.shuffleModeEnabled
        repeatMode = p.repeatMode
        durationMs = if (p.duration > 0) p.duration else 0L
        positionMs = p.currentPosition
    }

    fun refreshPosition() {
        controller?.let { positionMs = it.currentPosition; if (it.duration > 0) durationMs = it.duration }
    }

    fun loadSongs() {
        viewModelScope.launch {
            loading = true
            songs = MusicRepository.load(getApplication())
            loading = false
        }
    }

    fun songOf(id: Long): Song? = songs.firstOrNull { it.id == id }

    fun updateSong(u: Song) {
        songs = songs.map { if (it.id == u.id) u else it }
    }

    fun play(list: List<Song>, index: Int) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.setMediaItems(list.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) { controller?.seekTo(ms); positionMs = ms }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }
}
