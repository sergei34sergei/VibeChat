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
import android.view.Gravity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.google.firebase.ai.GenerativeBackend
import com.google.firebase.ai.ai
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val AlexBackground = Color(0xFF101113)
private val AlexPanel = Color(0xFF1B1C20)
private val AlexPurple = Color(0xFF9B86D8)
private val AlexLavender = Color(0xFFB9A2F5)
private val AlexPink = Color(0xFFE1A2C2)
private val AlexWhite = Color(0xFFF4F2F8)
private val AlexMuted = Color(0xFFA5A5AF)

@Composable
fun AlexVoiceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Готов к разговору") }
    var isListening by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var userText by remember { mutableStateOf("") }
    var alexText by remember {
        mutableStateOf("Привет! Я Алекс. Нажми на микрофон и поговорим.")
    }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var speechEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var speechReady by remember { mutableStateOf(false) }

    val conversation = remember {
        mutableStateListOf<Pair<String, String>>()
    }

    val model = remember {
        Firebase.ai(GenerativeBackend.googleAI())
            .generativeModel("gemini-3.8-flash")
    }

    fun stopSpeaking() {
        speechEngine?.stop()
        isSpeaking = false
    }

    fun speakAnswer(text: String) {
        val engine = speechEngine

        if (engine == null || !speechReady) {
            status = "Голосовой движок ещё загружается"
            return
        }

        engine.stop()
        engine.setPitch(0.92f)
        engine.setSpeechRate(0.95f)

        isSpeaking = true
        status = "Алекс говорит"

        val result = engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            "ALEX_REPLY"
        )

        if (result == TextToSpeech.ERROR) {
            isSpeaking = false
            status = "Не удалось воспроизвести голос"
        }
    }

    fun askAlex(message: String) {
        if (message.isBlank() || isThinking) return

        userText = message
        conversation.add("Ты" to message)
        isListening = false
        isThinking = true
        status = "Алекс думает"

        scope.launch {
            try {
                val history = conversation
                    .takeLast(16)
                    .joinToString("\n") { (speaker, text) ->
                        "$speaker: $text"
                    }

                val prompt = """
                    Ты — Алекс, дружелюбный голосовой помощник
                    внутри Android-приложения Vibe Chat.
                    Отвечай естественным современным русским языком.
                    Говори понятно, тепло и по делу.
                    Не утверждай, что можешь выполнять действия,
                    к которым у тебя нет подключённого доступа.
                    Отвечай так, чтобы твой текст было удобно
                    произносить вслух.

                    История разговора:
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
                isThinking = false
                speakAnswer(answer)
            } catch (error: Exception) {
                isThinking = false
                status = "Не удалось получить ответ"
                alexText = "Братан, сейчас не получилось ответить. Проверь интернет и попробуй ещё раз."
            }
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status = "На телефоне нет службы распознавания речи"
            return
        }

        stopSpeaking()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говори по-русски")
        }

        try {
            isListening = true
            status = "Слушаю тебя…"
            recognizer?.startListening(intent)
                ?: run {
                    isListening = false
                    status = "Микрофон пока не готов"
                }
        } catch (error: Exception) {
            isListening = false
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
        val engine = TextToSpeech(context) { result ->
            if (result == TextToSpeech.SUCCESS) {
                val localeResult = speechEngine?.setLanguage(
                    Locale("ru", "RU")
                )
                speechReady =
                    localeResult != TextToSpeech.LANG_MISSING_DATA &&
                    localeResult != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }

        speechEngine = engine

        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    status = "Готов к разговору"
                }

                @Deprecated("Deprecated in Android")
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    status = "Ошибка воспроизведения"
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int
                ) {
                    isSpeaking = false
                    status = "Ошибка воспроизведения"
                }
            }
        )

        onDispose {
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
                        status = "Слушаю тебя…"
                    }

                    override fun onBeginningOfSpeech() {
                        status = "Слушаю тебя…"
                    }

                    override fun onRmsChanged(rmsdB: Float) = Unit

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        isListening = false
                        status = "Обрабатываю речь"
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        status = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH ->
                                "Не расслышал. Попробуй ещё раз"
                            SpeechRecognizer.ERROR_NETWORK ->
                                "Проблема с сетью распознавания"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Нет разрешения на микрофон"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "Не услышал речь"
                            else -> "Не удалось распознать речь"
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false

                        val spoken = results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()
                            .orEmpty()

                        if (spoken.isNotBlank()) {
                            askAlex(spoken)
                        } else {
                            status = "Не расслышал. Попробуй ещё раз"
                        }
                    }

                    override fun onPartialResults(
                        partialResults: Bundle?
                    ) = Unit

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

    LaunchedEffect(Unit) {
        status = "Готов к разговору"
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
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(AlexPanel),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Алекс",
                            tint = AlexLavender
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Алекс",
                            color = AlexWhite,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Голосовой помощник",
                            color = AlexMuted,
                            fontSize = 13.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            stopSpeaking()
                            recognizer?.cancel()
                            isListening = false
                            status = "Готов к разговору"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Остановить",
                            tint = AlexWhite
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

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
                        Box(
                            modifier = Modifier.size(170.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawCircle(
                                    brush = Brush.linearGradient(
                                        listOf(
                                            AlexPurple,
                                            AlexPink,
                                            AlexLavender
                                        )
                                    ),
                                    radius = size.minDimension / 2f,
                                    style = Stroke(width = 2.dp.toPx())
                                )

                                drawCircle(
                                    color = AlexPurple.copy(alpha = 0.12f),
                                    radius = size.minDimension * 0.42f
                                )

                                drawCircle(
                                    brush = Brush.linearGradient(
                                        listOf(
                                            Color(0xFF352842),
                                            Color(0xFF211D2A)
                                        )
                                    ),
                                    radius = size.minDimension * 0.36f
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = AlexLavender,
                                    modifier = Modifier.size(42.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "ALEX",
                                    color = AlexWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 3.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(22.dp))

                        Text(
                            text = when {
                                isListening -> "Я тебя слушаю"
                                isThinking -> "Подбираю ответ…"
                                isSpeaking -> "Я говорю"
                                else -> "Давай поговорим"
                            },
                            color = AlexWhite,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = status,
                            color = AlexMuted,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(22.dp))

                        GradientVoiceWave(
                            active = isListening || isThinking || isSpeaking,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(76.dp)
                        )

                        Spacer(Modifier.height(18.dp))

                        Surface(
                            color = AlexPanel,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp)
                            ) {
                                if (userText.isNotBlank()) {
                                    Text(
                                        text = "ТЫ",
                                        color = AlexLavender,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = userText,
                                        color = AlexWhite,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.height(12.dp))
                                }

                                Text(
                                    text = "АЛЕКС",
                                    color = AlexPink,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = alexText,
                                    color = AlexWhite,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (isListening) {
                                recognizer?.stopListening()
                            } else if (!isThinking) {
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
                        enabled = !isThinking,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlexPurple,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(76.dp)
                    ) {
                        Icon(
                            imageVector = if (isListening) {
                                Icons.Default.Stop
                            } else {
                                Icons.Default.Mic
                            },
                            contentDescription = if (isListening) {
                                "Остановить запись"
                            } else {
                                "Говорить"
                            },
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(Modifier.width(22.dp))

                    IconButton(
                        onClick = { stopSpeaking() },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(AlexPanel)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Остановить голос",
                            tint = AlexLavender
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = if (isListening) {
                        "Нажми ещё раз, когда закончишь говорить"
                    } else {
                        "Нажми на микрофон, чтобы начать"
                    },
                    color = AlexMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
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
                durationMillis = if (active) 700 else 2200
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val colors = listOf(
        Color(0xFF8F7CCB),
        Color(0xFF9B86D8),
        Color(0xFFAA8FD5),
        Color(0xFFBD92D0),
        Color(0xFFD39BC7),
        Color(0xFFE1A2C2),
        Color(0xFFD39BC7),
        Color(0xFFBD92D0),
        Color(0xFFAA8FD5),
        Color(0xFF9B86D8)
    )

    Canvas(modifier = modifier) {
        val count = 25
        val barWidth = 5.dp.toPx()
        val gap = (size.width - count * barWidth) / (count + 1)
        val centerY = size.height / 2f
        val maxHeight = size.height * 0.78f
        val brush = Brush.horizontalGradient(colors)

        for (i in 0 until count) {
            val x = gap + i * (barWidth + gap)
            val wave = kotlin.math.abs(
                kotlin.math.sin(
                    phase + i * 0.48f
                )
            )

            val envelope = 0.22f +
                0.78f * kotlin.math.abs(
                    kotlin.math.sin(i * 0.22f + 0.7f)
                )

            val movement = if (active) wave else 0.25f + wave * 0.25f
            val barHeight = (0.12f + movement * envelope) * maxHeight

            drawRoundRect(
                brush = brush,
                topLeft = Offset(
                    x = x,
                    y = centerY - barHeight / 2f
                ),
                size = Size(
                    width = barWidth,
                    height = barHeight
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    barWidth / 2f,
                    barWidth / 2f
                )
            )
        }
    }
}
