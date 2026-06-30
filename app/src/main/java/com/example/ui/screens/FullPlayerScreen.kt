package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.PlaybackMode
import com.example.ui.ZorifyViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    viewModel: ZorifyViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val playbackMode by viewModel.playbackMode.collectAsState()
    val isRepeatOne by viewModel.isRepeatOne.collectAsState()
    val userLikedSongs by viewModel.userLikedSongs.collectAsState()
    val isCircleCoverMode by viewModel.isCircleCoverMode.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val currentSongProgress = currentSong?.let { downloadProgress[it.id] }

    // Lyrics flows
    val lyricLines by viewModel.lyricLines.collectAsState()
    val currentLyricIndex by viewModel.currentLyricIndex.collectAsState()

    var isLyricsExpanded by remember { mutableStateOf(false) }

    val sleepTimerSecondsLeft by viewModel.sleepTimerSecondsLeft.collectAsState()
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showDownloadLinkDialog by remember { mutableStateOf(false) }
    var resolvedDownloadLink by remember { mutableStateOf("") }

    val timerText = remember(sleepTimerSecondsLeft) {
        val seconds = sleepTimerSecondsLeft ?: return@remember ""
        val m = seconds / 60
        val s = seconds % 60
        String.format("%02d:%02d", m, s)
    }

    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    // Auto scroll lyrics as the track plays
    LaunchedEffect(currentLyricIndex) {
        if (currentLyricIndex >= 0 && lyricLines.isNotEmpty()) {
            scope.launch {
                lazyListState.animateScrollToItem(maxOf(0, currentLyricIndex - 2))
            }
        }
    }

    // RGB Infinite Rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "RGBHalo")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloRotation"
    )

    val isLiked = userLikedSongs.any { it.id == currentSong?.id }

    val playlists by viewModel.userPlaylists.collectAsState()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsState()
    val currentPlaylistName = remember(selectedPlaylistId, playlists) {
        playlists.find { it.id == selectedPlaylistId }?.name ?: "Zorify Chill Vibes"
    }

    val currentSongId = currentSong?.id
    val playlistIdsFlow = remember(currentSongId) {
        if (currentSongId != null) {
            viewModel.getPlaylistIdsForSongFlow(currentSongId)
        } else {
            flowOf(emptyList())
        }
    }
    val containingPlaylistIds by playlistIdsFlow.collectAsState(initial = emptyList())
    val isCurrentSongInAnyCollection = containingPlaylistIds.isNotEmpty() || isLiked

    val song = currentSong
    if (song == null) {
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF122436), // Elegant Spotify-style dark slate blue gradient
                        Color(0xFF0D141C),
                        Color(0xFF121212)
                    )
                )
            )
            .testTag("full_player_modal")
    ) {
        val screenHeight = maxHeight
        val collapsedHeight = 72.dp
        val expandedOffset = 110.dp

        // Animate vertical offset of sliding lyrics card
        val targetOffset = if (isLyricsExpanded) expandedOffset else screenHeight - collapsedHeight
        val animatedOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "lyricsSheetOffset"
        )

        // --- BACK LAYER: THE MUSIC PLAYER INTERFACE ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // --- HEADER BAR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("close_full_player_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Tutup",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "MEMAINKAN DARI PLAYLIST",
                        color = Color.LightGray.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentPlaylistName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                IconButton(onClick = {
                    currentSong?.let { viewModel.openPlaylistSelector(it) }
                }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Lebih Banyak Opsi",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- LARGE COPIED ALBUM COVER CONTAINER ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                val rgbBrush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                    )
                )

                Box(
                    modifier = Modifier
                        .size(if (isCircleCoverMode) 230.dp else 260.dp)
                        .drawBehind {
                            if (isPlaying && isCircleCoverMode) {
                                rotate(rotationAngle) {
                                    drawCircle(
                                        brush = rgbBrush,
                                        style = Stroke(width = 5.dp.toPx())
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = currentSong?.coverUrl,
                        contentDescription = currentSong?.title,
                        modifier = Modifier
                            .size(if (isCircleCoverMode) 200.dp else 260.dp)
                            .clip(if (isCircleCoverMode) CircleShape else RoundedCornerShape(8.dp))
                            .background(Color.DarkGray)
                            .clickable { viewModel.toggleCoverMode() }
                            .testTag("full_player_cover_image"),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- TRACK DETAILS (TITLE & ARTIST) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong?.title ?: "",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("full_player_title")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = currentSong?.artist ?: "",
                        color = Color.LightGray.copy(alpha = 0.8f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("full_player_artist")
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Favorite (Heart/Love) Button
                    IconButton(onClick = {
                        currentSong?.let { song ->
                            viewModel.toggleLikeSong(song.id)
                        }
                    }) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Lagu Favorit",
                            tint = if (isLiked) Color(0xFF1DB954) else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(onClick = {
                        viewModel.showSpotifyToast("Lagu dilewati / disembunyikan")
                        viewModel.skipNext()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Sembunyikan",
                            tint = Color.LightGray,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            currentSong?.let { song ->
                                viewModel.openPlaylistSelector(song)
                            }
                        },
                        modifier = Modifier.testTag("full_player_plus_button")
                    ) {
                        Icon(
                            imageVector = if (isCurrentSongInAnyCollection) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                            contentDescription = "Koleksi",
                            tint = if (isCurrentSongInAnyCollection) Color(0xFF1DB954) else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- PROGRESS SEEKER SLIDER (OPTIMIZED RECOMPOSITION) ---
            PlaybackSeekBar(
                currentPosition = currentPosition,
                duration = duration,
                onSeek = { viewModel.seekTo(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- PLAYBACK CONTROLS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(
                    onClick = { viewModel.togglePlaybackMode() },
                    modifier = Modifier.testTag("shuffle_mode_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Acak",
                        tint = if (playbackMode == PlaybackMode.SHUFFLE) Color(0xFF1DB954) else Color.LightGray,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Prev
                IconButton(
                    onClick = { viewModel.skipPrev() },
                    modifier = Modifier.testTag("prev_track_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Sebelumnya",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Play/Pause circular frame
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { viewModel.togglePlay() }
                        .testTag("full_play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Putar",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Next
                IconButton(
                    onClick = { viewModel.skipNext() },
                    modifier = Modifier.testTag("next_track_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Berikutnya",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Repeat Mode
                IconButton(
                    onClick = { viewModel.toggleRepeatOne() },
                    modifier = Modifier.testTag("repeat_mode_button")
                ) {
                    Icon(
                        imageVector = if (isRepeatOne) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Ulangi",
                        tint = if (isRepeatOne) Color(0xFF1DB954) else Color.LightGray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- BOTTOM DEVICE/UTILITY BAR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Perangkat
                IconButton(onClick = {
                    viewModel.showSpotifyToast("Memutar di handphone Anda")
                }) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "Pilih Perangkat",
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Sleep Timer (Clock)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = { showSleepTimerDialog = true },
                        modifier = Modifier.testTag("sleep_timer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Waktu Tidur",
                            tint = if (sleepTimerSecondsLeft != null) Color(0xFF1DB954) else Color.LightGray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (sleepTimerSecondsLeft != null) {
                        Text(
                            text = timerText,
                            color = Color(0xFF1DB954),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Copy Download Link Button
                IconButton(onClick = {
                    scope.launch {
                        viewModel.showSpotifyToast("Menghubungkan link download...")
                        val directUrl = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.example.data.UrlHelper.convertToDirectStreamUrl(song.remoteUrl, song.title, song.artist)
                        }
                        val finalUrl = if (directUrl.isNotBlank() && directUrl.startsWith("http")) {
                            directUrl
                        } else if (song.remoteUrl.isNotBlank() && song.remoteUrl.startsWith("http")) {
                            song.remoteUrl
                        } else {
                            ""
                        }

                        if (finalUrl.isNotBlank()) {
                            resolvedDownloadLink = finalUrl
                            showDownloadLinkDialog = true
                        } else {
                            viewModel.showSpotifyToast("Link tidak tersedia untuk lagu ini.")
                        }
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Salin Link",
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Download Button (Offline)
                if (currentSongProgress != null) {
                    CircularProgressIndicator(
                        progress = { currentSongProgress },
                        color = Color(0xFF1DB954),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    IconButton(onClick = {
                        if (song.isDownloaded) {
                            viewModel.showSpotifyToast("Lagu sudah diunduh untuk offline.")
                        } else {
                            viewModel.downloadSong(song)
                        }
                    }) {
                        Icon(
                            imageVector = if (song.isDownloaded) Icons.Default.DownloadForOffline else Icons.Outlined.Download,
                            contentDescription = "Unduh",
                            tint = if (song.isDownloaded) Color(0xFF1DB954) else Color.LightGray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Share Button
                IconButton(onClick = {
                    val sendIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(
                            android.content.Intent.EXTRA_TEXT,
                            "Dengarkan '${song.title}' oleh ${song.artist} di Zorify!\n\n${song.remoteUrl}"
                        )
                        type = "text/plain"
                    }
                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Bagikan Lagu")
                    context.startActivity(shareIntent)
                }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Bagikan",
                        tint = Color.LightGray,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Queue List Button
                IconButton(onClick = {
                    showQueueSheet = true
                }) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Antrean",
                        tint = Color.LightGray,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // --- FRONT LAYER: SLIDING LRC LYRICS OVERLAY SHEET ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(screenHeight - expandedOffset + 40.dp)
                .offset(y = animatedOffset)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        if (dragAmount > 8) {
                            isLyricsExpanded = false
                        } else if (dragAmount < -8) {
                            isLyricsExpanded = true
                        }
                    }
                }
                .testTag("sliding_lyrics_panel"),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xF2162520) // Deep dark emerald green frost container
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                // Drag handle bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clickable { isLyricsExpanded = !isLyricsExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(40.dp, 4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.3f))
                        )
                    }
                }

                // Header bar of the panel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isLyricsExpanded = !isLyricsExpanded }
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isLyricsExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = "Tarik Panel Lirik",
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Lirik Real-Time",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        // Real-time mini lrc karaoke preview text when collapsed!
                        if (!isLyricsExpanded) {
                            val activeLyricText = if (currentLyricIndex in lyricLines.indices) {
                                lyricLines[currentLyricIndex].text
                            } else {
                                ""
                            }
                            if (activeLyricText.isNotBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = activeLyricText,
                                    color = Color(0xFF1DB954),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Full scrolling lyrics list (Visible only when expanded)
                if (isLyricsExpanded) {
                    if (lyricLines.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Lirik tidak tersedia untuk lagu ini.",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(lyricLines) { idx, line ->
                                val isActive = idx == currentLyricIndex

                                val lyricColor by animateColorAsState(
                                    targetValue = if (isActive) Color.White else Color(0x66FFFFFF),
                                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                    label = "lyricColorAnim"
                                )
                                val animatedFontSizeValue by animateFloatAsState(
                                    targetValue = if (isActive) 19f else 15f,
                                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                    label = "lyricFontAnim"
                                )
                                val lyricFontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold

                                Text(
                                    text = line.text,
                                    color = lyricColor,
                                    fontSize = animatedFontSizeValue.sp,
                                    fontWeight = lyricFontWeight,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Tap on any lyric line to seek directly to that timestamp!
                                            viewModel.seekTo(line.timestampMs.toInt())
                                        }
                                        .testTag("lyric_row_$idx"),
                                    style = LocalTextStyle.current.copy(lineHeight = 26.sp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = {
                Text(
                    text = "Pengatur Waktu Tidur",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val options = listOf(
                    "Nonaktifkan" to null,
                    "10 Detik (Demo)" to 10,
                    "5 Menit" to 300,
                    "10 Menit" to 600,
                    "15 Menit" to 900,
                    "30 Menit" to 1800,
                    "45 Menit" to 2700,
                    "60 Menit" to 3600
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(options) { index, option ->
                        val label = option.first
                        val seconds = option.second
                        val isSelected = if (seconds == null) {
                            sleepTimerSecondsLeft == null
                        } else {
                            sleepTimerSecondsLeft != null && sleepTimerSecondsLeft == seconds
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF1DB954).copy(alpha = 0.15f) else Color.Transparent)
                                .clickable {
                                    viewModel.setSleepTimer(seconds)
                                    showSleepTimerDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color(0xFF1DB954) else Color.White,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Terpilih",
                                    tint = Color(0xFF1DB954),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Batal", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF1A1A1A)
        )
    }

    if (showDownloadLinkDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadLinkDialog = false },
            title = {
                Text(
                    text = "Link Download Lagu",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Berikut adalah tautan unduhan langsung untuk lagu \"${song.title}\" oleh ${song.artist}:",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2A2A2A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = resolvedDownloadLink,
                                color = Color(0xFF1DB954),
                                fontSize = 12.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                    
                    Text(
                        text = "Tips: Anda dapat menyalin tautan ini atau membukanya langsung di browser untuk mengunduh lagu .mp3 ini ke galeri musik perangkat Anda.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = {
                        try {
                            val openIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(resolvedDownloadLink))
                            context.startActivity(openIntent)
                        } catch (e: Exception) {
                            viewModel.showSpotifyToast("Gagal membuka browser.")
                        }
                    }) {
                        Text("Buka di Browser", color = Color(0xFF1DB954), fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = {
                            val clipData = android.content.ClipData.newPlainText("Zorify Download Link", resolvedDownloadLink)
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(clipData)
                            viewModel.showSpotifyToast("Link disalin ke papan klip!")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
                    ) {
                        Text("Salin Link", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadLinkDialog = false }) {
                    Text("Tutup", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF1A1A1A)
        )
    }

    if (showQueueSheet) {
        val queueSongs by viewModel.queue.collectAsState()
        AlertDialog(
            onDismissRequest = { showQueueSheet = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Antrean Putar",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${queueSongs.size} Lagu",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Sekarang Memutar",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Current Song item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1DB954).copy(alpha = 0.15f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = song.coverUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = Color(0xFF1DB954),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Sedang diputar",
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Berikutnya Dalam Antrean",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (queueSongs.isEmpty()) {
                        Text(
                            text = "Tidak ada lagu lain dalam antrean.",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(queueSongs) { index, s ->
                                val isCurrent = s.id == song.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCurrent) Color.White.copy(alpha = 0.05f) else Color.Transparent)
                                        .clickable {
                                            viewModel.playTrack(s, queueSongs)
                                            showQueueSheet = false
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = if (isCurrent) Color(0xFF1DB954) else Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(20.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    AsyncImage(
                                        model = s.coverUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = s.title,
                                            color = if (isCurrent) Color(0xFF1DB954) else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = s.artist,
                                            color = Color.LightGray,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQueueSheet = false }) {
                    Text("Tutup", color = Color(0xFF1DB954), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1A1A1A)
        )
    }
}

@Composable
fun PlaybackSeekBar(
    currentPosition: Int,
    duration: Int,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    val activePosition = sliderPosition ?: currentPosition.toFloat()
    val coercedPosition = activePosition.coerceIn(0f, maxOf(1f, duration.toFloat()))

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = coercedPosition,
            onValueChange = { newValue ->
                sliderPosition = newValue
            },
            onValueChangeFinished = {
                sliderPosition?.let {
                    onSeek(it.toInt())
                    sliderPosition = null
                }
            },
            valueRange = 0f..maxOf(1f, duration.toFloat()),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF1DB954),
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("song_progress_slider")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(coercedPosition.toInt()),
                color = Color.LightGray.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatTime(duration),
                color = Color.LightGray.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
