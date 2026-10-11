
package com.vibe.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.launch
import java.util.Locale

private val AlexCyan = Color(0xFF42DFFF)
private val AlexBlue = Color(0xFF208DFF)
private val AlexPurple = Color(0xFF9260FF)
private val AlexBackground = Color(0xFF070B18)
private val AlexPanel = Color(0xE8111A30)

@Composable
fun AlexVoiceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf("") }
    var answer by remember {
        mutableStateOf(
            "Привет! Я Алекс, твой помощник Vibe Chat. " +
                "Нажми на микрофон или напиши мне."
        )
    }

    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var fullScreenFace by remember { mutableStateOf(true) }
    var voiceError by remember { mutableStateOf("") }

    val tts = remember {
        TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (status != TextToSpeech.SUCCESS) {
                voiceError = "Русская озвучка пока недоступна"
            }
        }
    }

    LaunchedEffect(ttsReady) {
        if (ttsReady) {
            val result = tts.setLanguage(Locale("ru", "RU"))
            tts.setPitch(0.86f)
            tts.setSpeechRate(0.91f)

            if (
                result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                voiceError = "Установи поддержку русского голоса в настройках телефона"
            }
        }
    }

    DisposableEffect(tts) {
        onDispose {
            try {
                tts.stop()
                tts.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    val recognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                voiceError = ""
            }

            override fun onBeginningOfSpeech() {
                listening = true
                voiceError = ""
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                listening = false
            }

            override fun onError(error: Int) {
                listening = false

                voiceError = when (error) {
                    SpeechRecognizer.ERROR_AUDIO ->
                        "Проблема с аудио. Проверь микрофон."
                    SpeechRecognizer.ERROR_CLIENT ->
                        "Сбой голосового сервиса. Нажми микрофон ещё раз."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Разреши доступ к микрофону в настройках приложения."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Проблема с интернетом при распознавании речи."
                    SpeechRecognizer.ERROR_NO_MATCH ->
                        "Я не расслышал слова. Попробуй говорить чуть громче."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        "Микрофон уже занят. Подожди немного."
                    SpeechRecognizer.ERROR_SERVER ->
                        "Сервис распознавания временно недоступен."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "Я не услышал речь. Нажми микрофон и говори."
                    else ->
                        "Не удалось распознать речь. Попробуй ещё раз."
                }

                if (
                    error != SpeechRecognizer.ERROR_NO_MATCH &&
                    error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) {
                    Toast.makeText(
                        context,
                        voiceError,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false

                val words = results
                    ?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    ?.firstOrNull()
                    .orEmpty()

                if (words.isNotBlank()) {
                    draft = words
                    voiceError = ""
                } else {
                    voiceError = "Речь не распознана. Попробуй ещё раз."
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        onDispose {
            try {
                recognizer?.cancel()
                recognizer?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    fun startRecognition() {
        if (recognizer == null) {
            voiceError = "На телефоне не найден сервис распознавания речи."
            Toast.makeText(
                context,
                voiceError,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            voiceError = ""
            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Говори, Алекс слушает"
                )
            }

            recognizer.startListening(intent)
        } catch (_: Exception) {
            listening = false
            voiceError = "Не получилось включить микрофон. Попробуй снова."
            Toast.makeText(
                context,
                voiceError,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecognition()
        } else {
            listening = false
            voiceError = "Для голосового ввода разреши доступ к микрофону."
            Toast.makeText(
                context,
                voiceError,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val animation = rememberInfiniteTransition(label = "alex-animation")

    val glow by animation.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alex-glow"
    )

    val rotation by animation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000)
        ),
        label = "alex-ring-rotation"
    )

    val voicePulse by animation.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alex-voice-pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF060A16),
                        Color(0xFF0B1024),
                        Color(0xFF10102B)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "ALEX AI",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    letterSpacing = 2.sp
                )

                Text(
                    when {
                        busy -> "ДУМАЮ И АНАЛИЗИРУЮ"
                        listening -> "● СЛУШАЮ ТЕБЯ"
                        speaking -> "● ОТВЕЧАЮ ГОЛОСОМ"
                        else -> "ГОЛОСОВОЙ ПОМОЩНИК"
                    },
                    color = AlexCyan,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp
                )
            }

            TextButton(
                onClick = { fullScreenFace = !fullScreenFace }
            ) {
                Text(
                    if (fullScreenFace) "Свернуть" else "На весь экран",
                    color = Color(0xFFB7A5FF),
                    fontSize = 11.sp
                )
            }
        }

        if (fullScreenFace) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.95f)
                    .padding(vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height * 0.45f
                    val r = size.minDimension * 0.31f * glow

                    // Световое поле за головой
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x5032BFFF),
                                Color(0x18305DFF),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = r * 1.85f
                        ),
                        radius = r * 1.85f,
                        center = Offset(cx, cy)
                    )

                    // Внешние футуристические кольца
                    drawCircle(
                        color = AlexCyan.copy(alpha = 0.5f),
                        radius = r * 1.42f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.5f)
                    )

                    drawCircle(
                        color = AlexBlue.copy(alpha = 0.8f),
                        radius = r * 1.22f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.2f)
                    )

                    drawArc(
                        color = AlexCyan,
                        startAngle = rotation,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = Offset(cx - r * 1.36f, cy - r * 1.36f),
                        size = Size(r * 2.72f, r * 2.72f),
                        style = Stroke(width = 4f)
                    )

                    drawArc(
                        color = AlexPurple,
                        startAngle = rotation + 160f,
                        sweepAngle = 65f,
                        useCenter = false,
                        topLeft = Offset(cx - r * 1.22f, cy - r * 1.22f),
                        size = Size(r * 2.44f, r * 2.44f),
                        style = Stroke(width = 3f)
                    )

                    // Плечи и шея
                    val shoulders = Path().apply {
                        moveTo(cx - r * 0.88f, cy + r * 1.02f)
                        cubicTo(
                            cx - r * 0.82f, cy + r * 0.62f,
                            cx - r * 0.45f, cy + r * 0.56f,
                            cx - r * 0.30f, cy + r * 0.49f
                        )
                        lineTo(cx + r * 0.30f, cy + r * 0.49f)
                        cubicTo(
                            cx + r * 0.45f, cy + r * 0.56f,
                            cx + r * 0.82f, cy + r * 0.62f,
                            cx + r * 0.88f, cy + r * 1.02f
                        )
                    }

                    drawPath(
                        path = shoulders,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color(0xB52683D0),
                                Color(0xCC102D58),
                                Color(0x8029C8FF)
                            ),
                            startY = cy,
                            endY = cy + r * 1.05f
                        )
                    )

                    drawPath(
                        path = shoulders,
                        color = AlexCyan.copy(alpha = 0.9f),
                        style = Stroke(width = 2f)
                    )

                    // Шея
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color(0xFF83D8F5),
                                Color(0xFF225A9D),
                                Color(0xFF17345D)
                            ),
                            startY = cy + r * 0.26f,
                            endY = cy + r * 0.85f
                        ),
                        topLeft = Offset(cx - r * 0.24f, cy + r * 0.26f),
                        size = Size(r * 0.48f, r * 0.57f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            r * 0.12f
                        )
                    )

                    // Уши
                    drawOval(
                        color = Color(0xFF3D8BC7),
                        topLeft = Offset(cx - r * 0.53f, cy - r * 0.04f),
                        size = Size(r * 0.16f, r * 0.38f)
                    )
                    drawOval(
                        color = Color(0xFF3D8BC7),
                        topLeft = Offset(cx + r * 0.37f, cy - r * 0.04f),
                        size = Size(r * 0.16f, r * 0.38f)
                    )

                    // Голова — мягкий овал вместо геометрической маски
                    drawOval(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFD0F7FF),
                                Color(0xFF75C7F0),
                                Color(0xFF3676B8),
                                Color(0xFF172E65)
                            ),
                            start = Offset(cx - r * 0.5f, cy - r * 0.7f),
                            end = Offset(cx + r * 0.65f, cy + r * 0.5f)
                        ),
                        topLeft = Offset(cx - r * 0.46f, cy - r * 0.69f),
                        size = Size(r * 0.92f, r * 1.31f)
                    )

                    // Контур лица
                    drawOval(
                        color = AlexCyan.copy(alpha = 0.85f),
                        topLeft = Offset(cx - r * 0.46f, cy - r * 0.69f),
                        size = Size(r * 0.92f, r * 1.31f),
                        style = Stroke(width = 2f)
                    )

                    // Волосы
                    val hair = Path().apply {
                        moveTo(cx - r * 0.47f, cy - r * 0.25f)
                        lineTo(cx - r * 0.48f, cy - r * 0.50f)
                        cubicTo(
                            cx - r * 0.43f, cy - r * 0.79f,
                            cx - r * 0.12f, cy - r * 0.82f,
                            cx + r * 0.10f, cy - r * 0.72f
                        )
                        cubicTo(
                            cx + r * 0.37f, cy - r * 0.77f,
                            cx + r * 0.49f, cy - r * 0.52f,
                            cx + r * 0.46f, cy - r * 0.20f
                        )
                        lineTo(cx + r * 0.31f, cy - r * 0.34f)
                        lineTo(cx + r * 0.16f, cy - r * 0.43f)
                        lineTo(cx - r * 0.04f, cy - r * 0.33f)
                        lineTo(cx - r * 0.25f, cy - r * 0.42f)
                        close()
                    }

                    drawPath(
                        path = hair,
                        brush = Brush.linearGradient(
                            listOf(
                                Color(0xFF8CEBFF),
                                Color(0xFF25549C),
                                Color(0xFF101D4C)
                            ),
                            start = Offset(cx - r * 0.4f, cy - r * 0.7f),
                            end = Offset(cx + r * 0.4f, cy - r * 0.2f)
                        )
                    )

                    drawPath(
                        path = hair,
                        color = AlexCyan,
                        style = Stroke(width = 1.4f)
                    )

                    // Брови
                    drawLine(
                        Color(0xFF15366E),
                        Offset(cx - r * 0.34f, cy - r * 0.16f),
                        Offset(cx - r * 0.08f, cy - r * 0.19f),
                        strokeWidth = 4f
                    )
                    drawLine(
                        Color(0xFF15366E),
                        Offset(cx + r * 0.08f, cy - r * 0.19f),
                        Offset(cx + r * 0.34f, cy - r * 0.16f),
                        strokeWidth = 4f
                    )

                    // Глаза и светящиеся радужки
                    drawOval(
                        color = Color(0xFF07182D),
                        topLeft = Offset(cx - r * 0.34f, cy - r * 0.11f),
                        size = Size(r * 0.25f, r * 0.12f)
                    )
                    drawOval(
                        color = Color(0xFF07182D),
                        topLeft = Offset(cx + r * 0.09f, cy - r * 0.11f),
                        size = Size(r * 0.25f, r * 0.12f)
                    )

                    drawCircle(
                        color = AlexCyan,
                        radius = r * 0.046f,
                        center = Offset(cx - r * 0.21f, cy - r * 0.055f)
                    )
                    drawCircle(
                        color = AlexCyan,
                        radius = r * 0.046f,
                        center = Offset(cx + r * 0.21f, cy - r * 0.055f)
                    )

                    drawCircle(
                        color = Color.White,
                        radius = r * 0.014f,
                        center = Offset(cx - r * 0.225f, cy - r * 0.07f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = r * 0.014f,
                        center = Offset(cx + r * 0.195f, cy - r * 0.07f)
                    )

                    // Нос
                    drawPath(
                        path = Path().apply {
                            moveTo(cx, cy - r * 0.06f)
                            lineTo(cx - r * 0.055f, cy + r * 0.13f)
                            lineTo(cx + r * 0.045f, cy + r * 0.15f)
                        },
                        color = Color(0xFFB8F1FF),
                        style = Stroke(width = 2.4f)
                    )

                    // Скулы и нижняя часть лица
                    drawArc(
                        color = Color(0xFFB2EFFF).copy(alpha = 0.8f),
                        startAngle = 25f,
                        sweepAngle = 75f,
                        useCenter = false,
                        topLeft = Offset(cx - r * 0.38f, cy - r * 0.02f),
                        size = Size(r * 0.42f, r * 0.44f),
                        style = Stroke(width = 1.4f)
                    )

                    drawArc(
                        color = Color(0xFFB2EFFF).copy(alpha = 0.8f),
                        startAngle = 80f,
                        sweepAngle = 75f,
                        useCenter = false,
                        topLeft = Offset(cx - r * 0.04f, cy - r * 0.02f),
                        size = Size(r * 0.42f, r * 0.44f),
                        style = Stroke(width = 1.4f)
                    )

                    // Рот меняет размер во время голосового ответа
                    val mouthWidth = if (speaking) {
                        r * 0.25f * voicePulse
                    } else {
                        r * 0.20f
                    }

                    drawOval(
                        color = Color(0xFF092344),
                        topLeft = Offset(
                            cx - mouthWidth / 2f,
                            cy + r * 0.30f
                        ),
                        size = Size(mouthWidth, if (speaking) r * 0.085f else r * 0.035f)
                    )

                    drawArc(
                        color = AlexCyan,
                        startAngle = 5f,
                        sweepAngle = 170f,
                        useCenter = false,
                        topLeft = Offset(
                            cx - r * 0.24f,
                            cy + r * 0.25f
                        ),
                        size = Size(r * 0.48f, r * 0.17f),
                        style = Stroke(width = 2f)
                    )

                    // Тонкие строки цифровой голограммы
                    for (i in -3..3) {
                        val y = cy + i * r * 0.20f
                        drawLine(
                            color = AlexCyan.copy(alpha = 0.15f),
                            start = Offset(cx - r * 0.62f, y),
                            end = Offset(cx + r * 0.62f, y),
                            strokeWidth = 1f
                        )
                    }

                    // Световые маркеры вокруг лица
                    drawCircle(
                        AlexCyan,
                        r * 0.028f,
                        Offset(cx - r * 1.18f, cy + r * 0.18f)
                    )
                    drawCircle(
                        AlexPurple,
                        r * 0.028f,
                        Offset(cx + r * 1.18f, cy - r * 0.18f)
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "A L E X",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 5.sp
                    )

                    Text(
                        when {
                            listening -> "СЛУШАЮ ТВОЙ ГОЛОС"
                            busy -> "ОБРАБАТЫВАЮ ИНФОРМАЦИЮ"
                            speaking -> "ГОВОРЮ С ТОБОЙ"
                            else -> "ТВОЙ ИИ-ПОМОЩНИК"
                        },
                        color = AlexCyan,
                        fontSize = 9.sp,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AlexPanel)
                    .border(
                        1.dp,
                        AlexCyan.copy(alpha = 0.35f),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "◉   ALEX AI НА СВЯЗИ",
                    color = AlexCyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }

        // Панель ответа
        Column(
            modifier = Modifier
                .weight(if (fullScreenFace) 0.92f else 1.4f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(AlexPanel)
                .border(
                    1.dp,
                    Color(0xFF283C62),
                    RoundedCornerShape(22.dp)
                )
                .padding(14.dp)
        ) {
            Text(
                "ОТВЕТ АЛЕКСА",
                color = AlexCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    answer,
                    color = Color(0xFFF2F5FF),
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )

                if (voiceError.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        voiceError,
                        color = Color(0xFFFFB86C),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            if (busy) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AlexCyan,
                    trackColor = Color(0xFF202744)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "Скажи или напиши Алексу…",
                    color = Color(0xFF8792B7)
                )
            },
            shape = RoundedCornerShape(18.dp),
            maxLines = 3,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AlexCyan,
                unfocusedBorderColor = Color(0xFF303957),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = AlexCyan
            )
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    if (listening) {
                        try {
                            recognizer?.stopListening()
                        } catch (_: Exception) {
                            recognizer?.cancel()
                        }
                        listening = false
                    } else if (
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startRecognition()
                    } else {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.weight(0.9f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AlexCyan
                )
            ) {
                Text(
                    if (listening) "■ Стоп" else "🎙 Микрофон",
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = {
                    val question = draft.trim()

                    if (question.isNotBlank() && !busy) {
                        draft = ""
                        busy = true
                        voiceError = ""
                        speaking = false

                        scope.launch {
                            try {
                                val model = Firebase.ai(
                                    backend = GenerativeBackend.googleAI()
                                ).generativeModel("gemini-3.8-flash")

                                val response = model.generateContent(
                                    "Ты Алекс — спокойный, доброжелательный " +
                                        "голосовой помощник приложения Vibe Chat. " +
                                        "Отвечай на русском естественно, понятно " +
                                        "и не слишком длинно. Пользователь говорит: $question"
                                )

                                answer = response.text
                                    ?: "Не получилось получить текстовый ответ. Попробуй ещё раз."

                                if (ttsReady) {
                                    speaking = true
                                    tts.speak(
                                        answer,
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        "alex_reply"
                                    )

                                    // Состояние speaking будет сброшено при следующем запросе.
                                    // Сам TTS продолжает воспроизводить ответ в фоне.
                                }
                            } catch (e: Exception) {
                                answer = "Не удалось получить ответ: " +
                                    "${e.localizedMessage ?: "ошибка подключения к AI"}. " +
                                    "Проверь интернет, Firebase AI Logic и App Check."

                                speaking = false

                                Toast.makeText(
                                    context,
                                    "Ошибка связи с Алексом",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1.1f),
                enabled = !busy && draft.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7049D8),
                    disabledContainerColor = Color(0xFF39334D)
                )
            ) {
                Text(
                    if (busy) "Думаю…" else "Спросить Алекса",
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(5.dp))

        Text(
            "ALEX AI • VIBE CHAT",
            color = Color(0xFF63759B),
            fontSize = 9.sp,
            letterSpacing = 1.8.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
