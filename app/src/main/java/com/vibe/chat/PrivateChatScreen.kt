
package com.vibe.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

private val ChatBackground = Color(0xFF10121B)
private val ChatSurface = Color(0xFF191C29)
private val ChatSurfaceLight = Color(0xFF222638)
private val ChatMint = Color(0xFF70F5D0)
private val ChatViolet = Color(0xFFAA83FF)
private val ChatCoral = Color(0xFFFF8B91)
private val ChatText = Color(0xFFF1F3FF)
private val ChatMuted = Color(0xFF969DB6)

private fun privateChatGradient() = Brush.linearGradient(
    colors = listOf(
        Color(0xFF7155DE),
        Color(0xFF407FD9),
        Color(0xFF42D9C0)
    )
)

private data class PrivateChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val createdAt: Timestamp?
)

@Composable
fun PrivateChatScreen(
    otherUserId: String,
    otherUserName: String,
    onBack: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val myId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    val chatId = remember(myId, otherUserId) {
        listOf(myId, otherUserId).sorted().joinToString("_")
    }

    var myName by rememberSaveable { mutableStateOf("Пользователь") }
    var draft by rememberSaveable { mutableStateOf("") }
    var showEmojiPicker by rememberSaveable { mutableStateOf(false) }
    var messages by remember {
        mutableStateOf<List<PrivateChatMessage>>(emptyList())
    }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(myId) {
        if (myId.isNotBlank()) {
            db.collection("users")
                .document(myId)
                .get()
                .addOnSuccessListener { document ->
                    myName = document.getString("name")
                        ?: document.getString("phone")
                        ?: "Пользователь"
                }
        }
    }

    DisposableEffect(chatId, myId, otherUserId) {
        if (myId.isBlank() || otherUserId.isBlank()) {
            loading = false
            errorMessage = "Не удалось определить участников диалога."
            onDispose { }
        } else {
            val chatRef = db.collection("chats").document(chatId)

            chatRef.set(
                mapOf(
                    "participantIds" to listOf(myId, otherUserId),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).addOnFailureListener {
                errorMessage = "Не удалось подготовить диалог."
            }

            val registration = chatRef.collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(300)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        loading = false
                        errorMessage = "Не удалось загрузить сообщения."
                        return@addSnapshotListener
                    }

                    messages = snapshot?.documents?.map { document ->
                        PrivateChatMessage(
                            id = document.id,
                            senderId = document.getString("senderId").orEmpty(),
                            senderName = document.getString("senderName")
                                ?: "Пользователь",
                            text = document.getString("text").orEmpty(),
                            createdAt = document.getTimestamp("createdAt")
                        )
                    }.orEmpty()

                    loading = false
                    errorMessage = ""
                }

            onDispose {
                registration.remove()
            }
        }
    }

    fun sendMessage() {
        val text = draft.trim()

        if (text.isBlank() || sending || myId.isBlank()) return

        sending = true
        errorMessage = ""

        val chatRef = db.collection("chats").document(chatId)

        chatRef.collection("messages")
            .add(
                mapOf(
                    "senderId" to myId,
                    "senderName" to myName,
                    "text" to text,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                chatRef.set(
                    mapOf(
                        "participantIds" to listOf(myId, otherUserId),
                        "lastMessage" to text,
                        "lastSenderId" to myId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )

                draft = ""
                sending = false
                showEmojiPicker = false
            }
            .addOnFailureListener {
                sending = false
                errorMessage = "Сообщение не отправлено. Попробуй ещё раз."
            }
    }

    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF111522),
                        ChatBackground,
                        Color(0xFF151326)
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ChatSurface.copy(alpha = 0.97f))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    tint = ChatText
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(privateChatGradient())
                    .border(
                        width = 1.dp,
                        color = ChatMint.copy(alpha = 0.65f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = otherUserName.firstOrNull()
                        ?.uppercase()
                        ?: "V",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = otherUserName,
                    color = ChatText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "Личный диалог Vibe",
                    color = ChatMint,
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(ChatMint)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.Center),
                        color = ChatMint,
                        strokeWidth = 3.dp
                    )
                }

                messages.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(25.dp))
                                .background(privateChatGradient()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✦", fontSize = 36.sp, color = Color.White)
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            "Ваш диалог начинается здесь",
                            color = ChatText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(7.dp))

                        Text(
                            "Отправь первое сообщение",
                            color = ChatMuted,
                            fontSize = 13.sp
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = false,
                        contentPadding = PaddingValues(
                            horizontal = 14.dp,
                            vertical = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = messages,
                            key = { it.id }
                        ) { message ->
                            val mine = message.senderId == myId

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
                                        .fillMaxWidth(0.86f)
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 19.dp,
                                                topEnd = 19.dp,
                                                bottomStart = if (mine) 19.dp else 5.dp,
                                                bottomEnd = if (mine) 5.dp else 19.dp
                                            )
                                        )
                                        .background(
                                            if (mine) {
                                                Brush.linearGradient(
                                                    listOf(
                                                        Color(0xFF5542A4),
                                                        Color(0xFF37366B)
                                                    )
                                                )
                                            } else {
                                                Brush.linearGradient(
                                                    listOf(
                                                        ChatSurfaceLight,
                                                        ChatSurface
                                                    )
                                                )
                                            }
                                        )
                                        .padding(
                                            horizontal = 14.dp,
                                            vertical = 10.dp
                                        )
                                ) {
                                    if (!mine) {
                                        Text(
                                            message.senderName,
                                            color = ChatMint,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )

                                        Spacer(Modifier.height(4.dp))
                                    }

                                    Text(
                                        message.text,
                                        color = ChatText,
                                        fontSize = 15.sp,
                                        lineHeight = 21.sp
                                    )

                                    Spacer(Modifier.height(4.dp))

                                    Text(
                                        text = if (
                                            mine &&
                                            message.createdAt == null
                                        ) "Отправляется…" else "",
                                        color = ChatMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (errorMessage.isNotBlank()) {
            Text(
                text = errorMessage,
                color = ChatCoral,
                fontSize = 12.sp,
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 5.dp
                )
            )
        }

        if (showEmojiPicker) {
            val emojis = listOf(
                "😀", "😂", "🥹", "😍", "😎", "🤔",
                "🔥", "❤️", "💜", "✨", "👍", "👋",
                "🎉", "🚀", "🤖", "💯", "😊", "🥳"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ChatSurface)
                    .padding(10.dp)
            ) {
                Text(
                    "Эмодзи",
                    color = ChatMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(
                        start = 4.dp,
                        bottom = 8.dp
                    )
                )

                emojis.chunked(6).forEach { rowEmojis ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        rowEmojis.forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 25.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        draft += emoji
                                    }
                                    .padding(5.dp)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ChatSurface)
                .padding(
                    horizontal = 10.dp,
                    vertical = 10.dp
                ),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = {
                    showEmojiPicker = !showEmojiPicker
                }
            ) {
                Icon(
                    Icons.Default.EmojiEmotions,
                    contentDescription = "Эмодзи",
                    tint = if (showEmojiPicker) ChatMint else ChatViolet
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(21.dp))
                    .background(ChatSurfaceLight)
                    .border(
                        width = 1.dp,
                        color = ChatViolet.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(21.dp)
                    )
                    .padding(
                        horizontal = 14.dp,
                        vertical = 13.dp
                    )
            ) {
                if (draft.isEmpty()) {
                    Text(
                        "Написать сообщение…",
                        color = ChatMuted,
                        fontSize = 14.sp
                    )
                }

                BasicTextField(
                    value = draft,
                    onValueChange = {
                        draft = it.take(4000)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = ChatText,
                        fontSize = 14.sp
                    ),
                    maxLines = 5
                )
            }

            Spacer(Modifier.width(8.dp))

            IconButton(
                enabled = draft.isNotBlank() && !sending,
                onClick = { sendMessage() },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (draft.isNotBlank() && !sending) {
                            privateChatGradient()
                        } else {
                            ChatSurfaceLight
                        }
                    )
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = ChatMint,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Отправить",
                        tint = if (draft.isNotBlank()) {
                            Color.White
                        } else {
                            ChatMuted
                        }
                    )
                }
            }
        }
    }
}
