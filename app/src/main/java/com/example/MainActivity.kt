package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.Song
import com.example.ui.Screen
import com.example.ui.ZorifyViewModel
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[ZorifyViewModel::class.java]
    }

    private val playbackReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                "com.example.zorify.ACTION_PLAY_PAUSE" -> viewModel.togglePlay()
                "com.example.zorify.ACTION_NEXT" -> viewModel.skipNext()
                "com.example.zorify.ACTION_PREV" -> viewModel.skipPrev()
                "com.example.zorify.ACTION_LIKE" -> viewModel.toggleLikeCurrentSong()
                "com.example.zorify.ACTION_REPEAT" -> viewModel.toggleRepeatOne()
                "com.example.zorify.ACTION_SHUFFLE" -> viewModel.togglePlaybackMode()
                "com.example.zorify.ACTION_CAST" -> viewModel.triggerCastOutputToast()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission for Android 13+ (API 33)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = "android.permission.POST_NOTIFICATIONS"
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 101)
            }
        }

        // Register action receiver for system notification buttons
        val filter = android.content.IntentFilter().apply {
            addAction("com.example.zorify.ACTION_PLAY_PAUSE")
            addAction("com.example.zorify.ACTION_NEXT")
            addAction("com.example.zorify.ACTION_PREV")
            addAction("com.example.zorify.ACTION_LIKE")
            addAction("com.example.zorify.ACTION_REPEAT")
            addAction("com.example.zorify.ACTION_SHUFFLE")
            addAction("com.example.zorify.ACTION_CAST")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playbackReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(playbackReceiver, filter)
        }

        setContent {
            MyApplicationTheme {
                ZorifyApp(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(playbackReceiver)
        } catch (e: Exception) {
            // Ignored
        }
        // Cancel notification when app is destroyed/closed
        val manager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.cancel(1111)
    }
}

@Composable
fun ZorifyApp(viewModel: ZorifyViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    var showFullPlayer by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = remember(context) { context as? android.app.Activity }

    if (showFullPlayer) {
        BackHandler {
            showFullPlayer = false
        }
    } else if (currentScreen == Screen.AUTH) {
        BackHandler {
            // Exit/finish the activity if pressing back on login/register screen
            activity?.finish()
        }
    } else if (currentScreen != Screen.HOME) {
        BackHandler {
            when (currentScreen) {
                Screen.PLAYLIST_DETAIL, Screen.LIKED_SONGS -> viewModel.navigateTo(Screen.COLLECTION)
                Screen.HISTORY -> viewModel.navigateTo(Screen.HOME)
                Screen.ADMIN -> viewModel.navigateTo(Screen.PROFILE)
                else -> viewModel.navigateTo(Screen.HOME)
            }
        }
    } else {
        // When on Home screen, swiping or pressing back moves the app to background instead of finishing/destroying it,
        // so that the background music player continues to run seamlessly.
        BackHandler {
            activity?.moveTaskToBack(true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (!showFullPlayer && currentScreen != Screen.AUTH) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF121212))
                    ) {
                        // --- BOTTOM MINI PLAYER BAR ---
                        if (currentSong != null) {
                            MiniPlayerBar(
                                song = currentSong!!,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                onPlayPauseClick = { viewModel.togglePlay() },
                                onNextClick = { viewModel.skipNext() },
                                onBarClick = { showFullPlayer = true }
                            )
                        }

                        // --- NAVIGATION BOTTOM BAR ---
                        NavigationBar(
                            containerColor = Color(0xFF121212),
                            tonalElevation = 0.dp,
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            NavigationBarItem(
                                selected = currentScreen == Screen.HOME || currentScreen == Screen.HISTORY,
                                onClick = { viewModel.navigateTo(Screen.HOME) },
                                icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Home") },
                                label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF1DB954),
                                    selectedTextColor = Color(0xFF1DB954),
                                    unselectedIconColor = Color.LightGray,
                                    unselectedTextColor = Color.LightGray,
                                    indicatorColor = Color(0x221DB954)
                                ),
                                modifier = Modifier.testTag("nav_home_button")
                            )

                            NavigationBarItem(
                                selected = currentScreen == Screen.SEARCH,
                                onClick = { viewModel.navigateTo(Screen.SEARCH) },
                                icon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Cari") },
                                label = { Text("Cari", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF1DB954),
                                    selectedTextColor = Color(0xFF1DB954),
                                    unselectedIconColor = Color.LightGray,
                                    unselectedTextColor = Color.LightGray,
                                    indicatorColor = Color(0x221DB954)
                                ),
                                modifier = Modifier.testTag("nav_search_button")
                            )

                            NavigationBarItem(
                                selected = currentScreen == Screen.COLLECTION || currentScreen == Screen.PLAYLIST_DETAIL || currentScreen == Screen.LIKED_SONGS,
                                onClick = { viewModel.navigateTo(Screen.COLLECTION) },
                                icon = { Icon(imageVector = Icons.Default.LibraryMusic, contentDescription = "Koleksi") },
                                label = { Text("Koleksi", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF1DB954),
                                    selectedTextColor = Color(0xFF1DB954),
                                    unselectedIconColor = Color.LightGray,
                                    unselectedTextColor = Color.LightGray,
                                    indicatorColor = Color(0x221DB954)
                                ),
                                modifier = Modifier.testTag("nav_collection_button")
                            )

                            NavigationBarItem(
                                selected = currentScreen == Screen.PROFILE,
                                onClick = { viewModel.navigateTo(Screen.PROFILE) },
                                icon = { Icon(imageVector = Icons.Default.Person, contentDescription = "Akun") },
                                label = { Text("Akun", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF1DB954),
                                    selectedTextColor = Color(0xFF1DB954),
                                    unselectedIconColor = Color.LightGray,
                                    unselectedTextColor = Color.LightGray,
                                    indicatorColor = Color(0x221DB954)
                                ),
                                modifier = Modifier.testTag("nav_profile_button")
                            )
                        }
                    }
                }
            },
            contentWindowInsets = WindowInsets(0.dp) // handle drawing areas manually
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .background(Color(0xFF121212))
            ) {
                when (currentScreen) {
                    Screen.HOME -> HomeScreen(viewModel = viewModel)
                    Screen.SEARCH -> SearchScreen(viewModel = viewModel)
                    Screen.COLLECTION -> CollectionScreen(viewModel = viewModel)
                    Screen.PLAYLIST_DETAIL -> PlaylistDetailScreen(viewModel = viewModel)
                    Screen.LIKED_SONGS -> LikedSongsScreen(viewModel = viewModel)
                    Screen.PROFILE -> ProfileScreen(viewModel = viewModel)
                    Screen.HISTORY -> HistoryScreen(viewModel = viewModel)
                    Screen.ADMIN -> AdminScreen(viewModel = viewModel)
                    Screen.AUTH -> AuthScreen(viewModel = viewModel)
                }
            }

            // --- FULL PLAYER OVERLAY SCREEN WITH SLIDE-UP TRANSITION ---
            AnimatedVisibility(
                visible = showFullPlayer && currentScreen != Screen.AUTH,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(400)
                ) + fadeIn(animationSpec = tween(400)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(400)
                ) + fadeOut(animationSpec = tween(400))
            ) {
                FullPlayerScreen(
                    viewModel = viewModel,
                    onClose = { showFullPlayer = false }
                )
            }



            // --- SPOTIFY CUSTOM TOAST ALERT (SLIDE-UP) ---
            val spotifyToastMessage by viewModel.spotifyToastMessage.collectAsState()
            AnimatedVisibility(
                visible = spotifyToastMessage != null,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 250)
                ) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (showFullPlayer) 40.dp else 120.dp)
                    .padding(horizontal = 20.dp)
                    .zIndex(100f)
            ) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF282828)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (spotifyToastMessage?.contains("Dihapus") == true || spotifyToastMessage?.contains("masuk") == true) Icons.Default.Info else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (spotifyToastMessage?.contains("Dihapus") == true || spotifyToastMessage?.contains("masuk") == true) Color.LightGray else Color(0xFF1DB954),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = spotifyToastMessage ?: "",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Only show "Ubah" for Liked/Playlist actions
                        if (spotifyToastMessage?.contains("Lagu yang Disukai") == true || spotifyToastMessage?.contains("Ditambahkan") == true) {
                            Text(
                                text = "Ubah",
                                color = Color(0xFF1DB954),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.dismissSpotifyToast()
                                        viewModel.currentSong.value?.let { song ->
                                            viewModel.openPlaylistSelector(song)
                                        }
                                    }
                                    .padding(start = 12.dp)
                            )
                        }
                    }
                }
            }

            // --- PLAYLIST SELECTOR CHECKLIST DIALOG ---
            val playlistSelectorSong by viewModel.showPlaylistSelector.collectAsState()
            if (playlistSelectorSong != null) {
                val song = playlistSelectorSong!!
                val playlists by viewModel.userPlaylists.collectAsState()
                
                val playlistIdsFlow = remember(song.id) {
                    viewModel.getPlaylistIdsForSongFlow(song.id)
                }
                val containingPlaylists by playlistIdsFlow.collectAsState(initial = emptyList())
                val likedSongs by viewModel.userLikedSongs.collectAsState()
                val isLiked = remember(likedSongs, song.id) { likedSongs.any { it.id == song.id } }
                
                var showCreatePlaylistInlineDialog by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = { viewModel.closePlaylistSelector() },
                    title = {
                        Text(
                            text = "Simpan ke Playlist",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                        ) {
                            // Create New Playlist directly trigger
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showCreatePlaylistInlineDialog = true
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF282828)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = "Daftar Putar Baru",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // 1. Always show Liked Songs (Favorites) as the top option
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.toggleLikeSong(song.id)
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF1DB954).copy(alpha = 0.1f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                    contentDescription = null,
                                                    tint = if (isLiked) Color(0xFF1DB954) else Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Text(
                                                text = "Koleksi Lagu Favorit",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Checkbox(
                                            checked = isLiked,
                                            onCheckedChange = {
                                                viewModel.toggleLikeSong(song.id)
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = Color(0xFF1DB954),
                                                uncheckedColor = Color.LightGray,
                                                checkmarkColor = Color.Black
                                            )
                                        )
                                    }
                                }

                                // 2. Custom User Playlists
                                items(playlists) { playlist ->
                                    val isChecked = containingPlaylists.contains(playlist.id)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (isChecked) {
                                                    viewModel.removeSongFromPlaylist(playlist.id, song.id)
                                                } else {
                                                    viewModel.addSongToPlaylist(playlist.id, song.id)
                                                }
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            AsyncImage(
                                                model = playlist.coverUrl,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(4.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Text(
                                                text = playlist.name,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                if (checked == true) {
                                                    viewModel.addSongToPlaylist(playlist.id, song.id)
                                                } else {
                                                    viewModel.removeSongFromPlaylist(playlist.id, song.id)
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = Color(0xFF1DB954),
                                                uncheckedColor = Color.LightGray,
                                                checkmarkColor = Color.Black
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { viewModel.closePlaylistSelector() }
                        ) {
                            Text(
                                text = "Selesai",
                                color = Color(0xFF1DB954),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    },
                    containerColor = Color(0xFF1A1A1A)
                )

                if (showCreatePlaylistInlineDialog) {
                    var newPlaylistName by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showCreatePlaylistInlineDialog = false },
                        title = {
                            Text(
                                text = "Daftar Putar Baru",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            OutlinedTextField(
                                value = newPlaylistName,
                                onValueChange = { newPlaylistName = it },
                                label = { Text("Nama Daftar Putar", color = Color.LightGray) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF1DB954),
                                    unfocusedBorderColor = Color.LightGray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (newPlaylistName.isNotBlank()) {
                                        viewModel.createPlaylistWithSong(
                                            name = newPlaylistName,
                                            desc = "Dibuat dari player",
                                            coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=200",
                                            songId = song.id
                                        )
                                        showCreatePlaylistInlineDialog = false
                                    } else {
                                        Toast.makeText(context, "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text("Buat", color = Color(0xFF1DB954), fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCreatePlaylistInlineDialog = false }) {
                                Text("Batal", color = Color.LightGray)
                            }
                        },
                        containerColor = Color(0xFF1A1A1A)
                    )
                }
            }
        }
    }
}

@Composable
fun MiniPlayerBar(
    song: Song,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onBarClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onBarClick() }
            .testTag("mini_player_bar"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2B38))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                AsyncImage(
                    model = song.coverUrl,
                    contentDescription = song.title,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )

                Column {
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Animated miniature green equalizer waves if playing
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(12.dp)
                    ) {
                        EqualizerMiniAnimation()
                    }
                }

                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("mini_play_pause_button")
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onNextClick,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("mini_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Berikutnya",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EqualizerMiniAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "miniEQ")
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val barCount = 3
        for (i in 0 until barCount) {
            val duration = remember(i) { (300..500).random() }
            val heightPercent by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(duration, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "mini_bar_$i"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightPercent)
                    .background(Color(0xFF1DB954), shape = RoundedCornerShape(1.dp))
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

private fun formatTimeDot(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d.%02d", minutes, seconds)
}
