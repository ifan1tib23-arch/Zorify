package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.example.ui.Screen
import com.example.ui.ZorifyViewModel
import com.example.ui.components.PlayingEqualizerIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(viewModel: ZorifyViewModel, modifier: Modifier = Modifier) {
    val selectedId by viewModel.selectedPlaylistId.collectAsState()
    val playlists by viewModel.userPlaylists.collectAsState()
    val playlistSongs by viewModel.activePlaylistSongs.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    val playlist = playlists.find { it.id == selectedId }
    var showAddSongsDrawer by remember { mutableStateOf(false) }

    if (playlist == null) {
        Box(
            modifier = modifier.fillMaxSize().background(Color(0xFF121212)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF1DB954))
        }
        return
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(playlist.name) }
    var editDesc by remember { mutableStateOf(playlist.description) }
    var editCover by remember { mutableStateOf(playlist.coverUrl) }

    LaunchedEffect(playlist) {
        editName = playlist.name
        editDesc = playlist.description
        editCover = playlist.coverUrl
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Daftar Putar", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nama Daftar Putar") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF1DB954),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Deskripsi") },
                        singleLine = false,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF1DB954),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editCover,
                        onValueChange = { editCover = it },
                        label = { Text("URL Gambar Sampul") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF1DB954),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            viewModel.updatePlaylist(playlist.id, editName, editDesc, editCover)
                            showEditDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
                ) {
                    Text("Simpan", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Batal", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2C3E50), // steel blue-grey top
                        Color(0xFF121212)  // black
                    )
                )
            )
            .padding(horizontal = 16.dp)
    ) {
        // --- TOP BACK BUTTON BAR ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.navigateTo(Screen.COLLECTION) },
                    modifier = Modifier.testTag("playlist_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = Color.White
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.testTag("edit_playlist_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Playlist",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { viewModel.deletePlaylist(playlist.id) },
                        modifier = Modifier.testTag("delete_playlist_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Hapus Playlist",
                            tint = Color(0xFFE57373)
                        )
                    }
                }
            }
        }

        // --- PLAYLIST INFORMATION HEADER ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = playlist.coverUrl.ifBlank { "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=150" },
                    contentDescription = playlist.name,
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = playlist.name,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = playlist.description.ifBlank { "Daftar Putar khusus Zorify" },
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${playlistSongs.size} Lagu",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Play All Button Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (playlistSongs.isNotEmpty()) {
                                viewModel.playTrack(playlistSongs.first(), playlistSongs)
                            }
                        },
                        enabled = playlistSongs.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Putar", tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Putar Semua", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showAddSongsDrawer = !showAddSongsDrawer },
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(Color.LightGray, Color.Gray))),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = if (showAddSongsDrawer) Icons.Default.ExpandLess else Icons.Default.Add,
                            contentDescription = "Tambah Lagu",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tambah Lagu", color = Color.White)
                    }
                }
            }
        }

        // --- ADD SONGS COMPONENT DRAWER ---
        if (showAddSongsDrawer) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Ketuk '+' untuk menambahkan ke daftar ini:",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // List of songs not already in this playlist
                        val addableSongs = allSongs.filter { s -> playlistSongs.none { it.id == s.id } }

                        if (addableSongs.isEmpty()) {
                            Text(
                                text = "Semua lagu yang tersedia telah ditambahkan ke daftar ini.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                addableSongs.forEach { addableSong ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            AsyncImage(
                                                model = addableSong.coverUrl,
                                                contentDescription = addableSong.title,
                                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Column {
                                                Text(addableSong.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text(addableSong.artist, color = Color.Gray, fontSize = 10.sp)
                                            }
                                        }

                                        IconButton(
                                            onClick = { viewModel.addSongToPlaylist(playlist.id, addableSong.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AddCircle,
                                                contentDescription = "Tambah",
                                                tint = Color(0xFF1DB954)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- PLAYLIST SONGS LIST ---
        if (playlistSongs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.LibraryMusic, contentDescription = "Daftar Kosong", tint = Color.DarkGray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Daftar putar ini masih kosong.", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(playlistSongs, key = { it.id }) { song ->
                val isCurrent = currentSong?.id == song.id
                val isCurrentPlaying = isCurrent && isPlaying
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { viewModel.playTrack(song, playlistSongs) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        AsyncImage(
                            model = song.coverUrl,
                            contentDescription = song.title,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = song.title,
                                    color = if (isCurrent) Color(0xFF1DB954) else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isCurrent) {
                                    PlayingEqualizerIndicator(isPlaying = isCurrentPlaying)
                                }
                            }
                            Text(
                                text = song.artist,
                                color = if (isCurrent) Color(0xFF1DB954).copy(alpha = 0.7f) else Color.LightGray,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.removeSongFromPlaylist(playlist.id, song.id) },
                        modifier = Modifier.testTag("remove_song_from_playlist_${song.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RemoveCircleOutline,
                            contentDescription = "Hapus",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(140.dp))
        }
    }
}
