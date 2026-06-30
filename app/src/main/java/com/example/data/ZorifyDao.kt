package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ZorifyDao {

    // --- USER QUERIES ---
    @Query("SELECT * FROM users WHERE isCurrentActive = 1 LIMIT 1")
    fun getActiveUserFlow(): Flow<User?>

    @Query("SELECT * FROM users WHERE isCurrentActive = 1 LIMIT 1")
    suspend fun getActiveUser(): User?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    @Query("UPDATE users SET isCurrentActive = 0")
    suspend fun deactivateAllUsers()

    // --- SONG QUERIES ---
    @Query("SELECT * FROM songs ORDER BY id ASC")
    fun getAllSongsFlow(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY id ASC")
    suspend fun getAllSongs(): List<Song>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getSongById(id: Long): Song?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song)

    @Update
    suspend fun updateSong(song: Song)

    @Delete
    suspend fun deleteSong(song: Song)

    // --- LIKED SONGS QUERIES ---
    @Query("SELECT * FROM liked_songs WHERE userId = :userId")
    fun getLikedSongsFlow(userId: Long): Flow<List<LikedSong>>

    @Query("SELECT * FROM liked_songs WHERE userId = :userId AND songId = :songId LIMIT 1")
    suspend fun getLikedSong(userId: Long, songId: Long): LikedSong?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedSong(likedSong: LikedSong)

    @Query("DELETE FROM liked_songs WHERE userId = :userId AND songId = :songId")
    suspend fun deleteLikedSong(userId: Long, songId: Long)

    // --- PLAYLIST QUERIES ---
    @Query("SELECT * FROM playlists WHERE userId = :userId")
    fun getPlaylistsFlow(userId: Long): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: Long): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(playlistSong: PlaylistSong)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deletePlaylistSong(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_songs WHERE songId = :songId")
    suspend fun deleteAllPlaylistSongsForSong(songId: Long)

    @Query("DELETE FROM liked_songs WHERE songId = :songId")
    suspend fun deleteLikedSongForAllUsers(songId: Long)

    @Query("SELECT playlistId FROM playlist_songs WHERE songId = :songId")
    suspend fun getPlaylistIdsForSong(songId: Long): List<Long>

    @Query("SELECT playlistId FROM playlist_songs WHERE songId = :songId")
    fun getPlaylistIdsForSongFlow(songId: Long): Flow<List<Long>>

    @Query("""
        SELECT s.* FROM songs s 
        INNER JOIN playlist_songs ps ON s.id = ps.songId 
        WHERE ps.playlistId = :playlistId
    """)
    fun getSongsInPlaylistFlow(playlistId: Long): Flow<List<Song>>

    // --- PLAY HISTORY QUERIES ---
    @Query("""
        SELECT s.* FROM songs s 
        INNER JOIN play_history ph ON s.id = ph.songId 
        WHERE ph.userId = :userId 
        ORDER BY ph.playedAt DESC LIMIT 30
    """)
    fun getPlayHistoryFlow(userId: Long): Flow<List<Song>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayHistory(playHistory: PlayHistory)
}
