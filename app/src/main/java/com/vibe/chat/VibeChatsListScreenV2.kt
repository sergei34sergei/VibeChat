
package com.vibe.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val ChatsBg = Color(0xFF0B0D18)
private val ChatsCard = Color(0xFF131626)
private val ChatsCardLight = Color(0xFF191B30)
private val ChatsMint = Color(0xFF75F7DF)
private val ChatsCoral = Color(0xFFFF9BCB)
private val ChatsViolet = Color(0xFFAD94FF)
private val ChatsText = Color(0xFFE5E9FA)
private val ChatsMuted = Color(0xFF858BA9)
private val ChatsBorder = Color(0xFF343650)

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

    val privateUsers = chatUserIds
        .mapNotNull { users[it] }
        .filter { it.id != myId }
        .filter {
            search.isBlank() ||
                it.name.contains(search, ignoreCase = true) ||
                it.phone.contains(search, ignoreCase = true)
        }
        .distinctBy { it.id }
        .sortedBy { it.name.lowercase() }

    val otherUsers = if (search.isNotBlank()) {
        users.values
            .filter { it.id != myId }
            .filter {
                it.name.contains(search, ignoreCase = true) ||
                    it.phone.contains(search, ignoreCase = true)
            }
            .filter { candidate ->
                privateUsers.none { it.id == candidate.id }
            }
            .sortedBy { it.name.lowercase() }
    } else {
        emptyList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF101326),
                        ChatsBg,
                        Color(0xFF101522),
                        Color(0xFF0D111D)
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
            Spacer(Modifier.height(12.dp))

            // Компактный неоновый заголовок
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(43.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF203D48),
                                    Color(0xFF19172D)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    ChatsMint.copy(alpha = 0.65f),
                                    ChatsViolet.copy(alpha = 0.45f)
                                )
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "V",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                        color = ChatsMint
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = "Vibe Chat",
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                        color = ChatsMint,
                        letterSpacing = 0.2.sp,
                        lineHeight = 29.sp
                    )

                    Text(
                        text = "ТВОЁ ПРОСТРАНСТВО ОБЩЕНИЯ",
                        fontSize = 9.sp,
                        color = ChatsViolet.copy(alpha = 0.9f),
                        letterSpacing = 1.15.sp
                    )
                }

                Spacer(Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ChatsMint.copy(alpha = 0.9f))
                )
            }

            Spacer(Modifier.height(29.dp))

            Text(
                text = "Сообщения",
                fontSize = 25.sp,
                fontWeight = FontWeight.Medium,
                color = ChatsText,
                letterSpacing = 0.1.sp
            )

            Text(
                text = "Твои разговоры в одном месте",
                fontSize = 12.sp,
                color = ChatsMuted,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(19.dp))

            // Тонкая строка поиска с аккуратным свечением
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(49.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF171B2C),
                                Color(0xFF141626),
                                Color(0xFF19162B)
                            )
                        )
                    )
                    .border(
                        width = 0.8.dp,
                        color = if (search.isNotEmpty()) {
                            ChatsMint.copy(alpha = 0.65f)
                        } else {
                            ChatsBorder.copy(alpha = 0.8f)
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Поиск",
                    tint = ChatsMint.copy(alpha = 0.9f),
                    modifier = Modifier.size(21.dp)
                )

                Spacer(Modifier.width(10.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (search.isEmpty()) {
                        Text(
                            text = "Поиск чатов и людей",
                            fontSize = 13.sp,
                            color = ChatsMuted
                        )
                    }

                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = ChatsText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(ChatsMint)
                    )
                }

                if (search.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Очистить поиск",
                        tint = ChatsMuted,
                        modifier = Modifier
                            .size(19.dp)
                            .clickable { search = "" }
                    )
                }
            }

            Spacer(Modifier.height(25.dp))

            // Заголовок списка
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (search.isBlank()) {
                        "ТВОИ ЧАТЫ"
                    } else {
                        "РЕЗУЛЬТАТЫ ПОИСКА"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ChatsViolet,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.weight(1f)
                )

                if (search.isBlank()) {
                    Text(
                        text = privateUsers.size.toString(),
                        fontSize = 12.sp,
                        color = ChatsMint,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (loadError) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(13.dp),
                            color = Color(0xFF291D2B),
                            border = BorderStroke(
                                0.8.dp,
                                ChatsCoral.copy(alpha = 0.45f)
                            )
                        ) {
                            Text(
                                text = "Не удалось загрузить данные. Проверь подключение и Firebase.",
                                color = Color(0xFFFFB9D5),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                if (privateUsers.isEmpty() && search.isBlank()) {
                    item {
                        EmptyChatsState()
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
                    if (otherUsers.isNotEmpty()) {
                        item {
                            Text(
                                text = "Другие пользователи",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = ChatsMuted,
                                modifier = Modifier.padding(
                                    top = 12.dp,
                                    bottom = 2.dp
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
                    }

                    if (privateUsers.isEmpty() && otherUsers.isEmpty()) {
                        item {
                            Text(
                                text = "Ничего не найдено",
                                color = ChatsMuted,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 30.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 66.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            ChatsMint.copy(alpha = 0.16f),
                            ChatsViolet.copy(alpha = 0.09f),
                            Color.Transparent
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = ChatsViolet.copy(alpha = 0.23f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = ChatsMint,
                modifier = Modifier.size(35.dp)
            )
        }

        Spacer(Modifier.height(19.dp))

        Text(
            text = "Здесь будут твои чаты",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = ChatsText
        )

        Spacer(Modifier.height(7.dp))

        Text(
            text = "Начни разговор — и он появится здесь",
            fontSize = 12.sp,
            color = ChatsMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CosmicParticles(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centerX = size.width * 0.5f
        val centerY = size.height * 0.59f
        val maxRadius = size.width * 0.43f

        for (i in 0 until 115) {
            val angle = i * 2.399963
            val distance = maxRadius * sqrt((i + 1f) / 115f)

            val x = centerX + cos(angle).toFloat() * distance
            val y = centerY + sin(angle).toFloat() * distance * 1.2f

            if (x in 0f..size.width && y in 0f..size.height) {
                val radius = when {
                    i % 23 == 0 -> 1.7f
                    i % 9 == 0 -> 1.15f
                    else -> 0.65f
                }

                val particleColor = when {
                    i % 11 == 0 -> ChatsViolet
                    i % 7 == 0 -> ChatsCoral
                    else -> ChatsMint
                }

                drawCircle(
                    color = particleColor.copy(
                        alpha = when {
                            i % 23 == 0 -> 0.42f
                            i % 7 == 0 -> 0.25f
                            else -> 0.12f
                        }
                    ),
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
            .clip(RoundedCornerShape(17.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF171A2B),
                        ChatsCard,
                        Color(0xFF151525)
                    )
                )
            )
            .border(
                width = 0.7.dp,
                color = ChatsBorder.copy(alpha = 0.55f),
                shape = RoundedCornerShape(17.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(47.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF23413F),
                            Color(0xFF29213E)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            ChatsMint.copy(alpha = 0.65f),
                            ChatsViolet.copy(alpha = 0.55f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = user.name.firstOrNull()?.uppercase() ?: "V",
                color = ChatsMint,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = user.name,
                color = ChatsText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )

            Text(
                text = user.phone.ifBlank {
                    "Пользователь Vibe Chat"
                },
                color = ChatsMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(ChatsViolet.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "›",
                color = ChatsMint.copy(alpha = 0.9f),
                fontSize = 23.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}
