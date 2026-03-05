package mysh.dev.gemcap.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession

class GemcapPlayerManager(private val context: Context) {

    var player: ExoPlayer? = null
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

    @OptIn(UnstableApi::class)
    fun play(data: ByteArray, mimeType: String) {
        val exoPlayer = getOrCreatePlayer()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        val dataSourceFactory = ByteArrayDataSource.Factory(data, mimeType)
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

    fun release() {
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
    }

    fun isPlaying(): Boolean = player?.isPlaying == true
}
