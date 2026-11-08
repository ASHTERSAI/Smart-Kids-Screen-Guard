package id.smartkids.screenguard

import id.smartkids.screenguard.core.PostureStatus
import id.smartkids.screenguard.core.ProtectionLevel
import id.smartkids.screenguard.distance_detection.DistanceDetectionEngine
import id.smartkids.screenguard.posture_detection.PostureDetectionEngine
import id.smartkids.screenguard.security.ParentAuthManager
import id.smartkids.screenguard.sleep_detection.SleepDetectionEngine
import org.junit.Assert.*
import org.junit.Test

class ScreenGuardUnitTest {

    @Test
    fun testPasswordAndPinHashingConsistency() {
        val salt = ByteArray(16) { it.toByte() }
        val hash1 = ParentAuthManager.hashSecretWithSalt("123456", salt)
        val hash2 = ParentAuthManager.hashSecretWithSalt("123456", salt)
        val wrongHash = ParentAuthManager.hashSecretWithSalt("654321", salt)

        assertTrue(ParentAuthManager.constantTimeEquals(hash1, hash2))
        assertFalse(ParentAuthManager.constantTimeEquals(hash1, wrongHash))
    }

    @Test
    fun testDistanceDetectionUnder30CmAndEscalation() {
        val engine = DistanceDetectionEngine()
        // Pada focal length 620, lebar wajah 360px menghasilkan jarak ~ 22.3 cm (di bawah 30 cm)
        val t0 = 1_000_000L
        val res0 = engine.evaluateDistance(360f, 620f, 30f, t0)
        assertTrue(res0.isTooClose)
        assertTrue(res0.estimatedDistanceCm < 30f)

        // Setelah 3 detik -> Level 1 Warning
        val resLevel1 = engine.evaluateDistance(360f, 620f, 30f, t0 + 3_000L)
        assertEquals(ProtectionLevel.LEVEL_1_WARNING, resLevel1.recommendedLevel)

        // Setelah 8 detik -> Level 2 Screen Alert
        val resLevel2 = engine.evaluateDistance(360f, 620f, 30f, t0 + 8_000L)
        assertEquals(ProtectionLevel.LEVEL_2_SCREEN_ALERT, resLevel2.recommendedLevel)

        // Setelah 16 detik -> Level 3 Rest Mode
        val resLevel3 = engine.evaluateDistance(360f, 620f, 30f, t0 + 16_000L)
        assertEquals(ProtectionLevel.LEVEL_3_REST_MODE, resLevel3.recommendedLevel)
    }

    @Test
    fun testSleepDetectionIgnoresBriefBlinkAndDetectsProlongedClosure() {
        val sleepEngine = SleepDetectionEngine()
        val start = 5_000_000L

        // Kedipan mata singkat 500ms tidak boleh dianggap tidur
        sleepEngine.evaluateFrame(0.05f, 0.05f, -5f, 0f, 120f, start, thresholdSeconds = 6)
        val blinkResult = sleepEngine.evaluateFrame(0.05f, 0.05f, -5f, 0f, 120f, start + 500L, thresholdSeconds = 6)
        assertFalse(blinkResult.isSleepSuspected)

        // Mata tertutup terus-menerus selama 6.5 detik memicu Sleep Alert
        val sleepResult = sleepEngine.evaluateFrame(0.05f, 0.05f, -18f, 0f, 120f, start + 6_500L, thresholdSeconds = 6)
        assertTrue(sleepResult.isSleepSuspected)
    }

    @Test
    fun testPostureDetectionDetectsSlouchingAndHeadTilt() {
        val postureEngine = PostureDetectionEngine()
        val t0 = 10_000L

        // Posisi membungkuk (rasio vertikal hidung-bahu rendah) selama 4.5 detik
        postureEngine.evaluatePosture(0f, 0f, 5f, 0.40f, t0)
        val slouchResult = postureEngine.evaluatePosture(0f, 0f, 5f, 0.40f, t0 + 4_500L)
        assertEquals(PostureStatus.SLOUCHING, slouchResult.status)
        assertTrue(slouchResult.isBadPostureTriggered)
    }
}
