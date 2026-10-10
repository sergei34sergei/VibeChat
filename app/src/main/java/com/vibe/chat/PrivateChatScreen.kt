
package com.vibe.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

private val NeonBackground = Color(0xFF090817)
private val NeonPanel = Color(0xFF15122B)
private val NeonPurple = Color(0xFF9B6BFF)
private val NeonBlue = Color(0xFF62E9FF)
private val NeonText = Color(0xFFF7F3FF)
private val NeonMuted = Color(0xFFAAA4C8)

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

    var myName by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<PrivateMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var chatReady by remember(otherUserId, myId) { mutableStateOf(false) }

    val chatId = remember(myId, otherUserId) {
        listOf(myId, otherUserId).sorted().joinToString("_")
    }

    DisposableEffect(myId) {
        var registration: ListenerRegistration? = null

        if (myId.isNotBlank()) {
            registration = db.collection("users").document(myId)
                .addSnapshotListener { snap, _ ->
                    myName = snap?.getString("name")?.takeIf { it.isNotBlank() }
                        ?: currentUser?.phoneNumber.orEmpty().ifBlank {
                            "Пользователь"
                        }
                }
        }

        onDispose { registration?.remove() }
    }

    DisposableEffect(chatId, myId, otherUserId) {
        var messageRegistration: ListenerRegistration? = null

        if (myId.isNotBlank() && otherUserId.isNotBlank() && myId != otherUserId) {
            val chatRef = db.collection("chats").document(chatId)

            fun startMessagesListener() {
                messageRegistration?.remove()

                messageRegistration = chatRef.collection("messages")
                    .orderBy("createdAt")
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            error = "Не удалось загрузить сообщения: ${e.localizedMessage}"
                        } else if (snapshot != null) {
                            messages = snapshot.documents.map { doc ->
                                PrivateMessage(
                                    id = doc.id,
                                    authorId = doc.getString("authorId").orEmpty(),
                                    authorName = doc.getString("authorName").orEmpty(),
                                    text = doc.getString("text").orEmpty(),
                                    createdAt = doc.getTimestamp("createdAt")
                                )
                            }
                            error = ""
                        }
                    }
            }

            chatRef.set(
                mapOf(
                    "participantIds" to listOf(myId, otherUserId).sorted(),
                    "createdAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).addOnSuccessListener {
                chatReady = true
                error = ""
                startMessagesListener()
            }.addOnFailureListener { e ->
                error = "Не удалось открыть чат: ${e.localizedMessage}"
            }
        }

        onDispose { messageRegistration?.remove() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF17102F),
                        NeonBackground,
                        Color(0xFF100D24)
                    )
                )
            )
            .padding(horizontal = 14.dp)
    ) {
        // Neon header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF21183B),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, NeonBlue.copy(alpha = 0.55f)
                )
            ) {
                Text(
                    "‹",
                    color = NeonBlue,
                    fontSize = 32.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(NeonPurple, NeonBlue)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    otherUserName.firstOrNull()?.uppercase() ?: "V",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp
                )
            }

            Spacer(Modifier.width(11.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    otherUserName,
                    color = NeonText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (chatReady) Color(0xFF54F5B2) else NeonMuted)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (chatReady) "ЛИЧНЫЙ ЧАТ" else "ПОДКЛЮЧЕНИЕ…",
                        color = NeonBlue,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            Text("✦", color = NeonPurple, fontSize = 27.sp)
        }

        HorizontalDivider(color = NeonPurple.copy(alpha = 0.45f))

        if (error.isNotBlank()) {
            Surface(
                color = Color(0xFF381B35),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    error,
                    color = Color(0xFFFFB7C5),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (messages.isEmpty()) {
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(NeonPurple.copy(alpha = 0.12f))
                            .border(1.dp, NeonBlue.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✦", color = NeonBlue, fontSize = 38.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (chatReady) "Твоя личная переписка" else "Открываем чат…",
                        color = NeonText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (chatReady) "Отправь первое сообщение 👋"
                        else "Подключаемся к Firebase",
                        color = NeonMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val mine = message.authorId == myId

                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
                    ) {
                        Column(
                            Modifier
                                .widthIn(max = 300.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 20.dp,
                                        topEnd = 20.dp,
                                        bottomStart = if (mine) 20.dp else 5.dp,
                                        bottomEnd = if (mine) 5.dp else 20.dp
                                    )
                                )
                                .background(
                                    if (mine) {
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFF7545C8),
                                                Color(0xFF4031A0)
                                            )
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFF24203D),
                                                Color(0xFF19162D)
                                            )
                                        )
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (mine) NeonPurple.copy(alpha = 0.8f)
                                    else NeonBlue.copy(alpha = 0.35f),
                                    RoundedCornerShape(
                                        topStart = 20.dp,
                                        topEnd = 20.dp,
                                        bottomStart = if (mine) 20.dp else 5.dp,
                                        bottomEnd = if (mine) 5.dp else 20.dp
                                    )
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            if (!mine) {
                                Text(
                                    message.authorName.ifBlank { otherUserName },
                                    color = NeonBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(3.dp))
                            }

                            Text(
                                message.text,
                                color = NeonText,
                                fontSize = 15.sp,
                                lineHeight = 21.sp
                            )

                            message.createdAt?.let {
                                Text(
                                    SimpleDateFormat("HH:mm", Locale.getDefault())
                                        .format(Date(it.toDate().time)),
                                    color = if (mine) Color(0xFFD8CAFF) else NeonMuted,
                                    fontSize = 10.sp,
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .padding(top = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Emoji shortcuts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("😀", "❤️", "😂", "🔥", "👍", "🥰", "😍", "✨").forEach { emoji ->
                Surface(
                    onClick = {
                        if (input.length < 2000) input += emoji
                    },
                    color = NeonPanel,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, NeonPurple.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        emoji,
                        fontSize = 19.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Neon message composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp, top = 2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(NeonPanel)
                    .border(
                        1.dp,
                        NeonBlue.copy(alpha = 0.65f),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it.take(2000) },
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = NeonText,
                        fontSize = 15.sp
                    ),
                    cursorBrush = Brush.verticalGradient(
                        listOf(NeonBlue, NeonPurple)
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (input.isEmpty()) {
                                Text(
                                    "Сообщение…",
                                    color = NeonMuted,
                                    fontSize = 15.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(Modifier.width(9.dp))

            Surface(
                onClick = {
                    val textToSend = input.trim()
                    if (textToSend.isNotBlank() && chatReady &&
                        myId.isNotBlank() && !sending
                    ) {
                        sending = true
                        error = ""

                        db.collection("chats").document(chatId)
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
                            .addOnFailureListener { e ->
                                error = "Не удалось отправить: ${e.localizedMessage}"
                                sending = false
                            }
                    }
                },
                enabled = input.isNotBlank() && chatReady && !sending,
                shape = RoundedCornerShape(17.dp),
                color = Color.Transparent,
                contentColor = Color.White,
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(NeonPurple, Color(0xFF5548D9), NeonBlue)
                        ),
                        shape = RoundedCornerShape(17.dp)
                    )
                    .border(
                        1.dp,
                        NeonBlue.copy(alpha = 0.75f),
                        RoundedCornerShape(17.dp)
                    )
            ) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (sending) "…" else "➤",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
