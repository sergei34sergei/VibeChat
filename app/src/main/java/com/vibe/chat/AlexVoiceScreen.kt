
package com.vibe.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

private data class AlexChatMessage(
    val id: Long,
    val text: String,
    val fromAlex: Boolean
)

private val AlexBackground = Color(0xFF080D1C)
private val AlexCyan = Color(0xFF39E7FF)
private val AlexPurple = Color(0xFF9B72FF)

@Composable
fun AlexVoiceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }

    val messages = remember {
        mutableStateListOf(
            AlexChatMessage(
                id = 1L,
                text = "Привет! Я Алекс. Можешь написать мне сообщение или нажать на микрофон и поговорить со мной.",
                fromAlex = true
            )
        )
    }

    val listState = rememberLazyListState()

    val tts = remember {
        TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }

    LaunchedEffect(ttsReady) {
        if (ttsReady) {
            val result = tts.setLanguage(Locale("ru", "RU"))
            tts.setPitch(0.9f)
            tts.setSpeechRate(0.92f)

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Toast.makeText(
                    context,
                    "Русский голос не установлен. Проверь настройки синтеза речи.",
                    Toast.LENGTH_LONG
                ).show()
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

    val sendToAlex: (String) -> Unit = { rawText ->
        val question = rawText.trim()

        if (question.isNotBlank() && !busy) {
            messages.add(
                AlexChatMessage(
                    id = System.currentTimeMillis(),
                    text = question,
                    fromAlex = false
                )
            )

            draft = ""
            busy = true
            speaking = false

            scope.launch {
                try {
                    val model = Firebase.ai(
                        backend = GenerativeBackend.googleAI()
                    ).generativeModel("gemini-3.8-flash")

                    val history = messages
                        .dropLast(1)
                        .takeLast(12)
                        .joinToString("\n") {
                            if (it.fromAlex) {
                                "Алекс: ${it.text}"
                            } else {
                                "Пользователь: ${it.text}"
                            }
                        }

                    val prompt = """
                        Ты Алекс — голосовой помощник приложения Vibe Chat.
                        Отвечай на русском языке естественно, дружелюбно и понятно.
                        Учитывай предыдущие сообщения диалога.
                        Не утверждай, что выполняешь действия, которых не выполнял.

                        Предыдущий разговор:
                        $history

                        Новое сообщение пользователя:
                        $question
                    """.trimIndent()

                    val response = model.generateContent(prompt)
                    val reply = response.text
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: "Не получилось подготовить ответ. Попробуй спросить ещё раз."

                    messages.add(
                        AlexChatMessage(
                            id = System.currentTimeMillis() + 1,
                            text = reply,
                            fromAlex = true
                        )
                    )

                    if (ttsReady) {
                        speaking = true
                        tts.speak(
                            reply,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "alex_reply"
                        )
                    }
                } catch (e: Exception) {
                    messages.add(
                        AlexChatMessage(
                            id = System.currentTimeMillis() + 2,
                            text = "Не удалось получить ответ. Проверь интернет, настройки Firebase AI Logic и App Check. Подробности: ${e.localizedMessage ?: "ошибка подключения"}",
                            fromAlex = true
                        )
                    )
                } finally {
                    busy = false
                }
            }
        }
    }

    val latestSendToAlex by rememberUpdatedState(sendToAlex)

    DisposableEffect(recognizer, tts) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
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

                if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                    error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) {
                    Toast.makeText(
                        context,
                        "Не удалось распознать речь. Попробуй ещё раз.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResults(results: android.os.Bundle?) {
                listening = false

                val words = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()

                if (words.isNotBlank()) {
                    latestSendToAlex(words)
                } else {
                    Toast.makeText(
                        context,
                        "Я не расслышал. Попробуй ещё раз.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onPartialResults(
                partialResults: android.os.Bundle?
            ) = Unit

            override fun onEvent(
                eventType: Int,
                params: android.os.Bundle?
            ) = Unit
        })

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
        }
    }

    fun startListening() {
        if (busy) return

        if (recognizer == null) {
            Toast.makeText(
                context,
                "Распознавание речи недоступно на устройстве.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говори, я слушаю")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        try {
            recognizer.startListening(intent)
        } catch (_: Exception) {
            listening = false
            Toast.makeText(
                context,
                "Не получилось запустить микрофон.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            Toast.makeText(
                context,
                "Для разговора разреши доступ к микрофону.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val pulse = rememberInfiniteTransition(label = "alex-orb")
    val glow by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (listening) 450 else 1400
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb-glow"
    )

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(
                if (busy) messages.size else messages.lastIndex
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        AlexBackground,
                        Color(0xFF0C1024),
                        Color(0xFF11102A)
                    )
                )
            )
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Алекс",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        busy -> "ДУМАЮ НАД ОТВЕТОМ"
                        listening -> "СЛУШАЮ ТЕБЯ"
                        speaking -> "ОТВЕЧАЮ ГОЛОСОМ"
                        else -> "ТВОЙ ПОМОЩНИК VIBE CHAT"
                    },
                    color = AlexCyan,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF18213D))
                    .border(1.dp, Color(0xFF394A78), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A",
                    color = AlexCyan,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = size.minDimension * 0.31f * glow

                drawCircle(
                    color = Color(0x1639E7FF),
                    radius = radius * 1.8f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color(0x2439E7FF),
                    radius = radius * 1.45f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3f)
                )
                drawCircle(
                    color = AlexCyan.copy(alpha = 0.8f),
                    radius = radius * 1.12f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = AlexPurple.copy(alpha = 0.8f),
                    radius = radius * 0.92f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f)
                )

                val barCount = 35
                for (i in 0 until barCount) {
                    val angle = (i.toFloat() / barCount) * 6.283185f
                    val wave = (i % 5 + 1) / 5f
                    val inner = radius * 0.65f
                    val outer = inner + radius * 0.12f * wave *
                        if (listening || speaking) 2.2f else 1.0f

                    val start = Offset(
                        cx + kotlin.math.cos(angle) * inner,
                        cy + kotlin.math.sin(angle) * inner
                    )
                    val end = Offset(
                        cx + kotlin.math.cos(angle) * outer,
                        cy + kotlin.math.sin(angle) * outer
                    )

                    drawLine(
                        color = if (i % 2 == 0) AlexCyan else AlexPurple,
                        start = start,
                        end = end,
                        strokeWidth = 2.5f
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(98.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFF243E67),
                                Color(0xFF10172E)
                            )
                        )
                    )
                    .border(1.5.dp, AlexCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .width(9.dp)
                                .height(19.dp)
                                .clip(CircleShape)
                                .background(AlexCyan)
                        )
                        Box(
                            Modifier
                                .width(9.dp)
                                .height(19.dp)
                                .clip(CircleShape)
                                .background(AlexCyan)
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Box(
                        Modifier
                            .width(27.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(AlexPurple)
                    )
                }
            }
        }

        Text(
            text = when {
                busy -> "Алекс формирует ответ…"
                listening -> "Говори, я слушаю"
                speaking -> "Алекс отвечает голосом"
                else -> "Напиши сообщение или поговори"
            },
            color = Color(0xFFB7C4E8),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xD910162B))
                .border(
                    1.dp,
                    Color(0xFF283456),
                    RoundedCornerShape(22.dp)
                )
                .padding(10.dp)
        ) {
            Text(
                text = "ДИАЛОГ",
                color = AlexCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
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
                                .widthIn(max = 290.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomEnd = if (message.fromAlex) 18.dp else 5.dp,
                                        bottomStart = if (message.fromAlex) 5.dp else 18.dp
                                    )
                                )
                                .background(
                                    if (message.fromAlex) {
                                        Color(0xFF192540)
                                    } else {
                                        Color(0xFF453078)
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (message.fromAlex) {
                                        Color(0xFF30456A)
                                    } else {
                                        Color(0xFF7552B6)
                                    },
                                    RoundedCornerShape(18.dp)
                                )
                                .padding(horizontal = 13.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = if (message.fromAlex) "АЛЕКС" else "ТЫ",
                                color = if (message.fromAlex) AlexCyan else Color(0xFFD9C8FF),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            Spacer(Modifier.height(4.dp))

                            Text(
                                text = message.text,
                                color = Color(0xFFF4F5FF),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )

                            if (message.fromAlex && ttsReady) {
                                TextButton(
                                    onClick = {
                                        speaking = true
                                        tts.speak(
                                            message.text,
                                            TextToSpeech.QUEUE_FLUSH,
                                            null,
                                            "alex_message_${message.id}"
                                        )
                                    },
                                    contentPadding = PaddingValues(
                                        horizontal = 0.dp,
                                        vertical = 0.dp
                                    )
                                ) {
                                    Text(
                                        "▶ Прослушать",
                                        color = AlexCyan,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                if (busy) {
                    item(key = "alex-thinking") {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF192540))
                                .padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                color = AlexCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                "Алекс думает…",
                                color = Color(0xFFB7C4E8),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "Напиши Алексу сообщение…",
                    color = Color(0xFF8996B8)
                )
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences
            ),
            shape = RoundedCornerShape(18.dp),
            maxLines = 4,
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
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    if (listening) {
                        recognizer?.stopListening()
                        listening = false
                    } else if (
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startListening()
                    } else {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier
                    .weight(0.85f)
                    .height(52.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AlexCyan
                )
            ) {
                Text(
                    if (listening) "■ Стоп" else "🎙 Голос",
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = { latestSendToAlex(draft) },
                modifier = Modifier
                    .weight(1.15f)
                    .height(52.dp),
                enabled = !busy && draft.isNotBlank(),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7048CC),
                    disabledContainerColor = Color(0xFF343149)
                )
            ) {
                Text(
                    if (busy) "Думаю…" else "Отправить  ➤",
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}
