Add private chat screen

package com.vibe.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

private data class PrivateChatMessage(
    val id: String,
    val authorId: String,
    val text: String,
    val createdAt: Timestamp?
)

@Composable
fun PrivateChatScreen(
    otherUserId: String,
    otherUserName: String,
    onBack: () -> Unit
) {
    val myId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val db = FirebaseFirestore.getInstance()

    val chatId = remember(myId, otherUserId) {
        listOf(myId, otherUserId).sorted().joinToString("_")
    }

    val messages = remember(chatId) {
        mutableStateListOf<PrivateChatMessage>()
    }

    var messageText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    DisposableEffect(chatId, myId, otherUserId) {
        var active = true
        var registration: ListenerRegistration? = null

        fun listenForMessages() {
            if (!active) return

            registration = db.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (!active) return@addSnapshotListener

                    if (error != null) {
                        errorText = "Не удалось загрузить сообщения: ${error.localizedMessage}"
                        loading = false
                        return@addSnapshotListener
                    }

                    messages.clear()
                    snapshot?.documents?.forEach { document ->
                        messages.add(
                            PrivateChatMessage(
                                id = document.id,
                                authorId = document.getString("authorId").orEmpty(),
                                text = document.getString("text").orEmpty(),
                                createdAt = document.getTimestamp("createdAt")
                            )
                        )
                    }

                    loading = false
                    errorText = ""
                }
        }

        if (myId.isBlank()) {
            loading = false
            errorText = "Сначала войди в свой аккаунт."
        } else if (otherUserId.isBlank() || otherUserId == myId) {
            loading = false
            errorText = "Нельзя открыть чат с этим пользователем."
        } else {
            val chatRef = db.collection("chats").document(chatId)

            chatRef.get()
                .addOnSuccessListener { document ->
                    if (!active) return@addOnSuccessListener

                    if (document.exists()) {
                        listenForMessages()
                    } else {
                        val chatData = hashMapOf(
                            "participantIds" to listOf(myId, otherUserId).sorted(),
                            "createdAt" to FieldValue.serverTimestamp()
                        )

                        chatRef.set(chatData)
                            .addOnSuccessListener {
                                if (active) listenForMessages()
                            }
                            .addOnFailureListener { error ->
                                if (active) {
                                    loading = false
                                    errorText = "Не удалось создать чат: ${error.localizedMessage}"
                                }
                            }
                    }
                }
                .addOnFailureListener { error ->
                    if (active) {
                        loading = false
                        errorText = "Ошибка открытия чата: ${error.localizedMessage}"
                    }
                }
        }

        onDispose {
            active = false
            registration?.remove()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Назад")
            }
            Text(
                text = otherUserName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.padding(top = 6.dp))

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        if (errorText.isNotBlank()) {
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp)
            )
        }

        if (!loading && errorText.isBlank() && messages.isEmpty()) {
            Text(
                text = "Пока нет сообщений. Напиши первым!",
                modifier = Modifier.padding(8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                val mine = message.authorId == myId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (mine) 40.dp else 0.dp,
                            end = if (mine) 0.dp else 40.dp
                        ),
                    horizontalAlignment = if (mine) Alignment.End else Alignment.Start
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                color = if (mine) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(message.text)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение...") },
                maxLines = 4,
                enabled = !sending && errorText.isBlank()
            )

            Button(
                onClick = {
                    val textToSend = messageText.trim()
                    if (
                        textToSend.isNotEmpty() &&
                        !sending &&
                        myId.isNotBlank() &&
                        otherUserId.isNotBlank()
                    ) {
                        sending = true
                        val data = hashMapOf(
                            "text" to textToSend,
                            "authorId" to myId,
                            "createdAt" to FieldValue.serverTimestamp()
                        )

                        db.collection("chats")
                            .document(chatId)
                            .collection("messages")
                            .add(data)
                            .addOnSuccessListener {
                                messageText = ""
                                sending = false
                            }
                            .addOnFailureListener { error ->
                                errorText = "Не удалось отправить: ${error.localizedMessage}"
                                sending = false
                            }
                    }
                },
                enabled = messageText.isNotBlank() && !sending && errorText.isBlank(),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("➤")
            }
        }
    }
}
