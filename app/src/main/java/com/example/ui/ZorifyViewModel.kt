package com.example.ui

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ZorifyPlaybackService
import com.example.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.net.HttpURLConnection
import org.json.JSONObject
import org.json.JSONArray

data class LyricLine(val timestampMs: Long, val text: String)

enum class PlaybackMode {
    SEQUENTIAL, SHUFFLE
}

enum class Screen {
    HOME, SEARCH, COLLECTION, PLAYLIST_DETAIL, LIKED_SONGS, PROFILE, HISTORY, ADMIN, AUTH
}

class ZorifyViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "ZorifyViewModel"
    private val context = application.applicationContext
    
    private val db = ZorifyDatabase.getDatabase(context)
    val repository = ZorifyRepository(context, db.zorifyDao())

    private var mediaSession: android.media.session.MediaSession? = null

    // --- NAVIGATION STATE ---
    private val _currentScreen = MutableStateFlow(Screen.AUTH)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    // --- OFFLINE STATE ---
    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    // --- SYNC STATE ---
    private val _syncServerUrl = MutableStateFlow("https://ais-dev-63htz2l2knf47xa34vzhv2-1067765608864.asia-southeast1.run.app")
    val syncServerUrl: StateFlow<String> = _syncServerUrl.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncedTime = MutableStateFlow("Belum pernah disinkronkan")
    val lastSyncedTime: StateFlow<String> = _lastSyncedTime.asStateFlow()

    // --- AUTH & USER STATE ---
    val activeUser: StateFlow<User?> = repository.getActiveUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- SONGS & SEARCH ---
    val allSongs: StateFlow<List<Song>> = repository.getAllSongsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<Song>> = combine(allSongs, _searchQuery) { songs, query ->
        if (query.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- USER DATA (PLAYLISTS, FAVORITES, HISTORY) ---
    val userPlaylists: StateFlow<List<Playlist>> = activeUser.flatMapLatest { user ->
        if (user != null) repository.getPlaylistsFlow(user.id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userLikedSongs: StateFlow<List<Song>> = activeUser.flatMapLatest { user ->
        if (user != null) {
            repository.getLikedSongsFlow(user.id).flatMapLatest { likedList ->
                allSongs.map { songs ->
                    val likedIds = likedList.map { it.songId }.toSet()
                    songs.filter { it.id in likedIds }
                }
            }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePlaylistSongs: StateFlow<List<Song>> = _selectedPlaylistId.flatMapLatest { playlistId ->
        if (playlistId != null) repository.getSongsInPlaylistFlow(playlistId) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playHistory: StateFlow<List<Song>> = activeUser.flatMapLatest { user ->
        if (user != null) repository.getPlayHistoryFlow(user.id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- DOWNLOAD QUEUE STATES ---
    private val _downloadProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<Long, Float>> = _downloadProgress.asStateFlow()

    // --- MEDIA PLAYER STATE ---
    private var mediaPlayer: MediaPlayer? = null
    private var playRetryCount = 0
    private val maxPlayRetries = 5
    private var pendingSeekPosition: Int? = null

    private val wifiLock by lazy {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ZorifyWifiLock")
    }

    private fun acquireWifiLock() {
        try {
            if (!wifiLock.isHeld) {
                wifiLock.acquire()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to acquire WifiLock: ${e.message}")
        }
    }

    private fun releaseWifiLock() {
        try {
            if (wifiLock.isHeld) {
                wifiLock.release()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to release WifiLock: ${e.message}")
        }
    }

    private val wakeLock by lazy {
        val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Zorify::PlaybackWakeLock")
    }

    private fun acquireWakeLock(timeoutMs: Long = 15000) {
        try {
            if (!wakeLock.isHeld) {
                wakeLock.acquire(timeoutMs)
                Log.d(tag, "WakeLock acquired for $timeoutMs ms")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock.isHeld) {
                wakeLock.release()
                Log.d(tag, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to release WakeLock: ${e.message}")
        }
    }

    // Spotify Toast Alert states
    private val _spotifyToastMessage = MutableStateFlow<String?>(null)
    val spotifyToastMessage: StateFlow<String?> = _spotifyToastMessage.asStateFlow()

    private val _showPlaylistSelector = MutableStateFlow<Song?>(null)
    val showPlaylistSelector: StateFlow<Song?> = _showPlaylistSelector.asStateFlow()

    fun showSpotifyToast(message: String) {
        _spotifyToastMessage.value = message
        viewModelScope.launch {
            delay(3000)
            if (_spotifyToastMessage.value == message) {
                _spotifyToastMessage.value = null
            }
        }
    }

    fun dismissSpotifyToast() {
        _spotifyToastMessage.value = null
    }

    fun openPlaylistSelector(song: Song) {
        _showPlaylistSelector.value = song
    }

    fun closePlaylistSelector() {
        _showPlaylistSelector.value = null
    }

    suspend fun getPlaylistIdsForSong(songId: Long): List<Long> {
        return repository.getPlaylistIdsForSong(songId)
    }

    fun getPlaylistIdsForSongFlow(songId: Long): Flow<List<Long>> {
        return repository.getPlaylistIdsForSongFlow(songId)
    }
    
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private val _playbackMode = MutableStateFlow(PlaybackMode.SEQUENTIAL)
    val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    private val _isRepeatOne = MutableStateFlow(false)
    val isRepeatOne: StateFlow<Boolean> = _isRepeatOne.asStateFlow()

    private val _isCircleCoverMode = MutableStateFlow(false)
    val isCircleCoverMode: StateFlow<Boolean> = _isCircleCoverMode.asStateFlow()

    // --- LYRICS ---
    private val _lyricLines = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyricLines: StateFlow<List<LyricLine>> = _lyricLines.asStateFlow()

    private val _currentLyricIndex = MutableStateFlow(-1)
    val currentLyricIndex: StateFlow<Int> = _currentLyricIndex.asStateFlow()

    private val _lyricsEnabled = MutableStateFlow(true)
    val lyricsEnabled: StateFlow<Boolean> = _lyricsEnabled.asStateFlow()

    // --- SLEEP TIMER ---
    private val _sleepTimerSecondsLeft = MutableStateFlow<Int?>(null)
    val sleepTimerSecondsLeft: StateFlow<Int?> = _sleepTimerSecondsLeft.asStateFlow()
    private var sleepTimerJob: Job? = null

    // --- PLAYBACK QUEUE FLOW ---
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    fun toggleLyricsEnabled() {
        _lyricsEnabled.value = !_lyricsEnabled.value
        Toast.makeText(context, if (_lyricsEnabled.value) "Lirik Real-Time Diaktifkan" else "Lirik Real-Time Dimatikan", Toast.LENGTH_SHORT).show()
    }

    // --- SPOTIFY MEDIA ALERT POPUP ---
    private val _showMediaAlert = MutableStateFlow(false)
    val showMediaAlert: StateFlow<Boolean> = _showMediaAlert.asStateFlow()

    private var alertDismissJob: Job? = null

    fun triggerMediaAlert(show: Boolean) {
        _showMediaAlert.value = show
        if (show) {
            showPlaybackNotification()
            alertDismissJob?.cancel()
            alertDismissJob = viewModelScope.launch {
                delay(6000)
                _showMediaAlert.value = false
            }
        }
    }

    private fun updateMediaSessionState() {
        val playing = _isPlaying.value
        val position = _currentPosition.value
        val isShuffle = _playbackMode.value == PlaybackMode.SHUFFLE
        val song = _currentSong.value
        val isLiked = song?.let { s -> userLikedSongs.value.any { it.id == s.id } } ?: false
        val isRepeat = _isRepeatOne.value

        try {
            val stateBuilder = android.media.session.PlaybackState.Builder()
                .setActions(
                    android.media.session.PlaybackState.ACTION_PLAY or
                    android.media.session.PlaybackState.ACTION_PAUSE or
                    android.media.session.PlaybackState.ACTION_PLAY_PAUSE or
                    android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT or
                    android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    android.media.session.PlaybackState.ACTION_SEEK_TO
                )
                .setState(
                    if (playing) android.media.session.PlaybackState.STATE_PLAYING else android.media.session.PlaybackState.STATE_PAUSED,
                    position.toLong(),
                    1.0f
                )

            // Add Custom Actions for lockscreen & quick settings media player
            stateBuilder.addCustomAction(
                android.media.session.PlaybackState.CustomAction.Builder(
                    "com.example.zorify.ACTION_SHUFFLE",
                    if (isShuffle) "Acak Aktif" else "Acak Mati",
                    if (isShuffle) R.drawable.ic_shuffle_active else R.drawable.ic_shuffle
                ).build()
            )
            stateBuilder.addCustomAction(
                android.media.session.PlaybackState.CustomAction.Builder(
                    "com.example.zorify.ACTION_REPEAT",
                    if (isRepeat) "Ulangi Aktif" else "Ulangi Mati",
                    if (isRepeat) R.drawable.ic_repeat_active else R.drawable.ic_repeat
                ).build()
            )
            stateBuilder.addCustomAction(
                android.media.session.PlaybackState.CustomAction.Builder(
                    "com.example.zorify.ACTION_LIKE",
                    if (isLiked) "Disukai" else "Sukai",
                    if (isLiked) R.drawable.ic_like_filled else R.drawable.ic_like_outline
                ).build()
            )

            mediaSession?.setPlaybackState(stateBuilder.build())
        } catch (e: Exception) {
            Log.e(tag, "Failed to set MediaSession playback state: ${e.message}")
        }
    }

    private fun updateMediaSessionMetadata(song: Song, bitmap: android.graphics.Bitmap? = null) {
        try {
            val metadataBuilder = android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, song.title)
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, song.artist)
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, _duration.value.toLong())

            if (bitmap != null) {
                metadataBuilder.putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
            }
            mediaSession?.setMetadata(metadataBuilder.build())
        } catch (e: Exception) {
            Log.e(tag, "Failed to set MediaSession metadata: ${e.message}")
        }
    }

    fun showPlaybackNotification() {
        val song = _currentSong.value ?: return
        val playing = _isPlaying.value
        val isLiked = userLikedSongs.value.any { it.id == song.id }
        val isRepeat = _isRepeatOne.value
        val isShuffle = _playbackMode.value == PlaybackMode.SHUFFLE

        try {
            val serviceIntent = android.content.Intent(context, ZorifyPlaybackService::class.java).apply {
                action = ZorifyPlaybackService.ACTION_START_OR_UPDATE
                putExtra(ZorifyPlaybackService.EXTRA_TITLE, song.title)
                putExtra(ZorifyPlaybackService.EXTRA_ARTIST, song.artist)
                putExtra(ZorifyPlaybackService.EXTRA_COVER_URL, song.coverUrl)
                putExtra(ZorifyPlaybackService.EXTRA_IS_PLAYING, playing)
                putExtra(ZorifyPlaybackService.EXTRA_IS_LIKED, isLiked)
                putExtra(ZorifyPlaybackService.EXTRA_IS_REPEAT, isRepeat)
                putExtra(ZorifyPlaybackService.EXTRA_IS_SHUFFLE, isShuffle)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to start/update foreground playback service: ${e.message}")
        }

        // Keep updating MediaSession state so lockscreen controls can listen to it
        viewModelScope.launch {
            var bitmap: android.graphics.Bitmap? = null
            try {
                val loader = coil.ImageLoader(context)
                val request = coil.request.ImageRequest.Builder(context)
                    .data(song.coverUrl)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is coil.request.SuccessResult) {
                    bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to load album art asynchronously for MediaSession: ${e.message}")
            }
            updateMediaSessionMetadata(song, bitmap)
            updateMediaSessionState()
        }
    }

    fun stopPlaybackService() {
        try {
            val serviceIntent = android.content.Intent(context, ZorifyPlaybackService::class.java).apply {
                action = ZorifyPlaybackService.ACTION_STOP
            }
            context.stopService(serviceIntent)
        } catch (e: Exception) {
            Log.e(tag, "Failed to stop playback service: ${e.message}")
        }
    }

    fun dismissMediaAlert() {
        _showMediaAlert.value = false
        alertDismissJob?.cancel()
    }

    private fun saveLastPlayedSongId(songId: Long) {
        val prefs = context.getSharedPreferences("zorify_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_played_song_id", songId).apply()
    }

    private fun getLastPlayedSongId(): Long {
        val prefs = context.getSharedPreferences("zorify_prefs", Context.MODE_PRIVATE)
        return prefs.getLong("last_played_song_id", -1L)
    }

    // --- ACTIVE PLAYBACK QUEUE ---
    private var playbackQueue: List<Song> = emptyList()

    private var progressTickerJob: Job? = null

    init {
        try {
            val prefs = context.getSharedPreferences("zorify_prefs", Context.MODE_PRIVATE)
            _syncServerUrl.value = prefs.getString("sync_server_url", "https://ais-dev-63htz2l2knf47xa34vzhv2-1067765608864.asia-southeast1.run.app") ?: "https://ais-dev-63htz2l2knf47xa34vzhv2-1067765608864.asia-southeast1.run.app"
            _lastSyncedTime.value = prefs.getString("last_synced_time", "Belum pernah disinkronkan") ?: "Belum pernah disinkronkan"

            mediaSession = android.media.session.MediaSession(context, "Zorify").apply {
                isActive = true
                setCallback(object : android.media.session.MediaSession.Callback() {
                    override fun onPlay() {
                        play()
                    }
                    override fun onPause() {
                        pause()
                    }
                    override fun onSkipToNext() {
                        skipNext()
                    }
                    override fun onSkipToPrevious() {
                        skipPrev()
                    }
                    override fun onSeekTo(pos: Long) {
                        seekTo(pos.toInt())
                    }
                    override fun onCustomAction(action: String, extras: android.os.Bundle?) {
                        when (action) {
                            "com.example.zorify.ACTION_LIKE" -> toggleLikeCurrentSong()
                            "com.example.zorify.ACTION_REPEAT" -> toggleRepeatOne()
                            "com.example.zorify.ACTION_SHUFFLE" -> togglePlaybackMode()
                        }
                    }
                })
            }
            ZorifyPlaybackService.mediaSessionToken = mediaSession?.sessionToken
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize MediaSession: ${e.message}")
        }

        viewModelScope.launch {
            repository.initializeDefaultSongs()
            
            // Check if there is an active user logged in
            val user = repository.getActiveUser()
            if (user == null) {
                _currentScreen.value = Screen.AUTH
            } else {
                _currentScreen.value = Screen.HOME
            }

            // Set initial loaded song based on the last played song from persistent storage
            val lastPlayedId = getLastPlayedSongId()
            if (lastPlayedId != -1L) {
                val lastSong = repository.getSongById(lastPlayedId)
                if (lastSong != null) {
                    _currentSong.value = lastSong
                    parseLyricsForCurrentSong(lastSong.lyrics)
                } else {
                    _currentSong.value = null
                }
            } else {
                _currentSong.value = null
            }
        }
    }

    // --- NAVIGATION FUNCTIONS ---
    fun navigateTo(screen: Screen, playlistId: Long? = null) {
        if (activeUser.value == null) {
            _currentScreen.value = Screen.AUTH
            return
        }
        _selectedPlaylistId.value = playlistId
        _currentScreen.value = screen
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // --- OFFLINE MODE CONTROL ---
    fun toggleOfflineMode() {
        _isOfflineMode.value = !_isOfflineMode.value
        if (_isOfflineMode.value) {
            Toast.makeText(context, "Mode Offline Aktif (Hanya lagu terdownload)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Mode Online Aktif", Toast.LENGTH_SHORT).show()
        }
    }

    // --- AUTHENTICATION FLOWS ---
    fun register(username: String, pword: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val ok = repository.registerUser(username, pword)
            if (ok) {
                Toast.makeText(context, "Registrasi berhasil! Silakan masuk.", Toast.LENGTH_LONG).show()
                onSuccess()
            } else {
                Toast.makeText(context, "Registrasi gagal. Username mungkin sudah ada.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun login(username: String, pword: String) {
        viewModelScope.launch {
            val ok = repository.loginUser(username, pword)
            if (ok) {
                Toast.makeText(context, "Selamat datang, $username!", Toast.LENGTH_SHORT).show()
                _currentScreen.value = Screen.HOME
            } else {
                Toast.makeText(context, "Username atau password salah.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun loginWithGoogle(email: String) {
        viewModelScope.launch {
            val ok = repository.loginWithGoogle(email)
            if (ok) {
                Toast.makeText(context, "Berhasil masuk dengan Google: $email", Toast.LENGTH_SHORT).show()
                _currentScreen.value = Screen.HOME
                syncWithCloud()
            } else {
                Toast.makeText(context, "Gagal masuk dengan Google.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            pause()
            
            // Clear current playback state so nothing remains
            _currentSong.value = null
            _isPlaying.value = false
            mediaPlayer?.release()
            mediaPlayer = null
            
            // Cancel system media notification and stop background service
            stopPlaybackService()
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.cancel(ZorifyPlaybackService.NOTIFICATION_ID)
            } catch (e: Exception) {
                Log.e(tag, "Failed to cancel notification on logout: ${e.message}")
            }
            
            _currentScreen.value = Screen.AUTH
            Toast.makeText(context, "Keluar dari akun.", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateUserProfile(username: String, colorHex: String) {
        viewModelScope.launch {
            val ok = repository.updateUserProfile(username, colorHex)
            if (ok) {
                Toast.makeText(context, "Profil diperbarui!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- AUDIO PLAYER ENGINE ---
    fun playTrack(song: Song, queue: List<Song> = emptyList(), isRetry: Boolean = false) {
        val finalQueue = queue.ifEmpty { allSongs.value }
        playbackQueue = finalQueue
        _queue.value = finalQueue
        
        // Check offline restrictions
        if (_isOfflineMode.value && !song.isDownloaded) {
            Toast.makeText(context, "Lagu ini belum diunduh untuk diputar offline.", Toast.LENGTH_LONG).show()
            return
        }

        if (!isRetry) {
            playRetryCount = 0
            pendingSeekPosition = null
        }

        _currentSong.value = song
        saveLastPlayedSongId(song.id)
        parseLyricsForCurrentSong(song.lyrics)

        // Save to play history
        activeUser.value?.let { user ->
            viewModelScope.launch {
                repository.addSongToHistory(user.id, song.id)
            }
        }

        try {
            val player = mediaPlayer ?: MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            }
            mediaPlayer = player
            player.reset()

            // Re-apply attributes and wake lock after reset
            player.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            player.setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)

            // Determine whether to stream or load local file
            val hasLocalFile = song.isDownloaded && song.localFilePath != null && File(song.localFilePath).exists()
            
            _isBuffering.value = true
            
            player.setOnPreparedListener {
                _isBuffering.value = false
                playRetryCount = 0 // Reset retry count on successful preparation
                pendingSeekPosition?.let { pos ->
                    seekTo(pos)
                    pendingSeekPosition = null
                }
                player.start()
                _isPlaying.value = true
                _duration.value = player.duration
                startProgressTicker()
                triggerMediaAlert(true)
                releaseWakeLock() // Release our transition wake lock!
            }
            player.setOnCompletionListener {
                handleCompletion()
            }
            player.setOnInfoListener { _, what, extra ->
                when (what) {
                    android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                        Log.d(tag, "MediaPlayer buffering started")
                        _isBuffering.value = true
                        true
                    }
                    android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                        Log.d(tag, "MediaPlayer buffering ended")
                        _isBuffering.value = false
                        true
                    }
                    else -> false
                }
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e(tag, "MediaPlayer Error what=$what extra=$extra")
                _isBuffering.value = false
                _isPlaying.value = false
                releaseWifiLock()
                releaseWakeLock() // Release our transition wake lock!
                
                val lastPos = _currentPosition.value
                if (playRetryCount < maxPlayRetries) {
                    playRetryCount++
                    pendingSeekPosition = lastPos
                    Log.w(tag, "Koneksi lambat / terputus. Mencoba memutar kembali ($playRetryCount/$maxPlayRetries)...")
                    viewModelScope.launch {
                        delay(1000)
                        playTrack(song, playbackQueue, isRetry = true)
                    }
                } else {
                    Log.e(tag, "Koneksi tidak stabil. Beralih ke lagu berikutnya...")
                    viewModelScope.launch {
                        delay(500)
                        skipNext()
                    }
                }
                true
            }

            if (hasLocalFile) {
                player.setDataSource(song.localFilePath)
                Log.d(tag, "Playing local high-quality file for ${song.title}: ${song.localFilePath}")
                player.prepareAsync()
            } else {
                viewModelScope.launch {
                    try {
                        val directStreamUrl = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.example.data.UrlHelper.convertToDirectStreamUrl(song.remoteUrl, song.title, song.artist)
                        }
                        if (mediaPlayer == player) {
                            if (directStreamUrl.startsWith("http")) {
                                val uri = android.net.Uri.parse(directStreamUrl)
                                val headers = mapOf(
                                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                                    "Referer" to "https://spotifydown.com/",
                                    "Origin" to "https://spotifydown.com"
                                )
                                player.setDataSource(context, uri, headers)
                            } else {
                                player.setDataSource(directStreamUrl)
                            }
                            Log.d(tag, "Streaming remote file for ${song.title}: $directStreamUrl")
                            acquireWifiLock() // Lock Wi-Fi for remote streaming
                            player.prepareAsync()
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Exception preparing remote playback: ${e.message}", e)
                        _isBuffering.value = false
                        Toast.makeText(context, "Gagal memutar audio dari server.", Toast.LENGTH_SHORT).show()
                        releaseWakeLock()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception preparing playback: ${e.message}", e)
            Toast.makeText(context, "Gagal memutar audio.", Toast.LENGTH_SHORT).show()
            releaseWakeLock()
        }
    }

    fun play() {
        mediaPlayer?.let {
            it.start()
            _isPlaying.value = true
            startProgressTicker()
            triggerMediaAlert(true)
            val curSong = _currentSong.value
            if (curSong != null && (!curSong.isDownloaded || curSong.localFilePath == null)) {
                acquireWifiLock()
            }
        } ?: run {
            _currentSong.value?.let { playTrack(it) }
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
        _isPlaying.value = false
        stopProgressTicker()
        triggerMediaAlert(true)
        releaseWifiLock()
    }

    fun togglePlay() {
        if (_isPlaying.value) pause() else play()
    }

    // --- SLEEP TIMER TIMER ENGINE ---
    fun setSleepTimer(seconds: Int?) {
        sleepTimerJob?.cancel()
        if (seconds == null || seconds == 0) {
            _sleepTimerSecondsLeft.value = null
            Toast.makeText(context, "Pengatur waktu tidur dinonaktifkan", Toast.LENGTH_SHORT).show()
        } else {
            _sleepTimerSecondsLeft.value = seconds
            val text = if (seconds < 60) "$seconds detik" else "${seconds / 60} menit"
            Toast.makeText(context, "Pengatur waktu tidur diatur ke $text", Toast.LENGTH_SHORT).show()
            sleepTimerJob = viewModelScope.launch {
                var timeLeft = seconds
                while (timeLeft > 0) {
                    delay(1000L)
                    timeLeft--
                    _sleepTimerSecondsLeft.value = if (timeLeft > 0) timeLeft else null
                }
                pause()
                Toast.makeText(context, "Waktu tidur habis, pemutaran dihentikan", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        _currentPosition.value = positionMs
        updateLyricsIndex(positionMs)
        updateMediaSessionState()
    }

    fun skipNext() {
        if (playbackQueue.isEmpty()) {
            playbackQueue = allSongs.value
            _queue.value = allSongs.value
        }
        if (playbackQueue.isEmpty()) return

        val currentIndex = playbackQueue.indexOfFirst { it.id == _currentSong.value?.id }
        var nextIndex = if (_playbackMode.value == PlaybackMode.SHUFFLE) {
            playbackQueue.indices.random()
        } else {
            (currentIndex + 1) % playbackQueue.size
        }
        
        if (nextIndex < 0 || nextIndex >= playbackQueue.size) nextIndex = 0
        
        playTrack(playbackQueue[nextIndex], playbackQueue)
    }

    fun skipPrev() {
        if (playbackQueue.isEmpty()) {
            playbackQueue = allSongs.value
            _queue.value = allSongs.value
        }
        if (playbackQueue.isEmpty()) return

        val currentIndex = playbackQueue.indexOfFirst { it.id == _currentSong.value?.id }
        var prevIndex = if (_playbackMode.value == PlaybackMode.SHUFFLE) {
            playbackQueue.indices.random()
        } else {
            (currentIndex - 1 + playbackQueue.size) % playbackQueue.size
        }
        
        if (prevIndex < 0 || prevIndex >= playbackQueue.size) prevIndex = 0
        
        playTrack(playbackQueue[prevIndex], playbackQueue)
    }

    fun togglePlaybackMode() {
        _playbackMode.value = if (_playbackMode.value == PlaybackMode.SEQUENTIAL) PlaybackMode.SHUFFLE else PlaybackMode.SEQUENTIAL
        Toast.makeText(context, "Mode: " + if (_playbackMode.value == PlaybackMode.SHUFFLE) "Acak (Shuffle)" else "Berurutan", Toast.LENGTH_SHORT).show()
    }

    fun toggleRepeatOne() {
        _isRepeatOne.value = !_isRepeatOne.value
        Toast.makeText(context, if (_isRepeatOne.value) "Ulangi Lagu Aktif" else "Ulangi Mati", Toast.LENGTH_SHORT).show()
        showPlaybackNotification()
    }

    fun toggleCoverMode() {
        _isCircleCoverMode.value = !_isCircleCoverMode.value
    }

    private fun handleCompletion() {
        // Acquire wake lock to keep CPU awake during the 1-second delay and next song loading transition
        acquireWakeLock(15000)

        if (_isRepeatOne.value) {
            mediaPlayer?.seekTo(0)
            mediaPlayer?.start()
            _currentPosition.value = 0
            _currentLyricIndex.value = -1
            releaseWakeLock()
        } else {
            viewModelScope.launch {
                delay(1000) // Exactly 1 second delay as requested!
                skipNext()
            }
        }
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = viewModelScope.launch {
            while (true) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        val pos = it.currentPosition
                        _currentPosition.value = pos
                        updateLyricsIndex(pos)
                    }
                }
                delay(100)
            }
        }
    }

    private fun stopProgressTicker() {
        progressTickerJob?.cancel()
    }

    // --- REAL-TIME LRC LYRICS SYNC ENGINE ---
    private fun parseLyricsForCurrentSong(lrcText: String) {
        val lines = mutableListOf<LyricLine>()
        lrcText.split("\n").forEach { rawLine ->
            val line = rawLine.trim()
            try {
                if (line.startsWith("[") && line.contains("]")) {
                    val bracketIndex = line.indexOf(']')
                    if (bracketIndex != -1) {
                        val timeStr = line.substring(1, bracketIndex).trim() // e.g. "00:03.00"
                        val text = line.substring(bracketIndex + 1).trim()
                        val parts = timeStr.split(":", ".")
                        if (parts.size >= 2) {
                            val min = parts[0].toLongOrNull()
                            val sec = parts[1].toLongOrNull()
                            if (min != null && sec != null) {
                                // Parse decimal hundredths or milliseconds safely
                                val decStr = if (parts.size > 2) parts[2].trim() else "0"
                                val ms = when (decStr.length) {
                                    1 -> decStr.toLong() * 100
                                    2 -> decStr.toLong() * 10
                                    else -> decStr.toLongOrNull() ?: 0L
                                }
                                
                                val timestampMs = (min * 60 * 1000) + (sec * 1000) + ms
                                lines.add(LyricLine(timestampMs, text))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore faulty LRC rows
            }
        }
        _lyricLines.value = lines.sortedBy { it.timestampMs }
        _currentLyricIndex.value = -1
    }

    private fun updateLyricsIndex(currentPosMs: Int) {
        val lines = _lyricLines.value
        if (lines.isEmpty()) return
        val index = lines.indexOfLast { it.timestampMs <= currentPosMs }
        if (index != _currentLyricIndex.value) {
            _currentLyricIndex.value = index
        }
    }

    // --- PLAYLIST ACTIONS ---
    fun createPlaylist(name: String, desc: String, coverUrl: String = "") {
        activeUser.value?.let { user ->
            viewModelScope.launch {
                repository.createPlaylist(user.id, name, desc, coverUrl)
                Toast.makeText(context, "Daftar Putar '$name' dibuat!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun createPlaylistWithSong(name: String, desc: String, coverUrl: String = "", songId: Long) {
        activeUser.value?.let { user ->
            viewModelScope.launch {
                val playlistId = repository.createPlaylist(user.id, name, desc, coverUrl)
                repository.addSongToPlaylist(playlistId, songId)
                val playlistName = userPlaylists.value.find { it.id == playlistId }?.name ?: name
                Toast.makeText(context, "Daftar Putar '$playlistName' dibuat & lagu ditambahkan!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updatePlaylist(playlistId: Long, name: String, desc: String, coverUrl: String) {
        viewModelScope.launch {
            repository.updatePlaylist(playlistId, name, desc, coverUrl)
            Toast.makeText(context, "Daftar Putar diperbarui!", Toast.LENGTH_SHORT).show()
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            _currentScreen.value = Screen.COLLECTION
            Toast.makeText(context, "Daftar putar dihapus.", Toast.LENGTH_SHORT).show()
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
            val playlistName = userPlaylists.value.find { it.id == playlistId }?.name ?: "Daftar Putar"
            showSpotifyToast("Ditambahkan ke $playlistName")
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
            val playlistName = userPlaylists.value.find { it.id == playlistId }?.name ?: "Daftar Putar"
            showSpotifyToast("Dihapus dari $playlistName")
        }
    }

    // --- FAVORITE LIKES ACTIONS ---
    fun toggleLikeSong(songId: Long) {
        val user = activeUser.value
        if (user == null) {
            showSpotifyToast("Silakan masuk akun terlebih dahulu.")
            return
        }
        viewModelScope.launch {
            val isLiked = userLikedSongs.value.any { it.id == songId }
            repository.toggleLikeSong(user.id, songId)
            
            // Sync with local current track flow
            _currentSong.value?.let { cur ->
                if (cur.id == songId) {
                    // force flow reload
                    _currentSong.value = cur.copy()
                }
            }

            if (!isLiked) {
                showSpotifyToast("Ditambahkan ke Lagu yang Disukai")
            } else {
                showSpotifyToast("Dihapus dari Lagu yang Disukai")
            }
            showPlaybackNotification()
        }
    }

    // --- FILE DOWNLOADING ACTION ---
    fun downloadSong(song: Song) {
        if (song.isDownloaded) {
            viewModelScope.launch {
                repository.deleteDownloadedSong(song)
                Toast.makeText(context, "Unduhan '${song.title}' dihapus.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        viewModelScope.launch {
            Toast.makeText(context, "Mengunduh audio berkualitas tinggi: ${song.title}...", Toast.LENGTH_SHORT).show()
            
            val success = repository.downloadSong(song) { progress ->
                // Update active downloads map
                _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                    put(song.id, progress)
                }
            }
            
            _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                remove(song.id)
            }
            
            if (success) {
                Toast.makeText(context, "'${song.title}' berhasil diunduh untuk pemutaran offline!", Toast.LENGTH_SHORT).show()
                // Update current song in view if it's the one we just downloaded
                _currentSong.value?.let { cur ->
                    if (cur.id == song.id) {
                        val reloaded = repository.getSongById(song.id)
                        if (reloaded != null) {
                            _currentSong.value = reloaded
                        }
                    }
                }
            } else {
                Toast.makeText(context, "Gagal mengunduh '${song.title}'. Coba lagi nanti.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- ADMIN SONG CRUD ---
    fun saveSong(song: Song) {
        viewModelScope.launch {
            repository.saveSong(song)
            Toast.makeText(context, "Lagu '${song.title}' berhasil disimpan!", Toast.LENGTH_SHORT).show()
            
            // If editing currently playing song, sync its details
            _currentSong.value?.let { cur ->
                if (cur.id == song.id) {
                    _currentSong.value = song
                    parseLyricsForCurrentSong(song.lyrics)
                }
            }
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            repository.deleteSong(song)
            Toast.makeText(context, "Lagu '${song.title}' berhasil dihapus!", Toast.LENGTH_SHORT).show()
            
            // If the deleted song is currently playing, stop playback
            _currentSong.value?.let { cur ->
                if (cur.id == song.id) {
                    pause()
                    _currentSong.value = null
                    saveLastPlayedSongId(-1L)
                }
            }
        }
    }

    fun toggleLikeCurrentSong() {
        val song = _currentSong.value ?: return
        toggleLikeSong(song.id)
    }

    fun triggerCastOutputToast() {
        Toast.makeText(context, "Output Media: Ponsel ini (Zorify Connect)", Toast.LENGTH_SHORT).show()
    }

    fun updateSyncServerUrl(url: String) {
        _syncServerUrl.value = url.trim()
        val prefs = context.getSharedPreferences("zorify_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("sync_server_url", url.trim()).apply()
    }

    fun syncWithCloud() {
        val user = activeUser.value
        if (user == null) {
            Toast.makeText(context, "Silakan masuk akun terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return
        }

        val serverUrl = syncServerUrl.value
        if (serverUrl.isBlank()) {
            Toast.makeText(context, "Alamat server sinkronisasi tidak boleh kosong.", Toast.LENGTH_SHORT).show()
            return
        }

        if (_isSyncing.value) return
        _isSyncing.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Gather all data to push
                val payload = JSONObject()
                payload.put("username", user.username)
                payload.put("avatarColorHex", user.avatarColorHex)

                // Custom Songs (all songs with ID > 4)
                val localSongs = repository.getAllSongs()
                val customSongsArray = JSONArray()
                localSongs.filter { it.id > 4 }.forEach { song ->
                    val songObj = JSONObject()
                    songObj.put("title", song.title)
                    songObj.put("artist", song.artist)
                    songObj.put("remoteUrl", song.remoteUrl)
                    songObj.put("coverUrl", song.coverUrl)
                    songObj.put("lyrics", song.lyrics)
                    customSongsArray.put(songObj)
                }
                payload.put("customSongs", customSongsArray)

                // Liked Songs
                val likedSongsList = userLikedSongs.value
                val likedArray = JSONArray()
                likedSongsList.forEach { song ->
                    val songObj = JSONObject()
                    songObj.put("title", song.title)
                    songObj.put("artist", song.artist)
                    likedArray.put(songObj)
                }
                payload.put("likedSongs", likedArray)

                // Playlists and their songs
                val playlistsList = userPlaylists.value
                val playlistsArray = JSONArray()
                playlistsList.forEach { playlist ->
                    val plObj = JSONObject()
                    plObj.put("name", playlist.name)
                    plObj.put("description", playlist.description)
                    plObj.put("coverUrl", playlist.coverUrl)

                    // Get songs in this playlist
                    val songsInPl = repository.getSongsInPlaylistFlow(playlist.id).firstOrNull() ?: emptyList()
                    val songsArray = JSONArray()
                    songsInPl.forEach { s ->
                        val sObj = JSONObject()
                        sObj.put("title", s.title)
                        sObj.put("artist", s.artist)
                        songsArray.put(sObj)
                    }
                    plObj.put("songs", songsArray)
                    playlistsArray.put(plObj)
                }
                payload.put("playlists", playlistsArray)

                // Play History
                val historyList = playHistory.value
                val historyArray = JSONArray()
                historyList.forEach { s ->
                    val sObj = JSONObject()
                    sObj.put("title", s.title)
                    sObj.put("artist", s.artist)
                    historyArray.put(sObj)
                }
                payload.put("playHistory", historyArray)

                // 2. Perform POST Request to sync server
                val cleanUrl = if (serverUrl.endsWith("/")) serverUrl.substring(0, serverUrl.length - 1) else serverUrl
                val syncEndpoint = "$cleanUrl/api/sync/push"
                
                val urlObj = URL(syncEndpoint)
                val conn = urlObj.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")

                conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                    os.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()

                    // 3. Parse Server Response and Merge back into Local DB
                    val responseJson = JSONObject(responseStr)
                    if (responseJson.optBoolean("success", false)) {
                        val serverCustomSongs = responseJson.optJSONArray("customSongs") ?: JSONArray()
                        val serverLikedSongs = responseJson.optJSONArray("likedSongs") ?: JSONArray()
                        val serverPlaylists = responseJson.optJSONArray("playlists") ?: JSONArray()
                        val serverPlayHistory = responseJson.optJSONArray("playHistory") ?: JSONArray()

                        // A. Merge Custom Songs
                        val existingSongs = repository.getAllSongs()
                        var maxId = existingSongs.maxOfOrNull { it.id } ?: 4L
                        if (maxId < 4L) maxId = 4L

                        val titleArtistToSongMap = existingSongs.associateBy { "${it.title.lowercase()}||${it.artist.lowercase()}" }
                        
                        for (i in 0 until serverCustomSongs.length()) {
                            val sObj = serverCustomSongs.getJSONObject(i)
                            val sTitle = sObj.getString("title")
                            val sArtist = sObj.getString("artist")
                            val key = "${sTitle.lowercase()}||${sArtist.lowercase()}"
                            
                            if (!titleArtistToSongMap.containsKey(key)) {
                                maxId++
                                val newSong = Song(
                                    id = maxId,
                                    title = sTitle,
                                    artist = sArtist,
                                    remoteUrl = sObj.getString("remoteUrl"),
                                    coverUrl = sObj.optString("coverUrl", "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=150"),
                                    lyrics = sObj.optString("lyrics", "")
                                )
                                repository.saveSong(newSong)
                            }
                        }

                        // Refresh existing songs list after merging songs
                        val updatedSongs = repository.getAllSongs()
                        val updatedSongsMap = updatedSongs.associateBy { "${it.title.lowercase()}||${it.artist.lowercase()}" }

                        // B. Merge Liked Songs
                        for (i in 0 until serverLikedSongs.length()) {
                            val sObj = serverLikedSongs.getJSONObject(i)
                            val sTitle = sObj.getString("title")
                            val sArtist = sObj.getString("artist")
                            val key = "${sTitle.lowercase()}||${sArtist.lowercase()}"
                            
                            val localSong = updatedSongsMap[key]
                            if (localSong != null) {
                                val isLiked = repository.isSongLiked(user.id, localSong.id)
                                if (!isLiked) {
                                    repository.toggleLikeSong(user.id, localSong.id)
                                }
                            }
                        }

                        // C. Merge Playlists
                        val existingPlaylists = repository.getPlaylistsFlow(user.id).firstOrNull() ?: emptyList()
                        val plNameToObjMap = existingPlaylists.associateBy { it.name.lowercase() }

                        for (i in 0 until serverPlaylists.length()) {
                            val plObj = serverPlaylists.getJSONObject(i)
                            val plName = plObj.getString("name")
                            val plDesc = plObj.optString("description", "")
                            val plCover = plObj.optString("coverUrl", "")
                            
                            var targetPlId: Long
                            val localPl = plNameToObjMap[plName.lowercase()]
                            if (localPl == null) {
                                // Create new playlist
                                targetPlId = repository.createPlaylist(user.id, plName, plDesc, plCover)
                            } else {
                                targetPlId = localPl.id
                            }

                            // Get existing playlist songs to avoid duplication
                            val existingPlSongs = repository.getSongsInPlaylistFlow(targetPlId).firstOrNull() ?: emptyList()
                            val existingPlSongsSet = existingPlSongs.map { "${it.title.lowercase()}||${it.artist.lowercase()}" }.toSet()

                            val plSongsArray = plObj.optJSONArray("songs") ?: JSONArray()
                            for (j in 0 until plSongsArray.length()) {
                                val psObj = plSongsArray.getJSONObject(j)
                                val psTitle = psObj.getString("title")
                                val psArtist = psObj.getString("artist")
                                val psKey = "${psTitle.lowercase()}||${psArtist.lowercase()}"

                                if (!existingPlSongsSet.contains(psKey)) {
                                    val localSong = updatedSongsMap[psKey]
                                    if (localSong != null) {
                                        repository.addSongToPlaylist(targetPlId, localSong.id)
                                    }
                                }
                            }
                        }

                        // D. Merge Play History
                        val localHistory = repository.getPlayHistoryFlow(user.id).firstOrNull() ?: emptyList()
                        val localHistorySet = localHistory.map { "${it.title.lowercase()}||${it.artist.lowercase()}" }.toSet()

                        for (i in 0 until serverPlayHistory.length()) {
                            val sObj = serverPlayHistory.getJSONObject(i)
                            val sTitle = sObj.getString("title")
                            val sArtist = sObj.getString("artist")
                            val key = "${sTitle.lowercase()}||${sArtist.lowercase()}"

                            if (!localHistorySet.contains(key)) {
                                val localSong = updatedSongsMap[key]
                                if (localSong != null) {
                                    repository.addSongToHistory(user.id, localSong.id)
                                }
                            }
                        }

                        // 4. Update Synced Timestamp
                        val formatter = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss", java.util.Locale.getDefault())
                        val currentTime = formatter.format(java.util.Date())
                        
                        _lastSyncedTime.value = currentTime
                        val prefs = context.getSharedPreferences("zorify_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putString("last_synced_time", currentTime).apply()

                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Sinkronisasi Cloud Berhasil!", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val errMsg = responseJson.optString("message", "Gagal sinkronisasi data")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error server: $errMsg", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP status $responseCode"
                    conn.disconnect()
                    Log.e(tag, "Sync failed: $errorText")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Koneksi gagal: HTTP $responseCode", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Exception during Cloud Sync: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Kesalahan koneksi: ${e.localizedMessage ?: "periksa alamat server"}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sleepTimerJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        stopProgressTicker()
        releaseWifiLock()
        
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (e: Exception) {
            // Ignored
        }

        // Cancel notification and stop service when ViewModel is cleared
        stopPlaybackService()
    }
}
