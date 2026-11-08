package id.smartkids.screenguard.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import id.smartkids.screenguard.animation.*
import id.smartkids.screenguard.core.*
import id.smartkids.screenguard.parent_dashboard.MainViewModel
import id.smartkids.screenguard.vision.CameraVisionAnalyzer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

enum class AppScreen {
    SPLASH,
    ONBOARDING,
    PARENT_REGISTER,
    PARENT_LOGIN,
    PARENT_DASHBOARD,
    CHILD_MODE,
    PARENT_SECURITY_CENTER,
    USAGE_REPORT,
    SETTINGS
}

@Composable
fun SmartKidsScreenGuardRoot(viewModel: MainViewModel) {
    val context = LocalContext.current
    val telemetry by viewModel.telemetry.collectAsState()
    val settings by viewModel.settingsState.collectAsState()

    var currentScreen by remember { mutableStateOf(AppScreen.SPLASH) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            viewModel.startMonitoringForegroundService()
        }
    }

    // Aktifkan CameraX ImageAnalysis di latar aplikasi saat izin kamera diberikan
    if (hasCameraPermission) {
        HeadlessCameraVisionBinder(
            onFrame = { frame -> viewModel.onVisionFrameReceived(frame) }
        )
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF00838F),
            secondary = Color(0xFF7E57C2),
            tertiary = Color(0xFFEF6C00),
            background = Color(0xFFF7FBFD),
            surface = Color.White
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (currentScreen) {
                AppScreen.SPLASH -> SplashScreen(
                    onFinished = {
                        currentScreen = if (viewModel.authManager.isParentRegistered()) {
                            AppScreen.CHILD_MODE
                        } else {
                            AppScreen.ONBOARDING
                        }
                    }
                )

                AppScreen.ONBOARDING -> OnboardingScreen(
                    hasCameraPermission = hasCameraPermission,
                    onRequestCamera = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onContinue = {
                        currentScreen = if (viewModel.authManager.isParentRegistered()) {
                            AppScreen.PARENT_LOGIN
                        } else {
                            AppScreen.PARENT_REGISTER
                        }
                    }
                )

                AppScreen.PARENT_REGISTER -> ParentRegistrationScreen(
                    viewModel = viewModel,
                    onRegistered = { currentScreen = AppScreen.PARENT_DASHBOARD }
                )

                AppScreen.PARENT_LOGIN -> ParentLoginScreen(
                    viewModel = viewModel,
                    onAuthenticated = { currentScreen = AppScreen.PARENT_DASHBOARD },
                    onBackToChildMode = { currentScreen = AppScreen.CHILD_MODE }
                )

                AppScreen.PARENT_DASHBOARD -> ParentDashboardScreen(
                    viewModel = viewModel,
                    hasCameraPermission = hasCameraPermission,
                    onRequestCameraPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onNavigateChildMode = { currentScreen = AppScreen.CHILD_MODE },
                    onNavigateSecurityCenter = { currentScreen = AppScreen.PARENT_SECURITY_CENTER },
                    onNavigateUsageReport = { currentScreen = AppScreen.USAGE_REPORT },
                    onNavigateSettings = { currentScreen = AppScreen.SETTINGS }
                )

                AppScreen.CHILD_MODE -> ChildModeScreen(
                    telemetry = telemetry,
                    settings = settings,
                    onParentAccessClick = { currentScreen = AppScreen.PARENT_LOGIN },
                    onSimulateAlert = { cat, lvl -> viewModel.simulateAlertForDemoOrTest(cat, lvl) }
                )

                AppScreen.PARENT_SECURITY_CENTER -> ParentSecurityCenterScreen(
                    viewModel = viewModel,
                    hasCameraPermission = hasCameraPermission,
                    onBack = { currentScreen = AppScreen.PARENT_DASHBOARD }
                )

                AppScreen.USAGE_REPORT -> UsageReportScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = AppScreen.PARENT_DASHBOARD }
                )

                AppScreen.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = AppScreen.PARENT_DASHBOARD }
                )
            }

            // Overlay Peringatan Otomatis (Sleep Alert, Distance Alert, Posture Alert, Eye Rest Alert)
            if (telemetry.activeAlert != AlertCategory.NONE) {
                ActiveSmartAlertOverlay(
                    telemetry = telemetry,
                    onDismiss = { viewModel.acknowledgeAndDismissAlert() }
                )
            }
        }
    }
}

@Composable
fun HeadlessCameraVisionBinder(onFrame: (id.smartkids.screenguard.vision.RawVisionFrameData) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val cameraProvider = providerFuture.get()
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, CameraVisionAnalyzer(onFrame))
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    imageAnalysis
                )
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraExecutor.shutdown()
        }
    }
}

/**
 * 1. SPLASH SCREEN
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1500L)
        onFinished()
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        KikoEyeGuardianAnimation()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "SMART KIDS SCREEN GUARD",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Pelindung Mata, Postur, & Tidur Anak Berbasis AI Lokal",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 2. ONBOARDING SCREEN
 */
@Composable
fun OnboardingScreen(
    hasCameraPermission: Boolean,
    onRequestCamera: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Selamat Datang di Smart Kids Screen Guard",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        KikoEyeGuardianAnimation()
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Jaminan Privasi 100% On-Device:", fontWeight = FontWeight.Bold)
                Text("• Tidak merekam video dan tidak menyimpan foto wajah anak.")
                Text("• Seluruh analisis AI (Jarak 30 cm, Postur, Tidur, & Istirahat Mata 20-20-20) diproses langsung di HP tanpa server cloud.")
                Text("• Dilengkapi indikator kamera aktif dan Parent Lock terenkripsi Android Keystore.")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRequestCamera,
            enabled = !hasCameraPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (hasCameraPermission) "Izin Kamera Sudah Aktif" else "Berikan Izin Kamera Depan")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Lanjut ke Pengaturan Akun Orang Tua")
        }
    }
}

/**
 * 3. REGISTRASI ORANG TUA
 */
@Composable
fun ParentRegistrationScreen(viewModel: MainViewModel, onRegistered: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var recoveryAnswer by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Registrasi Akun Orang Tua (Parent Lock)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text("Kredensial dienkripsi menggunakan Android Keystore & PBKDF2-HMAC-SHA256.")

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username Orang Tua") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (Minimal 6 Karakter)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Konfirmasi Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 8) pin = it.filter { ch -> ch.isDigit() } },
            label = { Text("PIN Keamanan (Minimal 6 Digit Angka)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 8) confirmPin = it.filter { ch -> ch.isDigit() } },
            label = { Text("Konfirmasi PIN 6 Digit") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = recoveryAnswer,
            onValueChange = { recoveryAnswer = it },
            label = { Text("Kode/Jawaban Pemulihan Akun (Misal: Nama Kota Lahir)") },
            modifier = Modifier.fillMaxWidth()
        )

        errorMessage?.let {
            Text(text = it, color = Color.Red, fontWeight = FontWeight.SemiBold)
        }

        Button(
            onClick = {
                val res = viewModel.authManager.registerParentAccount(
                    username = username,
                    password = password,
                    confirmPassword = confirmPassword,
                    pin = pin,
                    confirmPin = confirmPin,
                    recoveryQuestionAnswer = recoveryAnswer
                )
                res.onSuccess {
                    viewModel.logSecurityAccess("REGISTER_PARENT", true, "Akun orang tua dibuat")
                    viewModel.startMonitoringForegroundService()
                    onRegistered()
                }.onFailure { err ->
                    errorMessage = err.message
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Simpan Akun Orang Tua & Aktifkan Perlindungan")
        }
    }
}

/**
 * 4. LOGIN ORANG TUA
 */
@Composable
fun ParentLoginScreen(
    viewModel: MainViewModel,
    onAuthenticated: () -> Unit,
    onBackToChildMode: () -> Unit
) {
    var secretInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showRecoveryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Autentikasi Orang Tua", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Masukkan PIN 6 Digit atau Password untuk membuka Dashboard Orang Tua", textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = secretInput,
            onValueChange = { secretInput = it },
            label = { Text("PIN 6 Digit / Password Orang Tua") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        errorMsg?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = Color.Red)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (viewModel.authManager.isLockedOut()) {
                    val sec = viewModel.authManager.getRemainingLockoutSeconds()
                    errorMsg = "Terlalu banyak percobaan salah. Coba lagi dalam $sec detik."
                    viewModel.logSecurityAccess("LOGIN_LOCKED", false, "Percobaan saat lockout")
                    return@Button
                }
                val ok = viewModel.authManager.verifyPinOrPassword(secretInput)
                if (ok) {
                    viewModel.logSecurityAccess("LOGIN_PARENT", true, "Berhasil masuk ke Dashboard")
                    onAuthenticated()
                } else {
                    errorMsg = "PIN atau Password salah!"
                    viewModel.logSecurityAccess("LOGIN_PARENT", false, "PIN/Password salah")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Masuk ke Dashboard Orang Tua")
        }

        TextButton(onClick = { showRecoveryDialog = true }) {
            Text("Lupa PIN / Password? Gunakan Kode Pemulihan")
        }

        OutlinedButton(onClick = onBackToChildMode, modifier = Modifier.fillMaxWidth()) {
            Text("Kembali ke Mode Anak")
        }
    }

    if (showRecoveryDialog) {
        var recoveryAns by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var recStatus by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = { Text("Pemulihan Akun Orang Tua") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = recoveryAns,
                        onValueChange = { recoveryAns = it },
                        label = { Text("Jawaban Pemulihan Saat Registrasi") }
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it.filter { c -> c.isDigit() } },
                        label = { Text("PIN Baru (Minimal 6 Digit)") }
                    )
                    recStatus?.let { Text(it, color = Color.Red) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val res = viewModel.authManager.recoverAccountWithAnswer(recoveryAns, newPin)
                    res.onSuccess {
                        viewModel.logSecurityAccess("ACCOUNT_RECOVERY", true, "PIN direset melalui pemulihan")
                        showRecoveryDialog = false
                        onAuthenticated()
                    }.onFailure {
                        recStatus = it.message
                    }
                }) {
                    Text("Pulihkan & Masuk")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryDialog = false }) { Text("Batal") }
            }
        )
    }
}

/**
 * 5. PARENT DASHBOARD SCREEN
 */
@Composable
fun ParentDashboardScreen(
    viewModel: MainViewModel,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onNavigateChildMode: () -> Unit,
    onNavigateSecurityCenter: () -> Unit,
    onNavigateUsageReport: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val telemetry by viewModel.telemetry.collectAsState()
    val settings by viewModel.settingsState.collectAsState()
    val security by viewModel.securityState.collectAsState()
    val usageSeconds by viewModel.sessionUsageSeconds.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshSecurityState()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Dashboard Orang Tua", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Status Perlindungan Real-Time & Kontrol AI", style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = onNavigateChildMode,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Icon(Icons.Default.ChildCare, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Mode Anak")
            }
        }

        // Indikator Kamera & Privasi
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (hasCameraPermission) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(if (hasCameraPermission) Color(0xFF2E7D32) else Color.Red, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasCameraPermission) "Kamera Aktif (Pemrosesan AI Lokal)" else "Izin Kamera Belum Aktif",
                        fontWeight = FontWeight.Bold
                    )
                }
                Text("Deteksi Wajah: ${if (telemetry.faceDetected) "Terdeteksi" else "Wajah Tidak Terlihat di Kamera"}")
                if (telemetry.lowLightDetected) {
                    Text(
                        "Peringatan Cahaya: Pencahayaan ruangan redup/buruk, akurasi deteksi dapat berkurang.",
                        color = Color(0xFFE65100),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (!hasCameraPermission) {
                    Button(onClick = onRequestCameraPermission) {
                        Text("Aktifkan Izin Kamera Sekarang")
                    }
                }
            }
        }

        // Metrik Real-Time AI Computer Vision
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Jarak Wajah",
                value = if (telemetry.faceDetected) "${telemetry.estimatedDistanceCm.toInt()} cm" else "-- cm",
                subtitle = "Batas minimal: ${settings.minDistanceCm.toInt()} cm"
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Status Postur",
                value = telemetry.postureStatus.label,
                subtitle = "Pitch: ${telemetry.headPitchX.toInt()}° | Roll: ${telemetry.headRollZ.toInt()}°"
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Deteksi Tidur",
                value = if (telemetry.sleepSuspected) "Mengantuk!" else "Terjaga",
                subtitle = "Mata terbuka: ${(telemetry.leftEyeOpenProb * 100).toInt()}%"
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Durasi Layar",
                value = "${usageSeconds / 60} mnt ${usageSeconds % 60} dtk",
                subtitle = "Batas: ${settings.dailyLimitMinutes} menit/hari"
            )
        }

        // Status Keamanan & Jadwal
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Status Keamanan & Jadwal", fontWeight = FontWeight.Bold)
                Text("• Parent Lock: Aktif")
                Text("• Uninstall Protection: ${security.protectionModeSummary}")
                Text("• Pengingat Istirahat Mata (20-20-20): Setiap ${settings.eyeRestIntervalMinutes} menit selama ${settings.eyeRestDurationSeconds} detik")
                Text("• Jadwal Tidur Malam: Pukul ${String.format("%02d:00", settings.bedtimeHour)} WIB")
            }
        }

        // Menu Navigasi Orang Tua
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNavigateSettings, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Pengaturan AI")
            }
            Button(onClick = onNavigateUsageReport, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Assessment, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Laporan")
            }
        }

        Button(
            onClick = onNavigateSecurityCenter,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.Security, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Parent Security Center & Uninstall Protection")
        }

        // Panel Uji Cepat Seluruh Alert untuk QA / Orang Tua
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Simulasi & Uji Coba Peringatan Otomatis (QA Panel)", fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.simulateAlertForDemoOrTest(AlertCategory.DISTANCE_TOO_CLOSE, ProtectionLevel.LEVEL_2_SCREEN_ALERT) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Uji Jarak <30cm") }
                    OutlinedButton(
                        onClick = { viewModel.simulateAlertForDemoOrTest(AlertCategory.SLEEP_DETECTED, ProtectionLevel.LEVEL_3_REST_MODE) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Uji Tidur") }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.simulateAlertForDemoOrTest(AlertCategory.BAD_POSTURE, ProtectionLevel.LEVEL_1_WARNING) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Uji Postur") }
                    OutlinedButton(
                        onClick = { viewModel.triggerEyeRestCountdown(settings.eyeRestDurationSeconds) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Uji Mata 20-20-20") }
                }
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, value: String, subtitle: String) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * 6. CHILD MODE SCREEN (Tampilan Ramah Anak dengan Kiko, Bubu, & Lala — Tanpa Akses Pengaturan Keamanan)
 */
@Composable
fun ChildModeScreen(
    telemetry: VisionTelemetry,
    settings: GuardSettings,
    onParentAccessClick: () -> Unit,
    onSimulateAlert: (AlertCategory, ProtectionLevel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFDF7))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(Color(0xFF2E7D32), CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sahabat Layar Pintar Aktif", fontWeight = FontWeight.Bold, color = Color(0xFF00695C))
            }
            OutlinedButton(onClick = onParentAccessClick) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Orang Tua")
            }
        }

        Text(
            text = "Halo Anak Hebat! Yuk Bermain HP dengan Sehat!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            color = Color(0xFF37474F)
        )

        // Kartu Status Jarak Anak yang Ramah
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F7FA))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                KikoEyeGuardianAnimation()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (telemetry.faceDetected) {
                        "Jarak Matamu Sekarang: ${telemetry.estimatedDistanceCm.toInt()} cm (Target Aman: ${settings.minDistanceCm.toInt()}+ cm)"
                    } else {
                        "Kiko siap menjaga matamu tetap sehat!"
                    },
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Kartu Bubu & Lala
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6))
            ) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BubuSleepGuardianAnimation()
                    Text("Ingatkan saat mengantuk", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
            ) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Postur Dudukmu:", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    Text(telemetry.postureStatus.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text("Bersama Lala si Pengingat Duduk", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        }

        // Tombol Edukasi Anak (Tombol Besar Warna Pastel)
        Button(
            onClick = { onSimulateAlert(AlertCategory.EYE_REST_20_20_20, ProtectionLevel.LEVEL_2_SCREEN_ALERT) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26A69A))
        ) {
            Text("Latihan Istirahat Mata 20 Detik Bersama Kiko", style = MaterialTheme.typography.titleMedium)
        }

        Button(
            onClick = { onSimulateAlert(AlertCategory.BAD_POSTURE, ProtectionLevel.LEVEL_1_WARNING) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA726))
        ) {
            Text("Lihat Cara Duduk Tegak Bersama Lala", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * 7, 8, 9, 10. SLEEP ALERT, DISTANCE ALERT, POSTURE ALERT, & EYE REST ALERT OVERLAY
 * Mendukung Level 1 (Warning Banner), Level 2 (Screen Alert), dan Level 3 (Rest Mode)
 */
@Composable
fun ActiveSmartAlertOverlay(
    telemetry: VisionTelemetry,
    onDismiss: () -> Unit
) {
    val isFullScreen = telemetry.activeProtectionLevel == ProtectionLevel.LEVEL_2_SCREEN_ALERT ||
        telemetry.activeProtectionLevel == ProtectionLevel.LEVEL_3_REST_MODE
    val isRestMode = telemetry.activeProtectionLevel == ProtectionLevel.LEVEL_3_REST_MODE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isFullScreen) Color(0xF5FFFFFF) else Color(0x66000000))
            .padding(20.dp),
        contentAlignment = if (isFullScreen) Alignment.Center else Alignment.TopCenter
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = telemetry.activeProtectionLevel.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                when (telemetry.activeAlert) {
                    AlertCategory.SLEEP_DETECTED -> {
                        BubuSleepGuardianAnimation()
                        Text(
                            text = AlertMessages.SLEEP_ALERT,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    AlertCategory.DISTANCE_TOO_CLOSE -> {
                        KikoEyeGuardianAnimation()
                        Text(
                            text = AlertMessages.DISTANCE_ALERT,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        if (telemetry.estimatedDistanceCm > 0f) {
                            Text(
                                text = "Jarak terdeteksi saat ini: ${telemetry.estimatedDistanceCm.toInt()} cm",
                                color = Color(0xFFD32F2F),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    AlertCategory.BAD_POSTURE -> {
                        LalaPostureGuardianAnimation()
                        Text(
                            text = AlertMessages.POSTURE_ALERT,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    AlertCategory.EYE_REST_20_20_20 -> {
                        KikoEyeGuardianAnimation(isResting = true)
                        Text(
                            text = AlertMessages.EYE_REST_ALERT,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Metode 20-20-20: Lihat objek sejauh 6 meter selama ${telemetry.restCountdownSec} detik.",
                            textAlign = TextAlign.Center
                        )
                    }

                    AlertCategory.NONE -> {}
                }

                if (telemetry.restCountdownSec > 0) {
                    Surface(
                        color = Color(0xFFEDE7F6),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Durasi Istirahat: ${telemetry.restCountdownSec} detik",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF4A148C)
                        )
                    }
                }

                val canDismiss = !isRestMode || telemetry.restCountdownSec == 0
                Button(
                    onClick = onDismiss,
                    enabled = canDismiss,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (canDismiss) "Saya Sudah Mengerti" else "Tunggu Durasi Istirahat Selesai...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 11. PARENT SECURITY CENTER SCREEN
 */
@Composable
fun ParentSecurityCenterScreen(
    viewModel: MainViewModel,
    hasCameraPermission: Boolean,
    onBack: () -> Unit
) {
    val security by viewModel.securityState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val audits by viewModel.securityAuditsFlow.collectAsState()

    val adminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshSecurityState()
    }

    var currentAuthInput by remember { mutableStateOf("") }
    var newSecretInput by remember { mutableStateOf("") }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
            Text("Parent Security Center", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Status Keamanan Sistem", fontWeight = FontWeight.Bold)
                Text("• Status Akun Orang Tua: Terdaftar & Terenkripsi Keystore")
                Text("• Status Parent Lock: Aktif")
                Text("• Status Device Admin: ${if (security.isDeviceAdminActive) "Aktif" else "Tidak Aktif"}")
                Text("• Status Device Owner: ${if (security.isDeviceOwnerActive) "Aktif (Level Sistem)" else "Tidak Aktif (APK Standar)"}")
                Text("• Status Uninstall Protection: ${if (security.isUninstallBlocked) "Terkunci (Device Owner)" else "Standar"}")
                Text("• Status Izin Kamera: ${if (hasCameraPermission) "Diizinkan" else "Dicabut / Belum Diizinkan"}")
                Text("• Status Layanan Pemantauan: ${if (telemetry.cameraActive) "Berjalan" else "Berhenti"}")
            }
        }

        // Kontrol Device Admin & Device Owner
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Konfigurasi Uninstall Protection Resmi Android", fontWeight = FontWeight.Bold)
                Text(
                    "Catatan Keterbatasan APK Standar: Pada Android modern, aplikasi yang diinstal via APK biasa hanya dapat mengaktifkan Device Admin. Pencegahan uninstall mutlak di level OS memerlukan aktivasi Device Owner melalui perintah ADB.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = { adminLauncher.launch(viewModel.deviceOwnerManager.createEnableDeviceAdminIntent()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Aktifkan Device Admin Android")
                }

                if (security.isDeviceOwnerActive) {
                    Button(
                        onClick = {
                            val res = viewModel.deviceOwnerManager.setUninstallProtection(true)
                            feedbackMessage = res.getOrElse { it.message }
                            viewModel.refreshSecurityState()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Aktifkan Blokir Uninstall (Device Owner)")
                    }
                }
            }
        }

        // Ubah Password / PIN & Lepaskan Perlindungan (Wajib Konfirmasi Password/PIN)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ubah Kredensial & Pelepasan Perlindungan", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = currentAuthInput,
                    onValueChange = { currentAuthInput = it },
                    label = { Text("Konfirmasi Password / PIN Saat Ini") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newSecretInput,
                    onValueChange = { newSecretInput = it },
                    label = { Text("Password Baru atau PIN 6 Digit Baru") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val res = viewModel.authManager.changePassword(currentAuthInput, newSecretInput)
                            feedbackMessage = res.fold(
                                onSuccess = {
                                    viewModel.logSecurityAccess("CHANGE_PASSWORD", true, "Password diubah")
                                    "Password berhasil diperbarui."
                                },
                                onFailure = { it.message }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Ubah Password") }

                    Button(
                        onClick = {
                            val res = viewModel.authManager.changePin(currentAuthInput, newSecretInput)
                            feedbackMessage = res.fold(
                                onSuccess = {
                                    viewModel.logSecurityAccess("CHANGE_PIN", true, "PIN diubah")
                                    "PIN berhasil diperbarui."
                                },
                                onFailure = { it.message }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Ubah PIN") }
                }

                Button(
                    onClick = {
                        if (!viewModel.authManager.verifyPinOrPassword(currentAuthInput)) {
                            feedbackMessage = "Konfirmasi password/PIN salah! Pelepasan perlindungan ditolak."
                            viewModel.logSecurityAccess("RELEASE_PROTECTION", false, "Gagal verifikasi password")
                        } else {
                            val res = viewModel.deviceOwnerManager.releaseAllProtections()
                            viewModel.stopMonitoringForegroundService()
                            viewModel.refreshSecurityState()
                            viewModel.logSecurityAccess("RELEASE_PROTECTION", true, "Proteksi dilepaskan orang tua")
                            feedbackMessage = res.getOrElse { it.message }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Lepaskan Seluruh Perlindungan & Izinkan Uninstall")
                }

                feedbackMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Riwayat Percobaan Akses
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Riwayat Percobaan Akses Keamanan", fontWeight = FontWeight.Bold)
                if (audits.isEmpty()) {
                    Text("Belum ada riwayat akses.")
                } else {
                    audits.take(10).forEach { item ->
                        val timeStr = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
                        Text(
                            text = "[$timeStr] ${item.action} - ${if (item.success) "BERHASIL" else "GAGAL"} (${item.note})",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.success) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 12. USAGE REPORT SCREEN (Laporan Harian & Mingguan dari Room Database)
 */
@Composable
fun UsageReportScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val events by viewModel.recentEventsFlow.collectAsState()
    val peakHour by viewModel.peakHour.collectAsState()
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    val todayEvents = events.filter { it.dateString == todayStr }
    val todayMinutes = todayEvents.count { it.eventType == "USAGE_MINUTE" }
    val todayDistanceAlerts = todayEvents.count { it.eventType == "DISTANCE" }
    val todayPostureAlerts = todayEvents.count { it.eventType == "POSTURE" }
    val todayEyeRestAlerts = todayEvents.count { it.eventType == "EYE_REST" }
    val todaySleepAlerts = todayEvents.count { it.eventType == "SLEEP" }

    val weeklyMinutes = events.count { it.eventType == "USAGE_MINUTE" }
    val weeklyDistanceAlerts = events.count { it.eventType == "DISTANCE" }
    val weeklyPostureAlerts = events.count { it.eventType == "POSTURE" }
    val weeklyEyeRestAlerts = events.count { it.eventType == "EYE_REST" }
    val weeklySleepAlerts = events.count { it.eventType == "SLEEP" }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
            Text("Laporan Penggunaan Harian & Mingguan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Ringkasan Hari Ini ($todayStr)", fontWeight = FontWeight.Bold)
                Text("• Durasi Penggunaan: $todayMinutes menit")
                Text("• Peringatan Jarak Dekat (<30 cm): $todayDistanceAlerts kali")
                Text("• Peringatan Postur Duduk: $todayPostureAlerts kali")
                Text("• Pengingat Istirahat Mata (20-20-20): $todayEyeRestAlerts kali")
                Text("• Indikasi Kemungkinan Tertidur: $todaySleepAlerts kali")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Ringkasan 7 Hari Terakhir", fontWeight = FontWeight.Bold)
                Text("• Total Durasi Penggunaan: $weeklyMinutes menit")
                Text("• Total Peringatan Jarak Dekat: $weeklyDistanceAlerts kali")
                Text("• Total Peringatan Postur: $weeklyPostureAlerts kali")
                Text("• Total Pengingat Mata: $weeklyEyeRestAlerts kali")
                Text("• Total Indikasi Tertidur: $weeklySleepAlerts kali")
                Text("• Jam Penggunaan Tertinggi: ${peakHour?.let { String.format("%02d:00 - %02d:59", it, it) } ?: "Belum cukup data"}")
            }
        }

        Text("Log Riwayat Peringatan Terbaru (Lokal Tanpa Foto/Video):", fontWeight = FontWeight.Bold)
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(events.take(30)) { ev ->
                val timeFormatted = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(ev.timestamp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("[${ev.eventType}] ${ev.detail}", fontWeight = FontWeight.Medium)
                        Text(timeFormatted, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

/**
 * 13. SETTINGS SCREEN (Dilindungi Autentikasi Orang Tua)
 */
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val settings by viewModel.settingsState.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
            Text("Pengaturan Perlindungan & Kalibrasi AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Modul AI Computer Vision", fontWeight = FontWeight.Bold)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktifkan AI Sleep Detection")
                    Switch(
                        checked = settings.sleepDetectionEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.settingsRepo.updateSettings { it.copy(sleepDetectionEnabled = enabled) } }
                        }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktifkan Eye Distance Guard (30 cm)")
                    Switch(
                        checked = settings.distanceGuardEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.settingsRepo.updateSettings { it.copy(distanceGuardEnabled = enabled) } }
                        }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktifkan Posture Guard")
                    Switch(
                        checked = settings.postureGuardEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.settingsRepo.updateSettings { it.copy(postureGuardEnabled = enabled) } }
                        }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktifkan Pengingat Mata 20-20-20")
                    Switch(
                        checked = settings.eyeRestEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { viewModel.settingsRepo.updateSettings { it.copy(eyeRestEnabled = enabled) } }
                        }
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Parameter Jarak, Sensitivitas, & Kalibrasi Kamera", fontWeight = FontWeight.Bold)

                Text("Jarak Minimal Aman: ${settings.minDistanceCm.toInt()} cm")
                Slider(
                    value = settings.minDistanceCm,
                    onValueChange = { v ->
                        scope.launch { viewModel.settingsRepo.updateSettings { it.copy(minDistanceCm = v) } }
                    },
                    valueRange = 20f..45f
                )

                Text("Durasi Deteksi Mengantuk: ${settings.sleepSensitivitySec} detik")
                Slider(
                    value = settings.sleepSensitivitySec.toFloat(),
                    onValueChange = { v ->
                        scope.launch { viewModel.settingsRepo.updateSettings { it.copy(sleepSensitivitySec = v.toInt()) } }
                    },
                    valueRange = 3f..15f
                )

                Text("Interval Istirahat Mata (Metode 20-20-20): ${settings.eyeRestIntervalMinutes} menit")
                Slider(
                    value = settings.eyeRestIntervalMinutes.toFloat(),
                    onValueChange = { v ->
                        scope.launch { viewModel.settingsRepo.updateSettings { it.copy(eyeRestIntervalMinutes = v.toInt()) } }
                    },
                    valueRange = 5f..60f
                )

                Text("Volume Suara Peringatan: ${(settings.soundVolume * 100).toInt()}%")
                Slider(
                    value = settings.soundVolume,
                    onValueChange = { v ->
                        scope.launch { viewModel.settingsRepo.updateSettings { it.copy(soundVolume = v) } }
                    },
                    valueRange = 0.1f..1.0f
                )

                Button(
                    onClick = { viewModel.calibrateCameraAt30Cm() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Kalibrasi Kamera Saat Wajah Berjarak 30 cm")
                }
                Text(
                    "Catatan Akurasi: Estimasi jarak menggunakan lebar piksel wajah dari kamera depan tunggal (monokular). Hasil dapat dipengaruhi FOV lensa perangkat, kemiringan kepala, dan pencahayaan ruangan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}
