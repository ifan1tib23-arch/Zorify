package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val password: String, // simple plain password for local offline sign-in
    val avatarColorHex: String = "#1DB954", // custom theme/avatar accent
    val isCurrentActive: Boolean = false
)

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: Long, // preloaded songs can have 1, 2, 3, 4
    val title: String,
    val artist: String,
    val remoteUrl: String,
    val coverUrl: String,
    val localFilePath: String? = null,
    val isDownloaded: Boolean = false,
    val lyrics: String = "" // Timestamped lyric LRC string
)

@Entity(tableName = "liked_songs")
data class LikedSong(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val songId: Long
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val name: String,
    val coverUrl: String = "",
    val description: String = ""
)

@Entity(tableName = "playlist_songs")
data class PlaylistSong(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val songId: Long
)

@Entity(tableName = "play_history")
data class PlayHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val songId: Long,
    val playedAt: Long = System.currentTimeMillis()
)
