
package com.vibe.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore

private val ChatsBg = Color(0xFFFAFAF9)
private val ChatsCard = Color(0xFFFFFFFF)
private val ChatsCoral = Color(0xFFE85B58)
private val ChatsMint = Color(0xFF75BDA7)
private val ChatsText = Color(0xFF252B2A)
private val ChatsMuted = Color(0xFF7A8581)

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
    var users by remember { mutableStateOf<Map<String, ChatsScreenUser>>(emptyMap()) }
    var chatUserIds by remember { mutableStateOf<List<String>>(emptyList()) }

    DisposableEffect(myId) {
        val usersListener = db.collection("users")
            .addSnapshotListener { snapshot, _ ->
                users = snapshot?.documents?.associate { doc ->
                    doc.id to ChatsScreenUser(
                        id = doc.id,
                        name = doc.getString("name") ?: "Пользователь",
                        phone = doc.getString("phone").orEmpty(),
                        avatarBase64 = doc.getString("avatarBase64").orEmpty()
                    )
                }.orEmpty()
            }

        val chatsListener = db.collection("chats")
            .whereArrayContains("participantIds", myId)
            .addSnapshotListener { snapshot, _ ->
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

    val privateUsers = chatUserIds.mapNotNull { users[it] }
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
        Spacer(Modifier.size(8.dp))

        Text(
            text = "Твои чаты",
            fontSize = 27.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ChatsText
        )

        Text(
            text = "Общайся, находи друзей, делись моментами",
            fontSize = 13.sp,
            color = ChatsMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ChatsCard)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "Поиск",
                tint = ChatsMint
            )

            Spacer(Modifier.width(10.dp))

            BasicTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = ChatsText
                ),
                decorationBox = { innerTextField ->
                    if (search.isEmpty()) {
                        Text(
                            "Поиск по имени или номеру",
                            color = ChatsMuted,
                            fontSize = 14.sp
                        )
                    }
                    innerTextField()
                }
            )
        }

        Spacer(Modifier.size(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickChatCard(
                modifier = Modifier.weight(1f),
                title = "Общий чат",
                subtitle = "Все вместе",
                icon = Icons.Default.Group,
                onClick = onOpenGeneralChat
            )

            QuickChatCard(
                modifier = Modifier.weight(1f),
                title = "Найти людей",
                subtitle = "Новые знакомства",
                icon = Icons.Default.PersonSearch,
                onClick = onFindPeople
            )
        }

        Spacer(Modifier.size(20.dp))

        Text(
            "Личные разговоры",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = ChatsText
        )

        Spacer(Modifier.size(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (privateUsers.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = ChatsMint,
                            modifier = Modifier.size(40.dp)
                        )

                        Spacer(Modifier.size(10.dp))

                        Text(
                            "Пока нет личных чатов",
                            color = ChatsText,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            "Найди пользователя и начни разговор",
                            color = ChatsMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                items(privateUsers, key = { it.id }) { user ->
                    PrivateChatRow(
                        user = user,
                        onClick = {
                            onOpenPrivateChat(user.id, user.name)
                        }
                    )
                }
            }

            if (search.isNotBlank()) {
                item {
                    Text(
                        "Пользователи",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = ChatsText,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(
                    filteredUsers.filter { candidate ->
                        privateUsers.none { it.id == candidate.id }
                    },
                    key = { "person_${it.id}" }
                ) { user ->
                    PrivateChatRow(
                        user = user,
                        onClick = {
                            onOpenPrivateChat(user.id, user.name)
                        }
                    )
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = ChatsCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(13.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = ChatsCoral,
                modifier = Modifier.size(27.dp)
            )

            Spacer(Modifier.size(12.dp))

            Text(
                title,
                color = ChatsText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Text(
                subtitle,
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
                .background(Color(0xFFFFE7E4)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                user.name.firstOrNull()?.uppercase() ?: "V",
                color = ChatsCoral,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                user.name,
                color = ChatsText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )

            Text(
                user.phone.ifBlank { "Пользователь Vibe Chat" },
                color = ChatsMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "Открыть",
            tint = ChatsMint
        )
    }
}
