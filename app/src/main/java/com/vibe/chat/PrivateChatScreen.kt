package com.vibe.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PrivateMessage(
    val id: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val createdAt: Timestamp?
)

private val VibeChatBackground = Color(0xFF090817)
private val VibeChatPanel = Color(0xFF17152B)
private val VibeChatPurple = Color(0xFF9B6BFF)
private val VibeChatBlue = Color(0xFF62E9FF)
private val VibeChatPink = Color(0xFFFF70C5)
private val VibeChatText = Color(0xFFF7F3FF)
private val VibeChatMuted = Color(0xFFAAA4C8)
private val VibeChatGreen = Color(0xFF54F5B2)

@Composable
fun PrivateChatScreen(
    otherUserId: String,
    otherUserName: String,
    onBack: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val currentUser = auth.currentUser
    val myId = currentUser?.uid.orEmpty()

    var myName by remember(myId) {
        mutableStateOf(
            currentUser?.phoneNumber.orEmpty().ifBlank { "Пользователь" }
        )
    }

    var actualOtherName by remember(otherUserId, otherUserName) {
        mutableStateOf(otherUserName.ifBlank { "Собеседник" })
    }

    var otherAvatarBase64 by remember(otherUserId) {
        mutableStateOf("")
    }

    var messages by remember(myId, otherUserId) {
        mutableStateOf<List<PrivateMessage>>(emptyList())
    }

    var input by remember(myId, otherUserId) {
        mutableStateOf("")
    }

    var error by remember(myId, otherUserId) {
        mutableStateOf("")
    }

    var sending by remember(myId, otherUserId) {
        mutableStateOf(false)
    }

    var chatReady by remember(myId, otherUserId) {
        mutableStateOf(false)
    }

    var loading by remember(myId, otherUserId) {
        mutableStateOf(true)
    }

    val listState = rememberLazyListState()

    val chatId = remember(myId, otherUserId) {
        listOf(myId, otherUserId).sorted().joinToString("_")
    }

    // Загружаем актуальное имя и общую фотографию собеседника.
    DisposableEffect(otherUserId) {
        var registration: ListenerRegistration? = null

        if (otherUserId.isNotBlank()) {
            registration = db.collection("users")
                .document(otherUserId)
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        // Не закрываем чат, если временно недоступен профиль.
                    } else if (snapshot != null && snapshot.exists()) {
                        val savedName = snapshot.getString("name")
                        actualOtherName = savedName
                            ?.takeIf { it.isNotBlank() }
                            ?: otherUserName.ifBlank { "Собеседник" }

                        otherAvatarBase64 =
                            snapshot.getString("avatarBase64").orEmpty()
                    }
                }
        }

        onDispose {
            registration?.remove()
        }
    }

    // Декодируем фотографию из Firestore.
    val otherAvatarBitmap = remember(otherAvatarBase64) {
        try {
            if (otherAvatarBase64.isBlank()) {
                null
            } else {
                val bytes = Base64.decode(
                    otherAvatarBase64,
                    Base64.DEFAULT
                )
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }

    // Проверяем пользователя и входные данные.
    LaunchedEffect(myId, otherUserId) {
        if (myId.isBlank()) {
            error = "Войди в аккаунт, чтобы открыть переписку."
            loading = false
        } else if (otherUserId.isBlank()) {
            error = "Не удалось определить собеседника."
            loading = false
        } else if (myId == otherUserId) {
            error = "Нельзя открыть личный чат с самим собой."
            loading = false
        }
    }

    // Получаем имя текущего пользователя.
    DisposableEffect(myId) {
        var registration: ListenerRegistration? = null

        if (myId.isNotBlank()) {
            registration = db.collection("users")
                .document(myId)
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        error = "Ошибка профиля: ${
                            exception.localizedMessage ?: "неизвестная ошибка"
                        }"
                    } else {
                        val savedName = snapshot?.getString("name")
                        myName = savedName
                            ?.takeIf { it.isNotBlank() }
                            ?: currentUser?.phoneNumber.orEmpty()
                                .ifBlank { "Пользователь" }
                    }
                }
        }

        onDispose {
            registration?.remove()
        }
    }

    // Создаём или открываем переписку и слушаем сообщения.
    DisposableEffect(chatId, myId, otherUserId) {
        var messageRegistration: ListenerRegistration? = null
        var active = true

        chatReady = false
        loading = true
        messages = emptyList()

        if (
            myId.isNotBlank() &&
            otherUserId.isNotBlank() &&
            myId != otherUserId
        ) {
            val chatRef = db.collection("chats").document(chatId)

            chatRef.set(
                mapOf(
                    "participantIds" to listOf(myId, otherUserId).sorted(),
                    "createdAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).addOnSuccessListener {
                if (!active) return@addOnSuccessListener

                chatReady = true
                error = ""

                messageRegistration = chatRef
                    .collection("messages")
                    .orderBy("createdAt")
                    .addSnapshotListener { snapshot, exception ->
                        if (!active) return@addSnapshotListener

                        if (exception != null) {
                            error = "Не удалось загрузить сообщения: ${
                                exception.localizedMessage
                                    ?: "проверь подключение и правила Firebase"
                            }"
                            loading = false
                        } else if (snapshot != null) {
                            messages = snapshot.documents.map { document ->
                                PrivateMessage(
                                    id = document.id,
                                    authorId = document.getString("authorId").orEmpty(),
                                    authorName = document.getString("authorName").orEmpty(),
                                    text = document.getString("text").orEmpty(),
                                    createdAt = document.getTimestamp("createdAt")
                                )
                            }
                            error = ""
                            loading = false
                        }
                    }
            }.addOnFailureListener { exception ->
                if (!active) return@addOnFailureListener

                chatReady = false
                loading = false
                error = "Не удалось открыть чат: ${
                    exception.localizedMessage
                        ?: "проверь интернет и настройки Firebase"
                }"
            }
        } else {
            loading = false
        }

        onDispose {
            active = false
            messageRegistration?.remove()
        }
    }

    // Прокручиваем переписку к последнему сообщению.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF21133D),
                        VibeChatBackground,
                        Color(0xFF100D24)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 65.dp, y = 70.dp)
                .size(190.dp)
                .clip(CircleShape)
                .background(VibeChatPurple.copy(alpha = 0.07f))
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-85).dp, y = (-100).dp)
                .size(220.dp)
                .clip(CircleShape)
                .background(VibeChatBlue.copy(alpha = 0.045f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            // Шапка личного чата с фотографией собеседника.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBack,
                    shape = RoundedCornerShape(17.dp),
                    color = VibeChatPanel,
                    border = BorderStroke(
                        1.dp,
                        VibeChatBlue.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "‹",
                            color = VibeChatBlue,
                            fontSize = 35.sp,
                            fontWeight = FontWeight.Light
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    VibeChatPurple,
                                    Color(0xFF5945C8),
                                    VibeChatBlue
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (otherAvatarBitmap != null) {
                        Image(
                            bitmap = otherAvatarBitmap.asImageBitmap(),
                            contentDescription = "Фотография профиля собеседника",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = actualOtherName.firstOrNull()?.uppercase() ?: "V",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = actualOtherName,
                        color = VibeChatText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )

                    Spacer(Modifier.height(5.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    if (chatReady) VibeChatGreen else VibeChatMuted,
                                    CircleShape
                                )
                        )

                        Spacer(Modifier.width(6.dp))

                        Text(
                            text = if (chatReady) {
                                "ЛИЧНАЯ ПЕРЕПИСКА"
                            } else {
                                "ПОДКЛЮЧЕНИЕ…"
                            },
                            color = VibeChatMuted,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(VibeChatPanel),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✦", color = VibeChatPink, fontSize = 23.sp)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                VibeChatPurple.copy(alpha = 0.8f),
                                VibeChatBlue.copy(alpha = 0.65f),
                                Color.Transparent
                            )
                        )
                    )
            )

            if (error.isNotBlank()) {
                Surface(
                    color = Color(0xFF351D38),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        Color(0xFFFF83B6).copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text(
                        text = error,
                        color = Color(0xFFFFB8D2),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    top = 18.dp,
                    bottom = 18.dp
                ),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                if (messages.isEmpty()) {
                    item(key = "chat_welcome") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 55.dp, bottom = 35.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                VibeChatPurple.copy(alpha = 0.23f),
                                                VibeChatBlue.copy(alpha = 0.10f)
                                            )
                                        ),
                                        CircleShape
                                    )
                                    .border(
                                        1.dp,
                                        VibeChatBlue.copy(alpha = 0.45f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✦",
                                    color = VibeChatBlue,
                                    fontSize = 45.sp
                                )
                            }

                            Spacer(Modifier.height(22.dp))

                            Text(
                                text = when {
                                    loading -> "Подключаем переписку"
                                    chatReady -> "Всё начинается с общения"
                                    else -> "Личная переписка"
                                },
                                color = VibeChatText,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(9.dp))

                            Text(
                                text = if (chatReady) {
                                    "Напиши $actualOtherName первое сообщение 👋"
                                } else {
                                    "Здесь будут ваши личные сообщения"
                                },
                                color = VibeChatMuted,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                items(
                    items = messages,
                    key = { it.id }
                ) { message ->
                    val mine = message.authorId == myId

                    val bubbleShape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (mine) 20.dp else 6.dp,
                        bottomEnd = if (mine) 6.dp else 20.dp
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (mine) {
                            Alignment.End
                        } else {
                            Alignment.Start
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 310.dp)
                                .clip(bubbleShape)
                                .background(
                                    if (mine) {
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFF8453D9),
                                                Color(0xFF4A3AA7)
                                            )
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFF282340),
                                                Color(0xFF1B1830)
                                            )
                                        )
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(
                                        if (mine) {
                                            listOf(
                                                VibeChatPink.copy(alpha = 0.55f),
                                                VibeChatBlue.copy(alpha = 0.55f)
                                            )
                                        } else {
                                            listOf(
                                                VibeChatBlue.copy(alpha = 0.35f),
                                                VibeChatPurple.copy(alpha = 0.25f)
                                            )
                                        }
                                    ),
                                    shape = bubbleShape
                                )
                                .padding(
                                    horizontal = 15.dp,
                                    vertical = 11.dp
                                )
                        ) {
                            if (!mine) {
                                Text(
                                    text = message.authorName.ifBlank {
                                        actualOtherName
                                    },
                                    color = VibeChatBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.height(5.dp))
                            }

                            Text(
                                text = message.text,
                                color = VibeChatText,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )

                            message.createdAt?.let { timestamp ->
                                Text(
                                    text = SimpleDateFormat(
                                        "HH:mm",
                                        Locale.getDefault()
                                    ).format(Date(timestamp.toDate().time)),
                                    color = if (mine) {
                                        Color(0xFFE2D7FF)
                                    } else {
                                        VibeChatMuted
                                    },
                                    fontSize = 10.sp,
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Быстрые эмодзи.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("😀", "❤️", "😂", "🔥", "👍", "🥰", "✨").forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VibeChatPanel)
                            .border(
                                1.dp,
                                VibeChatPurple.copy(alpha = 0.30f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (input.length < 2000) {
                                    input = (input + emoji).take(2000)
                                }
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 17.sp)
                    }
                }
            }

            // Поле ввода и кнопка отправки.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 54.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(VibeChatPanel)
                        .border(
                            1.dp,
                            VibeChatBlue.copy(alpha = 0.42f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(
                            horizontal = 15.dp,
                            vertical = 15.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it.take(2000) },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            color = VibeChatText,
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        ),
                        cursorBrush = Brush.verticalGradient(
                            listOf(VibeChatBlue, VibeChatPurple)
                        ),
                        decorationBox = { innerTextField ->
                            Box {
                                if (input.isEmpty()) {
                                    Text(
                                        text = "Напиши что-нибудь…",
                                        color = VibeChatMuted,
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                Spacer(Modifier.width(10.dp))

                val canSend = input.isNotBlank() &&
                    chatReady &&
                    myId.isNotBlank() &&
                    otherUserId.isNotBlank() &&
                    myId != otherUserId &&
                    !sending

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(
                            if (canSend) {
                                Brush.linearGradient(
                                    listOf(
                                        VibeChatPurple,
                                        Color(0xFF5D4BDB),
                                        VibeChatBlue
                                    )
                                )
                            } else {
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF39324F),
                                        Color(0xFF29243C)
                                    )
                                )
                            }
                        )
                        .border(
                            1.dp,
                            if (canSend) {
                                VibeChatBlue.copy(alpha = 0.75f)
                            } else {
                                VibeChatMuted.copy(alpha = 0.20f)
                            },
                            RoundedCornerShape(19.dp)
                        )
                        .clickable(enabled = canSend) {
                            val textToSend = input.trim()

                            if (textToSend.isNotBlank() && !sending) {
                                sending = true
                                error = ""

                                db.collection("chats")
                                    .document(chatId)
                                    .collection("messages")
                                    .add(
                                        mapOf(
                                            "text" to textToSend,
                                            "authorId" to myId,
                                            "authorName" to myName,
                                            "createdAt" to FieldValue.serverTimestamp()
                                        )
                                    )
                                    .addOnSuccessListener {
                                        input = ""
                                        sending = false
                                    }
                                    .addOnFailureListener { exception ->
                                        error = "Не удалось отправить сообщение: ${
                                            exception.localizedMessage
                                                ?: "ошибка Firebase"
                                        }"
                                        sending = false
                                    }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (sending) "…" else "➤",
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
