package com.vibe.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val chatId = remember(myId, otherUserId) { listOf(myId, otherUserId).sorted().joinToString("_") }

    DisposableEffect(myId) {
        var registration: ListenerRegistration? = null
        if (myId.isNotBlank()) {
            registration = db.collection("users").document(myId).addSnapshotListener { snap, _ ->
                myName = snap?.getString("name")?.takeIf { it.isNotBlank() }
                    ?: currentUser?.phoneNumber.orEmpty().ifBlank { "Пользователь" }
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

            // Create the chat directly instead of reading a possibly-missing document first.
            // Firestore rules can deny reading a chat document before it exists.
            chatRef.set(
                mapOf(
                    "participantIds" to listOf(myId, otherUserId).sorted(),
                    "createdAt" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).addOnSuccessListener {
                chatReady = true
                error = ""
                startMessagesListener()
            }.addOnFailureListener { e ->
                error = "Не удалось открыть/создать чат: ${e.localizedMessage}"
            }
        }
        onDispose { messageRegistration?.remove() }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF100B25)).padding(horizontal = 14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ Назад", color = Color(0xFF62E9FF)) }
            Column(Modifier.weight(1f)) {
                Text(otherUserName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Личная переписка", color = Color.LightGray, fontSize = 12.sp)
            }
        }
        HorizontalDivider(color = Color(0xFF493078))

        if (error.isNotBlank()) {
            Text(error, color = Color(0xFFFFA6A6), fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        }

        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (chatReady) "Напишите первое сообщение 👋" else "Открываем чат…",
                    color = Color.LightGray, textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = false,
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val mine = message.authorId == myId
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
                    ) {
                        Column(
                            Modifier.widthIn(max = 300.dp)
                                .background(
                                    if (mine) Color(0xFF493078) else Color(0xFF21183B),
                                    RoundedCornerShape(16.dp)
                                ).padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            if (!mine) Text(
                                message.authorName.ifBlank { otherUserName },
                                color = Color(0xFF62E9FF), fontSize = 11.sp, fontWeight = FontWeight.Bold
                            )
                            Text(message.text, color = Color.White, fontSize = 15.sp)
                            message.createdAt?.let {
                                Text(
                                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.toDate().time)),
                                    color = Color.LightGray, fontSize = 10.sp,
                                    modifier = Modifier.align(Alignment.End).padding(top = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(2000) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение…") },
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF62E9FF),
                    unfocusedBorderColor = Color(0xFF9B6BFF),
                    focusedPlaceholderColor = Color.LightGray,
                    unfocusedPlaceholderColor = Color.LightGray
                )
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = input.isNotBlank() && chatReady && !sending,
                onClick = {
                    val textToSend = input.trim()
                    if (textToSend.isNotBlank() && chatReady && myId.isNotBlank()) {
                        sending = true
                        error = ""
                        db.collection("chats").document(chatId).collection("messages")
                            .add(
                                mapOf(
                                    "text" to textToSend,
                                    "authorId" to myId,
                                    "authorName" to myName,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            ).addOnSuccessListener {
                                input = ""
                                sending = false
                            }.addOnFailureListener { e ->
                                error = "Не удалось отправить: ${e.localizedMessage}"
                                sending = false
                            }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B6BFF)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) { Text(if (sending) "…" else "➤") }
        }
    }
}
