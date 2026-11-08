package id.smartkids.screenguard.parent_dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.smartkids.screenguard.core.*
import id.smartkids.screenguard.data.SettingsRepository
import id.smartkids.screenguard.database.ScreenGuardDatabase
import id.smartkids.screenguard.database.SecurityAuditEntity
import id.smartkids.screenguard.database.UsageEventEntity
import id.smartkids.screenguard.distance_detection.DistanceDetectionEngine
import id.smartkids.screenguard.notification.SoundAndTtsHelper
import id.smartkids.screenguard.posture_detection.PostureDetectionEngine
import id.smartkids.screenguard.screen_protection.ScreenGuardForegroundService
import id.smartkids.screenguard.security.DeviceOwnerManager
import id.smartkids.screenguard.security.DeviceSecurityState
import id.smartkids.screenguard.security.ParentAuthManager
import id.smartkids.screenguard.sleep_detection.SleepDetectionEngine
import id.smartkids.screenguard.vision.RawVisionFrameData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ScreenGuardDatabase.getInstance(application)
    private val dao = db.dao()
    val settingsRepo = SettingsRepository(application)
    val authManager = ParentAuthManager(application)
    val deviceOwnerManager = DeviceOwnerManager(application)
    private val soundHelper = SoundAndTtsHelper(application)

    private val sleepEngine = SleepDetectionEngine()
    private val distanceEngine = DistanceDetectionEngine()
    private val postureEngine = PostureDetectionEngine()

    val settingsState: StateFlow<GuardSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, GuardSettings())

    private val _telemetry = MutableStateFlow(VisionTelemetry())
    val telemetry: StateFlow<VisionTelemetry> = _telemetry.asStateFlow()

    private val _securityState = MutableStateFlow(deviceOwnerManager.getSecurityState())
    val securityState: StateFlow<DeviceSecurityState> = _securityState.asStateFlow()

    private val _sessionUsageSeconds = MutableStateFlow(0L)
    val sessionUsageSeconds: StateFlow<Long> = _sessionUsageSeconds.asStateFlow()

    private val _peakHour = MutableStateFlow<Int?>(null)
    val peakHour: StateFlow<Int?> = _peakHour.asStateFlow()

    private var lastEyeRestTriggerSec = 0L
    private var lastLoggedEventTimeMs = 0L
    private var lastObservedFaceWidthPx = 0f

    val recentEventsFlow = dao.observeEventsSince(System.currentTimeMillis() - 7 * 24 * 3600 * 1000L)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val securityAuditsFlow = dao.observeSecurityAudits()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        startSessionAndEyeRestTimers()
    }

    private fun startSessionAndEyeRestTimers() {
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                _sessionUsageSeconds.value += 1
                val totalSec = _sessionUsageSeconds.value
                val cfg = settingsState.value

                if (totalSec % 60L == 0L) {
                    recordUsageEvent("USAGE_MINUTE", "Penggunaan aktif 1 menit")
                    refreshPeakUsageHour()
                }

                // Evaluasi Metode 20-20-20 Eye Rest Reminder
                val intervalSec = (cfg.eyeRestIntervalMinutes * 60).coerceAtLeast(60)
                if (cfg.eyeRestEnabled &&
                    totalSec - lastEyeRestTriggerSec >= intervalSec &&
                    _telemetry.value.activeAlert == AlertCategory.NONE
                ) {
                    lastEyeRestTriggerSec = totalSec
                    triggerEyeRestCountdown(cfg.eyeRestDurationSeconds)
                }

                // Hitung mundur Rest Mode jika sedang aktif
                if (_telemetry.value.restCountdownSec > 0) {
                    val nextSec = _telemetry.value.restCountdownSec - 1
                    _telemetry.update { it.copy(restCountdownSec = nextSec) }
                }
            }
        }
    }

    fun triggerEyeRestCountdown(durationSeconds: Int) {
        val cfg = settingsState.value
        recordUsageEvent("EYE_REST", "Pengingat 20-20-20 dipicu")
        soundHelper.playGentleAlertAndSpeak(AlertMessages.EYE_REST_ALERT, cfg.soundVolume)
        _telemetry.update {
            it.copy(
                activeAlert = AlertCategory.EYE_REST_20_20_20,
                activeProtectionLevel = ProtectionLevel.LEVEL_2_SCREEN_ALERT,
                restCountdownSec = durationSeconds
            )
        }
    }

    fun onVisionFrameReceived(frame: RawVisionFrameData) {
        val now = System.currentTimeMillis()
        val cfg = settingsState.value

        if (!frame.faceDetected) {
            _telemetry.update {
                it.copy(
                    cameraActive = true,
                    faceDetected = false,
                    lowLightDetected = frame.lowLight,
                    estimatedDistanceCm = 0f,
                    postureStatus = PostureStatus.UNKNOWN
                )
            }
            return
        }

        lastObservedFaceWidthPx = frame.faceWidthPx

        // 1. Evaluasi Jarak Wajah (< 30 cm)
        val distResult = distanceEngine.evaluateDistance(
            faceWidthPx = frame.faceWidthPx,
            calibratedFocalLengthPx = cfg.cameraFocalCalibration,
            minAllowedDistanceCm = cfg.minDistanceCm,
            nowMillis = now,
            maxProtectionLevel = cfg.maxProtectionLevel
        )

        // 2. Evaluasi Kemungkinan Anak Tertidur
        val sleepResult = sleepEngine.evaluateFrame(
            leftEyeOpenProb = frame.leftEyeOpenProb,
            rightEyeOpenProb = frame.rightEyeOpenProb,
            headPitchX = frame.headPitchX,
            headRollZ = frame.headRollZ,
            noseY = frame.noseY,
            nowMillis = now,
            thresholdSeconds = cfg.sleepSensitivitySec,
            sensitivityMultiplier = cfg.aiSensitivityMultiplier
        )

        // 3. Evaluasi Postur Duduk
        val postureResult = postureEngine.evaluatePosture(
            headPitchX = frame.headPitchX,
            headRollZ = frame.headRollZ,
            shoulderSlopeDeg = frame.shoulderSlopeDeg,
            noseToShoulderVerticalRatio = frame.noseToShoulderRatio,
            nowMillis = now,
            sensitivityMultiplier = cfg.aiSensitivityMultiplier
        )

        // Tentukan prioritas peringatan tertinggi
        var nextAlert = _telemetry.value.activeAlert
        var nextLevel = _telemetry.value.activeProtectionLevel
        var countdown = _telemetry.value.restCountdownSec

        if (cfg.sleepDetectionEnabled && sleepResult.isSleepSuspected) {
            nextAlert = AlertCategory.SLEEP_DETECTED
            nextLevel = ProtectionLevel.LEVEL_3_REST_MODE
            if (countdown <= 0) countdown = 30
            soundHelper.playGentleAlertAndSpeak(AlertMessages.SLEEP_ALERT, cfg.soundVolume)
            throttledLogEvent("SLEEP", sleepResult.reason, now)
        } else if (cfg.distanceGuardEnabled && distResult.isTooClose && distResult.recommendedLevel != ProtectionLevel.NONE) {
            nextAlert = AlertCategory.DISTANCE_TOO_CLOSE
            nextLevel = distResult.recommendedLevel
            if (nextLevel == ProtectionLevel.LEVEL_3_REST_MODE && countdown <= 0) countdown = 15
            soundHelper.playGentleAlertAndSpeak(AlertMessages.DISTANCE_ALERT, cfg.soundVolume)
            throttledLogEvent("DISTANCE", "Jarak wajah ${distResult.estimatedDistanceCm.toInt()} cm (< ${cfg.minDistanceCm.toInt()} cm)", now)
        } else if (cfg.postureGuardEnabled && postureResult.isBadPostureTriggered) {
            nextAlert = AlertCategory.BAD_POSTURE
            nextLevel = ProtectionLevel.LEVEL_1_WARNING
            soundHelper.playGentleAlertAndSpeak(AlertMessages.POSTURE_ALERT, cfg.soundVolume)
            throttledLogEvent("POSTURE", postureResult.status.label, now)
        } else if (nextAlert != AlertCategory.EYE_REST_20_20_20 && countdown <= 0) {
            nextAlert = AlertCategory.NONE
            nextLevel = ProtectionLevel.NONE
        }

        _telemetry.update {
            it.copy(
                cameraActive = true,
                faceDetected = true,
                lowLightDetected = frame.lowLight,
                estimatedDistanceCm = distResult.estimatedDistanceCm,
                leftEyeOpenProb = frame.leftEyeOpenProb ?: 1f,
                rightEyeOpenProb = frame.rightEyeOpenProb ?: 1f,
                headPitchX = frame.headPitchX,
                headYawY = frame.headYawY,
                headRollZ = frame.headRollZ,
                postureStatus = postureResult.status,
                sleepSuspected = sleepResult.isSleepSuspected,
                eyeClosedDurationMs = sleepResult.continuousDrowsyDurationMs,
                activeAlert = nextAlert,
                activeProtectionLevel = nextLevel,
                restCountdownSec = countdown
            )
        }
    }

    fun acknowledgeAndDismissAlert() {
        sleepEngine.reset()
        distanceEngine.reset()
        postureEngine.reset()
        _telemetry.update {
            it.copy(
                activeAlert = AlertCategory.NONE,
                activeProtectionLevel = ProtectionLevel.NONE,
                restCountdownSec = 0
            )
        }
    }

    fun simulateAlertForDemoOrTest(category: AlertCategory, level: ProtectionLevel) {
        val cfg = settingsState.value
        val msg = when (category) {
            AlertCategory.SLEEP_DETECTED -> AlertMessages.SLEEP_ALERT
            AlertCategory.DISTANCE_TOO_CLOSE -> AlertMessages.DISTANCE_ALERT
            AlertCategory.BAD_POSTURE -> AlertMessages.POSTURE_ALERT
            AlertCategory.EYE_REST_20_20_20 -> AlertMessages.EYE_REST_ALERT
            AlertCategory.NONE -> ""
        }
        if (msg.isNotEmpty()) {
            soundHelper.playGentleAlertAndSpeak(msg, cfg.soundVolume)
        }
        _telemetry.update {
            it.copy(
                activeAlert = category,
                activeProtectionLevel = level,
                restCountdownSec = if (level == ProtectionLevel.LEVEL_3_REST_MODE || category == AlertCategory.EYE_REST_20_20_20) 20 else 0
            )
        }
    }

    fun calibrateCameraAt30Cm() {
        if (lastObservedFaceWidthPx > 15f) {
            val newFocal = distanceEngine.calibrateFocalLengthAtDistance(lastObservedFaceWidthPx, 30f)
            viewModelScope.launch {
                settingsRepo.updateSettings { it.copy(cameraFocalCalibration = newFocal) }
            }
        }
    }

    fun refreshSecurityState() {
        _securityState.value = deviceOwnerManager.getSecurityState()
    }

    fun logSecurityAccess(action: String, success: Boolean, note: String) {
        viewModelScope.launch {
            dao.insertSecurityAudit(
                SecurityAuditEntity(action = action, success = success, note = note)
            )
        }
    }

    private fun throttledLogEvent(type: String, detail: String, nowMillis: Long) {
        if (nowMillis - lastLoggedEventTimeMs >= 10_000L) {
            lastLoggedEventTimeMs = nowMillis
            recordUsageEvent(type, detail)
        }
    }

    private fun recordUsageEvent(type: String, detail: String) {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            dao.insertUsageEvent(
                UsageEventEntity(
                    dateString = dateStr,
                    hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
                    eventType = type,
                    detail = detail
                )
            )
        }
    }

    private fun refreshPeakUsageHour() {
        viewModelScope.launch {
            val stat = dao.getPeakUsageHour(System.currentTimeMillis() - 7 * 24 * 3600 * 1000L)
            _peakHour.value = stat?.hourOfDay
        }
    }

    fun startMonitoringForegroundService() {
        ScreenGuardForegroundService.startService(getApplication())
    }

    fun stopMonitoringForegroundService() {
        ScreenGuardForegroundService.stopService(getApplication())
        _telemetry.update { it.copy(cameraActive = false) }
    }

    override fun onCleared() {
        super.onCleared()
        soundHelper.shutdown()
    }
}
