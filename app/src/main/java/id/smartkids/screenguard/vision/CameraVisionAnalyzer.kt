package id.smartkids.screenguard.vision

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.atan2

data class RawVisionFrameData(
    val faceDetected: Boolean,
    val lowLight: Boolean,
    val faceWidthPx: Float,
    val leftEyeOpenProb: Float?,
    val rightEyeOpenProb: Float?,
    val headPitchX: Float,
    val headYawY: Float,
    val headRollZ: Float,
    val noseY: Float?,
    val shoulderSlopeDeg: Float?,
    val noseToShoulderRatio: Float?
)

class CameraVisionAnalyzer(
    private val onFrameAnalyzed: (RawVisionFrameData) -> Unit
) : ImageAnalysis.Analyzer {

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
    )

    private val poseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    private var lastProcessedTimestamp = 0L

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        // Batasi pemrosesan maksimal 4 frame per detik untuk hemat baterai & suhu perangkat
        if (now - lastProcessedTimestamp < 250L) {
            imageProxy.close()
            return
        }
        lastProcessedTimestamp = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // Hitung rata-rata pencahayaan dari plane Y (Luma) tanpa menyimpan gambar ke disk
        val luma = calculateAverageLuminance(imageProxy.planes[0].buffer)
        val isLowLight = luma < 38.0

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (primaryFace == null) {
                    onFrameAnalyzed(
                        RawVisionFrameData(
                            faceDetected = false,
                            lowLight = isLowLight,
                            faceWidthPx = 0f,
                            leftEyeOpenProb = null,
                            rightEyeOpenProb = null,
                            headPitchX = 0f,
                            headYawY = 0f,
                            headRollZ = 0f,
                            noseY = null,
                            shoulderSlopeDeg = null,
                            noseToShoulderRatio = null
                        )
                    )
                    imageProxy.close()
                    return@addOnSuccessListener
                }

                val noseLandmark = primaryFace.getLandmark(FaceLandmark.NOSE_BASE)
                poseDetector.process(inputImage)
                    .addOnSuccessListener { pose ->
                        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
                        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
                        val poseNose = pose.getPoseLandmark(PoseLandmark.NOSE)

                        var shoulderSlope: Float? = null
                        var noseShoulderRatio: Float? = null

                        if (leftShoulder != null && rightShoulder != null) {
                            val dy = rightShoulder.position.y - leftShoulder.position.y
                            val dx = rightShoulder.position.x - leftShoulder.position.x
                            shoulderSlope = Math.toDegrees(atan2(dy.toDouble(), abs(dx.toDouble()))).toFloat()

                            val shoulderWidth = abs(dx).coerceAtLeast(1f)
                            val midShoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2f
                            if (poseNose != null) {
                                val verticalDist = abs(midShoulderY - poseNose.position.y)
                                noseShoulderRatio = verticalDist / shoulderWidth
                            }
                        }

                        onFrameAnalyzed(
                            RawVisionFrameData(
                                faceDetected = true,
                                lowLight = isLowLight,
                                faceWidthPx = primaryFace.boundingBox.width().toFloat(),
                                leftEyeOpenProb = primaryFace.leftEyeOpenProbability,
                                rightEyeOpenProb = primaryFace.rightEyeOpenProbability,
                                headPitchX = primaryFace.headEulerAngleX,
                                headYawY = primaryFace.headEulerAngleY,
                                headRollZ = primaryFace.headEulerAngleZ,
                                noseY = noseLandmark?.position?.y,
                                shoulderSlopeDeg = shoulderSlope,
                                noseToShoulderRatio = noseShoulderRatio
                            )
                        )
                    }
                    .addOnCompleteListener {
                        // Pastikan frame kamera langsung dihapus dari RAM (Zero Video/Photo Storage Policy)
                        imageProxy.close()
                    }
            }
            .addOnFailureListener {
                imageProxy.close()
            }
    }

    private fun calculateAverageLuminance(buffer: ByteBuffer): Double {
        buffer.rewind()
        val limit = buffer.limit()
        if (limit == 0) return 100.0
        var sum = 0L
        var count = 0
        val step = 16
        var i = 0
        while (i < limit) {
            sum += (buffer.get(i).toInt() and 0xFF)
            count++
            i += step
        }
        return if (count > 0) sum.toDouble() / count else 100.0
    }
}
