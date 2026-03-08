package mysh.dev.gemcap.media

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession

@Stable
class GemcapPlayerManager(private val context: Context) {

    var player: ExoPlayer? by mutableStateOf(null)
        private set

    var currentItemId: Int? by mutableStateOf(null)
        private set

    private var mediaSession: MediaSession? = null

    private fun getOrCreatePlayer(): ExoPlayer {
        player?.let { return it }
        val newPlayer = ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus= */ true
            )
        }
        player = newPlayer
        mediaSession = MediaSession.Builder(context, newPlayer).build()
        return newPlayer
    }

    /** Must be called on the main thread. */
    @OptIn(UnstableApi::class)
    fun play(data: ByteArray, mimeType: String, itemId: Int? = null) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "play() must be called on the main thread" }
        val exoPlayer = getOrCreatePlayer()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        currentItemId = itemId
        val dataSourceFactory = ByteArrayDataSource.Factory(data)
        val mediaItem = MediaItem.Builder()
            .setUri("gemini://local/media")
            .setMimeType(mimeType)
            .build()
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    /** Must be called on the main thread. */
    @OptIn(UnstableApi::class)
    fun playFromFile(file: java.io.File, mimeType: String, itemId: Int? = null): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper()) { "playFromFile() must be called on the main thread" }
        if (!file.exists() || !file.canRead()) {
            Log.e(TAG, "Cannot play file: ${file.absolutePath} (exists=${file.exists()}, canRead=${file.canRead()})")
            return false
        }
        val exoPlayer = getOrCreatePlayer()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        currentItemId = itemId
        val mediaItem = MediaItem.Builder()
            .setUri(android.net.Uri.fromFile(file))
            .setMimeType(mimeType)
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        return true
    }

    fun release() {
        currentItemId = null
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
    }

    fun isPlaying(): Boolean = player?.isPlaying == true

    companion object {
        private const val TAG = "GemcapPlayerManager"
    }
}
