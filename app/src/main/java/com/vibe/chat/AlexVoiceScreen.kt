
package com.vibe.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeBackend
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private data class AlexMessage(
    val id: String = UUID.randomUUID().toString(),
    val fromAlex: Boolean,
    val text: String
)

private val AlexBackground = Color(0xFF08091A)
private val AlexPurple = Color(0xFF995CFF)
private val AlexBlue = Color(0xFF45C8FF)
private val AlexPink = Color(0xFFFF83E8)

@Composable
fun AlexVoiceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var chatOpen by rememberSaveable { mutableStateOf(false) }
    var voiceActive by rememberSaveable { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable {
        mutableStateOf("Я Алекс. Рад тебя слышать.")
    }

    val messages = remember { mutableStateListOf<AlexMessage>() }

    var speechRecognizer by remember {
        mutableStateOf<SpeechRecognizer?>(null)
    }
    var textToSpeech by remember {
        mutableStateOf<TextToSpeech?>(null)
    }

    val latestVoiceActive by rememberUpdatedState(voiceActive)
    val latestListening by rememberUpdatedState(listening)

    val sendMessage: (String, Boolean) -> Unit = { rawText, speakReply ->
        val userText = rawText.trim()

        if (userText.isNotEmpty() && !busy) {
            draft = ""
            listening = false
            busy = true
            status = "Алекс думает…"

            messages.add(
                AlexMessage(fromAlex = false, text = userText)
            )

            scope.launch {
                try {
                    val history = messages
                        .takeLast(16)
                        .joinToString("\n") { message ->
                            val speaker =
                                if (message.fromAlex) "Алекс" else "Пользователь"
                            "$speaker: ${message.text}"
                        }

                    val prompt = """
                        Ты Алекс — дружелюбный голосовой ИИ-помощник
                        в приложении Vibe Chat.
                        Отвечай естественно, тепло и по-русски.
                        Учитывай историю диалога.
                        Не утверждай, что можешь выполнять действия,
                        если у тебя нет соответствующего инструмента.

                        История разговора:
                        $history

                        Ответь на последнее сообщение пользователя.
                    """.trimIndent()

                    val model = Firebase.ai(
                        backend = GenerativeBackend.googleAI()
                    ).generativeModel("gemini-3.8-flash")

                    val response = model.generateContent(prompt)
                    val answer = response.text
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: "Я пока не смог сформулировать ответ. Попробуй ещё раз."

                    messages.add(
                        AlexMessage(fromAlex = true, text = answer)
                    )

                    status = if (speakReply) {
                        "Алекс отвечает…"
                    } else {
                        "Я готов продолжать разговор."
                    }

                    if (speakReply) {
                        speaking = true
                        val tts = textToSpeech

                        if (tts != null) {
                            tts.speak(
                                answer,
                                TextToSpeech.QUEUE_FLUSH,
                                Bundle(),
                                UUID.randomUUID().toString()
                            )
                        } else {
                            speaking = false
                            status = "Озвучка пока недоступна."
                        }
                    }
                } catch (e: Exception) {
                    messages.add(
                        AlexMessage(
                            fromAlex = true,
                            text = "Не получилось получить ответ. Проверь интернет и настройки подключения Gemini, затем попробуй снова."
                        )
                    )
                    status = "Не удалось подключиться."
                } finally {
                    busy = false
                }
            }
        }
    }

    val latestSendMessage by rememberUpdatedState(sendMessage)

    val startListening: () -> Unit = {
        if (voiceActive && !busy && !speaking) {
            val recognizer = speechRecognizer

            if (recognizer == null) {
                status = "Распознавание речи недоступно на этом устройстве."
                listening = false
            } else {
                try {
                    val intent = Intent(
                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                    ).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE,
                            "ru-RU"
                        )
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                            "ru-RU"
                        )
                        putExtra(
                            RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                            false
                        )
                        putExtra(
                            RecognizerIntent.EXTRA_MAX_RESULTS,
                            1
                        )
                    }

                    status = "Слушаю тебя…"
                    listening = true
                    recognizer.startListening(intent)
                } catch (e: Exception) {
                    listening = false
                    status = "Не удалось включить микрофон."
                }
            }
        }
    }

    val latestStartListening by rememberUpdatedState(startListening)

    val microphonePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startListening()
            } else {
                voiceActive = false
                listening = false
                status = "Разреши доступ к микрофону в настройках приложения."
            }
        }

    val beginVoiceConversation: () -> Unit = {
        voiceActive = true
        chatOpen = false

        val permissionGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            startListening()
        } else {
            microphonePermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    val latestBeginVoice by rememberUpdatedState(beginVoiceConversation)

    DisposableEffect(context) {
        val recognizer = if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }

        speechRecognizer = recognizer

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                status = "Слушаю тебя…"
            }

            override fun onBeginningOfSpeech() {
                status = "Я тебя слышу…"
            }

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                listening = false
                status = "Обрабатываю речь…"
            }

            override fun onError(error: Int) {
                listening = false

                if (latestVoiceActive && !busy && !speaking) {
                    status = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "Не расслышал. Попробуй сказать ещё раз."

                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "Микрофон занят. Попробуй ещё раз."

                        else -> "Не удалось распознать речь. Нажми кнопку и попробуй снова."
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false

                val recognizedText = results
                    ?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    ?.firstOrNull()
                    ?.trim()

                if (!recognizedText.isNullOrEmpty()) {
                    latestSendMessage(recognizedText, true)
                } else {
                    status = "Я не расслышал слова."
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val tts = TextToSpeech(context) { result ->
            if (result == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("ru", "RU")
            }
        }

        textToSpeech = tts

        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    speaking = true
                    status = "Алекс говорит…"
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        speaking = false

                        if (latestVoiceActive) {
                            status = "Теперь твоя очередь говорить…"
                            mainHandler.postDelayed(
                                { latestStartListening() },
                                650
                            )
                        } else {
                            status = "Я готов продолжать разговор."
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        speaking = false
                        status = "Не удалось озвучить ответ."
                    }
                }
            }
        )

        onDispose {
            try {
                recognizer?.destroy()
            } catch (_: Exception) {
            }

            try {
                tts.stop()
                tts.shutdown()
            } catch (_: Exception) {
            }

            speechRecognizer = null
            textToSpeech = null
        }
    }

    if (chatOpen) {
        AlexTextChat(
            messages = messages,
            draft = draft,
            onDraftChange = { draft = it },
            busy = busy,
            onBack = { chatOpen = false },
            onSend = { sendMessage(draft, false) },
            onVoice = { latestBeginVoice() }
        )
    } else {
        AlexGalaxyHome(
            status = status,
            listening = listening,
            speaking = speaking,
            busy = busy,
            voiceActive = voiceActive,
            onStartVoice = { latestBeginVoice() },
            onStopVoice = {
                voiceActive = false
                listening = false
                speaking = false

                try {
                    speechRecognizer?.cancel()
                    textToSpeech?.stop()
                } catch (_: Exception) {
                }

                status = "Разговор завершён. Я рядом, когда понадоблюсь."
            },
            onOpenChat = { chatOpen = true },
            onTapOrb = {
                if (voiceActive) {
                    if (listening) {
                        try {
                            speechRecognizer?.stopListening()
                        } catch (_: Exception) {
                        }
                    } else if (!speaking && !busy) {
                        startListening()
                    }
                } else {
                    latestBeginVoice()
                }
            }
        )
    }
}

@Composable
private fun AlexGalaxyHome(
    status: String,
    listening: Boolean,
    speaking: Boolean,
    busy: Boolean,
    voiceActive: Boolean,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onOpenChat: () -> Unit,
    onTapOrb: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF17102F),
                        Color(0xFF0B0D21),
                        Color(0xFF050712)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        GalaxyStars(
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "VIBE  •  AI",
                    color = Color(0xFFBCA9FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Алекс",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Твой голосовой помощник",
                    color = Color(0xFFB6B6D1),
                    fontSize = 14.sp
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GalaxyOrb(
                        modifier = Modifier.fillMaxSize(),
                        active = voiceActive,
                        speaking = speaking,
                        listening = listening
                    )

                    Surface(
                        modifier = Modifier
                            .size(116.dp)
                            .shadow(
                                elevation = 24.dp,
                                shape = CircleShape,
                                ambientColor = AlexPurple,
                                spotColor = AlexBlue
                            )
                            .clip(CircleShape)
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.5f),
                                shape = CircleShape
                            ),
                        color = Color(0xFF34246B).copy(alpha = 0.9f),
                        shape = CircleShape,
                        onClick = onTapOrb
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "A",
                                color = Color.White,
                                fontSize = 58.sp,
                                fontWeight = FontWeight.Light
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = when {
                        speaking -> "Алекс говорит"
                        listening -> "Я слушаю тебя"
                        busy -> "Подбираю ответ"
                        voiceActive -> "Говори, я слушаю"
                        else -> "Нажми и поговорим"
                    },
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = status,
                    color = Color(0xFFBFC5E8),
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        if (voiceActive) onStopVoice() else onStartVoice()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (voiceActive) {
                            Color(0xFF3A254F)
                        } else {
                            Color(0xFF7853E8)
                        }
                    )
                ) {
                    Text(
                        text = if (voiceActive) {
                            "Завершить разговор"
                        } else {
                            "🎙  Начать разговор"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onOpenChat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color(0xFF8270C9).copy(alpha = 0.8f)
                    )
                ) {
                    Text(
                        text = "💬  Открыть текстовый чат",
                        fontSize = 15.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Текстовый и голосовой режимы",
                    color = Color(0xFF7E83A9),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun GalaxyOrb(
    modifier: Modifier = Modifier,
    active: Boolean,
    speaking: Boolean,
    listening: Boolean
) {
    val transition = rememberInfiniteTransition(label = "galaxy")

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (speaking) 3500 else 9000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = if (active) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (listening) 700 else 1600,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension * 0.28f
        val glowRadius = baseRadius * pulse

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    AlexBlue.copy(alpha = if (active) 0.34f else 0.2f),
                    AlexPurple.copy(alpha = 0.18f),
                    Color.Transparent
                ),
                center = center,
                radius = size.minDimension * 0.49f
            ),
            radius = size.minDimension * 0.49f,
            center = center
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFD7FF).copy(alpha = 0.85f),
                    AlexPink.copy(alpha = 0.7f),
                    AlexPurple.copy(alpha = 0.65f),
                    Color(0xFF283C9D).copy(alpha = 0.2f),
                    Color.Transparent
                ),
                center = center,
                radius = glowRadius * 1.8f
            ),
            radius = glowRadius * 1.8f,
            center = center
        )

        rotate(rotation, center) {
            drawOval(
                color = AlexBlue.copy(alpha = 0.75f),
                topLeft = Offset(
                    center.x - baseRadius * 1.5f,
                    center.y - baseRadius * 0.48f
                ),
                size = Size(baseRadius * 3f, baseRadius * 0.96f),
                style = Stroke(width = 2.dp.toPx())
            )

            drawOval(
                color = AlexPink.copy(alpha = 0.7f),
                topLeft = Offset(
                    center.x - baseRadius * 1.35f,
                    center.y - baseRadius * 0.72f
                ),
                size = Size(baseRadius * 2.7f, baseRadius * 1.44f),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        rotate(-rotation * 0.65f, center) {
            drawOval(
                color = Color(0xFFBCA2FF).copy(alpha = 0.65f),
                topLeft = Offset(
                    center.x - baseRadius * 1.22f,
                    center.y - baseRadius * 0.86f
                ),
                size = Size(baseRadius * 2.44f, baseRadius * 1.72f),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        for (i in 0 until 24) {
            val angle = i * (Math.PI * 2.0 / 24.0) + rotation * 0.012
            val radius = baseRadius * (1.15f + (i % 4) * 0.16f)
            val x = center.x + kotlin.math.cos(angle).toFloat() * radius
            val y = center.y + kotlin.math.sin(angle).toFloat() * radius
            val dotSize = if (i % 3 == 0) 3.2f else 1.7f

            drawCircle(
                color = if (i % 2 == 0) {
                    AlexBlue.copy(alpha = 0.9f)
                } else {
                    AlexPink.copy(alpha = 0.85f)
                },
                radius = dotSize,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
private fun GalaxyStars(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stars = listOf(
            Offset(0.12f, 0.12f),
            Offset(0.83f, 0.17f),
            Offset(0.74f, 0.31f),
            Offset(0.18f, 0.42f),
            Offset(0.88f, 0.58f),
            Offset(0.1f, 0.71f),
            Offset(0.67f, 0.79f),
            Offset(0.34f, 0.88f),
            Offset(0.91f, 0.91f),
            Offset(0.48f, 0.2f),
            Offset(0.26f, 0.61f),
            Offset(0.58f, 0.94f)
        )

        stars.forEachIndexed { index, point ->
            drawCircle(
                color = if (index % 2 == 0) {
                    AlexBlue.copy(alpha = 0.48f)
                } else {
                    AlexPink.copy(alpha = 0.42f)
                },
                radius = if (index % 3 == 0) 2.2f else 1.2f,
                center = Offset(
                    point.x * size.width,
                    point.y * size.height
                )
            )
        }
    }
}

@Composable
private fun AlexTextChat(
    messages: List<AlexMessage>,
    draft: String,
    onDraftChange: (String) -> Unit,
    busy: Boolean,
    onBack: () -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, busy) {
        val lastIndex = messages.lastIndex
        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF11102A),
                        Color(0xFF090B19),
                        Color(0xFF070812)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("‹  Назад", color = Color.White)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Чат с Алексом",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Vibe AI",
                    color = Color(0xFFAAA0E9),
                    fontSize = 12.sp
                )
            }

            OutlinedButton(
                onClick = onVoice,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("🎙", fontSize = 18.sp)
            }
        }

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        AlexPink,
                                        AlexPurple,
                                        Color(0xFF24214C)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "A",
                            color = Color.White,
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Light
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    Text(
                        text = "Привет! Я Алекс.",
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Напиши мне или перейди в голосовой режим.",
                        color = Color(0xFFB9B8D4),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.fromAlex) {
                            Arrangement.Start
                        } else {
                            Arrangement.End
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = if (message.fromAlex) 5.dp else 18.dp,
                                        bottomEnd = if (message.fromAlex) 18.dp else 5.dp
                                    )
                                )
                                .background(
                                    if (message.fromAlex) {
                                        Color(0xFF20203D)
                                    } else {
                                        Color(0xFF6247B7)
                                    }
                                )
                                .padding(14.dp)
                        ) {
                            Text(
                                text = if (message.fromAlex) "Алекс" else "Ты",
                                color = if (message.fromAlex) {
                                    Color(0xFFBDAEFF)
                                } else {
                                    Color(0xFFE8DFFF)
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(5.dp))

                            Text(
                                text = message.text,
                                color = Color.White,
                                fontSize = 15.sp,
                                lineHeight = 21.sp
                            )
                        }
                    }
                }

                if (busy) {
                    item(key = "alex-thinking") {
                        Text(
                            text = "Алекс думает…",
                            color = Color(0xFFBBAAFF),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Напиши сообщение…",
                        color = Color(0xFF9D9BB8)
                    )
                },
                enabled = !busy,
                shape = RoundedCornerShape(22.dp),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { onSend() }
                ),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF17172F),
                    unfocusedContainerColor = Color(0xFF17172F),
                    focusedIndicatorColor = AlexPurple,
                    unfocusedIndicatorColor = Color(0xFF474267),
                    cursorColor = AlexBlue
                )
            )

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = onSend,
                enabled = draft.isNotBlank() && !busy,
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlexPurple
                )
            ) {
                Text("➤", fontSize = 22.sp)
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}
