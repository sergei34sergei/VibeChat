
package com.vibe.chat

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.google.firebase.firestore.FirebaseFirestore

private val ChatV2Bg = Color(0xFF080B18)
private val ChatV2Card = Color(0xFF12192C)
private val ChatV2Cyan = Color(0xFF4CE7FF)
private val ChatV2Purple = Color(0xFF9B68FF)
private val ChatV2Text = Color(0xFFF4F6FF)
private val ChatV2Muted = Color(0xFF9AA8C7)

private data class ChatV2Conversation(
    val id: String,
    val otherId: String,
    val createdAt: Long
)

@Composable
fun VibeChatsListScreenV2(
    myId: String,
    onOpenChat: (String, String) -> Unit,
    onOpenGeneralChat: () -> Unit,
    onOpenUsers: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }

    var conversations by remember {
        mutableStateOf<List<ChatV2Conversation>>(emptyList())
    }
    var userNames by remember {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }

    DisposableEffect(db, myId) {
        val chatsListener = db.collection("chats")
            .whereArrayContains("participantIds", myId)
            .addSnapshotListener { snapshot, error ->
                loading = false
                loadError = error != null

                if (snapshot != null && error == null) {
                    conversations = snapshot.documents.mapNotNull { doc ->
                        val ids = (doc.get("participantIds") as? List<*>)
                            ?.filterIsInstance<String>()
                            .orEmpty()

                        val otherId = ids.firstOrNull { it != myId }
                            ?: return@mapNotNull null

                        ChatV2Conversation(
                            id = doc.id,
                            otherId = otherId,
                            createdAt = doc.getTimestamp("createdAt")
                                ?.toDate()?.time ?: 0L
                        )
                    }.sortedByDescending { it.createdAt }
                }
            }

        val usersListener = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null && error == null) {
                    userNames = snapshot.documents.associate { doc ->
                        val name = doc.getString("name")
                            .orEmpty()
                            .ifBlank {
                                doc.getString("phone")
                                    .orEmpty()
                                    .ifBlank { "Пользователь" }
                            }
                        doc.id to name
                    }
                }
            }

        onDispose {
            chatsListener.remove()
            usersListener.remove()
        }
    }

    val filtered = conversations.filter { conversation ->
        val name = userNames[conversation.otherId] ?: "Пользователь"
        name.contains(search, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF10152A),
                        ChatV2Bg,
                        Color(0xFF0B1020)
                    )
                )
            )
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "ТВОЯ ВСЕЛЕННАЯ",
                    color = ChatV2Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "Сообщения",
                    color = ChatV2Text,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Все твои разговоры в одном месте",
                    color = ChatV2Muted,
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(ChatV2Purple, ChatV2Cyan)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "V",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ChatV2Card)
                .border(
                    1.dp,
                    Color(0xFF303957),
                    RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 15.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⌕", color = ChatV2Cyan, fontSize = 27.sp)
            Spacer(Modifier.width(10.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (search.isEmpty()) {
                    Text(
                        "Найти переписку…",
                        color = ChatV2Muted,
                        fontSize = 14.sp
                    )
                }

                BasicTextField(
                    value = search,
                    onValueChange = { search = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = ChatV2Text,
                        fontSize = 14.sp
                    ),
                    cursorBrush = Brush.linearGradient(
                        listOf(ChatV2Cyan, ChatV2Purple)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "БЫСТРЫЙ ДОСТУП",
                color = ChatV2Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF4C2D80),
                                Color(0xFF202D59)
                            )
                        )
                    )
                    .clickable { onOpenGeneralChat() }
                    .padding(15.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("◎", color = Color.White, fontSize = 26.sp)
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Общий чат",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Все вместе",
                    color = Color(0xFFD6D9FF),
                    fontSize = 11.sp
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF124B60),
                                Color(0xFF182A46)
                            )
                        )
                    )
                    .clickable { onOpenUsers() }
                    .padding(15.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("＋", color = Color.White, fontSize = 27.sp)
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Найти людей",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Новые знакомства",
                    color = Color(0xFFD0F7FF),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(23.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ТВОИ ПЕРЕПИСКИ",
                color = ChatV2Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            Text(
                "${filtered.size}",
                color = ChatV2Cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF182D43))
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = ChatV2Cyan,
                        strokeWidth = 3.dp
                    )
                }
            }

            loadError -> {
                EmptyChatV2(
                    symbol = "!",
                    title = "Не удалось загрузить чаты",
                    subtitle = "Проверь соединение и правила доступа Firestore."
                )
            }

            filtered.isEmpty() -> {
                EmptyChatV2(
                    symbol = if (search.isBlank()) "✦" else "⌕",
                    title = if (search.isBlank()) {
                        "Здесь начнутся твои истории"
                    } else {
                        "Ничего не найдено"
                    },
                    subtitle = if (search.isBlank()) {
                        "Найди человека и отправь первое сообщение."
                    } else {
                        "Попробуй другое имя или очисти поиск."
                    },
                    action = if (search.isBlank()) "Найти людей" else null,
                    onAction = { onOpenUsers() }
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { conversation ->
                        val name = userNames[conversation.otherId]
                            ?: "Пользователь"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(21.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF151D32),
                                            Color(0xFF101729)
                                        )
                                    )
                                )
                                .border(
                                    1.dp,
                                    Color(0xFF29334F),
                                    RoundedCornerShape(21.dp)
                                )
                                .clickable {
                                    onOpenChat(conversation.otherId, name)
                                }
                                .padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(53.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(ChatV2Purple, ChatV2Cyan)
                                        )
                                    )
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF171C34)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    name.firstOrNull()?.uppercase() ?: "V",
                                    color = ChatV2Text,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    name,
                                    color = ChatV2Text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(Modifier.height(5.dp))

                                Text(
                                    "Открыть переписку",
                                    color = ChatV2Muted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1A3449)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "›",
                                    color = ChatV2Cyan,
                                    fontSize = 25.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatV2(
    symbol: String,
    title: String,
    subtitle: String,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            ChatV2Purple.copy(alpha = 0.25f),
                            ChatV2Cyan.copy(alpha = 0.15f)
                        )
                    )
                )
                .border(
                    1.dp,
                    ChatV2Cyan.copy(alpha = 0.35f),
                    RoundedCornerShape(26.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                symbol,
                color = ChatV2Cyan,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            title,
            color = ChatV2Text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(7.dp))

        Text(
            subtitle,
            color = ChatV2Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        if (action != null) {
            Spacer(Modifier.height(18.dp))
            Text(
                action,
                color = ChatV2Cyan,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF172A40))
                    .clickable { onAction() }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}
