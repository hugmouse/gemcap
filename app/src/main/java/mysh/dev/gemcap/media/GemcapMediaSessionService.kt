package mysh.dev.gemcap.media

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Wrapper service that hosts the [MediaSession] for system notification
 * controls. The session and player lifecycle are owned by [GemcapPlayerManager];
 * this service only holds a reference so [onGetSession] can return it.
 *
 * Call [publishSession] from [GemcapPlayerManager] when the session is created
 * or released. If the service hasn't started yet the session is kept as pending
 * and picked up in [onCreate].
 */
class GemcapMediaSessionService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        pendingSession?.let {
            mediaSession = it
            addSession(it)
            pendingSession = null
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession = null
        instance = null
        super.onDestroy()
    }

    companion object {
        private var instance: GemcapMediaSessionService? = null
        private var pendingSession: MediaSession? = null

        /**
         * Publishes or clears the [MediaSession] so that [onGetSession] can
         * return it. Called from [GemcapPlayerManager] when the session is
         * created or released.
         */
        internal fun publishSession(session: MediaSession?) {
            pendingSession = session
            instance?.let { service ->
                service.mediaSession?.let { service.removeSession(it) }
                service.mediaSession = session
                session?.let { service.addSession(it) }
            }
        }
    }
}
