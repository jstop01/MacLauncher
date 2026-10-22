package com.jex.maclauncher.widget.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationManagerCompat
import com.jex.maclauncher.R
import com.jex.maclauncher.widget.WidgetSize
import com.jex.maclauncher.widget.WidgetTheme

/** macOS '지금 재생 중' 위젯: 재생 중인 앱의 앨범 아트·제목·가수와 ⏮ ⏯ ⏭ */
class MusicWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAll(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) =
        updateAll(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val controls = current(context)?.transportControls ?: return
        when (intent.action) {
            ACTION_PLAY_PAUSE -> if (isPlaying(current(context))) controls.pause() else controls.play()
            ACTION_NEXT -> controls.skipToNext()
            ACTION_PREV -> controls.skipToPrevious()
        }
    }

    companion object {
        private const val ACTION_PLAY_PAUSE = "com.jex.maclauncher.music.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.jex.maclauncher.music.NEXT"
        private const val ACTION_PREV = "com.jex.maclauncher.music.PREV"
        private const val ART_PX = 320   // 위젯 갱신 크기 제한 안에서 충분히 선명하게

        private fun hasAccess(context: Context) =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        /** 재생 중인 세션 우선, 없으면 가장 최근 세션 */
        private fun current(context: Context): MediaController? {
            if (!hasAccess(context)) return null
            val manager = context.getSystemService(MediaSessionManager::class.java)
            val controllers = runCatching {
                manager.getActiveSessions(ComponentName(context, MusicListenerService::class.java))
            }.getOrNull().orEmpty()
            return controllers.firstOrNull(::isPlaying) ?: controllers.firstOrNull()
        }

        private fun isPlaying(c: MediaController?) = c?.playbackState?.state == PlaybackState.STATE_PLAYING

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MusicWidget::class.java))
            if (ids.isEmpty()) return
            val controller = current(context)
            ids.forEach { id -> manager.updateAppWidget(id, views(context, WidgetSize.of(manager, id), controller)) }
        }

        private fun views(context: Context, size: WidgetSize, c: MediaController?): RemoteViews {
            val small = size.narrowerThan(250)
            val views = RemoteViews(context.packageName, WidgetTheme.layout(context, if (small) R.layout.widget_music_small else R.layout.widget_music))
            val meta = c?.metadata
            val playing = isPlaying(c)

            val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            when {
                !hasAccess(context) -> {
                    views.setTextViewText(R.id.music_title, "지금 재생 중")
                    views.setTextViewText(R.id.music_artist, "탭해서 '알림 접근' 허용")
                }
                title == null -> {
                    views.setTextViewText(R.id.music_title, "재생 중인 음악 없음")
                    views.setTextViewText(R.id.music_artist, "음악 앱에서 재생하면 여기에 나와요")
                }
                else -> {
                    views.setTextViewText(R.id.music_title, title)
                    views.setTextViewText(R.id.music_artist, artist.orEmpty())
                }
            }

            val art = meta?.let(::artwork)
            if (art != null) views.setImageViewBitmap(R.id.music_art, art) else views.setImageViewResource(R.id.music_art, R.drawable.ic_music_note)

            val showButtons = c != null
            intArrayOf(R.id.music_prev, R.id.music_play, R.id.music_next).forEach {
                views.setViewVisibility(it, if (showButtons) View.VISIBLE else View.INVISIBLE)
            }
            views.setImageViewResource(
                R.id.music_play,
                WidgetTheme.drawable(context, if (playing) R.drawable.ic_media_pause else R.drawable.ic_media_play),
            )
            views.setOnClickPendingIntent(R.id.music_play, broadcast(context, ACTION_PLAY_PAUSE, 0))
            views.setOnClickPendingIntent(R.id.music_next, broadcast(context, ACTION_NEXT, 1))
            views.setOnClickPendingIntent(R.id.music_prev, broadcast(context, ACTION_PREV, 2))

            // 아트/글자 탭: 권한 없으면 알림 접근 설정, 있으면 재생 중인 앱 (없으면 기본 음악 앱)
            val open = when {
                !hasAccess(context) -> PendingIntent.getActivity(context, 3, accessSettings(context), PendingIntent.FLAG_IMMUTABLE)
                c?.sessionActivity != null -> c.sessionActivity
                c != null -> context.packageManager.getLaunchIntentForPackage(c.packageName)
                    ?.let { PendingIntent.getActivity(context, 4, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
                else -> PendingIntent.getActivity(
                    context, 5, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            open?.let {
                views.setOnClickPendingIntent(R.id.music_art, it)
                views.setOnClickPendingIntent(R.id.music_info, it)
            }
            return views
        }

        /** 앨범 아트를 위젯에 넣기 알맞은 크기로 */
        private fun artwork(meta: MediaMetadata): Bitmap? {
            val bmp = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                ?: return null
            val scale = ART_PX.toFloat() / maxOf(bmp.width, bmp.height)
            return if (scale >= 1f) bmp
            else Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
        }

        /** 이 앱의 알림 접근 설정 화면 (Android 11+는 바로 우리 항목으로) */
        private fun accessSettings(context: Context): Intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, MusicListenerService::class.java).flattenToString())
            } else {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }

        private fun broadcast(context: Context, action: String, slot: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, slot, Intent(context, MusicWidget::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
