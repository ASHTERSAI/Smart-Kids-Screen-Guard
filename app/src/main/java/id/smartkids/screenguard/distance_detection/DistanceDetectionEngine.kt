package id.smartkids.screenguard.distance_detection

import id.smartkids.screenguard.core.ProtectionLevel

data class DistanceEvaluationResult(
    val estimatedDistanceCm: Float,
    val isTooClose: Boolean,
    val recommendedLevel: ProtectionLevel,
    val violationDurationMs: Long
)

class DistanceDetectionEngine {

    companion object {
        // Rata-rata lebar wajah biometrik anak (antar tulang pipi / bounding box wajah) ~ 13.0 cm
        const val AVERAGE_CHILD_FACE_WIDTH_CM = 13.0f
    }

    private var violationStartMs: Long? = null
    private var smoothedDistanceCm: Float = 40f

    /**
     * Rumus Pinhole Camera terkalibrasi:
     * Distance (cm) = (Lebar Wajah Nyata (cm) * Focal Length Terkalibrasi (px)) / Lebar Wajah pada Frame (px)
     */
    fun estimateDistanceCm(
        faceBoundingBoxWidthPx: Float,
        calibratedFocalLengthPx: Float = 620f
    ): Float {
        if (faceBoundingBoxWidthPx <= 5f) return 0f
        val rawDistance = (AVERAGE_CHILD_FACE_WIDTH_CM * calibratedFocalLengthPx) / faceBoundingBoxWidthPx
        return rawDistance.coerceIn(10f, 120f)
    }

    /**
     * Menghitung konstanta focal length baru saat orang tua melakukan kalibrasi pada jarak 30 cm nyata.
     */
    fun calibrateFocalLengthAtDistance(
        measuredFaceWidthPx: Float,
        knownDistanceCm: Float = 30f
    ): Float {
        if (measuredFaceWidthPx <= 10f) return 620f
        return ((knownDistanceCm * measuredFaceWidthPx) / AVERAGE_CHILD_FACE_WIDTH_CM).coerceIn(300f, 1400f)
    }

    fun evaluateDistance(
        faceWidthPx: Float,
        calibratedFocalLengthPx: Float,
        minAllowedDistanceCm: Float = 30f,
        nowMillis: Long,
        maxProtectionLevel: Int = 3
    ): DistanceEvaluationResult {
        val instantCm = estimateDistanceCm(faceWidthPx, calibratedFocalLengthPx)
        smoothedDistanceCm = if (smoothedDistanceCm == 0f) {
            instantCm
        } else {
            (smoothedDistanceCm * 0.7f) + (instantCm * 0.3f)
        }

        val tooClose = smoothedDistanceCm in 1f..<minAllowedDistanceCm
        if (!tooClose) {
            violationStartMs = null
            return DistanceEvaluationResult(
                estimatedDistanceCm = smoothedDistanceCm,
                isTooClose = false,
                recommendedLevel = ProtectionLevel.NONE,
                violationDurationMs = 0L
            )
        }

        if (violationStartMs == null) {
            violationStartMs = nowMillis
        }
        val violationDuration = nowMillis - (violationStartMs ?: nowMillis)

        // Eskalasi bertahap:
        // >= 2 detik: Level 1 Warning
        // >= 7 detik: Level 2 Screen Alert
        // >= 15 detik: Level 3 Rest Mode
        val rawLevel = when {
            violationDuration >= 15_000L -> ProtectionLevel.LEVEL_3_REST_MODE
            violationDuration >= 7_000L -> ProtectionLevel.LEVEL_2_SCREEN_ALERT
            violationDuration >= 2_000L -> ProtectionLevel.LEVEL_1_WARNING
            else -> ProtectionLevel.NONE
        }

        val cappedLevel = when {
            rawLevel.level > maxProtectionLevel -> ProtectionLevel.entries.first { it.level == maxProtectionLevel }
            else -> rawLevel
        }

        return DistanceEvaluationResult(
            estimatedDistanceCm = smoothedDistanceCm,
            isTooClose = true,
            recommendedLevel = cappedLevel,
            violationDurationMs = violationDuration
        )
    }

    fun reset() {
        violationStartMs = null
    }
}
