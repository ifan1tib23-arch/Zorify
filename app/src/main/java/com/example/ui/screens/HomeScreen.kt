package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Playlist
import com.example.data.Song
import com.example.ui.Screen
import com.example.ui.ZorifyViewModel
import com.example.ui.components.PlayingEqualizerIndicator
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: ZorifyViewModel, modifier: Modifier = Modifier) {
    val songs by viewModel.allSongs.collectAsState()
    val playlists by viewModel.userPlaylists.collectAsState()
    val activeUser by viewModel.activeUser.collectAsState()
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    val greetingText = remember {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Selamat Pagi"
            hour < 15 -> "Selamat Siang"
            hour < 18 -> "Selamat Sore"
            else -> "Selamat Malam"
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F3E2F), // dark slate green top
                        Color(0xFF121212)  // pure pitch black
                    ),
                    startY = 0f,
                    endY = 1200f
                )
            ),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- HEADER ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = greetingText,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("greeting_title")
                    )
                    Text(
                        text = if (activeUser != null) "Halo, ${activeUser?.username}!" else "Masuk Zorify",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Offline Mode Badge
                    if (isOfflineMode) {
                        Surface(
                            color = Color(0xFFFF5252),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "OFFLINE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Notification Alert icon
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.HISTORY) },
                        modifier = Modifier.background(Color(0x11FFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History",
                            tint = Color.White
                        )
                    }

                    // Avatar Icon
                    val avatarColor = activeUser?.avatarColorHex?.let { Color(android.graphics.Color.parseColor(it)) } ?: Color(0xFF1DB954)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(avatarColor)
                            .clickable { viewModel.navigateTo(Screen.PROFILE) }
                            .testTag("profile_avatar_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = activeUser?.username?.take(1)?.uppercase() ?: "?",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // --- 2x2 RECENTLY PLAYED SONGS GRID ---
        item {
            Column {
                Text(
                    text = "Berdasarkan yang baru didengar",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (songs.isEmpty()) {
                    CircularProgressIndicator(
                        color = Color(0xFF1DB954),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    val gridSongs = songs.take(4)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 0 until gridSongs.size step 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Left Card
                                val song1 = gridSongs[i]
                                val isCurrent1 = currentSong?.id == song1.id
                                val isCurrentPlaying1 = isCurrent1 && isPlaying
                                Box(modifier = Modifier.weight(1f)) {
                                    RecentSongCard(
                                        song = song1,
                                        isOfflineMode = isOfflineMode,
                                        downloadProgress = downloadProgress[song1.id],
                                        isCurrent = isCurrent1,
                                        isPlaying = isCurrentPlaying1,
                                        onClick = { viewModel.playTrack(song1, songs) }
                                    )
                                }
                                // Right Card
                                if (i + 1 < gridSongs.size) {
                                    val song2 = gridSongs[i + 1]
                                    val isCurrent2 = currentSong?.id == song2.id
                                    val isCurrentPlaying2 = isCurrent2 && isPlaying
                                    Box(modifier = Modifier.weight(1f)) {
                                        RecentSongCard(
                                            song = song2,
                                            isOfflineMode = isOfflineMode,
                                            downloadProgress = downloadProgress[song2.id],
                                            isCurrent = isCurrent2,
                                            isPlaying = isCurrentPlaying2,
                                            onClick = { viewModel.playTrack(song2, songs) }
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- MY PLAYLISTS SECTION (HORIZONTAL ROW) ---
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daftar Putar Anda",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Lihat Semua",
                        color = Color(0xFF1DB954),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.navigateTo(Screen.COLLECTION) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (playlists.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x11FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Mulai buat daftar putarmu di tab Koleksi!",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            PlaylistHorizontalCard(
                                playlist = playlist,
                                onClick = { viewModel.navigateTo(Screen.PLAYLIST_DETAIL, playlist.id) }
                            )
                        }
                    }
                }
            }
        }

        // --- RECOMMENDATION HERO CARD ---
        item {
            Column {
                Text(
                    text = "Direkomendasikan Untukmu",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0x19FFFFFF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            AsyncImage(
                                model = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?q=80&w=150",
                                contentDescription = "Discover",
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )

                            Column {
                                Text(
                                    text = "Discover Weekly",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Pilihan lagu segar terbaik disinkronkan otomatis sesuai seleramu.",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (songs.isNotEmpty()) {
                                    viewModel.playTrack(songs.first(), songs)
                                }
                            },
                            modifier = Modifier
                                .background(Color(0xFF1DB954), CircleShape)
                                .size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Recommendations",
                                tint = Color.Black
                            )
                        }
                    }
                }
            }
        }

        // Extra spacing for bottom mini player
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun RecentSongCard(
    song: Song,
    isOfflineMode: Boolean,
    downloadProgress: Float?,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .testTag("recent_song_card_${song.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.coverUrl,
                contentDescription = song.title,
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = song.title,
                        color = if (isCurrent) Color(0xFF1DB954) else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCurrent) {
                        PlayingEqualizerIndicator(isPlaying = isPlaying)
                    }
                }
                Text(
                    text = song.artist,
                    color = if (isCurrent) Color(0xFF1DB954).copy(alpha = 0.7f) else Color.LightGray,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (downloadProgress != null) {
                    CircularProgressIndicator(
                        progress = { downloadProgress },
                        color = Color(0xFF1DB954),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (song.isDownloaded) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistHorizontalCard(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() }
            .testTag("playlist_card_${playlist.id}")
    ) {
        AsyncImage(
            model = playlist.coverUrl.ifBlank { "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=150" },
            contentDescription = playlist.name,
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = playlist.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Text(
            text = playlist.description.ifBlank { "Playlist oleh Zora" },
            color = Color.Gray,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
