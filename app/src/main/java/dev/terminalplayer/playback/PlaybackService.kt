package dev.terminalplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.terminalplayer.MainActivity
import dev.terminalplayer.R

/**
 * Foreground playback host.
 *
 * Holds the only ExoPlayer instance of the app, wraps it into a [MediaSession] (lock screen,
 * notification, Bluetooth/headset buttons, Android Auto style media controls) and publishes the
 * audio session id for the ASCII visualizer.
 *
 * An explicit audio session id is generated up front so the visualizer can attach even before the
 * first track starts.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // getSystemService() is annotated @Nullable in the platform stubs, so the result is only
        // usable behind a safe call. generateAudioSessionId() itself returns ERROR (-1) on failure.
        val audioManager = getSystemService(AudioManager::class.java)
        val generatedSessionId = runCatching { audioManager?.generateAudioSessionId() }
            .getOrNull()
            ?.takeIf { it > 0 }
            ?: C.AUDIO_SESSION_ID_UNSET

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        if (generatedSessionId != C.AUDIO_SESSION_ID_UNSET) {
            exoPlayer.setAudioSessionId(generatedSessionId)
        }
        player = exoPlayer

        // Safety net: if generating an id failed, ExoPlayer creates one lazily when the audio sink
        // initialises. Publish it as soon as playback starts so the visualizer can still attach.
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!isPlaying) return
                    val sessionId = exoPlayer.audioSessionId
                    if (sessionId > 0) AudioSessionBus.publish(sessionId)
                }
            },
        )

        val sessionActivity = PendingIntent.getActivity(
            this,
            REQUEST_CODE_SESSION,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivity)
            .build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.notification_channel_name)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_stat_terminal)
        setMediaNotificationProvider(notificationProvider)

        val publishedSessionId = if (generatedSessionId != C.AUDIO_SESSION_ID_UNSET) {
            generatedSessionId
        } else {
            exoPlayer.audioSessionId
        }
        AudioSessionBus.publish(publishedSessionId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = player
        if (currentPlayer == null || !currentPlayer.playWhenReady || currentPlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        AudioSessionBus.publish(C.AUDIO_SESSION_ID_UNSET)
        super.onDestroy()
    }

    private companion object {
        const val NOTIFICATION_CHANNEL_ID = "terminal_player_playback"
        const val REQUEST_CODE_SESSION = 1001
    }
}
