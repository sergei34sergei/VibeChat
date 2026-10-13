package com.vibe.chat

import androidx.compose.runtime.Composable

/**
 * Совместимость MainActivity с актуальным голосовым экраном.
 */
@Composable
fun AlexVoiceScreen(
    onOpenChat: () -> Unit,
    onBack: () -> Unit
) {
    com.vibe.chat.ui.screens.AlexVoiceScreen(
        onCloseClick = onBack
    )
}

/**
 * Совместимость MainActivity с экраном личной переписки.
 */
@Composable
fun PrivateChatScreen(
    otherUserId: String,
    otherUserName: String,
    onBack: () -> Unit
) {
    com.vibe.chat.ui.screens.PrivateChatScreen(
        companionName = otherUserName,
        onBackClick = onBack
    )
}

/**
 * Совместимость MainActivity с актуальным списком чатов.
 */
@Composable
fun VibeChatsListScreenV2(
    myId: String,
    onOpenPrivateChat: (String, String) -> Unit,
    onOpenGeneralChat: () -> Unit,
    onFindPeople: () -> Unit
) {
    com.vibe.chat.ui.screens.VibeChatsListScreenV2(
        onChatClick = { id, name ->
            onOpenPrivateChat(id, name)
        },
        onAlexClick = {
            onFindPeople()
        }
    )
}
