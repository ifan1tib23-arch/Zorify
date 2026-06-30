package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ZorifyPlaybackService : Service() {

    private val tag = "ZorifyPlaybackService"
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    companion object {
        const val NOTIFICATION_ID = 1111
        const val CHANNEL_ID = "zorify_playback_channel"

        const val ACTION_START_OR_UPDATE = "com.example.zorify.ACTION_START_OR_UPDATE"
        const val ACTION_STOP = "com.example.zorify.ACTION_STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_COVER_URL = "extra_cover_url"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_IS_LIKED = "extra_is_liked"
        const val EXTRA_IS_REPEAT = "extra_is_repeat"
        const val EXTRA_IS_SHUFFLE = "extra_is_shuffle"

        @Volatile
        var mediaSessionToken: android.media.session.MediaSession.Token? = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START_OR_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Zorify"
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: ""
                val coverUrl = intent.getStringExtra(EXTRA_COVER_URL) ?: ""
                val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                val isLiked = intent.getBooleanExtra(EXTRA_IS_LIKED, false)
                val isRepeat = intent.getBooleanExtra(EXTRA_IS_REPEAT, false)
                val isShuffle = intent.getBooleanExtra(EXTRA_IS_SHUFFLE, false)

                handleStartOrUpdate(title, artist, coverUrl, isPlaying, isLiked, isRepeat, isShuffle)
            }
            ACTION_STOP -> {
                handleStop()
            }
        }

        return START_STICKY
    }

    private fun handleStartOrUpdate(
        title: String,
        artist: String,
        coverUrl: String,
        isPlaying: Boolean,
        isLiked: Boolean,
        isRepeat: Boolean,
        isShuffle: Boolean
    ) {
        // Build initial notification to start foreground immediately
        val initialNotification = buildNotification(title, artist, null, isPlaying, isLiked, isRepeat, isShuffle)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, 
                    initialNotification, 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to startForeground: ${e.message}")
        }

        // Load the cover image asynchronously
        if (coverUrl.isNotEmpty()) {
            serviceScope.launch {
                val bitmap = loadCoverBitmap(coverUrl)
                if (bitmap != null) {
                    val updatedNotification = buildNotification(title, artist, bitmap, isPlaying, isLiked, isRepeat, isShuffle)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, updatedNotification)
                }
            }
        }
    }

    private fun handleStop() {
        Log.d(tag, "Stopping Foreground Service")
        stopForeground(true)
        stopSelf()
    }

    private suspend fun loadCoverBitmap(url: String): Bitmap? {
        return try {
            val loader = ImageLoader(this)
            val request = ImageRequest.Builder(this)
                .data(url)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                (result.drawable as? BitmapDrawable)?.bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Error loading notification image: ${e.message}")
            null
        }
    }

    private fun buildNotification(
        title: String,
        artist: String,
        bitmap: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        isRepeat: Boolean,
        isShuffle: Boolean
    ): Notification {
        // Initialize notification channel for Android Oreo (API 26) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Zorify Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menampilkan kontrol musik yang sedang diputar"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        // Play/Pause Action Intent
        val playPauseIntent = Intent("com.example.zorify.ACTION_PLAY_PAUSE").apply {
            `package` = packageName
        }
        val playPausePending = PendingIntent.getBroadcast(
            this, 0, playPauseIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // Next Action Intent
        val nextIntent = Intent("com.example.zorify.ACTION_NEXT").apply {
            `package` = packageName
        }
        val nextPending = PendingIntent.getBroadcast(
            this, 1, nextIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // Shuffle Action Intent
        val shuffleIntent = Intent("com.example.zorify.ACTION_SHUFFLE").apply {
            `package` = packageName
        }
        val shufflePending = PendingIntent.getBroadcast(
            this, 7, shuffleIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // Like Action Intent
        val likeIntent = Intent("com.example.zorify.ACTION_LIKE").apply {
            `package` = packageName
        }
        val likePending = PendingIntent.getBroadcast(
            this, 5, likeIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // Repeat Action Intent
        val repeatIntent = Intent("com.example.zorify.ACTION_REPEAT").apply {
            `package` = packageName
        }
        val repeatPending = PendingIntent.getBroadcast(
            this, 6, repeatIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // Click Intent to open MainActivity
        val clickIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val clickPending = PendingIntent.getActivity(
            this, 3, clickIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // Create system notification using standard platform Notification.Builder with MediaStyle
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder.setSmallIcon(R.drawable.ic_zorify_small_logo)
            .setContentTitle(title)
            .setContentText(artist)
            .setOngoing(isPlaying)
            .setContentIntent(clickPending)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
        }

        // Action 0: Shuffle Toggle
        builder.addAction(
            Notification.Action.Builder(
                if (isShuffle) R.drawable.ic_shuffle_active else R.drawable.ic_shuffle,
                if (isShuffle) "Acak Aktif" else "Acak Mati",
                shufflePending
            ).build()
        )

        // Action 1: Repeat Single Track Toggle
        builder.addAction(
            Notification.Action.Builder(
                if (isRepeat) R.drawable.ic_repeat_active else R.drawable.ic_repeat,
                if (isRepeat) "Ulangi Hidup" else "Ulangi Mati",
                repeatPending
            ).build()
        )

        // Action 2: Play/Pause Playback Toggle
        builder.addAction(
            Notification.Action.Builder(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Putar",
                playPausePending
            ).build()
        )

        // Action 3: Skip to Next Track
        builder.addAction(
            Notification.Action.Builder(
                android.R.drawable.ic_media_next, "Berikutnya", nextPending
            ).build()
        )

        // Action 4: Like/Favorite Toggle State
        builder.addAction(
            Notification.Action.Builder(
                if (isLiked) R.drawable.ic_like_filled else R.drawable.ic_like_outline,
                if (isLiked) "Disukai" else "Sukai",
                likePending
            ).build()
        )

        // Apply platform MediaStyle with session token
        val mediaStyle = Notification.MediaStyle()
            .setShowActionsInCompactView(1, 2, 3) // Compact view shows: Like, Play/Pause, Next
        mediaSessionToken?.let { token ->
            mediaStyle.setMediaSession(token)
        }

        builder.setStyle(mediaStyle)

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.d(tag, "Service Destroyed")
    }
}
