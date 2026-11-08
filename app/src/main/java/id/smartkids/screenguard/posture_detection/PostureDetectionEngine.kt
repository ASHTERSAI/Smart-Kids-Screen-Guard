package id.smartkids.screenguard.posture_detection

import id.smartkids.screenguard.core.PostureStatus
import kotlin.math.abs

data class PostureEvaluationResult(
    val status: PostureStatus,
    val isBadPostureTriggered: Boolean,
    val badPostureDurationMs: Long
)

class PostureDetectionEngine {

    private var badPostureStartMs: Long? = null

    /**
     * Mengevaluasi postur duduk berdasarkan kombinasi:
     * - Sudut kemiringan leher samping (headRollZ)
     * - Sudut tunduk kepala (headPitchX)
     * - Ketidakseimbangan bahu kiri & kanan atau jarak vertikal bahu-hidung dari Pose Detection
     */
    fun evaluatePosture(
        headPitchX: Float,
        headRollZ: Float,
        shoulderSlopeDeg: Float?,
        noseToShoulderVerticalRatio: Float?,
        nowMillis: Long,
        sensitivityMultiplier: Float = 1.0f
    ): PostureEvaluationResult {
        val rollThreshold = 18f / sensitivityMultiplier
        val pitchDownThreshold = -16f / sensitivityMultiplier
        val shoulderTiltThreshold = 14f / sensitivityMultiplier

        val detectedStatus = when {
            noseToShoulderVerticalRatio != null && noseToShoulderVerticalRatio < (0.55f * sensitivityMultiplier) ->
                PostureStatus.SLOUCHING
            shoulderSlopeDeg != null && abs(shoulderSlopeDeg) > shoulderTiltThreshold ->
                PostureStatus.SLOUCHING
            headPitchX < pitchDownThreshold ->
                PostureStatus.HEAD_TILT_DOWN
            abs(headRollZ) > rollThreshold ->
                PostureStatus.NECK_TILT
            else ->
                PostureStatus.GOOD
        }

        if (detectedStatus == PostureStatus.GOOD) {
            badPostureStartMs = null
            return PostureEvaluationResult(
                status = PostureStatus.GOOD,
                isBadPostureTriggered = false,
                badPostureDurationMs = 0L
            )
        }

        if (badPostureStartMs == null) {
            badPostureStartMs = nowMillis
        }
        val duration = nowMillis - (badPostureStartMs ?: nowMillis)
        val triggered = duration >= 4000L

        return PostureEvaluationResult(
            status = detectedStatus,
            isBadPostureTriggered = triggered,
            badPostureDurationMs = duration
        )
    }

    fun reset() {
        badPostureStartMs = null
    }
}
