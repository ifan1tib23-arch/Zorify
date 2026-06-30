package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ZorifyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(viewModel: ZorifyViewModel, modifier: Modifier = Modifier) {
    var isRegisterMode by remember { mutableStateOf(true) } // default is Register as requested: "buat akun dulu jika pengguna baru"
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var showGoogleChooser by remember { mutableStateOf(false) }
    var customGoogleEmail by remember { mutableStateOf("") }
    var showEmailInput by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F2E1B), // very deep green
                        Color(0xFF121212), // pitch black
                        Color(0xFF121212)
                    )
                )
            )
            .padding(24.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 450.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- GREEN SPOTIFY-LIKE LOGO ---
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1DB954)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Zorify Logo",
                    tint = Color.Black,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- HEADINGS ---
            Text(
                text = "Zorify",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp
            )

            Text(
                text = "Alunan Musik Tanpa Batas Tanpa Hambatan",
                color = Color.LightGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- TITLE STATE INDICATOR ---
            Text(
                text = if (isRegisterMode) "Buat Akun Baru" else "Masuk ke Akun Anda",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            // --- INPUT FIELDS ---
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Nama Pengguna (Username)", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "User", tint = Color.LightGray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF1DB954),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color(0xFF1DB954)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_username_field")
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Kata Sandi (Password)", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password", tint = Color.LightGray) },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isPasswordVisible) "Sembunyikan sandi" else "Tampilkan sandi",
                            tint = Color.LightGray
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF1DB954),
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color(0xFF1DB954)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_password_field")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // --- SUBMIT BUTTON ---
            Button(
                onClick = {
                    if (isRegisterMode) {
                        viewModel.register(username, password) {
                            // Automatically login after successful registration!
                            viewModel.login(username, password)
                        }
                    } else {
                        viewModel.login(username, password)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("auth_submit_button")
            ) {
                Text(
                    text = if (isRegisterMode) "Daftar Akun" else "Masuk",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- DIVIDER OR ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray.copy(alpha = 0.3f))
                Text(
                    text = " ATAU ",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray.copy(alpha = 0.3f))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- GOOGLE SIGN IN BUTTON ---
            Button(
                onClick = { showGoogleChooser = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("google_login_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "G ",
                        color = Color(0xFF4285F4),
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "Masuk dengan Google",
                        color = Color(0xFF121212),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- TOGGLE AUTH MODE ---
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isRegisterMode) "Sudah punya akun? " else "Pengguna baru? ",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
                Text(
                    text = if (isRegisterMode) "Masuk di sini" else "Daftar dulu",
                    color = Color(0xFF1DB954),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable {
                            isRegisterMode = !isRegisterMode
                        }
                        .testTag("auth_toggle_mode_button")
                )
            }

            // Quick note on pre-made account so they don't get stuck
            Text(
                text = "Tersedia akun default: zora (sandi: 1234)",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
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
