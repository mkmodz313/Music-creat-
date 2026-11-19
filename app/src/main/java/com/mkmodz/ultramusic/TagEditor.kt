package com.mkmodz.ultramusic

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

fun decodeLimited(ctx: Context, uri: Uri, maxSide: Int = 2400): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}

fun cropSquare(bmp: Bitmap, zoom: Float, ox: Float, oy: Float): Bitmap {
    val side = (minOf(bmp.width, bmp.height) / zoom).toInt().coerceAtLeast(8)
    val x = (((ox + 1f) / 2f) * (bmp.width - side)).toInt().coerceIn(0, bmp.width - side)
    val y = (((oy + 1f) / 2f) * (bmp.height - side)).toInt().coerceIn(0, bmp.height - side)
    val sq = Bitmap.createBitmap(bmp, x, y, side, side)
    val out = minOf(1000, side)
    return if (out == side) sq else Bitmap.createScaledBitmap(sq, out, out, true)
}

fun Bitmap.toJpeg(): ByteArray {
    val o = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 92, o)
    return o.toByteArray()
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, number: Boolean = false, lines: Int = 1) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) },
        singleLine = lines == 1,
        minLines = lines,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
fun TagEditorScreen(vm: MainViewModel, song: Song, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    var albumArtist by remember { mutableStateOf(song.albumArtist) }
    var genre by remember { mutableStateOf(song.genre) }
    var year by remember { mutableStateOf(if (song.year > 0) song.year.toString() else "") }
    var track by remember { mutableStateOf(if (song.track > 0) song.track.toString() else "") }
    var composer by remember { mutableStateOf(song.composer) }
    var lyrics by remember { mutableStateOf(song.lyrics) }

    var src by remember { mutableStateOf<Bitmap?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }
    var removeArt by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val cropped: Bitmap? = remember(src, zoom, offX, offY) { src?.let { cropSquare(it, zoom, offX, offY) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val b = decodeLimited(ctx, uri)
            if (b == null) Toast.makeText(ctx, "Could not open this image", Toast.LENGTH_SHORT).show()
            else { src = b; removeArt = false; zoom = 1f; offX = 0f; offY = 0f }
        }
    }

    var afterGrant by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        afterGrant?.invoke(r.resultCode == Activity.RESULT_OK)
        afterGrant = null
    }

    fun requestWrite(cb: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val pi = MediaStore.createWriteRequest(ctx.contentResolver, listOf(song.uri))
                afterGrant = cb
                writeLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } catch (e: Exception) { cb(false) }
        } else cb(true)
    }

    fun writeFile(granted: Boolean, edited: Song, art: ByteArray?): String {
        if (!granted) return "Saved inside the app. The file itself was not changed (permission not given)."
        return try {
            TagWriter.updateMediaStore(ctx, song, edited)
            if (TagWriter.isMp3(song)) {
                TagWriter.writeMp3(ctx, song, edited, art, removeArt)
                "Saved to the music file and the app."
            } else {
                "Saved. This format is not MP3, so edits are kept inside the app and the library."
            }
        } catch (e: Exception) {
            "Saved inside the app. Writing the file failed: ${e.message}"
        }
    }

    fun save() {
        if (saving) return
        saving = true
        val edited = song.copy(
            title = title.trim().ifEmpty { song.title },
            artist = artist.trim().ifEmpty { "Unknown Artist" },
            album = album.trim().ifEmpty { "Unknown Album" },
            albumArtist = albumArtist.trim(),
            genre = genre.trim(),
            year = year.trim().toIntOrNull() ?: 0,
            track = track.trim().substringBefore('/').toIntOrNull() ?: 0,
            composer = composer.trim(),
            lyrics = lyrics
        )
        val artBytes = cropped?.toJpeg()
        scope.launch {
            withContext(Dispatchers.IO) {
                MetaStore.save(edited)
                if (artBytes != null) ArtStore.save(song.id, artBytes)
                else if (removeArt) ArtStore.markNone(song.id)
            }
            vm.updateSong(edited)
            requestWrite { ok ->
                scope.launch {
                    val msg = withContext(Dispatchers.IO) { writeFile(ok, edited, artBytes) }
                    saving = false
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    onClose()
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Look.bg()).clickable(enabled = false) {}) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, "Back") }
                Text("Tag Editor", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Gold, modifier = Modifier.weight(1f))
                Button(onClick = { save() }, enabled = !saving, shape = RoundedCornerShape(50)) { Text(if (saving) "Saving..." else "Save") }
            }

            SectionTitle("ALBUM ARTWORK")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(130.dp).clip(RoundedCornerShape(18.dp)).background(Glass),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MusicNote, null, tint = Purple, modifier = Modifier.size(48.dp))
                    if (cropped != null) {
                        Image(cropped.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else if (!removeArt) {
                        AsyncImage(model = ArtStore.model(song), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Button(onClick = { picker.launch("image/*") }, shape = RoundedCornerShape(50)) { Text("Choose image") }
                    TextButton(onClick = { src = null; removeArt = true }) { Text("Remove artwork", color = Pink) }
                    if (src != null || removeArt) {
                        TextButton(onClick = { src = null; removeArt = false }) { Text("Keep current", color = Cyan) }
                    }
                }
            }
            if (src != null) {
                Text("Crop & resize (saved as a square 1000 px image)", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                Text("Zoom", fontSize = 12.sp)
                Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..4f)
                Text("Left / Right", fontSize = 12.sp)
                Slider(value = offX, onValueChange = { offX = it }, valueRange = -1f..1f)
                Text("Up / Down", fontSize = 12.sp)
                Slider(value = offY, onValueChange = { offY = it }, valueRange = -1f..1f)
            }

            SectionTitle("TAGS")
            Field("Title", title, { title = it })
            Field("Artist", artist, { artist = it })
            Field("Album", album, { album = it })
            Field("Album artist", albumArtist, { albumArtist = it })
            Field("Genre", genre, { genre = it })
            Field("Release year", year, { year = it.filter { c -> c.isDigit() }.take(4) }, number = true)
            Field("Track number", track, { track = it.filter { c -> c.isDigit() }.take(3) }, number = true)
            Field("Composer", composer, { composer = it })
            Field("Lyrics", lyrics, { lyrics = it }, lines = 6)

            Text(
                if (TagWriter.isMp3(song)) "MP3: tags and cover are written into the real file (Android will ask permission once)."
                else "This file is not an MP3: edits are saved in the app and the Android library.",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
