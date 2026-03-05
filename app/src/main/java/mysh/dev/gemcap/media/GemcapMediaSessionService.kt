package mysh.dev.gemcap.media

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Placeholder service for future background audio support.
 * Currently, playback is managed by GemcapPlayerManager with a local MediaSession.
 * Full background audio (playing with app closed) requires passing media data
 * to the service, which will be implemented in a future iteration.
 */
class GemcapMediaSessionService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    internal fun setSession(session: MediaSession) {
        mediaSession = session
    }
}
