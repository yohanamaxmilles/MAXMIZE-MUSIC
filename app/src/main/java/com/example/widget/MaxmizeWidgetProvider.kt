package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.player.MaxmizeMediaLibraryService

class MaxmizeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, null) ?: context.getString(R.string.widget_no_track)
        val artist = prefs.getString(KEY_ARTIST, null) ?: context.getString(R.string.widget_tap_to_open)
        val isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false)
        val artUriStr = prefs.getString(KEY_ART_URI, null)

        val artBitmap = artUriStr?.let { loadScaledArt(context, it) }

        for (widgetId in appWidgetIds) {
            val views = buildRemoteViews(context, title, artist, isPlaying, artBitmap)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    companion object {
        private const val PREFS_NAME = "maxmize_widget_state"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_IS_PLAYING = "is_playing"
        private const val KEY_ART_URI = "art_uri"

        fun updateWidgetState(
            context: Context,
            title: String?,
            artist: String?,
            isPlaying: Boolean,
            artworkUri: String?,
        ) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_TITLE, title)
                .putString(KEY_ARTIST, artist)
                .putBoolean(KEY_IS_PLAYING, isPlaying)
                .putString(KEY_ART_URI, artworkUri)
                .apply()

            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MaxmizeWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val displayTitle = title ?: context.getString(R.string.widget_no_track)
            val displayArtist = artist ?: context.getString(R.string.widget_tap_to_open)
            val bitmap = artworkUri?.let { loadScaledArt(context, it) }

            for (id in ids) {
                manager.updateAppWidget(id, buildRemoteViews(context, displayTitle, displayArtist, isPlaying, bitmap))
            }
        }

        private fun buildRemoteViews(
            context: Context,
            title: String,
            artist: String,
            isPlaying: Boolean,
            artBitmap: Bitmap?,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_maxmize_player)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            )
            if (artBitmap != null) {
                views.setImageViewBitmap(R.id.widget_artwork, artBitmap)
            } else {
                views.setImageViewResource(R.id.widget_artwork, android.R.drawable.ic_media_play)
            }

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val openPending = PendingIntent.getActivity(
                context,
                100,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPending)
            views.setOnClickPendingIntent(
                R.id.widget_play_pause,
                servicePendingIntent(context, MaxmizeMediaLibraryService.ACTION_WIDGET_PLAY_PAUSE, 101),
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                servicePendingIntent(context, MaxmizeMediaLibraryService.ACTION_WIDGET_NEXT, 102),
            )
            views.setOnClickPendingIntent(
                R.id.widget_prev,
                servicePendingIntent(context, MaxmizeMediaLibraryService.ACTION_WIDGET_PREV, 103),
            )
            return views
        }

        private fun servicePendingIntent(context: Context, action: String, reqCode: Int): PendingIntent {
            val intent = Intent(context, MaxmizeMediaLibraryService::class.java).apply {
                this.action = action
            }
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    reqCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                PendingIntent.getService(
                    context,
                    reqCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
        }

        private fun loadScaledArt(context: Context, uriStr: String): Bitmap? {
            return runCatching {
                val uri = Uri.parse(uriStr)
                val streamBitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeStream(input, null, opts)
                }
                if (streamBitmap != null) return streamBitmap

                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val embedded = retriever.embeddedPicture ?: return null
                    BitmapFactory.decodeByteArray(embedded, 0, embedded.size)
                } finally {
                    runCatching { retriever.release() }
                }
            }.getOrNull()
        }
    }
}
