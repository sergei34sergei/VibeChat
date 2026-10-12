
package com.vibe.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val AlexBackground = Color(0xFF090A12)
private val AlexPanel = Color(0xFF151725)
private val AlexPurple = Color(0xFF9275F5)
private val AlexLavender = Color(0xFFC5B7FF)
private val AlexPink = Color(0xFFFF9CD6)
private val AlexCyan = Color(0xFF72E7FF)
private val AlexWhite = Color(0xFFF6F3FF)
private val AlexMuted = Color(0xFFAAA9BF)

private enum class AlexMode {
    READY, LISTENING, THINKING, SPEAKING
}

@Composable
fun AlexVoiceScreen(
    onOpenChat: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Готов к разговору") }
    var mode by remember { mutableStateOf(AlexMode.READY) }
    var userText by remember { mutableStateOf("") }
    var alexText by remember {
        mutableStateOf("Привет! Я Алекс. Нажми на микрофон — и поговорим.")
    }

    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var speechEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var speechReady by remember { mutableStateOf(false) }
    var responseJob by remember { mutableStateOf<Job?>(null) }
    var ignoreRecognizerErrors by remember { mutableStateOf(false) }

    val conversation = remember {
        mutableStateListOf<Pair<String, String>>()
    }

    val model = remember {
        Firebase.ai(
            backend = GenerativeBackend.googleAI()
        ).generativeModel("gemini-3.8-flash")
    }

    fun stopSpeaking() {
        speechEngine?.stop()
        if (mode == AlexMode.SPEAKING) {
            mode = AlexMode.READY
            status = "Готов к разговору"
        }
    }

    // Останавливает генерацию, распознавание речи и озвучивание.
    fun stopEverything() {
        ignoreRecognizerErrors = true

        responseJob?.cancel()
        responseJob = null

        recognizer?.cancel()
        speechEngine?.stop()

        mode = AlexMode.READY
        status = "Остановлено"
    }

    fun speakAnswer(text: String) {
        val engine = speechEngine

        if (engine == null || !speechReady) {
            mode = AlexMode.READY
            status = "Голос загружается или недоступен"
            return
        }

        engine.stop()
        engine.setPitch(0.96f)
        engine.setSpeechRate(0.96f)

        mode = AlexMode.SPEAKING
        status = "Алекс говорит…"

        val result = engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            "ALEX_REPLY"
        )

        if (result == TextToSpeech.ERROR) {
            mode = AlexMode.READY
            status = "Не удалось включить голос"
        }
    }

    fun askAlex(message: String) {
        if (message.isBlank() || mode == AlexMode.THINKING) return

        stopSpeaking()

        userText = message.trim()
        conversation.add("Пользователь" to userText)

        mode = AlexMode.THINKING
        status = "Алекс обдумывает ответ…"

        responseJob = scope.launch {
            try {
                // Берём только последние сообщения, чтобы не перегружать запрос.
                val history = conversation
                    .takeLast(8)
                    .joinToString("\n") { (speaker, text) ->
                        "$speaker: $text"
                    }

                val prompt = """
                    Ты — Алекс, ИИ-помощник приложения Vibe Chat.

                    Отвечай естественно, дружелюбно и по существу.
                    По умолчанию используй русский язык.
                    Учитывай историю разговора.
                    Формулируй ответ удобно для прослушивания вслух.
                    Не утверждай, что выполнил действие, если у тебя нет
                    инструмента для его выполнения.

                    История:
                    $history

                    Ответь на последнее сообщение пользователя.
                """.trimIndent()

                val response = withContext(Dispatchers.IO) {
                    model.generateContent(prompt)
                }

                val answer = response.text
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: "Не получилось подготовить ответ. Попробуй ещё раз."

                alexText = answer
                conversation.add("Алекс" to answer)
                speakAnswer(answer)

            } catch (error: CancellationException) {
                // Отмена запроса кнопкой «Стоп» — это нормальное действие.
                throw error
            } catch (error: Exception) {
                mode = AlexMode.READY
                status = "Не удалось получить ответ"
                alexText =
                    "Не удалось получить ответ. Проверь интернет и настройки Firebase AI."
            } finally {
                responseJob = null
            }
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status = "На устройстве недоступно распознавание речи"
            return
        }

        stopSpeaking()

        val activeRecognizer = recognizer
        if (activeRecognizer == null) {
            status = "Распознавание речи ещё не готово"
            return
        }

        ignoreRecognizerErrors = false

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говори, я слушаю")
        }

        try {
            mode = AlexMode.LISTENING
            status = "Слушаю тебя…"
            activeRecognizer.startListening(intent)
        } catch (error: Exception) {
            mode = AlexMode.READY
            status = "Не удалось включить микрофон"
        }
    }

    val microphonePermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startListening()
            } else {
                status = "Разреши доступ к микрофону в настройках"
            }
        }

    DisposableEffect(context) {
        var alive = true

        val engine = TextToSpeech(context) { result ->
            if (!alive) return@TextToSpeech

            if (result == TextToSpeech.SUCCESS) {
                val languageResult =
                    speechEngine?.setLanguage(Locale("ru", "RU"))
                        ?: TextToSpeech.ERROR

                speechReady =
                    languageResult != TextToSpeech.LANG_MISSING_DATA &&
                    languageResult != TextToSpeech.LANG_NOT_SUPPORTED &&
                    languageResult != TextToSpeech.ERROR

                if (!speechReady) {
                    status = "Установи русские голосовые данные на телефоне"
                }
            } else {
                speechReady = false
                status = "Не удалось запустить голосовой движок"
            }
        }

        speechEngine = engine

        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (alive) {
                        mode = AlexMode.SPEAKING
                        status = "Алекс говорит…"
                    }
                }

                override fun onDone(utteranceId: String?) {
                    if (alive) {
                        mode = AlexMode.READY
                        status = "Готов к разговору"
                    }
                }

                @Deprecated("Deprecated in Android")
                override fun onError(utteranceId: String?) {
                    if (alive) {
                        mode = AlexMode.READY
                        status = "Ошибка воспроизведения голоса"
                    }
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int
                ) {
                    if (alive) {
                        mode = AlexMode.READY
                        status = "Ошибка воспроизведения голоса"
                    }
                }
            }
        )

        onDispose {
            alive = false
            engine.stop()
            engine.shutdown()
            speechEngine = null
            speechReady = false
        }
    }

    DisposableEffect(context) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onDispose { }
        } else {
            val speechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(context)

            recognizer = speechRecognizer

            speechRecognizer.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        mode = AlexMode.LISTENING
                        status = "Слушаю тебя…"
                    }

                    override fun onBeginningOfSpeech() {
                        status = "Я тебя слушаю"
                    }

                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        mode = AlexMode.THINKING
                        status = "Распознаю речь…"
                    }

                    override fun onError(error: Int) {
                        if (ignoreRecognizerErrors) return

                        mode = AlexMode.READY
                        status = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH ->
                                "Не расслышал. Попробуй ещё раз"

                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "Проверь сеть распознавания"

                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Нет разрешения на микрофон"

                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "Не услышал речь"

                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                                "Распознавание занято. Попробуй ещё раз"

                            else -> "Не удалось распознать речь"
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        if (ignoreRecognizerErrors) return

                        val spoken = results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()
                            .orEmpty()

                        if (spoken.isNotBlank()) {
                            askAlex(spoken)
                        } else {
                            mode = AlexMode.READY
                            status = "Не расслышал. Попробуй ещё раз"
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) = Unit

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) = Unit
                }
            )

            onDispose {
                speechRecognizer.cancel()
                speechRecognizer.destroy()
                recognizer = null
            }
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AlexBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(AlexPanel),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Голос Алекса",
                            tint = AlexLavender
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Алекс",
                            color = AlexWhite,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Голосовой ИИ · Vibe Chat",
                            color = AlexMuted,
                            fontSize = 12.sp
                        )
                    }

                    // Вернуться на экран чатов.
                    IconButton(
                        onClick = {
                            stopEverything()
                            onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад к чатам",
                            tint = AlexWhite
                        )
                    }

                    // Переключиться в текстовый чат с Алексом.
                    IconButton(
                        onClick = {
                            stopEverything()
                            onOpenChat()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "Открыть чат с Алексом",
                            tint = AlexCyan
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AlexEnergyOrb(
                            mode = mode,
                            modifier = Modifier.size(250.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = when (mode) {
                                AlexMode.LISTENING -> "Я тебя слушаю"
                                AlexMode.THINKING -> "Подбираю ответ…"
                                AlexMode.SPEAKING -> "Я говорю"
                                AlexMode.READY -> "Давай поговорим"
                            },
                            color = AlexWhite,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(7.dp))

                        Text(
                            text = status,
                            color = AlexMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(12.dp))

                        GradientVoiceWave(
                            active = mode != AlexMode.READY,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(55.dp)
                        )

                        Spacer(Modifier.height(10.dp))

                        Surface(
                            color = AlexPanel,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                if (userText.isNotBlank()) {
                                    Text(
                                        text = "ТЫ",
                                        color = AlexCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = userText,
                                        color = AlexWhite,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.height(10.dp))
                                }

                                Text(
                                    text = "АЛЕКС",
                                    color = AlexPink,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = alexText,
                                    color = AlexWhite,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (mode == AlexMode.LISTENING) {
                                recognizer?.stopListening()
                            } else if (mode != AlexMode.THINKING) {
                                val granted =
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED

                                if (granted) {
                                    startListening()
                                } else {
                                    microphonePermission.launch(
                                        Manifest.permission.RECORD_AUDIO
                                    )
                                }
                            }
                        },
                        enabled = mode != AlexMode.THINKING,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlexPurple,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(76.dp)
                    ) {
                        Icon(
                            imageVector = if (mode == AlexMode.LISTENING) {
                                Icons.Default.Stop
                            } else {
                                Icons.Default.Mic
                            },
                            contentDescription = if (mode == AlexMode.LISTENING) {
                                "Завершить запись"
                            } else {
                                "Говорить с Алексом"
                            },
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(Modifier.width(22.dp))

                    // Полная остановка: микрофон, генерация и голос.
                    IconButton(
                        onClick = { stopEverything() },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(AlexPanel)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Полностью остановить Алекса",
                            tint = AlexLavender,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(Modifier.height(9.dp))

                Text(
                    text = when (mode) {
                        AlexMode.LISTENING ->
                            "Нажми на микрофон, чтобы завершить запись"

                        AlexMode.THINKING ->
                            "Алекс готовит ответ — можно остановить кнопкой"

                        AlexMode.SPEAKING ->
                            "Кнопка справа останавливает голос"

                        AlexMode.READY ->
                            "Нажми на микрофон и говори по-русски"
                    },
                    color = AlexMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AlexEnergyOrb(
    mode: AlexMode,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "alex_orb")

    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    AlexMode.LISTENING -> 650
                    AlexMode.THINKING -> 420
                    AlexMode.SPEAKING -> 520
                    AlexMode.READY -> 1900
                }
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis =
                    if (mode == AlexMode.THINKING) 2200 else 8000
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )

    val glow = when (mode) {
        AlexMode.LISTENING -> AlexCyan
        AlexMode.THINKING -> AlexPink
        AlexMode.SPEAKING -> AlexLavender
        AlexMode.READY -> AlexPurple
    }

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val base = size.minDimension * 0.31f
        val radius = base * pulse

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glow.copy(alpha = 0.25f),
                    glow.copy(alpha = 0.09f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = radius * 2.5f
            ),
            radius = radius * 2.5f,
            center = Offset(cx, cy)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    AlexWhite.copy(alpha = 0.95f),
                    glow.copy(alpha = 0.8f),
                    AlexPurple.copy(alpha = 0.35f),
                    Color.Transparent
                ),
                center = Offset(
                    cx - radius * 0.3f,
                    cy - radius * 0.4f
                ),
                radius = radius * 1.8f
            ),
            radius = radius * 1.8f,
            center = Offset(cx, cy)
        )

        drawCircle(
            color = glow.copy(alpha = 0.62f),
            radius = radius * 1.42f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx())
        )

        drawCircle(
            color = AlexLavender.copy(alpha = 0.42f),
            radius = radius * 1.62f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx())
        )

        for (i in 0 until 8) {
            val angle = Math.toRadians(rotation + i * 45.0)
            val orbit = radius * if (i % 2 == 0) 1.48f else 1.68f
            val x = cx + cos(angle).toFloat() * orbit
            val y = cy + sin(angle).toFloat() * orbit
            val dotRadius = if (i % 2 == 0) 3.5f else 2.3f

            drawCircle(
                color = if (i % 2 == 0) glow else AlexWhite,
                radius = dotRadius.dp.toPx(),
                center = Offset(x, y)
            )
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFCF8FF),
                    AlexLavender,
                    glow.copy(alpha = 0.92f),
                    Color(0xFF30214F),
                    Color(0xFF100F20)
                ),
                center = Offset(
                    cx - radius * 0.28f,
                    cy - radius * 0.35f
                ),
                radius = radius * 1.65f
            ),
            radius = radius,
            center = Offset(cx, cy)
        )

        drawCircle(
            color = Color.White.copy(alpha = 0.52f),
            radius = radius * 0.14f,
            center = Offset(
                cx - radius * 0.38f,
                cy - radius * 0.43f
            )
        )

        drawArc(
            color = Color.White.copy(alpha = 0.65f),
            startAngle = rotation,
            sweepAngle = 85f,
            useCenter = false,
            topLeft = Offset(
                cx - radius * 1.15f,
                cy - radius * 1.15f
            ),
            size = Size(radius * 2.3f, radius * 2.3f),
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

@Composable
private fun GradientVoiceWave(
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "alex_voice_wave")

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (active) 600 else 2200
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val colors = listOf(
        AlexPurple,
        AlexLavender,
        AlexPink,
        AlexCyan,
        AlexPink,
        AlexLavender,
        AlexPurple
    )

    Canvas(modifier = modifier) {
        val count = 29
        val barWidth = 4.dp.toPx()
        val gap = (size.width - count * barWidth) / (count + 1)
        val centerY = size.height / 2f
        val maxHeight = size.height * 0.82f
        val brush = Brush.horizontalGradient(colors)

        for (i in 0 until count) {
            val x = gap + i * (barWidth + gap)
            val wave = abs(sin(phase + i * 0.43f))
            val envelope = 0.18f + 0.82f * abs(
                sin(i * 0.21f + 0.8f)
            )

            val movement = if (active) {
                wave
            } else {
                0.18f + wave * 0.24f
            }

            val barHeight =
                (0.10f + movement * envelope) * maxHeight

            drawRoundRect(
                brush = brush,
                topLeft = Offset(
                    x,
                    centerY - barHeight / 2f
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(
                    barWidth / 2f,
                    barWidth / 2f
                )
            )
        }
    }
}
