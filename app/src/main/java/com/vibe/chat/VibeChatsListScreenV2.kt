
package com.vibe.chat

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore

private val ChatV2Bg = Color(0xFF0D111A)
private val ChatV2Card = Color(0xFF151B25)
private val ChatV2Cyan = Color(0xFF78E8D0)
private val ChatV2Purple = Color(0xFFAD94FF)
private val ChatV2Text = Color(0xFFE8F0F2)
private val ChatV2Muted = Color(0xFF8D9AA6)
private val ChatV2Line = Color(0xFF29343E)

private data class ChatV2Conversation(
    val id: String,
    val otherId: String,
    val createdAt: Long
)

@Composable
fun VibeChatsListScreenV2(
    myId: String,
    onOpenPrivateChat: (String, String) -> Unit,
    onOpenGeneralChat: () -> Unit,
    onFindPeople: () -> Unit
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
                        Color(0xFF101522),
                        ChatV2Bg,
                        ChatV2Bg
                    )
                )
            )
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Баннер по референсу пользователя: иллюстрация общения Vibe Chat.
        Image(
            painter = painterResource(R.drawable.vibe_home_banner),
            contentDescription = "Vibe Chat — люди, общение и сообщения",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
                .clip(RoundedCornerShape(18.dp))
        )

        Spacer(Modifier.height(8.dp))

        // Обычный аккуратный заголовок
        Text(
            text = "Чаты",
            color = ChatV2Text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )

        // Компактный поиск
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(ChatV2Card)
                .border(
                    1.dp,
                    ChatV2Line,
                    RoundedCornerShape(15.dp)
                )
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⌕",
                color = ChatV2Cyan,
                fontSize = 25.sp
            )

            Spacer(Modifier.width(9.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (search.isEmpty()) {
                    Text(
                        text = "Поиск по имени…",
                        color = ChatV2Muted,
                        fontSize = 13.sp
                    )
                }

                BasicTextField(
                    value = search,
                    onValueChange = { search = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = ChatV2Text,
                        fontSize = 13.sp
                    ),
                    cursorBrush = Brush.linearGradient(
                        listOf(ChatV2Cyan, ChatV2Purple)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (search.isNotEmpty()) {
                Text(
                    text = "×",
                    color = ChatV2Muted,
                    fontSize = 21.sp,
                    modifier = Modifier
                        .clickable { search = "" }
                        .padding(start = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(17.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Переписки",
                color = ChatV2Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1A2730))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${filtered.size}",
                    color = ChatV2Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = ChatV2Cyan,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            loadError -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyChatV2(
                        symbol = "!",
                        title = "Не удалось загрузить чаты",
                        subtitle = "Проверь соединение и доступ к Firestore."
                    )
                }
            }

            filtered.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyChatV2(
                        symbol = if (search.isBlank()) "✦" else "⌕",
                        title = if (search.isBlank()) {
                            "Пока нет переписок"
                        } else {
                            "Ничего не найдено"
                        },
                        subtitle = if (search.isBlank()) {
                            "Найди человека и начни общение."
                        } else {
                            "Попробуй изменить поисковый запрос."
                        },
                        action = if (search.isBlank()) "Найти людей" else null,
                        onAction = { onFindPeople() }
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { conversation ->
                        val name = userNames[conversation.otherId]
                            ?: "Пользователь"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(17.dp))
                                .background(ChatV2Card)
                                .border(
                                    1.dp,
                                    ChatV2Line,
                                    RoundedCornerShape(17.dp)
                                )
                                .clickable {
                                    onOpenPrivateChat(
                                        conversation.otherId,
                                        name
                                    )
                                }
                                .padding(horizontal = 11.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                ChatV2Purple,
                                                ChatV2Cyan
                                            )
                                        )
                                    )
                                    .padding(1.5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF17202B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name.firstOrNull()
                                        ?.uppercase() ?: "V",
                                    color = ChatV2Text,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(Modifier.width(11.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = name,
                                    color = ChatV2Text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(Modifier.height(4.dp))

                                Text(
                                    text = "Открыть переписку",
                                    color = ChatV2Muted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = "›",
                                color = ChatV2Cyan,
                                fontSize = 25.sp,
                                modifier = Modifier.padding(start = 7.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VibeChatBrandHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF18202D),
                        Color(0xFF111722),
                        Color(0xFF19172B)
                    )
                )
            )
            .border(
                1.dp,
                Color(0xFF29343F),
                RoundedCornerShape(20.dp)
            )
            .padding(start = 15.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Vibe Chat",
                color = ChatV2Cyan,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = ChatV2Cyan.copy(alpha = 0.65f),
                        offset = Offset(0f, 0f),
                        blurRadius = 15f
                    )
                )
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Общайся в своём ритме",
                color = ChatV2Muted,
                fontSize = 11.sp
            )
        }

        // Неоновая 3D-сфера, нарисованная средствами Compose
        Box(
            modifier = Modifier.size(67.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                ChatV2Purple.copy(alpha = 0.30f),
                                ChatV2Cyan.copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .size(47.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF8CFBE5),
                                Color(0xFF5C8FEA),
                                Color(0xFF9B72F5)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.35f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(33.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF293C57),
                                    Color(0xFF171C30),
                                    Color(0xFF111522)
                                ),
                                center = Offset(9f, 7f),
                                radius = 48f
                            )
                        )
                        .border(
                            1.dp,
                            ChatV2Cyan.copy(alpha = 0.65f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "V",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(
                            shadow = Shadow(
                                color = ChatV2Cyan,
                                offset = Offset(0f, 0f),
                                blurRadius = 10f
                            )
                        )
                    )
                }
            }

            Text(
                text = "✦",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 1.dp)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 5.dp, bottom = 6.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(ChatV2Cyan)
            )
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
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(82.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            ChatV2Purple.copy(alpha = 0.22f),
                            ChatV2Cyan.copy(alpha = 0.12f)
                        )
                    )
                )
                .border(
                    1.dp,
                    ChatV2Cyan.copy(alpha = 0.30f),
                    RoundedCornerShape(27.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                ChatV2Cyan.copy(alpha = 0.35f),
                                ChatV2Purple.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = symbol,
                    color = ChatV2Cyan,
                    fontSize = 30.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = title,
            color = ChatV2Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(7.dp))

        Text(
            text = subtitle,
            color = ChatV2Muted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        if (action != null) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = action,
                color = ChatV2Cyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A2730))
                    .clickable { onAction() }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }
}
