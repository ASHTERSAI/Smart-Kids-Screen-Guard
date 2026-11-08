package id.smartkids.screenguard.sleep_detection

import kotlin.math.abs

data class SleepEvaluationResult(
    val isSleepSuspected: Boolean,
    val continuousDrowsyDurationMs: Long,
    val reason: String
)

class SleepDetectionEngine {

    private var drowsyStartTimestampMs: Long? = null
    private var lastNoseY: Float? = null
    private var staticFaceStartMs: Long? = null

    /**
     * Mengevaluasi kemungkinan anak tertidur:
     * 1. Mata tertutup berkelanjutan (bukan kedipan mata sesaat di bawah 1.5 detik).
     * 2. Kepala tertunduk (headPitchX < -15 derajat) atau miring ekstrem.
     * 3. Wajah tidak menunjukkan aktivitas gerakan mikro dalam rentang waktu tertentu.
     */
    fun evaluateFrame(
        leftEyeOpenProb: Float?,
        rightEyeOpenProb: Float?,
        headPitchX: Float,
        headRollZ: Float,
        noseY: Float?,
        nowMillis: Long,
        thresholdSeconds: Int = 6,
        sensitivityMultiplier: Float = 1.0f
    ): SleepEvaluationResult {
        val eyeClosedThreshold = (0.28f * sensitivityMultiplier).coerceIn(0.15f, 0.45f)
        val bothEyesClosed = leftEyeOpenProb != null && rightEyeOpenProb != null &&
            leftEyeOpenProb < eyeClosedThreshold && rightEyeOpenProb < eyeClosedThreshold

        val headNoddingDown = headPitchX < (-14f / sensitivityMultiplier) || abs(headRollZ) > 28f

        val isFaceMotionless = if (noseY != null && lastNoseY != null) {
            val delta = abs(noseY - lastNoseY!!)
            if (delta < 2.0f) {
                if (staticFaceStartMs == null) staticFaceStartMs = nowMillis
                (nowMillis - (staticFaceStartMs ?: nowMillis)) > 4000L
            } else {
                staticFaceStartMs = null
                false
            }
        } else {
            false
        }
        lastNoseY = noseY

        val drowsySignal = bothEyesClosed || (headNoddingDown && isFaceMotionless)

        if (drowsySignal) {
            if (drowsyStartTimestampMs == null) {
                drowsyStartTimestampMs = nowMillis
            }
            val elapsedMs = nowMillis - (drowsyStartTimestampMs ?: nowMillis)
            val requiredMs = (thresholdSeconds * 1000L / sensitivityMultiplier).toLong().coerceAtLeast(2500L)
            val sleepSuspected = elapsedMs >= requiredMs
            val reason = when {
                bothEyesClosed && headNoddingDown -> "Mata tertutup & kepala tertunduk"
                bothEyesClosed -> "Mata tertutup terus-menerus"
                else -> "Kepala tertunduk tanpa aktivitas wajah"
            }
            return SleepEvaluationResult(
                isSleepSuspected = sleepSuspected,
                continuousDrowsyDurationMs = elapsedMs,
                reason = reason
            )
        } else {
            drowsyStartTimestampMs = null
            return SleepEvaluationResult(
                isSleepSuspected = false,
                continuousDrowsyDurationMs = 0L,
                reason = "Aktif / Terjaga"
            )
        }
    }

    fun reset() {
        drowsyStartTimestampMs = null
        staticFaceStartMs = null
        lastNoseY = null
    }
}
