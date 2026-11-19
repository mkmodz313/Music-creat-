package com.mkmodz.ultramusic

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * Real tag writing.
 *  - MP3: the ID3v2.3 tag inside the actual file is rewritten (title, artist, album, album artist,
 *    genre, year, track, composer, lyrics, cover art). Other frames of an existing v2.3 tag are kept.
 *  - All formats: the Android MediaStore entry is updated too.
 */
object TagWriter {
    private val REPLACED = setOf("TIT2", "TPE1", "TALB", "TPE2", "TCON", "TYER", "TRCK", "TCOM", "USLT", "TDRC", "TDAT")

    fun isMp3(s: Song) = s.mime == "audio/mpeg" || s.path.endsWith(".mp3", ignoreCase = true)

    fun updateMediaStore(ctx: Context, orig: Song, e: Song) {
        val A = MediaStore.Audio.Media
        val cv = ContentValues().apply {
            put(A.TITLE, e.title)
            put(A.ARTIST, e.artist)
            put(A.ALBUM, e.album)
            put(A.YEAR, e.year)
            put(A.TRACK, e.track)
            put(A.COMPOSER, e.composer)
        }
        ctx.contentResolver.update(orig.uri, cv, null, null)
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val cv2 = ContentValues().apply { put("genre", e.genre); put("album_artist", e.albumArtist) }
                ctx.contentResolver.update(orig.uri, cv2, null, null)
            } catch (ex: Exception) { }
        }
    }

    fun writeMp3(ctx: Context, orig: Song, e: Song, art: ByteArray?, removeArt: Boolean) {
        val cr = ctx.contentResolver
        val tmp = File(ctx.cacheDir, "audio_tmp.bin")
        val kept = ArrayList<ByteArray>()

        val raw = cr.openInputStream(orig.uri) ?: throw IllegalStateException("Cannot open file")
        raw.use { rawIn ->
            val input = BufferedInputStream(rawIn, 1 shl 16)
            input.mark(16)
            val h = ByteArray(10)
            val n = readFully(input, h)
            val isId3 = n == 10 && h[0] == 'I'.code.toByte() && h[1] == 'D'.code.toByte() && h[2] == '3'.code.toByte()
            if (isId3) {
                val ver = h[3].toInt()
                val flags = h[5].toInt()
                val size = synchsafe(h, 6)
                val body = ByteArray(size)
                readFully(input, body)
                if (ver == 4 && (flags and 0x10) != 0) input.skip(10)
                val plain = (flags and 0x80) == 0 && (flags and 0x40) == 0
                if (plain && (ver == 3 || ver == 4)) {
                    parseFrames(body, ver, kept, keepApic = art == null && !removeArt)
                }
            } else {
                input.reset()
            }
            tmp.outputStream().use { out -> input.copyTo(out) }
        }

        val frames = ArrayList<ByteArray>()
        text("TIT2", e.title)?.let { frames.add(it) }
        text("TPE1", e.artist)?.let { frames.add(it) }
        text("TALB", e.album)?.let { frames.add(it) }
        text("TPE2", e.albumArtist)?.let { frames.add(it) }
        text("TCON", e.genre)?.let { frames.add(it) }
        if (e.year > 0) text("TYER", e.year.toString())?.let { frames.add(it) }
        if (e.track > 0) text("TRCK", e.track.toString())?.let { frames.add(it) }
        text("TCOM", e.composer)?.let { frames.add(it) }
        if (e.lyrics.isNotBlank()) {
            val d = ByteArrayOutputStream()
            d.write(1)
            d.write("eng".toByteArray(Charsets.ISO_8859_1))
            d.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0))
            d.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
            d.write(e.lyrics.toByteArray(Charsets.UTF_16LE))
            frames.add(frame("USLT", d.toByteArray()))
        }
        if (art != null) {
            val d = ByteArrayOutputStream()
            d.write(0)
            d.write("image/jpeg".toByteArray(Charsets.ISO_8859_1))
            d.write(0)
            d.write(3)
            d.write(0)
            d.write(art)
            frames.add(frame("APIC", d.toByteArray()))
        }
        frames.addAll(kept)

        val body = ByteArrayOutputStream()
        frames.forEach { body.write(it) }
        val bodyBytes = body.toByteArray()
        val hdr = ByteArrayOutputStream()
        hdr.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0))
        hdr.write(synchsafeBytes(bodyBytes.size))

        val os = cr.openOutputStream(orig.uri, "wt") ?: throw IllegalStateException("Cannot write file")
        os.use { out ->
            out.write(hdr.toByteArray())
            out.write(bodyBytes)
            tmp.inputStream().use { it.copyTo(out) }
        }
        tmp.delete()
    }

    private fun parseFrames(body: ByteArray, ver: Int, kept: MutableList<ByteArray>, keepApic: Boolean) {
        var pos = 0
        while (pos + 10 <= body.size) {
            if (body[pos].toInt() == 0) break
            val id = String(body, pos, 4, Charsets.ISO_8859_1)
            val size = if (ver == 4) synchsafe(body, pos + 4) else be32(body, pos + 4)
            if (size <= 0 || pos + 10 + size > body.size) break
            val flags1 = body[pos + 9].toInt()
            val data = body.copyOfRange(pos + 10, pos + 10 + size)
            pos += 10 + size
            if (id == "APIC") {
                if (!keepApic) continue
                if (ver == 4 && data.isNotEmpty() && data[0].toInt() == 3) data[0] = 0
                kept.add(frame(id, data))
                continue
            }
            if (ver != 3) continue
            if (id in REPLACED) continue
            if ((flags1 and 0xE0) != 0) continue
            kept.add(frame(id, data))
        }
    }

    private fun text(id: String, value: String): ByteArray? {
        if (value.isBlank()) return null
        val d = ByteArrayOutputStream()
        d.write(1)
        d.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        d.write(value.toByteArray(Charsets.UTF_16LE))
        return frame(id, d.toByteArray())
    }

    private fun frame(id: String, data: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(id.toByteArray(Charsets.ISO_8859_1))
        o.write(byteArrayOf((data.size ushr 24).toByte(), (data.size ushr 16).toByte(), (data.size ushr 8).toByte(), data.size.toByte()))
        o.write(byteArrayOf(0, 0))
        o.write(data)
        return o.toByteArray()
    }

    private fun be32(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun synchsafe(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or
            ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun synchsafeBytes(n: Int) = byteArrayOf(
        ((n shr 21) and 0x7F).toByte(), ((n shr 14) and 0x7F).toByte(),
        ((n shr 7) and 0x7F).toByte(), (n and 0x7F).toByte()
    )

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r < 0) break
            off += r
        }
        return off
    }
}
