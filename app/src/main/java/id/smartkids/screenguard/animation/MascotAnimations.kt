package id.smartkids.screenguard.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Karakter 1: Kiko si Penjaga Mata (Owl Pastel Biru-Mint dengan animasi kedipan mata & pengukur jarak)
 */
@Composable
fun KikoEyeGuardianAnimation(modifier: Modifier = Modifier, isResting: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition(label = "kiko_anim")
    val bounceY by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "kiko_bounce"
    )
    val eyeScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isResting) 0.15f else 0.85f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "kiko_eye"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f + bounceY)
            // Badan bulat pastel mint-biru
            drawCircle(color = Color(0xFF80DEEA), radius = size.minDimension * 0.42f, center = center)
            drawCircle(color = Color(0xFFE0F7FA), radius = size.minDimension * 0.28f, center = Offset(center.x, center.y + 18f))

            // Bingkai kacamata pelindung mata Kiko
            val leftEyeCenter = Offset(center.x - 32f, center.y - 12f)
            val rightEyeCenter = Offset(center.x + 32f, center.y - 12f)
            drawCircle(color = Color.White, radius = 24f, center = leftEyeCenter)
            drawCircle(color = Color.White, radius = 24f, center = rightEyeCenter)
            drawCircle(color = Color(0xFF00838F), radius = 24f, center = leftEyeCenter, style = Stroke(width = 6f))
            drawCircle(color = Color(0xFF00838F), radius = 24f, center = rightEyeCenter, style = Stroke(width = 6f))

            // Pupil mata Kiko
            drawOval(
                color = Color(0xFF263238),
                topLeft = Offset(leftEyeCenter.x - 10f, leftEyeCenter.y - 10f * eyeScale),
                size = Size(20f, 20f * eyeScale)
            )
            drawOval(
                color = Color(0xFF263238),
                topLeft = Offset(rightEyeCenter.x - 10f, rightEyeCenter.y - 10f * eyeScale),
                size = Size(20f, 20f * eyeScale)
            )

            // Paruh kecil oranye
            val beak = Path().apply {
                moveTo(center.x - 10f, center.y + 4f)
                lineTo(center.x + 10f, center.y + 4f)
                lineTo(center.x, center.y + 18f)
                close()
            }
            drawPath(beak, color = Color(0xFFFFB74D))
        }
        Text(
            text = "Kiko si Penjaga Mata",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF006064)
        )
    }
}

/**
 * Karakter 2: Bubu si Penjaga Tidur (Beruang Pastel Lavender dengan topi tidur & animasi Zzz)
 */
@Composable
fun BubuSleepGuardianAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "bubu_anim")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "bubu_breath"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.38f * breathScale

            // Telinga beruang Bubu
            drawCircle(color = Color(0xFFB39DDB), radius = 26f, center = Offset(center.x - 45f, center.y - 45f))
            drawCircle(color = Color(0xFFB39DDB), radius = 26f, center = Offset(center.x + 45f, center.y - 45f))

            // Wajah beruang Bubu
            drawCircle(color = Color(0xFFD1C4E9), radius = baseRadius, center = center)

            // Mata terpejam nyaman
            drawArc(
                color = Color(0xFF4A148C),
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x - 42f, center.y - 18f),
                size = Size(28f, 18f),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFF4A148C),
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x + 14f, center.y - 18f),
                size = Size(28f, 18f),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )

            // Moncong & hidung
            drawOval(
                color = Color(0xFFEDE7F6),
                topLeft = Offset(center.x - 24f, center.y + 2f),
                size = Size(48f, 34f)
            )
            drawCircle(color = Color(0xFF7E57C2), radius = 8f, center = Offset(center.x, center.y + 12f))
        }
        Text(
            text = "Bubu si Penjaga Tidur  Zzz...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4A148C)
        )
    }
}

/**
 * Karakter 3: Lala si Pengingat Duduk + Ilustrasi Posisi Duduk Benar vs Salah
 */
@Composable
fun LalaPostureGuardianAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "lala_anim")
    val neckStretch by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "lala_stretch"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Canvas(modifier = Modifier.size(130.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            // Leher tegak Jerapah Lala
            drawRoundRect(
                color = Color(0xFFFFCC80),
                topLeft = Offset(center.x - 16f, center.y - 30f - neckStretch),
                size = Size(32f, 85f + neckStretch),
                cornerRadius = CornerRadius(16f, 16f)
            )
            // Kepala Lala
            drawCircle(
                color = Color(0xFFFFB74D),
                radius = 34f,
                center = Offset(center.x, center.y - 35f - neckStretch)
            )
            // Mata ceria
            drawCircle(color = Color(0xFF3E2723), radius = 6f, center = Offset(center.x - 12f, center.y - 40f - neckStretch))
            drawCircle(color = Color(0xFF3E2723), radius = 6f, center = Offset(center.x + 12f, center.y - 40f - neckStretch))
        }
        Text(
            text = "Lala si Pengingat Duduk",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE65100)
        )
        Spacer(modifier = Modifier.height(8.dp))
        PostureComparisonIllustration()
    }
}

@Composable
fun PostureComparisonIllustration() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Ilustrasi Posisi Duduk Salah (Membungkuk)
        Column(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFFFFEBEE), RoundedCornerShape(16.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(modifier = Modifier.size(72.dp)) {
                // Punggung melengkung membungkuk (Merah)
                val path = Path().apply {
                    moveTo(size.width * 0.35f, size.height * 0.85f)
                    quadraticBezierTo(
                        size.width * 0.15f,
                        size.height * 0.45f,
                        size.width * 0.65f,
                        size.height * 0.30f
                    )
                }
                drawPath(path, color = Color(0xFFD32F2F), style = Stroke(width = 10f, cap = StrokeCap.Round))
                drawCircle(color = Color(0xFFD32F2F), radius = 12f, center = Offset(size.width * 0.70f, size.height * 0.25f))
            }
            Text("Salah: Membungkuk", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
            Text("Leher tegang & mata terlalu dekat", style = MaterialTheme.typography.bodySmall)
        }

        // Ilustrasi Posisi Duduk Benar (Tegak 90 Derajat & Jarak >= 30 cm)
        Column(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFFE8F5E9), RoundedCornerShape(16.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(modifier = Modifier.size(72.dp)) {
                // Punggung tegak lurus (Hijau)
                drawLine(
                    color = Color(0xFF2E7D32),
                    start = Offset(size.width * 0.4f, size.height * 0.85f),
                    end = Offset(size.width * 0.4f, size.height * 0.32f),
                    strokeWidth = 10f,
                    cap = StrokeCap.Round
                )
                drawCircle(color = Color(0xFF2E7D32), radius = 12f, center = Offset(size.width * 0.4f, size.height * 0.18f))
            }
            Text("Benar: Duduk Tegak", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
            Text("Punggung lurus & jarak HP 30+ cm", style = MaterialTheme.typography.bodySmall)
        }
    }
}
