package com.vibe.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Locale

private val AlexBg = Color(0xFF070A16)
private val AlexCard = Color(0xFF11182B)
private val AlexCardLight = Color(0xFF19233C)
private val AlexCyan = Color(0xFF55E8FF)
private val AlexBlue = Color(0xFF438BFF)
private val AlexPurple = Color(0xFF9B68FF)
private val AlexWhite = Color(0xFFF5F7FF)
private val AlexMuted = Color(0xFF9CA9C9)
private val AlexError = Color(0xFFFF8196)

private data class AlexMessage(
    val role: String,
    val text: String
)

@Composable
fun AlexVoiceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val messages = remember {
        mutableStateListOf(
            AlexMessage(
                "assistant",
                "Привет! Я Алекс — твой AI-помощник. Можем общаться здесь или голосом. Чем займёмся?"
            )
        )
    }

    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var voiceActive by remember { mutableStateOf(false) }
    var voicePaused by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val latestVoiceActive by rememberUpdatedState(voiceActive)
    val latestVoicePaused by rememberUpdatedState(voicePaused)
    val latestBusy by rememberUpdatedState(busy)
    val latestSpeaking by rememberUpdatedState(speaking)

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    val startListeningRef = remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        if (granted && voiceActive && !voicePaused) {
            startListeningRef.value?.invoke()
        }
    }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("ru", "RU"))
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        tts = engine

        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }

        onDispose {
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null

            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }

    fun speak(text: String) {
        val engine = tts
        if (engine == null || !ttsReady || !voiceActive || voicePaused) return

        speaking = true
        engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "alex_reply_${System.currentTimeMillis()}"
        )
    }

    fun startListening() {
        if (!voiceActive || voicePaused || busy || speaking) return

        if (
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        val speechRecognizer = recognizer
        if (speechRecognizer == null) {
            messages.add(
                AlexMessage(
                    "assistant",
                    "Распознавание речи недоступно на этом устройстве. Проверь приложение Google и настройки голосового ввода."
                )
            )
            return
        }

        try {
            listening = true
            speechRecognizer.startListening(speechIntent)
        } catch (e: Exception) {
            listening = false
            messages.add(
                AlexMessage(
                    "assistant",
                    "Не удалось запустить микрофон: ${e.localizedMessage ?: "неизвестная ошибка"}"
                )
            )
        }
    }

    startListeningRef.value = { startListening() }

    DisposableEffect(recognizer) {
        val speechRecognizer = recognizer

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
            }

            override fun onBeginningOfSpeech() {
                listening = true
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                listening = false
            }

            override fun onError(error: Int) {
                listening = false

                if (
                    latestVoiceActive &&
                    !latestVoicePaused &&
                    !latestBusy &&
                    !latestSpeaking
                ) {
                    scope.launch {
                        delay(900)
                        startListeningRef.value?.invoke()
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val spokenText = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()

                if (!spokenText.isNullOrBlank() && latestVoiceActive && !latestVoicePaused) {
                    draft = spokenText
                    scope.launch {
                        delay(150)
                        startListeningRef.value?.let {
                            // Отправка сообщения выполняется основной кнопкой отправки.
                        }
                    }
                } else if (latestVoiceActive && !latestVoicePaused) {
                    scope.launch {
                        delay(500)
                        startListeningRef.value?.invoke()
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        speechRecognizer?.setRecognitionListener(listener)

        onDispose {
            speechRecognizer?.cancel()
        }
    }

    fun sendMessage(text: String) {
        val question = text.trim()
        if (question.isBlank() || busy) return

        draft = ""
        messages.add(AlexMessage("user", question))
        busy = true
        listening = false

        scope.launch {
            try {
                val history = messages
                    .dropLast(1)
                    .takeLast(16)
                    .joinToString("\n") { message ->
                        val label = if (message.role == "user") "Пользователь" else "Алекс"
                        "$label: ${message.text}"
                    }

                val prompt = """
                    Ты — Алекс, дружелюбный AI-помощник внутри приложения Vibe Chat.
                    Отвечай по-русски, естественно, понятно и по делу.
                    Не утверждай, что можешь выполнять действия, которых не выполнял.
                    Если не знаешь ответа, честно скажи об этом.

                    История разговора:
                    $history

                    Новое сообщение пользователя:
                    $question

                    Ответь на последнее сообщение пользователя.
                """.trimIndent()

                val model = Firebase.ai(
                    backend = GenerativeBackend.googleAI()
                ).generativeModel("gemini-3.8-flash")

                val response = withTimeout(45_000L) {
                    model.generateContent(prompt)
                }

                val answer = response.text?.trim()

                if (answer.isNullOrBlank()) {
                    messages.add(
                        AlexMessage(
                            "assistant",
                            "Я получил пустой ответ от AI. Попробуй задать вопрос ещё раз."
                        )
                    )
                } else {
                    messages.add(AlexMessage("assistant", answer))
                    if (voiceActive && !voicePaused) {
                        speak(answer)
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                messages.add(
                    AlexMessage(
                        "assistant",
                        "Ответ не пришёл за 45 секунд. Проверь интернет и доступность модели, затем попробуй ещё раз."
                    )
                )
            } catch (e: Exception) {
                val details = e.localizedMessage
                    ?.takeIf { it.isNotBlank() }
                    ?: e.javaClass.simpleName

                messages.add(
                    AlexMessage(
                        "assistant",
                        "Не удалось получить ответ AI.\n\nПодробности: $details"
                    )
                )
            } finally {
                busy = false
                if (voiceActive && !voicePaused && !speaking) {
                    delay(900)
                    startListeningRef.value?.invoke()
                }
            }
        }
    }

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    fun stopVoice() {
        voiceActive = false
        voicePaused = false
        listening = false
        speaking = false
        recognizer?.cancel()
        tts?.stop()
    }

    fun toggleVoice() {
        if (voiceActive) {
            stopVoice()
        } else {
            voiceActive = true
            voicePaused = false
            if (
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startListening()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0B1022),
                        AlexBg,
                        Color(0xFF090D1D)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .shadow(14.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(AlexCyan, AlexBlue, AlexPurple)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("A", color = AlexBg, fontSize = 25.sp, fontWeight = FontWeight.Black)
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Алекс AI",
                        color = AlexWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (busy) AlexPurple else AlexCyan)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (busy) "Думаю над ответом…" else "Твой AI-помощник",
                            color = AlexMuted,
                            fontSize = 12.sp
                        )
                    }
                }

                TextButton(onClick = { showClearDialog = true }) {
                    Text("Очистить", color = AlexCyan, fontSize = 12.sp)
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .clickable { toggleVoice() },
                shape = RoundedCornerShape(22.dp),
                color = AlexCard,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    AlexCyan.copy(alpha = 0.22f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        AlexCyan.copy(alpha = 0.24f),
                                        AlexPurple.copy(alpha = 0.24f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("♫", color = AlexCyan, fontSize = 24.sp)
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Голосовой режим",
                            color = AlexWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            "Общайся с Алексом голосом",
                            color = AlexMuted,
                            fontSize = 12.sp
                        )
                    }

                    Text("ОТКРЫТЬ  ›", color = AlexCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 4.dp,
                    bottom = 16.dp
                )
            ) {
                items(messages) { message ->
                    AlexMessageBubble(message)
                }

                if (busy) {
                    item {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(AlexCard)
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(17.dp),
                                color = AlexCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Алекс формирует ответ", color = AlexMuted, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (permissionDenied) {
                Text(
                    "Для голосового режима разреши доступ к микрофону в настройках Android.",
                    color = AlexError,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp)
                    .imePadding()
                    .clip(RoundedCornerShape(24.dp))
                    .background(AlexCardLight)
                    .border(1.dp, AlexCyan.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        color = AlexWhite,
                        fontSize = 15.sp
                    ),
                    cursorBrush = Brush.linearGradient(listOf(AlexCyan, AlexPurple)),
                    decorationBox = { innerTextField ->
                        Box {
                            if (draft.isEmpty()) {
                                Text("Напиши Алексу…", color = AlexMuted, fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(AlexCyan, AlexBlue, AlexPurple))
                        )
                        .clickable(enabled = !busy) { sendMessage(draft) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("➤", color = AlexBg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (voiceActive) {
        Dialog(
            onDismissRequest = { stopVoice() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AlexVoiceOrbScreen(
                listening = listening,
                speaking = speaking,
                busy = busy,
                paused = voicePaused,
                onTogglePause = {
                    if (voicePaused) {
                        voicePaused = false
                        startListening()
                    } else {
                        voicePaused = true
                        listening = false
                        speaking = false
                        recognizer?.cancel()
                        tts?.stop()
                    }
                },
                onClose = { stopVoice() },
                onTypeMessage = {
                    stopVoice()
                }
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = AlexCard,
            title = {
                Text("Очистить переписку?", color = AlexWhite)
            },
            text = {
                Text(
                    "История на этом экране будет удалена.",
                    color = AlexMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        messages.clear()
                        messages.add(
                            AlexMessage(
                                "assistant",
                                "Чат очищен. Я готов к новому разговору!"
                            )
                        )
                        showClearDialog = false
                    }
                ) {
                    Text("Очистить", color = AlexCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Отмена", color = AlexMuted)
                }
            }
        )
    }
}

@Composable
private fun AlexMessageBubble(message: AlexMessage) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp, top = 2.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(AlexCyan, AlexBlue, AlexPurple))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("A", color = AlexBg, fontSize = 15.sp, fontWeight = FontWeight.Black)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 5.dp,
                        bottomEnd = if (isUser) 5.dp else 20.dp
                    )
                )
                .background(
                    if (isUser) {
                        Brush.linearGradient(listOf(Color(0xFF174E72), Color(0xFF34306C)))
                    } else {
                        Brush.linearGradient(listOf(AlexCard, Color(0xFF151D34)))
                    }
                )
                .border(
                    1.dp,
                    if (isUser) AlexCyan.copy(alpha = 0.2f)
                    else Color.White.copy(alpha = 0.05f),
                    RoundedCornerShape(20.dp)
                )
                .padding(14.dp)
        ) {
            Text(
                if (isUser) "ТЫ" else "АЛЕКС",
                color = if (isUser) AlexCyan else AlexPurple,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(5.dp))
            Text(
                message.text,
                color = AlexWhite,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun AlexVoiceOrbScreen(
    listening: Boolean,
    speaking: Boolean,
    busy: Boolean,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onClose: () -> Unit,
    onTypeMessage: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "alex_orb")
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "rotation"
    )

    val status = when {
        paused -> "ПАУЗА"
        busy -> "АЛЕКС ДУМАЕТ"
        speaking -> "АЛЕКС ГОВОРИТ"
        listening -> "Я ТЕБЯ СЛУШАЮ"
        else -> "ГОТОВ К ОБЩЕНИЮ"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF080B1C), Color(0xFF10112C), Color(0xFF070A16))
                )
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(24.dp)
    ) {
        Text(
            "VIBE  •  ALEX AI",
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
            color = AlexMuted,
            fontSize = 11.sp,
            letterSpacing = 3.sp,
            fontWeight = FontWeight.Bold
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(290.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val base = size.minDimension * 0.32f

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AlexCyan.copy(alpha = 0.13f),
                                AlexBlue.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = base * 2.0f
                        ),
                        radius = base * 2.0f,
                        center = center
                    )

                    drawCircle(
                        color = AlexCyan.copy(alpha = 0.42f),
                        radius = base * pulse,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    drawCircle(
                        color = AlexPurple.copy(alpha = 0.62f),
                        radius = base * 1.28f,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    drawArc(
                        color = AlexCyan.copy(alpha = 0.9f),
                        startAngle = rotation,
                        sweepAngle = 105f,
                        useCenter = false,
                        topLeft = Offset(center.x - base * 1.55f, center.y - base * 1.55f),
                        size = androidx.compose.ui.geometry.Size(base * 3.1f, base * 3.1f),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    drawArc(
                        color = AlexPurple.copy(alpha = 0.85f),
                        startAngle = 180f - rotation * 0.7f,
                        sweepAngle = 75f,
                        useCenter = false,
                        topLeft = Offset(center.x - base * 1.78f, center.y - base * 1.78f),
                        size = androidx.compose.ui.geometry.Size(base * 3.56f, base * 3.56f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFB9FFFF), AlexCyan, AlexBlue, AlexPurple),
                            center = center,
                            radius = base * 1.15f
                        ),
                        radius = base * pulse * 0.76f,
                        center = center
                    )

                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = base * 0.15f,
                        center = Offset(center.x - base * 0.25f, center.y - base * 0.25f)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                status,
                color = if (paused) AlexMuted else AlexCyan,
                fontSize = 12.sp,
                letterSpacing = 2.5.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(10.dp))

            Text(
                when {
                    paused -> "Можешь продолжить в любой момент"
                    busy -> "Подбираю ответ для тебя…"
                    speaking -> "Слушай мой ответ"
                    listening -> "Говори, я слушаю"
                    else -> "Мы на связи"
                },
                color = AlexWhite,
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VoiceControl(
                    symbol = if (paused) "▶" else "Ⅱ",
                    label = if (paused) "Продолжить" else "Пауза",
                    onClick = onTogglePause
                )

                Spacer(Modifier.width(30.dp))

                VoiceControl(
                    symbol = "✕",
                    label = "Завершить",
                    onClick = onClose,
                    danger = true
                )
            }

            Spacer(Modifier.height(18.dp))

            TextButton(onClick = onTypeMessage) {
                Text("Вернуться к текстовому чату", color = AlexMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun VoiceControl(
    symbol: String,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    if (danger) {
                        Brush.linearGradient(listOf(Color(0xFFFA668D), Color(0xFF8F3C79)))
                    } else {
                        Brush.linearGradient(listOf(AlexCyan, AlexBlue, AlexPurple))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                symbol,
                color = if (danger) Color.White else AlexBg,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(label, color = AlexMuted, fontSize = 12.sp)
    }
}
