package id.smartkids.screenguard.core

enum class ProtectionLevel(val level: Int, val title: String) {
    NONE(0, "Aman"),
    LEVEL_1_WARNING(1, "Level 1 - Warning (Animasi & Suara)"),
    LEVEL_2_SCREEN_ALERT(2, "Level 2 - Screen Alert (Layar Penuh)"),
    LEVEL_3_REST_MODE(3, "Level 3 - Rest Mode (Kunci Istirahat)")
}

enum class AlertCategory {
    NONE,
    SLEEP_DETECTED,
    DISTANCE_TOO_CLOSE,
    BAD_POSTURE,
    EYE_REST_20_20_20
}

enum class PostureStatus(val label: String) {
    GOOD("Tegak & Ideal"),
    SLOUCHING("Membungkuk"),
    HEAD_TILT_DOWN("Kepala Terlalu Menunduk"),
    NECK_TILT("Kemiringan Leher Kurang Baik"),
    UNKNOWN("Menunggu Deteksi")
}

data class VisionTelemetry(
    val cameraActive: Boolean = false,
    val faceDetected: Boolean = false,
    val lowLightDetected: Boolean = false,
    val estimatedDistanceCm: Float = 0f,
    val leftEyeOpenProb: Float = 1f,
    val rightEyeOpenProb: Float = 1f,
    val headPitchX: Float = 0f,
    val headYawY: Float = 0f,
    val headRollZ: Float = 0f,
    val postureStatus: PostureStatus = PostureStatus.UNKNOWN,
    val sleepSuspected: Boolean = false,
    val eyeClosedDurationMs: Long = 0L,
    val activeAlert: AlertCategory = AlertCategory.NONE,
    val activeProtectionLevel: ProtectionLevel = ProtectionLevel.NONE,
    val restCountdownSec: Int = 0
)

data class GuardSettings(
    val sleepDetectionEnabled: Boolean = true,
    val distanceGuardEnabled: Boolean = true,
    val postureGuardEnabled: Boolean = true,
    val eyeRestEnabled: Boolean = true,
    val minDistanceCm: Float = 30f,
    val cameraFocalCalibration: Float = 620f,
    val sleepSensitivitySec: Int = 6,
    val aiSensitivityMultiplier: Float = 1.0f,
    val eyeRestIntervalMinutes: Int = 20,
    val eyeRestDurationSeconds: Int = 20,
    val dailyLimitMinutes: Int = 120,
    val bedtimeHour: Int = 21,
    val bedtimeMinute: Int = 0,
    val soundVolume: Float = 0.8f,
    val maxProtectionLevel: Int = 3
)

object AlertMessages {
    const val SLEEP_ALERT =
        "Wah, sepertinya kamu mengantuk! Yuk, istirahat dulu. Letakkan HP dan tidur dengan nyaman."
    const val DISTANCE_ALERT =
        "Hei, jangan terlalu dekat! Yuk, mundurkan HP-mu. Jaga jarak minimal 30 cm dari mata."
    const val POSTURE_ALERT =
        "Yuk, duduk yang tegak! Jangan membungkuk saat bermain HP."
    const val EYE_REST_ALERT =
        "Matamu juga perlu istirahat. Yuk, lihat jauh sebentar!"
}
