package com.example.ui.custom

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.ui.viewmodel.PetVisualState

@Composable
fun BusterCharacterView(
    visualState: PetVisualState,
    outfitId: String,
    micAmplitude: Float,
    modifier: Modifier = Modifier,
    onPoke: (Float, Float, Int, Int) -> Unit
) {
    var viewWidth by remember { mutableStateOf(100) }
    var viewHeight by remember { mutableStateOf(100) }

    // ──────────────────────────────────────────────
    // CONTINUOUS ANIMATIONS (BREATHING & WOBBLE)
    // ──────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "BusterMotion")
    
    // Smooth breathing scale
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Breathing"
    )

    // Tail/Ear wiggle rotation
    val wiggleAngle by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Wiggle"
    )

    // Periodic blinking
    val blinkProgress by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3500
                1f at 0
                1f at 3200
                0f at 3350 // Blink fast
                1f at 3500
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Blink"
    )

    // Dizzy wobbly movement offset
    val dizzyWobbleX by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(180, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DizzyWobble"
    )

    // Eating jaws chomp state
    val eatingChomp by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(250, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Chomp"
    )

    // Compose Canvas
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned {
                viewWidth = it.size.width
                viewHeight = it.size.height
            }
            .pointerInput(visualState) {
                detectTapGestures { offset ->
                    onPoke(offset.x, offset.y, viewWidth, viewHeight)
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height * 0.58f
        val baseRadius = size.width * 0.28f

        // Let's determine specific offsets based on states
        val stateOffset = when (visualState) {
            PetVisualState.DIZZY -> Offset(dizzyWobbleX, 0f)
            PetVisualState.HAPPY_DANCE -> Offset(0f, wiggleAngle * 1.5f)
            else -> Offset(0f, 0f)
        }

        val dynamicCx = cx + stateOffset.x
        val dynamicCy = cy + stateOffset.y

        // Colors
        val bodyColor = Color(0xFF8D6E63)      // Main body: rich light brown
        val bodyAccentColor = Color(0xFFA1887F) // Body highlights
        val bellyColor = Color(0xFFFFCC80)     // Inner tummy peach color
        val pinkCheeks = Color(0xFFFF8A80)     // Blush color
        val shadowColor = Color(0x33000000)

        // 1. FLOOR SHADOW
        drawOval(
            color = shadowColor,
            topLeft = Offset(cx - baseRadius * 1.1f, cy + baseRadius * 0.88f),
            size = Size(baseRadius * 2.2f, baseRadius * 0.3f)
        )

        // 2. EARS - Left and Right Ears
        // Rotate ears according to idle wiggles, ear shapes are rounded triangular paths
        val leftEarPath = Path().apply {
            moveTo(dynamicCx - baseRadius * 0.8f, dynamicCy - baseRadius * 0.4f)
            quadraticTo(
                dynamicCx - baseRadius * 0.9f - wiggleAngle * 2.5f, dynamicCy - baseRadius * 1.4f,
                dynamicCx - baseRadius * 0.3f, dynamicCy - baseRadius * 0.9f
            )
            close()
        }
        drawPath(path = leftEarPath, color = bodyColor)
        
        val leftEarInnerPath = Path().apply {
            moveTo(dynamicCx - baseRadius * 0.72f, dynamicCy - baseRadius * 0.45f)
            quadraticTo(
                dynamicCx - baseRadius * 0.82f - wiggleAngle * 2f, dynamicCy - baseRadius * 1.25f,
                dynamicCx - baseRadius * 0.38f, dynamicCy - baseRadius * 0.85f
            )
            close()
        }
        drawPath(path = leftEarInnerPath, color = pinkCheeks)

        val rightEarPath = Path().apply {
            moveTo(dynamicCx + baseRadius * 0.3f, dynamicCy - baseRadius * 0.9f)
            quadraticTo(
                dynamicCx + baseRadius * 0.9f + wiggleAngle * 2.5f, dynamicCy - baseRadius * 1.4f,
                dynamicCx + baseRadius * 0.8f, dynamicCy - baseRadius * 0.4f
            )
            close()
        }
        drawPath(path = rightEarPath, color = bodyColor)

        val rightEarInnerPath = Path().apply {
            moveTo(dynamicCx + baseRadius * 0.38f, dynamicCy - baseRadius * 0.85f)
            quadraticTo(
                dynamicCx + baseRadius * 0.82f + wiggleAngle * 2f, dynamicCy - baseRadius * 1.25f,
                dynamicCx + baseRadius * 0.72f, dynamicCy - baseRadius * 0.45f
            )
            close()
        }
        drawPath(path = rightEarInnerPath, color = pinkCheeks)

        // 3. MAIN BODY & TUMMY (with breathing scale)
        val bodyScaleY = breathingScale
        val bodyScaleX = 1f / breathingScale
        val mainBodyWidth = baseRadius * 2f * bodyScaleX
        val mainBodyHeight = baseRadius * 2.1f * bodyScaleY

        drawOval(
            color = bodyColor,
            topLeft = Offset(dynamicCx - mainBodyWidth / 2f, dynamicCy - (mainBodyHeight * 0.55f)),
            size = Size(mainBodyWidth, mainBodyHeight)
        )

        // INNER BELLY OVAL
        val bellyWidth = mainBodyWidth * 0.52f
        val bellyHeight = mainBodyHeight * 0.42f
        drawOval(
            color = bellyColor,
            topLeft = Offset(dynamicCx - bellyWidth / 2f, dynamicCy + (mainBodyHeight * 0.05f)),
            size = Size(bellyWidth, bellyHeight)
        )

        // 4. FEET
        val footWidth = baseRadius * 0.45f
        val footHeight = baseRadius * 0.28f
        // Left Foot wiggles if dancing
        val leftFootYOffset = if (visualState == PetVisualState.HAPPY_DANCE) (wiggleAngle * 1.2f).coerceAtLeast(0f) else 0f
        drawRoundRect(
            color = bodyAccentColor,
            topLeft = Offset(dynamicCx - baseRadius * 0.78f, dynamicCy + baseRadius * 0.9f - leftFootYOffset),
            size = Size(footWidth, footHeight),
            cornerRadius = CornerRadius(18f, 18f)
        )
        // Right Foot
        val rightFootYOffset = if (visualState == PetVisualState.HAPPY_DANCE) (-wiggleAngle * 1.2f).coerceAtLeast(0f) else 0f
        drawRoundRect(
            color = bodyAccentColor,
            topLeft = Offset(dynamicCx + baseRadius * 0.33f, dynamicCy + baseRadius * 0.9f - rightFootYOffset),
            size = Size(footWidth, footHeight),
            cornerRadius = CornerRadius(18f, 18f)
        )

        // 5. ARMS/HANDS
        // Left hand is up if hearing!
        if (visualState == PetVisualState.HEARING) {
            // Hand waving up next to left ear
            drawOval(
                color = bodyColor,
                topLeft = Offset(dynamicCx - baseRadius * 1.2f, dynamicCy - baseRadius * 0.4f),
                size = Size(baseRadius * 0.32f, baseRadius * 0.5f)
            )
        } else {
            // Standard left hand cute resting
            drawOval(
                color = bodyColor,
                topLeft = Offset(dynamicCx - baseRadius * 1.08f, dynamicCy + baseRadius * 0.15f),
                size = Size(baseRadius * 0.3f, baseRadius * 0.35f)
            )
        }

        // Right hand
        drawOval(
            color = bodyColor,
            topLeft = Offset(dynamicCx + baseRadius * 0.78f, dynamicCy + baseRadius * 0.15f),
            size = Size(baseRadius * 0.3f, baseRadius * 0.35f)
        )

        // 6. FACE FEATURES (EYES & BLINKING)
        val eyeSpacing = baseRadius * 0.52f
        val eyeSize = baseRadius * 0.32f
        val eyeY = dynamicCy - baseRadius * 0.25f

        // LEFT EYE
        val leftEyeC = Offset(dynamicCx - eyeSpacing / 1.7f, eyeY)
        if (visualState == PetVisualState.SLEEPING) {
            // Drawn as sleeping cute crescent curves: "^"
            drawArc(
                color = Color.Black,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(leftEyeC.x - eyeSize / 2f, leftEyeC.y - eyeSize / 4f),
                size = Size(eyeSize, eyeSize / 2f),
                style = Stroke(width = 6f, cap = StrokeCap.Round)
            )
        } else if (visualState == PetVisualState.DIZZY) {
            // Spirals or @ eye structures
            drawCircle(
                color = Color.Black,
                radius = eyeSize * 0.35f,
                center = leftEyeC,
                style = Stroke(width = 5f)
            )
            drawArc(
                color = Color.Black,
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(leftEyeC.x - eyeSize * 0.18f, leftEyeC.y - eyeSize * 0.18f),
                size = Size(eyeSize * 0.36f, eyeSize * 0.36f),
                style = Stroke(width = 4f)
            )
        } else {
            // Standard blink eye
            val activeBlinkYScale = if (visualState == PetVisualState.BATHING) 0.1f else blinkProgress
            val heightScale = eyeSize * activeBlinkYScale

            if (heightScale > 3f) {
                // Outer white eye
                drawOval(
                    color = Color.White,
                    topLeft = Offset(leftEyeC.x - eyeSize / 2f, leftEyeC.y - heightScale / 2f),
                    size = Size(eyeSize, heightScale)
                )
                // Black Pupil
                drawCircle(
                    color = Color(0xFF0D47A1), // Deep majestic blue iris
                    radius = eyeSize * 0.35f * activeBlinkYScale,
                    center = leftEyeC
                )
                drawCircle(
                    color = Color.Black,
                    radius = eyeSize * 0.2f * activeBlinkYScale,
                    center = leftEyeC
                )
                // Cute glint reflection
                drawCircle(
                    color = Color.White,
                    radius = eyeSize * 0.08f * activeBlinkYScale,
                    center = Offset(leftEyeC.x - eyeSize * 0.08f, leftEyeC.y - heightScale * 0.12f)
                )
            } else {
                // Blink line
                drawLine(
                    color = Color(0xFF3E2723),
                    start = Offset(leftEyeC.x - eyeSize / 2f, leftEyeC.y),
                    end = Offset(leftEyeC.x + eyeSize / 2f, leftEyeC.y),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }
        }

        // RIGHT EYE
        val rightEyeC = Offset(dynamicCx + eyeSpacing / 1.7f, eyeY)
        if (visualState == PetVisualState.SLEEPING) {
            drawArc(
                color = Color.Black,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(rightEyeC.x - eyeSize / 2f, rightEyeC.y - eyeSize / 4f),
                size = Size(eyeSize, eyeSize / 2f),
                style = Stroke(width = 6f, cap = StrokeCap.Round)
            )
        } else if (visualState == PetVisualState.DIZZY) {
            drawCircle(
                color = Color.Black,
                radius = eyeSize * 0.35f,
                center = rightEyeC,
                style = Stroke(width = 5f)
            )
            drawArc(
                color = Color.Black,
                startAngle = 180f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(rightEyeC.x - eyeSize * 0.18f, rightEyeC.y - eyeSize * 0.18f),
                size = Size(eyeSize * 0.36f, eyeSize * 0.36f),
                style = Stroke(width = 4f)
            )
        } else {
            val activeBlinkYScale = if (visualState == PetVisualState.BATHING) 0.1f else blinkProgress
            val heightScale = eyeSize * activeBlinkYScale

            if (heightScale > 3f) {
                drawOval(
                    color = Color.White,
                    topLeft = Offset(rightEyeC.x - eyeSize / 2f, rightEyeC.y - heightScale / 2f),
                    size = Size(eyeSize, heightScale)
                )
                drawCircle(
                    color = Color(0xFF0D47A1),
                    radius = eyeSize * 0.35f * activeBlinkYScale,
                    center = rightEyeC
                )
                drawCircle(
                    color = Color.Black,
                    radius = eyeSize * 0.2f * activeBlinkYScale,
                    center = rightEyeC
                )
                drawCircle(
                    color = Color.White,
                    radius = eyeSize * 0.08f * activeBlinkYScale,
                    center = Offset(rightEyeC.x - eyeSize * 0.08f, rightEyeC.y - heightScale * 0.12f)
                )
            } else {
                drawLine(
                    color = Color(0xFF3E2723),
                    start = Offset(rightEyeC.x - eyeSize / 2f, rightEyeC.y),
                    end = Offset(rightEyeC.x + eyeSize / 2f, rightEyeC.y),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }
        }

        // 7. PINK BLUSH CHEEKS (glowing if happy)
        val cheekOffsetW = baseRadius * 0.65f
        val cheekOffsetH = baseRadius * 0.06f
        val cheekRadius = baseRadius * 0.15f
        drawCircle(
            color = pinkCheeks.copy(alpha = if (visualState == PetVisualState.HAPPY_DANCE) 0.75f else 0.45f),
            radius = cheekRadius,
            center = Offset(dynamicCx - cheekOffsetW, eyeY + cheekOffsetH)
        )
        drawCircle(
            color = pinkCheeks.copy(alpha = if (visualState == PetVisualState.HAPPY_DANCE) 0.75f else 0.45f),
            radius = cheekRadius,
            center = Offset(dynamicCx + cheekOffsetW, eyeY + cheekOffsetH)
        )

        // 8. TINY CAT NOSE (Triangular)
        val noseCenterY = eyeY + baseRadius * 0.18f
        val nosePath = Path().apply {
            moveTo(dynamicCx, noseCenterY)
            lineTo(dynamicCx - 14f, noseCenterY - 10f)
            lineTo(dynamicCx + 14f, noseCenterY - 10f)
            close()
        }
        drawPath(path = nosePath, color = Color(0xFF3E2723))

        // 9. DYNAMIC EXPRESSIVE MOUTH
        val mouthY = noseCenterY + 12f
        when (visualState) {
            PetVisualState.SLEEPING -> {
                // Micro line for sleeping mouth
                drawLine(
                    color = Color(0xFF3E2723),
                    start = Offset(dynamicCx - 12f, mouthY),
                    end = Offset(dynamicCx + 12f, mouthY),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }
            PetVisualState.EATING -> {
                // Round jaws gaping / chewing dynamically
                val chewOpenSize = baseRadius * 0.28f * eatingChomp
                drawOval(
                    color = Color(0xFF421D15),
                    topLeft = Offset(dynamicCx - chewOpenSize / 2f, mouthY),
                    size = Size(chewOpenSize, chewOpenSize)
                )
                // Draw a small red tongue
                drawOval(
                    color = Color(0xFFFF5252),
                    topLeft = Offset(dynamicCx - chewOpenSize * 0.3f, mouthY + chewOpenSize * 0.4f),
                    size = Size(chewOpenSize * 0.6f, chewOpenSize * 0.4f)
                )
            }
            PetVisualState.SPEAKING -> {
                // Mouth moves with the microphone real-time amplitude!
                val talkSize = (baseRadius * 0.15f) + (baseRadius * 0.5f * micAmplitude).coerceAtMost(baseRadius * 0.45f)
                drawOval(
                    color = Color(0xFF421D15),
                    topLeft = Offset(dynamicCx - talkSize / 1.5f, mouthY),
                    size = Size(talkSize * 1.33f, talkSize)
                )
            }
            PetVisualState.HAPPY_DANCE -> {
                // Huge smiling open mouth
                val smileWidth = baseRadius * 0.48f
                val smileHeight = baseRadius * 0.35f
                val smilePath = Path().apply {
                    moveTo(dynamicCx - smileWidth / 2f, mouthY)
                    quadraticTo(
                        dynamicCx, mouthY + smileHeight,
                        dynamicCx + smileWidth / 2f, mouthY
                    )
                    close()
                }
                drawPath(path = smilePath, color = Color(0xFF421D15))
                // Cute blush inner tongue
                drawCircle(
                    color = Color(0xFFFF5252),
                    radius = smileWidth * 0.25f,
                    center = Offset(dynamicCx, mouthY + smileHeight * 0.62f)
                )
            }
            PetVisualState.DIZZY -> {
                // Wavy confused line "波浪嘴"
                val wavePath = Path().apply {
                    moveTo(dynamicCx - 24f, mouthY)
                    quadraticTo(dynamicCx - 12f, mouthY + 10f, dynamicCx, mouthY)
                    quadraticTo(dynamicCx + 12f, mouthY - 10f, dynamicCx + 24f, mouthY)
                }
                drawPath(path = wavePath, color = Color(0xFF3E2723), style = Stroke(width = 5f, cap = StrokeCap.Round))
            }
            else -> {
                // Classic content cute cat whiskers and smile
                val catSmilePath = Path().apply {
                    moveTo(dynamicCx - 28f, mouthY)
                    quadraticTo(dynamicCx - 14f, mouthY + 12f, dynamicCx, mouthY + 2f)
                    quadraticTo(dynamicCx + 14f, mouthY + 12f, dynamicCx + 28f, mouthY)
                }
                drawPath(
                    path = catSmilePath, 
                    color = Color(0xFF3E2723), 
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )
            }
        }

        // 10. WHISKERS
        val whiskerY = mouthY + 4f
        // Left Whiskers
        drawLine(Color(0xFF4E342E), Offset(dynamicCx - baseRadius * 0.42f, whiskerY), Offset(dynamicCx - baseRadius * 1.05f, whiskerY - 8f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(Color(0xFF4E342E), Offset(dynamicCx - baseRadius * 0.42f, whiskerY + 6f), Offset(dynamicCx - baseRadius * 1.02f, whiskerY + 10f), strokeWidth = 3f, cap = StrokeCap.Round)

        // Right Whiskers
        drawLine(Color(0xFF4E342E), Offset(dynamicCx + baseRadius * 0.42f, whiskerY), Offset(dynamicCx + baseRadius * 1.05f, whiskerY - 8f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(Color(0xFF4E342E), Offset(dynamicCx + baseRadius * 0.42f, whiskerY + 6f), Offset(dynamicCx + baseRadius * 1.02f, whiskerY + 10f), strokeWidth = 3f, cap = StrokeCap.Round)

        // 11. ACCESSORY OVERLAY COGNITIVE PROCESSING (CROWN, SUNGLASSES, ETC.)
        when (outfitId) {
            "viking" -> {
                // Paint a mighty Viking Helmet
                // Helmet dome
                val helmWidth = baseRadius * 1.78f
                val helmHeight = baseRadius * 0.88f
                val helmY = eyeY - baseRadius * 1.15f
                
                drawArc(
                    color = Color(0xFFB0BEC5), // Steel grey dome
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(dynamicCx - helmWidth / 2f, helmY),
                    size = Size(helmWidth, helmHeight * 2f)
                )
                // Helmet banding
                drawRoundRect(
                    color = Color(0xFF78909C),
                    topLeft = Offset(dynamicCx - helmWidth / 2f, helmY + helmHeight - 8f),
                    size = Size(helmWidth, 16f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                // Left Horn: White curved path
                val leftHorn = Path().apply {
                    moveTo(dynamicCx - helmWidth / 2.3f, helmY + helmHeight * 0.4f)
                    quadraticTo(
                        dynamicCx - helmWidth * 0.78f, helmY - helmHeight * 0.4f,
                        dynamicCx - helmWidth * 0.7f, helmY - helmHeight * 0.62f
                    )
                    quadraticTo(
                        dynamicCx - helmWidth * 0.52f, helmY - helmHeight * 0.15f,
                        dynamicCx - helmWidth / 3.4f, helmY + helmHeight * 0.3f
                    )
                    close()
                }
                drawPath(path = leftHorn, color = Color.White)
                
                // Right Horn
                val rightHorn = Path().apply {
                    moveTo(dynamicCx + helmWidth / 2.3f, helmY + helmHeight * 0.4f)
                    quadraticTo(
                        dynamicCx + helmWidth * 0.78f, helmY - helmHeight * 0.4f,
                        dynamicCx + helmWidth * 0.7f, helmY - helmHeight * 0.62f
                    )
                    quadraticTo(
                        dynamicCx + helmWidth * 0.52f, helmY - helmHeight * 0.15f,
                        dynamicCx + helmWidth / 3.4f, helmY + helmHeight * 0.3f
                    )
                    close()
                }
                drawPath(path = rightHorn, color = Color.White)
            }
            "royal_crown" -> {
                // Gold crown with peaks and jewels
                val crownW = baseRadius * 1.58f
                val crownH = baseRadius * 0.88f
                val crownY = eyeY - baseRadius * 1.05f
                
                val crownPath = Path().apply {
                    moveTo(dynamicCx - crownW / 2f, crownY + crownH)
                    lineTo(dynamicCx - crownW / 2f, crownY + crownH * 0.3f)
                    lineTo(dynamicCx - crownW * 0.25f, crownY + crownH * 0.7f)
                    lineTo(dynamicCx, crownY)
                    lineTo(dynamicCx + crownW * 0.25f, crownY + crownH * 0.7f)
                    lineTo(dynamicCx + crownW / 2f, crownY + crownH * 0.3f)
                    lineTo(dynamicCx + crownW / 2f, crownY + crownH)
                    close()
                }
                drawPath(path = crownPath, color = Color(0xFFFFD54F)) // gold yellow
                
                // Red velvet padding underneath
                drawOval(
                    color = Color(0xFFD32F2F),
                    topLeft = Offset(dynamicCx - crownW * 0.38f, crownY + crownH * 0.65f),
                    size = Size(crownW * 0.76f, crownH * 0.3f)
                )

                // Gold headband row
                drawRect(
                    color = Color(0xFFF57F17),
                    topLeft = Offset(dynamicCx - crownW / 2f, crownY + crownH - 10f),
                    size = Size(crownW, 10f)
                )

                // Gems on peaks
                drawCircle(Color(0xFFE91E63), radius = 8f, center = Offset(dynamicCx - crownW / 2f, crownY + crownH * 0.3f))
                drawCircle(Color(0xFF2196F3), radius = 10f, center = Offset(dynamicCx, crownY))
                drawCircle(Color(0xFFE91E63), radius = 8f, center = Offset(dynamicCx + crownW / 2f, crownY + crownH * 0.3f))
            }
            "sunglasses" -> {
                // Cool classic dark shade glasses
                val glassW = baseRadius * 0.62f
                val glassH = baseRadius * 0.42f
                val glassY = eyeY - glassH / 2f

                // Left lens
                drawRoundRect(
                    color = Color(0xFF212121),
                    topLeft = Offset(dynamicCx - eyeSpacing / 1.7f - glassW / 2f, glassY),
                    size = Size(glassW, glassH),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                // Left glass glint
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(dynamicCx - eyeSpacing / 1.7f - glassW * 0.25f, glassY + 6f),
                    end = Offset(dynamicCx - eyeSpacing / 1.7f - glassW * 0.05f, glassY + glassH - 6f),
                    strokeWidth = 6f
                )

                // Right lens
                drawRoundRect(
                    color = Color(0xFF212121),
                    topLeft = Offset(dynamicCx + eyeSpacing / 1.7f - glassW / 2f, glassY),
                    size = Size(glassW, glassH),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                // Right glass glint
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(dynamicCx + eyeSpacing / 1.7f - glassW * 0.25f, glassY + 6f),
                    end = Offset(dynamicCx + eyeSpacing / 1.7f - glassW * 0.05f, glassY + glassH - 6f),
                    strokeWidth = 6f
                )

                // Glasses bridge connecting them
                drawLine(
                    color = Color(0xFF212121),
                    start = Offset(dynamicCx - eyeSpacing / 2f + glassW / 2f, glassY + 8f),
                    end = Offset(dynamicCx + eyeSpacing / 2f - glassW / 2f, glassY + 8f),
                    strokeWidth = 8f
                )
            }
            "scarf" -> {
                // Red warm scarf around the tummy-neck boundary
                val scarfW = baseRadius * 1.58f
                val scarfH = baseRadius * 0.28f
                val scarfY = dynamicCy + baseRadius * 0.22f

                drawRoundRect(
                    color = Color(0xFFD32F2F),
                    topLeft = Offset(dynamicCx - scarfW / 2f, scarfY),
                    size = Size(scarfW, scarfH),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                // Scarf stripes / details
                drawRect(
                    color = Color(0xFFFFCDD2),
                    topLeft = Offset(dynamicCx - scarfW * 0.3f, scarfY),
                    size = Size(14f, scarfH)
                )
                drawRect(
                    color = Color(0xFFFFCDD2),
                    topLeft = Offset(dynamicCx + scarfW * 0.2f, scarfY),
                    size = Size(14f, scarfH)
                )

                // Hanging tail of scarf
                val tailW = baseRadius * 0.32f
                val tailH = baseRadius * 0.65f
                drawRoundRect(
                    color = Color(0xFFC62828),
                    topLeft = Offset(dynamicCx + scarfW * 0.16f, scarfY + scarfH - 4f),
                    size = Size(tailW, tailH),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                // Scarf tassels
                for (i in 0..2) {
                    drawLine(
                        color = Color(0xFFFFCDD2),
                        start = Offset(dynamicCx + scarfW * 0.18f + i * 14f, scarfY + scarfH + tailH - 6f),
                        end = Offset(dynamicCx + scarfW * 0.18f + i * 14f, scarfY + scarfH + tailH + 10f),
                        strokeWidth = 4f
                    )
                }
            }
        }

        // 12. SHOWER WATER SPRAYS & BUBBLES COG (BATHROOM OPTION)
        if (visualState == PetVisualState.BATHING) {
            // Draw soapy bubble shapes randomly over tummy area
            drawCircle(Color.White.copy(alpha = 0.85f), radius = 22f, center = Offset(dynamicCx - baseRadius * 0.3f, dynamicCy + baseRadius * 0.45f))
            drawCircle(Color.White.copy(alpha = 0.85f), radius = 18f, center = Offset(dynamicCx - baseRadius * 0.18f, dynamicCy + baseRadius * 0.52f))
            drawCircle(Color.White.copy(alpha = 0.85f), radius = 28f, center = Offset(dynamicCx + baseRadius * 0.2f, dynamicCy + baseRadius * 0.38f))
            drawCircle(Color.White.copy(alpha = 0.85f), radius = 15f, center = Offset(dynamicCx + baseRadius * 0.35f, dynamicCy + baseRadius * 0.48f))

            // Draw bubble outlines around him
            drawCircle(Color(0xBB80DEEA), radius = 12f, center = Offset(dynamicCx - baseRadius * 0.98f, dynamicCy + baseRadius * 0.05f), style = Stroke(width = 3f))
            drawCircle(Color(0xBB80DEEA), radius = 18f, center = Offset(dynamicCx + baseRadius * 1.15f, dynamicCy + baseRadius * 0.58f), style = Stroke(width = 3f))
        }
    }
}
