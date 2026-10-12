
package com.vibe.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.cos
import kotlin.math.sin

private val ChatsBg = Color(0xFF101820)
private val ChatsCard = Color(0xFF17232D)
private val ChatsMint = Color(0xFF7AF7F0)
private val ChatsMintLight = Color(0xFF203A43)
private val ChatsCoral = Color(0xFFFF76C8)
private val ChatsText = Color(0xFFEAFBFF)
private val ChatsMuted = Color(0xFF9AB3BD)
private val ChatsBorder = Color(0xFF39747D)
private val ChatsViolet = Color(0xFFB68AFF)

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF111923),
                        ChatsBg,
                        Color(0xFF101E28)
                    )
                )
            )
    ) {
        CosmicParticles(
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Логотип Vibe Chat
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF24404D),
                                    Color(0xFF17212F)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "V",
                        fontSize = 45.sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                        color = ChatsMint
                    )
                }

                Spacer(Modifier.width(13.dp))

                Column {
                    Text(
                        text = "Vibe Chat",
                        fontSize = 31.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        color = ChatsMint,
                        lineHeight = 35.sp
                    )

                    Text(
                        text = "Твои разговоры",
                        fontSize = 14.sp,
                        color = ChatsMint.copy(alpha = 0.9f),
                        letterSpacing = 0.3.sp
                    )
                }
            }

            Spacer(Modifier.height(30.dp))

            // Заголовок поднят выше и сделан компактнее
            Text(
                text = "Твои чаты",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                color = ChatsMint,
                lineHeight = 40.sp
            )

            Text(
                text = "Общайся. Находи друзей. Будь на связи.",
                fontSize = 13.sp,
                color = ChatsMuted,
                modifier = Modifier.padding(top = 3.dp)
            )

            Spacer(Modifier.height(17.dp))

            // Неоновая строка поиска
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(ChatsCard)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                ChatsMint.copy(alpha = 0.045f),
                                Color.Transparent,
                                ChatsViolet.copy(alpha = 0.045f)
                            )
                        )
                    )
                    .then(
                        Modifier
                    )
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(ChatsCard)
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Поиск",
                        tint = ChatsMint,
                        modifier = Modifier.size(26.dp)
                    )

                    Spacer(Modifier.width(12.dp))

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
                                color = ChatsText,
                                fontSize = 15.sp
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
            }

            Spacer(Modifier.height(17.dp))

            // Общий чат доступен без отдельной вкладки.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(ChatsCard)
                    .clickable(onClick = onOpenGeneralChat)
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ChatsMint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = ChatsMint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(11.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Общий чат",
                        color = ChatsText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )

                    Text(
                        text = "Общайся со всеми",
                        color = ChatsMuted,
                        fontSize = 11.sp
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Открыть общий чат",
                    tint = ChatsMint
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
                    fontSize = 17.sp,
                    color = ChatsText,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ChatsMintLight,
                    border = BorderStroke(
                        1.dp,
                        ChatsBorder.copy(alpha = 0.65f)
                    )
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
                            color = Color(0xFF39252F),
                            border = BorderStroke(
                                1.dp,
                                ChatsCoral.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                text = "Не удалось обновить данные. Проверь подключение и настройки Firebase.",
                                color = Color(0xFFFFB6C9),
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
                                .padding(top = 34.dp, bottom = 26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                ChatsMint.copy(alpha = 0.19f),
                                                ChatsViolet.copy(alpha = 0.09f),
                                                Color.Transparent
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = ChatsMint,
                                    modifier = Modifier.size(38.dp)
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            Text(
                                text = "Пока нет личных чатов",
                                color = ChatsText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )

                            Text(
                                text = "Найди человека и начни разговор",
                                color = ChatsMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp)
                            )

                            Spacer(Modifier.height(13.dp))

                            Text(
                                text = "НАЙТИ ЛЮДЕЙ  →",
                                color = ChatsMint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable(onClick = onFindPeople)
                                    .background(ChatsMint.copy(alpha = 0.09f))
                                    .padding(
                                        horizontal = 17.dp,
                                        vertical = 10.dp
                                    )
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
                                fontSize = 16.sp,
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
}

@Composable
private fun CosmicParticles(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centerX = size.width * 0.5f
        val centerY = size.height * 0.59f
        val maxRadius = size.width * 0.36f

        for (i in 0 until 155) {
            val angle = i * 2.399963
            val distance = maxRadius *
                kotlin.math.sqrt((i + 1f) / 155f)

            val x = centerX + cos(angle).toFloat() * distance
            val y = centerY + sin(angle).toFloat() * distance * 1.2f

            if (x in 0f..size.width && y in 0f..size.height) {
                val radius = when {
                    i % 19 == 0 -> 2.0f
                    i % 7 == 0 -> 1.3f
                    else -> 0.7f
                }

                drawCircle(
                    color = if (i % 5 == 0) {
                        ChatsMint.copy(alpha = 0.42f)
                    } else {
                        ChatsMint.copy(alpha = 0.16f)
                    },
                    radius = radius.dp.toPx(),
                    center = Offset(x, y)
                )
            }
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
                .background(
                    Brush.linearGradient(
                        listOf(
                            ChatsMint.copy(alpha = 0.22f),
                            ChatsViolet.copy(alpha = 0.18f)
                        )
                    )
                ),
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
