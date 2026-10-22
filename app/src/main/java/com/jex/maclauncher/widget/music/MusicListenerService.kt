package com.jex.maclauncher.widget.music

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService

/**
 * '알림 접근'을 켜면 시스템이 붙여 두는 서비스. 알림 내용은 쓰지 않고,
 * 다른 앱의 재생 정보(MediaSession)를 읽을 자격을 얻어 곡/재생 상태가 바뀔 때마다 위젯을 다시 그린다.
 */
class MusicListenerService : NotificationListenerService() {

    private var sessions: MediaSessionManager? = null
    private val watched = mutableListOf<MediaController>()

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = MusicWidget.updateAll(this@MusicListenerService)
        override fun onPlaybackStateChanged(state: PlaybackState?) = MusicWidget.updateAll(this@MusicListenerService)
        override fun onSessionDestroyed() = MusicWidget.updateAll(this@MusicListenerService)
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        watch(controllers.orEmpty())
        MusicWidget.updateAll(this)
    }

    override fun onListenerConnected() {
        val manager = getSystemService(MediaSessionManager::class.java)
        sessions = manager
        val me = ComponentName(this, MusicListenerService::class.java)
        manager.addOnActiveSessionsChangedListener(sessionsChanged, me)
        watch(manager.getActiveSessions(me))
        MusicWidget.updateAll(this)
    }

    override fun onListenerDisconnected() {
        sessions?.removeOnActiveSessionsChangedListener(sessionsChanged)
        watch(emptyList())
        MusicWidget.updateAll(this)
    }

    private fun watch(controllers: List<MediaController>) {
        watched.forEach { it.unregisterCallback(controllerCallback) }
        watched.clear()
        controllers.forEach { it.registerCallback(controllerCallback); watched += it }
    }
}
