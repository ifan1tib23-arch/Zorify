package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ZorifyRepository(
    private val context: Context,
    private val zorifyDao: ZorifyDao
) {
    private val tag = "ZorifyRepository"

    // --- USER MANAGEMENT ---
    fun getActiveUserFlow(): Flow<User?> = zorifyDao.getActiveUserFlow()
    
    suspend fun getActiveUser(): User? = withContext(Dispatchers.IO) {
        zorifyDao.getActiveUser()
    }

    suspend fun registerUser(username: String, password: String): Boolean = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank()) return@withContext false
        val existing = zorifyDao.getUserByUsername(username)
        if (existing != null) return@withContext false // already registered

        // Generate a random bright color accent for the user's avatar
        val colors = listOf("#1DB954", "#18D3C4", "#FF4F6C", "#9D4EDD", "#FF9F1C", "#2EC4B6")
        val randomColor = colors.random()

        val newUser = User(
            username = username,
            password = password,
            avatarColorHex = randomColor,
            isCurrentActive = false
        )
        zorifyDao.insertUser(newUser)
        true
    }

    suspend fun loginUser(username: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val user = zorifyDao.getUserByUsername(username)
        if (user != null && user.password == password) {
            zorifyDao.deactivateAllUsers()
            val activeUser = user.copy(isCurrentActive = true)
            zorifyDao.updateUser(activeUser)
            true
        } else {
            false
        }
    }

    suspend fun loginWithGoogle(email: String): Boolean = withContext(Dispatchers.IO) {
        var user = zorifyDao.getUserByUsername(email)
        if (user == null) {
            val colors = listOf("#1DB954", "#18D3C4", "#FF4F6C", "#9D4EDD", "#FF9F1C", "#2EC4B6")
            val randomColor = colors.random()
            val newUser = User(
                username = email,
                password = "google_auth",
                avatarColorHex = randomColor,
                isCurrentActive = false
            )
            zorifyDao.insertUser(newUser)
            user = zorifyDao.getUserByUsername(email)
        }
        if (user != null) {
            zorifyDao.deactivateAllUsers()
            val activeUser = user.copy(isCurrentActive = true)
            zorifyDao.updateUser(activeUser)
            true
        } else {
            false
        }
    }

    suspend fun logout(): Unit = withContext(Dispatchers.IO) {
        zorifyDao.deactivateAllUsers()
    }

    suspend fun updateUserProfile(username: String, colorHex: String): Boolean = withContext(Dispatchers.IO) {
        val currentUser = zorifyDao.getActiveUser() ?: return@withContext false
        val updatedUser = currentUser.copy(username = username, avatarColorHex = colorHex)
        zorifyDao.updateUser(updatedUser)
        true
    }

    // --- SONG SERVICES & INITS ---
    fun getAllSongsFlow(): Flow<List<Song>> = zorifyDao.getAllSongsFlow()
    suspend fun getAllSongs(): List<Song> = zorifyDao.getAllSongs()
    
    suspend fun getSongById(id: Long): Song? = withContext(Dispatchers.IO) {
        zorifyDao.getSongById(id)
    }

    suspend fun saveSong(song: Song) = withContext(Dispatchers.IO) {
        // Do NOT convert remoteUrl to direct stream URL at insertion time,
        // because streaming URLs from Spotify/YouTube/etc are temporary/short-lived and will expire.
        // We preserve the original URL (e.g. open.spotify.com/track/...) in the DB
        // and resolve it dynamically on-the-fly when playing or downloading.
        zorifyDao.insertSong(song)
    }

    suspend fun deleteSong(song: Song) = withContext(Dispatchers.IO) {
        zorifyDao.deleteSong(song)
    }

    suspend fun initializeDefaultSongs() = withContext(Dispatchers.IO) {
        val defaultSongs = listOf(
            Song(
                id = 1,
                title = "Halu",
                artist = "Feby Putri",
                remoteUrl = "https://www.dropbox.com/scl/fi/j7ddf65rwrkp6ifk2m1zz/Halu-Feby-Putri-Official-Music-Video-Feby-Putri-NC.mp3?rlkey=lqh6405a319exv53d6ksuhnug&st=r153vdio&dl=1",
                coverUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRK8WYYgsHZg91_JxvNhJG_KvNf_p8-KOIc8ZFvuR2Gkg&s",
                lyrics = """
                    [00:00.00]Zorify High Quality Audio
                    [00:03.00]Halu - Feby Putri
                    [00:07.00]Ku tersenyum saat kau hadir dalam lamunanku
                    [00:15.00]Menari-nari indah di sudut bayanganku
                    [00:23.00]Rasa yang aneh bersemi di dalam dada ini
                    [00:30.00]Mengisi ruang kosong yang t'lah lama sepi
                    [00:38.00]Ku melayang tinggi dalam halusinasi
                    [00:46.00]Membayangkan dirimu selalu ada di sini
                    [00:54.00]Menanti senyuman yang tak kunjung kembali...
                    [01:03.00]Zorify Premium • Terima kasih telah mendengarkan
                """.trimIndent()
            ),
            Song(
                id = 2,
                title = "Apa Artinya Cinta",
                artist = "Zinu Arashi",
                remoteUrl = "https://www.dropbox.com/scl/fi/8bcp7jci5ctsapjjtotvj/Apa-Artinya-Cinta-Video-Lirik-Cover-Viral-TikTok-YouTube-Zinu-Arashi.mp3?rlkey=27z6xff02v2qiehjkciis5mbf&st=ion466up&dl=1",
                coverUrl = "https://i.scdn.co/image/ab67616d00001e027496de43aa02afd149919060",
                lyrics = """
                    [00:00.00]Zorify High Quality Audio
                    [00:02.00]Apa Artinya Cinta - Zinu Arashi
                    [00:06.00]Mencari arti cinta di sunyinya malam yang kelabu
                    [00:14.00]Menatap deretan bintang-bintang di atas sana
                    [00:22.00]Apakah ini rasa tulus yang selalu kunanti?
                    [00:30.00]Atau sekadar mimpi yang akan hilang lagi?
                    [00:38.00]Cinta sejati tak akan pernah berpaling
                    [00:46.00]Dia akan setia bernyanyi di relung batin...
                    [00:55.00]Zorify - High-Fidelity Audio Experience
                """.trimIndent()
            ),
            Song(
                id = 3,
                title = "Maha Melihat",
                artist = "Opick feat. Amanda",
                remoteUrl = "https://www.dropbox.com/scl/fi/adadrizmar6dvfb2c18mm/Maha-Melihat-Opick-feat-Amanda-Cover-by-PI7U-PI7U.mp3?rlkey=e0oa4dokzi66vo7xbntensy1f&st=fjihg0rw&dl=1",
                coverUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?q=80&w=200",
                lyrics = """
                    [00:00.00]Zorify High Quality Audio
                    [00:03.00]Maha Melihat - Opick feat. Amanda
                    [00:08.00]Sering kali hamba ini lalai dan lupa akan-Mu
                    [00:16.00]Saat tawa bahagia menyelimuti langkahku
                    [00:24.00]Namun Engkau selalu sabar menanti hamba kembali
                    [00:32.00]Maha Melihat segala duka lara di hati ini
                    [00:40.00]Ampunilah salah dan khilaf hamba-Mu ini
                    [00:48.00]Tuntunlah raga ini menuju ridho-Mu yang suci...
                    [00:57.00]Zorify - Musik yang Menyentuh Jiwa
                """.trimIndent()
            ),
            Song(
                id = 4,
                title = "Santai Malam",
                artist = "Indie Chill",
                remoteUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=150",
                lyrics = """
                    [00:00.00]Zorify High Quality Audio
                    [00:02.00]Indie Chill - Santai Malam
                    [00:06.00]Menikmati sejuknya angin malam yang berbisik lirih
                    [00:15.00]Duduk santai di teras ditemani cangkir kopi hangat
                    [00:24.00]Alunan instrumen mengalir lambat menenangkan pikiran
                    [00:34.00]Melupakan seluruh penat kesibukan hari ini
                    [00:44.00]Damai terasa mengalir menyentuh sukma...
                    [00:54.00]Zorify Chill Lounge - Santai Malam Anda
                """.trimIndent()
            )
        )
        val existing = zorifyDao.getAllSongs()
        if (existing.isEmpty()) {
            zorifyDao.insertSongs(defaultSongs)
            
            // Also register a default account "zora" with password "1234" to let the user log in immediately!
            val zoraUser = zorifyDao.getUserByUsername("zora")
            if (zoraUser == null) {
                val user = User(
                    username = "zora",
                    password = "1234",
                    avatarColorHex = "#1DB954",
                    isCurrentActive = false // do not pre-activate, force login/register screen on new install
                )
                val userId = zorifyDao.insertUser(user)
                
                // Pre-create a favorite/liked playlist
                val playlist = Playlist(
                    userId = userId,
                    name = "Zorify Chill Vibes",
                    description = "Kumpulan lagu santai terbaik untuk harimu.",
                    coverUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?q=80&w=200"
                )
                val plId = zorifyDao.insertPlaylist(playlist)
                // Do not preload Halu (songId = 1) so it starts with a plus (+) icon as requested
                zorifyDao.insertPlaylistSong(PlaylistSong(playlistId = plId, songId = 4))
            }
        } else {
            // Update default songs in the database with current lyrics to guarantee sync
            defaultSongs.forEach { defSong ->
                val match = existing.find { it.id == defSong.id }
                if (match != null && (match.lyrics.isNullOrBlank() || match.lyrics != defSong.lyrics)) {
                    zorifyDao.updateSong(match.copy(lyrics = defSong.lyrics))
                }
            }
        }
        
        // Clean up: Ensure "Halu" (ID = 1) is removed from all playlists and liked songs so it starts completely fresh with a plus (+) icon
        try {
            zorifyDao.deleteAllPlaylistSongsForSong(1)
            zorifyDao.deleteLikedSongForAllUsers(1)
        } catch (e: Exception) {
            // ignore
        }
    }

    // --- FAVORITES (LIKED SONGS) ---
    fun getLikedSongsFlow(userId: Long): Flow<List<LikedSong>> = zorifyDao.getLikedSongsFlow(userId)

    suspend fun isSongLiked(userId: Long, songId: Long): Boolean = withContext(Dispatchers.IO) {
        zorifyDao.getLikedSong(userId, songId) != null
    }

    suspend fun toggleLikeSong(userId: Long, songId: Long) = withContext(Dispatchers.IO) {
        val existing = zorifyDao.getLikedSong(userId, songId)
        if (existing != null) {
            zorifyDao.deleteLikedSong(userId, songId)
        } else {
            zorifyDao.insertLikedSong(LikedSong(userId = userId, songId = songId))
        }
    }

    // --- PLAYLISTS ---
    fun getPlaylistsFlow(userId: Long): Flow<List<Playlist>> = zorifyDao.getPlaylistsFlow(userId)

    suspend fun createPlaylist(userId: Long, name: String, desc: String, cover: String = ""): Long = withContext(Dispatchers.IO) {
        val defaultCover = cover.ifBlank { "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=150" }
        val playlist = Playlist(userId = userId, name = name, description = desc, coverUrl = defaultCover)
        zorifyDao.insertPlaylist(playlist)
    }

    suspend fun updatePlaylist(playlistId: Long, name: String, desc: String, coverUrl: String) = withContext(Dispatchers.IO) {
        val existing = zorifyDao.getPlaylistById(playlistId) ?: return@withContext
        val updated = existing.copy(
            name = name,
            description = desc,
            coverUrl = coverUrl.ifBlank { "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=150" }
        )
        zorifyDao.insertPlaylist(updated)
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        zorifyDao.deletePlaylist(playlistId)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        zorifyDao.insertPlaylistSong(PlaylistSong(playlistId = playlistId, songId = songId))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        zorifyDao.deletePlaylistSong(playlistId, songId)
    }

    fun getSongsInPlaylistFlow(playlistId: Long): Flow<List<Song>> = zorifyDao.getSongsInPlaylistFlow(playlistId)

    suspend fun getPlaylistIdsForSong(songId: Long): List<Long> = withContext(Dispatchers.IO) {
        zorifyDao.getPlaylistIdsForSong(songId)
    }

    fun getPlaylistIdsForSongFlow(songId: Long): Flow<List<Long>> {
        return zorifyDao.getPlaylistIdsForSongFlow(songId)
    }

    // --- PLAY HISTORY ---
    fun getPlayHistoryFlow(userId: Long): Flow<List<Song>> = zorifyDao.getPlayHistoryFlow(userId)

    suspend fun addSongToHistory(userId: Long, songId: Long) = withContext(Dispatchers.IO) {
        zorifyDao.insertPlayHistory(PlayHistory(userId = userId, songId = songId))
    }

    // --- GENUINE FILE DOWNLOADER (FOR STABLE OFFLINE PERFORMANCE) ---
    suspend fun downloadSong(song: Song, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Downloading song ${song.title} from ${song.remoteUrl}")
            
            val downloadDir = File(context.filesDir, "downloads")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            val destinationFile = File(downloadDir, "song_${song.id}.mp3")
            
            val directUrl = UrlHelper.convertToDirectStreamUrl(song.remoteUrl, song.title, song.artist)
            val url = URL(directUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            connection.setRequestProperty("Referer", "https://spotifydown.com/")
            connection.setRequestProperty("Origin", "https://spotifydown.com")
            connection.connect()
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(tag, "HTTP status error during download: ${connection.responseCode} ${connection.responseMessage}")
                return@withContext false
            }
            
            val fileLength = connection.contentLength
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(destinationFile)
            
            val buffer = ByteArray(4096)
            var totalBytesRead = 0L
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (fileLength > 0) {
                    val progress = totalBytesRead.toFloat() / fileLength.toFloat()
                    onProgress(progress)
                }
            }
            
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            connection.disconnect()
            
            // Save file details in local DB
            val updatedSong = song.copy(
                localFilePath = destinationFile.absolutePath,
                isDownloaded = true
            )
            zorifyDao.updateSong(updatedSong)
            
            Log.d(tag, "Song downloaded and database updated successfully: ${destinationFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(tag, "Exception during downloading song ${song.title}: ${e.message}", e)
            false
        }
    }

    // --- REMOVE DOWNLOAD FOR HOUSEKEEPING ---
    suspend fun deleteDownloadedSong(song: Song) = withContext(Dispatchers.IO) {
        try {
            song.localFilePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
            val updatedSong = song.copy(
                localFilePath = null,
                isDownloaded = false
            )
            zorifyDao.updateSong(updatedSong)
        } catch (e: Exception) {
            Log.e(tag, "Exception during deleting downloaded song ${song.title}: ${e.message}", e)
        }
    }
}
