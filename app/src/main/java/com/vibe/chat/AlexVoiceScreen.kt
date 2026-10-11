
package com.vibe.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val AlexCyan = Color(0xFF35E0F5)
private val AlexBlue = Color(0xFF176BFF)
private val AlexPurple = Color(0xFF963CFF)
private val AlexBackground = Color(0xFF080D1B)
private val AlexPanel = Color(0xFF141D32)

@Composable
fun AlexVoiceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var question by remember { mutableStateOf("") }
    var answer by remember {
        mutableStateOf(
            "Привет! Я Алекс, твой помощник Vibe Chat. Чем могу помочь?"
        )
    }
    var errorText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }

    var recognizer by remember {
        mutableStateOf<SpeechRecognizer?>(null)
    }
    var tts by remember {
        mutableStateOf<TextToSpeech?>(null)
    }

    fun copyText(label: String, value: String) {
        val clipboard = context.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(label, value)
        )

        Toast.makeText(
            context,
            "Текст скопирован",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun startListening() {
        errorText = ""

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            errorText = "На устройстве недоступно распознавание речи."
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            errorText = "Разреши доступ к микрофону в настройках приложения."
            return
        }

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
            }

            recognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            isListening = false
            errorText = "Ошибка микрофона: ${e.message}"
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startListening()
            } else {
                errorText = "Для голосового ввода нужно разрешение на микрофон."
            }
        }

    DisposableEffect(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            recognizer =
                SpeechRecognizer.createSpeechRecognizer(context)

            recognizer?.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                    }

                    override fun onBeginningOfSpeech() {
                        isListening = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        errorText = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH ->
                                "Не удалось распознать речь. Попробуй ещё раз."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "Я не услышал речь. Попробуй ещё раз."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Нет разрешения на микрофон."
                            else ->
                                "Ошибка распознавания речи. Код: $error"
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false

                        val matches = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        if (!matches.isNullOrEmpty()) {
                            question = matches[0]
                        }
                    }

                    override fun onPartialResults(
                        partialResults: Bundle?
                    ) {}

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) {}
                }
            )
        }

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ru", "RU")
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.0f)
            }
        }

        onDispose {
            recognizer?.destroy()
            recognizer = null
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return

        val engine = tts
        if (engine == null) {
            errorText = "Голосовой движок ещё запускается. Попробуй снова."
            return
        }

        engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "alex_answer"
        )

        isSpeaking = true
    }

    LaunchedEffect(isSpeaking) {
        if (isSpeaking) {
            delay(1500)
            isSpeaking = false
        }
    }

    val pulse by rememberInfiniteTransition(
        label = "alex_pulse"
    ).animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AlexBackground
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF101B38),
                            AlexBackground,
                            Color(0xFF100D27)
                        )
                    )
                )
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = "VIBE CHAT",
                    color = AlexCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Алекс",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Твой голосовой AI-помощник",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(22.dp))

                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .scale(
                            if (isListening || isSpeaking) pulse else 1f
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        AlexBlue,
                                        AlexPurple,
                                        AlexCyan
                                    )
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(164.dp)
                                .background(
                                    AlexBackground,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(
                                    id = R.drawable.alex_avatar
                                ),
                                contentDescription = "Аватар Алекса",
                                modifier = Modifier.size(150.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = when {
                        isLoading -> "Алекс думает..."
                        isListening -> "Я слушаю тебя..."
                        isSpeaking -> "Алекс говорит..."
                        else -> "Я готов к разговору"
                    },
                    color = AlexCyan,
                    fontSize = 15.sp
                )

                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Напиши вопрос Алексу") },
                    placeholder = { Text("Например: помоги мне...") },
                    shape = RoundedCornerShape(18.dp),
                    minLines = 2,
                    maxLines = 5
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                startListening()
                            } else {
                                permissionLauncher.launch(
                                    Manifest.permission.RECORD_AUDIO
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlexPurple
                        )
                    ) {
                        Text(
                            if (isListening) "Слушаю..."
                            else "🎙 Микрофон"
                        )
                    }

                    Button(
                        onClick = {
                            question = ""
                            errorText = ""
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlexPanel
                        )
                    ) {
                        Text("Очистить")
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        val userQuestion = question.trim()

                        if (userQuestion.isBlank()) {
                            errorText =
                                "Напиши вопрос или воспользуйся микрофоном."
                        } else {
                            isLoading = true
                            errorText = ""

                            scope.launch {
                                try {
                                    val result = withContext(
                                        Dispatchers.IO
                                    ) {
                                        val model = Firebase.ai(
                                            backend =
                                                GenerativeBackend.googleAI()
                                        ).generativeModel(
                                            "gemini-3.5-flash-lite"
                                        )

                                        model.generateContent(
                                            """
                                            Ты Алекс — дружелюбный голосовой
                                            помощник приложения Vibe Chat.
                                            Отвечай на русском языке,
                                            понятно и естественно.
                                            Обычно отвечай кратко.
                                            Если пользователь просит подробности,
                                            объясняй пошагово.
                                            Вопрос пользователя:
                                            $userQuestion
                                            """.trimIndent()
                                        ).text
                                    }

                                    if (result.isNullOrBlank()) {
                                        answer =
                                            "Я не получил текст ответа. Попробуй ещё раз."
                                    } else {
                                        answer = result
                                    }
                                } catch (e: Exception) {
                                    val details = buildString {
                                        append(e.javaClass.name)
                                        append("\n")
                                        append(
                                            e.message
                                                ?: "Подробности отсутствуют."
                                        )

                                        var cause = e.cause
                                        var count = 0

                                        while (
                                            cause != null && count < 3
                                        ) {
                                            append("\nПричина: ")
                                            append(
                                                cause.javaClass.simpleName
                                            )
                                            append(": ")
                                            append(
                                                cause.message
                                                    ?: "без подробностей"
                                            )
                                            cause = cause.cause
                                            count++
                                        }
                                    }

                                    errorText = details

                                    val lower = details.lowercase(
                                        Locale.ROOT
                                    )

                                    answer = if (
                                        lower.contains("quota") ||
                                        lower.contains("429") ||
                                        lower.contains("resource_exhausted") ||
                                        lower.contains("too many requests")
                                    ) {
                                        "Брат, бесплатный лимит запросов Gemini временно исчерпан. Попробуй позже. Нажми «Копировать ошибку», если нужно прислать мне технические подробности."
                                    } else {
                                        "Не удалось получить ответ от Gemini. Проверь подключение к интернету. Нажми «Копировать ошибку» и пришли мне текст, чтобы я помог разобраться."
                                    }
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AlexBlue
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Думаю...")
                    } else {
                        Text(
                            text = "✨ Спросить Алекса",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            AlexPanel,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Ответ Алекса",
                        color = AlexCyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = answer,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 23.sp
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { speak(answer) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AlexPurple
                            )
                        ) {
                            Text("🔊 Озвучить")
                        }

                        Button(
                            onClick = {
                                copyText("Ответ Алекса", answer)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AlexBlue
                            )
                        ) {
                            Text("📋 Копировать")
                        }
                    }
                }

                if (errorText.isNotBlank()) {
                    Spacer(Modifier.height(18.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color(0xFF351A28),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Техническая информация",
                            color = Color(0xFFFF8DAB),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = errorText,
                            color = Color.White,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                copyText("Ошибка Vibe Chat", errorText)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8B2447)
                            )
                        ) {
                            Text("📋 Копировать ошибку")
                        }
                    }
                }

                Spacer(Modifier.height(30.dp))

                Text(
                    text = "Алекс • Vibe Chat",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
