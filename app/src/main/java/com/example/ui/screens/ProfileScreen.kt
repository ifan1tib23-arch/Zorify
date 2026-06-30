package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ZorifyViewModel
import com.example.ui.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: ZorifyViewModel, modifier: Modifier = Modifier) {
    val activeUser by viewModel.activeUser.collectAsState()
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()

    val syncServerUrl by viewModel.syncServerUrl.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncedTime by viewModel.lastSyncedTime.collectAsState()

    // Login/Register Form states
    var isSignUpMode by remember { mutableStateOf(false) }
    var usernameField by remember { mutableStateOf("") }
    var passwordField by remember { mutableStateOf("") }

    var showGoogleChooser by remember { mutableStateOf(false) }
    var customGoogleEmail by remember { mutableStateOf("") }
    var showEmailInput by remember { mutableStateOf(false) }

    // Active User profile editing states
    var editUsername by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf("#1DB954") }

    var serverUrlField by remember { mutableStateOf("") }

    LaunchedEffect(syncServerUrl) {
        serverUrlField = syncServerUrl
    }

    LaunchedEffect(activeUser) {
        activeUser?.let {
            editUsername = it.username
            selectedColorHex = it.avatarColorHex
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF243B55), // luxury dark blue-grey top
                        Color(0xFF121212)  // black
                    )
                )
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (activeUser == null) {
            // --- GUEST VIEW (REGISTRATION / SIGN-IN FORM) ---
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = if (isSignUpMode) "Daftar Akun Zorify" else "Masuk ke Zorify",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("auth_form_title")
                        )

                        Text(
                            text = if (isSignUpMode) "Simpan daftar putar dan sinkronkan riwayat Anda di berbagai perangkat." else "Masuk untuk mengakses daftar putar favorit Anda.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = usernameField,
                            onValueChange = { usernameField = it },
                            label = { Text("Nama Pengguna") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF1DB954),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("username_field")
                        )

                        OutlinedTextField(
                            value = passwordField,
                            onValueChange = { passwordField = it },
                            label = { Text("Kata Sandi") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF1DB954),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_field")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (isSignUpMode) {
                                    viewModel.register(usernameField, passwordField) {
                                        isSignUpMode = false // switch to login upon successful sign up
                                    }
                                } else {
                                    viewModel.login(usernameField, passwordField)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("auth_action_button")
                        ) {
                            Text(
                                text = if (isSignUpMode) "DAFTAR SEKARANG" else "MASUK AKUN",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // --- DIVIDER OR ---
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray.copy(alpha = 0.3f))
                            Text(
                                text = " ATAU ",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray.copy(alpha = 0.3f))
                        }

                        // --- GOOGLE SIGN IN BUTTON ---
                        Button(
                            onClick = { showGoogleChooser = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("google_login_button_profile")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "G ",
                                    color = Color(0xFF4285F4),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Masuk dengan Google",
                                    color = Color(0xFF121212),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        TextButton(
                            onClick = { isSignUpMode = !isSignUpMode },
                            modifier = Modifier.testTag("auth_mode_toggle_button")
                        ) {
                            Text(
                                text = if (isSignUpMode) "Sudah punya akun? Masuk disini" else "Belum punya akun? Daftar gratis disini",
                                color = Color(0xFF1DB954),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        } else {
            // --- LOGGED IN USER PROFILE ---
            item {
                val avatarColor = Color(android.graphics.Color.parseColor(selectedColorHex))
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(avatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeUser?.username?.take(1)?.uppercase() ?: "Z",
                        color = Color.Black,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = activeUser?.username ?: "",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Anggota Premium Zorify",
                    color = Color(0xFF1DB954),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // --- STABLE OFFLINE MODE SETTING CARD ---
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                text = "Mode Offline Stabil",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Hanya memutar audio berkualitas tinggi yang sudah terunduh secara lokal tanpa data internet.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp),
                                style = LocalTextStyle.current.copy(lineHeight = 15.sp)
                            )
                        }

                        Switch(
                            checked = isOfflineMode,
                            onCheckedChange = { viewModel.toggleOfflineMode() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFF1DB954),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.testTag("offline_mode_switch")
                        )
                    }
                }
            }

            // --- SINKRONISASI CLOUD CARD ---
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Sinkronisasi Cloud Zorify",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Sinkronisasikan data lagu, daftar putar, histori, dan favorit Anda antara aplikasi Android ini dengan versi Web.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp),
                            style = LocalTextStyle.current.copy(lineHeight = 15.sp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = serverUrlField,
                            onValueChange = {
                                serverUrlField = it
                                viewModel.updateSyncServerUrl(it)
                            },
                            label = { Text("Alamat Server Sinkronisasi") },
                            modifier = Modifier.fillMaxWidth().testTag("sync_server_url_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1DB954),
                                unfocusedBorderColor = Color.DarkGray,
                                focusedLabelColor = Color(0xFF1DB954),
                                unfocusedLabelColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Terakhir Sinkron: $lastSyncedTime",
                            color = if (lastSyncedTime.contains("Belum")) Color.LightGray else Color(0xFF1DB954),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.syncWithCloud() },
                            enabled = !isSyncing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("sync_now_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1DB954),
                                disabledContainerColor = Color.DarkGray
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Menyinkronkan...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            } else {
                                Text("SINKRONKAN SEKARANG", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // --- EDIT PROFILE CARD (CRUD) ---
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Pengaturan Profil",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = editUsername,
                            onValueChange = { editUsername = it },
                            label = { Text("Ubah Nama Pengguna") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF1DB954),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Avatar Colors Selection Grid
                        Text(
                            text = "Pilih Warna Aksen Avatar:",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        val colorsList = listOf("#1DB954", "#18D3C4", "#FF4F6C", "#9D4EDD", "#FF9F1C", "#2EC4B6")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            colorsList.forEach { colorHex ->
                                val color = Color(android.graphics.Color.parseColor(colorHex))
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable { selectedColorHex = colorHex }
                                        .testTag("avatar_color_$colorHex")
                                ) {
                                    if (selectedColorHex == colorHex) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.Black,
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (editUsername.isNotBlank()) {
                                    viewModel.updateUserProfile(editUsername, selectedColorHex)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .align(Alignment.End)
                                .height(38.dp)
                        ) {
                            Text("Simpan Perubahan", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // --- ADMIN DASHBOARD ACCESS CARD ---
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.navigateTo(Screen.ADMIN) }
                        .testTag("admin_dashboard_card"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F3E2F)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1DB954)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Admin Shield",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Akses Dashboard Admin",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tambah, ubah, atau hapus lagu dari database lokal Zorify Anda secara langsung.",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp),
                                style = LocalTextStyle.current.copy(lineHeight = 14.sp)
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFF1DB954)
                        )
                    }
                }
            }

            // --- ACCOUNT LOG OUT CARD ---
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Sinkronisasi Berhasil",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Semua playlist, lagu disukai, dan riwayat mutar telah dicadangkan di penyimpanan lokal.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            style = LocalTextStyle.current.copy(lineHeight = 15.sp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { viewModel.logout() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("logout_button")
                        ) {
                            Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Keluar", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("KELUAR DARI AKUN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(140.dp))
        }
    }

    // --- GOOGLE ACCOUNT CHOOSER DIALOG ---
    if (showGoogleChooser) {
        AlertDialog(
            onDismissRequest = { showGoogleChooser = false },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGoogleChooser = false }) {
                    Text("Batal", color = Color(0xFF1DB954))
                }
            },
            containerColor = Color(0xFF1C1B1F),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 32.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Pilih Akun Google",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "untuk melanjutkan ke Zorify",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!showEmailInput) {
                        // Account 1: User's actual Google Account from metadata
                        Card(
                            onClick = {
                                viewModel.loginWithGoogle("zzora7174@gmail.com")
                                showGoogleChooser = false
                            },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2B30)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1DB954)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Z", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Zora User", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("zzora7174@gmail.com", color = Color.LightGray, fontSize = 12.sp)
                                }
                            }
                        }

                        // Account 2: Preloaded demo account
                        Card(
                            onClick = {
                                viewModel.loginWithGoogle("zorify.music@gmail.com")
                                showGoogleChooser = false
                            },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2B30)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF9D4EDD)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Z", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Zorify Premium", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("zorify.music@gmail.com", color = Color.LightGray, fontSize = 12.sp)
                                }
                            }
                        }

                        // Option to sign in with a different Google account
                        TextButton(
                            onClick = { showEmailInput = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Gunakan akun lain", color = Color(0xFF1DB954), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Input form for custom Google Email
                        Text("Masukkan Email Google Anda:", color = Color.White, fontSize = 14.sp)
                        OutlinedTextField(
                            value = customGoogleEmail,
                            onValueChange = { customGoogleEmail = it },
                            placeholder = { Text("nama@gmail.com", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF1DB954),
                                unfocusedBorderColor = Color.Gray
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { showEmailInput = false }) {
                                Text("Kembali", color = Color.Gray)
                            }
                            Button(
                                onClick = {
                                    if (customGoogleEmail.contains("@")) {
                                        viewModel.loginWithGoogle(customGoogleEmail.trim())
                                        showGoogleChooser = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
                            ) {
                                Text("Masuk", color = Color.Black)
                            }
                        }
                    }
                }
            }
        )
    }
}
