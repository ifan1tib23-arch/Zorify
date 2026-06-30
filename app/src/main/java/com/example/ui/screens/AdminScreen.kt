package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Song
import com.example.ui.Screen
import com.example.ui.ZorifyViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(viewModel: ZorifyViewModel, modifier: Modifier = Modifier) {
    val allSongs by viewModel.allSongs.collectAsState()
    
    val coroutineScope = rememberCoroutineScope()
    var isFetchingMetadata by remember { mutableStateOf(false) }
    
    var selectedTab by remember { mutableStateOf(0) } // 0: List & Manage, 1: Add New Song
    
    // Song detail dialog edit state
    var editingSong by remember { mutableStateOf<Song?>(null) }
    
    // Add Song Form state
    var titleInput by remember { mutableStateOf("") }
    var artistInput by remember { mutableStateOf("") }
    var audioUrlInput by remember { mutableStateOf("") }
    var coverUrlInput by remember { mutableStateOf("") }
    var lyricsInput by remember { mutableStateOf("") }
    var idInput by remember { mutableStateOf("") }

    // Suggest next ID when form opens or songs list change
    LaunchedEffect(allSongs, selectedTab) {
        if (selectedTab == 1 && idInput.isEmpty()) {
            val nextId = (allSongs.maxOfOrNull { it.id } ?: 0L) + 1
            idInput = nextId.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield, 
                            contentDescription = null,
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Dashboard Admin", 
                            color = Color.White, 
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.PROFILE) },
                        modifier = Modifier.testTag("admin_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Kembali ke Profil", 
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
            )
        },
        containerColor = Color(0xFF121212)
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E1E))
            ) {
                TabButton(
                    text = "Daftar & Kelola Lagu",
                    isSelected = selectedTab == 0,
                    icon = Icons.Default.MusicNote,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = 0 }
                )
                TabButton(
                    text = "Tambah Lagu Baru",
                    isSelected = selectedTab == 1,
                    icon = Icons.Default.AddCircle,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = 1 }
                )
            }

            if (selectedTab == 0) {
                // TAB 0: Manage & List Songs
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0x1F1DB954)),
                            modifier = Modifier.padding(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF1DB954),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Daftar ini adalah database lagu lokal Zorify. Setiap lagu yang Anda tambahkan, edit, atau hapus di sini akan langsung mempengaruhi seluruh daftar putar, pencarian, dan pemutar musik Anda secara real-time!",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    style = LocalTextStyle.current.copy(lineHeight = 15.sp)
                                )
                            }
                        }
                    }

                    if (allSongs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Belum ada lagu di database. Tambahkan lagu baru!", color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(allSongs) { song ->
                            SongAdminRow(
                                song = song,
                                onEditClick = { editingSong = song },
                                onDeleteClick = { viewModel.deleteSong(song) }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            } else {
                // TAB 1: Add New Song Form
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Formulir Penambahan Lagu Baru", 
                            color = Color.White, 
                            fontSize = 16.sp, 
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Silakan lengkapi data lagu di bawah ini untuk dimasukkan langsung ke aplikasi Anda.", 
                            color = Color.Gray, 
                            fontSize = 11.sp
                        )
                    }

                    // Pre-fill / Quick Template Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Pengisian Cepat (Uji Coba)", color = Color(0xFF1DB954), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Klik tombol di bawah ini untuk mengisi form secara otomatis dengan lagu uji coba agar lebih cepat mengetes.", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(bottom = 8.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val nextId = (allSongs.maxOfOrNull { it.id } ?: 0L) + 1
                                            idInput = nextId.toString()
                                            titleInput = "Runtuh"
                                            artistInput = "Feby Putri feat. Fiersa Besari"
                                            audioUrlInput = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"
                                            coverUrlInput = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?q=80&w=200"
                                            lyricsInput = """
                                                [00:00.00]Zorify High Quality Audio
                                                [00:03.00]Runtuh - Feby Putri feat. Fiersa Besari
                                                [00:08.00]Ku terbangun di pagi hari yang cerah ini
                                                [00:15.00]Melihat bayangmu perlahan-lahan menghilang
                                                [00:22.00]Tak mengapa jika ku harus menangis tersedu
                                                [00:30.00]Sebab manusia punya batas tuk terlihat kuat...
                                                [00:38.00]Zorify Premium • Terimakasih
                                            """.trimIndent()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123423)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFF1DB954), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Isi Template", color = Color(0xFF1DB954), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            idInput = ""
                                            titleInput = ""
                                            artistInput = ""
                                            audioUrlInput = ""
                                            coverUrlInput = ""
                                            lyricsInput = ""
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E1A1A)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = null, tint = Color(0xFFE57373), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Bersihkan", color = Color(0xFFE57373), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // ID INPUT
                    item {
                        OutlinedTextField(
                            value = idInput,
                            onValueChange = { idInput = it },
                            label = { Text("ID Lagu (Angka Unik)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF1DB954),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("admin_add_id")
                        )
                    }

                    // TITLE
                    item {
                        OutlinedTextField(
                            value = titleInput,
                            onValueChange = { titleInput = it },
                            label = { Text("Judul Lagu") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF1DB954),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("admin_add_title")
                        )
                    }

                    // ARTIST
                    item {
                        OutlinedTextField(
                            value = artistInput,
                            onValueChange = { artistInput = it },
                            label = { Text("Nama Artis / Penyanyi") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF1DB954),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("admin_add_artist")
                        )
                    }

                    // AUDIO URL
                    item {
                        Column {
                            OutlinedTextField(
                                value = audioUrlInput,
                                onValueChange = { audioUrlInput = it },
                                label = { Text("URL Link (Spotify, YouTube, Dropbox, Drive, dll.)") },
                                singleLine = true,
                                placeholder = { Text("https://open.spotify.com/track/...") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF1DB954),
                                    unfocusedBorderColor = Color.Gray
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("admin_add_audio_url")
                            )
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Zorify mendukung konversi otomatis link YouTube & Spotify secara instan!",
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                
                                Button(
                                    onClick = {
                                        if (audioUrlInput.isNotBlank()) {
                                            isFetchingMetadata = true
                                            coroutineScope.launch {
                                                val meta = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    com.example.data.UrlHelper.fetchMetadataForUrl(audioUrlInput)
                                                }
                                                isFetchingMetadata = false
                                                if (meta != null) {
                                                    titleInput = meta.title
                                                    artistInput = meta.artist
                                                    if (meta.coverUrl.isNotBlank()) {
                                                        coverUrlInput = meta.coverUrl
                                                    }
                                                    android.widget.Toast.makeText(viewModel.getApplication(), "Berhasil mengambil data lagu!", android.widget.Toast.LENGTH_SHORT).show()
                                                } else {
                                                    android.widget.Toast.makeText(viewModel.getApplication(), "Gagal mengambil data. Silakan isi manual.", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isFetchingMetadata && audioUrlInput.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1DB954),
                                        disabledContainerColor = Color(0xFF333333)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    if (isFetchingMetadata) {
                                        CircularProgressIndicator(
                                            color = Color.Black,
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Mengambil...", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ambil Info", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // COVER ART URL
                    item {
                        OutlinedTextField(
                            value = coverUrlInput,
                            onValueChange = { coverUrlInput = it },
                            label = { Text("URL Gambar Cover (JPG/PNG)") },
                            singleLine = true,
                            placeholder = { Text("https://images.unsplash.com/photo-...") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF1DB954),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("admin_add_cover_url")
                        )
                    }

                    // LYRICS LRC
                    item {
                        OutlinedTextField(
                            value = lyricsInput,
                            onValueChange = { lyricsInput = it },
                            label = { Text("Lirik format LRC (Sinkron dengan Waktu)") },
                            placeholder = { Text("[00:01.50] Lirik baris pertama\n[00:05.00] Baris kedua...") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF1DB954),
                                unfocusedBorderColor = Color.Gray
                            ),
                            minLines = 4,
                            modifier = Modifier.fillMaxWidth().testTag("admin_add_lyrics")
                        )
                    }

                    // SUBMIT BUTTON
                    item {
                        Button(
                            onClick = {
                                val parsedId = idInput.toLongOrNull()
                                if (parsedId == null) {
                                    // Invalid ID
                                    return@Button
                                }
                                if (titleInput.isBlank() || artistInput.isBlank() || audioUrlInput.isBlank()) {
                                    // Mandatory check
                                    return@Button
                                }

                                val finalCover = coverUrlInput.ifBlank { "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=150" }
                                val finalLyrics = lyricsInput.ifBlank { "[00:00.00] Zorify Premium Audio • ${titleInput}\n[00:05.00] Terima kasih telah mendengarkan." }

                                val newSong = Song(
                                    id = parsedId,
                                    title = titleInput,
                                    artist = artistInput,
                                    remoteUrl = audioUrlInput,
                                    coverUrl = finalCover,
                                    lyrics = finalLyrics,
                                    isDownloaded = false,
                                    localFilePath = null
                                )

                                viewModel.saveSong(newSong)
                                
                                // Reset Form and Go Back to List
                                idInput = ""
                                titleInput = ""
                                artistInput = ""
                                audioUrlInput = ""
                                coverUrlInput = ""
                                lyricsInput = ""
                                selectedTab = 0
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                            shape = RoundedCornerShape(24.dp),
                            enabled = idInput.toLongOrNull() != null && titleInput.isNotBlank() && artistInput.isNotBlank() && audioUrlInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("admin_submit_song_button")
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SIMPAN LAGU KE DATABASE", color = Color.Black, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(120.dp))
                    }
                }
            }
        }
    }

    // --- DIALOG EDIT SONG ---
    if (editingSong != null) {
        var editTitle by remember { mutableStateOf(editingSong!!.title) }
        var editArtist by remember { mutableStateOf(editingSong!!.artist) }
        var editAudioUrl by remember { mutableStateOf(editingSong!!.remoteUrl) }
        var editCoverUrl by remember { mutableStateOf(editingSong!!.coverUrl) }
        var editLyrics by remember { mutableStateOf(editingSong!!.lyrics) }

        AlertDialog(
            onDismissRequest = { editingSong = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = Color(0xFF1DB954))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ubah Data Lagu", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text("ID Lagu: ${editingSong!!.id} (Tidak dapat diubah)", color = Color.Gray, fontSize = 12.sp)
                    }

                    item {
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("Judul Lagu") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF1DB954),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("admin_edit_title")
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = editArtist,
                            onValueChange = { editArtist = it },
                            label = { Text("Nama Artis") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF1DB954),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("admin_edit_artist")
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = editAudioUrl,
                            onValueChange = { editAudioUrl = it },
                            label = { Text("URL Streaming MP3") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF1DB954),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("admin_edit_audio_url")
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = editCoverUrl,
                            onValueChange = { editCoverUrl = it },
                            label = { Text("URL Gambar Cover") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF1DB954),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("admin_edit_cover_url")
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = editLyrics,
                            onValueChange = { editLyrics = it },
                            label = { Text("Lirik LRC (Sinkronisasi)") },
                            minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF1DB954),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("admin_edit_lyrics")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = editingSong!!.copy(
                            title = editTitle,
                            artist = editArtist,
                            remoteUrl = editAudioUrl,
                            coverUrl = editCoverUrl,
                            lyrics = editLyrics
                        )
                        viewModel.saveSong(updated)
                        editingSong = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                    shape = RoundedCornerShape(16.dp),
                    enabled = editTitle.isNotBlank() && editArtist.isNotBlank() && editAudioUrl.isNotBlank()
                ) {
                    Text("Simpan Perubahan", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { editingSong = null }
                ) {
                    Text("Batal", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun TabButton(
    text: String,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF0F3E2F) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color(0xFF1DB954) else Color.Gray,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = if (isSelected) Color.White else Color.Gray,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun SongAdminRow(
    song: Song,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showConfirmDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                AsyncImage(
                    model = song.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = song.artist,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "ID: ${song.id}",
                        color = Color(0xFF1DB954),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Lagu",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { showConfirmDelete = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus Lagu",
                        tint = Color(0xFFE57373),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = {
                Text("Hapus Lagu?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text("Apakah Anda yakin ingin menghapus lagu '${song.title}' oleh '${song.artist}' secara permanen dari database lokal Anda?", color = Color.LightGray, fontSize = 14.sp)
            },
            containerColor = Color(0xFF1E1E1E),
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteClick()
                        showConfirmDelete = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Hapus", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDelete = false }
                ) {
                    Text("Batal", color = Color.Gray)
                }
            }
        )
    }
}
