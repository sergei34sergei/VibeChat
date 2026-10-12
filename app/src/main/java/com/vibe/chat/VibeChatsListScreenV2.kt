
package com.vibe.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore

private val ChatsBg = Color(0xFFF3FAF6)
private val ChatsCard = Color(0xFFFFFFFF)
private val ChatsMint = Color(0xFF208A60)
private val ChatsMintLight = Color(0xFFE1F5E9)
private val ChatsCoral = Color(0xFFE87979)
private val ChatsText = Color(0xFF20372D)
private val ChatsMuted = Color(0xFF718078)
private val ChatsBorder = Color(0xFFDCEAE1)

private data class ChatsScreenUser(
    val id: String,
    val name: String,
    val phone: String,
    val avatarBase64: String
)

@Composable
fun VibeChatsListScreenV2(
    myId: String,
    onOpenPrivateChat: (String, String) -> Unit,
    onOpenGeneralChat: () -> Unit,
    onFindPeople: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }

    var search by remember { mutableStateOf("") }
    var users by remember {
        mutableStateOf<Map<String, ChatsScreenUser>>(emptyMap())
    }
    var chatUserIds by remember {
        mutableStateOf<List<String>>(emptyList())
    }
    var loadError by remember { mutableStateOf(false) }

    DisposableEffect(myId) {
        val usersListener = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    loadError = true
                } else {
                    loadError = false
                    users = snapshot?.documents?.associate { doc ->
                        doc.id to ChatsScreenUser(
                            id = doc.id,
                            name = doc.getString("name")
                                ?.takeIf { it.isNotBlank() }
                                ?: "Пользователь",
                            phone = doc.getString("phone").orEmpty(),
                            avatarBase64 = doc.getString("avatarBase64").orEmpty()
                        )
                    }.orEmpty()
                }
            }

        val chatsListener = db.collection("chats")
            .whereArrayContains("participantIds", myId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    loadError = true
                } else {
                    chatUserIds = snapshot?.documents
                        ?.filter { it.id != "general" }
                        ?.mapNotNull { doc ->
                            (doc.get("participantIds") as? List<*>)
                                ?.filterIsInstance<String>()
                                ?.firstOrNull { it != myId }
                        }
                        ?.distinct()
                        .orEmpty()
                }
            }

        onDispose {
            usersListener.remove()
            chatsListener.remove()
        }
    }

    val filteredUsers = users.values
        .filter { it.id != myId }
        .filter {
            search.isBlank() ||
                it.name.contains(search, ignoreCase = true) ||
                it.phone.contains(search, ignoreCase = true)
        }
        .sortedBy { it.name.lowercase() }

    val privateUsers = chatUserIds
        .mapNotNull { users[it] }
        .filter {
            search.isBlank() ||
                it.name.contains(search, ignoreCase = true) ||
                it.phone.contains(search, ignoreCase = true)
        }
        .distinctBy { it.id }
        .sortedBy { it.name.lowercase() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatsBg)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "VIBE MESSENGER",
                    color = ChatsMint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    text = "Твои чаты",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ChatsText
                )

                Text(
                    text = "Общайся. Находи друзей. Будь на связи.",
                    fontSize = 12.sp,
                    color = ChatsMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(47.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(ChatsMint, ChatsCoral)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = "Vibe Chat",
                    tint = Color.White,
                    modifier = Modifier.size(25.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))
                .background(ChatsCard)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Поиск",
                tint = ChatsMint
            )

            Spacer(Modifier.width(10.dp))

            Box(Modifier.weight(1f)) {
                if (search.isEmpty()) {
                    Text(
                        text = "Поиск по имени или номеру",
                        color = ChatsMuted,
                        fontSize = 14.sp
                    )
                }

                BasicTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = ChatsText
                    ),
                    cursorBrush = SolidColor(ChatsMint)
                )
            }

            if (search.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Очистить поиск",
                    tint = ChatsMuted,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { search = "" }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickChatCard(
                modifier = Modifier.weight(1f),
                title = "Общий чат",
                subtitle = "Все вместе",
                icon = Icons.Default.Group,
                accent = ChatsMint,
                onClick = onOpenGeneralChat
            )

            QuickChatCard(
                modifier = Modifier.weight(1f),
                title = "Найти людей",
                subtitle = "Новые знакомства",
                icon = Icons.Default.PersonSearch,
                accent = ChatsCoral,
                onClick = onFindPeople
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (search.isBlank()) {
                    "Личные разговоры"
                } else {
                    "Результаты поиска"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = ChatsText,
                modifier = Modifier.weight(1f)
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ChatsMintLight
            ) {
                Text(
                    text = if (search.isBlank()) {
                        privateUsers.size.toString()
                    } else {
                        filteredUsers.size.toString()
                    },
                    color = ChatsMint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        horizontal = 10.dp,
                        vertical = 5.dp
                    )
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (loadError) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFFF0ED)
                    ) {
                        Text(
                            text = "Не удалось обновить данные. Проверь подключение и настройки Firebase.",
                            color = Color(0xFFB94E4E),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            if (privateUsers.isEmpty() && search.isBlank()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(ChatsMintLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = ChatsMint,
                                modifier = Modifier.size(35.dp)
                            )
                        }

                        Spacer(Modifier.height(13.dp))

                        Text(
                            text = "Пока нет личных чатов",
                            color = ChatsText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )

                        Text(
                            text = "Найди пользователя и начни разговор",
                            color = ChatsMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                }
            } else {
                items(
                    items = privateUsers,
                    key = { it.id }
                ) { user ->
                    PrivateChatRow(user = user) {
                        onOpenPrivateChat(user.id, user.name)
                    }
                }
            }

            if (search.isNotBlank()) {
                val otherUsers = filteredUsers.filter { candidate ->
                    privateUsers.none { it.id == candidate.id }
                }

                if (otherUsers.isNotEmpty()) {
                    item {
                        Text(
                            text = "Другие пользователи",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = ChatsText,
                            modifier = Modifier.padding(
                                top = 12.dp,
                                bottom = 4.dp
                            )
                        )
                    }

                    items(
                        items = otherUsers,
                        key = { "person_${it.id}" }
                    ) { user ->
                        PrivateChatRow(user = user) {
                            onOpenPrivateChat(user.id, user.name)
                        }
                    }
                } else if (privateUsers.isEmpty()) {
                    item {
                        Text(
                            text = "Ничего не найдено. Попробуй другое имя или номер.",
                            color = ChatsMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickChatCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(
            containerColor = ChatsCard
        ),
        border = BorderStroke(1.dp, ChatsBorder),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(13.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(39.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.height(11.dp))

            Text(
                text = title,
                color = ChatsText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Text(
                text = subtitle,
                color = ChatsMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
private fun PrivateChatRow(
    user: ChatsScreenUser,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ChatsCard)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(49.dp)
                .clip(CircleShape)
                .background(ChatsMintLight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = user.name.firstOrNull()?.uppercase() ?: "V",
                color = ChatsMint,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = user.name,
                color = ChatsText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )

            Text(
                text = user.phone.ifBlank {
                    "Пользователь Vibe Chat"
                },
                color = ChatsMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Открыть чат",
            tint = ChatsMint
        )
    }
}
