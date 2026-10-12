package com.vibe.chat

import android.graphics.BitmapFactory
import android.util.Base64
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

// Vibe Chat 2.0 — светлая мята и коралловые акценты.
private val VibeChatBackground = Color(0xFFF6FAF8)
private val VibeChatPanel = Color(0xFFFFFFFF)
private val VibeChatIncoming = Color(0xFFEAF2EF)
private val VibeChatOutgoing = Color(0xFFFF806F)
private val VibeChatOutgoingDark = Color(0xFFF46B60)
private val VibeChatText = Color(0xFF24332F)
private val VibeChatMuted = Color(0xFF7D8C86)
private val VibeChatAccent = Color(0xFF3D8C79)
private val VibeChatGreen = Color(0xFF42A982)
private val VibeChatLine = Color(0xFFDDE8E2)
private val VibeChatCoralPale = Color(0xFFFFE7E2)

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

    DisposableEffect(otherUserId) {
        var registration: ListenerRegistration? = null

        if (otherUserId.isNotBlank()) {
            registration = db.collection("users")
                .document(otherUserId)
                .addSnapshotListener { snapshot, exception ->
                    if (exception == null && snapshot != null && snapshot.exists()) {
                        actualOtherName = snapshot.getString("name")
                            ?.takeIf { it.isNotBlank() }
                            ?: otherUserName.ifBlank { "Собеседник" }

                        otherAvatarBase64 =
                            snapshot.getString("avatarBase64").orEmpty()
                    }
                }
        }

        onDispose { registration?.remove() }
    }

    val otherAvatarBitmap = remember(otherAvatarBase64) {
        try {
            if (otherAvatarBase64.isBlank()) {
                null
            } else {
                val bytes = Base64.decode(otherAvatarBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }

    LaunchedEffect(myId, otherUserId) {
        when {
            myId.isBlank() -> {
                error = "Войди в аккаунт, чтобы открыть переписку."
                loading = false
            }

            otherUserId.isBlank() -> {
                error = "Не удалось определить собеседника."
                loading = false
            }

            myId == otherUserId -> {
                error = "Нельзя открыть личный чат с самим собой."
                loading = false
            }
        }
    }

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
                        myName = snapshot?.getString("name")
                            ?.takeIf { it.isNotBlank() }
                            ?: currentUser?.phoneNumber.orEmpty()
                                .ifBlank { "Пользователь" }
                    }
                }
        }

        onDispose { registration?.remove() }
    }

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

                messageRegistration = chatRef.collection("messages")
                    .orderBy("createdAt")
                    .addSnapshotListener { snapshot, exception ->
                        if (!active) return@addSnapshotListener

                        if (exception != null) {
                            error = "Не удалось загрузить сообщения: ${
                                exception.localizedMessage
                                    ?: "проверь интернет и правила Firebase"
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
                    exception.localizedMessage ?: "проверь интернет и Firebase"
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

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VibeChatBackground)
            .imePadding()
    ) {
        // Верхняя панель диалога.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VibeChatPanel)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    tint = VibeChatText
                )
            }

            Box(
                modifier = Modifier
                    .size(43.dp)
                    .clip(CircleShape)
                    .background(VibeChatIncoming)
                    .border(1.dp, VibeChatLine, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (otherAvatarBitmap != null) {
                    Image(
                        bitmap = otherAvatarBitmap.asImageBitmap(),
                        contentDescription = "Аватар собеседника",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        actualOtherName.firstOrNull()?.uppercase() ?: "?",
                        color = VibeChatAccent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(11.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    actualOtherName,
                    color = VibeChatText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )

                Spacer(Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (chatReady) VibeChatGreen else VibeChatMuted
                            )
                    )

                    Spacer(Modifier.width(6.dp))

                    Text(
                        if (chatReady) "Личный чат" else "Подключение…",
                        color = VibeChatMuted,
                        fontSize = 12.sp
                    )
                }
            }

            IconButton(onClick = {}) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Дополнительно",
                    tint = VibeChatMuted
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(VibeChatLine)
        )

        if (error.isNotBlank()) {
            Surface(
                color = VibeChatCoralPale,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    error,
                    color = Color(0xFF9B4139),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(11.dp)
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = 12.dp,
                vertical = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (messages.isEmpty()) {
                item(key = "chat_welcome") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 50.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(78.dp)
                                .clip(CircleShape)
                                .background(VibeChatIncoming)
                                .border(1.dp, VibeChatLine, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✦",
                                color = VibeChatAccent,
                                fontSize = 35.sp
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        Text(
                            when {
                                loading -> "Подключаем переписку…"
                                chatReady -> "Начни разговор"
                                else -> "Личная переписка"
                            },
                            color = VibeChatText,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(7.dp))

                        Text(
                            if (chatReady) {
                                "Напиши $actualOtherName первое сообщение"
                            } else {
                                "Здесь будут ваши сообщения"
                            },
                            color = VibeChatMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { message ->
                val mine = message.authorId == myId

                val bubbleShape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (mine) 18.dp else 5.dp,
                    bottomEnd = if (mine) 5.dp else 18.dp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) {
                        Arrangement.End
                    } else {
                        Arrangement.Start
                    },
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 285.dp)
                            .clip(bubbleShape)
                            .background(
                                if (mine) {
                                    Brush.linearGradient(
                                        listOf(
                                            VibeChatOutgoing,
                                            VibeChatOutgoingDark
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(
                                        listOf(
                                            VibeChatIncoming,
                                            VibeChatIncoming
                                        )
                                    )
                                }
                            )
                            .then(
                                if (mine) {
                                    Modifier
                                } else {
                                    Modifier.border(
                                        1.dp,
                                        VibeChatLine,
                                        bubbleShape
                                    )
                                }
                            )
                            .padding(
                                horizontal = 12.dp,
                                vertical = 8.dp
                            )
                    ) {
                        if (!mine) {
                            Text(
                                message.authorName.ifBlank { actualOtherName },
                                color = VibeChatAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )

                            Spacer(Modifier.height(3.dp))
                        }

                        Text(
                            message.text,
                            color = if (mine) Color.White else VibeChatText,
                            fontSize = 14.sp,
                            lineHeight = 19.sp
                        )

                        Row(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            message.createdAt?.let { timestamp ->
                                Text(
                                    SimpleDateFormat(
                                        "HH:mm",
                                        Locale.getDefault()
                                    ).format(Date(timestamp.toDate().time)),
                                    color = if (mine) {
                                        Color(0xFFFFF0ED)
                                    } else {
                                        VibeChatMuted
                                    },
                                    fontSize = 10.sp
                                )
                            }

                            if (mine) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "✓",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Быстрые эмодзи.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VibeChatBackground)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            listOf("😀", "❤️", "😂", "🔥", "👍", "🥰", "✨").forEach { emoji ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VibeChatPanel)
                        .border(
                            1.dp,
                            VibeChatLine,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            if (input.length < 2000) {
                                input = (input + emoji).take(2000)
                            }
                        }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 16.sp)
                }
            }
        }

        // Поле ввода.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VibeChatPanel)
                .padding(
                    start = 10.dp,
                    end = 10.dp,
                    top = 7.dp,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.Bottom
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(VibeChatBackground)
                    .border(
                        1.dp,
                        VibeChatLine,
                        RoundedCornerShape(23.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it.take(2000) },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        color = VibeChatText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    cursorBrush = Brush.verticalGradient(
                        listOf(VibeChatAccent, VibeChatOutgoing)
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (input.isEmpty()) {
                                Text(
                                    "Написать сообщение…",
                                    color = VibeChatMuted,
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(Modifier.width(8.dp))

            val canSend = input.isNotBlank() &&
                chatReady &&
                myId.isNotBlank() &&
                otherUserId.isNotBlank() &&
                myId != otherUserId &&
                !sending

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) VibeChatOutgoing
                        else VibeChatIncoming
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
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Отправить",
                    tint = if (canSend) Color.White else VibeChatMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
