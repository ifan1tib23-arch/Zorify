package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Song
import com.example.ui.ZorifyViewModel
import com.example.ui.components.PlayingEqualizerIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: ZorifyViewModel, modifier: Modifier = Modifier) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val userLikedSongs by viewModel.userLikedSongs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E1E24), // slate black top
                        Color(0xFF121212)  // pitch black bottom
                    )
                )
            )
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Cari",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // --- SEARCH BOX ---
        TextField(
            value = query,
            onValueChange = { viewModel.updateSearchQuery(it) },
            placeholder = { Text("Lagu, artis, atau genre...", color = Color.Gray, fontSize = 14.sp) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Cari", tint = Color.LightGray) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Color.LightGray)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF282828),
                unfocusedContainerColor = Color(0xFF242424),
                disabledContainerColor = Color(0xFF242424),
                cursorColor = Color(0xFF1DB954),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_input_box")
        )

        Spacer(modifier = Modifier.height(20.dp))

        // --- SEARCH RESULTS OR BROWSE SUGGESTIONS ---
        if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Search icon",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Lagu atau artis tidak ditemukan",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(results, key = { it.id }) { song ->
                    val isLiked = userLikedSongs.any { it.id == song.id }
                    val isCurrent = currentSong?.id == song.id
                    val isCurrentPlaying = isCurrent && isPlaying
                    
                    SearchSongRow(
                        song = song,
                        isLiked = isLiked,
                        isOfflineMode = isOfflineMode,
                        downloadProgress = downloadProgress[song.id],
                        isCurrent = isCurrent,
                        isPlaying = isCurrentPlaying,
                        onPlayClick = { viewModel.playTrack(song, results) },
                        onLikeClick = { viewModel.toggleLikeSong(song.id) },
                        onDownloadClick = { viewModel.downloadSong(song) }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
fun SearchSongRow(
    song: Song,
    isLiked: Boolean,
    isOfflineMode: Boolean,
    downloadProgress: Float?,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    onPlayClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onPlayClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.coverUrl,
            contentDescription = song.title,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.title,
                    color = if (isCurrent) Color(0xFF1DB954) else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isCurrent) {
                    Spacer(modifier = Modifier.width(6.dp))
                    PlayingEqualizerIndicator(isPlaying = isPlaying)
                }
                
                if (song.isDownloaded) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = song.artist,
                color = if (isCurrent) Color(0xFF1DB954).copy(alpha = 0.7f) else Color.LightGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Action Buttons Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Like/Favorite
            IconButton(onClick = onLikeClick) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Suka",
                    tint = if (isLiked) Color(0xFF1DB954) else Color.LightGray,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Download Button
            if (downloadProgress != null) {
                CircularProgressIndicator(
                    progress = { downloadProgress },
                    color = Color(0xFF1DB954),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                IconButton(onClick = onDownloadClick) {
                    Icon(
                        imageVector = if (song.isDownloaded) Icons.Default.DownloadForOffline else Icons.Outlined.Download,
                        contentDescription = "Unduh",
                        tint = if (song.isDownloaded) Color(0xFF1DB954) else Color.LightGray,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
